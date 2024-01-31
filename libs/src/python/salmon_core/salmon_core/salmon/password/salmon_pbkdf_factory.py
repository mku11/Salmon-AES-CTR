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
from salmon_core.salmon.password.isalmon_pbkdf_provider import ISalmonPbkdfProvider
from salmon_core.salmon.password.pbkdf_type import PbkdfType
from salmon_core.salmon.password.salmon_default_pbkdf_provider import SalmonDefaultPbkdfProvider

from typeguard import typechecked


@typechecked
class SalmonPbkdfFactory:
    """
     * Creates AES transformer implementations.
    """

    @staticmethod
    def create(pbkdf_type: PbkdfType) -> ISalmonPbkdfProvider:
        """
         * Create an instance of a pbkdf provider.
         * @param type The pbkdf type.
         * @return The provider.
        """
        match pbkdf_type:
            case PbkdfType.Default:
                return SalmonDefaultPbkdfProvider()
        raise RuntimeError("Unknown Pbkdf provider type")
