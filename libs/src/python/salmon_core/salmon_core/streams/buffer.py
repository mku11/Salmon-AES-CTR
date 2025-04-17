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

from typeguard import typechecked

@typechecked
class Buffer:
    """!
    Class will be used to cache decrypted data that can later be read via the ReadAt() method
    without requesting frequent decryption reads.
    """

    def __init__(self, buffer_size: int):
        """
        Instantiate a cache buffer.

        @param buffer_size:             """
        self.__start_pos: int = 0
        self.__count: int = 0
        self.__data: bytearray = bytearray(buffer_size)

    def get_data(self) -> bytearray:
        """
        Get the buffer data
        @returns The buffer data
        """
        return self.__data

    def get_count(self) -> int:
        """
        Get the number of bytes in the buffer
        @returns The byte count
        """
        return self.__count

    def set_count(self, count):
        """
        Set the data count
        @param count The data count
        """
        self.__count = count

    def get_start_pos(self) -> int:
        """
        Get the start position
        @returns The start position
        """
        return self.__start_pos

    def set_start_pos(self, start_pos):
        """
        Set the start position
        @param start_pos The start position
        """
        self.__start_pos = start_pos

    def clear(self):
        """
        Clear the buffer.
        """
        if self.__data:
            self.__data[0:len(self.__data)] = [0] * len(self.__data)
