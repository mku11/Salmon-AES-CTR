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
import random

from typeguard import typechecked

from file.py_drive import PyDrive
from file.py_file import PyFile
from file.virtual_file import VirtualFile
from iostream.memory_stream import MemoryStream
from salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon.salmon_default_options import SalmonDefaultOptions
from salmonfs.salmon_auth_exception import SalmonAuthException
from salmonfs.salmon_drive import SalmonDrive
from salmonfs.salmon_drive_manager import SalmonDriveManager
from salmonfs.salmon_file import SalmonFile, IRealFile
from salmonfs.salmon_file_input_stream import SalmonFileInputStream
from sequence.isalmon_sequence_serializer import ISalmonSequenceSerializer
from sequence.salmon_file_sequencer import SalmonFileSequencer
from testfs.python_fs_test_helper import PythonFSTestHelper

from sequence.salmon_sequence_serializer import SalmonSequenceSerializer
from test.salmon_python_test_runner import SalmonPythonTestRunner
from test.test_helper import TestHelper
from utils.salmon_file_commander import SalmonFileCommander


@typechecked
class SalmonFSPythonTestRunner(SalmonPythonTestRunner):
    SalmonDriveManager.set_virtual_drive_class(PyDrive)

    def test_AuthenticateNegative(self):
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)
        SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
        wrong_password: bool = False
        root_dir: VirtualFile = SalmonDriveManager.get_drive().get_virtual_root()
        root_dir.list_files()
        try:
            SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_FALSE_PASSWORD)
        except SalmonAuthException as ex:
            wrong_password = True

        self.assertTrue(wrong_password)

    def test_CatchNotAuthenticatedNegative(self):
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)
        SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
        wrong_password: bool = False
        SalmonDriveManager.close_drive()
        try:
            SalmonDriveManager.open_drive(vault_dir)
            root_dir: VirtualFile = SalmonDriveManager.get_drive().get_virtual_root()
            root_dir.list_files()
        except SalmonAuthException as ex:
            wrong_password = True

        self.assertTrue(wrong_password)

    def test_AuthenticatePositive(self):
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)
        SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
        wrong_password: bool = False
        SalmonDriveManager.close_drive()
        try:
            SalmonDriveManager.open_drive(vault_dir)
            SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
            virtual_root: VirtualFile = SalmonDriveManager.get_drive().get_virtual_root()
        except SalmonAuthException as ex:
            wrong_password = True

        self.assertFalse(wrong_password)

    def test_ImportAndExportNoIntegrityBitFlipDataNoCatch(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 False, True, 24 + 10, False, False, False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_ImportAndExportNoIntegrity(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 False, False, 0, True, False,
                                                 False)
        except Exception as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_import_and_search_files(self):
        PythonFSTestHelper.import_and_search(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                             TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                             PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                             PythonFSTestHelper.ENC_IMPORT_THREADS)

    def test_ImportAndCopyFile(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_copy(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                               TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                               PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                               PythonFSTestHelper.ENC_IMPORT_THREADS, "subdir", False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_import_and_move_file(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_copy(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                               TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                               PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                               PythonFSTestHelper.ENC_IMPORT_THREADS,
                                               "subdir",
                                               True)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_import_and_export_integrity_bit_flip_data(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, True, 24 + 10, False, True, True)
        except Exception as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertTrue(integrity_failed)

    def test_ImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch(self):
        integrity_failed: bool = False
        failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, True, 24 + 10, False, False, False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True
        except Exception as ex:
            failed = True

        self.assertFalse(integrity_failed)

        self.assertFalse(failed)

    def test_ImportAndExportNoAppliedIntegrityYesVerifyIntegrityNoBitFlipDataShouldCatch(self):
        failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, False, 0, False,
                                                 False, True)
        except Exception as ex:
            failed = True

        self.assertTrue(failed)

    def test_ImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch(self):
        failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, True, 36, False,
                                                 True, False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                failed = True
        self.assertFalse(failed)

    def test_ImportAndExportAppliedIntegrityNoVerifyIntegrity(self):
        failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, False, 0, True,
                                                 True, False)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                failed = True

        self.assertFalse(failed)

    def test_ImportAndExportIntegrityBitFlipHeader(self):
        integrity_failed: bool = False
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, True, 20, False,
                                                 True, True)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                integrity_failed = True

        self.assertTrue(integrity_failed)

    def test_ImportAndExportIntegrity(self):
        import_success: bool = True
        try:
            PythonFSTestHelper.import_and_export(TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR),
                                                 TestHelper.TEST_PASSWORD, SalmonPythonTestRunner.TEST_IMPORT_FILE,
                                                 PythonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_IMPORT_THREADS,
                                                 PythonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                 PythonFSTestHelper.ENC_EXPORT_THREADS,
                                                 True, False, 0, True,
                                                 True, True)
        except IOError as ex:
            print(ex)
            if isinstance(ex.__cause__, SalmonIntegrityException):
                import_success = False
        self.assertTrue(import_success)

    def test_CatchVaultMaxFiles(self):
        SalmonDriveManager.set_virtual_drive_class(PyDrive)

        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        seq_file: str = vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1

        PythonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE,
                                          TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, True)

        # we need 2 nonces once of the filename the other for the file
        # so this should fail
        vault_dir = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        seq_file = vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1
        PythonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE,
                                          TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, False)

        vault_dir = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        seq_file = vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1
        PythonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE,
                                          TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, False)

        vault_dir = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        seq_file = vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1
        PythonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE,
                                          TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, False)

    def test_CreateFileWithoutVault(self):
        PythonFSTestHelper.should_create_file_without_vault(bytearray(TestHelper.TEST_TEXT.encode('utf-8')),
                                                            TestHelper.TEST_KEY_BYTES,
                                                            True, True, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                                                            TestHelper.TEST_FILENAME_NONCE_BYTES,
                                                            TestHelper.TEST_NONCE_BYTES,
                                                            SalmonFSPythonTestRunner.TEST_OUTPUT_DIR,
                                                            False,
                                                            -1, True)

    def test_CreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught(self):

        caught: bool = False
        try:
            PythonFSTestHelper.should_create_file_without_vault(bytearray(TestHelper.TEST_TEXT.encode('utf-8')),
                                                                TestHelper.TEST_KEY_BYTES,
                                                                True, True, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                                                                TestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                TestHelper.TEST_NONCE_BYTES,
                                                                SalmonFSPythonTestRunner.TEST_OUTPUT_DIR,
                                                                True, 45, True)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_CreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught(self):
        text: str = TestHelper.TEST_TEXT
        for i in range(0, len(text)):
            caught: bool = False
            failed: bool = False
            try:
                PythonFSTestHelper.should_create_file_without_vault(bytearray(text.encode('utf-8')),
                                                                    TestHelper.TEST_KEY_BYTES,
                                                                    True, False, 64,
                                                                    TestHelper.TEST_HMAC_KEY_BYTES,
                                                                    TestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                    TestHelper.TEST_NONCE_BYTES,
                                                                    SalmonFSPythonTestRunner.TEST_OUTPUT_DIR, True, i,
                                                                    False)
            except IOError as ex:
                if isinstance(ex.__cause__, SalmonIntegrityException):
                    caught = True
            except Exception as ex:
                print(ex)
                failed = True
            self.assertFalse(caught)
            self.assertFalse(failed)

    def test_CreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual(self):

        caught: bool = False
        failed: bool = False
        try:
            PythonFSTestHelper.should_create_file_without_vault(TestHelper.TEST_TEXT.encode('utf-8'),
                                                                TestHelper.TEST_KEY_BYTES,
                                                                True, False, 64,
                                                                TestHelper.TEST_HMAC_KEY_BYTES,
                                                                TestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                TestHelper.TEST_NONCE_BYTES,
                                                                SalmonFSPythonTestRunner.TEST_OUTPUT_DIR,
                                                                True, 24 + 32 + 5, True)
        except IOError as ex:
            if isinstance(ex.__cause__, SalmonIntegrityException):
                caught = True
        except Exception as ex:
            failed = True

        self.assertFalse(caught)

        self.assertTrue(failed)

    def test_ExportAndImportAuth(self):
        vault: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT_DIR)
        import_file_path: str = SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE
        PythonFSTestHelper.export_and_import_auth(vault, import_file_path)

    def test_examples(self):
        PythonFSTestHelper.test_examples()

    def test_EncryptAndDecryptStream(self):
        data: bytearray = PythonFSTestHelper.get_real_file_contents(SalmonPythonTestRunner.TEST_IMPORT_FILE)
        PythonFSTestHelper.encrypt_and_decrypt_stream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES)

    def test_ShouldEncryptAndReadFileInputStream(self):
        data: bytearray = bytearray(256)
        for i in range(0, len(data)):
            data[i] = i

        file: SalmonFile = PythonFSTestHelper.should_create_file_without_vault(data, TestHelper.TEST_KEY_BYTES,
                                                                               True, True, 64,
                                                                               TestHelper.TEST_HMAC_KEY_BYTES,
                                                                               TestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                               TestHelper.TEST_NONCE_BYTES,
                                                                               SalmonFSPythonTestRunner.TEST_OUTPUT_DIR,
                                                                               False, -1, True)
        file_input_stream: SalmonFileInputStream = SalmonFileInputStream(file,
                                                                         3, 50, 2, 12)

        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 0, 32, 0, 32)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 220, 8, 2, 8)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 100, 2, 0, 2)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 6, 16, 0, 16)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 50, 40, 0, 40)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 124, 50, 0, 50)
        PythonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 250, 10, 0, 6)

    def test_CreateDriveAndOpenFsFolder(self):
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        sequence_file: PyFile = PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1)
        serializer: ISalmonSequenceSerializer = SalmonSequenceSerializer()
        sequencer: SalmonFileSequencer = SalmonFileSequencer(sequence_file, serializer)
        SalmonDriveManager.set_sequencer(sequencer)
        SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
        wrong_password: bool = False
        root_dir: VirtualFile = SalmonDriveManager.get_drive().get_virtual_root()
        root_dir.list_files()
        SalmonDriveManager.get_drive().close()

        # reopen but open the fs folder instead it should still login
        try:
            drive: SalmonDrive = SalmonDriveManager.open_drive(vault_dir + "/fs")
            self.assertTrue(drive.has_config())
            SalmonDriveManager.get_drive().authenticate(TestHelper.TEST_PASSWORD)
        except SalmonAuthException as ignored:
            wrong_password = True

        self.assertFalse(wrong_password)

    def test_CreateWinFileSequencer(self):
        PythonFSTestHelper.should_test_file_sequencer()

    def test_shouldPerformOperationsRealFiles(self):
        caught: bool = False
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        v_dir: PyFile = PyFile(vault_dir)
        file: PyFile = PyFile(SalmonPythonTestRunner.TEST_IMPORT_TINY_FILE)
        file1: IRealFile = file.copy(v_dir)
        file2: IRealFile
        try:
            file2 = file.copy(v_dir)
        except Exception as ex:
            print(ex)
            caught = True

        self.assertEqual(True, caught)
        file2 = file.copy(v_dir, IRealFile.auto_rename_file(file))

        self.assertEqual(2, v_dir.get_children_count())
        self.assertTrue(v_dir.get_child(file.get_base_name()).exists())
        self.assertTrue(v_dir.get_child(file.get_base_name()).is_file())
        self.assertTrue(v_dir.get_child(file2.get_base_name()).exists())
        self.assertTrue(v_dir.get_child(file2.get_base_name()).is_file())

        dir1: IRealFile = v_dir.create_directory("folder1")
        self.assertTrue(v_dir.get_child("folder1").exists())
        self.assertTrue(v_dir.get_child("folder1").is_directory())
        self.assertEqual(3, v_dir.get_children_count())

        folder1: IRealFile = v_dir.create_directory("folder2")
        self.assertTrue(folder1.exists())
        renamed: bool = folder1.rename_to("folder3")
        self.assertTrue(renamed)
        self.assertFalse(v_dir.get_child("folder2").exists())
        self.assertTrue(v_dir.get_child("folder3").exists())
        self.assertTrue(v_dir.get_child("folder3").is_directory())
        self.assertEqual(4, v_dir.get_children_count())
        delres: bool = v_dir.get_child("folder3").delete()
        self.assertTrue(delres)
        self.assertFalse(v_dir.get_child("folder3").exists())
        self.assertEqual(3, v_dir.get_children_count())

        file1.move(v_dir.get_child("folder1"))
        file2.move(v_dir.get_child("folder1"))

        file3: IRealFile = file.copy(v_dir)
        caught = False
        try:
            file3.move(v_dir.get_child("folder1"))
        except Exception as ex:
            print(ex)
            caught = True

        self.assertTrue(caught)
        file4: IRealFile = file3.move(v_dir.get_child("folder1"), IRealFile.auto_rename_file(file3))
        self.assertTrue(file4.exists())
        self.assertEqual(3, v_dir.get_child("folder1").get_children_count())

        folder2: IRealFile = v_dir.get_child("folder1").create_directory("folder2")
        for rfile in v_dir.get_child("folder1").list_files():
            rfile.copy(folder2)
        self.assertEqual(4, v_dir.get_child("folder1").get_children_count())
        self.assertEqual(4, v_dir.get_child("folder1").get_child("folder2").get_children_count())

        # recursive copy
        folder3: IRealFile = v_dir.create_directory("folder4")
        v_dir.get_child("folder1").copy_recursively(folder3)
        count1: int = PythonFSTestHelper.get_children_count_recursively(v_dir.get_child("folder1"))
        count2: int = PythonFSTestHelper.get_children_count_recursively(v_dir.get_child("folder4").get_child("folder1"))
        self.assertEqual(count1, count2)

        dfile: IRealFile = v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(
            file.get_base_name())
        self.assertTrue(dfile.exists())
        self.assertTrue(dfile.delete())
        self.assertEqual(3, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())
        v_dir.get_child("folder1").copy_recursively(folder3, None, IRealFile.auto_rename_file, False, None)
        self.assertEqual(2, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(7, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(6, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())
        self.assertEqual(0, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(
            "folder2").get_children_count())

        v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(file.get_base_name()).delete()
        v_dir.get_child("folder4").get_child("folder1").get_child(file.get_base_name()).delete()
        failed: list[IRealFile] = []
        v_dir.get_child("folder1").copy_recursively(folder3, None, None, False,
                                                    lambda failed_file, e: failed.append(failed_file))
        self.assertEqual(4, len(failed))
        self.assertEqual(2, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(7, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(6, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())
        self.assertEqual(0, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(
            "folder2").get_children_count())

        v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(file.get_base_name()).delete()
        v_dir.get_child("folder4").get_child("folder1").get_child(file.get_base_name()).delete()
        failedmv: list[IRealFile] = []
        v_dir.get_child("folder1").move_recursively(v_dir.get_child("folder4"), None, IRealFile.auto_rename_file, False,
                                                    lambda failed_file, e1: failedmv.append(failed_file))
        self.assertEqual(4, len(failed))
        self.assertEqual(1, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(9, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(8, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())
        self.assertEqual(0, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(
            "folder2").get_children_count())

    def ShouldReadFromFileMultithreaded(self):
        caught: bool = False
        vault_dir: str = TestHelper.generate_folder(SalmonPythonTestRunner.TEST_VAULT2_DIR)
        file: IRealFile = PyFile(TestHelper.TEST_IMPORT_MEDIUM_FILE)

        SalmonDriveManager.set_virtual_drive_class(PyDrive)
        sequencer: SalmonFileSequencer = SalmonFileSequencer(PyFile(vault_dir + "/" + TestHelper.TEST_SEQUENCER_FILE1),
                                                             SalmonSequenceSerializer())
        SalmonDriveManager.set_sequencer(sequencer)
        drive: SalmonDrive = SalmonDriveManager.create_drive(vault_dir, TestHelper.TEST_PASSWORD)
        sfiles: list[SalmonFile] = SalmonFileCommander(SalmonDefaultOptions.getBufferSize(),
                                                       SalmonDefaultOptions.getBufferSize(), 2).import_files(
            [file],
            drive.get_virtual_root(),
            False, True,
            None, None,
            None)

        file_input_stream1: SalmonFileInputStream = SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024)
        h1: str = hashlib.md5(file_input_stream1).hexdigest()

        file_input_stream2: SalmonFileInputStream = SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024)
        h2: str = hashlib.md5(file_input_stream2).hexdigest()
        self.assertEqual(h1, h2)

        pos: int = abs(random.randint(0, file.length()))

        file_input_stream1 = SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024)
        file_input_stream1.seek(pos, 1)
        ms1: MemoryStream = MemoryStream()
        PythonFSTestHelper.copy_stream(file_input_stream1, ms1)
        ms1.flush()
        ms1.set_position(0)
        file_input_stream1.set_position(0)
        h3: str = hashlib.md5(ms1.to_array()).hexdigest()
        file_input_stream1.close()
        ms1.close()

        file_input_stream2 = SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024)
        file_input_stream2.seek(pos, 1)
        ms2: MemoryStream = MemoryStream()
        PythonFSTestHelper.copy_stream(file_input_stream2, ms2)
        ms2.flush()
        ms2.set_position(0)
        h4: str = hashlib.md5(ms2.to_array()).hexdigest()
        file_input_stream2.close()
        ms2.close()
        self.assertEqual(h3, h4)
