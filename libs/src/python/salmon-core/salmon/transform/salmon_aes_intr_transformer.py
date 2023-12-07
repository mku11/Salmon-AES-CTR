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
from salmon.transform.salmon_native_transformer import SalmonNativeTransformer


class SalmonAesIntrTransformer(SalmonNativeTransformer):
    """
     * Salmon AES transformer implemented with AES intrinsics.
    """

    AES_IMPL_AES_INTR = 1
    """
     * The constant to pass to the native code while initializing.
    """

    def init(self, key: bytearray, nonce: bytearray):
        """
         * Initialize the native Aes intrinsics transformer.
         * @param key The AES key to use.
         * @param nonce The nonce to use.
         * @throws SalmonSecurityException
        """
        self.getNativeProxy().salmonInit(SalmonAesIntrTransformer.AES_IMPL_AES_INTR)
        super().init(key, nonce)

    def encryptData(self, srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        """
         * Encrypt the data.
         * @param srcBuffer The source byte array.
         * @param srcOffset The source byte offset.
         * @param destBuffer The destination byte array.
         * @param destOffset The destination byte offset.
         * @param count The number of bytes to transform.
         * @return The number of bytes transformed.
        """
        # AES intrinsics needs the expanded key
        return self.getNativeProxy().salmonTransform(self.getExpandedKey(), self.getCounter(),
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
        # AES intrinsics needs the expanded key
        return self.getNativeProxy().salmonTransform(self.getExpandedKey(), self.getCounter(),
                                                     SalmonStream.EncryptionMode.Encrypt.ordinal(),
                                                     srcBuffer, srcOffset,
                                                     destBuffer, destOffset, count)
