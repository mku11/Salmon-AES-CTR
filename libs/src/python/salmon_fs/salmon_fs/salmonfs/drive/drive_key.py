#!/usr/bin/env python3
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

from typeguard import typechecked


@typechecked
class DriveKey:
    """
    Encryption keys and properties.
    """

    def __init__(self):
        self.__masterKey: bytearray | None = None
        self.__driveKey: bytearray | None = None
        self.__hash_key: bytearray | None = None
        self.__iterations: int = 0

    def clear(self):
        """
        Clear the properties from memory.
        """
        if self.__driveKey is not None:
            self.__driveKey[0:len(self.__driveKey)] = [0] * len(self.__driveKey)
        self.__driveKey = None

        if self.__hash_key is not None:
            self.__hash_key[0:len(self.__hash_key)] = [0] * len(self.__hash_key)
        self.__hash_key = None

        if self.__masterKey is not None:
            self.__masterKey[0:len(self.__masterKey)] = [0] * len(self.__masterKey)
        self.__masterKey = None
        self.__iterations = 0

    def get_drive_key(self) -> bytearray | None:
        """
        Function returns the encryption key that will be used to encrypt/decrypt the files
        """
        return self.__driveKey

    def get_hash_key(self) -> bytearray | None:
        """
        Function returns the hash key that will be used to sign the file chunks
        """
        return self.__hash_key

    def set_drive_key(self, drive_key: bytearray):
        """
        Set the drive key.
        :param drive_key:         """
        self.__driveKey = drive_key

    def set_hash_key(self, hash_key: bytearray):
        """
        Set the hash key.
        :param hash_key:         """
        self.__hash_key = hash_key

    def get_master_key(self) -> bytearray:
        """
        Get the master key.
        :return: The master key
        """
        return self.__masterKey

    def set_master_key(self, master_key: bytearray):
        """
        Set the master key.
        :param master_key:         """
        self.__masterKey = master_key

    def get_iterations(self) -> int:
        """
        Get the number of iterations for the master key derivation.
        :return: The iterations
        """
        return self.__iterations

    def set_iterations(self, iterations: int):
        """
        Set the number of iterations for the master key derivation.
        :param iterations:         """
        self.__iterations = iterations
