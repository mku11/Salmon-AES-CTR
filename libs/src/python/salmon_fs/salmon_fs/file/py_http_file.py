#!/usr/bin/env python3
'''
MIT License

Copyright (c) 2025 Max Kas

Permission is hereby granted, free of charge, to Any person obtaining a copy
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
import http.client
from urllib.parse import urlparse
from http.client import HTTPResponse, HTTPConnection, HTTPSConnection
from typing import Any, Callable
import re
from typeguard import typechecked
from wrapt import synchronized
import urllib
from urllib.parse import urljoin

from salmon_core.streams.memory_stream import MemoryStream
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.py_http_file_stream import PyHttpFileStream
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class PyHttpFile(IRealFile):
    """
    Salmon Http (read only) File implementation for Python.
    """

    MAX_REDIRECTS: int = 5
    separator: str = "/"

    def __init__(self, path: str):
        """
        Instantiate a real file represented by the filepath and service path provided.
        :param path: The filepath.
        """
        self.__file_path: str = path
        self.__response: HTTPResponse | None = None

    @synchronized
    def __get_response(self) -> HTTPResponse:
        if not self.__response:
            headers = {}
            self.__set_default_headers(headers)
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                url = self.__file_path
                while count := PyHttpFile.MAX_REDIRECTS:
                    conn = self.__create_connection(urlparse(url).netloc)
                    conn.request("GET", url, headers=headers)
                    self.__response = conn.getresponse()
                    if self.__response.getheader('location'):
                        url = urljoin(url, self.__response.getheader('location'))
                        count -= 1
                        self.__response.close()
                        conn.close()
                    else:
                        break
                self.__check_status(self.__response, 200)
            finally:
                if conn:
                    conn.close()
                if self.__response:
                    self.__response.close()
        return self.__response

    def create_directory(self, dir_name: str) -> IRealFile:
        """
        Create a directory under this directory.
        :param dir_name: The name of the new directory.
        :return: The newly created directory.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def create_file(self, filename: str) -> IRealFile:
        """
        Create a file under this directory.
        :param filename: The name of the new file.
        :return: The newly created file.
        :raises IOError: Thrown if there is an IO error.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def delete(self) -> bool:
        """
        Delete this file or directory.
        :return: True if deletion is successful.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def exists(self) -> bool:
        """
        True if file or directory exists.
        :return: True if exists
        """
        return self.__get_response().status == 200 or self.__get_response().status == 206

    def get_absolute_path(self) -> str:
        """
        Get the absolute path on the physical disk.
        :return: The absolute path.
        """
        return self.__file_path

    def get_base_name(self) -> str:
        """
        Get the name of this file or directory.
        :return: The name of this file or directory.
        """
        if not self.__file_path:
            raise Exception("Filepath is not assigned")
        n_filepath = self.__file_path
        if n_filepath.endswith("/"):
            n_filepath = n_filepath[0:-1]
        basename: str | None = n_filepath.split(PyHttpFile.separator).pop()
        if not basename:
            raise Exception("Could not get basename")
        if "%" in basename:
            basename = urllib.parse.unquote(basename)
        return basename

    def get_input_stream(self) -> RandomAccessStream:
        """
        Get a stream for reading the file.
        :return: The stream to read from.
        :raises FileNotFoundException:     """
        return PyHttpFileStream(self, "r")

    def get_output_stream(self) -> RandomAccessStream:
        """
        Get a stream for writing to this file.
        :return: The stream to write to.
        :raises FileNotFoundException:         """
        raise Exception("Unsupported Operation, readonly filesystem")

    def get_parent(self) -> IRealFile:
        """
        Get the parent directory of this file or directory.
        :return: The parent directory.
        """
        path: str = self.__file_path
        if path.endswith(PyHttpFile.separator):
            path = path[0:-1]

        parent_file_path: str = path[0:path.rindex(PyHttpFile.separator)]
        return PyHttpFile(parent_file_path)

    def get_path(self) -> str:
        """
        Get the path of this file. For python this is the same as the absolute filepath.
        :return: The path
        """
        return self.__file_path

    def is_directory(self) -> bool:
        """
        True if this is a directory.
        :return: True if directory
        """
        res: HTTPResponse = self.__get_response()
        if not res:
            raise Exception("Could not get response")
        content_type: str | None = res.getheader("Content-Type")
        if not content_type:
            raise Exception("Could not get content type")
        return content_type.startswith("text/html")

    def is_file(self) -> bool:
        """
        True if this is a file.
        :return: True if file
        """
        return not self.is_directory()

    def last_modified(self) -> int:
        """
        Get the last modified date on disk.
        :return: The last modified date in milliseconds
        """
        return self.__get_response().lastModified

    def length(self) -> int:
        """
        Get the size of the file on disk.
        :return: The size
        """
        return self.__get_response().length

    def get_children_count(self) -> int:
        """
        Get the count of files and subdirectories
        :return: The children
        """
        return len(self.list_files())

    def list_files(self) -> list[IRealFile]:
        """
        List all files under this directory.
        :return: The list of files.
        """
        if self.is_directory():
            ms: MemoryStream = MemoryStream()
            stream: RandomAccessStream | None = None
            try:
                files: list[IRealFile] = []
                stream = self.get_input_stream()
                stream.copy_to(ms)
                contents: str = ms.to_array().decode()
                matches = re.findall("HREF=\"(.+?)\"", contents, flags=re.I)
                for match in matches:
                    filename: str = match
                    if ":" in filename or ".." in filename:
                        continue
                    if "%" in filename:
                        filename = urllib.parse.unquote(filename)
                    p_file: PyHttpFile = PyHttpFile(self.__file_path + PyHttpFile.separator + filename)
                    files.append(p_file)
                return files
            finally:
                if ms:
                    ms.close()
                if stream:
                    stream.close()
        return []

    def move(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: Callable[[int, int], Any] | None = None) -> IRealFile:
        """
        Move this file or directory under a new directory.
        :param new_dir: The target directory.
        :param new_name: The new filename
        :param progress_listener: Observer to notify when progress changes.
        :return: The moved file. Use this file for subsequent operations instead of the original.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def copy(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: Callable[[int, int], Any] | None = None) -> IRealFile:
        """
        Move this file or directory under a new directory.
        :param new_dir:    The target directory.
        :param new_name:   New filename
        :param progress_listener: Observer to notify when progress changes.
        :return: The copied file. Use this file for subsequent operations instead of the original.
        :raises IOError: Thrown if there is an IO error.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def get_child(self, filename: str) -> IRealFile | None:
        """
        Get the file or directory under this directory with the provided name.
        :param filename: The name of the file or directory.
        :return: The child file
        """
        if self.is_file():
            return None
        n_filepath: str = self.get_child_path(filename)
        child: PyHttpFile = PyHttpFile(n_filepath)
        return child

    def rename_to(self, new_filename: str) -> bool:
        """
        Rename the current file or directory.
        :param new_filename: The new name for the file or directory.
        :return: True if successfully renamed.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def mkdir(self) -> bool:
        """
        Create this directory under the current filepath.
        :return: True if created.
        """
        raise Exception("Unsupported Operation, readonly filesystem")

    def get_child_path(self, filename: str) -> str:
        n_filepath = self.__file_path
        if not n_filepath.endswith(PyHttpFile.separator):
            n_filepath += PyHttpFile.separator
        n_filepath += filename
        return n_filepath

    def __str__(self) -> str:
        """
        Returns a string representation of this object
        """
        return self.__file_path

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
        scheme = urlparse(self.__file_path).scheme
        if scheme == "http":
            conn = http.client.HTTPConnection(host)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(host)
        return conn
