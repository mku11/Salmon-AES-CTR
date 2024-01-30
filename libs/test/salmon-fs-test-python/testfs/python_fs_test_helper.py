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

import hashlib
import os
import time
from typing import BinaryIO
from unittest import TestCase

from typeguard import typechecked

from convert.bit_converter import BitConverter
from file.py_drive import PyDrive
from file.py_file import PyFile
from file.ireal_file import IRealFile
from iostream.memory_stream import MemoryStream
from iostream.random_access_stream import RandomAccessStream
from salmon.iostream.encryption_mode import EncryptionMode
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_decryptor import SalmonDecryptor
from salmon.salmon_default_options import SalmonDefaultOptions
from salmon.salmon_encryptor import SalmonEncryptor
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from salmonfs.salmon_drive import SalmonDrive
from salmonfs.salmon_drive_manager import SalmonDriveManager
from salmonfs.salmon_file import SalmonFile
from salmonfs.salmon_file_input_stream import SalmonFileInputStream
from sequence.isalmon_sequence_serializer import ISalmonSequenceSerializer
from sequence.salmon_file_sequencer import SalmonFileSequencer
from sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from test.test_helper import TestHelper
from utils.salmon_file_exporter import SalmonFileExporter
from utils.salmon_file_importer import SalmonFileImporter
from utils.salmon_file_searcher import SalmonFileSearcher


@typechecked
class PythonFSTestHelper:
    TEST_SEQUENCER_DIR = "D:\\tmp\\output"
    TEST_SEQUENCER_FILENAME = "fileseq.xml"

    ENC_IMPORT_BUFFER_SIZE = 512 * 1024
    ENC_IMPORT_THREADS = 2
    ENC_EXPORT_BUFFER_SIZE = 512 * 1024
    ENC_EXPORT_THREADS = 2

    ENABLE_LOG = True
    ENABLE_LOG_DETAILS = True
    ENABLE_FILE_PROGRESS = False
    ENABLE_MULTI_CPU = False

    testCase: TestCase = TestCase()

    @staticmethod
    def assert_equal(a, b):
        return TestHelper.assert_equal(a, b)

    @staticmethod
    def assert_array_equal(a, b):
        return TestHelper.assert_equal(a, b)

    @staticmethod
    def get_checksum(real_file: IRealFile) -> str:
        ins: BinaryIO | None = None
        try:
            ins = open(real_file.get_path(), 'rb')
            hash_md5 = hashlib.md5()
            while True:
                data = ins.read(32768)
                if not data:
                    break
                hash_md5.update(data)
            return hash_md5.hexdigest()

        finally:
            if ins is not None:
                ins.close()

    @staticmethod
    def import_and_export(vault_dir: str, password: str, import_file: str, import_buffer_size: int, import_threads: int,
                          export_buffer_size: int, export_threads: int, integrity: bool, bitflip: bool,
                          flip_position: int,
                          should_be_equal: bool, apply_file_integrity: bool, verify_file_integrity: bool):
        SalmonDriveManager.set_virtual_drive_class(PyDrive)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)

        SalmonDriveManager.create_drive(vault_dir, password)
        root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        root_dir.list_files()

        salmon_root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()

        file_to_import: PyFile = PyFile(import_file)
        hash_pre_import: str = PythonFSTestHelper.get_checksum(file_to_import)

        # import
        file_importer: SalmonFileImporter = SalmonFileImporter(import_buffer_size, import_threads,
                                                               PythonFSTestHelper.ENABLE_MULTI_CPU)
        file_importer.set_enable_log(PythonFSTestHelper.ENABLE_LOG)
        file_importer.set_enable_log_details(PythonFSTestHelper.ENABLE_LOG_DETAILS)
        print_import_progress = lambda vb, tb: print(
            "importing file: " + file_to_import.get_base_name() + ": " + str(vb) + "/" + str(
                tb)) if PythonFSTestHelper.ENABLE_FILE_PROGRESS else None
        salmon_file: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False,
                                                            apply_file_integrity, print_import_progress)

        PythonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        PythonFSTestHelper.testCase.assertTrue(salmon_file.exists())
        salmon_files: list[SalmonFile] = SalmonDriveManager.get_drive().get_virtual_root().list_files()
        real_file_size: int = file_to_import.length()
        for file in salmon_files:
            if file.get_base_name() == file_to_import.get_base_name():
                if should_be_equal:
                    PythonFSTestHelper.testCase.assertTrue(file.exists())
                    file_size: int = file.get_size()
                    PythonFSTestHelper.testCase.assertEqual(real_file_size, file_size)

        # export
        file_exporter: SalmonFileExporter = SalmonFileExporter(export_buffer_size, export_threads,
                                                               PythonFSTestHelper.ENABLE_MULTI_CPU)
        file_exporter.set_enable_log(PythonFSTestHelper.ENABLE_LOG)
        file_exporter.set_enable_log_details(PythonFSTestHelper.ENABLE_LOG_DETAILS)
        print_export_progress = lambda vb, tb: print(
            "exporting file: " + file_to_import.get_base_name() + ": " + str(vb) + "/" + str(
                tb)) if PythonFSTestHelper.ENABLE_FILE_PROGRESS else None

        if bitflip:
            PythonFSTestHelper.flip_bit(salmon_file, flip_position)

        export_file: IRealFile = file_exporter.export_file(salmon_file, SalmonDriveManager.get_drive().get_export_dir(),
                                                           None,
                                                           True, verify_file_integrity, print_export_progress)

        hash_post_export: str = PythonFSTestHelper.get_checksum(export_file)
        if should_be_equal:
            PythonFSTestHelper.testCase.assertEqual(hash_pre_import, hash_post_export)

    @staticmethod
    def import_and_search(vault_dir: str, password: str, import_file: str,
                          import_buffer_size: int, import_threads: int):
        SalmonDriveManager.set_virtual_drive_class(PyDrive)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)

        SalmonDriveManager.create_drive(vault_dir, password)
        root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        root_dir.list_files()
        salmon_root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        file_to_import: PyFile = PyFile(import_file)
        rbasename: str = file_to_import.get_base_name()

        # import
        file_importer: SalmonFileImporter = SalmonFileImporter(import_buffer_size, import_threads,
                                                               PythonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False, None)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_base_name()

        PythonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        PythonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        searcher: SalmonFileSearcher = SalmonFileSearcher()
        files: list[SalmonFile] = searcher.search(salmon_root_dir, basename, True, None, None)

        PythonFSTestHelper.testCase.assertTrue(len(files) > 0)
        PythonFSTestHelper.testCase.assertEqual(files[0].get_base_name(), basename)

    @staticmethod
    def import_and_copy(vault_dir: str, password: str, import_file: str, import_buffer_size: int, import_threads: int,
                        new_dir: str, move: bool):
        SalmonDriveManager.set_virtual_drive_class(PyDrive)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)

        SalmonDriveManager.create_drive(vault_dir, password)
        root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        root_dir.list_files()
        salmon_root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        file_to_import: PyFile = PyFile(import_file)
        rbasename: str = file_to_import.get_base_name()

        # import
        file_importer: SalmonFileImporter = SalmonFileImporter(import_buffer_size, import_threads,
                                                               PythonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False, None)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_base_name()

        PythonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        PythonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        check_sum_before: str = PythonFSTestHelper.get_checksum(salmon_file.get_real_file())
        new_dir1: SalmonFile = salmon_root_dir.create_directory(new_dir)
        new_file: SalmonFile
        if move:
            new_file = salmon_file.move(new_dir1, None)
        else:
            new_file = salmon_file.copy(new_dir1, None)

        PythonFSTestHelper.testCase.assertIsNotNone(new_file)
        check_sum_after: str = PythonFSTestHelper.get_checksum(new_file.get_real_file())

        PythonFSTestHelper.testCase.assertEqual(check_sum_before, check_sum_after)
        PythonFSTestHelper.testCase.assertEqual(salmon_file.get_base_name(), new_file.get_base_name())

    @staticmethod
    def flip_bit(salmon_file: SalmonFile, position: int):
        stream: RandomAccessStream = salmon_file.get_real_file().get_output_stream()
        stream.set_position(position)
        stream.write(bytearray([1]), 0, 1)
        stream.flush()
        stream.close()

    @staticmethod
    def should_create_file_without_vault(test_bytes: bytearray, key: bytearray, apply_integrity: bool,
                                         verify_integrity: bool,
                                         chunk_size: int, hash_key: bytearray, filename_nonce: bytearray,
                                         file_nonce: bytearray,
                                         output_dir: str, flip_bit: bool, flip_position: int, check_data: bool):
        # write file
        real_dir: IRealFile = PyFile(output_dir)
        v_dir: SalmonFile = SalmonFile(real_dir, None)
        filename: str = "test_" + str(int(time.time() * 1000)) + ".txt"
        new_file: SalmonFile = v_dir.create_file(filename, key, filename_nonce, file_nonce)
        if apply_integrity:
            new_file.set_apply_integrity(True, hash_key, chunk_size)
        stream: RandomAccessStream = new_file.get_output_stream()

        stream.write(test_bytes, 0, len(test_bytes))
        stream.flush()
        stream.close()
        real_file_path: str = new_file.get_real_file().get_path()

        # tamper
        if flip_bit:
            real_tmp_file: IRealFile = new_file.get_real_file()
            real_stream: RandomAccessStream = real_tmp_file.get_output_stream()
            real_stream.set_position(flip_position)
            real_stream.write(bytearray([0]), 0, 1)
            real_stream.flush()
            real_stream.close()

        # open file for read
        real_file: IRealFile = PyFile(real_file_path)
        read_file: SalmonFile = SalmonFile(real_file, None)
        read_file.set_encryption_key(key)
        read_file.set_requested_nonce(file_nonce)
        if verify_integrity:
            read_file.set_verify_integrity(True, hash_key)
        in_stream: SalmonStream = read_file.get_input_stream()
        text_bytes: bytearray = bytearray(len(test_bytes))
        in_stream.read(text_bytes, 0, len(text_bytes))
        in_stream.close()
        if check_data:
            PythonFSTestHelper.assert_array_equal(test_bytes, text_bytes)
        return read_file

    @staticmethod
    def export_and_import_auth(vault: str, import_file_path: str):
        export_auth_file_path: str = vault + os.sep + TestHelper.TEST_EXPORT_DIR
        seq_file1: str = vault + "/" + TestHelper.TEST_SEQUENCER_FILE1
        seq_file2: str = vault + "/" + TestHelper.TEST_SEQUENCER_FILE2

        # emulate 2 different devices with different sequencers
        sequencer1: SalmonFileSequencer = SalmonFileSequencer(PyFile(seq_file1), SalmonSequenceSerializer())
        sequencer2: SalmonFileSequencer = SalmonFileSequencer(PyFile(seq_file2), SalmonSequenceSerializer())

        # set to the first sequencer and create the vault
        SalmonDriveManager.set_sequencer(sequencer1)
        SalmonDriveManager.create_drive(vault, TestHelper.TEST_PASSWORD)
        SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
        # import a test file
        salmon_root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
        file_to_import: IRealFile = PyFile(import_file_path)
        file_importer: SalmonFileImporter = SalmonFileImporter(0, 0, PythonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file_a1: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                               None)
        nonce_a1: int = BitConverter.to_long(salmon_file_a1.get_requested_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        SalmonDriveManager.close_drive()

        # open with another device (different sequencer) and export auth id
        SalmonDriveManager.set_sequencer(sequencer2)
        SalmonDriveManager.open_drive(vault)
        SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
        auth_id: str = SalmonDriveManager.get_auth_id()
        success: bool = False
        try:
            # import a test file should fail because not authorized
            salmon_root_dir = SalmonDriveManager.get_drive().get_virtual_root()
            file_to_import = PyFile(import_file_path)
            file_importer = SalmonFileImporter(0, 0, PythonFSTestHelper.ENABLE_MULTI_CPU)
            file_importer.import_file(file_to_import, salmon_root_dir, None, False, False, None)
            success = True
        except Exception as ignored:
            pass
        PythonFSTestHelper.testCase.assertFalse(success)
        SalmonDriveManager.close_drive()

        # reopen with first device sequencer and export the auth file with the auth id from the second device
        SalmonDriveManager.set_sequencer(sequencer1)
        SalmonDriveManager.open_drive(vault)
        SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
        SalmonDriveManager.export_auth_file(auth_id, vault, TestHelper.TEST_EXPORT_DIR)
        config_file: IRealFile = PyFile(export_auth_file_path)
        salmon_cfg_file: SalmonFile = SalmonFile(config_file, SalmonDriveManager.get_drive())
        nonce_cfg: int = BitConverter.to_long(salmon_cfg_file.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        #  import another test file
        salmon_root_dir = SalmonDriveManager.get_drive().get_virtual_root()
        file_to_import = PyFile(import_file_path)
        file_importer = SalmonFileImporter(0, 0, PythonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file_a2: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                               None)
        nonce_a2: int = BitConverter.to_long(salmon_file_a2.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        SalmonDriveManager.close_drive()

        # reopen with second device(sequencer) and import auth file
        SalmonDriveManager.set_sequencer(sequencer2)
        SalmonDriveManager.open_drive(vault)
        SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
        SalmonDriveManager.import_auth_file(export_auth_file_path)
        # now import a 3rd file
        salmon_root_dir = SalmonDriveManager.get_drive().get_virtual_root()
        file_to_import = PyFile(import_file_path)
        salmon_file_b1: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                               None)
        nonce_b1: int = BitConverter.to_long(salmon_file_b1.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        salmon_file_b2: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                               None)
        nonce_b2: int = BitConverter.to_long(salmon_file_b2.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        SalmonDriveManager.close_drive()

        PythonFSTestHelper.testCase.assertEqual(nonce_a1, nonce_cfg - 1)
        PythonFSTestHelper.testCase.assertEqual(nonce_cfg, nonce_a2 - 2)
        PythonFSTestHelper.testCase.assertNotEqual(nonce_a2, nonce_b1)
        PythonFSTestHelper.testCase.assertEqual(nonce_b1, nonce_b2 - 2)

    class TestSalmonFileSequencer(SalmonFileSequencer):
        def __init__(self, sequence_file: IRealFile, serializer: ISalmonSequenceSerializer, test_max_nonce: bytearray,
                     offset: int):
            super().__init__(sequence_file, serializer)
            self.testMaxNonce = test_max_nonce
            self.offset = offset

        def init_sequence(self, drive_id: str, auth_id: str, start_nonce: bytearray, max_nonce: bytearray):
            n_max_nonce: int = BitConverter.to_long(self.testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH)
            start_nonce = BitConverter.to_bytes(n_max_nonce + self.offset, SalmonGenerator.NONCE_LENGTH)
            max_nonce = BitConverter.to_bytes(n_max_nonce, SalmonGenerator.NONCE_LENGTH)
            super().init_sequence(drive_id, auth_id, start_nonce, max_nonce)
            pass

    @staticmethod
    def test_max_files(vault_dir: str, seq_file: str, import_file: str, test_max_nonce: bytearray, offset: int,
                       should_import: bool):
        import_success: bool
        try:
            sequencer: SalmonFileSequencer = PythonFSTestHelper.TestSalmonFileSequencer(PyFile(seq_file),
                                                                                        SalmonSequenceSerializer(),
                                                                                        test_max_nonce, offset)

            SalmonDriveManager.set_sequencer(sequencer)
            try:
                drive: SalmonDrive = SalmonDriveManager.open_drive(vault_dir)
                drive.authenticate(TestHelper.TEST_PASSWORD)
            except Exception as ex:
                SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
            root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
            root_dir.list_files()
            salmon_root_dir: SalmonFile = SalmonDriveManager.get_drive().get_virtual_root()
            file_to_import: PyFile = PyFile(import_file)
            file_importer: SalmonFileImporter = SalmonFileImporter(0, 0, PythonFSTestHelper.ENABLE_MULTI_CPU)
            salmon_file: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                                None)
            import_success = salmon_file is not None
        except Exception as ex:
            import_success = False
            print(ex)

        PythonFSTestHelper.testCase.assertEqual(should_import, import_success)

    @staticmethod
    def test_examples():
        text: str = "This is a plaintext that will be used for testing"
        test_file: str = "D:/tmp/file.txt"
        t_file: IRealFile = PyFile(test_file)
        if t_file.exists():
            t_file.delete()
        v_bytes: bytearray = bytearray(text.encode('utf-8'))
        key: bytearray = SalmonGenerator.get_secure_random_bytes(32)  # 256-bit key
        nonce: bytearray = SalmonGenerator.get_secure_random_bytes(8)  # 64-bit nonce

        # Example 1: encrypt byte array
        enc_bytes: bytearray = SalmonEncryptor().encrypt(v_bytes, key, nonce, False)
        # decrypt byte array
        dec_bytes: bytearray = SalmonDecryptor(multi_cpu=TestHelper.ENABLE_MULTI_CPU).decrypt(enc_bytes, key, nonce,
                                                                                              False)

        PythonFSTestHelper.assert_array_equal(v_bytes, dec_bytes)

        # Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.get_secure_random_bytes(8)  # always get a fresh nonce!
        enc_text: str = SalmonTextEncryptor.encrypt_string(text, key, nonce, True)
        # decrypt string
        dec_text: str = SalmonTextDecryptor.decrypt_string(enc_text, key, None, True)

        PythonFSTestHelper.testCase.assertEqual(text, dec_text)

        # Example 3: encrypt data to an output stream
        enc_out_stream: MemoryStream = MemoryStream()  # or any other writeable Stream like to a file
        nonce = SalmonGenerator.get_secure_random_bytes(8)  # always get a fresh nonce!
        # pass the output stream to the SalmonStream
        encryptor: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Encrypt, enc_out_stream,
                                               None, False, None, None)
        # encrypt and write with a single call, you can also Seek() and Write()
        encryptor.write(v_bytes, 0, len(v_bytes))
        # encrypted data are now written to the encOutStream.
        enc_out_stream.set_position(0)
        enc_data: bytearray = enc_out_stream.to_array()
        encryptor.flush()
        encryptor.close()
        enc_out_stream.close()
        # decrypt a stream with encoded data
        enc_input_stream: RandomAccessStream = MemoryStream(enc_data)  # or any other readable Stream like from a file
        decryptor: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Decrypt, enc_input_stream,
                                               None, False, None, None)
        dec_buffer: bytearray = bytearray(1024)
        # decrypt and read data with a single call, you can also Seek() before Read()
        bytes_read: int = decryptor.read(dec_buffer, 0, len(dec_buffer))
        # encrypted data are now in the decBuffer
        decstr: str = dec_buffer[0:bytes_read].decode('utf-8')
        print(decstr)
        decryptor.close()
        enc_input_stream.close()

        PythonFSTestHelper.testCase.assertEqual(text, decstr)

        # Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        # with copy, move, rename, delete operations
        enc_file: SalmonFile = SalmonFile(PyFile(test_file), None)
        nonce = SalmonGenerator.get_secure_random_bytes(8)  # always get a fresh nonce!
        enc_file.set_encryption_key(key)
        enc_file.set_requested_nonce(nonce)
        stream: RandomAccessStream = enc_file.get_output_stream()
        # encrypt data and write with a single call
        stream.write(v_bytes, 0, len(v_bytes))
        stream.flush()
        stream.close()
        # decrypt an encrypted file
        enc_file2: SalmonFile = SalmonFile(PyFile(test_file), None)
        enc_file2.set_encryption_key(key)
        stream2: RandomAccessStream = enc_file2.get_input_stream()
        dec_buff: bytearray = bytearray(1024)
        # decrypt and read data with a single call, you can also Seek() to any position before Read()
        enc_bytes_read: int = stream2.read(dec_buff, 0, len(dec_buff))
        decstr2: str = dec_buff[0:enc_bytes_read].decode('utf-8')
        print(decstr2)
        stream2.close()

        PythonFSTestHelper.testCase.assertEqual(text, decstr2)

    @staticmethod
    def encrypt_and_decrypt_stream(data: bytearray, key: bytearray, nonce: bytearray):
        enc_out_stream: MemoryStream = MemoryStream()
        encryptor: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Encrypt, enc_out_stream)
        input_stream: RandomAccessStream = MemoryStream(data)
        input_stream.copy_to(encryptor)
        enc_out_stream.set_position(0)
        enc_data: bytearray = enc_out_stream.to_array()
        encryptor.flush()
        encryptor.close()
        enc_out_stream.close()
        input_stream.close()

        enc_input_stream: RandomAccessStream = MemoryStream(enc_data)
        decryptor: SalmonStream = SalmonStream(key, nonce, EncryptionMode.Decrypt, enc_input_stream)
        out_stream: MemoryStream = MemoryStream()
        decryptor.copy_to(out_stream)
        out_stream.set_position(0)
        dec_data: bytearray = out_stream.to_array()
        decryptor.close()
        enc_input_stream.close()
        out_stream.close()

        PythonFSTestHelper.assert_array_equal(data, dec_data)

    @staticmethod
    def get_real_file_contents(file_path: str) -> bytearray:
        file: IRealFile = PyFile(file_path)
        ins: RandomAccessStream = file.get_input_stream()
        outs: MemoryStream = MemoryStream()
        ins.copy_to(outs)
        outs.set_position(0)
        outs.flush()
        outs.close()
        ins.close()
        return outs.to_array()

    @staticmethod
    def seek_and_read_file_input_stream(data: bytearray, file_input_stream: SalmonFileInputStream, start: int,
                                        length: int,
                                        read_offset: int, should_read_length: int):
        buffer: bytearray = bytearray(length + read_offset)
        file_input_stream.seek(start, 0)
        bytes_read: int = file_input_stream.readinto(memoryview(buffer)[read_offset:read_offset + length])
        PythonFSTestHelper.testCase.assertEqual(should_read_length, bytes_read)
        tdata: bytearray = bytearray(len(buffer))
        tdata[read_offset:read_offset + should_read_length] = data[start:start + should_read_length]
        PythonFSTestHelper.assert_array_equal(tdata, buffer)

    @staticmethod
    def should_test_file_sequencer():
        file: IRealFile = PyFile(
            PythonFSTestHelper.TEST_SEQUENCER_DIR + "\\" + PythonFSTestHelper.TEST_SEQUENCER_FILENAME)
        if file.exists():
            file.delete()
        sequencer: SalmonFileSequencer = SalmonFileSequencer(file, SalmonSequenceSerializer())

        sequencer.create_sequence("AAAA", "AAAA")
        sequencer.init_sequence("AAAA", "AAAA",
                                BitConverter.to_bytes(1, 8),
                                BitConverter.to_bytes(4, 8))
        nonce: bytearray = sequencer.next_nonce("AAAA")
        PythonFSTestHelper.testCase.assertEqual(1, BitConverter.to_long(nonce, 0, 8))
        nonce = sequencer.next_nonce("AAAA")
        PythonFSTestHelper.testCase.assertEqual(2, BitConverter.to_long(nonce, 0, 8))
        nonce = sequencer.next_nonce("AAAA")
        PythonFSTestHelper.testCase.assertEqual(3, BitConverter.to_long(nonce, 0, 8))

        caught: bool = False
        try:
            nonce = sequencer.next_nonce("AAAA")
            PythonFSTestHelper.testCase.assertEqual(5, BitConverter.to_long(nonce, 0, 8))
        except SalmonRangeExceededException as ex:
            print(ex)
            caught = True
        PythonFSTestHelper.testCase.assertTrue(caught)

    @staticmethod
    def get_children_count_recursively(real_file: IRealFile) -> int:
        count: int = 1
        if real_file.is_directory():
            for child in real_file.list_files():
                count += PythonFSTestHelper.get_children_count_recursively(child)
        return count

    @staticmethod
    def copy_stream(src: SalmonFileInputStream, dest: MemoryStream):
        buffer_size: int = SalmonDefaultOptions.getBufferSize()
        bytes_read: int
        buffer: bytearray = bytearray(buffer_size)
        while (bytes_read := src.readinto(memoryview(buffer)[0: buffer_size])) > 0:
            dest.write(buffer, 0, bytes_read)
        dest.flush()
