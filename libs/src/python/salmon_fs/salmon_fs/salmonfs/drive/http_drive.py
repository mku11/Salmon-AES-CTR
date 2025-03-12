from __future__ import annotations

__license__ = """
MIT License

Copyright (c) 2025 Max Kas

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
import platform
from typeguard import typechecked

from salmon_fs.fs.file.file import File
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.file.ivirtual_file import IVirtualFile
from salmon_fs.salmonfs.drive.aes_drive import AesDrive
from salmon_fs.salmonfs.file.aes_file import AesFile


@typechecked
class HttpDrive(AesDrive):
    """
    AesDrive implementation for Python HTTP files. This provides a virtual drive implementation
    that you can use to access encrypted files.
    """

    def __init__(self):
        """
        Private constructor, use open() or create() instead.
        """
        super().__init__()

    @staticmethod
    def open(v_dir: IFile, password: str) -> AesDrive:
        """
        Helper method that opens and initializes a JavaDrive
        :param v_dir: The directory that hosts the drive.
        :param password: The password.
        :return: The drive.
        """
        return AesDrive.open_drive(v_dir, HttpDrive, password)

    def get_private_dir(self) -> IFile:
        """
        Get a private dir for sharing files with external applications.
        :return: The private file
        :raises Exception:         """
        file_folder: str | None = None
        platform_os: str = platform.system().upper()
        if "WIN" in platform_os:
            file_folder = os.getenv("APPDATA") + "\\" + "Salmon"
        elif "MAC" in platform_os:
            file_folder = os.path.expanduser("~") + "/Library/Application " + "/" + "Salmon"
        elif "LINUX" in platform_os:
            file_folder = os.path.expanduser("~") + ".Salmon"

        if file_folder is None:
            Exception("Operating System not supported")
        return File(file_folder)

    def _on_unlock_success(self):
        """
        When authorization succeed.
        """
        print("drive unlocked")

    def _on_unlock_error(self):
        """
        When authorization succeeds.
        """
        print("drive failed to unlock")

    def get_virtual_file(self, file: IFile) -> IVirtualFile | None:
        """
        Get the virtual file backed by a real file
        :param file: The file
        :return: The
        """
        return AesFile(file, self)

    def get_root(self) -> AesFile | None:
        """
        Get the drive root
        :return: The drive root directory
        """
        return super().get_root()
