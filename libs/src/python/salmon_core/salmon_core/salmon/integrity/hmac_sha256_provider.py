#!/usr/bin/env python3
"""!@brief Python HMAC256 hashing.
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

import hashlib
import hmac

from salmon.integrity.ihash_provider import IHashProvider
from salmon_core.salmon.integrity.integrity_exception import IntegrityException

from typeguard import typechecked


@typechecked
class HmacSHA256Provider(IHashProvider):
    """!
    Provides Python HMAC256 hashing.
    """

    def calc(self, hash_key: bytearray, buffer: bytearray, offset: int, count: int) -> bytearray:
        """!
        Calculate HMAC SHA256 hash for a byte buffer.
        @param hash_key: The HMAC SHA256 key to use for hashing (32 bytes).
        @param buffer: The buffer to read the data from.
        @param offset: The position reading will start from.
        @param count: The count of bytes to be read.
        @returns The HMAC SHA256 hash.
        @exception IntegrityException: thrown if hash cannot be calculated
        """
        try:
            v_hmac = hmac.new(hash_key, memoryview(buffer)[offset:offset + count], digestmod=hashlib.sha256)
            hash_value: bytearray = bytearray(v_hmac.digest())
            return hash_value
        except Exception as ex:
            raise IntegrityException("Could not calculate HMAC") from ex
