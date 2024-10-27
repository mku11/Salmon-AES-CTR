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
from salmon_core.bridge.inative_proxy import INativeProxy
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_core.salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer

from typeguard import typechecked


@typechecked
class SalmonNativeTransformer(SalmonAES256CTRTransformer):
    """
    Generic Native AES transformer. Extend this with your specific
    native transformer.
    """

    __nativeProxy: INativeProxy = NativeProxy()
    """
    The native proxy to use for loading libraries for different platforms and operating systems.
    """

    __implType: int = 1

    @staticmethod
    def set_native_proxy(proxy: INativeProxy):
        SalmonNativeTransformer.__nativeProxy = proxy

    @staticmethod
    def get_native_proxy() -> INativeProxy:
        return SalmonNativeTransformer.__nativeProxy

    def __init__(self, implType: int):
        """
        Constructs a transformer that will use the native aes implementations
        :param implType: The AES native implementation see ProviderType enum
        """
        super().__init__()
        self.__implType = implType

    def init(self, key: bytearray, nonce: bytearray):
        """
        Initialize the native transformer.
        :param key: The AES key to use.
        :param nonce: The nonce to use.
        :raises IntegrityException: Thrown when security error
        """
        self.get_native_proxy().salmon_init(self.__implType)
        expanded_key: bytearray = bytearray(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE)
        SalmonNativeTransformer.get_native_proxy().salmon_expand_key(key, expanded_key)
        self.set_expanded_key(expanded_key)
        super().init(key, nonce)

    def encrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
        Encrypt the data.
        :param srcBuffer: The source byte array.
        :param srcOffset: The source byte offset.
        :param destBuffer: The destination byte array.
        :param destOffset: The destination byte offset.
        :param count: The number of bytes to transform.
        :return: The number of bytes transformed.
        """

        return SalmonNativeTransformer.__nativeProxy.salmon_transform(self.get_expanded_key(), self.get_counter(),
                                                                      src_buffer, src_offset,
                                                                      dest_buffer, dest_offset, count)

    def decrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
        Decrypt the data.
        :param srcBuffer: The source byte array.
        :param srcOffset: The source byte offset.
        :param destBuffer: The destination byte array.
        :param destOffset: The destination byte offset.
        :param count: The number of bytes to transform.
        :return: The number of bytes transformed.
        """

        return SalmonNativeTransformer.__nativeProxy.salmon_transform(self.get_expanded_key(), self.get_counter(),
                                                                      src_buffer, src_offset,
                                                                      dest_buffer, dest_offset, count)
