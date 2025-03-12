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

import hashlib
import math
import os
import platform
import random
import sys
import time

from unittest import TestCase

from Crypto.Cipher import AES
from Crypto.Util import Counter
from typeguard import typechecked

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
from salmon_core.convert.bit_converter import BitConverter
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon_core.salmon.bridge.native_proxy import NativeProxy
from salmon.integrity.ihash_provider import IHashProvider
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_decryptor import SalmonDecryptor
from salmon_core.salmon.salmon_encryptor import SalmonEncryptor
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.transform.salmon_transformer_factory import SalmonTransformerFactory


@typechecked
class SalmonCoreTestHelper:
    TEST_ENC_BUFFER_SIZE = 512 * 1024
    TEST_ENC_THREADS = 2
    TEST_DEC_BUFFER_SIZE = 512 * 1024
    ENABLE_MULTI_CPU = False
    TEST_DEC_THREADS = 2

    TEST_PASSWORD = "test123"
    TEST_FALSE_PASSWORD = "Falsepass"

    MAX_ENC_COUNTER = int(math.pow(256, 7))
    #  a nonce ready to overflow if a file is imported
    TEXT_VAULT_MAX_FILE_NONCE = bytearray([0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF])

    TEXT_ITERATIONS = 1
    TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"  # 256bit
    TEST_KEY_BYTES = bytearray(TEST_KEY.encode('utf-8'))
    TEST_NONCE = "12345678"  # 8 bytes
    TEST_NONCE_BYTES = bytearray(TEST_NONCE.encode('utf-8'))
    TEST_FILENAME_NONCE = "ABCDEFGH"  # 8 bytes
    TEST_FILENAME_NONCE_BYTES = bytearray(TEST_FILENAME_NONCE.encode('utf-8'))
    TEST_HMAC_KEY = "12345678901234561234567890123456"  # 32bytes
    TEST_HMAC_KEY_BYTES = bytearray(TEST_HMAC_KEY.encode('utf-8'))

    TEST_HEADER = "SOMEHEADERDATASOMEHEADER"
    TEST_TINY_TEXT = "test.txt"
    TEST_TEXT = "This is another test that could be very if used correctly."
    TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS"

    random.seed()
    hashProvider: IHashProvider = HmacSHA256Provider()
    encryptor: SalmonEncryptor
    decryptor: SalmonDecryptor

    testCase: TestCase = TestCase()

    @staticmethod
    def initialize():
        print("init core helper")
        SalmonCoreTestHelper.hashProvider = HmacSHA256Provider()
        SalmonCoreTestHelper.encryptor = SalmonEncryptor(SalmonCoreTestHelper.TEST_ENC_THREADS)
        SalmonCoreTestHelper.decryptor = SalmonDecryptor(SalmonCoreTestHelper.TEST_DEC_THREADS)

        # set native library path
        platform_os: str = platform.system().upper()
        if "WIN" in platform_os:
            NativeProxy.set_library_path(
                "../../projects/salmonfs-libs-gradle/salmonfs-native/build/libs/salmonfs/shared/salmon.dll")
        elif "MAC" in platform_os:
            NativeProxy.set_library_path(
                "../../projects/salmonfs-libs-xcode-macos/salmonfs/DerivedData/salmonfs/Build/Products/Release/libsalmon.dylib")
        elif "LINUX" in platform_os:
            NativeProxy.set_library_path(
                "../../projects/salmonfs-libs-gradle/salmonfs-native/build/libs/salmonfs/shared/libsalmon.so")

    @staticmethod
    def close():
        print("closing core helper")
        if SalmonCoreTestHelper.encryptor:
            SalmonCoreTestHelper.encryptor.close()
        if SalmonCoreTestHelper.decryptor:
            SalmonCoreTestHelper.decryptor.close()

    @staticmethod
    def get_encryptor() -> SalmonEncryptor:
        return SalmonCoreTestHelper.encryptor

    @staticmethod
    def get_decryptor() -> SalmonDecryptor:
        return SalmonCoreTestHelper.decryptor

    @staticmethod
    def assert_equal(a, b):
        return SalmonCoreTestHelper.testCase.assertEqual(a, b)

    @staticmethod
    def assert_array_equal(a, b):
        return SalmonCoreTestHelper.testCase.assertEqual(a, b)

    @staticmethod
    def seek_and_get_substring_by_read(reader, seek, read_count, seek_origin):
        reader.seek(seek, seek_origin)
        enc_outs2 = MemoryStream()

        v_bytes = bytearray(read_count)
        bytes_read = 0
        total_bytes_read = 0
        while total_bytes_read < read_count and (bytes_read := reader.read(v_bytes, 0, len(v_bytes))) > 0:
            #  we skip the alignment offset and start reading the bytes we need
            enc_outs2.write(v_bytes, 0, bytes_read)
            total_bytes_read += bytes_read

        dec_text1 = enc_outs2.to_array().decode('utf-8')
        enc_outs2.close()
        return dec_text1

    @staticmethod
    def encrypt_write_decrypt_read(text, key, iv,
                                   enc_buffer_size, dec_buffer_size, test_integrity, chunk_size: int | None,
                                   hash_key, flip_bits, header: str | None, max_text_length: int | None):
        test_text = text

        t_builder = ""
        for i in range(0, SalmonCoreTestHelper.TEXT_ITERATIONS):
            t_builder += test_text

        plain_text = t_builder
        if max_text_length is not None and max_text_length < len(plain_text):
            plain_text = plain_text[0:max_text_length]

        header_length = 0
        if header is not None:
            header_length = len(header.encode('utf-8'))
        input_bytes = bytearray(plain_text.encode('utf-8'))
        enc_bytes = SalmonCoreTestHelper.encrypt(input_bytes, key, iv, enc_buffer_size,
                                                 test_integrity, chunk_size, hash_key, header)
        if flip_bits:
            enc_bytes[len(enc_bytes) // 2] = 0

        #  Use to read from cipher byte array and to Write to byte array
        output_byte2 = SalmonCoreTestHelper.decrypt(enc_bytes, key, iv, dec_buffer_size,
                                                    test_integrity, chunk_size, hash_key,
                                                    header_length if header is not None else None)
        dec_text = output_byte2.decode('utf-8')

        print(plain_text)
        print(dec_text)

        SalmonCoreTestHelper.assert_equal(plain_text, dec_text)

    @staticmethod
    def encrypt(input_bytes, key, iv, buffer_size,
                integrity, chunk_size: int | None, hash_key,
                header) -> bytearray:
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        header_data = None
        if header is not None:
            header_data = bytearray(header.encode('utf-8'))
            outs.write(header_data, 0, len(header_data))

        writer = SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                              header_data, integrity, chunk_size, hash_key)

        if buffer_size == 0:  # use the internal buffer size of the to copy
            ins.copy_to(writer)
        else:  # use our manual buffer to test
            bytes_read: int = 0
            buffer = bytearray(buffer_size)
            while (bytes_read := ins.read(buffer, 0, len(buffer))) > 0:
                writer.write(buffer, 0, bytes_read)

        writer.flush()
        v_bytes = outs.to_array()
        writer.close()
        ins.close()
        return v_bytes

    @staticmethod
    def decrypt(input_bytes, key, iv, buffer_size,
                integrity, chunk_size: int | None, hash_key,
                header_length: int | None):
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        header_data = None
        if header_length is not None:
            header_data = bytearray(header_length)
            ins.read(header_data, 0, len(header_data))

        reader = SalmonStream(key, iv, EncryptionMode.Decrypt, ins,
                              header_data, integrity, chunk_size, hash_key)

        if buffer_size == 0:  # use the internal buffersize of the to copy
            reader.copy_to(outs)
        else:  # use our manual buffer to test
            bytes_read: int = 0
            buffer = bytearray(buffer_size)
            while (bytes_read := reader.read(buffer, 0, len(buffer))) > 0:
                outs.write(buffer, 0, bytes_read)

        outs.flush()
        v_bytes = outs.to_array()
        reader.close()
        outs.close()
        return v_bytes

    @staticmethod
    def seek_and_read(text, key, iv,
                      integrity, chunk_size, hash_key):
        test_text = text

        t_builder = ""
        for i in range(0, SalmonCoreTestHelper.TEXT_ITERATIONS):
            t_builder += test_text

        plain_text = t_builder

        #  Use read from text byte array and to Write to byte array
        input_bytes = bytearray(plain_text.encode('utf-8'))
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        enc_writer = SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                                  None, integrity, chunk_size, hash_key)
        ins.copy_to(enc_writer)
        ins.close()
        enc_writer.flush()
        enc_writer.close()
        enc_bytes = outs.to_array()

        #  Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        enc_ins = MemoryStream(enc_bytes)
        dec_reader = SalmonStream(key, iv, EncryptionMode.Decrypt, enc_ins,
                                  None, integrity, chunk_size, hash_key)

        correct_text = plain_text[0:6]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 0, 6,
                                                                       RandomAccessStream.SeekOrigin.Begin)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[0:6]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 0, 6,
                                                                       RandomAccessStream.SeekOrigin.Begin)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[dec_reader.get_position() + 4: dec_reader.get_position() + 4 + 4]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 4, 4,
                                                                       RandomAccessStream.SeekOrigin.Current)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[dec_reader.get_position() + 6: dec_reader.get_position() + 6 + 4]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 6, 4,
                                                                       RandomAccessStream.SeekOrigin.Current)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[dec_reader.get_position() + 10: dec_reader.get_position() + 10 + 6]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 10, 6,
                                                                       RandomAccessStream.SeekOrigin.Current)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[12: 12 + 8]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 12, 8,
                                                                       RandomAccessStream.SeekOrigin.Begin)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)

        correct_text = plain_text[len(plain_text) - 14: len(plain_text) - 14 + 7]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 14, 7,
                                                                       RandomAccessStream.SeekOrigin.End)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)

        correct_text = plain_text[len(plain_text) - 27: len(plain_text) - 27 + 12]
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 27, 12,
                                                                       RandomAccessStream.SeekOrigin.End)

        SalmonCoreTestHelper.assert_equal(correct_text, dec_text)
        SalmonCoreTestHelper.test_counter(dec_reader)
        enc_ins.close()
        dec_reader.close()

    @staticmethod
    def seek_test_counter_and_block(text, key, iv,
                                    integrity, chunk_size, hash_key):

        t_builder = ""
        for i in range(0, SalmonCoreTestHelper.TEXT_ITERATIONS):
            t_builder += text

        plain_text = t_builder

        #  Use read from text byte array and to Write to byte array
        input_bytes = bytearray(plain_text.encode('utf-8'))
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        enc_writer = SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                                  None, integrity, chunk_size, hash_key)
        ins.copy_to(enc_writer)
        ins.close()
        enc_writer.flush()
        enc_writer.close()
        enc_bytes = outs.to_array()

        #  Use to read from cipher text and seek and read to different positions in the stream
        enc_ins = MemoryStream(enc_bytes)
        dec_reader = SalmonStream(key, iv, EncryptionMode.Decrypt, enc_ins,
                                  None, integrity, chunk_size, hash_key)
        for i in range(0, 100):
            dec_reader.set_position(dec_reader.get_position() + 7)
            SalmonCoreTestHelper.test_counter(dec_reader)

        enc_ins.close()
        dec_reader.close()

    @staticmethod
    def test_counter(dec_reader: SalmonStream):
        expected_block = dec_reader.get_position() // SalmonGenerator.BLOCK_SIZE

        SalmonCoreTestHelper.assert_equal(expected_block, dec_reader.get_block())

        counter_block = BitConverter.to_long(dec_reader.get_counter(), SalmonGenerator.NONCE_LENGTH,
                                             SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH)
        expected_counter_value = dec_reader.get_block()

        SalmonCoreTestHelper.assert_equal(expected_counter_value, counter_block)

        nonce = BitConverter.to_long(dec_reader.get_counter(), 0, SalmonGenerator.NONCE_LENGTH)
        expected_nonce = BitConverter.to_long(dec_reader.get_nonce(), 0, SalmonGenerator.NONCE_LENGTH)

        SalmonCoreTestHelper.assert_equal(expected_nonce, nonce)

    @staticmethod
    def seek_and_write(text: str, key, iv,
                       seek: int, write_count, text_to_write,
                       integrity, chunk_size, hash_key,
                       set_allow_range_write
                       ):

        t_builder = ""
        for i in range(0, SalmonCoreTestHelper.TEXT_ITERATIONS):
            t_builder += text

        plain_text = t_builder

        #  Use read from text byte array and to Write to byte array
        input_bytes = bytearray(plain_text.encode('utf-8'))
        ins = MemoryStream(input_bytes)
        outs = MemoryStream()
        enc_writer = SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                                  None, integrity, chunk_size, hash_key)
        ins.copy_to(enc_writer)
        ins.close()
        enc_writer.flush()
        enc_writer.close()
        enc_bytes = outs.to_array()

        #  partial write
        write_bytes = bytearray(text_to_write.encode('utf-8'))
        p_outs = MemoryStream(enc_bytes)
        partial_writer = SalmonStream(key, iv, EncryptionMode.Encrypt, p_outs,
                                      None, integrity, chunk_size, hash_key)
        aligned_position = seek
        align_offset = 0
        count = write_count

        #  set to allow rewrite
        if set_allow_range_write:
            partial_writer.set_allow_range_write(set_allow_range_write)
        partial_writer.seek(aligned_position, RandomAccessStream.SeekOrigin.Begin)
        partial_writer.write(write_bytes, 0, count)
        partial_writer.close()
        p_outs.close()

        #  Use SalmonStrem to read from cipher text and test if writing was successful
        enc_ins = MemoryStream(enc_bytes)
        dec_reader = SalmonStream(key, iv, EncryptionMode.Decrypt, enc_ins,
                                  None, integrity, chunk_size, hash_key)
        dec_text = SalmonCoreTestHelper.seek_and_get_substring_by_read(dec_reader, 0, len(text),
                                                                       RandomAccessStream.SeekOrigin.Begin)

        SalmonCoreTestHelper.assert_equal(text[0:seek], dec_text[0:seek])

        SalmonCoreTestHelper.assert_equal(text_to_write, dec_text[seek:seek + write_count])

        SalmonCoreTestHelper.assert_equal(text[seek + write_count:], dec_text[seek + write_count:])
        SalmonCoreTestHelper.test_counter(dec_reader)

        enc_ins.close()
        dec_reader.close()

    @staticmethod
    def test_counter_value(text, key, nonce, counter: int):
        test_text_bytes = bytearray(text.encode('utf-8'))
        ms = MemoryStream(test_text_bytes)
        stream: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Encrypt, ms,
                                            None, False, None, None)
        stream.set_allow_range_write(True)

        #  creating enormous files to test is overkill and since the law was made for man
        #  we use reflection to test this.
        try:
            stream._SalmonStream__transformer._increase_counter(counter)
        finally:
            stream.close()

    @staticmethod
    def default_aesctr_transform(plain_text: bytearray, test_key_bytes: bytearray, test_nonce_bytes: bytearray,
                                 encrypt: bool) -> bytearray:
        if len(test_nonce_bytes) < 16:
            tmp = bytearray(16)
            tmp[0:len(test_nonce_bytes)] = test_nonce_bytes[0:]
            test_nonce_bytes = tmp

        ctr = Counter.new(len(test_nonce_bytes) * 8,
                          initial_value=BitConverter.to_long(test_nonce_bytes, 0, len(test_nonce_bytes)))
        cipher = AES.new(test_key_bytes, AES.MODE_CTR, counter=ctr)

        #  mode doesn't make a difference since the encryption is symmetrical
        if encrypt:
            encrypted = cipher.encrypt(plain_text)
        else:
            encrypted = cipher.decrypt(plain_text)
        return bytearray(encrypted)

    @staticmethod
    def native_ctr_transform(v_input: bytearray, test_key_bytes: bytearray, test_nonce_bytes: bytearray,
                             encrypt: bool, provider_type: ProviderType):
        if len(test_nonce_bytes) < 16:
            tmp = bytearray(16)
            tmp[0:len(test_nonce_bytes)] = test_nonce_bytes[0:]
            test_nonce_bytes = tmp

        transformer = SalmonTransformerFactory.create(provider_type)
        transformer.init(test_key_bytes, test_nonce_bytes)
        output = bytearray(len(v_input))
        transformer.reset_counter()
        transformer.sync_counter(0)
        if encrypt:
            transformer.encrypt_data(v_input, 0, output, 0, len(v_input))
        else:
            transformer.decrypt_data(v_input, 0, output, 0, len(v_input))
        return output

    @staticmethod
    def get_rand_array(size):
        data = bytearray(os.urandom(size))
        return data

    @staticmethod
    def get_rand_array_same(size):
        data = bytearray(os.urandom(size))
        return data

    @staticmethod
    def encrypt_and_decrypt_byte_array(size: int, enable_log: bool = False):
        data = SalmonCoreTestHelper.get_rand_array(size)
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array2(data, enable_log)

    @staticmethod
    def encrypt_and_decrypt_byte_array2(data, enable_log):
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.get_encryptor() \
            .encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, False)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.get_decryptor() \
            .decrypt(enc_data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, False)
        t3 = time.time() * 1000

        SalmonCoreTestHelper.assert_array_equal(data, dec_data)
        if enable_log:
            print("enc time: " + str(t2 - t1))
            print("dec time: " + str(t3 - t2))
            print("Total: " + str(t3 - t1))

    @staticmethod
    def encrypt_and_decrypt_byte_array_native(size, enable_log):
        data = SalmonCoreTestHelper.get_rand_array(size)
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native2(data, enable_log)

    @staticmethod
    def encrypt_and_decrypt_byte_array_native2(data, enable_log):
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.native_ctr_transform(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                             SalmonCoreTestHelper.TEST_NONCE_BYTES, True,
                                                             SalmonStream.get_aes_provider_type())
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.native_ctr_transform(enc_data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                             SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                             False,
                                                             SalmonStream.get_aes_provider_type())
        t3 = time.time() * 1000

        SalmonCoreTestHelper.assert_array_equal(data, dec_data)
        if enable_log:
            print("enc time: " + str(t2 - t1))
            print("dec time: " + str(t3 - t2))
            print("Total: " + str(t3 - t1))

    @staticmethod
    def encrypt_and_decrypt_byte_array_def(size, enable_log):
        data = SalmonCoreTestHelper.get_rand_array(size)
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_def2(data, enable_log)

    @staticmethod
    def encrypt_and_decrypt_byte_array_def2(data, enable_log):
        t1 = time.time() * 1000
        enc_data = SalmonCoreTestHelper.default_aesctr_transform(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                 SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                 True)
        t2 = time.time() * 1000
        dec_data = SalmonCoreTestHelper.default_aesctr_transform(enc_data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                 SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                 False)
        t3 = time.time() * 1000

        SalmonCoreTestHelper.assert_array_equal(data, dec_data)
        if enable_log:
            print("enc: " + str(t2 - t1))
            print("dec: " + str(t3 - t2))
            print("Total: " + str(t3 - t1))

    @staticmethod
    def copy_memory(size):
        t1 = time.time() * 1000
        data = SalmonCoreTestHelper.get_rand_array(size)
        t2 = time.time() * 1000
        t3 = time.time() * 1000
        print("gen time: " + str(t2 - t1))
        print("copy time: " + str(t3 - t2))

        mem = bytearray(16)
        ms = MemoryStream(mem)
        ms.write(bytearray([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]), 3, 2)
        output = ms.to_array()
        print("write: " + str(output))
        buff = bytearray(16)
        ms.set_position(0)
        ms.read(buff, 1, 4)
        print("read: " + str(buff))

    @staticmethod
    def copy_from_mem_stream(size, buffer_size):
        test_data = SalmonCoreTestHelper.get_rand_array(size)
        digest = hashlib.md5(test_data).hexdigest()

        ms1 = MemoryStream(test_data)
        ms2 = MemoryStream()
        ms1.copy_to(ms2, buffer_size, None)
        ms1.close()
        ms2.close()
        data2: bytearray = ms2.to_array()

        SalmonCoreTestHelper.assert_equal(len(test_data), len(data2))
        digest2 = hashlib.md5(data2).hexdigest()
        ms1.close()
        ms2.close()

        SalmonCoreTestHelper.assert_array_equal(digest, digest2)

    @staticmethod
    def copy_from_mem_stream_to_salmon_stream(size, key, nonce,
                                              integrity, chunk_size: int | None, hash_key,
                                              buffer_size):

        test_data = SalmonCoreTestHelper.get_rand_array(size)
        digest = hashlib.md5(test_data)

        #  copy to a mem byte stream
        ms1 = MemoryStream(test_data)
        ms2 = MemoryStream()
        ms1.copy_to(ms2, buffer_size, None)
        ms1.close()

        #  encrypt to a memory byte stream
        ms2.set_position(0)
        ms3 = MemoryStream()
        salmon_stream: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Encrypt, ms3,
                                                   None, integrity, chunk_size, hash_key)
        #  we always align the writes to the chunk size if we enable integrity
        if integrity:
            buffer_size = salmon_stream.get_chunk_size()
        ms2.copy_to(salmon_stream, buffer_size, None)
        salmon_stream.close()
        ms2.close()
        enc_data = ms3.to_array()

        #  decrypt
        ms3 = MemoryStream(enc_data)
        ms3.set_position(0)
        ms4 = MemoryStream()
        salmon_stream2 = SalmonStream(key, nonce, EncryptionMode.Decrypt, ms3,
                                      None, integrity, chunk_size, hash_key)
        salmon_stream2.copy_to(ms4, buffer_size, None)
        salmon_stream2.close()
        ms3.close()
        ms4.set_position(0)
        digest2 = hashlib.md5(ms4.to_array())
        ms4.close()

    @staticmethod
    def calculate_hmac(v_bytes, offset, length, hash_key, include_data):
        salmon_integrity = SalmonIntegrity(True, hash_key, None, HmacSHA256Provider(),
                                           SalmonGenerator.HASH_RESULT_LENGTH)
        return SalmonIntegrity.calculate_hash(SalmonCoreTestHelper.hashProvider, v_bytes, offset, length, hash_key,
                                              include_data)
