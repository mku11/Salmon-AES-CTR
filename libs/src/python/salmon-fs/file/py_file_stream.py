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
from mmap import mmap
from threading import RLock
from typing import BinaryIO

from typeguard import typechecked

from file.ireal_file import IRealFile
from iostream.random_access_stream import RandomAccessStream


@typechecked
class PyFileStream(RandomAccessStream):
    """
     * An advanced Salmon File Stream implementation for python files.
     * This class is used internally for random file access of physical (real) files.
    """

    __lock = RLock()
    """
    Global lock for resizing files. 
    """

    def __init__(self, file: IRealFile, mode: str):
        """
         * Construct a file stream from an AndroidFile.
         * This will create a wrapper stream that will route read() and write() to the file
         *
         * @param file The AndroidFile that will be used to get the read/write stream
         * @param mode The mode "r" for read "rw" for write
        """

        self.__raf: BinaryIO | None = None
        """
         * The random access file associated with this stream.
        """

        self.__mm: mmap | None = None
        """
         * mapped memory file for random access
        """

        self.__file: IRealFile | None = None
        """
         * The python file associated with this stream.
        """

        self.__canWrite: bool = False

        self.__file = file
        if mode == "rw":
            self.__canWrite = True
            if self.__file.exists() and self.__file.length() > 0:
                mode = "a+"
            else:
                mode = "w+"

        self.__raf: BinaryIO = open(self.__file.get_path(), mode + "b")
        if mode == "a+":
            self.__mm = mmap(self.__raf.fileno(), 0)
            self.__mm.seek(0)

    def can_read(self) -> bool:
        """
         * True if stream can read from file.
         * @return
        """
        return not self.__canWrite

    def can_write(self) -> bool:
        """
         * True if stream can write to file.
         * @return
        """
        return self.__canWrite

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
        return self.__file.length()

    def get_position(self) -> int:
        return self.__mm.tell() if self.__mm else self.__raf.tell()

    def set_position(self, value: int):
        """
         * Set the current position of the stream.
         * @param value The new position.
         * @throws IOError
        """
        self.seek(value, RandomAccessStream.SeekOrigin.Begin)

    def set_length(self, value: int):
        # with (PyFileStream.__lock):
        self.__raf.truncate(value)

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
         * Read data from the file stream into the buffer provided.
         * @param buffer The buffer to write the data.
         * @param offset The offset of the buffer to start writing the data.
         * @param count The maximum number of bytes to read from.
         * @return
         * @throws IOError
        """
        buff: bytes = self.__raf.read(count)
        bytes_read: int = len(buff)
        buffer[offset:offset + bytes_read] = buff[:]
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
        print("write: " + str(self.get_position()) + " - " + str(self.get_position() + count))
        if self.__mm:
            if self.get_position() + count > self.__file.length():
                print("write resize: " + str(self.__file.length()) + " new: " + str(self.get_position() + count))
                self.__resize(self.get_position() + count)
            self.__mm.write(buffer[offset:offset + count])
        else:
            self.__raf.write(buffer[offset:offset + count])

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """
         * Seek to the offset provided.
         * @param offset The position to seek to.
         * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
         * @return The new position after seeking.
         * @throws IOError
        """
        pos: int = self.__mm.tell() if self.__mm else self.__raf.tell()
        if origin == RandomAccessStream.SeekOrigin.Begin:
            pos = offset
        elif origin == RandomAccessStream.SeekOrigin.Current:
            pos += offset
        elif origin == RandomAccessStream.SeekOrigin.End:
            pos = self.__file.length() - offset

        if self.__mm and pos > self.__file.length():
            print("seek resize: " + str(self.__file.length()) + " new: " + str(pos))
            self.__resize(pos)

        self.__mm.seek(pos) if self.__mm else self.__raf.seek(pos)
        return self.__mm.tell() if self.__mm else self.__raf.tell()

    def flush(self):
        """
         * Flush the buffers to the associated file.
        """
        try:
            self.__mm.flush() if self.__mm else self.__raf.flush()
        except Exception as ex:
            print(ex)

    def close(self):
        """
         * Close this stream and associated resources.
         * @throws IOError
        """
        if self.__mm:
            self.__mm.close()
        self.__raf.close()

    def __resize(self, value: int):
        self.set_length(value)
        if self.__mm:
            pos: int = self.__mm.tell()
            self.__mm.flush()
            self.__mm.close()
            self.__mm = mmap(self.__raf.fileno(), 0)
            self.__mm.seek(pos)
