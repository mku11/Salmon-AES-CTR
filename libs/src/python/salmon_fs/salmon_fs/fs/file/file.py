#!/usr/bin/env python3
"""!@brief File implementation for Python.
"""

from __future__ import annotations

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

import os
from typeguard import typechecked

from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.streams.file_stream import FileStream
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class File(IFile):
    """!
    File implementation for Python.
    """

    def __init__(self, path: str):
        """!
        Instantiate a real file represented by the filepath provided.
        @param path: The filepath.
        """
        self.__file_path: str | None = None
        self.__file_path = path

    def create_directory(self, dir_name: str) -> IFile:
        """!
        Create a directory under this directory.
        @param dir_name: The name of the new directory.
        @returns The newly created directory.
        """
        n_dir_path: str = self.__file_path + os.sep + dir_name
        os.makedirs(n_dir_path)
        ndir: File = File(n_dir_path)
        return ndir

    def create_file(self, filename: str) -> IFile:
        """!
        Create a file under this directory.
        @param filename: The name of the new file.
        @returns The newly created file.
        @exception IOError: Thrown if there is an IO error.
        """
        n_file_path: str = self.__file_path + os.sep + filename
        open(n_file_path, 'a').close()
        n_file = File(n_file_path)
        return n_file

    def delete(self) -> bool:
        """!
        Delete this file or directory.
        @returns True if deletion is successful.
        """
        if self.is_directory():
            p_files: list[IFile] = self.list_files()
            for p_file in p_files:
                p_file.delete()
            os.rmdir(self.__file_path)
        else:
            os.remove(self.__file_path)
        return not os.path.exists(self.__file_path)

    def exists(self) -> bool:
        """!
        True if file or directory exists.
        @returns True if exists
        """
        return os.path.exists(self.__file_path)

    def get_display_path(self) -> str:
        """!
        Get the absolute path on the physical disk.
        @returns The absolute path.
        """
        return os.path.abspath(self.__file_path)

    def get_name(self) -> str:
        """!
        Get the name of this file or directory.
        @returns The name of this file or directory.
        """
        return os.path.basename(self.__file_path)

    def get_input_stream(self) -> RandomAccessStream:
        """!
        Get a stream for reading the file.
        @returns The stream to read from.
        @exception FileNotFoundException:     """
        return FileStream(self, "r")

    def get_output_stream(self) -> RandomAccessStream:
        """!
        Get a stream for writing to this file.
        @returns The stream to write to.
        @exception FileNotFoundException:         """
        return FileStream(self, "rw")

    def get_parent(self) -> IFile:
        """!
        Get the parent directory of this file or directory.
        @returns The parent directory.
        """
        dir_path: str = os.path.dirname(self.get_display_path())
        parent: File = File(dir_path)
        return parent

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
        return os.path.isdir(self.__file_path)

    def is_file(self) -> bool:
        """!
        True if this is a file.
        @returns True if file
        """
        return os.path.isfile(self.__file_path)

    def get_last_date_modified(self) -> int:
        """!
        Get the last modified date on disk.
        @returns The last modified date in milliseconds
        """
        return int(os.path.getmtime(self.__file_path))

    def get_length(self) -> int:
        """!
        Get the size of the file on disk.
        @returns The size
        """
        return os.path.getsize(self.__file_path)

    def get_children_count(self) -> int:
        """!
        Get the count of files and subdirectories
        @returns The children
        """
        return len(os.listdir(self.__file_path)) if self.is_directory() else 0

    def list_files(self) -> list[IFile]:
        """!
        List all files under this directory.
        @returns The list of files.
        """
        files: list[str] = os.listdir(self.__file_path)
        if files is None:
            return []

        real_files: list[File] = []
        real_dirs: list[File] = []
        for i in range(0, len(files)):
            file = os.path.abspath(os.path.join(self.__file_path, files[i]))
            p_file: File = File(file)
            if p_file.is_directory():
                real_dirs.append(p_file)
            else:
                real_files.append(p_file)

        real_dirs.extend(real_files)
        return real_dirs

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
            raise IOError("Target directory does not exists")
        new_file: IFile = new_dir.get_child(new_name)
        if new_file and new_file.exists():
            raise IOError("Another file/directory already exists")

        if options.on_progress_changed:
            options.on_progress_changed(0, self.get_length())
        if self.is_directory():
            raise IOError("Could not move directory use IRealFile moveRecursively() instead")
        else:
            n_file_path: str = new_dir.get_path() + os.sep + new_name
            os.rename(self.__file_path, n_file_path)
            if not os.path.exists(n_file_path):
                raise RuntimeError("directory already exists")
            new_file = File(n_file_path)
            if options.on_progress_changed:
                options.on_progress_changed(new_file.get_length(), new_file.get_length())
            return new_file

    def copy(self, new_dir: IFile, options: IFile.CopyOptions | None = None) -> IFile:
        """!
        Move this file or directory under a new directory.
        @param new_dir:    The target directory.
        @param options:     The options
        @returns The copied file. Use this file for subsequent operations instead of the original.
        @exception IOError: Thrown if there is an IO error.
        """
        if not options:
            options = IFile.CopyOptions()
        new_name: str = options.new_filename if options.new_filename else self.get_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exists")
        new_file: IFile = new_dir.get_child(new_name)
        if new_file and new_file.exists():
            raise IOError("Another file/directory already exists")
        if self.is_directory():
            raise IOError("Could not copy directory use IRealFile copyRecursively() instead")
        else:
            new_file = new_dir.create_file(new_name)
            copy_content_options = IFile.CopyContentsOptions()
            copy_content_options.on_progress_changed = options.on_progress_changed
            res: bool = IFile.copy_file_contents(self, new_file, copy_content_options)
            return new_file if res else None

    def get_child(self, filename: str) -> IFile | None:
        """!
        Get the file or directory under this directory with the provided name.
        @param filename: The name of the file or directory.
        @returns The child file
        """
        if self.is_file():
            return None
        child: File = File(self.__file_path + os.sep + filename)
        return child

    def rename_to(self, new_filename: str) -> bool:
        """!
        Rename the current file or directory.
        @param new_filename: The new name for the file or directory.
        @returns True if successfully renamed.
        """
        v_dir = os.path.dirname(self.__file_path)
        os.rename(self.__file_path, v_dir + os.sep + new_filename)
        return os.path.exists(v_dir + os.sep + new_filename)

    def mkdir(self) -> bool:
        """!
        Create this directory under the current filepath.
        @returns True if created.
        """
        os.makedirs(self.__file_path)
        return self.exists()

    def reset(self):
        """!
        Clear cached properties
        """
        pass

    def get_credentials(self):
        """!
        Get the credentials if available
        """
        pass

    def __str__(self) -> str:
        """!
        Returns a string representation of this object
        """
        return self.__file_path
