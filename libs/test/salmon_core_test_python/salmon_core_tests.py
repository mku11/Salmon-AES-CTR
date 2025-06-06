#!/usr/bin/env python3

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

from typeguard import typechecked, TypeCheckError
import time
import traceback
from unittest import TestCase
import os
import sys

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
from salmon_core.convert.bit_converter import BitConverter
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.range_exceeded_exception import RangeExceededException
from salmon_core.salmon.security_exception import SecurityException
from salmon_core.salmon.text.text_decryptor import TextDecryptor
from salmon_core.salmon.text.text_encryptor import TextEncryptor
from salmon_core.salmon.generator import Generator
from salmon_core_test_helper import SalmonCoreTestHelper
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.integrity.integrity import Integrity


@typechecked
class SalmonCoreTests(TestCase):

    @classmethod
    def setUpClass(cls):
        threads: int = int(os.getenv("ENC_THREADS")) if os.getenv("ENC_THREADS") else 1
        print("threads: " + str(threads))

        # SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024
        # SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads

        SalmonCoreTestHelper.initialize()

        provider_type: ProviderType = ProviderType[os.getenv("AES_PROVIDER_TYPE")] if os.getenv(
            "AES_PROVIDER_TYPE") else ProviderType.Default
        print("ProviderType: " + str(provider_type))
        AesStream.set_aes_provider_type(provider_type)

    @classmethod
    def tearDownClass(cls):
        SalmonCoreTestHelper.close()

    def test_shouldEncryptAndDecryptText(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        enc_text = TextEncryptor.encrypt_string(plain_text, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                EncryptionFormat.Generic)
        dec_text = TextDecryptor.decrypt_string(enc_text, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                EncryptionFormat.Generic)
        self.assertEqual(plain_text, dec_text)

    def test_shouldEncryptAndDecryptTextWithHeader(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        enc_text = TextEncryptor.encrypt_string(plain_text, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                SalmonCoreTestHelper.TEST_NONCE_BYTES)
        dec_text = TextDecryptor.decrypt_string(enc_text, SalmonCoreTestHelper.TEST_KEY_BYTES)
        self.assertEqual(plain_text, dec_text)

    def test_shouldEncryptCatchNoKey(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        caught = False

        try:
            TextEncryptor.encrypt_string(plain_text, None, SalmonCoreTestHelper.TEST_NONCE_BYTES)
        except (TypeCheckError, SecurityException) as ex:
            print(ex, file=sys.stderr)
            caught = True

        self.assertTrue(caught)

    def test_shouldEncryptCatchNoNonce(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        caught = False

        try:
            TextEncryptor.encrypt_string(plain_text, SalmonCoreTestHelper.TEST_KEY_BYTES, None)
        except (TypeCheckError, SecurityException) as ex:
            print(ex, file=sys.stderr)
            caught = True
        except Exception as e:
            print(e, file=sys.stderr)

        self.assertTrue(caught)

    def test_shouldEncryptDecryptNoHeaderCatchNoNonce(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        caught = False

        try:
            enc_text = TextEncryptor.encrypt_string(plain_text, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                    SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic)
            TextDecryptor.decrypt_string(enc_text, SalmonCoreTestHelper.TEST_KEY_BYTES, None, EncryptionFormat.Generic)
        except Exception as ex:
            print(ex, file=sys.stderr)
            caught = True

        self.assertTrue(caught)

    def test_shouldEncryptDecryptCatchNoKey(self):
        plain_text = SalmonCoreTestHelper.TEST_TINY_TEXT
        caught = False

        try:
            enc_text = TextEncryptor.encrypt_string(plain_text, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                    SalmonCoreTestHelper.TEST_NONCE_BYTES)
            TextDecryptor.decrypt_string(enc_text, None, SalmonCoreTestHelper.TEST_NONCE_BYTES)
        except Exception as ex:
            print(ex, file=sys.stderr)
            caught = True

        self.assertTrue(caught)

    def test_encrypt_and_decrypt_text_compatible(self):
        plain_text = SalmonCoreTestHelper.TEST_TEXT
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
        enc_bytes = SalmonCoreTestHelper.get_encryptor() \
            .encrypt(v_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)

        self.assertEqual(enc_bytes_def, enc_bytes)
        dec_bytes = SalmonCoreTestHelper.get_decryptor() \
            .decrypt(enc_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)

        self.assertEqual(v_bytes, dec_bytes)

    def test_encrypt_decrypt_using_stream_no_buffers_specified(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        0, 0,
                                                        False, 0, None, False, None)

    def test_encrypt_decrypt_using_stream_large_buffers_align_specified(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE,
                                                        False, 0, None, False, None)

    def test_encrypt_decrypt_using_stream_large_buffers_no_align_specified(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE + 3,
                                                        SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE + 3,
                                                        False, 0, None, False, None)

    def test_encrypt_decrypt_using_stream_aligned_buffer(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        16 * 2, 16 * 2,
                                                        False, 0, None, False, None)

    def test_encrypt_decrypt_using_stream_dec_no_aligned_buffer(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        16 * 2, 16 * 2 + 3,
                                                        False, 0, None, False, None)

    def test_encrypt_decrypt_using_stream_test_integrity_positive(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                        False, None)

    def test_encrypt_decrypt_using_stream_test_integrity_no_buffer_specified_positive(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                        False, None)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        0, 0,
                                                        True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                        64)

    def test_encrypt_decrypt_using_stream_test_integrity_with_chunks_specified_with_header_no_buffer_specified_positive(
            self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        0, 0,
                                                        True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                        128)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_positive(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                        True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                        32)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_buffer_smaller_aligned_positive(
            self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        128, 128, True, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                        False, None)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative(self):
        caught = False
        try:
            SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                                                            True, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                            None)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_encrypt_decrypt_stream_test_integrity_multiple_chunks_no_buffer_specified_enc_buffer_not_aligned_negative(
            self):
        caught = False
        try:
            SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                                                            True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                            None)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_dec_buffer_not_aligned(self):
        SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                        SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                        SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                        16 * 2, 16 * 2 + 3,
                                                        True, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False, None)

    def test_encrypt_decrypt_stream_test_integrity_multiple_chunks_no_buffer_specified_dec_buffer_not_aligned_negative(
            self):
        caught = False
        try:
            SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                                                            True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, False,
                                                            None)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True
            else:
                raise ex

        self.assertTrue(caught)

    def test_encrypt_decrypt_using_stream_test_integrity_negative(self):
        caught = False
        try:
            SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                            True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, True,
                                                            None)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative(self):
        caught = False
        try:
            SalmonCoreTestHelper.encrypt_write_decrypt_read(SalmonCoreTestHelper.TEST_TEXT,
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                                                            True, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, True,
                                                            None)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_shouldNotReadFromStreamEncryptionMode(self):
        test_text = SalmonCoreTestHelper.TEST_TEXT

        t_builder = ""
        for i in range(0, 10):
            t_builder += test_text

        plain_text = t_builder
        caught = False
        input_bytes = bytearray(plain_text.encode('utf-8'))
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        enc_writer = AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                               EncryptionMode.Encrypt, outs, EncryptionFormat.Salmon)
        try:
            enc_writer.copy_to(outs)
        except Exception as ex:
            caught = True

        ins.close()
        enc_writer.flush()
        enc_writer.close()

        self.assertTrue(caught)

    def test_shouldNotWriteToStreamDecryptionMode(self):
        test_text = SalmonCoreTestHelper.TEST_TEXT
        t_builder = ""
        for i in range(0, 10):
            t_builder += test_text
        plain_text = t_builder
        caught = False
        input_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes = SalmonCoreTestHelper.encrypt(input_bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                 SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                 SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, False, 0, None)

        ins = MemoryStream(enc_bytes)
        enc_writer = AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                               EncryptionMode.Decrypt, ins, EncryptionFormat.Salmon)
        try:
            ins.copy_to(enc_writer)
        except Exception as ex:
            caught = True
        ins.close()
        enc_writer.flush()
        enc_writer.close()

        self.assertTrue(caught)

    def test_seek_and_read_no_integrity(self):
        SalmonCoreTestHelper.seek_and_read(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                           SalmonCoreTestHelper.TEST_NONCE_BYTES, False, 0,
                                           None)

    def test_seek_and_test_block_and_counter(self):
        SalmonCoreTestHelper.seek_test_counter_and_block(SalmonCoreTestHelper.TEST_TEXT,
                                                         SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                         SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                         False, 0, None)

    def test_seek_and_read_with_integrity(self):
        SalmonCoreTestHelper.seek_and_read(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                           SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                           True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES)

    def test_seek_and_read_with_integrity_multi_chunks(self):
        SalmonCoreTestHelper.seek_and_read(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                           SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                           True, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES)

    def test_seek_and_write_no_integrity(self):
        SalmonCoreTestHelper.seek_and_write(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                            SalmonCoreTestHelper.TEST_NONCE_BYTES, 16,
                                            len(SalmonCoreTestHelper.TEST_TEXT_WRITE),
                                            SalmonCoreTestHelper.TEST_TEXT_WRITE, False, 0, None, True)

    def test_seek_and_write_no_integrity_no_allow_range_write_negative(self):
        caught = False
        try:
            SalmonCoreTestHelper.seek_and_write(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                SalmonCoreTestHelper.TEST_NONCE_BYTES, 5,
                                                len(SalmonCoreTestHelper.TEST_TEXT_WRITE),
                                                SalmonCoreTestHelper.TEST_TEXT_WRITE, False, 0, None,
                                                False)
        except IOError as ex:
            if isinstance(ex.__cause__, SecurityException):
                caught = True

        self.assertTrue(caught)

    def test_seek_and_write_with_integrity_not_aligned_multi_chunks_negative(self):
        caught = False
        try:
            SalmonCoreTestHelper.seek_and_write(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                5, len(SalmonCoreTestHelper.TEST_TEXT_WRITE),
                                                SalmonCoreTestHelper.TEST_TEXT_WRITE, False,
                                                32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, True)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_catch_ctr_overflow(self):
        caught = False
        try:
            SalmonCoreTestHelper.test_counter_value(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                    SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                    SalmonCoreTestHelper.MAX_ENC_COUNTER)
        except Exception as throwable:
            print(throwable)
            if isinstance(throwable, RangeExceededException) or isinstance(throwable, ValueError):
                caught = True
            else:
                traceback.print_exc()

        self.assertTrue(caught)

    def test_hold_ctr_value(self):
        caught = False
        try:
            SalmonCoreTestHelper.testCounterValue(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                  SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                  SalmonCoreTestHelper.MAX_ENC_COUNTER - 1)
        except Exception as throwable:
            print(throwable)
            if isinstance(throwable, RangeExceededException):
                caught = True

        self.assertFalse(caught)

    def test_calc_h_mac256(self):
        v_bytes = bytearray(SalmonCoreTestHelper.TEST_TEXT.encode('utf-8'))
        v_hash = SalmonCoreTestHelper.calculate_hmac(v_bytes, 0, len(v_bytes), SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                     None)
        for b in v_hash:
            print(format(b, '02x') + " ", end="")
        print()

    def test_convert(self):
        num1 = 12564
        v_bytes = BitConverter.to_bytes(num1, 4)
        num2 = BitConverter.to_long(v_bytes, 0, 4)

        self.assertEqual(num1, num2)

        lnum1 = 56445783493
        v_bytes = BitConverter.to_bytes(lnum1, 8)
        lnum2 = BitConverter.to_long(v_bytes, 0, 8)

        self.assertEqual(lnum1, lnum2)

    def test_encrypt_and_decrypt_array_generic(self):
        data = SalmonCoreTestHelper.get_rand_array(1 * 1024 * 1024 + 4)
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.get_encryptor() \
            .encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.get_decryptor() \
            .decrypt(enc_data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Generic)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_encrypt_and_decrypt_array_integrity(self):
        data = SalmonCoreTestHelper.get_rand_array(1 * 1024 * 1024 + 3)
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.get_encryptor().encrypt(data,
                                                                SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                EncryptionFormat.Salmon, True,
                                                                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.get_decryptor() \
            .decrypt(enc_data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                     EncryptionFormat.Salmon, True, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_encrypt_and_decrypt_array_integrity_custom_chunk_size(self):
        data = SalmonCoreTestHelper.get_rand_array(1 * 1024 * 1024)
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.get_encryptor().encrypt(data,
                                                                SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                EncryptionFormat.Salmon, True,
                                                                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                32)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data,
                                                                SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                EncryptionFormat.Salmon, True,
                                                                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                32)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_ShouldEncryptAndDecryptArrayIntegrityNoApply(self):
        data = bytearray(SalmonCoreTestHelper.TEST_TEXT.encode())
        key = Generator.get_secure_random_bytes(32)
        nonce = Generator.get_secure_random_bytes(8)
        hash_key = Generator.get_secure_random_bytes(32)

        enc_data = SalmonCoreTestHelper.get_encryptor().encrypt(data, key, nonce, EncryptionFormat.Salmon, True,
                                                                hash_key)

        # specify integrity
        dec_data2 = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data, key, None, EncryptionFormat.Salmon, True,
                                                                 hash_key)
        self.assertEqual(data, dec_data2)

        # skip integrity
        dec_data3: bytearray = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data, key, None,
                                                                            EncryptionFormat.Salmon,
                                                                            False)
        self.assertEqual(data, dec_data3)

        # tamper
        enc_data[14] = 0

        # specify integrity
        caught: bool = False
        try:
            dec_data4: bytearray = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data, key, None,
                                                                                EncryptionFormat.Salmon, True, hash_key)
            self.assertEqual(data, dec_data4)
        except Exception as ex:
            caught = True
        self.assertTrue(caught)

        # skip integrity, not failing but results don't match
        caught2: bool = False
        try:
            dec_data5: bytearray = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data, key, None,
                                                                                EncryptionFormat.Salmon, False)
        except Exception as ex:
            caught2 = True

        self.assertFalse(caught2)

    def test_ShouldEncryptAndDecryptArrayIntegrityCustomChunkSizeDecNoChunkSize(self):
        data: bytearray = SalmonCoreTestHelper.get_rand_array(1 * 1024 * 1024)
        t1 = time.time() * 1000
        enc_data: bytearray = SalmonCoreTestHelper.get_encryptor().encrypt(data,
                                                                           SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                           SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                           EncryptionFormat.Salmon, True,
                                                                           SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.get_decryptor().decrypt(enc_data,
                                                                SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                EncryptionFormat.Salmon, True,
                                                                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES)

        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_copy_memory(self):
        SalmonCoreTestHelper.copy_memory(4 * 1024 * 1024)

    def test_copy_from_to_memory_stream(self):
        SalmonCoreTestHelper.copy_from_mem_stream(1 * 1024 * 1024, 0)
        SalmonCoreTestHelper.copy_from_mem_stream(1 * 1024 * 1024, 32768)

    def test_copy_from_memory_stream_to_salmon_stream(self):
        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   0)
        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   32768)

        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, Integrity.DEFAULT_CHUNK_SIZE,
                                                                   SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   0)
        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, Integrity.DEFAULT_CHUNK_SIZE,
                                                                   SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   32768)

        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, 128 * 1024,
                                                                   SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   0)
        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, 128 * 1024,
                                                                   SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   32768)

        # SYNC:
        SalmonCoreTestHelper.copy_from_mem_stream_to_salmon_stream(32,
                                                                   SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                   SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                   True, 16, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                   32)
