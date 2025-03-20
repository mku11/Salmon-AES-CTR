#!/usr/bin/env python3
"""!@brief Salmon AES transformer based on the cryptodome routines.
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

from Crypto.Cipher import AES
from Crypto.Util import Counter

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.salmon.security_exception import SecurityException
from salmon_core.salmon.transform.aes_ctr_transformer import AESCTRTransformer

from typeguard import typechecked


@typechecked
class AesDefaultTransformer(AESCTRTransformer):
    """!
    Salmon AES transformer based on the cryptodome routines.
    """

    def __init__(self):
        super().__init__()

    def init(self, key: bytearray, nonce: bytearray):
        """!
        Initialize the default Python AES cipher transformer.
        @param key: The AES256 key to use.
        @param nonce: The nonce to use.
        @exception IntegrityException: Thrown when security error
        """
        super().init(key, nonce)

    def encrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """!
        Encrypt the data.
        @param src_offset: The source byte array.
        @param src_buffer: The source byte offset.
        @param dest_buffer: The destination byte array.
        @param dest_offset: The destination byte offset.
        @param count: The number of bytes to transform.
        @returns The number of bytes transformed.
        @exception IntegrityException: Thrown when security error
        """
        if self.get_key() is None:
            raise SecurityException("No key defined, run init first")
        try:
            counter: bytearray = self.get_counter()
            ctr = Counter.new(len(counter) * 8, initial_value=BitConverter.to_long(counter, 0, len(counter)))
            cipher = AES.new(self.get_key(), AES.MODE_CTR, counter=ctr)
            v_bytes = cipher.encrypt(memoryview(src_buffer)[src_offset:src_offset + count])
            dest_buffer[dest_offset:dest_offset + count] = v_bytes[0:count]
            return count
        except Exception as ex:
            raise SecurityException("Could not encrypt data: ") from ex

    def decrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """!
        Decrypt the data.
        @param src_buffer: The source byte array.
        @param src_offset: The source byte offset.
        @param dest_buffer: The destination byte array.
        @param dest_offset: The destination byte offset.
        @param count: The number of bytes to transform.
        @returns The number of bytes transformed.
        @exception IntegrityException: Thrown when security error
        """

        if self.get_key() is None:
            raise SecurityException("No key defined, run init first")
        try:
            counter: bytearray = self.get_counter()
            ctr = Counter.new(len(counter) * 8, initial_value=BitConverter.to_long(counter, 0, len(counter)))
            cipher = AES.new(self.get_key(), AES.MODE_CTR, counter=ctr)
            v_bytes = cipher.decrypt(memoryview(src_buffer)[src_offset:src_offset + count])
            dest_buffer[dest_offset:dest_offset + count] = v_bytes[0:count]
            return count
        except Exception as ex:
            raise SecurityException("Could not decrypt data: ") from ex
