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

from enum import Enum

from typeguard import typechecked


@typechecked
class ProviderType(Enum):
    """
    AES provider types. List of AES implementations that currently supported.
    
    @see #Default
    @see #AesIntrinsics
    @see #TinyAES
    """

    Default = 0
    """
    Default AES cipher.
    """

    AesIntrinsics = 1
    """
    Salmon builtin AES intrinsics. This needs the SalmonNative library to be loaded. 
     @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
    """

    TinyAES = 2
    """
    Tiny AES implementation. This needs the SalmonNative library to be loaded. 
     @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
    """
