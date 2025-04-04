#!/usr/bin/env python3
"""!@brief Utility class that decrypts text strings.
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

from salmon_core.salmon.encode.base64_utils import Base64Utils
from salmon_core.salmon.decryptor import Decryptor
from salmon_core.salmon.streams.encryption_format import EncryptionFormat

from typeguard import typechecked


@typechecked
class TextDecryptor:
    """!
    Utility class that decrypts text strings.
    """

    __decryptor: Decryptor = Decryptor()

    @staticmethod
    def decrypt_string(text: str, key: bytearray, nonce: bytearray | None = None,
                       enc_format: EncryptionFormat = EncryptionFormat.Salmon,
                       integrity: bool = False, hash_key: bytearray | None = None,
                       chunk_size: int = 0) -> str:
        """!
        Decrypts a text String using AES256 with the key and nonce provided.
        
        @param text:  Text to be decrypted.
        @param key:   The encryption key to be used.
        @param nonce: The nonce to be used, set only if header=false.
        @param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        @param integrity: True if you want to calculate and store hash signatures for each chunk size
        @param hash_key: Hash key to be used for all chunks.
        @param chunk_size: The chunk size.
        @returns The decrypted text.
        @exception IOError: Thrown if there is an IO error.
        @exception IntegrityException: Thrown when security error
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        """
        v_bytes: bytearray = Base64Utils.get_base64().decode(text)
        dec_bytes: bytearray = TextDecryptor.__decryptor.decrypt(v_bytes, key, nonce, enc_format, integrity, hash_key,
                                                                 chunk_size)
        dec_string: str = dec_bytes.decode('utf-8')
        return dec_string
