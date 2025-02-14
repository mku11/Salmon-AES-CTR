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
import os
import json
import http.client
import urllib.parse
from urllib.parse import urlparse
from http.client import HTTPResponse, HTTPConnection, HTTPSConnection
from typing import Any, Callable

from typeguard import typechecked

from salmon_core.convert.base_64 import Base64
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.py_ws_file_stream import PyWSFileStream
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class PyWSFile(IRealFile):
    """
    Salmon Web Service File implementation for Python.
    """

    __PATH: str = "path"
    __DEST_DIR: str = "destDir"
    __FILENAME: str = "filename"
    separator: str = "/"

    def get_service_path(self):
        return self.__service_path

    def get_credentials(self):
        return self.__credentials

    def set_credentials(self, credentials: Credentials):
        self.__credentials = credentials

    def __init__(self, path: str, service_path: str, credentials: Credentials):
        """
        Instantiate a real file represented by the filepath and service path provided.
        :param path: The filepath.
        """
        if not path.startswith(PyWSFile.separator):
            path = PyWSFile.separator + path
        self.__file_path: str = path
        self.__service_path: str = service_path
        self.__credentials: Credentials = credentials
        self.__response: HTTPResponse | None = None

    def __get_response(self) -> HTTPResponse:
        if not self.__response:
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({PyWSFile.__PATH: self.__file_path})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("GET", "/api/info" + "?" + params, headers=headers)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                self.__response = json.loads(http_response.read())
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()
        return self.__response

    def create_directory(self, dir_name: str) -> IRealFile:
        """
        Create a directory under this directory.
        :param dir_name: The name of the new directory.
        :return: The newly created directory.
        """
        n_dir_path: str = self.get_child_path(dir_name)
        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({PyWSFile.__PATH: n_dir_path})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("POST", "/api/mkdir", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()
        ndir: PyWSFile = PyWSFile(n_dir_path, self.__service_path, self.__credentials)
        return ndir

    def create_file(self, filename: str) -> IRealFile:
        """
        Create a file under this directory.
        :param filename: The name of the new file.
        :return: The newly created file.
        :raises IOError: Thrown if there is an IO error.
        """
        n_filepath: str = self.get_child_path(filename)
        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({PyWSFile.__PATH: n_filepath})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("POST", "/api/create", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()
        n_file: PyWSFile = PyWSFile(n_filepath, self.__service_path, self.__credentials)
        return n_file

    def delete(self) -> bool:
        """
        Delete this file or directory.
        :return: True if deletion is successful.
        """
        if self.is_directory():
            p_files: list[IRealFile] = self.list_files()
            for p_file in p_files:
                headers = {}
                self.__set_default_headers(headers)
                self.set_service_auth(headers)
                params = urllib.parse.urlencode({PyWSFile.__PATH: p_file.get_path()})
                conn: HTTPConnection | HTTPSConnection | None = None
                http_response: HTTPResponse | None = None
                try:
                    conn = self.__create_connection()
                    conn.request("DELETE", "/api/delete", headers=headers, body=params)
                    http_response = conn.getresponse()
                    self.__check_status(http_response, 200)
                finally:
                    if conn:
                        conn.close()
                    if http_response:
                        http_response.close()

        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({PyWSFile.__PATH: self.__file_path})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("DELETE", "/api/delete", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()
        return not os.path.exists(self.__file_path)

    def exists(self) -> bool:
        """
        True if file or directory exists.
        :return: True if exists
        """
        return self.__get_response()['present']

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
        basename: str | None = n_filepath.split(PyWSFile.separator).pop()
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
        return PyWSFileStream(self, "r")

    def get_output_stream(self) -> RandomAccessStream:
        """
        Get a stream for writing to this file.
        :return: The stream to write to.
        :raises FileNotFoundException:         """
        self.__response = None
        return PyWSFileStream(self, "rw")

    def get_parent(self) -> IRealFile:
        """
        Get the parent directory of this file or directory.
        :return: The parent directory.
        """
        path: str = self.__file_path
        if path.endswith(PyWSFile.separator):
            path = path[0:-1]

        parent_file_path: str = path[0:path.rindex(PyWSFile.separator)]
        return PyWSFile(parent_file_path, self.__service_path, self.__credentials)

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
        return self.__get_response()['directory']

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
        return self.__get_response()['lastModified']

    def length(self) -> int:
        """
        Get the size of the file on disk.
        :return: The size
        """
        return self.__get_response()['length']

    def get_children_count(self) -> int:
        """
        Get the count of files and subdirectories
        :return: The children
        """
        if self.is_directory():
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({PyWSFile.__PATH: self.get_path()})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("GET", "/api/list" + "?" + params, headers=headers)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                contents = http_response.read()
                return len(json.loads(contents))
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()
        return 0

    def list_files(self) -> list[IRealFile]:
        """
        List all files under this directory.
        :return: The list of files.
        """
        if self.is_directory():
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({PyWSFile.__PATH: self.get_path()})
            conn: HTTPConnection | HTTPSConnection | None = None
            try:
                conn = self.__create_connection()
                conn.request("GET", "/api/list" + "?" + params, headers=headers)
                response = conn.getresponse()
                self.__check_status(response, 200)
                contents = response.read()
                files: list[Any] = json.loads(contents)
                real_files: list[PyWSFile] = []
                real_dirs: list[PyWSFile] = []

                for file in files:
                    p_file: PyWSFile = PyWSFile(file['path'], self.get_service_path(), self.get_credentials())
                    if p_file.is_directory():
                        real_dirs.append(p_file)
                    else:
                        real_files.append(p_file)

                real_dirs.extend(real_files)
                return real_dirs
            finally:
                if conn:
                    conn.close()
                if response:
                    response.close()
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
        new_name = new_name if new_name is not None else self.get_base_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exist")

        new_file: IRealFile | None = new_dir.get_child(new_name)
        if new_file is not None and new_file.exists():
            raise IOError("Another file/directory already exists")

        if self.is_directory():
            raise IOError("Could not move directory use IRealFile moveRecursively() instead")
        else:
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({PyWSFile.__PATH: self.get_path(),
                                             PyWSFile.__DEST_DIR: new_dir.get_path(),
                                             PyWSFile.__FILENAME: new_name})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("PUT", "/api/move", headers=headers, body=params)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                new_file = PyWSFile(json.loads(http_response.read())['path'], self.__service_path, self.__credentials)
                self.__response = None
                return new_file
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()

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
        new_name = new_name if new_name is not None else self.get_base_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exists")
        new_file: IRealFile | None = new_dir.get_child(new_name)
        if new_file is not None and new_file.exists():
            raise IOError("Another file/directory already exists")
        if self.is_directory():
            raise IOError("Could not copy directory use IRealFile copyRecursively() instead")
        else:
            headers = {}
            self.__set_default_headers(headers)
            self.set_service_auth(headers)
            params = urllib.parse.urlencode({PyWSFile.__PATH: self.get_path(),
                                             PyWSFile.__DEST_DIR: new_dir.get_path(),
                                             PyWSFile.__FILENAME: new_name})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("POST", "/api/copy", headers=headers, body=params)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                self.__response = None
                new_file = PyWSFile(json.loads(http_response.read())['path'], self.__service_path, self.__credentials)
                return new_file
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()

    def get_child(self, filename: str) -> IRealFile | None:
        """
        Get the file or directory under this directory with the provided name.
        :param filename: The name of the file or directory.
        :return: The child file
        """
        if self.is_file():
            return None
        n_filepath: str = self.get_child_path(filename)
        child: PyWSFile = PyWSFile(n_filepath, self.__service_path, self.__credentials)
        return child

    def rename_to(self, new_filename: str) -> bool:
        """
        Rename the current file or directory.
        :param new_filename: The new name for the file or directory.
        :return: True if successfully renamed.
        """
        self.__response = None
        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({PyWSFile.__PATH: self.get_path(),
                                         PyWSFile.__FILENAME: new_filename})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("PUT", "/api/rename", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
            return True
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()

    def mkdir(self) -> bool:
        """
        Create this directory under the current filepath.
        :return: True if created.
        """
        self.__response = None
        headers = {}
        self.__set_default_headers(headers)
        self.set_service_auth(headers)
        params = urllib.parse.urlencode({PyWSFile.__PATH: self.__file_path})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("POST", "/api/mkdir", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
            return True
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()

    def get_child_path(self, filename: str) -> str:
        n_filepath = self.__file_path
        if not n_filepath.endswith(PyWSFile.separator):
            n_filepath += PyWSFile.separator
        n_filepath += filename
        return n_filepath

    def __str__(self) -> str:
        """
        Returns a string representation of this object
        """
        return self.__file_path

    def set_service_auth(self, headers: dict[str, str]):
        if not self.__credentials:
            return
        headers['Authorization'] = 'Basic ' + Base64().encode(bytearray(
            (self.__credentials.get_service_user() + ":" + self.__credentials.get_service_password())
            .encode('utf-8')))

    def __check_status(self, http_response: HTTPResponse, status: int):
        if http_response.status != status:
            raise IOError(str(http_response.status)
                          + " " + http_response.reason + "\n"
                          + http_response.read().decode())

    def __set_default_headers(self, headers: dict[str, str]):
        headers["Cache"] = "no-store"
        headers["Connection"] = "keep-alive"
        headers["Content-type"] = "application/x-www-form-urlencoded"

    def __create_connection(self):
        conn = None
        scheme = urlparse(self.__service_path).scheme
        if scheme == "http":
            conn = http.client.HTTPConnection(urlparse(self.__service_path).netloc)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(urlparse(self.__service_path).netloc)
        return conn

class Credentials:
    def __init__(self, service_user: str, service_password: str):
        self.__service_user: str = service_user
        self.__service_password: str = service_password

    def get_service_user(self) -> str:
        return self.__service_user

    def get_service_password(self) -> str:
        return self.__service_password
