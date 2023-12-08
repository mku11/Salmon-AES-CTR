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
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer

from typeguard import typechecked


@typechecked
class SalmonDefaultTransformer(SalmonAES256CTRTransformer):
    """
     * Salmon AES transformer based on the javax.crypto routines.
    """

    def __init__(self):
        super().__init__()
        #__cipher: Cipher
        """
         * Default Java AES cipher.
        """

        #__encSecretKey: SecretKeySpec
        """
         * Key spec for the initial nonce (counter).
        """

    def init(self, key: bytearray, nonce: bytearray):
        """
         * Initialize the default Java AES cipher transformer.
         * @param key The AES256 key to use.
         * @param nonce The nonce to use.
         * @throws SalmonSecurityException
        """
        super().init(key, nonce)
        try:
            pass
        # encSecretKey = new SecretKeySpec(key, "AES")
        # cipher = Cipher.getInstance("AES/CTR/NoPadding")
        except Exception as e:
            raise SalmonSecurityException("Could not init AES transformer") from e

    def encrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
         * Encrypt the data.
         * @param srcBuffer The source byte array.
         * @param srcOffset The source byte offset.
         * @param destBuffer The destination byte array.
         * @param destOffset The destination byte offset.
         * @param count The number of bytes to transform.
         * @return The number of bytes transformed.
         * @throws SalmonSecurityException
        """
        # TODO:
        return 0
        # try:
        # # byte[] counter = getCounter();
        # # IvParameterSpec ivSpec = new IvParameterSpec(counter);
        # # cipher.init(Cipher.ENCRYPT_MODE, encSecretKey, ivSpec);
        # # return cipher.doFinal(srcBuffer, srcOffset, count, destBuffer, destOffset);
        # except Exception as ex:
        #     raise SalmonSecurityException("Could not encrypt data: ") from ex

    def decrypt_data(self, src_buffer: bytearray, src_offset: int,
                     dest_buffer: bytearray, dest_offset: int, count: int) -> int:
        """
         * Decrypt the data.
         * @param srcBuffer The source byte array.
         * @param srcOffset The source byte offset.
         * @param destBuffer The destination byte array.
         * @param destOffset The destination byte offset.
         * @param count The number of bytes to transform.
         * @return The number of bytes transformed.
         * @throws SalmonSecurityException
        """
        try:
            pass
        # byte[] counter = getCounter();
        # IvParameterSpec ivSpec = new IvParameterSpec(counter);
        # cipher.init(Cipher.DECRYPT_MODE, encSecretKey, ivSpec);
        # return cipher.doFinal(srcBuffer, srcOffset, count, destBuffer, destOffset);
        except Exception as ex:
            raise SalmonSecurityException("Could not decrypt data: ") from ex
