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
from salmon.encode.salmon_encoder import SalmonEncoder
from salmon.salmon_decryptor import SalmonDecryptor

from typeguard import typechecked


@typechecked
class SalmonTextDecryptor:
    """
     * Utility class that encrypts and decrypts text strings.
    """

    __decryptor: SalmonDecryptor = SalmonDecryptor()

    @staticmethod
    def decrypt_string(text: str, key: bytearray, nonce: bytearray | None, header: bool,
                       integrity: bool = False, hash_key: bytearray | None = None,
                       chunk_size: int | None = None) -> str:
        """
         * Decrypts a text String using AES256 with the key and nonce provided.
         *
         * @param text  Text to be decrypted.
         * @param key   The encryption key to be used.
         * @param nonce The nonce to be used, set only if header=false.
         * @param header Set to true if you encrypted the string with encrypt(header=true), set only if nonce=None
         *               otherwise you will have to provide the original nonce.
         * @param integrity True if you want to calculate and store hash signatures for each chunkSize
         * @param hashKey Hash key to be used for all chunks.
         * @param chunkSize The chunk size.
         * @return The decrypted text.
         * @throws IOError
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
        """
        v_bytes: bytearray = SalmonEncoder.get_base64().decode(text)
        dec_bytes: bytearray = SalmonTextDecryptor.__decryptor.decrypt(v_bytes, key, nonce, header, integrity, hash_key,
                                                                       chunk_size)
        dec_string: str = dec_bytes.decode('utf-8')
        return dec_string
