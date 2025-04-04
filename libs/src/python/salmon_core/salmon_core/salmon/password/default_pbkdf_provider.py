#!/usr/bin/env python3
"""!@brief Pbkdf Providers
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
from salmon_core.salmon.password.ipbkdf_provider import ISalmonPbkdfProvider
from salmon_core.salmon.password.pbkdf_algo import PbkdfAlgo

import hashlib

from salmon_core.salmon.security_exception import SecurityException

from typeguard import typechecked


@typechecked
class DefaultPbkdfProvider(ISalmonPbkdfProvider):
    """!
    Provider for Pbkdf via Python default algorithm.
    """

    def get_key(self, password: str, salt: bytearray, iterations: int, output_bytes: int,
                pbkdf_algo: PbkdfAlgo) -> bytearray:
        """!
        Get a key derived from a text password
        @param password: The text password.
        @param salt: The salt needs to be at least 24 bytes.
        @param iterations: Iterations to use. Make sure you use a high number according to your hardware specs.
        @param output_bytes: The length of the output key.
        @param pbkdf_algo: The hash algorithm to use.
        @returns The key.
        @exception IntegrityException: Thrown when security error
        """
        # PBEKeySpec
        # keySpec = new
        # PBEKeySpec(password.toCharArray(), salt, iterations, outputBytes * 8)

        pbkdf_algo_str: str = ISalmonPbkdfProvider.get_pbkdf_algo_string(pbkdf_algo)
        key: bytearray | None = None
        try:
            key = bytearray(
                hashlib.pbkdf2_hmac(pbkdf_algo_str, password.encode("utf-8"), salt, iterations, output_bytes))
        except Exception as e:
            raise SecurityException("Could not initialize pbkdf") from e
        return key
