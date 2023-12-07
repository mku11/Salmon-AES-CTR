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
from salmon.transform.inative_proxy import INativeProxy
from salmon.transform.native_proxy import NativeProxy
from salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer


class SalmonNativeTransformer(SalmonAES256CTRTransformer):
    """
     * Generic Native AES transformer. Extend this with your specific
     * native transformer.
    """

    __nativeProxy: INativeProxy = NativeProxy()
    """
     * The native proxy to use for loading libraries for different platforms and operating systems.
    """

    @staticmethod
    def setNativeProxy(proxy: INativeProxy):
        SalmonNativeTransformer.__nativeProxy = proxy

    @staticmethod
    def getNativeProxy() -> INativeProxy:
        return SalmonNativeTransformer.__nativeProxy

    def init(self, key: bytearray, nonce: bytearray):
        """
         * Initialize the native transformer.
         * @param key The AES key to use.
         * @param nonce The nonce to use.
         * @throws SalmonSecurityException
        """
        super().init(key, nonce)
        self.__expandedKey: bytearray = bytearray(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE)
        SalmonNativeTransformer.__nativeProxy.salmonExpandKey(key, self.__expandedKey)
        SalmonNativeTransformer.setExpandedKey(self.__expandedKey)

    """
     * Encrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
    """

    def encryptData(self, srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        return SalmonNativeTransformer.__nativeProxy.salmonTransform(self.getKey(), self.getCounter(),
                                                                     SalmonStream.EncryptionMode.Encrypt.ordinal(),
                                                                     srcBuffer, srcOffset,
                                                                     destBuffer, destOffset, count)

    """
     * Decrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
    """

    def decryptData(self, srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        return SalmonNativeTransformer.__nativeProxy.salmonTransform(self.getKey(), self.getCounter(),
                                                                     SalmonStream.EncryptionMode.Encrypt.ordinal(),
                                                                     srcBuffer, srcOffset,
                                                                     destBuffer, destOffset, count);
