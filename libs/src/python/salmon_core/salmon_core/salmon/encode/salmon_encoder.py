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

from salmon_core.convert.base_64 import Base64
from salmon_core.convert.ibase_64 import IBase64

from typeguard import typechecked


@typechecked
class SalmonEncoder:
    """
     * Provides generic encoder (ie Base64).
    """

    __base64: IBase64 = Base64()
    """
     * Current global Base64 implementation for encrypting/decrypting text strings. To change use set_base64().
    """

    @staticmethod
    def set_base64(base64: IBase64):
        """
         * Change the current global Base64 implementation.
         * @param base64 The new Base64 implementation.
        """
        SalmonEncoder.base64 = base64

    @staticmethod
    def get_base64() -> IBase64:
        """
         * Get the global default Base64 implementation.
         * @return The Base64 implementation.
        """
        return SalmonEncoder.__base64
