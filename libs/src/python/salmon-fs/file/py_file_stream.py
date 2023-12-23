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
from typing import BinaryIO

from file.py_file import PyFile
from iostream.random_access_stream import RandomAccessStream


class PyFileStream(RandomAccessStream):
    """
     * An advanced Salmon File Stream implementation for python files.
     * This class is used internally for random file access of physical (real) files.
    """

    def __init__(self, file: PyFile, mode: str):
        """
         * Construct a file stream from an AndroidFile.
         * This will create a wrapper stream that will route read() and write() to the FileChannel
         *
         * @param file The AndroidFile that will be used to get the read/write stream
         * @param mode The mode "r" for read "rw" for write
        """

        self.__raf: BinaryIO | None = None
        """
         * The random access file associated with this stream.
        """

        self.__file: PyFile | None = None
        """
         * The python file associated with this stream.
        """

        self.__canWrite: bool = False

        self.__file = file
        if mode == "r":
            self.__canWrite = True

        self.__raf: BinaryIO = open(file.get_path(), mode + "b")

    def can_read(self) -> bool:
        """
         * True if stream can read from file.
         * @return
        """
        return self.fileChannel.isOpen() and not self.__canWrite

    def can_write(self) -> bool:
        """
         * True if stream can write to file.
         * @return
        """
        return self.fileChannel.isOpen() and self.__canWrite

    def can_seek(self) -> bool:
        """
         * True if stream can seek.
         * @return
        """
        return True

    def length(self) -> int:
        """
         * Get the length of the stream. This is the same as the backed file.
         * @return
        """
        return self.file.length()

    def get_position(self) -> int:
        return self.fileChannel.__position()

    def set_position(self, value: int):
        """
         * Set the current position of the stream.
         * @param value The new position.
         * @throws IOError
        """
        self.fileChannel.set_position(value)

    def set_length(self, value: int):
        self.fileChannel.__position(value)

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
         * Read data from the file stream into the buffer provided.
         * @param buffer The buffer to write the data.
         * @param offset The offset of the buffer to start writing the data.
         * @param count The maximum number of bytes to read from.
         * @return
         * @throws IOError
        """
        bytes_read: int = self.readinto(memoryview(buffer)[offset:offset + count])
        if bytes_read <= 0:
            return -1
        return bytes_read

    def write(self, buffer: bytearray, offset: int, count: int):
        """
         * Write the data from the buffer provided into the stream.
         * @param buffer The buffer to read the data from.
         * @param offset The offset of the buffer to start reading the data.
         * @param count The maximum number of bytes to read from the buffer.
         * @throws IOError
        """
        self.__raf.write(buffer[offset:offset + count])

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """
         * Seek to the offset provided.
         * @param offset The position to seek to.
         * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
         * @return The new position after seeking.
         * @throws IOError
        """
        pos: int = self.fileChannel.__position()
        if origin == RandomAccessStream.SeekOrigin.Begin:
            pos = offset
        elif origin == RandomAccessStream.SeekOrigin.Current:
            pos += offset
        elif origin == RandomAccessStream.SeekOrigin.End:
            pos = self.file.length() - offset

        self.fileChannel.__position(pos)
        return self.fileChannel.__position()

    def flush(self):
        """
         * Flush the buffers to the associated file.
        """
        try:
            if self.fileChannel.isOpen():
                self.fileChannel.force(True)

        except Exception as ex:
            print(ex)

    def close(self):
        """
         * Close this stream and associated resources.
         * @throws IOError
        """
        self.fileChannel.close()
        self.fileChannel.close()
        self.raf.close()