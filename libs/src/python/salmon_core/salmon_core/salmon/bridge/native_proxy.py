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
import ctypes

from salmon.bridge.inative_proxy import INativeProxy

from typeguard import typechecked
import sys


@typechecked
class NativeProxy(INativeProxy):
    """
    Proxy class for use with windows native library.
    """

    __loaded: bool = False
    """
    If proxy is loaded 
    """

    __library_path: str = "salmon.dll"
    """
    The dll path for the salmonfs library.
    """

    @staticmethod
    def set_library_path(library_path: str):
        NativeProxy.__library_path = library_path

    @staticmethod
    def get_char_array(v_bytes: bytearray):
        return (ctypes.c_char * len(v_bytes)).from_buffer(bytearray(v_bytes))

    def salmon_init(self, aes_impl: int):
        """
        Proxy Init the native code with AES implementation, and hash length options.
        :param aes_impl:         """
        self._load_library()
        NativeProxy.__init(aes_impl)

    def _load_library(self):
        """
        Load the native library
        """
        if NativeProxy.__loaded:
            return
        try:
            lib = ctypes.CDLL(NativeProxy.__library_path)
            NativeProxy.__init = lib.salmon_init
            NativeProxy.__init.argtypes = [ctypes.c_int]

            NativeProxy.__expand_key = lib.salmon_expandKey
            NativeProxy.__expand_key.argtypes = [ctypes.c_char_p, ctypes.c_char_p]

            NativeProxy.__transform = lib.salmon_transform
            NativeProxy.__transform.argtypes = [ctypes.c_char_p, ctypes.c_char_p,
                                                ctypes.c_char_p, ctypes.c_int,
                                                ctypes.c_char_p, ctypes.c_int, ctypes.c_int]
            NativeProxy.__transform.restype = ctypes.c_int

        except Exception as ex:
            print(ex, file=sys.stderr)

        NativeProxy.__loaded = True

    def salmon_expand_key(self, key: bytearray, expanded_key: bytearray):
        """
        Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
        :param key: The key
        :param expanded_key: The expanded key
        """
        c_key = NativeProxy.get_char_array(key)
        c_expanded_key = NativeProxy.get_char_array(expanded_key)
        NativeProxy.__expand_key(c_key, c_expanded_key)
        expanded_key[0:len(expanded_key)] = bytearray(c_expanded_key)

    def salmon_transform(self, key: bytearray, counter: bytearray,
                         src_buffer: bytearray, src_offset: int, dest_buffer: bytearray, dest_offset: int,
                         count: int) -> int:
        """
        Proxy Transform the input byte array using AES 256 using encryption or decryption mode.
        :param key: The encryption key
        :param counter: The counter
        :param src_buffer: The source buffer
        :param src_offset: The source buffer offset
        :param dest_buffer: The destination buffer
        :param dest_offset: The destination buffer offset
        :param count: The number of bytes to transform
        :return: The number of bytes transformed
        """

        c_key = NativeProxy.get_char_array(key)
        c_counter = NativeProxy.get_char_array(counter)
        c_src_buffer = NativeProxy.get_char_array(src_buffer)
        c_dest_buffer = NativeProxy.get_char_array(dest_buffer)
        v_bytes: int = NativeProxy.__transform(c_key, c_counter,
                                               c_src_buffer, src_offset,
                                               c_dest_buffer, dest_offset, count)
        dest_buffer[0:len(dest_buffer)] = bytearray(c_dest_buffer)
        return v_bytes
