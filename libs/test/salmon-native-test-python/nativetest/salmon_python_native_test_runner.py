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

from __future__ import annotations
from unittest import TestCase

from typeguard import typechecked

from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.iostream.provider_type import ProviderType
from salmon.iostream.salmon_stream import SalmonStream
from salmon.password.pbkdf_type import PbkdfType
from salmon.password.salmon_password import SalmonPassword
from salmon.salmon_decryptor import SalmonDecryptor
from salmon.salmon_default_options import SalmonDefaultOptions
from salmon.salmon_encryptor import SalmonEncryptor
from salmon.salmon_generator import SalmonGenerator
from salmon.transform.native_proxy import NativeProxy
from test.test_helper import TestHelper


@typechecked
class SalmonPythonNativeTestRunner(TestCase):
    ENC_THREADS = 1
    DEC_THREADS = 1

    SalmonPassword.set_pbkdf_type(PbkdfType.Default)
    testCase: TestCase = TestCase()

    def setUp(self):
        SalmonDefaultOptions.set_buffer_size(SalmonIntegrity.DEFAULT_CHUNK_SIZE)
        NativeProxy.set_library_path(
            "../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # SalmonStream.set_aes_provider_type(ProviderType.TinyAES)

    def test_encrypt_and_decrypt_native_text_compatible(self):
        plain_text = TestHelper.TEST_TEXT  # [0:16]
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)

        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES, False)
        self.assertEqual(v_bytes, dec_bytes_def)

        enc_bytes = TestHelper.native_ctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                    True,
                                                    SalmonStream.get_aes_provider_type())
        self.assertEqual(enc_bytes_def, enc_bytes)
        dec_bytes = TestHelper.native_ctr_transform(enc_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                    False,
                                                    SalmonStream.get_aes_provider_type())
        self.assertEqual(v_bytes, dec_bytes)

    def test_encrypt_and_decrypt_native_stream_text_compatible(self):
        plain_text = TestHelper.TEST_TEXT
        for i in range(0, 2):
            plain_text += plain_text
        plain_text = plain_text.substring(0, 16)

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)
        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            False)
        self.assertEqual(v_bytes, dec_bytes_def)

        enc_bytes = SalmonEncryptor(SalmonPythonNativeTestRunner.ENC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).encrypt(v_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   False, None, None)
        self.assertEqual(enc_bytes_def, enc_bytes)

        dec_bytes = SalmonDecryptor(SalmonPythonNativeTestRunner.DEC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).decrypt(enc_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   False, None, None)
        self.assertEqual(bytes, dec_bytes)

    def test_encrypt_and_decrypt_native_stream_read_buffers_not_aligned_text_compatible(self):
        plain_text = TestHelper.TEST_TEXT
        for i in range(0, 3):
            plain_text += plain_text

        plain_text = plain_text.substring(0, 64 + 6)
        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)
        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            False)
        self.assertEqual(v_bytes, dec_bytes_def)

        enc_bytes = SalmonEncryptor(SalmonPythonNativeTestRunner.ENC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).encrypt(v_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   False, None, None)
        self.assertEqual(enc_bytes_def, enc_bytes)

        SalmonDefaultOptions.set_buffer_size(32 + 2)
        dec_bytes = SalmonDecryptor(SalmonPythonNativeTestRunner.DEC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).decrypt(enc_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   False, None, None)
        self.assertEqual(v_bytes, dec_bytes)

    def test_encrypt_and_decrypt_native_stream_compatible_with_integrity(self):
        plain_text = TestHelper.TEST_TEXT
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)
        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES, False)

        chunk_size = 256 * 1024
        self.assertEqual(v_bytes, dec_bytes_def)
        enc_bytes = SalmonEncryptor(SalmonPythonNativeTestRunner.ENC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).encrypt(v_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   True,
                                                                                   TestHelper.TEST_HMAC_KEY_BYTES,
                                                                                   chunk_size)

        SalmonPythonNativeTestRunner.assert_equal_with_integrity(enc_bytes_def, enc_bytes, chunk_size)
        dec_bytes = SalmonDecryptor(SalmonPythonNativeTestRunner.DEC_THREADS,
                                    multi_cpu=TestHelper.ENABLE_MULTI_CPU).decrypt(enc_bytes,
                                                                                   TestHelper.TEST_KEY_BYTES,
                                                                                   TestHelper.TEST_NONCE_BYTES,
                                                                                   False,
                                                                                   True,
                                                                                   TestHelper.TEST_HMAC_KEY_BYTES,
                                                                                   chunk_size)

        self.assertEqual(v_bytes, dec_bytes)

    @staticmethod
    def assert_equal_with_integrity(v_buffer: bytearray, buffer_with_integrity: bytearray, chunk_size):
        index = 0
        for i in range(0, len(v_buffer), chunk_size):
            n_chunk_size = min(chunk_size, len(v_buffer) - i)
            buff1 = bytearray(chunk_size)
            buff1[0:n_chunk_size] = v_buffer[i:i + n_chunk_size]

            buff2 = bytearray(chunk_size)
            end = index + SalmonGenerator.HASH_RESULT_LENGTH + n_chunk_size
            buff2[0:n_chunk_size] = buffer_with_integrity[index + SalmonGenerator.HASH_RESULT_LENGTH:end]

            SalmonPythonNativeTestRunner.testCase.assertEqual(buff1, buff2)
            index += n_chunk_size + SalmonGenerator.HASH_RESULT_LENGTH
            SalmonPythonNativeTestRunner.testCase.assertEqual(len(buffer_with_integrity), index)
