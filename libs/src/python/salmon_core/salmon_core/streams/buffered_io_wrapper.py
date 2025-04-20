#!/usr/bin/env python3
"""!@brief Wrapper stream of AbsStream to Python's native IOBase interface.
"""

__license__ = """
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

from io import RawIOBase, BufferedIOBase
from typeguard import typechecked
from wrapt import synchronized

from salmon_core.streams.buffer import Buffer
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class BufferedIOWrapper(BufferedIOBase):
    """!
    Wrapper stream of AbsStream to Python's native IOBase interface.
    Use this class to wrap any AbsStream to a less powerful but familiar and compatible Python InputStream.
    """

    __DEFAULT_BUFFER_SIZE = 512 * 1024
    """
    Default cache buffer should be high enough for most buffer needs
    the cache buffers should be aligned to the AesFile chunk size for efficiency
    """

    __DEFAULT_BUFFERS = 1
    """
    The default buffer count
    """

    __DEFAULT_BACK_OFFSET = 32768
    """
    The default backwards buffer offset
    """

    __MAX_BUFFERS = 6
    """
    The maximum allowed buffer count
    """

    def __init__(self, stream: RandomAccessStream | None, buffers_count: int = 1, buffer_size: int = 0, back_offset: int = 32768,
                 align_size: int = 0):
        """!
        Instantiates an BufferedIOWrapper with a base stream.
        @param stream: The base AbsStream that you want to wrap.
        """

        self.__stream = stream
        self.__buffersCount: int
        self.__buffers: list[Buffer | None] | None = None
        self.__buffer_size: int = 0
        self.__stream_position: int = 0
        self.__total_size: int = 0
        self.__align_size: int = 0

        self.__lru_buffers_index: list[int] = []
        """
        We reuse the least recently used buffer. Since the buffer count is relative
        small (see {@link #MAX_BUFFERS}) there is no need for a fast-access lru queue
        so a simple linked list of keeping the indexes is adequately fast.
        """

        self.__backOffset: int
        """
        Negative offset for the buffers. Some stream consumers might request data right before
        the last request. We provide this offset so we don't make multiple requests for filling
        the buffers ending up with too much overlapping data.
        """

        if stream:
            self.__total_size = stream.get_length()
            self.__stream_position = stream.get_position()
        self.__position_start: int
        self.__position_end: int

        if buffers_count <= 0:
            buffers_count = BufferedIOWrapper.__DEFAULT_BUFFERS
        if buffers_count > BufferedIOWrapper.__MAX_BUFFERS:
            buffers_count = BufferedIOWrapper.__MAX_BUFFERS
        if buffer_size <= 0:
            buffer_size = BufferedIOWrapper.__DEFAULT_BUFFER_SIZE
        if back_offset < 0:
            back_offset = BufferedIOWrapper.__DEFAULT_BACK_OFFSET

        if align_size <= 0 and stream:
            align_size = stream.get_align_size()

        # align the buffers for performance
        if align_size > 0:
            if back_offset > 0:
                n_back_offset = back_offset // align_size * align_size
                if n_back_offset < back_offset:
                    n_back_offset += align_size
                back_offset = n_back_offset

            n_buffer_size = buffer_size // align_size * align_size
            if n_buffer_size < align_size:
                n_buffer_size = align_size
            if n_buffer_size < buffer_size:
                n_buffer_size += align_size
            buffer_size = n_buffer_size

        if back_offset > 0:
            buffer_size += back_offset
            #  we use a minimum 2 buffers since it is very likely
            #  that the previous buffer in use will have the backoffset
            # data of the new one
            if buffers_count == 1:
                buffers_count = 2

        self.__buffers_count = buffers_count
        self.__buffer_size = buffer_size
        self.__backoffset = back_offset
        self.__position_start = 0
        self.__position_end = self.__total_size - 1
        self.__align_size = align_size
        self.__create_buffers()

    def __create_buffers(self):
        """!
        Create cache buffers that will be used for sourcing the files.
        These will help reducing multiple small decryption reads from the encrypted source.
        The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
        The rest of the buffers can be placed to whatever position the user slides to
        """
        self.__buffers: list[Buffer] | list[None] = [None] * self.__buffers_count
        for i in range(0, self.__buffers_count):
            self.__buffers[i] = Buffer(self.__buffer_size)

    @synchronized
    def _fill_buffer(self, cache_buffer: Buffer, start_position: int,
                     length: int) -> int:
        """!
        Fills a cache buffer with the decrypted data from the encrypted source file.

        @param cache_buffer: The cache buffer that will store the decrypted contents
        @param length:  The length of the data requested
        """
        bytes_read = self._fill_buffer_part(cache_buffer, start_position, 0, length, self.__stream)
        return bytes_read

    def _fill_buffer_part(self, cache_buffer: Buffer, start: int, offset: int,
                          length: int,
                          stream: RandomAccessStream) -> int:
        """!
        Fills a cache buffer with the decrypted data from a part of an encrypted file

        @param cache_buffer:  The cache buffer that will store the decrypted contents
        @param start:  The start position
        @param offset:  The data offset
        @param length:   The length of the data requested
        @param stream: The stream that will be used to read from
        """
        stream.seek(start, RandomAccessStream.SeekOrigin.Begin)
        bytes_read: int = 0
        total_bytes_read: int = 0
        while ((bytes_read := stream.read(cache_buffer.get_data(), offset + total_bytes_read,
                                          length - total_bytes_read)) > 0):
            total_bytes_read += bytes_read
        return total_bytes_read

    @synchronized
    def _get_avail_cache_buffer(self) -> Buffer | None:
        """!
        Returns an available cache buffer if there is none then reuse the least recently used one.
        """
        if not self.__buffers:
            raise Exception("No buffers found")
        index = -1
        if len(self.__lru_buffers_index) == self.__buffers_count:
            index: int = self.__lru_buffers_index.pop()
        else:
            for i in range(len(self.__buffers)):
                buff: Buffer = self.__buffers[i]
                if buff and buff.get_count() == 0:
                    index = i
                    break
        if index < 0:
            index = len(self.__buffers) - 1
        self.__lru_buffers_index.insert(0, index)
        return self.__buffers[index]

    @synchronized
    def __get_cache_buffer(self, position: int, count: int) -> Buffer | None:
        """!
        Returns the buffer that contains the data requested.

        @param position: The source file position of the data to be read
        """
        if not self.__buffers:
            return None
        for i in range(0, len(self.__buffers)):
            buffer: Buffer = self.__buffers[i]
            if buffer and position >= buffer.get_start_pos() and position + count \
                    <= buffer.get_start_pos() + buffer.get_count():
                # promote buffer to the front
                self.__lru_buffers_index.remove(i)
                self.__lru_buffers_index.insert(0, i)
                return buffer
        return None

    def readinto(self, buffer: bytearray | memoryview) -> int:
        """!
        Reads and decrypts the contents of an encrypted file

        @param buffer: The buffer that will store the decrypted contents
        """

        if self.__stream_position >= self.__position_end + 1:
            return 0

        min_count: int
        bytes_read: int

        # truncate the count so getCacheBuffer() reports the correct buffer
        count = min(len(buffer), self.__total_size - self.__stream_position)

        cache_buffer: Buffer | None = self.__get_cache_buffer(self.__stream_position, count)
        if cache_buffer is None:
            cache_buffer = self._get_avail_cache_buffer()
            # the stream is closed
            if not cache_buffer:
                return 0

            # for some applications like media players they make a second immediate request
            # in a position a few bytes before the first request. To make
            # sure we don't make 2 overlapping requests we start the buffer
            # a position ahead of the first request.
            start_position = self.__stream_position
            if self.__align_size > 0:
                start_position = start_position // self.__align_size * self.__align_size

            length = self.__buffer_size

            # if we have the backoffset data in an existing buffer we don't include the backoffset
            # in the new request because we want to prevent network streams resetting.
            if start_position > 0 and not self.has_backoffset(start_position):
                start_position -= self.__backoffset
            else:
                length -= self.__backoffset

            bytes_read = self._fill_buffer(cache_buffer, start_position, length)

            if bytes_read <= 0:
                return bytes_read
            cache_buffer.set_start_pos(start_position)
            cache_buffer.set_count(bytes_read)

        # align the count also
        end = self.__stream_position + count
        n_count = end // self.__align_size * self.__align_size - self.__stream_position
        if n_count > 0 and n_count < count:
            count = n_count

        min_count = min(count, cache_buffer.get_count() - self.__stream_position + cache_buffer.get_start_pos())
        buffer[0:min_count] = cache_buffer.get_data()[
                              self.__stream_position - cache_buffer.get_start_pos():
                              self.__stream_position - cache_buffer.get_start_pos() + min_count]
        self.__stream_position += min_count
        return min_count

    def has_backoffset(self, start_position: int) -> bool:
        pos = start_position - self.__backoffset
        for i in range(len(self.__buffers)):
            buffer: Buffer = self.__buffers[i]
            if buffer and buffer.get_count() > 0 \
                    and buffer.get_start_pos() <= pos \
                    and start_position <= buffer.get_start_pos() + buffer.get_count():
                return True
        return False

    def read(self, size: int = __DEFAULT_BUFFER_SIZE) -> bytearray:
        """!
        Read from the stream
        @param size: The number of bytes to read.
        @returns The data
        """
        return self.read1(size)

    def read1(self, size: int = __DEFAULT_BUFFER_SIZE) -> bytearray:
        """!
        Read from the stream
        @param size: The number of bytes to read.
        @returns The data
        """
        buff: bytearray = bytearray(size)
        bytes_read = self.readinto(buff)
        return buff[0:bytes_read]

    def detach(self) -> RawIOBase:
        raise NotImplementedError()

    def write(self, __buffer: bytearray) -> int:
        """!
        Write to the stream. Not supported
        @param __buffer: The data to write.
        @returns The number of data written
        """
        raise NotImplementedError()

    def readinto1(self, __buffer: bytearray) -> int:
        """!
        Read from the stream
        @param __buffer: The data to read into.
        @returns The number of bytes read
        """
        return self.readinto(__buffer)

    def close(self):
        """!
        Closes the base stream.
        @exception IOError: Thrown if there is an IO error.
        """
        if self.__stream:
            self.__stream.close()
            self.__stream = None
        self.__clear_buffers()

    def seek(self, pos: int, whence: int = ...) -> int:
        """!
        Seek to a position in the stream

        @param pos: the number of bytes to
        @param whence: Origin: 0: from the start, 1: from the current position, 2: from the end of the stream
        @returns The current position after seeking
        """
        if whence == 0:
            self.__stream_position = pos
        elif whence == 1:
            self.__stream_position += pos
        elif whence == 2:
            self.__stream_position = self.length() - pos
        return self.__stream_position

    def tell(self):
        """!
        Get the position of the stream
        @returns The position
        """
        return self.__stream_position

    @synchronized
    def __clear_buffers(self):
        """!
        Clear all buffers.
        """
        for i in range(0, len(self.__buffers)):
            if self.__buffers[i]:
                self.__buffers[i].clear()
            self.__buffers[i] = None

    def get_buffer_size(self) -> int:
        """!
        Get the buffer size

        @returns The buffer size
        """
        return self.__buffer_size

    def get_backoffset(self) -> int:
        """!
        Get the back offset

        @returns The back offset
        """
        return self.__backoffset

    def get_total_size(self) -> int:
        """!
        Get the total size of the stream.

        @returns The size
        """
        return self.__total_size

    def set_total_size(self, total_size: int):
        """!
        Set the total size
        @param total_size The total size
        """
        self.__total_size = total_size

    def get_position_start(self) -> int:
        """!
        Get the start position
        @returns The start position
        """
        return self.__position_start

    def set_position_start(self, pos: int):
        """!
        Set the start position
        @param pos The start position
        """
        self.__position_start = pos

    def get_position_end(self) -> int:
        """!
        Get the end position
        @returns The end position
        """
        return self.__position_end

    def set_position_end(self, pos: int):
        """!
        Set the end position
        @param pos The end position
        """
        self.__position_end = pos
