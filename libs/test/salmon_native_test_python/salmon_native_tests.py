#!/usr/bin/env python3

from __future__ import annotations

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

import os
import platform
import sys
from unittest import TestCase
from typeguard import typechecked

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
sys.path.append(os.path.dirname(__file__) + '/../../test/salmon_core_test_python')
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.password.pbkdf_type import PbkdfType
from salmon_core.salmon.password.password import Password
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.decryptor import Decryptor
from salmon_core.salmon.encryptor import Encryptor
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon_core_test_helper import SalmonCoreTestHelper


@typechecked
class SalmonNativeTests(TestCase):
    ENC_THREADS = 1
    DEC_THREADS = 1

    def setUp(self):
        SalmonCoreTestHelper.initialize()
        
        provider_type: ProviderType = ProviderType[os.getenv("AES_PROVIDER_TYPE")] if os.getenv(
            "AES_PROVIDER_TYPE") else ProviderType.Aes
        print("ProviderType: " + str(provider_type))
        threads: int = int(os.getenv("ENC_THREADS")) if os.getenv("ENC_THREADS") else 1

        print("ProviderType: " + str(provider_type))
        print("threads: " + str(threads))

        AesStream.set_aes_provider_type(provider_type)
        SalmonNativeTests.ENC_THREADS = threads
        SalmonNativeTests.DEC_THREADS = threads
    
    @classmethod
    def tearDownClass(cls):
        SalmonCoreTestHelper.close()
        
    def test_encrypt_and_decrypt_native_text_compatible(self):
        plain_text = SalmonCoreTestHelper.TEST_TEXT  # [0:16]
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                      True)

        dec_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(enc_bytes_def,
                                                                      SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES, False)
        self.assertEqual(v_bytes, dec_bytes_def)

        enc_bytes = SalmonCoreTestHelper.native_ctr_transform(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                              SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                              True,
                                                              AesStream.get_aes_provider_type())
        self.assertEqual(enc_bytes_def, enc_bytes)
        dec_bytes = SalmonCoreTestHelper.native_ctr_transform(enc_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                              SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                              False,
                                                              AesStream.get_aes_provider_type())
        self.assertEqual(v_bytes, dec_bytes)

    def test_encrypt_and_decrypt_native_stream_text_compatible(self):
        plain_text = SalmonCoreTestHelper.TEST_TEXT
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                      True)
        dec_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(enc_bytes_def,
                                                                      SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                      False)
        self.assertEqual(v_bytes, dec_bytes_def)

        encryptor = Encryptor(SalmonNativeTests.ENC_THREADS, multi_cpu=SalmonCoreTestHelper.ENABLE_MULTI_CPU)
        enc_bytes = encryptor.encrypt(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        encryptor.close()
        self.assertEqual(enc_bytes_def, enc_bytes)

        decryptor = Decryptor(SalmonNativeTests.DEC_THREADS, multi_cpu=SalmonCoreTestHelper.ENABLE_MULTI_CPU)
        dec_bytes = decryptor.decrypt(enc_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        decryptor.close()
        self.assertEqual(v_bytes, dec_bytes)

    def test_encrypt_and_decrypt_native_stream_read_buffers_not_aligned_text_compatible(self):
        plain_text = SalmonCoreTestHelper.TEST_TEXT
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                      True)
        dec_bytes_def = SalmonCoreTestHelper.default_aesctr_transform(enc_bytes_def,
                                                                      SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                      False)
        self.assertEqual(v_bytes, dec_bytes_def)

        encryptor = Encryptor(SalmonNativeTests.ENC_THREADS, multi_cpu=SalmonCoreTestHelper.ENABLE_MULTI_CPU)
        enc_bytes = encryptor.encrypt(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        encryptor.close()
        self.assertEqual(enc_bytes_def, enc_bytes)

        decryptor = Decryptor(SalmonNativeTests.DEC_THREADS, multi_cpu=SalmonCoreTestHelper.ENABLE_MULTI_CPU)
        dec_bytes = decryptor.decrypt(enc_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        decryptor.close()
        self.assertEqual(v_bytes, dec_bytes)
