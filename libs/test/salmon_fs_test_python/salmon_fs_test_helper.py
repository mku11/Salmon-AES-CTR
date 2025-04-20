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
import time
import sys
import os
from enum import Enum
from unittest import TestCase
import random
from io import BufferedIOBase

from typeguard import typechecked

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_fs')
sys.path.append(os.path.dirname(__file__) + '/../salmon_core_test_python')
from salmon_core.convert.bit_converter import BitConverter
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.streams.buffered_io_wrapper import BufferedIOWrapper
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.range_exceeded_exception import RangeExceededException
from salmon_fs.salmonfs.drive.utils.aes_file_commander import AesFileCommander
from salmon_fs.salmonfs.drive.ws_drive import WSDrive
from salmon_fs.salmonfs.drive.http_drive import HttpDrive
from salmon_fs.salmonfs.drive.drive import Drive
from salmon_fs.fs.file.http_sync_client import HttpSyncClient
from salmon_fs.fs.file.http_file import HttpFile
from salmon_fs.fs.file.credentials import Credentials
from salmon_fs.fs.file.ws_file import WSFile
from salmon_fs.fs.file.file import File
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.salmonfs.drive.aes_drive import AesDrive
from salmon_fs.salmonfs.file.aes_file import AesFile
from salmon_fs.salmonfs.streams.aes_file_input_stream import AesFileInputStream
from salmon.sequence.inonce_sequence_serializer import INonceSequenceSerializer
from salmon_fs.salmonfs.sequence.file_sequencer import FileSequencer
from salmon.sequence.sequence_serializer import SequenceSerializer
from salmon_fs.salmonfs.drive.utils.aes_file_exporter import AesFileExporter
from salmon_fs.salmonfs.drive.utils.aes_file_importer import AesFileImporter
from salmon_fs.salmonfs.drive.utils.aes_file_searcher import AesFileSearcher
from salmon_fs.salmonfs.auth.auth_config import AuthConfig

from salmon_core_test_helper import SalmonCoreTestHelper


@typechecked
class TestMode(Enum):
    Local = 0
    Http = 2
    WebService = 3


@typechecked
class SalmonFSTestHelper:
    curr_test_mode = None
    drive_class_type = None
    TEST_ROOT_DIR = None  # root dir for testing
    TEST_INPUT_DIRNAME = "input"
    TEST_OUTPUT_DIRNAME = "output"
    TEST_VAULT_DIRNAME = "vault"
    TEST_OPER_DIRNAME = "files"
    TEST_EXPORT_AUTH_DIRNAME = "auth"
    TEST_EXPORT_DIRNAME = "export"
    TEST_IMPORT_TINY_FILENAME = "tiny_test.txt"
    TEST_IMPORT_SMALL_FILENAME = "small_test.dat"
    TEST_IMPORT_MEDIUM_FILENAME = "medium_test.dat"
    TEST_IMPORT_LARGE_FILENAME = "large_test.dat"
    TEST_IMPORT_HUGE_FILENAME = "huge_test.dat"
    TINY_FILE_CONTENTS = "This is a new file created that will be used for testing encryption and decryption."
    TEST_SEQ_DIRNAME = "seq"
    TEST_SEQ_FILENAME = "fileseq.xml"
    TEST_EXPORT_AUTH_FILENAME = "export.slma"

    # Web service
    WS_SERVER_URL = "http://localhost:8080"
    # WS_SERVER_URL = "https://localhost:8443" // for testing from the Web browser
    WS_SERVER_URL = os.getenv("WS_SERVER_URL", WS_SERVER_URL)
    WS_TEST_DIRNAME = "ws"
    credentials = Credentials("user", "password")

    # HTTP server(Read - only)
    HTTP_SERVER_URL = "http://localhost"
    HTTP_SERVER_URL = os.getenv("HTTP_SERVER_URL", HTTP_SERVER_URL)
    HTTP_SERVER_VIRTUAL_URL = HTTP_SERVER_URL + "/test"
    HTTP_TEST_DIRNAME = "httpserv"
    HTTP_VAULT_DIRNAME = "vault"
    HTTP_VAULT_DIR_URL = HTTP_SERVER_VIRTUAL_URL + "/" + HTTP_TEST_DIRNAME + "/" + HTTP_VAULT_DIRNAME
    HTTP_VAULT_FILES_DIR_URL = HTTP_VAULT_DIR_URL + "/file"
    http_credentials = Credentials("user", "password")

    # performance
    ENC_IMPORT_BUFFER_SIZE = 512 * 1024
    ENC_IMPORT_THREADS = 1
    ENC_EXPORT_BUFFER_SIZE = 512 * 1024
    ENC_EXPORT_THREADS = 1
    TEST_FILE_INPUT_STREAM_THREADS = 1
    TEST_USE_FILE_INPUT_STREAM = False

    # progress
    ENABLE_FILE_PROGRESS = False

    # test dirs and files
    TEST_INPUT_DIR = None
    TEST_OUTPUT_DIR = None
    TEST_IMPORT_TINY_FILE = None
    TEST_IMPORT_SMALL_FILE = None
    TEST_IMPORT_MEDIUM_FILE = None
    TEST_IMPORT_LARGE_FILE = None
    TEST_IMPORT_HUGE_FILE = None
    TEST_IMPORT_FILE = None
    WS_TEST_DIR = None
    HTTP_TEST_DIR = None
    HTTP_VAULT_DIR = None
    TEST_HTTP_TINY_FILE = None
    TEST_HTTP_SMALL_FILE = None
    TEST_HTTP_MEDIUM_FILE = None
    TEST_HTTP_LARGE_FILE = None
    TEST_HTTP_HUGE_FILE = None
    TEST_HTTP_FILE = None
    TEST_SEQ_DIR = None
    TEST_EXPORT_AUTH_DIR = None
    TEST_EXPORT_DIR = None
    file_importer: AesFileImporter = None
    file_exporter: AesFileExporter = None
    sequence_serializer = SequenceSerializer()

    ENABLE_MULTI_CPU = True
    testCase: TestCase = TestCase()

    @staticmethod
    def assert_equal(a, b):
        return SalmonCoreTestHelper.assert_equal(a, b)

    @staticmethod
    def assert_array_equal(a, b):
        return SalmonCoreTestHelper.assert_equal(a, b)

    @staticmethod
    def get_sequence_serializer() -> INonceSequenceSerializer:
        return SequenceSerializer()

    @staticmethod
    def set_test_params(test_dir: str, test_mode: TestMode):
        SalmonFSTestHelper.curr_test_mode = test_mode

        if test_mode == TestMode.Local:
            SalmonFSTestHelper.drive_class_type = Drive
        elif test_mode == TestMode.Http:
            SalmonFSTestHelper.drive_class_type = HttpDrive
        elif test_mode == TestMode.WebService:
            SalmonFSTestHelper.drive_class_type = WSDrive

        SalmonFSTestHelper.TEST_ROOT_DIR = File(test_dir)
        if not SalmonFSTestHelper.TEST_ROOT_DIR.exists():
            SalmonFSTestHelper.TEST_ROOT_DIR.mkdir()

        print("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.get_path())
        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                          SalmonFSTestHelper.TEST_INPUT_DIRNAME)
        if test_mode == TestMode.WebService:
            SalmonFSTestHelper.TEST_OUTPUT_DIR = WSFile("/", SalmonFSTestHelper.WS_SERVER_URL,
                                                        SalmonFSTestHelper.credentials)
        else:
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                               SalmonFSTestHelper.TEST_OUTPUT_DIRNAME)
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                       SalmonFSTestHelper.WS_TEST_DIRNAME)
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                         SalmonFSTestHelper.HTTP_TEST_DIRNAME)
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                        SalmonFSTestHelper.TEST_SEQ_DIRNAME)
        SalmonFSTestHelper.TEST_EXPORT_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                           SalmonFSTestHelper.TEST_EXPORT_DIRNAME)
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.create_dir(SalmonFSTestHelper.TEST_ROOT_DIR,
                                                                                SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME)
        SalmonFSTestHelper.HTTP_VAULT_DIR = HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL, SalmonFSTestHelper.http_credentials)
        SalmonFSTestHelper.create_test_files()
        SalmonFSTestHelper.create_http_files()
        SalmonFSTestHelper.create_http_vault()

        HttpSyncClient.set_allow_clear_text_traffic(True) # only for testing purposes

    @staticmethod
    def create_dir(parent: IFile, dir_name: str):
        v_dir = parent.get_child(dir_name)
        if not v_dir.exists():
            v_dir.mkdir()
        return v_dir

    @staticmethod
    def create_test_files():
        SalmonFSTestHelper.TEST_IMPORT_TINY_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME)
        SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)
        SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME)
        SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME)
        SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME)
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE

        SalmonFSTestHelper.create_file(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                                       SalmonFSTestHelper.TINY_FILE_CONTENTS)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE, 1024 * 1024)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE, 12 * 1024 * 1024)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE, 48 * 1024 * 1024)
        # SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE, 512 * 1024 * 1024)

    @staticmethod
    def create_http_files():
        SalmonFSTestHelper.TEST_HTTP_TINY_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME)
        SalmonFSTestHelper.TEST_HTTP_SMALL_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)
        SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME)
        SalmonFSTestHelper.TEST_HTTP_LARGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME)
        SalmonFSTestHelper.TEST_HTTP_HUGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME)
        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_TINY_FILE

        SalmonFSTestHelper.create_file(SalmonFSTestHelper.TEST_HTTP_TINY_FILE,
                                       SalmonFSTestHelper.TINY_FILE_CONTENTS)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_HTTP_SMALL_FILE, 1024 * 1024)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE, 12 * 1024 * 1024)
        SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_HTTP_LARGE_FILE, 48 * 1024 * 1024)
        # SalmonFSTestHelper.create_file_random_data(SalmonFSTestHelper.TEST_HTTP_HUGE_FILE, 512 * 1024 * 1024)

    @staticmethod
    def create_http_vault():
        http_vault_dir = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(SalmonFSTestHelper.HTTP_VAULT_DIRNAME)
        if http_vault_dir and http_vault_dir.exists():
            return
        http_vault_dir.mkdir()
        sequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(http_vault_dir, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        root_dir = drive.get_root()
        import_files = [SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                        SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,
                        SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,
                        SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE]
        importer = AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS)
        import_options: AesFileImporter.FileImportOptions = AesFileImporter.FileImportOptions()
        import_options.integrity = True
        for import_file in import_files:
            importer.import_file(import_file, root_dir, import_options)
        importer.close()

    @staticmethod
    def create_file(file: IFile, contents: str):
        if file.exists():
            return
        stream = file.get_output_stream()
        data: bytearray = bytearray(contents.encode())
        stream.write(data, 0, len(data))
        stream.flush()
        stream.close()

    @staticmethod
    def create_file_random_data(file: IFile, size: int):
        if file.exists():
            return
        stream = file.get_output_stream()
        while size > 0:
            data = random.randbytes(65536)
            length = min(size, len(data))
            stream.write(bytearray(data), 0, length)
            size -= length

        stream.flush()
        stream.close()

    @staticmethod
    def initialize():
        SalmonFSTestHelper.file_importer = AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                           SalmonFSTestHelper.ENC_IMPORT_THREADS,
                                                           SalmonFSTestHelper.ENABLE_MULTI_CPU)
        SalmonFSTestHelper.file_exporter = AesFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                           SalmonFSTestHelper.ENC_EXPORT_THREADS,
                                                           SalmonFSTestHelper.ENABLE_MULTI_CPU)

    @staticmethod
    def close():
        if SalmonFSTestHelper.file_importer:
            SalmonFSTestHelper.file_importer.close()
        if SalmonFSTestHelper.file_exporter:
            SalmonFSTestHelper.file_exporter.close()

    @staticmethod
    def create_salmon_file_sequencer() -> FileSequencer:
        # always create the sequencer files locally
        seq_dir = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_SEQ_DIRNAME,
                                                     SalmonFSTestHelper.TEST_SEQ_DIR,
                                                     True)
        seq_file = seq_dir.get_child(SalmonFSTestHelper.TEST_SEQ_FILENAME)
        return FileSequencer(seq_file, SalmonFSTestHelper.sequence_serializer)

    @staticmethod
    def generate_folder(name: str, parent: IFile | None = None, rand=True):
        if not parent:
            parent: IFile = SalmonFSTestHelper.TEST_OUTPUT_DIR
        dir_name: str = name + ("_" + str(time.time() * 1000 + random.random()) if rand else "")
        v_dir: IFile = parent.get_child(dir_name)
        if not v_dir.exists():
            v_dir.mkdir()
        print("generated folder: " + v_dir.get_path())
        return v_dir

    @staticmethod
    def get_checksum(file: IFile | AesFile) -> str:
        stream: RandomAccessStream | None = file.get_input_stream()
        return SalmonFSTestHelper.get_checksum_stream(stream)

    @staticmethod
    def get_checksum_stream(stream: RandomAccessStream | None = None) -> str:
        try:
            hash_md5 = hashlib.md5()
            buffer: bytearray = bytearray(256 * 1024)
            while (bytes_read := stream.read(buffer, 0, len(buffer))) > 0:
                hash_md5.update(buffer[0:bytes_read])
            return hash_md5.hexdigest()

        finally:
            if stream:
                stream.close()

    @staticmethod
    def import_and_export(vault_dir: IFile, password: str, import_file: IFile,
                          bitflip: bool, flip_position: int, should_be_equal: bool,
                          apply_file_integrity: bool, verify_file_integrity: bool):
        sequencer: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir = drive.get_root()
        root_dir.list_files()

        file_to_import: IFile = import_file
        hash_pre_import: str = SalmonFSTestHelper.get_checksum(file_to_import)

        # import
        def print_import_progress(vb, tb):
            print("importing file: " + file_to_import.get_name() + ": " + str(vb) + "/" + str(
                tb)) if SalmonFSTestHelper.ENABLE_FILE_PROGRESS else None

        import_options = AesFileImporter.FileImportOptions()
        import_options.integrity = apply_file_integrity
        import_options.on_progress_changed = print_import_progress
        salmon_file: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir, import_options)

        # get fresh copy of the file
        # TODO: for remote files the output stream should clear all cached file properties
        # instead of having to get a new file
        salmon_file = root_dir.get_child(salmon_file.get_name())

        chunk_size: int = salmon_file.get_file_chunk_size()
        if chunk_size == 0 or not verify_file_integrity:
            salmon_file.set_verify_integrity(False)
        else:
            salmon_file.set_verify_integrity(True)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())
        hash_post_import = SalmonFSTestHelper.get_checksum(salmon_file)
        if should_be_equal:
            SalmonFSTestHelper.testCase.assertEqual(hash_post_import, hash_pre_import)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file)
        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        salmon_files: list[AesFile] = drive.get_root().list_files()
        real_file_size: int = file_to_import.get_length()
        for file in salmon_files:
            if file.get_name() == file_to_import.get_name():
                if should_be_equal:
                    SalmonFSTestHelper.testCase.assertTrue(file.exists())
                    file_size: int = file.get_length()
                    SalmonFSTestHelper.testCase.assertEqual(real_file_size, file_size)

        # export
        def print_export_progress(vb, tb):
            print("exporting file: " + salmon_file.get_name() + ": " + str(vb) + "/" + str(
                tb)) if SalmonFSTestHelper.ENABLE_FILE_PROGRESS else None

        if bitflip:
            SalmonFSTestHelper.flip_bit(salmon_file, flip_position)
        chunk_size2: int | None = salmon_file.get_file_chunk_size()
        if chunk_size2 > 0 and verify_file_integrity:
            salmon_file.set_verify_integrity(True)
        else:
            salmon_file.set_verify_integrity(False)

        export_options = AesFileExporter.FileExportOptions()
        export_options.integrity = verify_file_integrity
        export_options.on_progress_changed = print_export_progress
        export_dir: IFile = SalmonFSTestHelper.generate_folder("export", SalmonFSTestHelper.TEST_EXPORT_DIR, False)
        export_file: IFile = SalmonFSTestHelper.file_exporter.export_file(salmon_file, export_dir,
                                                                          export_options)

        hash_post_export: str = SalmonFSTestHelper.get_checksum(export_file)
        if should_be_equal:
            SalmonFSTestHelper.testCase.assertEqual(hash_pre_import, hash_post_export)

    @staticmethod
    def open_drive(vault_dir: IFile, drive_class_type: type, password: str,
                   sequencer: FileSequencer | None = None):
        if drive_class_type == WSDrive:
            # use the remote service instead
            return WSDrive.open(vault_dir, password, sequencer)
        else:
            return AesDrive.open_drive(vault_dir, drive_class_type, password, sequencer)

    @staticmethod
    def create_drive(vault_dir: IFile, drive_class_type: type, password: str,
                     sequencer: FileSequencer | None = None):
        if drive_class_type == WSDrive:
            return WSDrive.create(vault_dir, password, sequencer)
        else:
            return AesDrive.create_drive(vault_dir, drive_class_type, password, sequencer)

    @staticmethod
    def import_and_search(vault_dir: IFile, password: str, import_file: IFile):
        sequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir: AesFile = drive.get_root()
        file_to_import: IFile = import_file
        rbasename: str = file_to_import.get_name()

        # import
        salmon_file: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_name()

        SalmonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        searcher: AesFileSearcher = AesFileSearcher()
        search_options = AesFileSearcher.SearchOptions()
        search_options.any_term = True
        files: list[AesFile] = searcher.search(root_dir, basename, search_options)

        SalmonFSTestHelper.testCase.assertTrue(len(files) > 0)
        SalmonFSTestHelper.testCase.assertEqual(files[0].get_name(), basename)

    @staticmethod
    def import_and_copy(vault_dir: IFile, password: str, import_file: IFile,
                        new_dir: str, move: bool):
        sequencer: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type, password, sequencer)
        root_dir: AesFile = drive.get_root()
        file_to_import: IFile = import_file
        rbasename: str = file_to_import.get_name()

        # import
        salmon_file: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)

        # trigger the cache to add the filename
        basename: str = salmon_file.get_name()

        SalmonFSTestHelper.testCase.assertIsNotNone(salmon_file)

        SalmonFSTestHelper.testCase.assertTrue(salmon_file.exists())

        check_sum_before: str = SalmonFSTestHelper.get_checksum(salmon_file.get_real_file())
        new_dir1: AesFile = root_dir.create_directory(new_dir)
        new_file: AesFile
        if move:
            new_file = salmon_file.move(new_dir1, None)
        else:
            new_file = salmon_file.copy(new_dir1, None)

        SalmonFSTestHelper.testCase.assertIsNotNone(new_file)
        check_sum_after: str = SalmonFSTestHelper.get_checksum(new_file.get_real_file())

        SalmonFSTestHelper.testCase.assertEqual(check_sum_before, check_sum_after)
        SalmonFSTestHelper.testCase.assertEqual(salmon_file.get_name(), new_file.get_name())

    @staticmethod
    def flip_bit(salmon_file: AesFile, position: int):
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
                                         flip_bit: bool, flip_position: int, check_data: bool):
        # write file
        real_dir: IFile = SalmonFSTestHelper.generate_folder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, False)
        v_dir: AesFile = AesFile(real_dir)
        filename: str = "test_" + str(int(time.time() * 1000)) + "." + str(flip_position) + ".txt"
        new_file: AesFile = v_dir.create_file(filename, key, filename_nonce, file_nonce)
        print("new file: " + new_file.get_path())
        if apply_integrity:
            new_file.set_apply_integrity(True, hash_key, chunk_size)
        else:
            new_file.set_apply_integrity(False)
        stream: RandomAccessStream = new_file.get_output_stream()

        stream.write(test_bytes, 0, len(test_bytes))
        stream.flush()
        stream.close()
        real_file: IFile = new_file.get_real_file()

        # tamper
        if flip_bit:
            real_tmp_file: IFile = new_file.get_real_file()
            real_stream: RandomAccessStream = real_tmp_file.get_output_stream()
            real_stream.set_position(flip_position)
            real_stream.write(bytearray([0]), 0, 1)
            real_stream.flush()
            real_stream.close()

        # open file for read
        read_file: AesFile = AesFile(real_file)
        read_file.set_encryption_key(key)
        read_file.set_requested_nonce(file_nonce)
        if verify_integrity:
            read_file.set_verify_integrity(True, hash_key)
        else:
            read_file.set_verify_integrity(False)

        in_stream: AesStream = read_file.get_input_stream()
        try:
            text_bytes: bytearray = bytearray(len(test_bytes))
            in_stream.read(text_bytes, 0, len(text_bytes))
            in_stream.close()
            if check_data:
                SalmonFSTestHelper.assert_array_equal(test_bytes, text_bytes)
        finally:
            in_stream.close()

        return read_file

    @staticmethod
    def export_and_import_auth(vault: IFile, import_file_path: IFile):
        # emulate 2 different devices with different sequencers
        sequencer1: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        sequencer2: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()

        # set to the first sequencer and create the vault
        drive = SalmonFSTestHelper.create_drive(vault, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer1)
        # import a test file
        root_dir: AesFile = drive.get_root()
        file_to_import: IFile = import_file_path
        salmon_file_a1: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)
        nonce_a1: int = BitConverter.to_long(salmon_file_a1.get_requested_nonce(), 0, Generator.NONCE_LENGTH)
        drive.close()

        # open with another device (different sequencer) and export auth id
        drive = AesDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer2)
        auth_id: str = drive.get_auth_id()
        success: bool = False
        try:
            # import a test file should fail because not authorized
            root_dir = drive.get_root()
            file_to_import = import_file_path
            SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)
            success = True
        except Exception as ignored:
            print("failed: " + str(ignored), file=sys.stderr)
        SalmonFSTestHelper.testCase.assertFalse(success)
        drive.close()

        # reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = AesDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer1)
        export_auth_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME,
                                                                    SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR)
        export_file = export_auth_dir.create_file(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME)
        AuthConfig.export_auth_file(drive, auth_id, export_file)
        export_auth_file: IFile = export_auth_dir.get_child(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME)
        salmon_cfg_file: AesFile = AesFile(export_auth_file, drive)
        nonce_cfg: int = BitConverter.to_long(salmon_cfg_file.get_file_nonce(), 0, Generator.NONCE_LENGTH)
        #  import another test file
        salmon_root_dir = drive.get_root()
        file_to_import = import_file_path
        salmon_file_a2: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, salmon_root_dir)
        nonce_a2: int = BitConverter.to_long(salmon_file_a2.get_file_nonce(), 0, Generator.NONCE_LENGTH)
        drive.close()

        # reopen with second device(sequencer) and import auth file
        drive = AesDrive.open_drive(vault, SalmonFSTestHelper.drive_class_type,
                                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer2)
        AuthConfig.import_auth_file(drive, export_auth_file)
        # now import a 3rd file
        root_dir = drive.get_root()
        file_to_import = import_file_path
        salmon_file_b1: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)
        nonce_b1: int = BitConverter.to_long(salmon_file_b1.get_file_nonce(), 0, Generator.NONCE_LENGTH)
        salmon_file_b2: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)
        nonce_b2: int = BitConverter.to_long(salmon_file_b2.get_file_nonce(), 0, Generator.NONCE_LENGTH)
        drive.close()

        SalmonFSTestHelper.testCase.assertEqual(nonce_a1, nonce_cfg - 1)
        SalmonFSTestHelper.testCase.assertEqual(nonce_cfg, nonce_a2 - 2)
        SalmonFSTestHelper.testCase.assertNotEqual(nonce_a2, nonce_b1)
        SalmonFSTestHelper.testCase.assertEqual(nonce_b1, nonce_b2 - 2)

    @typechecked
    class TestFileSequencer(FileSequencer):
        def __init__(self, sequence_file: IFile, serializer: INonceSequenceSerializer, test_max_nonce: bytearray,
                     offset: int):
            super().__init__(sequence_file, serializer)
            self.testMaxNonce = test_max_nonce
            self.offset = offset

        def init_sequence(self, drive_id: str, auth_id: str, start_nonce: bytearray, max_nonce: bytearray):
            n_max_nonce: int = BitConverter.to_long(self.testMaxNonce, 0, Generator.NONCE_LENGTH)
            start_nonce = BitConverter.to_bytes(n_max_nonce + self.offset, Generator.NONCE_LENGTH)
            max_nonce = BitConverter.to_bytes(n_max_nonce, Generator.NONCE_LENGTH)
            super().init_sequence(drive_id, auth_id, start_nonce, max_nonce)
            pass

    @staticmethod
    def test_max_files(vault_dir: IFile, seq_file: IFile, import_file: IFile, test_max_nonce: bytearray,
                       offset: int,
                       should_import: bool):
        import_success: bool
        try:
            sequencer: FileSequencer = SalmonFSTestHelper.TestFileSequencer(seq_file,
                                                                            SequenceSerializer(),
                                                                            test_max_nonce, offset)
            try:
                drive = AesDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                            SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            except Exception as ex:
                drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                        SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            root_dir: AesFile = drive.get_root()
            root_dir.list_files()
            file_to_import: IFile = import_file
            salmon_file: AesFile = SalmonFSTestHelper.file_importer.import_file(file_to_import, root_dir)
            import_success = salmon_file is not None
        except Exception as ex:
            if type(ex) is RangeExceededException:
                import_success = False
            else:
                raise

        SalmonFSTestHelper.testCase.assertEqual(should_import, import_success)

    @staticmethod
    def test_raw_file():
        text = "This is a plaintext that will be used for testing"
        # for i in range(18):
        #     text += text
        buff_size = 256 * 1024
        v_dir = SalmonFSTestHelper.generate_folder("test")
        filename = "file.txt"
        test_file = v_dir.create_file(filename)
        v_bytes = bytearray(text.encode())

        # write file
        write_file = v_dir.get_child(filename)
        wstream: RandomAccessStream = write_file.get_output_stream()
        idx = 0
        while idx < len(text):
            length = min(buff_size, len(text) - idx)
            wstream.write(v_bytes, idx, length)
            idx += length
        wstream.flush()
        wstream.close()

        # read a file
        read_file: IFile = v_dir.get_child(filename)
        rstream: RandomAccessStream = read_file.get_input_stream()
        read_buff = bytearray(buff_size)
        bytes_read = 0
        lstream = MemoryStream()
        while (bytes_read := rstream.read(read_buff, 0, len(read_buff))) > 0:
            lstream.write(read_buff, 0, bytes_read)
        lbytes = lstream.to_array()
        string = lbytes.decode()
        # console.log(string)
        rstream.close()

        SalmonFSTestHelper.testCase.assertEqual(string, text)

    @staticmethod
    def test_enc_dec_file():
        text = "This is a plaintext that will be used for testing"
        # for i in range(18):
        #     text += text
        buff_size = 256 * 1024

        v_dir = SalmonFSTestHelper.generate_folder("test")
        filename = "file.dat"
        test_file = v_dir.create_file(filename)
        v_bytes = text.encode()
        key = Generator.get_secure_random_bytes(32)  # 256-bit key
        nonce = Generator.get_secure_random_bytes(8)  # 64-bit nonce

        # Example 4: encrypt to a file, the AesFile has a virtual file system API
        # with copy, move, rename, delete operations
        wfile = v_dir.get_child(filename)
        enc_file = AesFile(wfile)
        nonce = Generator.get_secure_random_bytes(8)  # always get a fresh nonce!
        enc_file.set_encryption_key(key)
        enc_file.set_requested_nonce(nonce)
        stream = enc_file.get_output_stream()
        # encrypt data and write with a single call
        idx = 0
        while idx < len(text):
            length = min(buff_size, len(text) - idx)
            stream.write(bytearray(v_bytes[idx:idx + length]), 0, length)
            idx += length
        stream.flush()
        stream.close()

        # decrypt an encrypted file
        rfile = v_dir.get_child(filename)
        enc_file2 = AesFile(rfile)
        enc_file2.set_encryption_key(key)
        stream2 = enc_file2.get_input_stream()
        dec_buff = bytearray(buff_size)
        lstream = MemoryStream()
        bytes_read = 0
        # decrypt and read data with a single call, you can also Seek() to any position before Read()
        while (bytes_read := stream2.read(dec_buff, 0, len(dec_buff))) > 0:
            lstream.write(dec_buff, 0, bytes_read)

        lbytes = lstream.to_array()
        dec_string2 = lbytes.decode()
        # console.log(dec_string2)
        stream2.close()

        SalmonFSTestHelper.testCase.assertEqual(dec_string2, text)

    @staticmethod
    def encrypt_and_decrypt_stream(data: bytearray, key: bytearray, nonce: bytearray):
        enc_out_stream: MemoryStream = MemoryStream()
        encryptor: AesStream = AesStream(key, nonce, EncryptionMode.Encrypt, enc_out_stream)
        input_stream: RandomAccessStream = MemoryStream(data)
        input_stream.copy_to(encryptor)
        enc_out_stream.set_position(0)
        enc_data: bytearray = enc_out_stream.to_array()
        encryptor.flush()
        encryptor.close()
        enc_out_stream.close()
        input_stream.close()

        enc_input_stream: RandomAccessStream = MemoryStream(enc_data)
        decryptor: AesStream = AesStream(key, nonce, EncryptionMode.Decrypt, enc_input_stream)
        out_stream: MemoryStream = MemoryStream()
        decryptor.copy_to(out_stream)
        out_stream.set_position(0)
        dec_data: bytearray = out_stream.to_array()
        decryptor.close()
        enc_input_stream.close()
        out_stream.close()

        SalmonFSTestHelper.assert_array_equal(data, dec_data)

    @staticmethod
    def get_real_file_contents(file_path: IFile) -> bytearray:
        file: IFile = file_path
        ins: RandomAccessStream = file.get_input_stream()
        outs: MemoryStream = MemoryStream()
        ins.copy_to(outs)
        outs.set_position(0)
        outs.flush()
        outs.close()
        ins.close()
        return outs.to_array()

    @staticmethod
    def seek_and_read_file_input_stream(data: bytearray, file_input_stream: AesFileInputStream, start: int,
                                        length: int,
                                        read_offset: int, should_read_length: int):
        buffer: bytearray = bytearray(length + read_offset)
        file_input_stream.seek(start, 0)
        total_bytes_read = 0
        while buff := file_input_stream.read(length - total_bytes_read):
            buffer[read_offset + total_bytes_read:read_offset + total_bytes_read + len(buff)] = buff[:]
            total_bytes_read += len(buff)
        SalmonFSTestHelper.testCase.assertEqual(should_read_length, total_bytes_read)
        tdata: bytearray = bytearray(len(buffer))
        tdata[read_offset:read_offset + should_read_length] = data[start:start + should_read_length]
        SalmonFSTestHelper.assert_array_equal(tdata, buffer)

    @staticmethod
    def should_test_file_sequencer():
        sequencer = SalmonFSTestHelper.create_salmon_file_sequencer()

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
        except RangeExceededException as ex:
            print(ex, file=sys.stderr)
            caught = True
        SalmonFSTestHelper.testCase.assertTrue(caught)

    @staticmethod
    def get_children_count_recursively(real_file: IFile) -> int:
        count: int = 1
        if real_file.is_directory():
            for child in real_file.list_files():
                count += SalmonFSTestHelper.get_children_count_recursively(child)
        return count

    @staticmethod
    def copy_bufferedio_stream(src: AesFileInputStream, dest: MemoryStream):
        buffer_size: int = RandomAccessStream.DEFAULT_BUFFER_SIZE
        bytes_read: int
        buffer: bytearray = bytearray(buffer_size)
        while (bytes_read := src.readinto(memoryview(buffer)[0: buffer_size])) > 0:
            dest.write(buffer, 0, bytes_read)

        # total_bytes_read = 0
        # while buff := src.read(RandomAccessStream.DEFAULT_BUFFER_SIZE):
        #     dest.write(buff, 0, len(buff))
        #     total_bytes_read += len(buff)

        dest.flush()

    @staticmethod
    def should_read_file(vault_path: IFile, filename: str):
        local_file = SalmonFSTestHelper.TEST_INPUT_DIR.get_child(filename)
        local_chksum = SalmonFSTestHelper.get_checksum(local_file)

        vault_dir = vault_path
        sequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                              SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        root = drive.get_root()
        file = root.get_child(filename)
        print("file size: " + str(file.get_length()))
        print("file last modified: " + str(file.get_last_date_modified()))
        SalmonFSTestHelper.testCase.assertTrue(file.exists())

        stream: RandomAccessStream = file.get_input_stream()
        ms = MemoryStream()
        stream.copy_to(ms)
        ms.flush()
        ms.set_position(0)
        digest = SalmonFSTestHelper.get_checksum_stream(ms)
        ms.close()
        stream.close()
        # print("Text: ")
        # print(ms.to_array().decode())
        SalmonFSTestHelper.testCase.assertEqual(digest, local_chksum)

    @staticmethod
    def seek_and_read_http_file(data, file,
                                buffers_count=0, buffer_size=0, back_offset=0):
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     0, 32, 0, 32,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     220, 8, 2, 8,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     100, 2, 0, 2,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     6, 16, 0, 16,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     50, 40, 0, 40,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     124, 50, 0, 50,
                                                     buffers_count, buffer_size, back_offset)
        SalmonFSTestHelper.seek_and_read_file_stream(data, file,
                                                     250, 10, 0, 10,
                                                     buffers_count, buffer_size, back_offset)

    @staticmethod
    def seek_and_read_file_stream(data, file,
                                  start, length, read_offset, should_read_length,
                                  buffers_count=0, buffers_size=0, back_offset=0):
        buffer = bytearray(length + read_offset)

        stream: BufferedIOBase | None = None
        if SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM:
            # multi threaded
            stream = AesFileInputStream(file, buffers_count, buffers_size,
                                        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, back_offset)
        else:
            back_offset = 32768
            buffer_size = 4 * 1024 * 1024
            aes_stream: AesStream = file.get_input_stream()
            stream = BufferedIOWrapper(aes_stream, 1, buffer_size, back_offset, aes_stream.get_align_size())

        stream.seek(start, 1)
        res = stream.read(length)
        SalmonFSTestHelper.testCase.assertTrue(res)
        for i in range(length):
            buffer[read_offset + i] = res[i]

        tdata = bytearray(len(buffer))
        for i in range(should_read_length):
            tdata[read_offset + i] = data[start + i]
        print(tdata)
        print(buffer)
        stream.close()
        SalmonCoreTestHelper.testCase.assertEqual(tdata, buffer)

    @staticmethod
    def export_files(files: list[AesFile], v_dir: IFile, threads: int = 1):
        buffer_size = RandomAccessStream.DEFAULT_BUFFER_SIZE
        commander = AesFileCommander(buffer_size, buffer_size, threads,
                                     multi_cpu=SalmonFSTestHelper.ENABLE_MULTI_CPU)

        hash_pre_export = []
        for file in files:
            hash_pre_export.append(SalmonFSTestHelper.get_checksum(file))

        def print_export_progress(task_progress: AesFileCommander.AesFileTaskProgress) -> any:
            if not SalmonFSTestHelper.ENABLE_FILE_PROGRESS:
                return
            try:
                print("file exporting: " + task_progress.get_file().get_name() + ": "
                      + str(task_progress.get_processed_bytes()) + "/" + str(task_progress.get_total_bytes())
                      + " bytes")
            except Exception as e:
                print(e, file=sys.stderr)

        def on_failed(sfile: AesFile, ex: Exception):
            # file failed to import
            print(ex, file=sys.stderr)
            if ex.__cause__:
                print(ex.__cause__, file=sys.stderr)
            print("export failed: " + sfile.get_name() + "\n" + str(ex))

        # export files
        export_options = AesFileCommander.BatchExportOptions()
        export_options.delete_source = False
        export_options.integrity = True
        export_options.auto_rename = IFile.auto_rename_file
        export_options.on_failed = on_failed
        export_options.on_progress_changed = print_export_progress
        files_exported = commander.export_files(files, v_dir, export_options)

        print("Files exported")

        for i in range(len(files)):
            stream = files_exported[i].get_input_stream()
            hash_post_import = SalmonFSTestHelper.get_checksum_stream(stream)
            stream.close()
            SalmonFSTestHelper.testCase.assertEqual(hash_post_import, hash_pre_export[i])

        # close the file commander
        commander.close()
