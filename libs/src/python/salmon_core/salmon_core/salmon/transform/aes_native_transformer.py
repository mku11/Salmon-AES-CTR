#!/usr/bin/env python3
"""!@brief Generic Native AES transformer. 
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

from salmon.bridge.inative_proxy import INativeProxy
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_core.salmon.transform.aes_ctr_transformer import AESCTRTransformer
from salmon_core.salmon.security_exception import SecurityException

from typeguard import typechecked
from threading import RLock


@typechecked
class AesNativeTransformer(AESCTRTransformer):
    """!
    Generic Native AES transformer. Extend this with your specific
    native transformer.
    """

    __native_proxy: INativeProxy = NativeProxy()
    """
    The native proxy to use for loading libraries for different platforms and operating systems.
    """

    __lock_object: RLock = RLock()

    __impl_type: int = 1

    @staticmethod
    def set_native_proxy(proxy: INativeProxy):
        AesNativeTransformer.__native_proxy = proxy

    @staticmethod
    def get_native_proxy() -> INativeProxy:
        return AesNativeTransformer.__native_proxy

    def __init__(self, impl_type: int):
        """!
        Constructs a transformer that will use the native aes implementations
        @param impl_type: The AES native implementation see ProviderType enum
        """
        super().__init__()
        self.__impl_type = impl_type

    def init(self, key: bytearray, nonce: bytearray):
        """!
        Initialize the native transformer.
        @param key: The AES key to use.
        @param nonce: The nonce to use.
        @exception IntegrityException: Thrown when security error
        """
        self.get_native_proxy().salmon_init(self.__impl_type)
        expanded_key: bytearray = bytearray(AESCTRTransformer.EXPANDED_KEY_SIZE)
        AesNativeTransformer.get_native_proxy().salmon_expand_key(key, expanded_key)
        self.set_expanded_key(expanded_key)
        super().init(key, nonce)

    def encrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """!
        Encrypt the data.
        @param src_buffer: The source byte array.
        @param src_offset: The source byte offset.
        @param dest_buffer: The destination byte array.
        @param dest_offset: The destination byte offset.
        @param count: The number of bytes to transform.
        @returns The number of bytes transformed.
        """

        if self.get_key() is None:
            raise SecurityException("No key found, run init first")
        if self.get_counter() is None:
            raise SecurityException("No counter found, run init first")

        # we block for AES GPU since it's not entirely thread safe
        if self.__impl_type == 3:
            with AesNativeTransformer.__lock_object:
                return AesNativeTransformer.__native_proxy.salmon_transform(self.get_expanded_key(),
                                                                            self.get_counter(),
                                                                            src_buffer, src_offset,
                                                                            dest_buffer, dest_offset, count)
        else:
            return AesNativeTransformer.__native_proxy.salmon_transform(self.get_expanded_key(), self.get_counter(),
                                                                        src_buffer, src_offset,
                                                                        dest_buffer, dest_offset, count)

    def decrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """!
        Decrypt the data.
        @param src_buffer: The source byte array.
        @param src_offset: The source byte offset.
        @param dest_buffer: The destination byte array.
        @param dest_offset: The destination byte offset.
        @param count: The number of bytes to transform.
        @returns The number of bytes transformed.
        """
        if self.get_key() is None:
            raise SecurityException("No key found, run init first")
        if self.get_counter() is None:
            raise SecurityException("No counter found, run init first")

        # we block for AES GPU since it's not entirely thread safe
        if self.__impl_type == 3:
            with AesNativeTransformer.__lock_object:
                return AesNativeTransformer.__native_proxy.salmon_transform(self.get_expanded_key(),
                                                                            self.get_counter(),
                                                                            src_buffer, src_offset,
                                                                            dest_buffer, dest_offset, count)
        else:
            return AesNativeTransformer.__native_proxy.salmon_transform(self.get_expanded_key(), self.get_counter(),
                                                                        src_buffer, src_offset,
                                                                        dest_buffer, dest_offset, count)
