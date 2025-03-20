#!/usr/bin/env python3
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
from queue import Queue
from threading import Thread
import http.client
from http.client import HTTPResponse, HTTPConnection, HTTPSConnection
import urllib
from urllib import parse
from urllib.parse import urlparse
from io import RawIOBase

from salmon_core.convert.base_64 import Base64
from salmon_fs.fs.file.ifile import IFile
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class WSFileStream(RandomAccessStream):
    """
    An advanced Salmon File Stream implementation for python web service files.
    This class is used internally for random file access of physical (real) files.
    """
    __PATH: str = "path"
    __POSITION: str = "position"
    __LENGTH: str = "length"

    def __init__(self, file: IFile, mode: str):
        """
        Construct a file stream from an AndroidFile.
        This will create a wrapper stream that will route read() and write() to the file

        :param file: The file that will be used to get the read/write stream
        :param mode: The mode "r" for read "rw" for write
        """

        self.__file: IFile | None = file
        """
        The python file associated with this stream.
        """

        self.position: int = 0

        self.__canWrite: bool = mode == "rw"
        self.__response: HTTPResponse | None = None
        self.conn: HTTPConnection | HTTPSConnection | None = None
        self.queue: Queue | None = None
        self.start_position: int = 0
        self.upload_thread: Thread | None = None
        self.closed: bool = False

    def get_input_response(self) -> HTTPResponse:
        if self.closed:
            raise IOError("Stream is closed")
        if not self.__response:
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({WSFileStream.__PATH: self.__file.get_path(),
                                             WSFileStream.__POSITION: self.position})
            self.conn = self.__create_connection()
            self.conn.request("GET", "/api/get" + "?" + params, headers=headers)
            self.__response = self.conn.getresponse()
            self.__check_status(self.__response, 206 if self.position > 0 else 200)
        return self.__response

    def get_output_queue(self) -> Queue:
        if self.closed:
            raise IOError("Stream is closed")
        if not self.queue:
            self.start_position: int = self.get_position()
            boundary = "*******"
            header = "--" + boundary + "\r\n"
            header += "Content-Disposition: form-data; name=\"file\"; filename=\"" \
                      + self.__file.get_name() + "\"\r\n"
            header += "\r\n"
            header_data = bytearray(header.encode())

            footer = "\r\n--" + boundary + "--"
            footer_data: bytearray = bytearray(footer.encode())

            class PipedIO(RawIOBase):
                def __init__(self, queue: Queue):
                    self.is_closed = False
                    self.buff: bytearray | None = None
                    self.queue = queue

                def read(self, size: int | None = ...) -> bytearray | None:
                    if self.is_closed:
                        return None
                    if self.buff and len(self.buff) > 0:
                        buf = self.buff[0:size]
                        self.buff = self.buff[size:]
                        return buf
                    buffer: bytearray = bytearray()
                    while (message := self.queue.get()):
                        buffer.extend(message)
                        if len(buffer) > size:
                            self.buff = buffer[size:]
                            buffer = buffer[0:size]
                            break
                    if message is None:
                        buffer.extend(footer_data)
                        self.is_closed = True
                    return buffer

            self.queue = Queue()
            self.queue.put(header_data)
            data = PipedIO(self.queue)

            headers = {}
            self.__set_default_headers(headers)
            headers["Content-type"] = "multipart/form-data;boundary=" + boundary
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({WSFileStream.__PATH: self.__file.get_path(),
                                             WSFileStream.__POSITION: self.start_position})
            self.conn = self.__create_connection()

            def start_upload():
                self.conn.request("POST", "/api/upload" + "?" + params, headers=headers, body=data)
                self.__response = self.conn.getresponse()

            self.upload_thread = Thread(target=start_upload)
            self.upload_thread.start()

        return self.queue

    def can_read(self) -> bool:
        """
        True if stream can read from file.
        :return: True if readable
        """
        return not self.__canWrite

    def can_write(self) -> bool:
        """
        True if stream can write to file.
        :return: True if writable
        """
        return self.__canWrite

    def can_seek(self) -> bool:
        """
        True if stream can seek.
        :return: True if seekable
        """
        return True

    def get_length(self) -> int:
        """
        Get the length of the stream. This is the same as the backed file.
        :return: The length
        """
        return self.__file.get_length()

    def get_position(self) -> int:
        """
        Get the position of the stream
        :returns The position
        """
        return self.position

    def set_position(self, value: int):
        """
        Set the current position of the stream.
        :param value: The new position.
        :raises IOError: Thrown if there is an IO error.
        """
        if self.position != value:
            self.reset()
        self.position = value

    def set_length(self, value: int):
        """
        Set the length of the stream
        """
        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({WSFileStream.__PATH: self.__file.get_path(),
                                         WSFileStream.__LENGTH: str(value)})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("PUT", "/api/setLength", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
            return True
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
        Read data from the file stream into the buffer provided.
        :param buffer: The buffer to write the data.
        :param offset: The offset of the buffer to start writing the data.
        :param count: The maximum number of bytes to read from.
        :return: The bytes read
        :raises IOError: Thrown if there is an IO error.
        """
        bytes_read: int = 0
        while bytes_read < count:
            buff: bytes = self.get_input_response().read(count - bytes_read)
            if not buff or len(buff) == 0:
                break
            buffer[offset + bytes_read:offset + bytes_read + len(buff)] = buff[:]
            bytes_read += len(buff)
        if bytes_read <= 0:
            return -1
        self.position += bytes_read
        return bytes_read

    def write(self, buffer: bytearray, offset: int, count: int):
        """
        Write the data from the buffer provided into the stream.
        :param buffer: The buffer to read the data from.
        :param offset: The offset of the buffer to start reading the data.
        :param count: The maximum number of bytes to read from the buffer.
        :raises IOError: Thrown if there is an IO error.
        """
        buff = buffer[offset:offset + count]
        queue = self.get_output_queue()
        queue.put(buff)
        self.position += len(buff)

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """
        Seek to the offset provided.
        :param offset: The position to seek to.
        :param origin: The type of origin {@link RandomAccessStream.SeekOrigin}
        :return: The new position after seeking.
        :raises IOError: Thrown if there is an IO error.
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
        """
        Flush the buffers to the associated file.
        """
        # TODO: flush network upload before queueing more?
        pass

    def close(self):
        """
        Close this stream and associated resources.
        :raises IOError: Thrown if there is an IO error.
        """
        self.reset()
        self.closed = True

    def reset(self):
        """
        Reset the stream
        """
        if self.can_write():
            if self.queue:
                self.queue.put(None)
            if self.upload_thread:
                self.upload_thread.join()
            if self.__response:
                self.__check_status(self.__response, 206 if self.start_position > 0 else 200)
                self.__response.close()
            if self.conn:
                self.conn.close()
        else:
            if self.__response:
                self.__response.close()
            if self.conn:
                self.conn.close()

        self.conn = None
        self.__response = None
        self.queue = None
        self.upload_thread = None
        self.start_position = 0
        self.__file.reset()

    def set_service_auth(self, headers: dict[str, str]):
        if not self.__file.get_credentials():
            return
        headers['Authorization'] = 'Basic ' + Base64().encode(
            bytearray((self.__file.get_credentials().get_service_user() + ":"
                       + self.__file.get_credentials().get_service_password())
                      .encode('utf-8')))

    def __check_status(self, http_response: HTTPResponse, status: int):
        if http_response.status != status:
            raise IOError(str(http_response.status)
                          + " " + http_response.reason)

    def __set_default_headers(self, headers: dict[str, str]):
        headers["Cache"] = "no-store"
        headers["Connection"] = "keep-alive"
        headers["Content-type"] = "application/x-www-form-urlencoded"

    def __create_connection(self) -> HTTPConnection | HTTPSConnection:
        conn = None
        scheme = urlparse(self.__file.get_service_path()).scheme
        if scheme == "http":
            conn = http.client.HTTPConnection(urlparse(self.__file.get_service_path()).netloc)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(urlparse(self.__file.get_service_path()).netloc)
        return conn
