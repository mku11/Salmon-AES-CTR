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
class IBase64(ABC):
    """
    Interface for a Base64 encoder/decoder.
    """

    @abstractmethod
    def decode(self, text: str) -> bytearray:
        """
        Decode a Base64 encoded string into a byte array.
        :param text: String to be decoded
        :return: Byte array of decoded data.
        """
        pass

    @abstractmethod
    def encode(self, data: bytearray) -> str:
        """
        Encode a byte array into a Base64 encoded string.
        :param data: Byte array to be encoded
        :return: String of encoded data.
        """
        pass
