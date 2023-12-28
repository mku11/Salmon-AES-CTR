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
import time
import traceback
from unittest import TestCase

from convert.bit_converter import BitConverter
from iostream.memory_stream import MemoryStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon.iostream.encryption_mode import EncryptionMode
from salmon.iostream.provider_type import ProviderType
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_decryptor import SalmonDecryptor
from salmon.salmon_default_options import SalmonDefaultOptions
from salmon.salmon_encryptor import SalmonEncryptor
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from test.test_helper import TestHelper

from typeguard import typechecked, TypeCheckError


@typechecked
class SalmonPythonTestRunner(TestCase):
    ENC_IMPORT_BUFFER_SIZE = 512 * 1024
    ENC_IMPORT_THREADS = 4
    TEST_OUTPUT_DIR = "d:\\tmp\\output"
    TEST_VAULT_DIR = "d:\\tmp\\output\\enc"
    TEST_VAULT2_DIR = "d:\\tmp\\output\\enc2"
    TEST_EXPORT_AUTH_DIR = "d:\\tmp\\output\\export"
    TEST_IMPORT_TINY_FILE = "d:\\tmp\\testdata\\tiny_test.txt"
    TEST_IMPORT_SMALL_FILE = "d:\\tmp\\testdata\\small_test.zip"
    TEST_IMPORT_MEDIUM_FILE = "d:\\tmp\\testdata\\medium_test.zip"
    TEST_IMPORT_LARGE_FILE = "d:\\tmp\\testdata\\large_test.mp4"
    TEST_IMPORT_HUGE_FILE = "d:\\tmp\\testdata\\huge.zip"
    TEST_IMPORT_FILE = TEST_IMPORT_MEDIUM_FILE

    def setUp(self):
        SalmonStream.set_aes_provider_type(ProviderType.Default)
        SalmonDefaultOptions.set_buffer_size(SalmonIntegrity.DEFAULT_CHUNK_SIZE)

    def test_shouldEncryptAndDecryptText(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        enc_text = SalmonTextEncryptor.encrypt_string(plain_text, TestHelper.TEST_KEY_BYTES,
                                                      TestHelper.TEST_NONCE_BYTES,
                                                      False)
        dec_text = SalmonTextDecryptor.decrypt_string(enc_text, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                      False)
        self.assertEqual(plain_text, dec_text)

    def test_shouldEncryptAndDecryptTextWithHeader(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        enc_text = SalmonTextEncryptor.encrypt_string(plain_text, TestHelper.TEST_KEY_BYTES,
                                                      TestHelper.TEST_NONCE_BYTES,
                                                      True)
        dec_text = SalmonTextDecryptor.decrypt_string(enc_text, TestHelper.TEST_KEY_BYTES, None, True)
        self.assertEqual(plain_text, dec_text)

    def test_shouldEncryptCatchNoKey(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        caught = False

        try:
            SalmonTextEncryptor.encrypt_string(plain_text, None, TestHelper.TEST_NONCE_BYTES, True)
        except (TypeCheckError, SalmonSecurityException) as ex:
            print(ex)
            caught = True

        self.assertTrue(caught)

    def test_shouldEncryptCatchNoNonce(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        caught = False

        try:
            SalmonTextEncryptor.encrypt_string(plain_text, TestHelper.TEST_KEY_BYTES, None, True)
        except (TypeCheckError, SalmonSecurityException) as ex:
            print(ex)
            caught = True
        except Exception as e:
            print(e)

        self.assertTrue(caught)

    def test_shouldEncryptDecryptNoHeaderCatchNoNonce(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        caught = False

        try:
            enc_text = SalmonTextEncryptor.encrypt_string(plain_text, TestHelper.TEST_KEY_BYTES,
                                                          TestHelper.TEST_NONCE_BYTES, False)
            SalmonTextDecryptor.decrypt_string(enc_text, TestHelper.TEST_KEY_BYTES, None, False)
        except Exception as ex:
            print(ex)
            caught = True

        self.assertTrue(caught)

    def test_shouldEncryptDecryptCatchNoKey(self):
        plain_text = TestHelper.TEST_TINY_TEXT
        caught = False

        try:
            enc_text = SalmonTextEncryptor.encrypt_string(plain_text, TestHelper.TEST_KEY_BYTES,
                                                          TestHelper.TEST_NONCE_BYTES, True)
            SalmonTextDecryptor.decrypt_string(enc_text, None, TestHelper.TEST_NONCE_BYTES, True)
        except Exception as ex:
            print(ex)
            caught = True

        self.assertTrue(caught)

    def test_encrypt_and_decrypt_text_compatible(self):
        plain_text = TestHelper.TEST_TEXT
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)
        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES, False)

        self.assertEqual(v_bytes, dec_bytes_def)
        enc_bytes = SalmonEncryptor().encrypt(v_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, False)

        self.assertEqual(enc_bytes_def, enc_bytes)
        dec_bytes = SalmonDecryptor().decrypt(enc_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, False)

        self.assertEqual(v_bytes, dec_bytes)

    def test_shouldEncryptAndDecryptTextCompatibleWithIntegrity(self):
        plain_text = TestHelper.TEST_TEXT
        for i in range(0, 13):
            plain_text += plain_text

        v_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes_def = TestHelper.default_aesctr_transform(v_bytes, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            True)
        dec_bytes_def = TestHelper.default_aesctr_transform(enc_bytes_def, TestHelper.TEST_KEY_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES, False)

        threads = 1
        chunk_size = 256 * 1024
        self.assertEqual(v_bytes, dec_bytes_def)
        enc_bytes = SalmonEncryptor(threads).encrypt(v_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                     False,
                                                     True, TestHelper.TEST_HMAC_KEY_BYTES, chunk_size)

        self.assertEqualWithIntegrity(enc_bytes_def, enc_bytes, chunk_size)
        dec_bytes = SalmonDecryptor(threads).decrypt(enc_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                     False,
                                                     True, TestHelper.TEST_HMAC_KEY_BYTES, chunk_size)

        self.assertEqual(v_bytes, dec_bytes)

    def assertEqualWithIntegrity(self, buffer: bytearray, buffer_with_integrity: bytearray, chunk_size: int):
        index = 0
        for i in range(0, len(buffer), chunk_size):
            n_chunk_size = min(chunk_size, len(buffer) - i)
            buff1 = bytearray(chunk_size)
            buff1[0:n_chunk_size] = buffer[i:i + n_chunk_size]

            buff2 = bytearray(chunk_size)
            end = index + SalmonGenerator.HASH_RESULT_LENGTH + n_chunk_size
            buff2[0:n_chunk_size] = buffer_with_integrity[index + SalmonGenerator.HASH_RESULT_LENGTH:end]

            self.assertEqual(buff1, buff2)
            index += n_chunk_size + SalmonGenerator.HASH_RESULT_LENGTH
        self.assertEqual(len(buffer_with_integrity), index)

    def test_encrypt_decrypt_using_stream_no_buffers_specified(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              0, 0,
                                              False, None, None, False, None, None)

    def test_encrypt_decrypt_using_stream_large_buffers_align_specified(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_DEC_BUFFER_SIZE,
                                              False, None, None, False, None, None)

    def test_encrypt_decrypt_using_stream_large_buffers_no_align_specified(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              TestHelper.TEST_ENC_BUFFER_SIZE + 3, TestHelper.TEST_DEC_BUFFER_SIZE + 3,
                                              False, None, None, False, None, None)

    def test_encrypt_decrypt_using_stream_aligned_buffer(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              16 * 2, 16 * 2,
                                              False, None, None, False, None, None)

    def test_encrypt_decrypt_using_stream_dec_no_aligned_buffer(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              16 * 2, 16 * 2 + 3,
                                              False, None, None, False, None, None)

    def test_encrypt_decrypt_using_stream_test_integrity_positive(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                                              True, None, TestHelper.TEST_HMAC_KEY_BYTES,
                                              False, None, None)

    def test_encrypt_decrypt_using_stream_test_integrity_no_buffer_specified_positive(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                                              True, None, TestHelper.TEST_HMAC_KEY_BYTES,
                                              False, None, None)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              0, 0,
                                              True, None, TestHelper.TEST_HMAC_KEY_BYTES, False, TestHelper.TEST_HEADER,
                                              64)

    def test_encrypt_decrypt_using_stream_test_integrity_with_chunks_specified_with_header_no_buffer_specified_positive(
            self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              0, 0,
                                              True, None, TestHelper.TEST_HMAC_KEY_BYTES, False, TestHelper.TEST_HEADER,
                                              128)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_positive(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                                              True, 0, TestHelper.TEST_HMAC_KEY_BYTES, False, None, 32)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_buffer_smaller_aligned_positive(
            self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              128, 128, True, 64, TestHelper.TEST_HMAC_KEY_BYTES, False, None, None)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative(self):
        caught = False
        try:
            TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                                  TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                                                  True, 32, TestHelper.TEST_HMAC_KEY_BYTES, False, None, None)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_no_buffer_specified_enc_buffer_not_aligned_negative(
            self):
        caught = False
        try:
            TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                                  TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                                                  True, None, TestHelper.TEST_HMAC_KEY_BYTES, False, None, None)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_specified_dec_buffer_not_aligned(self):
        TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                              TestHelper.TEST_NONCE_BYTES,
                                              16 * 2, 16 * 2 + 3,
                                              True, 32, TestHelper.TEST_HMAC_KEY_BYTES, False, None, None)

    def test_encrypt_decrypt_using_stream_test_integrity_multiple_chunks_no_buffer_specified_dec_buffer_not_aligned_negative(
            self):
        caught = False
        try:
            TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                                  TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                                                  True, None, TestHelper.TEST_HMAC_KEY_BYTES, False, None, None)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_encrypt_decrypt_using_stream_test_integrity_negative(self):
        caught = False
        try:
            TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                                  TestHelper.TEST_NONCE_BYTES,
                                                  TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                                                  True, None, TestHelper.TEST_HMAC_KEY_BYTES, True, None, None)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative(self):
        caught = False
        try:
            TestHelper.encrypt_write_decrypt_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                                  TestHelper.TEST_NONCE_BYTES,
                                                  TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                                                  True, 32, TestHelper.TEST_HMAC_KEY_BYTES, True, None, None)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_shouldNotReadFromStreamEncryptionMode(self):
        test_text = TestHelper.TEST_TEXT

        t_builder = ""
        for i in range(0, 10):
            t_builder += test_text

        plain_text = t_builder
        caught = False
        input_bytes = bytearray(plain_text.encode('utf-8'))
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        enc_writer = SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs,
                                  None, False, None, None)
        try:
            enc_writer.copy_to(outs)
        except Exception as ex:
            caught = True

        ins.close()
        enc_writer.flush()
        enc_writer.close()

        self.assertTrue(caught)

    def test_shouldNotWriteToStreamDecryptionMode(self):
        test_text = TestHelper.TEST_TEXT
        t_builder = ""
        for i in range(0, 10):
            t_builder += test_text
        plain_text = t_builder
        caught = False
        input_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes = TestHelper.encrypt(input_bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                       TestHelper.TEST_ENC_BUFFER_SIZE, False, 0, None, None)

        ins = MemoryStream(enc_bytes)
        enc_writer = SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, EncryptionMode.Decrypt, ins,
                                  None, False, None, None)
        try:
            ins.copy_to(enc_writer)
        except Exception as ex:
            caught = True
        ins.close()
        enc_writer.flush()
        enc_writer.close()

        self.assertTrue(caught)

    def test_seek_and_read_no_integrity(self):
        TestHelper.seek_and_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, False, 0,
                                 None)

    def test_seek_and_test_block_and_counter(self):
        TestHelper.seek_test_counter_and_block(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                                               TestHelper.TEST_NONCE_BYTES,
                                               False, 0, None)

    def test_seek_and_read_with_integrity(self):
        TestHelper.seek_and_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                 True, 0, TestHelper.TEST_HMAC_KEY_BYTES)

    def test_seek_and_read_with_integrity_multi_chunks(self):
        TestHelper.seek_and_read(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                 True, 32, TestHelper.TEST_HMAC_KEY_BYTES)

    def test_seek_and_write_no_integrity(self):
        TestHelper.seek_and_write(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16,
                                  len(TestHelper.TEST_TEXT_WRITE), TestHelper.TEST_TEXT_WRITE, False, 0, None, True)

    def test_seek_and_write_no_integrity_no_allow_range_write_negative(self):
        caught = False
        try:
            TestHelper.seek_and_write(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
                                      len(TestHelper.TEST_TEXT_WRITE), TestHelper.TEST_TEXT_WRITE, False, 0, None,
                                      False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonSecurityException):
                caught = True

        self.assertTrue(caught)

    def test_seek_and_write_with_integrity_not_aligned_multi_chunks_negative(self):
        caught = False
        try:
            TestHelper.seek_and_write(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                      5, len(TestHelper.TEST_TEXT_WRITE), TestHelper.TEST_TEXT_WRITE, False,
                                      32, TestHelper.TEST_HMAC_KEY_BYTES, True)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_catch_ctr_overflow(self):
        caught = False
        try:
            TestHelper.test_counter_value(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                          TestHelper.MAX_ENC_COUNTER)
        except Exception as throwable:
            print(throwable)
            if isinstance(throwable, SalmonRangeExceededException) or isinstance(throwable, ValueError):
                caught = True
            else:
                traceback.print_exc()

        self.assertTrue(caught)

    def test_hold_ctr_value(self):
        caught = False
        try:
            TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                        TestHelper.MAX_ENC_COUNTER - 1)
        except Exception as throwable:
            print(throwable)
            if isinstance(throwable, SalmonRangeExceededException):
                caught = True

        self.assertFalse(caught)

    def test_calc_h_mac256(self):
        v_bytes = bytearray(TestHelper.TEST_TEXT.encode('utf-8'))
        v_hash = TestHelper.calculate_hmac(v_bytes, 0, len(v_bytes), TestHelper.TEST_HMAC_KEY_BYTES, None)
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

    def test_encrypt_and_decrypt_array_multiple_threads(self):
        data = TestHelper.get_rand_array(1 * 1024 * 1024)
        t1 = time.time() * 1000
        enc_data = SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False)
        t2 = time.time() * 1000
        dec_data = SalmonDecryptor(2).decrypt(enc_data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_encrypt_and_decrypt_array_multiple_threads_integrity(self):
        SalmonDefaultOptions.set_buffer_size(2 * 1024 * 1024)
        data = TestHelper.get_rand_array(1 * 1024 * 1024 + 3)
        t1 = time.time() * 1000
        enc_data = SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False, True, TestHelper.TEST_HMAC_KEY_BYTES, None)
        t2 = time.time() * 1000
        dec_data = SalmonDecryptor(2).decrypt(enc_data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False, True, TestHelper.TEST_HMAC_KEY_BYTES, None)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_encrypt_and_decrypt_array_multiple_threads_integrity_custom_chunk_size(self):
        data = TestHelper.get_rand_array(1 * 1024 * 1024)
        t1 = time.time() * 1000
        enc_data = SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False, True, TestHelper.TEST_HMAC_KEY_BYTES, 32)
        t2 = time.time() * 1000
        dec_data = SalmonDecryptor(2).decrypt(enc_data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                              False, True, TestHelper.TEST_HMAC_KEY_BYTES, 32)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_encrypt_and_decrypt_array_multiple_threads_integrity_custom_chunk_size_store_header(self):
        data = TestHelper.get_rand_array_same(129 * 1024)
        t1 = time.time() * 1000
        enc_data = SalmonEncryptor().encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                             True, True, TestHelper.TEST_HMAC_KEY_BYTES, 32)
        t2 = time.time() * 1000
        dec_data = SalmonDecryptor().decrypt(enc_data, TestHelper.TEST_KEY_BYTES,
                                             None, True, True, TestHelper.TEST_HMAC_KEY_BYTES, None)
        t3 = time.time() * 1000

        self.assertEqual(data, dec_data)
        print("enc time: " + str(t2 - t1))
        print("dec time: " + str(t3 - t2))

    def test_copy_memory(self):
        TestHelper.copy_memory(4 * 1024 * 1024)

    def test_copy_from_to_memory_stream(self):
        TestHelper.copy_from_mem_stream(1 * 1024 * 1024, 0)
        TestHelper.copy_from_mem_stream(1 * 1024 * 1024, 32768)

    def test_copy_from_memory_stream_to_salmon_stream(self):
        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, None, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         0)
        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, None, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         32768)

        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         0)
        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         32768)

        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         0)
        TestHelper.copy_from_mem_stream_to_salmon_stream(1 * 1024 * 1024,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         32768)

        # SYNC:
        TestHelper.copy_from_mem_stream_to_salmon_stream(32,
                                                         TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                                                         True, 16, TestHelper.TEST_HMAC_KEY_BYTES,
                                                         32)
