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

from fs.file.ifile import IFile
from fs.drive import VirtualDrive
from salmon_core.streams.random_access_stream import RandomAccessStream


@typechecked
class IVirtualFile(ABC):
    """
    A virtual file. Read-only operations are included. Since write operations can be implementation
    specific ie for encryption they can be implemented by extending this class.
    """

    def __init__(self, real_file: IFile, drive: VirtualDrive | None = None):
        pass

    def get_input_stream(self) -> RandomAccessStream:
        pass

    def get_output_stream(self, nonce: bytearray) -> RandomAccessStream:
        pass

    def list_files(self) -> list[IVirtualFile]:
        pass

    def get_child(self, filename: str) -> IVirtualFile | None:
        pass

    def is_file(self) -> bool:
        pass

    def is_directory(self) -> bool:
        pass

    def get_path(self) -> str:
        pass

    def get_real_path(self) -> str:
        pass

    def get_real_file(self) -> IFile:
        pass

    def get_base_name(self) -> str:
        pass

    def get_parent(self) -> IVirtualFile | None:
        pass

    def delete(self):
        pass

    def mkdir(self):
        pass

    def get_last_date_time_modified(self) -> int:
        pass

    def get_size(self) -> int:
        pass

    def exists(self) -> bool:
        pass

    def create_directory(self, dir_name: str) -> IVirtualFile:
        pass

    def create_file(self, real_filename: str) -> IVirtualFile:
        pass

    def rename(self, new_filename: str):
        pass

    def move(self, v_dir: IVirtualFile, on_progress_listener: Callable[[int, int], Any] | None = None) -> IVirtualFile:
        pass

    def copy(self, v_dir: IVirtualFile, on_progress_listener: Callable[[int, int], Any] | None = None) -> IVirtualFile:
        pass

    def copy_recursively(self, dest: IVirtualFile,
                         progress_listener: Callable[[IVirtualFile, int, int], Any],
                         auto_rename: Callable[[IVirtualFile], str],
                         auto_rename_folders: bool,
                         on_failed: Callable[[IVirtualFile, Exception], Any]):
        pass

    def move_recursively(self, dest: IVirtualFile,
                         progress_listener: Callable[[IVirtualFile, int, int], Any],
                         auto_rename: Callable[[IVirtualFile], str],
                         auto_rename_folders: bool,
                         on_failed: Callable[[IVirtualFile, Exception], Any]):
        pass

    def delete_recursively(self, progress_listener: Callable[[IVirtualFile, int, int], Any],
                           on_failed: Callable[[IVirtualFile, Exception], Any]):
        pass
