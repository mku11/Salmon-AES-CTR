#!/usr/bin/env python3
'''
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
'''
from __future__ import annotations

import math
import threading
from concurrent.futures import ThreadPoolExecutor
from io import BufferedIOBase, RawIOBase

from typeguard import typechecked
from wrapt import synchronized

from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException
from salmon_fs.salmon.salmon_file import SalmonFile


@typechecked
class SalmonFileInputStream(BufferedIOBase):
    """
    Implementation of a Python InputStream for seeking and reading a SalmonFile.
    This class provides a seekable source with parallel substreams and cached buffers
    for performance.
    """

    # TAG: str = type(SalmonFileInputStream.__class__).__name__

    # Default cache buffer should be high enough for some mpeg videos to work
    # the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    __DEFAULT_BUFFER_SIZE = 512 * 1024

    # default threads is one but you can increase it
    __DEFAULT_THREADS = 1

    __DEFAULT_BUFFERS = 3

    __MAX_BUFFERS = 6

    def __init__(self, salmon_file: SalmonFile, buffers_count: int, buffer_size: int, threads: int, back_offset: int):

        """
        Instantiate a seekable stream from an encrypted file source
        
        :param salmon_file:   The source file.
        :param buffers_count: Number of buffers to use.
        :param buffer_size:   The length of each buffer.
        :param threads:      The number of threads/streams to source the file in parallel.
        :param back_offset:   The back offset.
        """
        self.__buffersCount: int
        self.__buffers: list[SalmonFileInputStream.CacheBuffer] | list[None] | None = None
        self.__streams: list[SalmonStream] | list[None] | None = None
        self.__salmonFile: SalmonFile | None = None
        self.__cacheBufferSize: int = 0
        self.__threads: int = 0
        self.__executor: ThreadPoolExecutor | None = None
        self.__position: int = 0
        self.__size: int = 0

        self.__lruBuffersIndex: list[int] = []
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

        self.__salmonFile = salmon_file
        self.__size = salmon_file.get_size()
        self.__positionStart: int
        self.__positionEnd: int

        if buffers_count == 0:
            buffers_count = SalmonFileInputStream.__DEFAULT_BUFFERS
        if buffers_count > SalmonFileInputStream.__MAX_BUFFERS:
            buffers_count = SalmonFileInputStream.__MAX_BUFFERS
        if buffer_size == 0:
            buffer_size = SalmonFileInputStream.__DEFAULT_BUFFER_SIZE
        if back_offset > 0:
            buffer_size += back_offset
        if threads == 0:
            threads = SalmonFileInputStream.__DEFAULT_THREADS

        self.__buffersCount = buffers_count
        self.__cacheBufferSize = buffer_size
        self.__threads = threads
        self.__backOffset = back_offset
        self.__positionStart = 0
        self.__positionEnd = self.__size - 1

        self.create_buffers()
        self.__create_streams()

    def __create_streams(self):
        """
        Method creates the parallel streams for reading from the file
        """
        self.__executor = ThreadPoolExecutor(self.__threads)
        self.__streams = [None] * self.__threads
        try:
            for i in range(0, self.__threads):
                self.__streams[i] = self.__salmonFile.get_input_stream()

        except (SalmonSecurityException, IntegrityException) as ex:
            raise IOError("Could not create streams") from ex

    def create_buffers(self):
        """
        Create cache buffers that will be used for sourcing the files.
        These will help reducing multiple small decryption reads from the encrypted source.
        The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
        The rest of the buffers can be placed to whatever position the user slides to
        """
        self.__buffers: list[SalmonFileInputStream.CacheBuffer] | list[None] = [None] * self.__buffersCount
        for i in range(0, self.__buffersCount):
            self.__buffers[i] = SalmonFileInputStream.CacheBuffer(self.__cacheBufferSize)

    def seek(self, v_bytes: int, whence: int = ...) -> int:
        """
        Seek to a position in the stream
        
        :param v_bytes: the number of bytes to
        :param whence: Origin: 0: from the start, 1: from the current position, 2: from the end of the stream
        :return: The current position after seeking
        """
        if whence == 0:
            self.__position = v_bytes
        elif whence == 1:
            self.__position += v_bytes
        elif whence == 2:
            self.__position = self.length() - v_bytes
        return self.__position

    def readinto(self, buffer: bytearray | memoryview) -> int:
        """
        Reads and decrypts the contents of an encrypted file
        
        :param buffer: The buffer that will store the decrypted contents
        """

        if self.__position >= self.__positionEnd + 1:
            return -1

        min_count: int
        bytes_read: int

        # truncate the count so getCacheBuffer() reports the correct buffer
        count = min(len(buffer), self.__size - self.__position)

        cache_buffer: SalmonFileInputStream.CacheBuffer | None = self.__get_cache_buffer(self.__position, count)
        if cache_buffer is None:
            cache_buffer = self.__get_avail_cache_buffer()
            # the stream is closed
            if cache_buffer is None:
                return -1
            # for some applications like media players they make a second immediate request
            # in a position a few bytes before the first request. To make
            # sure we don't make 2 overlapping requests we start the buffer
            # a position ahead of the first request.
            start_position: int = self.__position - self.__backOffset
            if start_position < 0:
                start_position = 0

            bytes_read = self.__fill_buffer(cache_buffer, start_position, self.__cacheBufferSize)
            if bytes_read <= 0:
                return -1
            cache_buffer.startPos = start_position
            cache_buffer.count = bytes_read

        min_count = min(count, cache_buffer.count - self.__position + cache_buffer.startPos)
        buffer[0:min_count] = cache_buffer.buffer[
                              self.__position - cache_buffer.startPos:
                              self.__position - cache_buffer.startPos + min_count]
        self.__position += min_count
        return min_count

    def read(self, size: int | None = ...) -> bytearray:
        raise NotImplementedError("use readinto instead")

    def read1(self, size: int = ...) -> bytearray:
        raise NotImplementedError("use readinto instead")

    def detach(self) -> RawIOBase:
        raise NotImplementedError()

    def write(self, __buffer: bytearray) -> int:
        raise NotImplementedError()

    def readinto1(self, __buffer: bytearray) -> int:
        raise NotImplementedError()

    def tell(self) -> int:
        return self.__position - self.__positionStart

    @synchronized
    def __fill_buffer(self, cache_buffer: SalmonFileInputStream.CacheBuffer, start_position: int,
                      buffer_size: int) -> int:
        """
        Fills a cache buffer with the decrypted data from the encrypted source file.
        
        :param cache_buffer: The cache buffer that will store the decrypted contents
        :param buffer_size:  The length of the data requested
        """

        bytes_read: int
        if self.__threads == 1:
            bytes_read = self.__fill_buffer_part(cache_buffer, start_position, 0, buffer_size, self.__streams[0])
        else:
            bytes_read = self.__fill_buffer_multi(cache_buffer, start_position, buffer_size)

        return bytes_read

    def __fill_buffer_part(self, cache_buffer: SalmonFileInputStream.CacheBuffer, start: int, offset: int,
                           buffer_size: int,
                           salmon_stream: SalmonStream) -> int:
        """
        Fills a cache buffer with the decrypted data from a part of an encrypted file served as a salmon stream
        
        :param cache_buffer:  The cache buffer that will store the decrypted contents
        :param buffer_size:   The length of the data requested
        :param salmon_stream: The stream that will be used to read from
        """
        salmon_stream.seek(start, RandomAccessStream.SeekOrigin.Begin)
        total_bytes_read: int = salmon_stream.read(cache_buffer.buffer, offset, buffer_size)
        return total_bytes_read

    def __fill_buffer_multi(self, cache_buffer: SalmonFileInputStream.CacheBuffer, start_position: int,
                            buffer_size: int) -> int:
        """
        Fill the buffer using parallel streams for performance
        
        :param cache_buffer:   The cache buffer that will store the decrypted data
        :param start_position: The source file position the read will start from
        :param buffer_size:    The buffer size that will be used to read from the file
        """
        bytes_read = [0]
        ex: Exception | None = None
        # Multithreaded decryption jobs
        done: threading.Barrier = threading.Barrier(self.__threads + 1)
        part_size: int = int(math.ceil(buffer_size / float(self.__threads)))
        for i in range(0, self.__threads):

            def __fill(index: int):
                nonlocal ex, bytes_read

                start: int = part_size * index
                length: int
                if index == self.__threads - 1:
                    length = buffer_size - start
                else:
                    length = part_size
                try:
                    chunk_bytes_read: int = self.__fill_buffer_part(cache_buffer, start_position + start, start, length,
                                                                    self.__streams[index])
                    if chunk_bytes_read >= 0:
                        bytes_read[0] += chunk_bytes_read
                except IOError as ex1:
                    ex = ex1
                except Exception as ex2:
                    print(ex2)

                done.wait()

            self.__executor.submit(__fill, i)

        try:
            done.wait()
        except InterruptedError as ignored:
            pass

        if ex is not None:
            try:
                raise ex
            except Exception as e:
                raise RuntimeError() from e

        return bytes_read[0]

    @synchronized
    def __get_avail_cache_buffer(self) -> SalmonFileInputStream.CacheBuffer | None:
        """
        Returns an available cache buffer if there is none then reuse the least recently used one.
        """
        if len(self.__lruBuffersIndex) == self.__buffersCount:
            # getting least recently used buffer
            index: int = self.__lruBuffersIndex[-1]
            # promote to the top
            self.__lruBuffersIndex.remove(index)
            self.__lruBuffersIndex.insert(0, index)
            return self.__buffers[self.__lruBuffersIndex[-1]]

        for i in range(0, len(self.__buffers)):
            buffer: SalmonFileInputStream.CacheBuffer = self.__buffers[i]
            if buffer is not None and buffer.count == 0:
                self.__lruBuffersIndex.insert(0, i)
                return buffer

        if self.__buffers[len(self.__buffers) - 1] is not None:
            return self.__buffers[len(self.__buffers) - 1]
        else:
            return None

    @synchronized
    def __get_cache_buffer(self, position: int, count: int) -> SalmonFileInputStream.CacheBuffer | None:
        """
        Returns the buffer that contains the data requested.
        
        :param position: The source file position of the data to be read
        """
        for i in range(0, len(self.__buffers)):
            buffer: SalmonFileInputStream.CacheBuffer = self.__buffers[i]
            if buffer is not None and self.__position >= buffer.startPos and self.__position + count \
                    <= buffer.startPos + buffer.count:
                # promote buffer to the front
                self.__lruBuffersIndex.remove(i)
                self.__lruBuffersIndex.insert(0, i)
                return buffer

        return None

    def get_size(self) -> int:
        """
        Get the size of the stream.
        
        :return: The size
        """
        return self.__positionEnd - self.__positionStart + 1

    def get_position_start(self) -> int:
        return self.__positionStart

    def set_position_start(self, pos: int):
        self.__positionStart = pos

    def set_position_end(self, pos: int):
        self.__positionEnd = pos

    def close(self):
        """
        Close the stream and associated backed streams and clear buffers.
        
        :raises IOError: Thrown if there is an IO error.
        """
        self.__close_streams()
        self.__clear_buffers()
        self.__executor.shutdown()

    @synchronized
    def __clear_buffers(self):
        """
        Clear all buffers.
        """
        for i in range(0, len(self.__buffers)):
            if self.__buffers[i] is not None:
                self.__buffers[i].clear()
            self.__buffers[i] = None

    @synchronized
    def __close_streams(self):
        """
        Close all back streams.
        
        :raises IOError: Thrown if there is an IO error.
        """
        for i in range(0, self.__threads):
            if self.__streams[i] is not None:
                self.__streams[i].close()
            self.__streams[i] = None

    # TODO: replace the CacheBuffer with a MemoryStream to simplify the code
    class CacheBuffer:
        """
        Class will be used to cache decrypted data that can later be read via the ReadAt() method
        without requesting frequent decryption reads.
        """

        def __init__(self, buffer_size: int):
            """
            Instantiate a cache buffer.
            
            :param buffer_size:             """
            self.buffer: bytearray
            self.startPos: int = 0
            self.count: int = 0

            self.buffer = bytearray(buffer_size)

        def clear(self):
            """
            Clear the buffer.
            """
            if self.buffer is not None:
                self.buffer[0:len(self.buffer)] = [0] * len(self.buffer)
