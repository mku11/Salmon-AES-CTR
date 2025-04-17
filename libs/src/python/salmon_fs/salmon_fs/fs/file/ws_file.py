#!/usr/bin/env python3
"""!@brief Web Service File implementation for Python.
"""

from __future__ import annotations

__license__ = """
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
"""

import json
import http.client
import urllib.parse
from urllib.parse import urlparse
from http.client import HTTPResponse, HTTPConnection, HTTPSConnection
from typing import Any

from typeguard import typechecked
from threading import RLock

from salmon_core.convert.base_64 import Base64
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.streams.ws_file_stream import WSFileStream
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class WSFile(IFile):
    """!
    Web Service File implementation for Python.
    """

    __PATH: str = "path"
    __DEST_DIR: str = "destDir"
    __FILENAME: str = "filename"
    separator: str = "/"
    __lock_object: RLock = RLock()

    def get_service_path(self):
        """!
        Get the service path
        """
        return self.__service_path

    def get_credentials(self):
        """!
        Get the web service credentials
        @returns The credentials
        """
        return self.__credentials

    def set_credentials(self, credentials: Credentials):
        """!
        Set the web service credentials
        @param credentials: The credentials
        """
        self.__credentials = credentials

    def __init__(self, path: str, service_path: str, credentials: Credentials):
        """!
        Instantiate a real file represented by the filepath and service path provided.
        @param path: The filepath.
        """
        if not path.startswith(WSFile.separator):
            path = WSFile.separator + path
        self.__file_path: str = path
        self.__service_path: str = service_path
        self.__credentials: Credentials = credentials
        self.__response: HTTPResponse | None = None

    def __get_response(self) -> dict[str, any]:
        with WSFile.__lock_object:
            if not self.__response:
                headers = {}
                self.__set_default_headers(headers)
                self.__set_service_auth(headers)
                params = urllib.parse.urlencode({WSFile.__PATH: self.__file_path})
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

    def create_directory(self, dir_name: str) -> IFile:
        """!
        Create a directory under this directory.
        @param dir_name: The name of the new directory.
        @returns The newly created directory.
        """
        n_dir_path: str = self.get_child_path(dir_name)
        headers = {}
        self.__set_default_headers(headers)
        self.__set_service_auth(headers)
        params = urllib.parse.urlencode({WSFile.__PATH: n_dir_path})
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
        ndir: WSFile = WSFile(n_dir_path, self.__service_path, self.__credentials)
        return ndir

    def create_file(self, filename: str) -> IFile:
        """!
        Create a file under this directory.
        @param filename: The name of the new file.
        @returns The newly created file.
        @exception IOError: Thrown if there is an IO error.
        """
        n_filepath: str = self.get_child_path(filename)
        headers = {}
        self.__set_default_headers(headers)
        self.__set_service_auth(headers)
        params = urllib.parse.urlencode({WSFile.__PATH: n_filepath})
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
        n_file: WSFile = WSFile(n_filepath, self.__service_path, self.__credentials)
        return n_file

    def delete(self) -> bool:
        """!
        Delete this file or directory.
        @returns True if deletion is successful.
        """
        self.reset()
        if self.is_directory():
            p_files: list[IFile] = self.list_files()
            for p_file in p_files:
                headers = {}
                self.__set_default_headers(headers)
                self.__set_service_auth(headers)
                params = urllib.parse.urlencode({WSFile.__PATH: p_file.get_path()})
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
        self.__set_service_auth(headers)
        params = urllib.parse.urlencode({WSFile.__PATH: self.__file_path})
        conn: HTTPConnection | HTTPSConnection | None = None
        http_response: HTTPResponse | None = None
        try:
            conn = self.__create_connection()
            conn.request("DELETE", "/api/delete", headers=headers, body=params)
            http_response = conn.getresponse()
            self.__check_status(http_response, 200)
            self.reset()
            return True
        finally:
            if conn:
                conn.close()
            if http_response:
                http_response.close()

    def exists(self) -> bool:
        """!
        True if file or directory exists.
        @returns True if exists
        """
        return self.__get_response()['present']

    def get_display_path(self) -> str:
        """!
        Get the absolute path on the physical disk.
        @returns The absolute path.
        """
        return self.__file_path

    def get_name(self) -> str:
        """!
        Get the name of this file or directory.
        @returns The name of this file or directory.
        """
        if not self.__file_path:
            raise Exception("Filepath is not assigned")
        n_filepath = self.__file_path
        if n_filepath.endswith("/"):
            n_filepath = n_filepath[0:-1]
        basename: str | None = n_filepath.split(WSFile.separator).pop()
        if not basename:
            raise Exception("Could not get basename")
        if "%" in basename:
            basename = urllib.parse.unquote(basename)
        return basename

    def get_input_stream(self) -> RandomAccessStream:
        """!
        Get a stream for reading the file.
        @returns The stream to read from.
        @exception FileNotFoundException:     """
        self.reset()
        return WSFileStream(self, "r")

    def get_output_stream(self) -> RandomAccessStream:
        """!
        Get a stream for writing to this file.
        @returns The stream to write to.
        @exception FileNotFoundException:         """
        self.reset()
        return WSFileStream(self, "rw")

    def get_parent(self) -> IFile:
        """!
        Get the parent directory of this file or directory.
        @returns The parent directory.
        """
        path: str = self.__file_path
        if path.endswith(WSFile.separator):
            path = path[0:-1]

        parent_file_path: str = path[0:path.rindex(WSFile.separator)] if WSFile.separator in path else ""
        return WSFile(parent_file_path, self.__service_path, self.__credentials)

    def get_path(self) -> str:
        """!
        Get the path of this file. For python this is the same as the absolute filepath.
        @returns The path
        """
        return self.__file_path

    def is_directory(self) -> bool:
        """!
        True if this is a directory.
        @returns True if directory
        """
        return self.__get_response()['directory']

    def is_file(self) -> bool:
        """!
        True if this is a file.
        @returns True if file
        """
        return self.__get_response()['file']

    def get_last_date_modified(self) -> int:
        """!
        Get the last modified date on disk.
        @returns The last modified date in milliseconds
        """
        return self.__get_response()['lastModified']

    def get_length(self) -> int:
        """!
        Get the size of the file on disk.
        @returns The size
        """
        return self.__get_response()['length']

    def get_children_count(self) -> int:
        """!
        Get the count of files and subdirectories
        @returns The children
        """
        if self.is_directory():
            headers = {}
            self.__set_default_headers(headers)
            self.__set_service_auth(headers)
            params = urllib.parse.urlencode({WSFile.__PATH: self.get_path()})
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

    def list_files(self) -> list[IFile]:
        """!
        List all files under this directory.
        @returns The list of files.
        """
        if self.is_directory():
            headers = {}
            self.__set_default_headers(headers)
            self.__set_service_auth(headers)
            params = urllib.parse.urlencode({WSFile.__PATH: self.get_path()})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("GET", "/api/list" + "?" + params, headers=headers)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                contents = http_response.read()
                files: list[Any] = json.loads(contents)
                real_files: list[WSFile] = []
                real_dirs: list[WSFile] = []

                for file in files:
                    p_file: WSFile = WSFile(file['path'], self.get_service_path(), self.get_credentials())
                    if file['directory']:
                        real_dirs.append(p_file)
                    else:
                        real_files.append(p_file)

                real_dirs.extend(real_files)
                return real_dirs
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()
        return []

    def move(self, new_dir: IFile, options: IFile.MoveOptions | None = None) -> IFile:
        """!
        Move this file or directory under a new directory.
        @param new_dir: The target directory.
        @param options: The options
        @returns The moved file. Use this file for subsequent operations instead of the original.
        """

        if not options:
            options = IFile.MoveOptions()

        new_name: str = options.new_filename if options.new_filename else self.get_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exist")

        new_file: IFile | None = new_dir.get_child(new_name)
        if new_file and new_file.exists():
            raise IOError("Another file/directory already exists")

        if self.is_directory():
            raise IOError("Could not move directory use IRealFile moveRecursively() instead")
        else:
            headers = {}
            self.__set_default_headers(headers)
            self.__set_service_auth(headers)
            params = urllib.parse.urlencode({WSFile.__PATH: self.get_path(),
                                             WSFile.__DEST_DIR: new_dir.get_path(),
                                             WSFile.__FILENAME: new_name})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("PUT", "/api/move", headers=headers, body=params)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                new_file = WSFile(json.loads(http_response.read())['path'], self.__service_path, self.__credentials)
                self.reset()
                return new_file
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()

    def copy(self, new_dir: IFile, options: IFile.CopyOptions | None = None) -> IFile:
        """!
        Move this file or directory under a new directory.
        @param new_dir:    The target directory.
        @param options: The options
        @returns The copied file. Use this file for subsequent operations instead of the original.
        @exception IOError: Thrown if there is an IO error.
        """

        if not options:
            options = IFile.CopyOptions()

        new_name: str = options.new_filename if options.new_filename else self.get_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exists")
        new_file: IFile | None = new_dir.get_child(new_name)
        if new_file and new_file.exists():
            raise IOError("Another file/directory already exists")
        if self.is_directory():
            raise IOError("Could not copy directory use IRealFile copyRecursively() instead")
        else:
            headers = {}
            self.__set_default_headers(headers)
            self.__set_service_auth(headers)
            params = urllib.parse.urlencode({WSFile.__PATH: self.get_path(),
                                             WSFile.__DEST_DIR: new_dir.get_path(),
                                             WSFile.__FILENAME: new_name})
            conn: HTTPConnection | HTTPSConnection | None = None
            http_response: HTTPResponse | None = None
            try:
                conn = self.__create_connection()
                conn.request("POST", "/api/copy", headers=headers, body=params)
                http_response = conn.getresponse()
                self.__check_status(http_response, 200)
                self.reset()
                new_file = WSFile(json.loads(http_response.read())['path'], self.__service_path, self.__credentials)
                return new_file
            finally:
                if conn:
                    conn.close()
                if http_response:
                    http_response.close()

    def get_child(self, filename: str) -> IFile | None:
        """!
        Get the file or directory under this directory with the provided name.
        @param filename: The name of the file or directory.
        @returns The child file
        """
        if self.is_file():
            return None
        n_filepath: str = self.get_child_path(filename)
        child: WSFile = WSFile(n_filepath, self.__service_path, self.__credentials)
        return child

    def rename_to(self, new_filename: str) -> bool:
        """!
        Rename the current file or directory.
        @param new_filename: The new name for the file or directory.
        @returns True if successfully renamed.
        """
        self.reset()
        headers = {}
        self.__set_default_headers(headers)
        self.__set_service_auth(headers)
        params = urllib.parse.urlencode({WSFile.__PATH: self.get_path(),
                                         WSFile.__FILENAME: new_filename})
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
        """!
        Create this directory under the current filepath.
        @returns True if created.
        """
        self.reset()
        headers = {}
        self.__set_default_headers(headers)
        self.__set_service_auth(headers)
        params = urllib.parse.urlencode({WSFile.__PATH: self.__file_path})
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

    def reset(self):
        """!
        Clear cached properties
        """
        with WSFile.__lock_object:
            self.__response = None

    def get_child_path(self, filename: str) -> str:
        n_filepath = self.__file_path
        if not n_filepath.endswith(WSFile.separator):
            n_filepath += WSFile.separator
        n_filepath += filename
        return n_filepath

    def __str__(self) -> str:
        """!
        Returns a string representation of this object
        """
        return self.__file_path

    def __set_service_auth(self, headers: dict[str, str]):
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
        headers["Content-type"] = "application/x-www-form-urlencoded"

    def __create_connection(self):
        conn = None
        scheme = urlparse(self.__service_path).scheme
        if scheme == "http":
            conn = http.client.HTTPConnection(urlparse(self.__service_path).netloc)
        elif scheme == "https":
            conn = http.client.HTTPSConnection(urlparse(self.__service_path).netloc)
        return conn

@typechecked
class Credentials:
    """!
    Credentials
    """
    def __init__(self, service_user: str, service_password: str):
        """!
        Instntiate the credentials
        @param service_user: The user name
        @param service_password: The password
        """
        self.__service_user: str = service_user
        self.__service_password: str = service_password

    def get_service_user(self) -> str:
        """!
        Get the service user name
        @returns The user name
        """
        return self.__service_user

    def get_service_password(self) -> str:
        """!
        Get the service password
        @returns The password
        """
        return self.__service_password
