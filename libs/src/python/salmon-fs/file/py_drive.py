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
import platform

from typeguard import typechecked

from file.py_file import PyFile
from file.ireal_file import IRealFile
from file.virtual_file import VirtualFile
from salmonfs.salmon_drive import SalmonDrive
from salmonfs.salmon_file import SalmonFile

@typechecked
class PyDrive(SalmonDrive):
    """
     * SalmonDrive implementation for standard Python file API. This provides a virtual drive implementation
     * that you can use to store and access encrypted files.
    """

    def __init__(self, real_root: str, create_if_not_exists: bool):
        """
         * Instantiate a virtual drive with the provided real filepath.
         * Encrypted files will be located under the {@link SalmonDrive#virtual_drive_directory_name}.
         * @param real_root The filepath to the location of the virtual drive.
         * @param create_if_not_exists Create the drive if it doesn't exist.
        """
        super().__init__(real_root, create_if_not_exists)

    @staticmethod
    def get_private_dir() -> str:
        """
         * Get a private dir for sharing files with external applications.
         * @return
         * @throws Exception
        """
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
        return file_folder

    def get_real_file(self, filepath: str, is_directory: bool) -> IRealFile:
        """
         * Get a file from the real filesystem.
         * @param filepath The file path.
         * @param is_directory True if filepath corresponds to a directory.
         * @return
        """
        py_file: PyFile = PyFile(filepath)
        return py_file

    def _on_authentication_success(self):
        """
         * When authentication succeed.
        """
        pass

    def _on_authentication_error(self):
        """
         * When authentication succeeds.
        """
        pass

    def _create_virtual_root(self, virtual_root_real_file: IRealFile) -> VirtualFile:
        return SalmonFile(virtual_root_real_file, self)