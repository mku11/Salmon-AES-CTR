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
from datetime import datetime
from typing import Callable, Any
from typeguard import typechecked

from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class IRealFile(ABC):
    """
    Interface that represents a real file. This class is used internally by the virtual disk to
    import, store, and export the encrypted files.
    Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
    See: {@link PyFile}
    """

    def exists(self) -> bool:
        """
        True if this file exists.
        
        :return: True if exists
        """
        pass

    def delete(self) -> bool:
        """
        Delete this file.
        
        :return: True if file deleted
        """
        pass

    def get_input_stream(self) -> RandomAccessStream:
        """
        Get a stream for reading the file.
        
        :return: The stream
        :raises FileNotFoundException:         """
        pass

    def get_output_stream(self) -> RandomAccessStream:
        """
        Get a stream for writing to the file.
        
        :return: The stream
        :raises FileNotFoundException:         """
        pass

    def rename_to(self, new_filename: str) -> bool:
        """
        Rename file.
        
        :param new_filename: The new filename
        :return: True if success.
        :raises FileNotFoundException:         """
        pass

    def length(self) -> int:
        """
        Get the length for the file.
        
        :return: The length.
        """
        pass

    def get_children_count(self) -> int:
        """
        Get the count of files and subdirectories
        
        :return: The children count
        """
        pass

    def last_modified(self) -> int:
        """
        Get the last modified date of the file.
        
        :return: The last modified date in milliseconds
        """
        pass

    def get_absolute_path(self) -> str:
        """
        Get the absolute path of the file on disk.
        
        :return: The absolute path
        """
        pass

    def get_path(self) -> str:
        """
        Get the original filepath of this file. This might symlinks or merged folders. To get the absolute path
        use {@link #getAbsolutePath()}.
        
        :return: The path
        """
        pass

    def is_file(self) -> bool:
        """
        True if this is a file.
        
        :return: True if file
        """
        pass

    def is_directory(self) -> bool:
        """
        True if this is a directory.
        
        :return: True if directory
        """
        pass

    def list_files(self) -> list[IRealFile]:
        """
        Get all files and directories under this directory.
        
        :return: The files
        """
        pass

    def get_base_name(self) -> str:
        """
        Get the basename of the file.
        
        :return: The base name
        """
        pass

    def create_directory(self, dir_name: str) -> IRealFile:
        """
        Create the directory with the name provided under this directory.
        
        :param dir_name: Directory name.
        :return: The newly created directory.
        """
        pass

    def get_parent(self) -> IRealFile:
        """
        Get the parent directory of this file/directory.
        
        :return: The parent directory.
        """
        pass

    def create_file(self, filename: str) -> IRealFile:
        """
        Create an empty file with the provided name.
        
        :param filename: The name for the new file.
        :return: The newly create file.
        :raises IOError: Thrown if there is an IO error.
        """
        pass

    def move(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: Callable[[int, int], Any] | None = None) -> IRealFile:
        """
        Move this file to another directory.
        
        :param new_dir:           The target directory.
        :param new_name:          The new filename.
        :param progress_listener: Observer to notify of the move progress.
        :return: The file after the move. Use this instance for any subsequent file operations.
        """
        pass

    def copy(self, new_dir: IRealFile, new_name: str | None = None,
             progress_listener: Callable[[int, int], Any] | None = None) -> IRealFile:
        """
        Copy this file to another directory.
        
        :param new_dir:           The target directory.
        :param new_name:          The new filename.
        :param progress_listener: Observer to notify of the copy progress.
        :return: The file after the copy. Use this instance for any subsequent file operations.
        :raises IOError: Thrown if there is an IO error.
        """
        pass

    def get_child(self, filename: str) -> IRealFile:
        """
        Get the file/directory matching the name provided under this directory.
        
        :param filename: The name of the file or directory to match.
        :return: The file that was matched.
        """
        pass

    def mkdir(self) -> bool:
        """
        Create a directory with the current filepath.
        
        :return: True if directory created
        """
        pass

    def reset(self):
        """
        Clear cached properties
        """
        pass

    @staticmethod
    def copy_file_contents(src: IRealFile, dest: IRealFile, delete: bool = False,
                           progress_listener: Callable[[int, int], Any] | None = None) -> bool:
        """
        Copy contents of a file to another file.
        
        :param src:              The source directory
        :param dest:             The target directory
        :param delete:           True to delete the source files when complete
        :param progress_listener: The progress listener
        :return: True if contents copied
        :raises IOError: Thrown if there is an IO error.
        """
        source: RandomAccessStream = src.get_input_stream()
        target: RandomAccessStream = dest.get_output_stream()
        try:
            source.copy_to(target, progress_listener=progress_listener)
        except Exception as ex:
            dest.delete()
            return False
        finally:
            source.close()
            target.close()
        if delete:
            src.delete()
        return True

    def copy_recursively(self, dest: IRealFile,
                         progress_listener: Callable[[IRealFile, int, int], Any] | None = None,
                         auto_rename: Callable[[IRealFile], str] | None = None,
                         auto_rename_folders: bool = True,
                         on_failed: Callable[[IRealFile, Exception], Any] | None = None):
        """
        Copy a directory recursively
        
        :param dest: The destination directory
        :param progress_listener: The progress listener
        :param auto_rename: The autorename function
        :param auto_rename_folders: Apply autorename to folders also (default is True)
        :param on_failed: Callback when copy failed
        :raises IOError: Thrown if there is an IO error.
        """
        new_filename: str = self.get_base_name()
        new_file: IRealFile
        new_file = dest.get_child(new_filename)
        if self.is_file():
            if new_file is not None and new_file.exists():
                if auto_rename is not None:
                    new_filename = auto_rename(self)
                else:
                    if on_failed is not None:
                        on_failed(self, Exception("Another file exists"))
                    return

            self.copy(dest, new_filename, lambda position, length: self.notify(position, length, progress_listener))
        elif self.is_directory():
            if progress_listener is not None:
                progress_listener(self, 0, 1)
            if dest.get_absolute_path().startswith(self.get_absolute_path()):
                if progress_listener:
                    progress_listener(self, 1, 1)
                return
            if new_file is not None and new_file.exists() and auto_rename is not None and auto_rename_folders:
                new_file = dest.create_directory(auto_rename(self))
            elif new_file is None or not new_file.exists():
                new_file = dest.create_directory(new_filename)
            if progress_listener is not None:
                progress_listener(self, 1, 1)

            for child in self.list_files():
                child.copy_recursively(new_file, progress_listener, auto_rename, auto_rename_folders, on_failed)

    def move_recursively(self, dest: IRealFile,
                         progress_listener: Callable[[IRealFile, int, int], Any] | None = None,
                         auto_rename: Callable[[IRealFile], str] | None = None,
                         auto_rename_folders: bool = True,
                         on_failed: Callable[[IRealFile, Exception], Any] | None = None):
        """
        Move a directory recursively
        
        :param dest:              The target directory
        :param progress_listener: The progress listener
        :param auto_rename: The autorename function
        :param auto_rename_folders: Apply autorename to folders also (default is True)
        :param on_failed: Callback when move failed
        """
        # target directory is the same
        if self.get_parent().get_path() == dest.get_path():
            if progress_listener is not None:
                progress_listener(self, 0, 1)
                progress_listener(self, 1, 1)
            return

        new_filename: str = self.get_base_name()
        new_file: IRealFile
        new_file = dest.get_child(new_filename)
        if self.is_file():
            if new_file is not None and new_file.exists():
                if new_file.get_path() == self.get_path():
                    return
                if auto_rename is not None:
                    new_filename = auto_rename(self)
                else:
                    if on_failed is not None:
                        on_failed(self, Exception("Another file exists"))
                    return

            self.move(dest, new_filename,
                      lambda position, length: self.notify(position, length, progress_listener))
        elif self.is_directory():
            if progress_listener is not None:
                progress_listener(self, 0, 1)
            if (new_file is not None and new_file.exists() and auto_rename is not None and auto_rename_folders) \
                    or new_file is None or not new_file.exists():
                new_file = self.move(dest, auto_rename(self))
                return

            if progress_listener is not None:
                progress_listener(self, 1, 1)

            for child in self.list_files():
                child.move_recursively(new_file, progress_listener, auto_rename, auto_rename_folders, on_failed)

            if not self.delete():
                on_failed(self, Exception("Could not delete source directory"))

    def delete_recursively(self, progress_listener: Callable[[IRealFile, int, int], Any],
                           on_failed: Callable[[IRealFile, Exception], Any]):
        """
        Delete a directory recursively
        :param progress_listener: Progress listener
        :param on_failed: Callback when delete fails
        """
        if self.is_file():
            progress_listener(self, 0, 1)
            if not self.delete():
                on_failed(self, Exception("Could not delete file"))
                return

            progress_listener(self, 1, 1)
        elif self.is_directory():
            for child in self.list_files():
                child.delete_recursively(progress_listener, on_failed)

            if not self.delete():
                on_failed(self, Exception("Could not delete directory"))
                return

    @staticmethod
    def auto_rename_file(file: IRealFile) -> str:
        """
        Get an auto generated copy of the name for a file.
        """
        return IRealFile.auto_rename(file.get_base_name())

    @staticmethod
    def auto_rename(filename: str) -> str:
        """
        Get an auto generated copy of a filename
        
        :param filename:
        :return: The new file name
        """
        ext: str = IRealFile.__get_extension(filename)
        filename_no_ext: str | None = None
        if len(ext) > 0:
            filename_no_ext = filename[0:len(filename) - len(ext) - 1]
        else:
            filename_no_ext = filename
        new_filename: str = filename_no_ext + " (" + datetime.today().strftime("%H%m%S%f")[:-3] + ")"
        if len(ext) > 0:
            new_filename += "." + ext
        return new_filename

    def notify(self, position: int, length: int, progress_listener: Callable[[IRealFile, int, int], Any] | None = None):
        if progress_listener is not None:
            progress_listener(self, position, length)

    @staticmethod
    def __get_extension(file_name: str) -> str:
        if file_name is None:
            return ""
        index: int = file_name.rindex(".")
        if index >= 0:
            return file_name[index + 1:]
        else:
            return ""
