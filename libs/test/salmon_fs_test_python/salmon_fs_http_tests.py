#!/usr/bin/env python3
'''
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
'''
from __future__ import annotations

from unittest import TestCase

from typeguard import typechecked
import os
import sys

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_fs')
sys.path.append(os.path.dirname(__file__) + '/../salmon_core_test_python')

from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.streams.memory_stream import MemoryStream
from salmon_fs.file.ireal_file import IRealFile
from salmon_fs.file.ivirtual_file import IVirtualFile
from salmon_fs.salmon.drive.py_http_drive import PyHttpDrive
from salmon_fs.salmon.salmon_drive import SalmonDrive

from salmon_core_test_helper import SalmonCoreTestHelper
from salmon_fs_test_helper import SalmonFSTestHelper, TestMode


@typechecked
class SalmonFSHttpTests(TestCase):
    old_test_mode = None

    @classmethod
    def setUpClass(cls):
        SalmonFSHttpTests.old_test_mode = SalmonFSTestHelper.curr_test_mode
        SalmonFSTestHelper.set_test_params("d:\\tmp\\salmon\\test", TestMode.Http)

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE

        # SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024
        # SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 1
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 1
        SalmonFSTestHelper.ENABLE_MULTI_CPU = False

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = False

        SalmonCoreTestHelper.initialize()
        SalmonFSTestHelper.initialize()

    @classmethod
    def tearDownClass(cls):
        SalmonFSTestHelper.close()
        SalmonCoreTestHelper.close()
        if SalmonFSHttpTests.old_test_mode:
            SalmonFSTestHelper.set_test_params(SalmonFSTestHelper.TEST_ROOT_DIR.get_path(),
                                               SalmonFSHttpTests.old_test_mode)

    def test_shouldCatchNotAuthorizeNegative(self):
        vault_dir: IRealFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        wrong_password: bool = False
        try:
            drive: PyHttpDrive = SalmonDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                        SalmonCoreTestHelper.TEST_FALSE_PASSWORD)
        except Exception as ex:
            print(ex, file=sys.stderr)
            wrong_password = True
        self.assertTrue(wrong_password)

    def test_shouldAuthorizePositive(self):
        vault_dir: IRealFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        wrong_password: bool = False
        try:
            drive: PyHttpDrive = SalmonDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                        SalmonCoreTestHelper.TEST_PASSWORD)
        except Exception as ex:
            print(ex, file=sys.stderr)
            wrong_password = True
            raise ex
        self.assertFalse(wrong_password)

    def test_shouldReadFromRealFileTiny(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME)

    def test_shouldReadFromRealFileSmall(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)

    def test_shouldReadFromRealFileLarge(self):
        SalmonFSTestHelper.should_read_file(SalmonFSTestHelper.HTTP_VAULT_DIR,
                                            SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME)

    def test_shouldSeekAndReadEncryptedFileStreamFromDrive(self):
        vault_dir: IRealFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        drive: PyHttpDrive = SalmonDrive.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                    SalmonCoreTestHelper.TEST_PASSWORD)
        root: IVirtualFile = drive.get_root()
        enc_file: IVirtualFile = root.get_child(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)
        self.assertEqual(enc_file.get_base_name(), SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)

        enc_stream: RandomAccessStream = enc_file.get_input_stream()
        ms: MemoryStream = MemoryStream()
        enc_stream.copy_to(ms)
        data: bytearray = ms.to_array()
        ms.close()
        enc_stream.close()
        SalmonFSTestHelper.seek_and_read_http_file(data, enc_file, True, 3, 50, 12)

    def test_shouldListFilesFromDrive(self):
        vault_dir: IRealFile = SalmonFSTestHelper.HTTP_VAULT_DIR
        drive: PyHttpDrive = PyHttpDrive.open(vault_dir, SalmonCoreTestHelper.TEST_PASSWORD)
        root: IVirtualFile = drive.get_root()
        files: list[IVirtualFile] = root.list_files()
        filenames: list[str] = []
        for i in range(len(files)):
            filename = files[i].get_base_name()
            filenames.append(filename)
        self.assertEqual(len(files), 4)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME in filenames)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME in filenames)
        self.assertTrue(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME in filenames)
