#!/usr/bin/env python3
from __future__ import annotations

__license__ = """
MIT License

Copyright (c) 2025 Max Kas

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

from unittest import TestCase
from typeguard import typechecked
import os
import sys

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_fs')
sys.path.append(os.path.dirname(__file__) + '/../salmon_core_test_python')
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_fs.fs.file.ifile import IFile
from salmon_fs.fs.file.http_file import HttpFile
from salmon_fs.fs.file.ivirtual_file import IVirtualFile
from salmon_fs.salmonfs.drive.http_drive import HttpDrive
from salmon_fs.salmonfs.drive.aes_drive import AesDrive

from salmon_core_test_helper import SalmonCoreTestHelper
from salmon_fs_test_helper import SalmonFSTestHelper, TestMode


@typechecked
class SalmonFSHttpTests(TestCase):
    old_test_mode = None

    @classmethod
    def setUpClass(cls):
        SalmonFSHttpTests.old_test_mode = SalmonFSTestHelper.curr_test_mode

        test_dir: str = os.getenv("TEST_DIR", "d:\\tmp\\salmon\\test")
        test_mode: TestMode = TestMode.Http
        threads: int = int(os.getenv("ENC_THREADS")) if os.getenv("ENC_THREADS") else 1

        SalmonFSTestHelper.set_test_params(test_dir, test_mode)
        print("test_dir", test_dir)
        print("test_mode", test_mode)
        print("threads", threads)
        print("http server url: ", SalmonFSTestHelper.HTTP_SERVER_URL)
        print("HTTP_VAULT_DIR_URL: ", SalmonFSTestHelper.HTTP_VAULT_DIR_URL)

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE

        # SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024
        # SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads
        SalmonFSTestHelper.ENABLE_MULTI_CPU = True

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = True

        SalmonCoreTestHelper.initialize()
        SalmonFSTestHelper.initialize()

        provider_type: ProviderType = ProviderType[os.getenv("AES_PROVIDER_TYPE")] if os.getenv(
            "AES_PROVIDER_TYPE") else ProviderType.Default
        print("ProviderType: " + str(provider_type))

        AesStream.set_aes_provider_type(provider_type)

    @classmethod
    def tearDownClass(cls):
        SalmonFSTestHelper.close()
        SalmonCoreTestHelper.close()
        if SalmonFSHttpTests.old_test_mode:
            SalmonFSTestHelper.set_test_params(SalmonFSTestHelper.TEST_ROOT_DIR.get_path(),
                                               SalmonFSHttpTests.old_test_mode)

    def test_shouldCatchNotAuthorizeNegative(self):
        vault_dir: IFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        wrong_password: bool = False
        try:
            drive: HttpDrive = AesDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                   SalmonCoreTestHelper.TEST_FALSE_PASSWORD)
        except Exception as ex:
            print(ex, file=sys.stderr)
            wrong_password = True
        self.assertTrue(wrong_password)

    def test_shouldAuthorizePositive(self):
        vault_dir: IFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        wrong_password: bool = False
        try:
            drive: HttpDrive = AesDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                   SalmonCoreTestHelper.TEST_PASSWORD)
        except Exception as ex:
            print(ex, file=sys.stderr)
            wrong_password = True
            raise ex
        self.assertFalse(wrong_password)

    def test_shouldReadFromFileTiny(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME)

    def test_shouldReadFromFileSmall(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)

    def test_shouldReadFromFileMedium(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME)

    def test_shouldReadFromFileLarge(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME)

    def test_shouldSeekAndReadEncryptedFileStreamFromDrive(self):
        vault_dir: IFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        drive: HttpDrive = AesDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                               SalmonCoreTestHelper.TEST_PASSWORD)
        root: IVirtualFile = drive.get_root()
        enc_file: IVirtualFile = root.get_child(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)
        self.assertEqual(enc_file.get_name(), SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)

        enc_stream: RandomAccessStream = enc_file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        enc_stream.copy_to(ms)
        data: bytearray = ms.to_array()
        ms.close()
        enc_stream.close()
        SalmonFSTestHelper.seek_and_read_http_file(data, enc_file, 3, 50, 12)

    def test_shouldListFilesFromDrive(self):
        vault_dir: IFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        drive: HttpDrive = HttpDrive.open(vault_dir, SalmonCoreTestHelper.TEST_PASSWORD)
        root: IVirtualFile = drive.get_root()
        files: list[IVirtualFile] = root.list_files()
        filenames: list[str] = []
        for i in range(len(files)):
            filename = files[i].get_name()
            filenames.append(filename)
        self.assertEqual(len(files), 4)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME in filenames)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME in filenames)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME in filenames)

    def test_shouldExportFileFromDrive(self):
        vault_dir = SalmonFSTestHelper.HTTP_VAULT_DIR
        drive = SalmonFSTestHelper.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                              SalmonCoreTestHelper.TEST_PASSWORD)
        file = drive.get_root().get_child(SalmonFSTestHelper.TEST_HTTP_FILE.get_name())
        export_dir = SalmonFSTestHelper.generate_folder("export_http", SalmonFSTestHelper.TEST_EXPORT_DIR, False)
        local_file = export_dir.get_child(SalmonFSTestHelper.TEST_HTTP_FILE.get_name())
        if local_file.exists():
            local_file.delete()
        SalmonFSTestHelper.export_files([file], export_dir)
        drive.close()

    def test_shouldReadRawFile(self):
        local_file = SalmonFSTestHelper.HTTP_TEST_DIR.get_child(
            SalmonFSTestHelper.TEST_HTTP_FILE.get_name())
        print("reading: " + local_file.get_display_path())
        local_chk_sum = SalmonFSTestHelper.get_checksum(local_file)
        http_root = HttpFile(SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL
                             + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME,
                             SalmonFSTestHelper.http_credentials)
        http_file = http_root.get_child(SalmonFSTestHelper.TEST_HTTP_FILE.get_name())
        stream = http_file.get_input_stream()
        ms = MemoryStream()
        stream.copy_to(ms)
        ms.flush()
        ms.set_position(0)
        ms.close()
        stream.close()
        digest = SalmonCoreTestHelper.get_checksum_stream(ms)
        SalmonFSTestHelper.testCase.assertEqual(digest, local_chk_sum)
