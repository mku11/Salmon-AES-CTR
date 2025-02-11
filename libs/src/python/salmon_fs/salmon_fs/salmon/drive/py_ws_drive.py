'''
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
'''
from __future__ import annotations
import os
import platform

from typeguard import typechecked

from salmon_fs.file.py_file import PyFile
from salmon_fs.file.py_ws_file import PyWSFile, Credentials
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.ivirtual_file import IVirtualFile
from salmon_fs.salmon.salmon_drive import SalmonDrive
from salmon_fs.salmon.salmon_file import SalmonFile
from salmon_fs.sequence.inonce_sequencer import INonceSequencer


@typechecked
class PyWSDrive(SalmonDrive):
    """
    SalmonDrive implementation for web service Python file API. This provides a virtual drive implementation
    that you can use to store and access encrypted files.
    """
    service_credentials: dict[IRealFile, Credentials] = dict()

    def __init__(self):
        """
        Private constructor, use open() or create() instead.
        """
        super().__init__()

    @staticmethod
    def open(v_dir: IRealFile, password: str, sequencer: INonceSequencer,
             service_user: str, service_password: str) -> SalmonDrive:
        """
        Helper method that opens and initializes a JavaDrive
        :param v_dir: The directory that hosts the drive.
        :param password: The password.
        :param sequencer: The nonce sequencer that will be used for encryption.
        :param service_user: The web service username
        :param service_password: The web service password
        :return: The drive.
        """
        PyWSDrive.service_credentials[v_dir] = Credentials(service_user, service_password)
        return SalmonDrive.open_drive(v_dir, PyWSDrive, password, sequencer)

    @staticmethod
    def create(v_dir: IRealFile, password: str, sequencer: INonceSequencer,
               service_user: str, service_password: str) -> SalmonDrive:
        """
        Helper method that creates and initializes a JavaDrive
        :param v_dir: The directory that will host the drive.
        :param password: The password.
        :param sequencer: The nonce sequencer that will be used for encryption.
        :param service_user: The web service username
        :param service_password: The web service password
        :return: The drive.
        """
        PyWSDrive.service_credentials[v_dir] = Credentials(service_user, service_password)
        return SalmonDrive.create_drive(v_dir, PyWSDrive, password, sequencer)

    def get_private_dir(self) -> IRealFile:
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
        return PyFile(file_folder)

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

    def get_file(self, file: IRealFile) -> IVirtualFile | None:
        """
        Get the virtual file backed by a real file
        :param file: The file
        :return: The
        """
        return SalmonFile(file, self)

    def get_root(self) -> SalmonFile | None:
        """
        Get the drive root
        :return: The drive root directory
        """
        return super().get_root()

    def initialize(self, real_root: IRealFile | PyWSFile, create_if_not_exists: bool):
        """
        Initialize a web service drive at the directory path provided

        :param real_root: The directory for the drive
        :param create_if_not_exists: Create the drive if it does not exist
        """
        credentials: Credentials = PyWSDrive.service_credentials[real_root]
        if credentials:
            real_root.set_credentials(credentials)
        super().initialize(real_root, create_if_not_exists)
