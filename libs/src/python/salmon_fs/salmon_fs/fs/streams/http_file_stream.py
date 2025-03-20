#!/usr/bin/env python3
"""!@brief Stream implemetation for HTTP files.
"""

__license__ = """
MIT License

Copyright (c) 2025 Max Kas

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
import http.client
from http.client import HTTPResponse, HTTPConnection, HTTPSConnection
from urllib.parse import urlparse
from urllib.parse import urljoin
from wrapt import synchronized

from salmon_fs.fs.file.ifile import IFile
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class HttpFileStream(RandomAccessStream):
    """!
    Stream implemetation for HTTP files.
    This class is used internally for random file access of physical (real) files.
    """

    MAX_REDIRECTS: int = 5

    def __init__(self, file: IFile, mode: str):
        """!
        Construct a file stream from an AndroidFile.
        This will create a wrapper stream that will route read() and write() to the file
        
        @param file: The file that will be used to get the read/write stream
        @param mode: The mode "r" for read "rw" for write
        """

        self.__file: IFile | None = file
        """
        The python file associated with this stream.
        """

        self.position: int = 0
        if mode == "rw":
            raise Exception("Unsupported Operation, readonly filesystem")

        self.__canWrite: bool = mode == "rw"
        self.conn: HTTPConnection | HTTPSConnection | None = None
        self.__response: HTTPResponse | None = None
        self.closed: bool = False

    def __get_input_response(self) -> HTTPResponse:
        if self.closed:
            raise IOError("Stream is closed")
        if not self.__response:
            headers = {}
            self.__set_default_headers(headers)
            if self.position > 0:
                headers['Range'] = "bytes=" + str(self.position) + "-"
            url = self.__file.get_path()
            while count := HttpFileStream.MAX_REDIRECTS:
                self.conn = self.__create_connection(urlparse(url).netloc)
                self.conn.request("GET", urlparse(url).path, headers=headers)
                self.__response = self.conn.getresponse()
                if self.__response.getheader('location'):
                    url = urljoin(url, self.__response.getheader('location'))
                    count -= 1
                    self.__response.close()
                    self.conn.close()
                else:
                    break
            self.__check_status(self.__response, 206 if self.position > 0 else 200)
        return self.__response

    def can_read(self) -> bool:
        """!
        True if stream can read from file.
        @returns True if readable
        """
        return True

    def can_write(self) -> bool:
        """!
        True if stream can write to file.
        @returns True if writable
        """
        return False

    def can_seek(self) -> bool:
        """!
        True if stream can seek.
        @returns True if seekable
        """
        return True

    def get_length(self) -> int:
        """!
        Get the length of the stream. This is the same as the backed file.
        @returns The length
        """
        return self.__file.get_length()

    def get_position(self) -> int:
        return self.position

    def set_position(self, value: int):
        """!
        Set the current position of the stream.
        @param value: The new position.
        @exception IOError: Thrown if there is an IO error.
        """
        if self.position != value:
            self.reset()
        self.position = value

    def set_length(self, value: int):
        raise Exception("Unsupported Operation, readonly filesystem")

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """!
        Read data from the file stream into the buffer provided.
        @param buffer: The buffer to write the data.
        @param offset: The offset of the buffer to start writing the data.
        @param count: The maximum number of bytes to read from.
        @returns The bytes read
        @exception IOError: Thrown if there is an IO error.
        """
        bytes_read: int = 0
        while bytes_read < count:
            buff: bytes = self.__get_input_response().read(count - bytes_read)
            if not buff or len(buff) == 0:
                break
            buffer[offset + bytes_read:offset + bytes_read + len(buff)] = buff[:]
            bytes_read += len(buff)
        if bytes_read <= 0:
            return -1
        self.position += bytes_read
        return bytes_read

    def write(self, buffer: bytearray, offset: int, count: int):
        """!
        Write the data from the buffer provided into the stream.
        @param buffer: The buffer to read the data from.
        @param offset: The offset of the buffer to start reading the data.
        @param count: The maximum number of bytes to read from the buffer.
        @exception IOError: Thrown if there is an IO error.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """!
        Seek to the offset provided.
        @param offset: The position to seek to.
        @param origin: The type of origin {@link RandomAccessStream.SeekOrigin}
        @returns The new position after seeking.
        @exception IOError: Thrown if there is an IO error.
        """
        pos: int = self.position
        if origin == RandomAccessStream.SeekOrigin.Begin:
            pos = offset
        elif origin == RandomAccessStream.SeekOrigin.Current:
            pos += offset
        elif origin == RandomAccessStream.SeekOrigin.End:
            pos = self.__file.get_length() - offset

        self.set_position(pos)
        return self.position

    def flush(self):
        """!
        Flush the buffers to the associated file.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def close(self):
        """!
        Close this stream and associated resources.
        @exception IOError: Thrown if there is an IO error.
        """
        self.reset()
        self.closed = True

    @synchronized
    def reset(self):
        """!
        Reset the stream.
        @exception IOError: Thrown if there is an IO error.
        """
        if self.__response:
            self.__response.close()
        if self.conn:
            self.conn.close()
        self.conn = None
        self.__response = None

    def __check_status(self, http_response: HTTPResponse, status: int):
        if http_response.status != status:
            raise IOError(str(http_response.status)
                          + " " + http_response.reason)

    def __set_default_headers(self, headers: dict[str, str]):
        headers["Cache"] = "no-store"
        headers["Connection"] = "keep-alive"
        headers["Content-type"] = "application/x-www-form-urlencoded"

    def __create_connection(self, host):
        conn = None
        scheme = urlparse(self.__file.get_path()).scheme
        if scheme == "http":
            conn = http.client.HTTPConnection(host)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(host)
        return conn
