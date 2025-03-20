#!/usr/bin/env python3
"""!@brief Interface to native libraries that provide AES-256 encryption in CTR mode.
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

from abc import ABC, abstractmethod
from typeguard import typechecked


@typechecked
class INativeProxy(ABC):
    """
    Interface to native libraries that provide AES-256 encryption in CTR mode.
    """

    @abstractmethod
    def salmon_init(self, aes_impl: int):
        """!
        Initializes the native library with the specified AES implementation.
        @param aes_impl:         """
        pass

    @abstractmethod
    def salmon_expand_key(self, key: bytearray, expanded_key: bytearray):
        """!
        Expands the specified AES encryption key.
        @param key: The AES-256 encryption key (32 bytes)
        @param expanded_key: The expanded key (240 bytes)
        """
        pass

    @abstractmethod
    def salmon_transform(self, key: bytearray, counter: bytearray,
                         src_buffer: bytearray, src_offset: int,
                         dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """!
        Transforms data using CTR mode which is symmetric so you should use it for both encryption and decryption.
        @param key: The AES-256 encryption key (32 bytes)
        @param counter: The counter (16 bytes)
        @param src_buffer: The source buffer
        @param src_offset: The source buffer offset
        @param dest_buffer: The destination buffer
        @param dest_offset: The destination buffer offset
        @param count: The number of bytes to transform
        @returns The number of bytes transformed
        """
        pass
