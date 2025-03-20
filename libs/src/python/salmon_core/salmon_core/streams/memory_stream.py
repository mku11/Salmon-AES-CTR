#!/usr/bin/env python3
"""!@brief Memory Stream for seeking, reading, and writing to a memory buffer 
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

from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class MemoryStream(RandomAccessStream):
    """!
    Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
    If the memory buffer is not specified then an internal resizable buffer will be created.
    """

    __CAPACITY_INCREMENT = 128 * 1024
    """
    Increment to resize to when capacity is exhausted.
    """

    def __init__(self, v_bytes: bytearray | None = None):
        """!
        Create a memory stream backed by an existing byte-array.
        @param v_bytes: The buffer to use
        """

        self.__bytes: bytearray | None = None
        """!
        Buffer to store the data. This can be provided via the constructor.
        """

        self.__position: int = 0
        """
        Current position of the stream.
        """

        self.__capacity: int = 0
        """
        Current capacity.
        """

        self.__length: int = 0
        """
        Current length of the stream.
        """

        if v_bytes is None:
            self.__bytes = bytearray(MemoryStream.__CAPACITY_INCREMENT)
            self.__capacity = MemoryStream.__CAPACITY_INCREMENT
            return
        self.__length = len(v_bytes)
        self.__bytes = v_bytes
        self.__capacity = len(v_bytes)

    def can_read(self) -> bool:
        """!
        @returns Always True.
        """
        return True

    def can_write(self) -> bool:
        """!
        @returns Always True.
        """
        return True

    def can_seek(self) -> bool:
        """!
        @returns Always True.
        """
        return True

    def get_length(self) -> int:
        """!
        
        @returns The length of the stream.
        """
        return self.__length

    def get_position(self) -> int:
        """!
        
        @returns The position of the stream.
        @exception IOError: Thrown if there is an IO error.
        """
        return self.__position

    def set_position(self, value: int):
        """!
        Changes the current position of the stream. For more options use seek() method.
        @param value: The new position of the stream.
        @exception IOError: Thrown if there is an IO error.
        """
        self.__position = value

    def set_length(self, value: int):
        """!
        Changes the length of the stream. The capacity of the stream might also change
        if the value is lesser than the current capacity.
        @param value:         @exception IOError: Thrown if there is an IO error.
        """
        self.__check_and_resize(value)
        self.__capacity = value

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """!
        Read a sequence of bytes into the provided buffer.
        @param buffer: The buffer to write the bytes that are read from the stream.
        @param offset: The offset of the buffer that will be used to write the bytes.
        @param count: The length of the bytes that can be read from the stream and written to the buffer.
        @returns The bytes read
        @exception IOError: Thrown if there is an IO error.
        """
        bytes_read: int = int(min(self.__length - self.get_position(), count))
        buffer[offset:offset + bytes_read] = self.__bytes[self.__position:self.__position + bytes_read]
        self.set_position(self.get_position() + bytes_read)
        if bytes_read <= 0:
            return -1
        return bytes_read

    def write(self, buffer: bytearray, offset: int, count: int):
        """!
        Write a sequence of bytes into the stream.
        @param buffer: The buffer that the bytes will be read from.
        @param offset: The position offset that will be used to read from the buffer.
        @param count: The number of bytes that will be written to the stream.
        @exception IOError: Thrown if there is an IO error.
        """
        self.__check_and_resize(self.__position + count)
        self.__bytes[self.__position:self.__position + count] = buffer[offset:offset + count]
        self.set_position(self.get_position() + count)

    def __check_and_resize(self, new_length: int):
        """!
        Check if there is no more space in the byte array and increase the capacity.
        @param new_length: The new length of the stream.
        """
        if self.__capacity < new_length:
            new_capacity: int = self.__capacity + MemoryStream.__CAPACITY_INCREMENT * (
                    (new_length - self.__capacity) // MemoryStream.__CAPACITY_INCREMENT)
            if new_capacity < new_length:
                new_capacity += MemoryStream.__CAPACITY_INCREMENT
            n_bytes: bytearray = bytearray(new_capacity)
            n_bytes[0:self.__capacity] = self.__bytes[0:self.__capacity]
            self.__capacity = new_capacity
            self.__bytes = n_bytes
        self.__length = new_length

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """!
        Seek to a position in the stream.
        @param offset: The offset
        @param origin: Possible Values: Begin, Current, End
        @returns The current position after seeking
        @exception IOError: Thrown if there is an IO error.
        """
        n_pos: int = 0
        if origin == RandomAccessStream.SeekOrigin.Begin:
            n_pos = offset
        elif origin == RandomAccessStream.SeekOrigin.Current:
            n_pos = self.get_position() + offset
        elif origin == RandomAccessStream.SeekOrigin.End:
            n_pos = len(self.__bytes) - offset
        self.__check_and_resize(n_pos)
        self.set_position(n_pos)
        return self.get_position()

    def flush(self):
        """!
        Flush the stream. Not-Applicable for memory stream.
        """
        pass

    def close(self):
        """!
        Close any resources the stream is using. Not-Applicable for memory stream.
        """
        pass

    def to_array(self) -> bytearray:
        """!
        Convert the stream to an array:
        @returns A byte array containing the data from the stream.
        """
        n_bytes: bytearray = bytearray(self.__length)
        n_bytes[0:self.__length] = self.__bytes[0:self.__length]
        return n_bytes
