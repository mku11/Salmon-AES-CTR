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

from abc import ABC, abstractmethod

from typeguard import typechecked


@typechecked
class ICTRTransformer(ABC):
    """
    Contract for the encryption/decryption transformers.
    Note that Counter mode needs to be supported.
    """

    @abstractmethod
    def init(self, key: bytearray, nonce: bytearray):
        """
        Initialize the transformer.
        :param key: The AES key to use.
        :param nonce: The nonce to use.
        :raises IntegrityException: Thrown when security error
        """
        pass

    @abstractmethod
    def encrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
        Encrypt the data.
        :param src_buffer: The source byte array.
        :param src_offset: The source byte offset.
        :param dest_buffer: The destination byte array.
        :param dest_offset: The destination byte offset.
        :param count: The number of bytes to transform.
        :return: The number of bytes transformed.
        :raises IntegrityException: Thrown when security error
        :raises SalmonRangeExceededException: Thrown when maximum nonce range is exceeded.
        """
        pass

    @abstractmethod
    def decrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
        Decrypt the data.
        :param src_buffer: The source byte array.
        :param src_offset: The source byte offset.
        :param dest_buffer: The destination byte array.
        :param dest_offset: The destination byte offset.
        :param count: The number of bytes to transform.
        :return: The number of bytes transformed.
        :raises IntegrityException: Thrown when security error
        :raises SalmonRangeExceededException: Thrown when maximum nonce range is exceeded.
        """
        pass

    @abstractmethod
    def get_counter(self) -> bytearray:
        """
        Get the current counter.
        :return: The counter
        """
        pass

    @abstractmethod
    def get_key(self) -> bytearray:
        """
        Get the current encryption key.
        :return: The key
        """
        pass

    @abstractmethod
    def get_block(self) -> int:
        """
        Get the current block.
        :return: The block
        """
        pass

    @abstractmethod
    def get_nonce(self) -> bytearray:
        """
        Get the nonce (initial counter) to be used for the data.
        :return: The nonce
        """
        pass

    @abstractmethod
    def reset_counter(self):
        """
        Reset the counter to the nonce (initial counter).
        """
        pass

    @abstractmethod
    def sync_counter(self, position: int):
        """
        Calculate the value of the counter based on the current block. After an encryption
        operation (ie sync or read) the block will be incremented. This method calculates
        the Counter.
        :param position:         :raises SalmonRangeExceededException: Thrown when maximum nonce range is exceeded.
        """
        pass
