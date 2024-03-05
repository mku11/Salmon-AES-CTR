/*
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
*/

import { MemoryStream } from '../../lib/salmon-core/io/memory_stream.js';
import { BitConverter } from '../../lib/salmon-core/convert/bit_converter.js';
import { SalmonDrive } from '../../lib/salmon-fs/salmonfs/salmon_drive.js';
import { TestHelper } from '../salmon-core/test_helper.js';
import { setTestMode, TestMode, getFile, getFileStream, getSequenceSerializer, TsFsTestHelper } from './ts_fs_test_helper.js';
import { SalmonFileSequencer } from '../../lib/salmon-fs/sequence/salmon_file_sequencer.js';
import { SalmonIntegrityException } from '../../lib/salmon-core/salmon/integrity/salmon_integrity_exception.js';
import { copyRecursively, moveRecursively, autoRenameFile } from '../../lib/salmon-fs/file/ireal_file.js'
import { SalmonFileReadableStream } from '../../lib/salmon-fs/salmonfs/salmon_file_readable_stream.js';
import { SalmonFileCommander } from '../../lib/salmon-fs/utils/salmon_file_commander.js';
import { SalmonDefaultOptions } from '../../lib/salmon-core/salmon/salmon_default_options.js';
import { SalmonAuthException } from '../../lib/salmon-fs/salmonfs/salmon_auth_exception.js'

// change to Local to run on the browser
// change to Node to run on the command line or VS code
await setTestMode(TestMode.Local);

describe('salmon-fs', () => {

    beforeAll(() => {
        TsFsTestHelper.TEST_IMPORT_FILE = TsFsTestHelper.TEST_IMPORT_MEDIUM_FILE;

        TsFsTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        TsFsTestHelper.ENC_IMPORT_THREADS = 2;
        TsFsTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        TsFsTestHelper.ENC_EXPORT_THREADS = 2;

        TsFsTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        TsFsTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        TestHelper.initialize();
        TsFsTestHelper.initialize();
    });

    afterAll(() => {
        TsFsTestHelper.close();
        TestHelper.close();
    });

    beforeEach(() => {

    });

    it('shouldAuthorizeNegative', async () => {
        let vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
        let wrongPassword = false;
        let rootDir = await drive.getVirtualRoot();
        await rootDir.listFiles();
        try {
            await drive.unlock(TestHelper.TEST_FALSE_PASSWORD);
        } catch (ex) {
            console.error(ex);
            if (ex instanceof SalmonAuthException)
                wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldCatchNotAuthorizeNegative', async () => {
        let vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
        let wrongPassword = false;
        drive.lock();
        try {
            let drive = await SalmonDrive.openDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer);
            let rootDir = await drive.getVirtualRoot();
            await rootDir.listFiles();
        } catch (ex) {
            console.error(ex);
            if (ex instanceof SalmonAuthException)
                wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldAuthorizePositive', async () => {
        let vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
        let wrongPassword = false;
        drive.lock();
        try {
            drive = await SalmonDrive.openDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer);
            await drive.unlock(TestHelper.TEST_PASSWORD);
            let virtualRoot = await drive.getVirtualRoot();
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }
        expect(wrongPassword).toBeFalsy();
    });

    it('shouldImportAndExportNoIntegrityBitFlipDataNoCatch', async () => {
        let integrityFailed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, true, false, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }
        expect(integrityFailed).toBeFalsy();
    });

    it('shouldImportAndExportNoIntegrity', async () => {
        let integrityFailed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                false, 0, true, false,
                false);
        } catch (ex) {
            console.error(ex);
            integrityFailed = true;
        }

        expect(integrityFailed).toBeFalsy();
    });

    it('shouldImportAndSearchFiles', async () => {
        await TsFsTestHelper.importAndSearch(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE);
    });

    it('shouldImportAndCopyFile', async () => {
        let failed = false;
        try {
            await TsFsTestHelper.importAndCopy(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                "subdir", false);
        } catch (ex) {
            console.error(ex);
            failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndMoveFile', async () => {
        let failed = false;
        try {
            await TsFsTestHelper.importAndCopy(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                "subdir", true);
        } catch (ex) {
            console.error(ex);
            failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportIntegrityBitFlipData', async () => {
        let integrityFailed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        expect(integrityFailed).toBeTruthy();
    });

    it('shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch', async () => {
        let integrityFailed = false;
        let failed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, false, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
            failed = true;
        }
        expect(integrityFailed).toBeFalsy();
        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportNoAppliedIntegrityYesVerifyIntegrityNoBitFlipDataShouldCatch', async () => {
        let failed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                false, 0, false,
                false, true);
        } catch (ex) {
            console.error(ex);
            failed = true;
        }

        expect(failed).toBeTruthy();
    });

    it('shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch', async () => {
        let failed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                true, 36, false,
                true, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportAppliedIntegrityNoVerifyIntegrity', async () => {
        let failed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                false, 0, true,
                true, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportIntegrityBitFlipHeader', async () => {
        let integrityFailed = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                true, 20, false,
                true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        expect(integrityFailed).toBeTruthy();
    });

    it('shouldImportAndExportIntegrity', async () => {
        let importSuccess = true;
        let integrityCaught = false;
        try {
            await TsFsTestHelper.importAndExport(await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TsFsTestHelper.TEST_IMPORT_FILE,
                false, 0, true,
                true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                integrityCaught = true;
            importSuccess = false;
        }
        expect(importSuccess).toBeTruthy();
        expect(integrityCaught).toBeFalsy();
    });

    it('shouldCatchVaultMaxFiles', async () => {

        let vaultDir = null;
        let seqFile = null;

        vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        seqFile = await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        await TsFsTestHelper.testMaxFiles(vaultDir, seqFile, TsFsTestHelper.TEST_IMPORT_TINY_FILE,
            TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        seqFile = await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        await TsFsTestHelper.testMaxFiles(vaultDir, seqFile, TsFsTestHelper.TEST_IMPORT_TINY_FILE,
            TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        seqFile = await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        await TsFsTestHelper.testMaxFiles(vaultDir, seqFile, TsFsTestHelper.TEST_IMPORT_TINY_FILE,
            TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        seqFile = await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        await TsFsTestHelper.testMaxFiles(vaultDir, seqFile, TsFsTestHelper.TEST_IMPORT_TINY_FILE,
            TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    });

    it('shouldCreateFileWithoutVault', async () => {
        let success = true;
        try {
            await TsFsTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TsFsTestHelper.TEST_OUTPUT_DIR, false, -1, true);
        } catch (ex) {
            console.error(ex);
            success = false;
        }
        expect(success).toBeTruthy();
    });

    it('shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught', async () => {
        let caught = false;
        try {
            await TsFsTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TsFsTestHelper.TEST_OUTPUT_DIR,
                true, 45, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught', async () => {
        let text = TestHelper.TEST_TEXT;
        for (let i = 0; i < text.length; i++) {
            let caught = false;
            let failed = false;
            try {
                await TsFsTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(text), TestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    TestHelper.TEST_HMAC_KEY_BYTES, TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TsFsTestHelper.TEST_OUTPUT_DIR, true, i, false);
            } catch (ex) {
                console.error(ex);
                if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                    caught = true;
                failed = true;
            }
            expect(caught).toBeFalsy();
            expect(failed).toBeFalsy();
        }
    });

    it('shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual', async () => {
        let caught = false;
        let failed = false;
        try {
            await TsFsTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                true, false, 64,
                TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                TsFsTestHelper.TEST_OUTPUT_DIR,
                true, 24 + 32 + 5, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
            else
                failed = true;
        }

        expect(caught).toBeFalsy();
        expect(failed).toBeTruthy();
    });

    it('shouldExportAndImportAuth', async () => {
        let vault = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT_DIR);
        let importFilePath = TsFsTestHelper.TEST_IMPORT_TINY_FILE;
        await TsFsTestHelper.exportAndImportAuth(vault, importFilePath);
    });

    it('testExamples', async () => {
        await TsFsTestHelper.testExamples();
    });

    it('shouldEncryptAndDecryptStream', async () => {
        let data = await TsFsTestHelper.getRealFileContents(TsFsTestHelper.TEST_IMPORT_FILE);
        await TsFsTestHelper.encryptAndDecryptStream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
    });

    it('ShouldEncryptAndReadFileInputStream', async () => {
        let data = new Uint8Array(256);
        for (let i = 0; i < data.length; i++) {
            data[i] = i;
        }
        let file = await TsFsTestHelper.shouldCreateFileWithoutVault(data, TestHelper.TEST_KEY_BYTES,
            true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
            TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TsFsTestHelper.TEST_OUTPUT_DIR,
            false, -1, true);
        let fileInputStream = new SalmonFileReadableStream(file,
            3, 50, TsFsTestHelper.TEST_FILE_INPUT_STREAM_THREADS, 12);

        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        await TsFsTestHelper.seekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
    });

    it('shouldCreateDriveAndOpenFsFolder', async () => {
        let vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let sequenceFile = await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        let serializer = getSequenceSerializer();
        let sequencer = new SalmonFileSequencer(sequenceFile, serializer);
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
        let wrongPassword = false;
        let rootDir = await drive.getVirtualRoot();
        rootDir.listFiles();
        drive.lock();

        // reopen but open the fs folder instead it should still login
        try {
            drive = await SalmonDrive.openDrive(await getFile(vaultDir, "fs"), TsFsTestHelper.driveClassType, sequencer);
            expect(await drive.hasConfig()).toBeTruthy();
            await drive.unlock(TestHelper.TEST_PASSWORD);
        } catch (ignored) {
            wrongPassword = true;
        }

        expect(wrongPassword).toBeFalsy();
    });

    it('shouldCreateWinFileSequencer', async () => {
        await TsFsTestHelper.shouldTestFileSequencer();
    });

    it('shouldPerformOperationsRealFiles', async () => {
        let caught = false;
        let dir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let file = await getFile(TsFsTestHelper.TEST_IMPORT_TINY_FILE);
        let file1 = await file.copy(dir);
        let file2;
        try {
            file2 = await file.copy(dir);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }
        expect(true).toBe(caught);
        file2 = await file.copy(dir, await autoRenameFile(file));

        expect(2).toBe(await dir.getChildrenCount());
        expect(await dir.getChild(file.getBaseName()).then(_ => _.exists())).toBeTruthy();
        expect(await dir.getChild(file.getBaseName()).then(_ => _.isFile())).toBeTruthy();
        expect(await dir.getChild(file2.getBaseName()).then(_ => _.exists())).toBeTruthy();
        expect(await dir.getChild(file2.getBaseName()).then(_ => _.isFile())).toBeTruthy();

        let dir1 = await dir.createDirectory("folder1");
        expect(await dir.getChild("folder1").then(_ => _.exists())).toBeTruthy();
        expect(await dir.getChild("folder1").then(_ => _.isDirectory())).toBeTruthy();
        expect(3).toBe(await dir.getChildrenCount());

        let folder1 = await dir.createDirectory("folder2");
        expect(await folder1.exists()).toBeTruthy();
        let renamed = await folder1.renameTo("folder3");
        expect(renamed).toBeTruthy();
        expect(await dir.getChild("folder2").then(_ => _.exists())).toBeFalsy();
        expect(await dir.getChild("folder3").then(_ => _.exists())).toBeTruthy();
        expect(await dir.getChild("folder3").then(_ => _.isDirectory())).toBeTruthy();
        expect(4).toBe(await dir.getChildrenCount());
        let delres = await dir.getChild("folder3").then(_ => _.delete());
        expect(delres).toBeTruthy();
        expect(await dir.getChild("folder3").then(_ => _.exists())).toBeFalsy();
        expect(3).toBe(await dir.getChildrenCount());

        await file1.move(await dir.getChild("folder1"));
        await file2.move(await dir.getChild("folder1"));

        let file3 = await file.copy(dir);
        caught = false;
        try {
            await file3.move(await dir.getChild("folder1"));
        } catch (ex) {
            console.error(ex);
            caught = true;
        }
        expect(caught).toBeTruthy();
        let file4 = await file3.move(await dir.getChild("folder1"), await autoRenameFile(file3));
        expect(await file4.exists()).toBeTruthy();
        expect(3).toBe(await dir.getChild("folder1").then(_ => _.getChildrenCount()));

        let folder2 = await (await dir.getChild("folder1")).createDirectory("folder2");
        for (let rfile of await dir.getChild("folder1").then(_ => _.listFiles()))
            await rfile.copy(folder2);
        expect(4).toBe(await dir.getChild("folder1").then(_ => _.getChildrenCount()));
        expect(4).toBe(await dir.getChild("folder1").then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));

        // recursive copy
        let folder3 = await dir.createDirectory("folder4");
        await copyRecursively(await dir.getChild("folder1"), folder3);
        let count1 = await TsFsTestHelper.getChildrenCountRecursively(await dir.getChild("folder1"));
        let count2 = await TsFsTestHelper.getChildrenCountRecursively(await dir.getChild("folder4").then(_ => _.getChild("folder1")));
        expect(count1).toBe(count2);

        let dfile = await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChild(file.getBaseName()));
        expect(await dfile.exists()).toBeTruthy();
        expect(await dfile.delete()).toBeTruthy();
        expect(3).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));
        await copyRecursively(await dir.getChild("folder1"), folder3, null, autoRenameFile, false, null);
        expect(2).toBe(await dir.getChildrenCount());
        expect(1).toBe(await dir.getChild("folder4").then(_ => _.getChildrenCount()));
        expect(7).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChildrenCount()));
        expect(6).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));
        expect(0).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));

        await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChild(file.getBaseName())).then(_ => _.delete());
        await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild(file.getBaseName()))
            .then(_ => _.delete());
        let failed = [];
        await copyRecursively(await dir.getChild("folder1"), folder3, null, null, false, (failedFile, ex) => {
            failed.push(failedFile);
        });
        expect(4).toBe(await failed.length);
        expect(2).toBe(await dir.getChildrenCount());
        expect(1).toBe(await dir.getChild("folder4").then(_ => _.getChildrenCount()));
        expect(7).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChildrenCount()));
        expect(6).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChildrenCount()));
        expect(0).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));

        await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChild(file.getBaseName())).then(_ => _.delete());
        await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild(file.getBaseName()))
            .then(_ => _.delete());
        let failedmv = [];
        await moveRecursively(await dir.getChild("folder1"), await dir.getChild("folder4"), null, autoRenameFile, false, (failedFile, ex) => {
            failedmv.push(failedFile);
        });
        expect(4).toBe(await failed.length);
        expect(1).toBe(await dir.getChildrenCount());
        expect(1).toBe(await dir.getChild("folder4").then(_ => _.getChildrenCount()));
        expect(9).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChildrenCount()));
        expect(8).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChildrenCount()));
        expect(0).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));
    });

    it('shouldReadFromFileMultithreaded', async () => {
        let vaultDir = await TsFsTestHelper.generateFolder(TsFsTestHelper.TEST_VAULT2_DIR);
        let file = await getFile(TsFsTestHelper.TEST_IMPORT_MEDIUM_FILE);

        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
        let fileCommander = new SalmonFileCommander(SalmonDefaultOptions.getBufferSize(), SalmonDefaultOptions.getBufferSize(), 2)
        let sfiles = await fileCommander.importFiles([file],
            await drive.getVirtualRoot(), false, true, null, null, null);

        let fileInputStream1 = new SalmonFileReadableStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        let ms = new MemoryStream();
        TsFsTestHelper.copyReadableStream(fileInputStream1, ms);
        await fileInputStream1.cancel();
        await ms.flush();
        await ms.close();
        let h1 = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));

        let fileInputStream2 = new SalmonFileReadableStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        let ms52 = new MemoryStream();
        TsFsTestHelper.copyReadableStream(fileInputStream2, ms52);
        await fileInputStream2.cancel();
        await ms52.flush();
        await ms52.close();
        let h2 = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms52.toArray())));
        expect(h1).toBe(h2);
        fileCommander.close();
    });
});