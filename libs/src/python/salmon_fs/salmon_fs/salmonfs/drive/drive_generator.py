#!/usr/bin/env python3
"""!@brief Utility class generates internal secure properties for the drive.
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

from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.salmon.generator import Generator


@typechecked
class DriveGenerator:
    """!
    Utility class generates internal secure properties for the drive.
    """

    IV_LENGTH = 16
    """
    Initial vector length that will be used for encryption and master encryption of the combined key
    """

    COMBINED_KEY_LENGTH = Generator.KEY_LENGTH + Generator.HASH_KEY_LENGTH
    """
    combined key is drive key + hash key.
    """

    SALT_LENGTH = 24
    """
    Salt length.
    """

    DRIVE_ID_LENGTH = 16
    """
    Drive ID size.
    """

    AUTH_ID_SIZE = 16
    """
    Auth ID size
    """

    ITERATIONS_LENGTH = 4
    """
    Length for the iterations that will be stored in the encrypted data header.
    """

    MASTER_KEY_LENGTH = 32
    """
    Master key to encrypt the combined key we also use AES256.
    """

    __iterations = 65536
    """
    Global default iterations that will be used for the master key derivation.
    """

    LONG_MAX_VALUE = (2 << 62) - 1

    @staticmethod
    def generate_drive_id() -> bytearray:
        """!
        Generate a Drive ID.
        @returns The Drive ID.
        """
        return Generator.get_secure_random_bytes(DriveGenerator.DRIVE_ID_LENGTH)

    @staticmethod
    def generate_auth_id() -> bytearray:
        """!
        Generate a secure random authorization ID.
        @returns The authorization Id (16 bytes).
        """
        return Generator.get_secure_random_bytes(DriveGenerator.AUTH_ID_SIZE)

    @staticmethod
    def generate_combined_key() -> bytearray:
        """!
        Generates a secure random combined key (drive key + hash key)
        @returns The length of the combined key.
        """
        return Generator.get_secure_random_bytes(DriveGenerator.COMBINED_KEY_LENGTH)

    @staticmethod
    def generate_master_key_iv() -> bytearray:
        """!
        Generates the initial vector that will be used with the master key
        to encrypt the combined key (drive key + hash key)
        """
        return Generator.get_secure_random_bytes(DriveGenerator.IV_LENGTH)

    @staticmethod
    def generate_salt() -> bytearray:
        """!
        Generates a salt.
        @returns The salt byte array.
        """
        return Generator.get_secure_random_bytes(DriveGenerator.SALT_LENGTH)

    @staticmethod
    def get_starting_nonce() -> bytearray:
        """!
        Get the starting nonce that will be used for encrypt drive files and filenames.
        @returns A secure random byte array (8 bytes).
        """
        v_bytes: bytearray = bytearray(Generator.NONCE_LENGTH)
        return v_bytes

    @staticmethod
    def get_max_nonce() -> bytearray:
        """!
        Get the default max nonce to be used for drives.
        @returns A secure random byte array (8 bytes).
        """
        return BitConverter.to_bytes(DriveGenerator.LONG_MAX_VALUE, 8)

    @staticmethod
    def get_iterations() -> int:
        """!
        Returns the iterations used for deriving the combined key from
        the text password
        @returns The current iterations for the key derivation.
        """
        return DriveGenerator.__iterations

    @staticmethod
    def set_iterations(iterations: int):
        """!
        Set the default iterations.
        @param iterations:         """
        DriveGenerator.iterations = iterations
