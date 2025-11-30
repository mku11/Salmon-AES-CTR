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
from unittest import TestCase
from typeguard import typechecked
import os
import sys

sys.path.append(os.path.dirname(__file__) + '/../../src/python/simple_io')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/simple_fs')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_fs')
sys.path.append(os.path.dirname(__file__) + '/../salmon_core_test_python')
from simple_io.streams.memory_stream import MemoryStream
from simple_io.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.integrity.integrity import Integrity
from simple_fs.fs.file.ivirtual_file import IVirtualFile
from salmon_fs.salmonfs.auth.auth_exception import AuthException
from salmon_fs.salmonfs.file.aes_file import AesFile, IFile
from salmon_fs.salmonfs.streams.aes_file_input_stream import AesFileInputStream
from salmon_fs.salmonfs.sequence.file_sequencer import FileSequencer
from salmon_fs.salmonfs.drive.utils.aes_file_commander import AesFileCommander

from salmon_core_test_helper import SalmonCoreTestHelper
from salmon_fs_test_helper import SalmonFSTestHelper, TestMode


@typechecked
class SalmonFSTests(TestCase):
    @classmethod
    def setUpClass(cls):

        test_dir: str = os.getenv("TEST_DIR", "d:\\tmp\\salmon\\test")
        test_mode: TestMode = TestMode[os.getenv("TEST_MODE")] if os.getenv("TEST_MODE") else TestMode.Local
        threads: int = int(os.getenv("ENC_THREADS")) if os.getenv("ENC_THREADS") else 1

        SalmonFSTestHelper.set_test_params(test_dir, test_mode)
        print("test_dir: " + test_dir)
        print("test_mode: " + str(test_mode))
        print("threads: " + str(threads))
        if test_mode == TestMode.WebService:
            print("ws server url: " + SalmonFSTestHelper.WS_SERVER_URL)

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE

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
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = False

        SalmonCoreTestHelper.initialize()
        SalmonFSTestHelper.initialize()

        provider_type: ProviderType = ProviderType[os.getenv("AES_PROVIDER_TYPE")] if os.getenv(
            "AES_PROVIDER_TYPE") else ProviderType.Default
        print("ProviderType: " + str(provider_type))

        AesStream.set_aes_provider_type(ProviderType.Default)

    @classmethod
    def tearDownClass(cls):
        SalmonFSTestHelper.close()
        SalmonCoreTestHelper.close()

    def test_CatchNotAuthorizedNegative(self):
        vault_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        sequencer: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        wrong_password: bool = False
        drive.close()
        try:
            drive = SalmonFSTestHelper.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                  SalmonCoreTestHelper.TEST_FALSE_PASSWORD, sequencer)
            root_dir: AesFile = drive.getRoot()
            root_dir.listFiles()
        except AuthException as ex:
            wrong_password = True

        self.assertTrue(wrong_password)

    def test_AuthorizedPositive(self):
        vault_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        sequencer: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        wrong_password: bool = False
        drive.close()
        try:
            drive = SalmonFSTestHelper.open_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                  SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            virtual_root: IVirtualFile = drive.get_root()
        except AuthException as ex:
            wrong_password = True

        self.assertFalse(wrong_password)

    def test_ImportAndExportNoIntegrityBitFlipDataNoCatch(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                True, 24 + 10, False, False, False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                integrity_failed = True
            else:
                raise ex

        self.assertFalse(integrity_failed)

    def test_ImportAndExportNoIntegrity(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                False, 0, True, False, False)
        except Exception as ex:
            if isinstance(ex.__cause__, IntegrityException):
                integrity_failed = True
            else:
                raise ex

        self.assertFalse(integrity_failed)

    def test_import_and_search_files(self):
        SalmonFSTestHelper.import_and_search(SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                                             SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE)

    def test_ImportAndCopyFile(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_copy(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                "subdir", False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_import_and_move_file(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_copy(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                "subdir", True)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                integrity_failed = True

        self.assertFalse(integrity_failed)

    def test_import_and_export_integrity_bit_flip_data(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                True, 24 + 10, False, True, True)
        except Exception as ex:

            if isinstance(ex.__cause__, IntegrityException) or "Data corrupt or tampered" in str(ex.__cause__):
                print("Caught:", ex)
                integrity_failed = True
            else:
                raise ex

        self.assertTrue(integrity_failed)

    def test_ImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch(self):
        integrity_failed: bool = False
        failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                True, 24 + 10, False, False, False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                integrity_failed = True
        except Exception as ex:
            failed = True

        self.assertFalse(integrity_failed)

        self.assertFalse(failed)

    def test_ImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch(self):
        failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                True, 36, False,
                True, False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                failed = True
        self.assertFalse(failed)

    def test_ImportAndExportAppliedIntegrityNoVerifyIntegrity(self):
        failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                False, 0, True,
                True, False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                failed = True
            raise ex

        self.assertFalse(failed)

    def test_ImportAndExportIntegrityBitFlipHeader(self):
        integrity_failed: bool = False
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                True, 20, False,
                True, True)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException) or "Data corrupt or tampered" in str(ex.__cause__):
                integrity_failed = True

        self.assertTrue(integrity_failed)

    def test_ImportAndExportIntegrity(self):
        import_success: bool = True
        try:
            SalmonFSTestHelper.import_and_export(
                SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD,
                SalmonFSTestHelper.TEST_IMPORT_FILE,
                False, 0, True,
                True, True)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                import_success = False
            raise ex
        self.assertTrue(import_success)

    def test_CatchVaultMaxFiles(self):
        vault_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        seq_dir = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_SEQ_DIRNAME,
                                                     SalmonFSTestHelper.TEST_SEQ_DIR, True)
        seq_file = seq_dir.get_child(SalmonFSTestHelper.TEST_SEQ_FILENAME)
        SalmonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                                          SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, True)

        # we need 2 nonces once of the filename the other for the file
        # so this should fail
        vault_dir = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        SalmonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                                          SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, False)

        vault_dir = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        SalmonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                                          SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, False)

        vault_dir = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        SalmonFSTestHelper.test_max_files(vault_dir, seq_file, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                                          SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, False)

    def test_CreateFileWithoutVault(self):
        SalmonFSTestHelper.should_create_file_without_vault(bytearray(SalmonCoreTestHelper.TEST_TEXT.encode('utf-8')),
                                                            SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                            True, True, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                            SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES,
                                                            SalmonCoreTestHelper.TEST_NONCE_BYTES, False, -1, True)

    def test_CreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught(self):

        caught: bool = False
        try:
            SalmonFSTestHelper.should_create_file_without_vault(
                bytearray(SalmonCoreTestHelper.TEST_TEXT.encode('utf-8')),
                SalmonCoreTestHelper.TEST_KEY_BYTES,
                True, True, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, True, 45, True)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True

        self.assertTrue(caught)

    def test_CreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught(self):
        text: str = SalmonCoreTestHelper.TEST_TEXT
        for i in range(5):
            caught: bool = False
            failed: bool = False
            try:
                SalmonFSTestHelper.should_create_file_without_vault(bytearray(text.encode('utf-8')),
                                                                    SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                    True, False, 64,
                                                                    SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                    SalmonCoreTestHelper.TEST_NONCE_BYTES, True, i,
                                                                    False)
            except IOError as ex:
                if isinstance(ex.__cause__, IntegrityException):
                    caught = True
            except Exception as ex:
                print(ex, file=sys.stderr)
                failed = True
            self.assertFalse(caught)
            self.assertFalse(failed)

    def test_CreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual(self):

        caught: bool = False
        try:
            SalmonFSTestHelper.should_create_file_without_vault(SalmonCoreTestHelper.TEST_TEXT.encode('utf-8'),
                                                                SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                                True, False, 64,
                                                                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                                                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES,
                                                                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                                                True, 24 + 32 + 5, False)
        except IOError as ex:
            if isinstance(ex.__cause__, IntegrityException):
                caught = True
        except Exception as ex:
            failed = True

        self.assertFalse(caught)

    def test_ExportAndImportAuth(self):
        vault: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        import_file_path: IFile = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE
        SalmonFSTestHelper.export_and_import_auth(vault, import_file_path)

    def test_EncryptAndDecryptStream(self):
        data: bytearray = SalmonFSTestHelper.get_real_file_contents(SalmonFSTestHelper.TEST_IMPORT_FILE)
        SalmonFSTestHelper.encrypt_and_decrypt_stream(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                                                      SalmonCoreTestHelper.TEST_NONCE_BYTES)

    def test_ShouldEncryptAndReadFileInputStream(self):
        data: bytearray = bytearray(256)
        for i in range(0, len(data)):
            data[i] = i

        file: AesFile = SalmonFSTestHelper \
            .should_create_file_without_vault(data,
                                              SalmonCoreTestHelper.TEST_KEY_BYTES,
                                              True, True, 64,
                                              SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                                              SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES,
                                              SalmonCoreTestHelper.TEST_NONCE_BYTES,
                                              False, -1, True)
        file_input_stream: AesFileInputStream = AesFileInputStream(file,
                                                                   3, 100,
                                                                   SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS,
                                                                   12)

        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 0, 32, 0, 32)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 220, 8, 2, 8)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 100, 2, 0, 2)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 6, 16, 0, 16)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 50, 40, 0, 40)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 124, 50, 0, 50)
        SalmonFSTestHelper.seek_and_read_file_input_stream(data, file_input_stream, 250, 10, 0, 6)
        file_input_stream.close()

    def test_CreateDriveAndOpenFsFolder(self):
        vault_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        sequencer: FileSequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        wrong_password: bool = False
        root_dir: IVirtualFile = drive.get_root()
        root_dir.list_files()
        drive.close()

        # reopen but open the file folder instead it should still login
        try:
            drive = SalmonFSTestHelper.open_drive(vault_dir.get_child("fs"), SalmonFSTestHelper.drive_class_type,
                                                  SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
            self.assertTrue(drive.has_config())
        except AuthException as ignored:
            wrong_password = True

        self.assertFalse(wrong_password)

    def test_CreateFileSequencer(self):
        SalmonFSTestHelper.should_test_file_sequencer()

    def test_shouldPerformOperationsRealFiles(self):
        caught: bool = False
        v_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        file: IFile = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE
        file1: IFile = file.copy(v_dir)
        file2: IFile
        try:
            file2 = file.copy(v_dir)
        except Exception as ex:
            print("Caught:", ex)
            caught = True

        self.assertEqual(True, caught)
        copy_options: IFile.CopyOptions = IFile.CopyOptions()
        copy_options.new_filename = IFile.auto_rename_file(file)
        file2 = file.copy(v_dir, copy_options)

        self.assertEqual(2, v_dir.get_children_count())
        self.assertTrue(v_dir.get_child(file.get_name()).exists())
        self.assertTrue(v_dir.get_child(file.get_name()).is_file())
        self.assertTrue(v_dir.get_child(file2.get_name()).exists())
        self.assertTrue(v_dir.get_child(file2.get_name()).is_file())

        dir1: IFile = v_dir.create_directory("folder1")
        self.assertTrue(v_dir.get_child("folder1").exists())
        self.assertTrue(v_dir.get_child("folder1").is_directory())
        self.assertEqual(3, v_dir.get_children_count())

        folder1: IFile = v_dir.create_directory("folder2")
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

        file3: IFile = file.copy(v_dir)
        caught = False
        try:
            file3.move(v_dir.get_child("folder1"))
        except Exception as ex:
            print("Caught:", ex)
            caught = True

        self.assertTrue(caught)
        move_options = IFile.MoveOptions()
        move_options.new_filename = IFile.auto_rename_file(file3)
        file4: IFile = file3.move(v_dir.get_child("folder1"), move_options)
        self.assertTrue(file4.exists())
        self.assertEqual(3, v_dir.get_child("folder1").get_children_count())

        folder2: IFile = v_dir.get_child("folder1").create_directory("folder2")
        for rfile in v_dir.get_child("folder1").list_files():
            rfile.copy_recursively(folder2)
        self.assertEqual(4, v_dir.get_child("folder1").get_children_count())
        self.assertEqual(3, v_dir.get_child("folder1").get_child("folder2").get_children_count())

        # recursive copy
        folder3: IFile = v_dir.create_directory("folder4")
        v_dir.get_child("folder1").copy_recursively(folder3)
        count1: int = SalmonFSTestHelper.get_children_count_recursively(v_dir.get_child("folder1"))
        count2: int = SalmonFSTestHelper.get_children_count_recursively(v_dir.get_child("folder4").get_child("folder1"))
        self.assertEqual(count1, count2)

        dfile: IFile = v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(
            file.get_name())
        self.assertTrue(dfile.exists())
        self.assertTrue(dfile.delete())
        self.assertEqual(2, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())
        rec_copy_options = IFile.RecursiveCopyOptions()
        rec_copy_options.auto_rename = IFile.auto_rename_file
        v_dir.get_child("folder1").copy_recursively(folder3, rec_copy_options)
        self.assertEqual(2, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(7, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(5, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())

        v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(file.get_name()).delete()
        v_dir.get_child("folder4").get_child("folder1").get_child(file.get_name()).delete()
        failed: list[IFile] = []
        rec_copy_options = IFile.RecursiveCopyOptions()
        rec_copy_options.on_failed = lambda failed_file, e: failed.append(failed_file)
        v_dir.get_child("folder1").copy_recursively(folder3, rec_copy_options)
        self.assertEqual(4, len(failed))
        self.assertEqual(2, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(7, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(5, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())

        v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_child(file.get_name()).delete()
        v_dir.get_child("folder4").get_child("folder1").get_child(file.get_name()).delete()
        failedmv: list[IFile] = []
        rec_move_options: IFile.RecursiveMoveOptions = IFile.RecursiveMoveOptions()
        rec_move_options.auto_rename = IFile.auto_rename_file
        rec_move_options.on_failed = lambda failed_file, e1: failedmv.append(failed_file)
        v_dir.get_child("folder1").move_recursively(v_dir.get_child("folder4"), rec_move_options)
        self.assertEqual(4, len(failed))
        self.assertEqual(1, v_dir.get_children_count())
        self.assertEqual(1, v_dir.get_child("folder4").get_children_count())
        self.assertEqual(9, v_dir.get_child("folder4").get_child("folder1").get_children_count())
        self.assertEqual(7, v_dir.get_child("folder4").get_child("folder1").get_child("folder2").get_children_count())

    def test_shouldReadFromFileMultithreaded(self):
        caught: bool = False
        vault_dir: IFile = SalmonFSTestHelper.generate_folder(SalmonFSTestHelper.TEST_VAULT_DIRNAME)
        file: IFile = SalmonFSTestHelper.TEST_IMPORT_FILE
        pos: int = 3 * Integrity.DEFAULT_CHUNK_SIZE + 3

        stream: RandomAccessStream = file.get_input_stream()
        stream.seek(pos, RandomAccessStream.SeekOrigin.Begin)
        h1 = SalmonCoreTestHelper.get_checksum_stream(stream)
        stream.close()

        sequencer = SalmonFSTestHelper.create_salmon_file_sequencer()
        drive = SalmonFSTestHelper.create_drive(vault_dir, SalmonFSTestHelper.drive_class_type,
                                                SalmonCoreTestHelper.TEST_PASSWORD, sequencer)
        file_commander: AesFileCommander = AesFileCommander(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE,
                                                            SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE,
                                                            SalmonFSTestHelper.ENC_IMPORT_THREADS,
                                                            multi_cpu=SalmonFSTestHelper.ENABLE_MULTI_CPU)

        def on_failed(v_file, ex):
            print("Error while import: " + str(ex), file=sys.stderr)
            raise ex

        import_options: AesFileCommander.BatchImportOptions = AesFileCommander.BatchImportOptions()
        import_options.integrity = True
        import_options.auto_rename = IFile.auto_rename_file
        import_options.on_failed = on_failed
        sfiles: list[AesFile] = file_commander.import_files([file], drive.get_root(), import_options)
        file_commander.close()
        print("files imported")

        print("using 1 thread to read")
        file_input_stream1 = AesFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024)
        print("seeking to: " + str(pos))
        file_input_stream1.seek(pos, 1)
        ms1: MemoryStream = MemoryStream()
        SalmonFSTestHelper.copy_bufferedio_stream(file_input_stream1, ms1)
        ms1.flush()
        ms1.set_position(0)
        h2: str = hashlib.md5(ms1.to_array()).hexdigest()
        file_input_stream1.close()
        ms1.close()
        self.assertEqual(h2, h1)

        print("using 2 threads to read")
        file_input_stream2 = AesFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 2, 256 * 1024)
        print("seeking to: " + str(pos))
        file_input_stream2.seek(pos, 1)
        ms2: MemoryStream = MemoryStream()
        SalmonFSTestHelper.copy_bufferedio_stream(file_input_stream2, ms2)
        ms2.flush()
        ms2.set_position(0)
        h3: str = hashlib.md5(ms2.to_array()).hexdigest()
        file_input_stream2.close()
        ms2.close()
        self.assertEqual(h3, h1)

    def test_raw_file(self):
        SalmonFSTestHelper.test_raw_file()

    def test_enc_dec_file(self):
        SalmonFSTestHelper.test_enc_dec_file()
