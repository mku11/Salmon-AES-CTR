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
from abc import ABC, abstractmethod

from typeguard import typechecked


@typechecked
class INativeProxy(ABC):
    """
     * Proxy interface for use with native libraries for different platforms and operating systems.
    """

    @abstractmethod
    def salmon_init(self, aes_impl: int):
        """
         * Proxy Init the native code with AES implementation, and hash length options.
         * @param aesImpl
        """
        pass

    @abstractmethod
    def salmon_expand_key(self, key: bytearray, expanded_key: bytearray):
        """
         * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
         * for AES 256.
         * @param key
         * @param expandedKey
        """
        pass

    @abstractmethod
    def salmon_transform(self, key: bytearray, counter: bytearray,
                         src_buffer: bytearray, src_offset: int,
                         dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
         * Proxy Transform the input byte array using AES 256 encryption or decryption mode.
         * @param key
         * @param counter
         * @param srcBuffer
         * @param srcOffset
         * @param destBuffer
         * @param destOffset
         * @param count
         * @return
        """
        pass
