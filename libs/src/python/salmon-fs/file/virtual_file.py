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

from abc import ABC

from file.ireal_file import IRealFile
from file.virtual_drive import VirtualDrive
from iostream.random_access_stream import RandomAccessStream


class VirtualFile(ABC):
    """
     * A virtual file. Read-only operations are included. Since write operations can be implementation
     * specific ie for encryption they can be implemented by extending this class.
    """
    def __init__(self, real_file: IRealFile, drive: VirtualDrive | None = None):
        pass

    def get_input_stream(self) -> RandomAccessStream:
        pass

    def get_output_stream(self, nonce: bytearray) -> RandomAccessStream:
        pass

    def list_files(self) -> [VirtualFile]:
        pass

    def get_child(self, filename: str) -> VirtualFile | None:
        pass

    def is_file(self) -> bool:
        pass

    def is_directory(self) -> bool:
        pass

    def get_path(self) -> str:
        pass

    def get_real_path(self) -> str:
        pass

    def get_base_name(self) -> str:
        pass

    def get_parent(self) -> VirtualFile | None:
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