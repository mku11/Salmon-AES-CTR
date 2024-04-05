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
from salmon_core.salmon.encode.salmon_encoder import SalmonEncoder
from salmon_core.salmon.salmon_encryptor import SalmonEncryptor
from typeguard import typechecked


@typechecked
class SalmonTextEncryptor:
    """
     * Utility class that encrypts and decrypts text strings.
    """

    __encryptor: SalmonEncryptor = SalmonEncryptor()

    @staticmethod
    def encrypt_string(text: str, key: bytearray, nonce: bytearray, header: bool, integrity: bool = False,
                       hash_key: bytearray | None = None, chunk_size: int | None = None) -> str:
        """
         * Encrypts a text String using AES256 with the key and nonce provided.
         *
         * @param text  Text to be encrypted.
         * @param key   The encryption key to be used.
         * @param nonce The nonce to be used.
         * @param header Set to true to store a header with information like nonce and/or chunk size,
         *               otherwise you will have to store that information externally.
         * @param integrity True if you want to calculate and store hash signatures for each chunkSize
         * @param hash_key Hash key to be used for all chunks.
         * @param chunkSize The chunk size.
         * @throws IOError
         * @throws SalmonSecurityException
         * @throws IntegrityException
         * @throws IOError
        """
        v_bytes: bytearray = bytearray(text.encode('utf-8'))
        enc_bytes: bytearray = SalmonTextEncryptor.__encryptor.encrypt(v_bytes, key, nonce, header, integrity, hash_key,
                                                                       chunk_size)
        enc_string: str = SalmonEncoder.get_base64().encode(enc_bytes).replace("\n", "")
        return enc_string
