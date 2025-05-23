#!/usr/bin/env python3
"""!@brief Base64 encoders/decoders.
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

from typeguard import typechecked
import base64
from salmon_core.convert.ibase_64 import IBase64


@typechecked
class Base64(IBase64):
    """!
    Base64 encoder/decoder.
    """

    def decode(self, text: str) -> bytearray:
        """!
        Decode a Base64 encoded string into a byte array.
        @param text: String to be decoded
        @returns Byte array of decoded data.
        """
        return bytearray(base64.b64decode(text))

    def encode(self, data: bytearray) -> str:
        """!
        Encode a byte array into a Base64 encoded string.
        @param data: Byte array to be encoded
        @returns String of encoded data.
        """
        return base64.b64encode(data).decode('utf-8')
