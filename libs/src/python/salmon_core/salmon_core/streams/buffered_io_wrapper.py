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
from io import RawIOBase, BufferedIOBase

from typeguard import typechecked

from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class BufferedIOWrapper(BufferedIOBase):
    """
    Wrapper stream of AbsStream to Python's native IOBase interface.
    Use this class to wrap any AbsStream to a less powerful but familiar and compatible Python InputStream.
    """

    # Default cache buffer should be high enough for most buffer needs
    # the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    __DEFAULT_BUFFER_SIZE = 512 * 1024

    def __init__(self, stream: RandomAccessStream):
        """
        Instantiates an BufferedIOWrapper with a base stream.
        :param stream: The base AbsStream that you want to wrap.
        """

        self.__stream = stream

    def readinto(self, buffer: bytearray) -> int:
        """
        Read a sequence of bytes from the base stream into the buffer provided.
        to specify the count and offset pass a memoryview instead
        :param buffer:     the buffer into which the data is read.
        :return: The number of bytes read.
        :raises IOError: with an optional inner Exception if the base stream is a SalmonStream
        """
        bytes_read: int
        try:
            bytes_read = self.__stream.read(buffer, 0, len(buffer))
        except Exception as exception:
            raise IOError() from exception
        return bytes_read

    def read(self, size: int | None = ...) -> bytearray:
        if size is None:
            size = BufferedIOWrapper.__DEFAULT_BUFFER_SIZE
        return self.read1(size)

    def read1(self, size: int = ...) -> bytearray:
        buff: bytearray = bytearray(size)
        bytes_read = self.readinto(buff)
        return buff[0:bytes_read]

    def detach(self) -> RawIOBase:
        raise NotImplementedError()

    def write(self, __buffer: bytearray) -> int:
        raise NotImplementedError()

    def readinto1(self, __buffer: bytearray) -> int:
        return self.readinto(__buffer)

    def close(self):
        """
        Closes the base stream.
        :raises IOError: Thrown if there is an IO error.
        """
        self.__stream.close()

    def seek(self, pos: int, whence: int = ...) -> int:
        """
        Skip number of bytes on the stream.
        :param pos:   the number of bytes to be skipped.
        :param whence: Origin: 0: from the start, 1: from the current position, 2: from the end of the stream
        :return: The number of bytes skipped
        :raises IOError: Thrown if there is an IO error.
        """
        if whence == 1:
            pos += self.__stream.get_position()
        elif whence == 2:
            pos = self.__stream.length() - pos
        if pos > self.__stream.length():
            self.__stream.set_position(self.__stream.length())
        else:
            self.__stream.set_position(self.__stream.get_position() + pos)
        return self.__stream.get_position()

    def tell(self):
        return self.__stream.get_position()
