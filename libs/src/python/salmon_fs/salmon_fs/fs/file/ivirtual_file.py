#!/usr/bin/env python3
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

from abc import ABC
from typing import Any, Callable
from typeguard import typechecked

from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.drive.virtual_drive import VirtualDrive
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class IVirtualFile(ABC):
    """
    A virtual file. Read-only operations are included. Since write operations can be implementation
    specific ie for encryption they can be implemented by extending this class.
    """

    def __init__(self, real_file: IFile, drive: VirtualDrive | None = None):
        """!
        Initialize the virtual file backed by a real file on disk.
        @param real_file: The real file
        @param drive: The drive the file belongs to.
        """
        pass

    def get_input_stream(self) -> RandomAccessStream:
        """!
        Get an input stream
        """
        pass

    def get_output_stream(self, nonce: bytearray) -> RandomAccessStream:
        """!
        Get an output stream
        """
        pass

    def list_files(self) -> list[IVirtualFile]:
        """!
        List the files and subdirectories
        """
        pass

    def get_child(self, filename: str) -> IVirtualFile | None:
        """!
        Get a file under this directory
        @param filename: The name of the file
        """
        pass

    def is_file(self) -> bool:
        """!
        Check if this is a file
        @returns True if file
        """
        pass

    def is_directory(self) -> bool:
        """!
        Check if this is a directory
        @returns True if directory
        """
        pass

    def get_path(self) -> str:
        """!
        Get the file path
        @returns The file path
        """
        pass

    def get_real_path(self) -> str:
        """!
        Get the real path on disk
        @returns The real path
        """
        pass

    def get_real_file(self) -> IFile:
        """!
        Get the real file
        @returns The real file
        """
        pass

    def get_name(self) -> str:
        """!
        Get the name
        @returns The name
        """
        pass

    def get_parent(self) -> IVirtualFile | None:
        """!
        Get the parent
        @returns The parent
        """
        pass

    def delete(self):
        """!
        Delete this file
        """
        pass

    def mkdir(self):
        """!
        Create this as directory
        """
        pass

    def get_last_date_modified(self) -> int:
        """!
        Get the last date modified
        @returns The last date modified
        """
        pass

    def get_length(self) -> int:
        """!
        Get the length
        @returns The length
        """
        pass

    def exists(self) -> bool:
        """!
        Check if exists
        @returns True if exists
        """
        pass

    def create_directory(self, dir_name: str) -> IVirtualFile:
        """!
        Create the directory
        @param dir_name The directory name
        """
        pass

    def create_file(self, filename: str) -> IVirtualFile:
        """!
        Create a file
        @param filename: The file name
        """
        pass

    def rename(self, new_filename: str):
        """!
        Rename
        @param new_filename: The new file name
        """
        pass

    def move(self, v_dir: IVirtualFile, options: IFile.MoveOptions | None = None) -> IVirtualFile:
        """!
        Move to another directory
        @param v_dir: The destination directory
        @param options: The options
        """
        pass

    def copy(self, v_dir: IVirtualFile, options: IFile.CopyOptions | None = None) -> IVirtualFile:
        """!
        Copy to another directory
        @param v_dir: The destination directory
        @param options: The options
        """
        pass

    def copy_recursively(self, dest: IVirtualFile, options: IVirtualFile.VirtualRecursiveCopyOptions | None = None):
        """!
        Copy directory (recursively) to another directory
        @param dest: The destination directory
        @param options: The options
        """
        pass

    def move_recursively(self, dest: IVirtualFile, options: IVirtualFile.VirtualRecursiveMoveOptions | None = None):
        """!
        Move directory (recursively) to another directory
        @param dest: The destination directory
        @param options: The options
        """
        pass

    def delete_recursively(self, options: IVirtualFile.VirtualRecursiveDeleteOptions | None = None):
        """!
        Delete directory (recursively)
        @param options: The options
        """
        pass

    class VirtualRecursiveCopyOptions:
        """!
          Directory copy options (recursively)
        """

        auto_rename: Callable[[IVirtualFile], str] | None = None
        """!
          Callback when file with same name exists
        """

        auto_rename_folders: bool = False
        """
          True to autorename folders
        """

        on_failed: Callable[[IVirtualFile, Exception], Any] | None = None
        """!
          Callback when file changes
        """

        on_progress_changed: Callable[[IVirtualFile, int, int], Any] | None = None
        """!
          Callback where progress changed
        """

    class VirtualRecursiveMoveOptions:
        """!
          Directory move options (recursively)
        """

        auto_rename: Callable[[IVirtualFile], str] | None = None
        """!
          Callback when file with the same name exists
        """

        auto_rename_folders: bool = False
        """
          True to autorename folders
        """

        on_failed: Callable[[IVirtualFile, Exception], Any] | None = None
        """!
          Callback when file failed
        """

        on_progress_changed: Callable[[IVirtualFile, int, int], Any] | None = None
        """!
          Callback when progress changes
        """

    class VirtualRecursiveDeleteOptions:
        """!
          Directory move options (recursively)
        """

        on_failed: Callable[[IVirtualFile, Exception], Any] | None = None
        """!
          Callback when file failed
        """

        on_progress_changed: Callable[[IVirtualFile, int, int], Any] | None = None
        """!
          Callback when progress changed
        """
