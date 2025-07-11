#!/usr/bin/env python3
"""!@brief Interface that represents a real file
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

from abc import ABC
from datetime import datetime
from typing import Callable, Any
from typeguard import typechecked

from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class IFile(ABC):
    """!
    Interface that represents a real file. This class is used internally by the virtual disk to
    import, store, and export the encrypted files.
    Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
    See: {@link PyFile}
    """

    def exists(self) -> bool:
        """!
        True if this file exists.
        
        @returns True if exists
        """
        pass

    def delete(self) -> bool:
        """!
        Delete this file.
        
        @returns True if file deleted
        """
        pass

    def get_input_stream(self) -> RandomAccessStream:
        """!
        Get a stream for reading the file.
        
        @returns The stream
        @exception FileNotFoundException:         """
        pass

    def get_output_stream(self) -> RandomAccessStream:
        """!
        Get a stream for writing to the file.
        
        @returns The stream
        @exception FileNotFoundException:         """
        pass

    def rename_to(self, new_filename: str) -> bool:
        """!
        Rename file.
        
        @param new_filename: The new filename
        @returns True if success.
        @exception FileNotFoundException:         """
        pass

    def get_length(self) -> int:
        """!
        Get the length for the file.
        
        @returns The length.
        """
        pass

    def get_children_count(self) -> int:
        """!
        Get the count of files and subdirectories
        
        @returns The children count
        """
        pass

    def get_last_date_modified(self) -> int:
        """!
        Get the last modified date of the file.
        
        @returns The last modified date in milliseconds
        """
        pass

    def get_display_path(self) -> str:
        """!
        Get the absolute path of the file on disk.
        
        @returns The absolute path
        """
        pass

    def get_path(self) -> str:
        """!
        Get the original filepath of this file. This might symlinks or merged folders. To get the absolute path
        use {@link #getAbsolutePath()}.
        
        @returns The path
        """
        pass

    def is_file(self) -> bool:
        """!
        True if this is a file.
        
        @returns True if file
        """
        pass

    def is_directory(self) -> bool:
        """!
        True if this is a directory.
        
        @returns True if directory
        """
        pass

    def list_files(self) -> list[IFile]:
        """!
        Get all files and directories under this directory.
        
        @returns The files
        """
        pass

    def get_name(self) -> str:
        """!
        Get the basename of the file.
        
        @returns The base name
        """
        pass

    def create_directory(self, dir_name: str) -> IFile:
        """!
        Create the directory with the name provided under this directory.
        
        @param dir_name: Directory name.
        @returns The newly created directory.
        """
        pass

    def get_parent(self) -> IFile:
        """!
        Get the parent directory of this file/directory.
        
        @returns The parent directory.
        """
        pass

    def create_file(self, filename: str) -> IFile:
        """!
        Create an empty file with the provided name.
        
        @param filename: The name for the new file.
        @returns The newly create file.
        @exception IOError: Thrown if there is an IO error.
        """
        pass

    def move(self, new_dir: IFile, options: IFile.MoveOptions | None = None) -> IFile:
        """!
        Move this file to another directory.
        
        @param new_dir:           The target directory.
        @param options: The options
        @returns The file after the move. Use this instance for any subsequent file operations.
        """
        pass

    def copy(self, new_dir: IFile, options: IFile.CopyOptions | None = None) -> IFile:
        """!
        Copy this file to another directory.
        
        @param new_dir:           The target directory.
        @param options: The options
        @returns The file after the copy. Use this instance for any subsequent file operations.
        @exception IOError: Thrown if there is an IO error.
        """
        pass

    def get_child(self, filename: str) -> IFile:
        """!
        Get the file/directory matching the name provided under this directory.
        
        @param filename: The name of the file or directory to match.
        @returns The file that was matched.
        """
        pass

    def mkdir(self) -> bool:
        """!
        Create a directory with the current filepath.
        
        @returns True if directory created
        """
        pass

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

    @staticmethod
    def copy_file_contents(src: IFile, dest: IFile, options: IFile.CopyContentsOptions) -> bool:
        """!
        Copy contents of a file to another file.

        @param src:              The source directory
        @param dest:             The target directory
        @param options:     The options
        @returns True if contents copied
        @exception IOError: Thrown if there is an IO error.
        """
        source: RandomAccessStream = src.get_input_stream()
        target: RandomAccessStream = dest.get_output_stream()
        try:
            source.copy_to(target, on_progress_changed=options.on_progress_changed)
        except Exception as ex:
            dest.delete()
            return False
        finally:
            source.close()
            target.close()
        return True

    def copy_recursively(self, dest: IFile, options: IFile.RecursiveCopyOptions | None = None):
        """!
        Copy a directory recursively

        @param dest: The destination directory
        @param options: The options
        @exception IOError: Thrown if there is an IO error.
        """

        if not options:
            options = IFile.RecursiveCopyOptions()
        new_filename: str = self.get_name()
        new_file: IFile
        new_file = dest.get_child(new_filename)
        if self.is_file():
            if new_file and new_file.exists():
                if options.auto_rename:
                    new_filename = options.auto_rename(self)
                else:
                    if options.on_failed:
                        options.on_failed(self, Exception("Another directory/file exists"))
                    return
            copy_options = IFile.CopyOptions()
            copy_options.new_filename = new_filename
            copy_options.on_progress_changed = lambda position, length: self.notify(position, length,
                                                                                    options.on_progress_changed)
            self.copy(dest, copy_options)
        elif self.is_directory():
            if options.on_progress_changed:
                options.on_progress_changed(self, 0, 1)
            if dest.get_display_path().startswith(self.get_display_path()):
                if options.on_progress_changed:
                    options.on_progress_changed(self, 1, 1)
                return
            
            if new_file and new_file.exists() and options.auto_rename and options.auto_rename_folders:
                new_file = dest.create_directory(options.auto_rename(self))
            elif new_file is None or not new_file.exists():
                new_file = dest.create_directory(new_filename)
            elif new_file and new_file.exists() and new_file.is_file():
                if options.on_failed:
                    options.on_failed(self, Exception("Another file exists"))
                return

            if options.on_progress_changed:
                options.on_progress_changed(self, 1, 1)

            for child in self.list_files():
                child.copy_recursively(new_file, options)

    def move_recursively(self, dest: IFile, options: IFile.RecursiveMoveOptions | None = None):
        """!
        Move a directory recursively

        @param dest:              The target directory
        @param options: The options
        """
        if not options:
            options = IFile.RecursiveMoveOptions()

        # target directory is the same
        if self.get_parent().get_path() == dest.get_path():
            if options.on_progress_changed:
                options.on_progress_changed(self, 0, 1)
                options.on_progress_changed(self, 1, 1)
            return

        new_filename: str = self.get_name()
        new_file: IFile
        new_file = dest.get_child(new_filename)
        if self.is_file():
            if new_file and new_file.exists():
                if new_file.get_path() == self.get_path():
                    return
                if options.auto_rename:
                    new_filename = options.auto_rename(self)
                else:
                    if options.on_failed:
                        options.on_failed(self, Exception("Another directory/file exists"))
                    return

            move_options: IFile.MoveOptions = IFile.MoveOptions()
            move_options.new_filename = new_filename
            move_options.on_progress_changed = lambda position, length: self.notify(position, length,
                                                                                    options.on_progress_changed)
            self.move(dest, move_options)
        elif self.is_directory():
            if options.on_progress_changed:
                options.on_progress_changed(self, 0, 1)
            if dest.get_display_path().startswith(self.get_display_path()):
                if options.on_progress_changed:
                    options.on_progress_changed(self, 1, 1)
                return

            if new_file and new_file.exists() and options.auto_rename and options.auto_rename_folders:
                new_file = dest.create_directory(options.auto_rename(self))
            elif new_file is None or not new_file.exists():
                new_file = dest.create_directory(new_filename)
            elif new_file and new_file.exists() and new_file.is_file():
                if options.on_failed:
                    options.on_failed(self, Exception("Another file exists"))
                return

            if options.on_progress_changed:
                options.on_progress_changed(self, 1, 1)

            for child in self.list_files():
                child.move_recursively(new_file, options)

            if not self.delete():
                options.on_failed(self, Exception("Could not delete source directory"))

    def delete_recursively(self, options: IFile.RecursiveDeleteOptions | None = None):
        """!
        Delete a directory recursively
        @param options: The options
        """
        if not options:
            options = IFile.RecursiveDeleteOptions()

        if self.is_file():
            options.on_progress_changed(self, 0, 1)
            if not self.delete():
                options.on_failed(self, Exception("Could not delete file"))
                return

            options.on_progress_changed(self, 1, 1)
        elif self.is_directory():
            for child in self.list_files():
                child.delete_recursively(options)

            if not self.delete():
                if options.on_failed:
                    options.on_failed(self, Exception("Could not delete directory"))
                return

    @staticmethod
    def auto_rename_file(file: IFile) -> str:
        """!
        Get an auto generated copy of the name for a file.
        """
        return IFile.auto_rename(file.get_name())

    @staticmethod
    def auto_rename(filename: str) -> str:
        """!
        Get an auto generated copy of a filename

        @param filename:
        @returns The new file name
        """
        ext: str = IFile.__get_extension(filename)
        filename_no_ext: str | None = None
        if len(ext) > 0:
            filename_no_ext = filename[0:len(filename) - len(ext) - 1]
        else:
            filename_no_ext = filename
        new_filename: str = filename_no_ext + " (" + datetime.today().strftime("%H%m%S%f")[:-3] + ")"
        if len(ext) > 0:
            new_filename += "." + ext
        return new_filename

    def notify(self, position: int, length: int, progress_listener: Callable[[IFile, int, int], Any] | None = None):
        if progress_listener:
            progress_listener(self, position, length)

    @staticmethod
    def __get_extension(file_name: str) -> str:
        if file_name is None:
            return ""
        index: int = file_name.rindex(".") if "." in file_name else -1
        if index >= 0:
            return file_name[index + 1:]
        else:
            return ""

    @typechecked
    class CopyOptions:
        """!
        File copy options
        """

        new_filename: str | None = None
        """!
        Override filename
        """

        on_progress_changed: Callable[[int, int], Any] | None = None
        """!
        Callback where progress changed
        """

    @typechecked
    class MoveOptions:
        """!
        File move options
        """

        new_filename: str | None = None
        """!
        Override filename
        """

        on_progress_changed: Callable[[int, int], Any] | None = None
        """!
        Callback where progress changed
        """

    @typechecked
    class RecursiveCopyOptions:
        """!
        Directory copy options (recursively)
        """

        auto_rename: Callable[[IFile], str] | None = None
        """!
        Callback when file with same name exists
        """

        auto_rename_folders: bool = False
        """
        True to auto_rename folders
        """

        on_failed: Callable[[IFile, Exception], Any] | None = None
        """!
        Callback when file changes
        """

        on_progress_changed: Callable[[IFile, int, int], Any] | None = None
        """!
        Callback where progress changed
        """

    @typechecked
    class RecursiveMoveOptions:
        """!
        Directory move options (recursively)
        """

        auto_rename: Callable[[IFile], str] | None = None
        """!
        Callback when file with the same name exists
        """

        auto_rename_folders: bool = False
        """
        True to auto_rename folders
        """

        on_failed: Callable[[IFile, Exception], Any] | None = None
        """!
        Callback when file failed
        """

        on_progress_changed: Callable[[IFile, int, int], Any] | None = None
        """!
        Callback when progress changes
        """

    @typechecked
    class RecursiveDeleteOptions:
        """!
        Directory move options (recursively)
        """

        on_failed: Callable[[IFile, Exception], Any] | None = None
        """!
        Callback when file failed
        """

        on_progress_changed: Callable[[IFile, int, int], Any] | None = None
        """!
        Callback when progress changed
        """

    @typechecked
    class CopyContentsOptions:
        """!
        Directory move options (recursively)
        """

        on_progress_changed: Callable[[int, int], Any] | None = None
        """!
        Callback when progress changed
        """
