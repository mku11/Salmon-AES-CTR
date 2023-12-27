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
from __future__ import annotations
import os

from file.ireal_file import IRealFile
from file.py_file_stream import PyFileStream
from iostream.random_access_stream import RandomAccessStream


class PyFile(IRealFile):
    """
     * Salmon RealFile implementation for Python.
    """

    def __init__(self, path: str):
        """
         * Instantiate a real file represented by the filepath provided.
         * @param path The filepath.
        """
        self.__file_path: str | None = None
        self.__file_path = path

    def create_directory(self, dir_name: str) -> IRealFile:
        """
         * Create a directory under this directory.
         * @param dir_name The name of the new directory.
         * @return The newly created directory.
        """
        n_dir_path: str = self.__file_path + os.sep + dir_name
        os.mkdir(n_dir_path)
        if not os.path.exists(n_dir_path):
            raise RuntimeError("directory already exists")
        ndir: PyFile = PyFile(n_dir_path)
        return ndir

    def create_file(self, filename: str) -> IRealFile:
        """
         * Create a file under this directory.
         * @param filename The name of the new file.
         * @return The newly created file.
         * @throws IOError
        """
        n_file_path: str = self.__file_path + os.sep + filename
        open(n_file_path, 'a').close()
        n_file = PyFile(n_file_path)
        return n_file

    def delete(self) -> bool:
        """
         * Delete this file or directory.
         * @return True if deletion is successful.
        """
        if self.is_directory():
            p_files: [IRealFile] = self.list_files()
            for p_file in p_files:
                p_file.delete()
        os.remove(self.__file_path)
        return not os.path.exists(self.__file_path)

    def exists(self) -> bool:
        """
         * True if file or directory exists.
         * @return
        """
        return os.path.exists(self.__file_path)

    def get_absolute_path(self) -> str:
        """
         * Get the absolute path on the physical disk.
         * @return The absolute path.
        """
        return os.path.abspath(self.__file_path)

    def get_base_name(self) -> str:
        """
         * Get the name of this file or directory.
         * @return The name of this file or directory.
        """
        return os.path.basename(self.__file_path)

    def get_input_stream(self) -> RandomAccessStream:
        return PyFileStream(self, "r")

    """
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
    """

    def get_output_stream(self) -> RandomAccessStream:
        """
         * Get a stream for writing to this file.
         * @return The stream to write to.
         * @throws FileNotFoundException
        """
        return PyFileStream(self, "w")

    def get_parent(self) -> IRealFile:
        """
         * Get the parent directory of this file or directory.
         * @return The parent directory.
        """
        dir_path: str = os.path.dirname(self.__file_path)
        parent: PyFile = PyFile(dir_path)
        return parent

    def get_path(self) -> str:
        """
         * Get the path of this file. For python this is the same as the absolute filepath.
         * @return
        """
        return self.__file_path

    def is_directory(self) -> bool:
        """
         * True if this is a directory.
         * @return
        """
        return os.path.isdir(self.__file_path)

    def is_file(self) -> bool:
        """
         * True if this is a file.
         * @return
        """
        return not self.is_directory()

    def last_modified(self) -> int:
        """
         * Get the last modified date on disk.
         * @return
        """
        return int(os.path.getmtime(self.__file_path))

    def length(self) -> int:
        """
         * Get the size of the file on disk.
         * @return
        """
        return os.path.getsize(self.__file_path)

    def get_children_count(self) -> int:
        """
         * Get the count of files and subdirectories
         * @return
        """
        return len(os.listdir(self.__file_path)) if self.is_directory() else 0

    def list_files(self) -> [IRealFile]:
        """
         * List all files under this directory.
         * @return The list of files.
        """
        files: [str] = os.listdir(self.__file_path)
        if files is None:
            return []

        real_files: [PyFile] = []
        real_dirs: [PyFile] = []
        for i in range(0, len(files)):
            p_file: PyFile = PyFile(files[i])
            if p_file.is_directory():
                real_dirs.append(p_file)
            else:
                real_files.append(p_file)

        real_dirs.extend(real_files)
        return real_dirs

    def move(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: RandomAccessStream.OnProgressListener | None = None) -> IRealFile:
        """
         * Move this file or directory under a new directory.
         * @param new_dir The target directory.
         * @param new_name The new filename
         * @param progress_listener Observer to notify when progress changes.
         * @return The moved file. Use this file for subsequent operations instead of the original.
        """
        new_name = new_name if new_name is not None else self.get_base_name()
        n_file_path: str = new_dir.get_path() + os.sep + new_name
        os.rename(self.__file_path, n_file_path)
        if not os.path.exists(n_file_path):
            raise RuntimeError("directory already exists")
        return PyFile(n_file_path)

    def copy(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: RandomAccessStream.OnProgressListener | None = None) -> IRealFile:
        """
         * Move this file or directory under a new directory.
         * @param new_dir    The target directory.
         * @param new_name   New filename
         * @param progress_listener Observer to notify when progress changes.
         * @return The copied file. Use this file for subsequent operations instead of the original.
         * @throws IOError
        """
        new_name = new_name if new_name is not None else self.get_base_name()
        if new_dir is None or not new_dir.exists():
            raise IOError("Target directory does not exists")
        new_file: IRealFile = new_dir.get_child(new_name)
        if new_file is not None and new_file.exists():
            raise IOError("Another file/directory already exists")
        if self.is_directory():
            return new_dir.create_directory(new_name)
        else:
            new_file = new_dir.create_file(new_name)
            res: bool = IRealFile.copy_file_contents(self, new_file, False, progress_listener)
            return new_file if res else None

    def get_child(self, filename: str) -> IRealFile | None:
        """
         * Get the file or directory under this directory with the provided name.
         * @param filename The name of the file or directory.
         * @return
        """
        if self.is_file():
            return None
        child: PyFile = PyFile(self.__file_path + os.sep + filename)
        return child

    def rename_to(self, new_filename: str) -> bool:
        """
         * Rename the current file or directory.
         * @param new_filename The new name for the file or directory.
         * @return True if successfully renamed.
        """
        v_dir = os.path.dirname(self.__file_path)
        os.rename(self.__file_path, v_dir + os.sep + new_filename)
        return os.path.exists(v_dir + os.sep + new_filename)

    def mkdir(self) -> bool:
        """
         * Create this directory under the current filepath.
         * @return True if created.
        """
        os.mkdir(self.__file_path)
        return self.exists()

    def __str__(self) -> str:
        """
         * Returns a string representation of this object
        """
        return self.__file_path
