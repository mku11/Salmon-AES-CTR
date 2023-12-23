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

from typeguard import typechecked


@typechecked
class SalmonDefaultOptions:
    """
     * Global options for salmon operations.
    """

    DEFAULT_BUFFER_SIZE = 256 * 1024
    """
     Default buffer size
    """

    @staticmethod
    def get_buffer_size() -> int:
        """
         * Get the default buffer size for all internal streams including Encryptors and Decryptors.
         * @return
        """
        return SalmonDefaultOptions.__buffer_size

    __buffer_size = DEFAULT_BUFFER_SIZE
    """
     * Default buffer size for all internal streams including Encryptors and Decryptors
    """

    @staticmethod
    def set_buffer_size(buffer_size: int):
        """
         * Set the default buffer size for all internal streams including Encryptors and Decryptors.
         *
         * @param buffer_size
        """
        SalmonDefaultOptions.__buffer_size = buffer_size
