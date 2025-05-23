#!/usr/bin/env python3
"""!@brief Pbkdf provider interfaces
"""


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

from salmon_core.salmon.password.pbkdf_algo import PbkdfAlgo
from salmon_core.salmon.security_exception import SecurityException
from abc import ABC, abstractmethod
from typeguard import typechecked


@typechecked
class ISalmonPbkdfProvider(ABC):
    """!
    Provider interface for key derivation text passwords.
    """

    PBKDF_SHA256: str = "sha256"
    """
    Python Cipher key for SHA256. See cryptodome
    """

    @staticmethod
    def get_pbkdf_algo_string(pbkdf_algo: PbkdfAlgo) -> str:
        """!
        Get the PBKDF python cipher algorithm string.

        @param pbkdf_algo: The PBKDF algorithm to be used
        @returns The python cipher algorithm string. See cryptodome
        """
        match pbkdf_algo:
            case PbkdfAlgo.SHA256:
                return ISalmonPbkdfProvider.PBKDF_SHA256
        raise SecurityException("Unknown pbkdf algorithm")

    @abstractmethod
    def get_key(self, password: str, salt: bytearray, iterations: int, output_bytes: int,
                pbkdf_algo: PbkdfAlgo) -> bytearray:
        """!
        Get a key derived from a text password.

        @param password: The text password.
        @param salt: The salt needs to be at least 24 bytes.
        @param iterations: Iterations to use. Make sure you use a high number according to your hardware specs.
        @param output_bytes: The length of the output key.
        @param pbkdf_algo: The hash algorithm to use.
        @returns The key.
        :raises: SalmonSecurityException
        """
        pass
