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
from typing import BinaryIO, Type
from unittest import TestCase

from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.salmon.drive.py_drive import PyDrive
from salmon_fs.file.py_file import PyFile
from salmon_fs.file.ireal_file import IRealFile
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_decryptor import SalmonDecryptor
from salmon_core.salmon.salmon_encryptor import SalmonEncryptor
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon_core.salmon.text.salmon_text_decryptor import SalmonTextDecryptor
from salmon_core.salmon.text.salmon_text_encryptor import SalmonTextEncryptor
from salmon_fs.salmon.salmon_drive import SalmonDrive
from salmon_fs.salmon.salmon_file import SalmonFile
from salmon_fs.salmon.streams.salmon_file_input_stream import SalmonFileInputStream
from salmon_fs.sequence.inonce_sequence_serializer import INonceSequenceSerializer
from salmon_fs.salmon.sequence.salmon_file_sequencer import SalmonFileSequencer
from salmon_fs.salmon.sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from salmon_core_test_helper import SalmonCoreTestHelper
from salmon_fs.salmon.utils.salmon_file_exporter import SalmonFileExporter
from salmon_fs.salmon.utils.salmon_file_importer import SalmonFileImporter
from salmon_fs.utils.file_searcher import FileSearcher
from salmon_fs.salmon.salmon_auth_config import SalmonAuthConfig


@typechecked
class SalmonFSTestHelper:
    drive_class_type: Type = PyDrive  # drive
    TEST_ROOT_DIR = "d:\\tmp\\"
    TEST_OUTPUT_DIR = TEST_ROOT_DIR + "output\\"
    TEST_VAULT_DIR = TEST_OUTPUT_DIR + "enc"

    TEST_VAULT2_DIR = TEST_OUTPUT_DIR + "enc2"

    TEST_EXPORT_AUTH_DIR = TEST_OUTPUT_DIR + "export\\"

    TEST_DATA_DIR_FOLDER = TEST_ROOT_DIR + "testdata\\"

    TEST_IMPORT_TINY_FILE = TEST_DATA_DIR_FOLDER + "tiny_test.txt"

    TEST_IMPORT_SMALL_FILE = TEST_DATA_DIR_FOLDER + "small_test.zip"

    TEST_IMPORT_MEDIUM_FILE = TEST_DATA_DIR_FOLDER + "medium_test.zip"

    TEST_IMPORT_LARGE_FILE = TEST_DATA_DIR_FOLDER + "large_test.mp4"

    TEST_IMPORT_HUGE_FILE = TEST_DATA_DIR_FOLDER + "huge.zip"

    TEST_IMPORT_FILE = TEST_IMPORT_TINY_FILE

    TEST_SEQUENCER_DIR = TEST_OUTPUT_DIR
    TEST_SEQUENCER_FILENAME = "fileseq.json"
    TEST_EXPORT_FILENAME = "export.slma"
    ENC_IMPORT_BUFFER_SIZE = 512 * 1024
    ENC_IMPORT_THREADS = 1
    ENC_EXPORT_BUFFER_SIZE = 512 * 1024
    ENC_EXPORT_THREADS = 1

    TEST_FILE_INPUT_STREAM_THREADS = 1
    TEST_USE_FILE_INPUT_STREAM = False
    ENABLE_FILE_PROGRESS = True

    TEST_SEQUENCER_FILE1 = "seq1.json"
    TEST_SEQUENCER_FILE2 = "seq2.json"

    TEST_HTTP_TINY_FILE = "tiny_test.txt"

    TEST_HTTP_SMALL_FILE = "small_test.zip"

    TEST_HTTP_MEDIUM_FILE = "medium_test.zip"

    TEST_HTTP_LARGE_FILE = "large_test.mp4"

    TEST_HTTP_FILE = TEST_HTTP_MEDIUM_FILE

    TEST_HTTP_TINY_FILE_SIZE = 27

    TEST_HTTP_TINY_FILE_CONTENTS = "This is a new file created."

    TEST_HTTP_TINY_FILE_CHKSUM = "69470e3c51279c8493be3f2e116a27ef620b3791cd51b27f924c589cb014eb92"

    TEST_HTTP_SMALL_FILE_SIZE = 1814885

    TEST_HTTP_SMALL_FILE_CHKSUM = "c3a0ef1598711e35ba2ba54d60d3722ebe0369ad039df324391ff39263edabd4"

    TEST_HTTP_LARGE_FILE_SIZE = 43315070

    TEST_HTTP_LARGE_FILE_CHKSUM = "3aaecd80a8fa3cbe6df8e79364af0412b7da6fa423d14c8c6bd332b32d7626b7"

    TEST_HTTP_DATA256_FILE = "data256.dat"

    TEST_HTTP_ENCDATA256_FILE = "encdata256.dat"

    TEST_ENC_HTTP_FILE = "encfile.dat"

    file_importer: SalmonFileImporter | None = None
    file_exporter: SalmonFileExporter | None = None
    ENABLE_MULTI_CPU = False

    testCase: TestCase = TestCase()

    @staticmethod
    def assert_equal(a, b):
        return SalmonCoreTestHelper.assert_equal(a, b)

    @staticmethod
    def assert_array_equal(a, b):
        return SalmonCoreTestHelper.assert_equal(a, b)

    @staticmethod
    def get_sequence_serializer() -> INonceSequenceSerializer:
        return SalmonSequenceSerializer()

    @staticmethod
    def set_drive_class_type(drive_class_type: Type):
        SalmonFSTestHelper.drive_class_type = drive_class_type

    @staticmethod
    def initialize():
        SalmonFSTestHelper.file_importer = SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                              SalmonFSTestHelper.ENC_IMPORT_THREADS,
                                                              SalmonFSTestHelper.ENABLE_MULTI_CPU)
        SalmonFSTestHelper.file_exporter = SalmonFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                              SalmonFSTestHelper.ENC_EXPORT_THREADS,
                                                              SalmonFSTestHelper.ENABLE_MULTI_CPU)

    @staticmethod
    def close():
        SalmonFSTestHelper.file_importer.close()
        SalmonFSTestHelper.file_exporter.close()

    @staticmethod
    def generate_folder(dir_path: str):
        v_time = time.time() * 1000
        dir_path = dir_path + "_" + str(int(v_time))
        os.mkdir(dir_path)
        return PyFile(dir_path)

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
    def import_and_export(vault_dir: IRealFile, password: str, import_file: str, import_buffer_size: int,
                          import_threads: int,
                          export_buffer_size: int, export_threads: int, integrity: bool, bitflip: bool,
                          flip_position: int,
                          should_be_equal: bool, apply_file_integrity: bool, verify_file_integrity: bool):
        sequencer: SalmonFileSequencer = SalmonFileSequencer(
            vault_dir.get_child(SalmonFSTestHelper.TEST_SEQUENCER_FILE1),
            SalmonFSTestHelper.get_sequence_serializer())
        drive = SalmonDrive.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir = drive.get_root()
        root_dir.list_files()

        file_to_import: PyFile = PyFile(import_file)
        hash_pre_import: str = SalmonFSTestHelper.get_checksum(file_to_import)

        # import
        print_import_progress = lambda vb, tb: print(
            "importing file: " + file_to_import.get_base_name() + ": " + str(vb) + "/" + str(
                tb)) if SalmonFSTestHelper.ENABLE_FILE_PROGRESS else None
        salmon_file: SalmonFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir, None, False,
                                                                               apply_file_integrity,
                                                                               print_import_progress)

        SalmonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())
        salmon_files: list[SalmonFile] = drive.get_root().list_files()
        real_file_size: int = file_to_import.length()
        for file in salmon_files:
            if file.get_base_name() == file_to_import.get_base_name():
                if should_be_equal:
                    SalmonFSTestHelper.testCase.assertTrue(file.exists())
                    file_size: int = file.get_size()
                    SalmonFSTestHelper.testCase.assertEqual(real_file_size, file_size)

        # export
        file_exporter: SalmonFileExporter = SalmonFileExporter(export_buffer_size, export_threads,
                                                               SalmonFSTestHelper.ENABLE_MULTI_CPU)
        print_export_progress = lambda vb, tb: print(
            "exporting file: " + salmon_file.get_base_name() + ": " + str(vb) + "/" + str(
                tb)) if SalmonFSTestHelper.ENABLE_FILE_PROGRESS else None

        if bitflip:
            SalmonFSTestHelper.flip_bit(salmon_file, flip_position)

        export_file: IRealFile = file_exporter.export_file(salmon_file, drive.get_export_dir(),
                                                           None,
                                                           True, verify_file_integrity, print_export_progress)

        hash_post_export: str = SalmonFSTestHelper.get_checksum(export_file)
        if should_be_equal:
            SalmonFSTestHelper.testCase.assertEqual(hash_pre_import, hash_post_export)

    @staticmethod
    def import_and_search(vault_dir: IRealFile, password: str, import_file: str,
                          import_buffer_size: int, import_threads: int):
        sequencer: SalmonFileSequencer = SalmonFileSequencer(
            vault_dir.get_child(SalmonFSTestHelper.TEST_SEQUENCER_FILE1),
            SalmonSequenceSerializer())
        drive = SalmonDrive.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir: SalmonFile = drive.get_root()
        file_to_import: PyFile = PyFile(import_file)
        rbasename: str = file_to_import.get_base_name()

        # import
        file_importer: SalmonFileImporter = SalmonFileImporter(import_buffer_size, import_threads,
                                                               SalmonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file: SalmonFile = file_importer.import_file(file_to_import, root_dir, None, False, False, None)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_base_name()

        SalmonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        searcher: FileSearcher = FileSearcher()
        files: list[SalmonFile] = searcher.search(root_dir, basename, True, None, None)

        SalmonFSTestHelper.testCase.assertTrue(len(files) > 0)
        SalmonFSTestHelper.testCase.assertEqual(files[0].get_base_name(), basename)

    @staticmethod
    def import_and_copy(vault_dir: IRealFile, password: str, import_file: str, import_buffer_size: int,
                        import_threads: int,
                        new_dir: str, move: bool):
        sequencer: SalmonFileSequencer = SalmonFileSequencer(
            vault_dir.get_child(SalmonFSTestHelper.TEST_SEQUENCER_FILE1),
            SalmonSequenceSerializer())
        drive = SalmonDrive.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir: SalmonFile = drive.get_root()
        file_to_import: PyFile = PyFile(import_file)
        rbasename: str = file_to_import.get_base_name()

        # import
        file_importer: SalmonFileImporter = SalmonFileImporter(import_buffer_size, import_threads,
                                                               SalmonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file: SalmonFile = file_importer.import_file(file_to_import, root_dir, None, False, False, None)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_base_name()

        SalmonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        check_sum_before: str = SalmonFSTestHelper.get_checksum(salmon_file.get_real_file())
        new_dir1: SalmonFile = root_dir.create_directory(new_dir)
        new_file: SalmonFile
        if move:
            new_file = salmon_file.move(new_dir1, None)
        else:
            new_file = salmon_file.copy(new_dir1, None)

        SalmonFSTestHelper.testCase.assertIsNotNone(new_file)
        check_sum_after: str = SalmonFSTestHelper.get_checksum(new_file.get_real_file())

        SalmonFSTestHelper.testCase.assertEqual(check_sum_before, check_sum_after)
        SalmonFSTestHelper.testCase.assertEqual(salmon_file.get_base_name(), new_file.get_base_name())

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
        filename: str = "test_" + str(int(time.time() * 1000)) + "." + str(flip_position) + ".txt"
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
            SalmonFSTestHelper.assert_array_equal(test_bytes, text_bytes)
        return read_file

    @staticmethod
    def export_and_import_auth(vault: IRealFile, import_file_path: str):
        seq_file1: IRealFile = vault.get_child(SalmonFSTestHelper.TEST_SEQUENCER_FILE1)
        seq_file2: IRealFile = vault.get_child(SalmonFSTestHelper.TEST_SEQUENCER_FILE2)

        # emulate 2 different devices with different sequencers
        sequencer1: SalmonFileSequencer = SalmonFileSequencer(seq_file1, SalmonSequenceSerializer())
        sequencer2: SalmonFileSequencer = SalmonFileSequencer(seq_file2, SalmonSequenceSerializer())

        # set to the first sequencer and create the vault
        drive = SalmonDrive.create_drive(vault, SalmonFSTestHelper.drive_class_type,
                                         SalmonCoreTestHelper.TEST_PASSWORD, sequencer1)
        # import a test file
        root_dir: SalmonFile = drive.get_root()
        file_to_import: IRealFile = PyFile(import_file_path)
        file_importer: SalmonFileImporter = SalmonFileImporter(0, 0, SalmonFSTestHelper.ENABLE_MULTI_CPU)
        salmon_file_a1: SalmonFile = file_importer.import_file(file_to_import, root_dir, None, False, False,
                                                               None)
        nonce_a1: int = BitConverter.to_long(salmon_file_a1.get_requested_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        drive.close()

        # open with another device (different sequencer) and export auth id
        drive = SalmonDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                       SalmonCoreTestHelper.TEST_PASSWORD, sequencer2)
        auth_id: str = drive.get_auth_id()
        success: bool = False
        try:
            # import a test file should fail because not authorized
            root_dir = drive.get_root()
            file_to_import = PyFile(import_file_path)
            file_importer.import_file(file_to_import, root_dir, None, False, False, None)
            success = True
        except Exception as ignored:
            pass
        SalmonFSTestHelper.testCase.assertFalse(success)
        drive.close()

        # reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = SalmonDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                       SalmonCoreTestHelper.TEST_PASSWORD, sequencer1)
        export_file: IRealFile = vault.get_child(SalmonFSTestHelper.TEST_EXPORT_FILENAME)
        SalmonAuthConfig.export_auth_file(drive, auth_id, export_file)
        export_auth_file: IRealFile = vault.get_child(SalmonFSTestHelper.TEST_EXPORT_FILENAME)
        salmon_cfg_file: SalmonFile = SalmonFile(export_auth_file, drive)
        nonce_cfg: int = BitConverter.to_long(salmon_cfg_file.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        #  import another test file
        salmon_root_dir = drive.get_root()
        file_to_import = PyFile(import_file_path)
        salmon_file_a2: SalmonFile = file_importer.import_file(file_to_import, salmon_root_dir, None, False, False,
                                                               None)
        nonce_a2: int = BitConverter.to_long(salmon_file_a2.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        drive.close()

        # reopen with second device(sequencer) and import auth file
        drive = SalmonDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                       SalmonCoreTestHelper.TEST_PASSWORD, sequencer2)
        SalmonAuthConfig.import_auth_file(drive, export_auth_file)
        # now import a 3rd file
        root_dir = drive.get_root()
        file_to_import = PyFile(import_file_path)
        salmon_file_b1: SalmonFile = file_importer.import_file(file_to_import, root_dir, None, False, False,
                                                               None)
        nonce_b1: int = BitConverter.to_long(salmon_file_b1.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        salmon_file_b2: SalmonFile = file_importer.import_file(file_to_import, root_dir, None, False, False,
                                                               None)
        nonce_b2: int = BitConverter.to_long(salmon_file_b2.get_file_nonce(), 0, SalmonGenerator.NONCE_LENGTH)
        drive.close()

        SalmonFSTestHelper.testCase.assertEqual(nonce_a1, nonce_cfg - 1)
        SalmonFSTestHelper.testCase.assertEqual(nonce_cfg, nonce_a2 - 2)
        SalmonFSTestHelper.testCase.assertNotEqual(nonce_a2, nonce_b1)
        SalmonFSTestHelper.testCase.assertEqual(nonce_b1, nonce_b2 - 2)

    class TestSalmonFileSequencer(SalmonFileSequencer):
        def __init__(self, sequence_file: IRealFile, serializer: INonceSequenceSerializer, test_max_nonce: bytearray,
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
    def test_max_files(vault_dir: IRealFile, seq_file: IRealFile, import_file: str, test_max_nonce: bytearray,
                       offset: int,
                       should_import: bool):
        import_success: bool
        try:
            sequencer: SalmonFileSequencer = SalmonFSTestHelper.TestSalmonFileSequencer(seq_file,
                                                                                        SalmonSequenceSerializer(),
                                                                                        test_max_nonce, offset)
            try:
                drive = SalmonDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                               SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            except Exception as ex:
                drive = SalmonDrive.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                 SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            root_dir: SalmonFile = drive.get_root()
            root_dir.list_files()
            file_to_import: PyFile = PyFile(import_file)
            salmon_file: SalmonFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir,
                                                                                   None, False, False, None)
            import_success = salmon_file is not None
        except Exception as ex:
            import_success = False
            print(ex)

        SalmonFSTestHelper.testCase.assertEqual(should_import, import_success)

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
        dec_bytes: bytearray = SalmonDecryptor(multi_cpu=SalmonCoreTestHelper.ENABLE_MULTI_CPU).decrypt(enc_bytes, key,
                                                                                                        nonce,
                                                                                                        False)

        SalmonFSTestHelper.assert_array_equal(v_bytes, dec_bytes)

        # Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.get_secure_random_bytes(8)  # always get a fresh nonce!
        enc_text: str = SalmonTextEncryptor.encrypt_string(text, key, nonce, True)
        # decrypt string
        dec_text: str = SalmonTextDecryptor.decrypt_string(enc_text, key, None, True)

        SalmonFSTestHelper.testCase.assertEqual(text, dec_text)

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

        SalmonFSTestHelper.testCase.assertEqual(text, decstr)

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

        SalmonFSTestHelper.testCase.assertEqual(text, decstr2)

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

        SalmonFSTestHelper.assert_array_equal(data, dec_data)

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
        SalmonFSTestHelper.testCase.assertEqual(should_read_length, bytes_read)
        tdata: bytearray = bytearray(len(buffer))
        tdata[read_offset:read_offset + should_read_length] = data[start:start + should_read_length]
        SalmonFSTestHelper.assert_array_equal(tdata, buffer)

    @staticmethod
    def should_test_file_sequencer():
        file: IRealFile = PyFile(
            SalmonFSTestHelper.TEST_SEQUENCER_DIR + "\\" + SalmonFSTestHelper.TEST_SEQUENCER_FILENAME)
        if file.exists():
            file.delete()
        sequencer: SalmonFileSequencer = SalmonFileSequencer(file, SalmonSequenceSerializer())

        sequencer.create_sequence("AAAA", "AAAA")
        sequencer.init_sequence("AAAA", "AAAA",
                                BitConverter.to_bytes(1, 8),
                                BitConverter.to_bytes(4, 8))
        nonce: bytearray = sequencer.next_nonce("AAAA")
        SalmonFSTestHelper.testCase.assertEqual(1, BitConverter.to_long(nonce, 0, 8))
        nonce = sequencer.next_nonce("AAAA")
        SalmonFSTestHelper.testCase.assertEqual(2, BitConverter.to_long(nonce, 0, 8))
        nonce = sequencer.next_nonce("AAAA")
        SalmonFSTestHelper.testCase.assertEqual(3, BitConverter.to_long(nonce, 0, 8))

        caught: bool = False
        try:
            nonce = sequencer.next_nonce("AAAA")
            SalmonFSTestHelper.testCase.assertEqual(5, BitConverter.to_long(nonce, 0, 8))
        except SalmonRangeExceededException as ex:
            print(ex)
            caught = True
        SalmonFSTestHelper.testCase.assertTrue(caught)

    @staticmethod
    def get_children_count_recursively(real_file: IRealFile) -> int:
        count: int = 1
        if real_file.is_directory():
            for child in real_file.list_files():
                count += SalmonFSTestHelper.get_children_count_recursively(child)
        return count

    @staticmethod
    def copy_stream(src: SalmonFileInputStream, dest: MemoryStream):
        buffer_size: int = 256 * 1024
        bytes_read: int
        buffer: bytearray = bytearray(buffer_size)
        while (bytes_read := src.readinto(memoryview(buffer)[0: buffer_size])) > 0:
            dest.write(buffer, 0, bytes_read)
        dest.flush()
