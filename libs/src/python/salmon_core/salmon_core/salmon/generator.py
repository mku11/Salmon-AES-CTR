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

import secrets

from typeguard import typechecked


@typechecked
class Generator:
    """
    Utility class generates internal secure properties.
    """

    VERSION = 2
    """
    * Version.
    """

    MAGIC_LENGTH = 3
    """
    Length for the magic bytes.
    """

    VERSION_LENGTH = 1
    """
    Length for the Version in the data header.
    """

    BLOCK_SIZE = 16
    """
    Should be 16 for AES256 the same as the iv.
    """

    KEY_LENGTH = 32
    """
    Encryption key length for AES256.
    """

    HASH_KEY_LENGTH = 32
    """
    HASH Key length for integrity, currently we use HMAC SHA256.
    """

    HASH_RESULT_LENGTH = 32
    """
    Hash signature size for integrity, currently we use HMAC SHA256.
    """

    NONCE_LENGTH = 8
    """
    Nonce size.
    """

    CHUNK_SIZE_LENGTH = 4
    """
    Chunk size format length.
    """

    MAGIC_BYTES = "SLM"
    """
    Magic bytes.
    """

    @staticmethod
    def get_magic_bytes() -> bytearray:
        """!
        Gets the fixed magic bytes array
        """
        return bytearray(Generator.MAGIC_BYTES.encode('utf-8'))

    @staticmethod
    def get_version() -> int:
        """!
        Returns the current Salmon format version.
        """
        return Generator.VERSION

    @staticmethod
    def get_secure_random_bytes(size: int) -> bytearray:
        """!
        Returns a secure random byte array. To be used when generating keys, initial vectors, and nonces.
        @param size: The size of the byte array.
        @returns The random secure byte array.
        """
        v_bytes: bytearray = bytearray(secrets.token_bytes(size))
        return v_bytes
