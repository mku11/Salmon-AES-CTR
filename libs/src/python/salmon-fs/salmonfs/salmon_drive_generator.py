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

from convert.bit_converter import BitConverter
from salmon.salmon_generator import SalmonGenerator


class SalmonDriveGenerator:
    """
     * Utility class generates internal secure properties for the drive.
    """

    IV_LENGTH = 16
    """
     * Initial vector length that will be used for encryption and master encryption of the combined key
    """

    COMBINED_KEY_LENGTH = SalmonGenerator.KEY_LENGTH + SalmonGenerator.HASH_KEY_LENGTH
    """
     * combined key is drive key + hash key.
    """

    SALT_LENGTH = 24
    """
     * Salt length.
    """

    DRIVE_ID_LENGTH = 16
    """
     * Drive ID size.
    """

    AUTH_ID_SIZE = 16
    """
     * Auth ID size
    """

    ITERATIONS_LENGTH = 4
    """
     * Length for the iterations that will be stored in the encrypted data header.
    """

    MASTER_KEY_LENGTH = 32
    """
     * Master key to encrypt the combined key we also use AES256.
    """

    __iterations = 65536
    """
     * Global default iterations that will be used for the master key derivation.
    """

    LONG_MAX_VALUE = (2 << 62) - 1

    @staticmethod
    def generate_drive_id() -> bytearray:
        """
         * Generate a Drive ID.
         * @return The Drive ID.
        """
        return SalmonGenerator.get_secure_random_bytes(SalmonDriveGenerator.DRIVE_ID_LENGTH)

    @staticmethod
    def generate_auth_id() -> bytearray:
        """
         * Generate a secure random authentication ID.
         * @return The authentication Id (16 bytes).
        """
        return SalmonGenerator.get_secure_random_bytes(SalmonDriveGenerator.AUTH_ID_SIZE)

    @staticmethod
    def generate_combined_key() -> bytearray:
        """
         * Generates a secure random combined key (drive key + hash key)
         * @return The length of the combined key.
        """
        return SalmonGenerator.get_secure_random_bytes(SalmonDriveGenerator.COMBINED_KEY_LENGTH)

    @staticmethod
    def generate_master_key_iv() -> bytearray:
        """
         * Generates the initial vector that will be used with the master key
         * to encrypt the combined key (drive key + hash key)
        """
        return SalmonGenerator.get_secure_random_bytes(SalmonDriveGenerator.IV_LENGTH)

    @staticmethod
    def generate_salt() -> bytearray:
        """
         * Generates a salt.
         * @return The salt byte array.
        """
        return SalmonGenerator.get_secure_random_bytes(SalmonDriveGenerator.SALT_LENGTH)

    @staticmethod
    def get_starting_nonce() -> bytearray:
        """
         * Get the starting nonce that will be used for encrypt drive files and filenames.
         * @return A secure random byte array (8 bytes).
        """
        v_bytes: bytearray = bytearray(SalmonGenerator.NONCE_LENGTH)
        return v_bytes

    @staticmethod
    def get_max_nonce() -> bytearray:
        """
         * Get the default max nonce to be used for drives.
         * @return A secure random byte array (8 bytes).
        """
        return BitConverter.toBytes(SalmonDriveGenerator.LONG_MAX_VALUE, 8)

    @staticmethod
    def get_iterations() -> int:
        """
         * Returns the iterations used for deriving the combined key from
         * the text password
         * @return The current iterations for the key derivation.
        """
        return SalmonDriveGenerator.__iterations

    @staticmethod
    def set_iterations(iterations: int):
        """
         * Set the default iterations.
         * @param iterations
        """
        SalmonDriveGenerator.iterations = iterations
