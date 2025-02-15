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

import { MemoryStream } from '../../lib/salmon-core/streams/memory_stream.js';
import { BitConverter } from '../../lib/salmon-core/convert/bit_converter.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';
import { getTestRunnerMode, SalmonFSTestHelper } from './salmon_fs_test_helper.js';
import { IntegrityException } from '../../lib/salmon-core/integrity/integrity_exception.js';
import { copyRecursively, moveRecursively, autoRenameFile } from '../../lib/salmon-fs/file/ireal_file.js'
import { SalmonFileReadableStream } from '../../lib/salmon-fs/salmon/streams/salmon_file_readable_stream.js';
import { SalmonFileCommander } from '../../lib/salmon-fs/salmon/utils/salmon_file_commander.js';
import { SalmonAuthException } from '../../lib/salmon-fs/salmon/salmon_auth_exception.js'
import { getTestMode, TestMode } from "./salmon_fs_test_helper.js";

function checkParams() {
    if(getTestMode() == TestMode.Http) {
        throw Error("TestMode Http not supported, please specify 'Node', 'Local', or 'WebService'");
    }
    if(getTestMode() != TestMode.Node && getTestMode() != TestMode.Local && getTestMode() != TestMode.WebService) {
        throw Error("TestMode not found, please specify 'Node', 'Local', or 'WebService'");
    }
    if(!getTestRunnerMode()) {
        throw Error("TestRunnerMode not found, please specify 'Browser' or 'NodeJS'");
    }
}

describe('salmon-fs', () => {
	beforeAll(() => {
        checkParams();
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;
        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
		// SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
		SalmonCoreTestHelper.TEST_ENC_THREADS = 2;
		SalmonCoreTestHelper.TEST_DEC_THREADS = 2;

        // SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        // SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 1;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 1;
        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 1;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();
    });

    afterAll(() => {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
    });

    it('shouldCatchNotAuthorizeNegative', async () => {
        let vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let wrongPassword = false;
        drive.close();
        try {
            let drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, 
                SalmonCoreTestHelper.TEST_FALSE_PASSWORD, sequencer);
            let rootDir = await drive.getRoot();
            await rootDir.listFiles();
        } catch (ex) {
            console.error(ex);
            if (ex instanceof SalmonAuthException)
                wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldAuthorizePositive', async () => {
        let vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let wrongPassword = false;
        drive.close();
        try {
            drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            let virtualRoot = await drive.getRoot();
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }
        expect(wrongPassword).toBeFalsy();
    });

    it('shouldImportAndExportNoIntegrityBitFlipDataNoCatch', async () => {
        let integrityFailed = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), 
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, false, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        }
        expect(integrityFailed).toBeFalsy();
    });

    it('shouldImportAndExportNoIntegrity', async () => {
        let integrityFailed = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                false, 0, true, false, false);
        } catch (ex) {
            console.error(ex);
            integrityFailed = true;
        }

        expect(integrityFailed).toBeFalsy();
    });

    it('shouldImportAndSearchFiles', async () => {
        await SalmonFSTestHelper.importAndSearch(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE);
    });

    it('shouldImportAndCopyFile', async () => {
        let failed = false;
        try {
            await SalmonFSTestHelper.importAndCopy(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
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
            await SalmonFSTestHelper.importAndCopy(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
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
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        }

        expect(integrityFailed).toBeTruthy();
    });

    it('shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch', async () => {
        let integrityFailed = false;
        let failed = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, false, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
            failed = true;
        }
        expect(integrityFailed).toBeFalsy();
        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch', async () => {
        let failed = false;
        try {
            // use a small file
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                true, 36, false,
                true, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportAppliedIntegrityNoVerifyIntegrity', async () => {
        let failed = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                false, 0, true,
                true, false);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                failed = true;
        }

        expect(failed).toBeFalsy();
    });

    it('shouldImportAndExportIntegrityBitFlipHeader', async () => {
        let integrityFailed = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 20, false,
                true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        }

        expect(integrityFailed).toBeTruthy();
    });

    it('shouldImportAndExportIntegrity', async () => {
        let importSuccess = true;
        let integrityCaught = false;
        try {
            await SalmonFSTestHelper.importAndExport(await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                false, 0, true,
                true, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                integrityCaught = true;
            importSuccess = false;
        }
        expect(importSuccess).toBeTruthy();
        expect(integrityCaught).toBeFalsy();
    });

    it('shouldCatchVaultMaxFiles', async () => {
        let vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let seqDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        let seqFile = await seqDir.getChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
		
        await SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
            SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        await SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
            SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        await SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
            SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        await SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
            SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    });

    it('shouldCreateFileWithoutVault', async () => {
        let success = true;
        try {
            await SalmonFSTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false, -1, true);
        } catch (ex) {
            console.error(ex);
            success = false;
        }
        expect(success).toBeTruthy();
    });

    it('shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught', async () => {
        let caught = false;
        try {
            await SalmonFSTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true, 45, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught', async () => {
        let text = SalmonCoreTestHelper.TEST_TEXT;
        for (let i = 0; i < 5; i++) {
            let caught = false;
            let failed = false;
            try {
                await SalmonFSTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(text), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true, i, false);
            } catch (ex) {
                console.error(ex);
                if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
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
            await SalmonFSTestHelper.shouldCreateFileWithoutVault(new TextEncoder().encode(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, false, 64,
                SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 24 + 32 + 5, true);
        } catch (ex) {
            console.error(ex);
            if (ex.getCause != undefined && ex.getCause() instanceof IntegrityException)
                caught = true;
            else
                failed = true;
        }

        expect(caught).toBeFalsy();
        expect(failed).toBeTruthy();
    });

    it('shouldExportAndImportAuth', async () => {
        let vault = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let importFilePath = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
        await SalmonFSTestHelper.exportAndImportAuth(vault, importFilePath);
    });

    it('shouldEncryptAndDecryptStream', async () => {
        let data = await SalmonFSTestHelper.getRealFileContents(SalmonFSTestHelper.TEST_IMPORT_FILE);
        await SalmonFSTestHelper.encryptAndDecryptStream(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
    });

    it('ShouldEncryptAndReadFileInputStream', async () => {
        let data = new Uint8Array(256);
        for (let i = 0; i < data.length; i++) {
            data[i] = i;
        }
        let file = await SalmonFSTestHelper.shouldCreateFileWithoutVault(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
            true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            false, -1, true);
        let fileInputStream = SalmonFileReadableStream.create(file,
            3, 50, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, 12);

        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        await SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
        await fileInputStream.cancel();
    });

    it('shouldCreateDriveAndOpenFsFolder', async () => {
        let vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let wrongPassword = false;
        let rootDir = await drive.getRoot();
        rootDir.listFiles();
        drive.close();

        // reopen but open the fs folder instead it should still login
        try {
            drive = await SalmonFSTestHelper.openDrive(await vaultDir.getChild("fs"), SalmonFSTestHelper.driveClassType, 
                SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            expect(await drive.hasConfig()).toBeTruthy();
        } catch (ignored) {
            wrongPassword = true;
        }

        expect(wrongPassword).toBeFalsy();
    });

    it('shouldCreateFileSequencer', async () => {
        await SalmonFSTestHelper.shouldTestFileSequencer();
    });

    it('shouldPerformOperationsRealFiles', async () => {
        let caught = false;
        let dir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_OPER_DIRNAME);
        let file = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
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
            await copyRecursively(rfile, folder2);
        expect(4).toBe(await dir.getChild("folder1").then(_ => _.getChildrenCount()));
        expect(3).toBe(await dir.getChild("folder1").then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));

        // recursive copy
        let folder3 = await dir.createDirectory("folder4");
        await copyRecursively(await dir.getChild("folder1"), folder3);
        let count1 = await SalmonFSTestHelper.getChildrenCountRecursively(await dir.getChild("folder1"));
        let count2 = await SalmonFSTestHelper.getChildrenCountRecursively(await dir.getChild("folder4").then(_ => _.getChild("folder1")));
        expect(count1).toBe(count2);

        let dfile = await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChild(file.getBaseName()));
        expect(await dfile.exists()).toBeTruthy();
        expect(await dfile.delete()).toBeTruthy();
        expect(2).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));
        await copyRecursively(await dir.getChild("folder1"), folder3, null, autoRenameFile, false, null);
        expect(2).toBe(await dir.getChildrenCount());
        expect(1).toBe(await dir.getChild("folder4").then(_ => _.getChildrenCount()));
        expect(7).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChildrenCount()));
        expect(5).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1"))
            .then(_ => _.getChild("folder2")).then(_ => _.getChildrenCount()));

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
        expect(5).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChildrenCount()));

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
        expect(7).toBe(await dir.getChild("folder4").then(_ => _.getChild("folder1")).then(_ => _.getChild("folder2"))
            .then(_ => _.getChildrenCount()));
    });

    it('shouldReadFromFileMultithreaded', async () => {
        if(getTestMode() == TestMode.WebService) {
            console.log("Skipping test, multithreading for web service files is not supported");
            return;
        }

        let vaultDir = await SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        let file = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE;

        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let fileCommander = new SalmonFileCommander(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, 2);
        let sfiles = await fileCommander.importFiles([file],
            await drive.getRoot(), false, true, null, null, null);

        let fileInputStream1 = SalmonFileReadableStream.create(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        let ms = new MemoryStream();
        await SalmonFSTestHelper.copyReadableStream(fileInputStream1, ms);
        await ms.flush();
        await ms.close();
        let h1 = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));

        let fileInputStream2 = SalmonFileReadableStream.create(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        let ms52 = new MemoryStream();
        await SalmonFSTestHelper.copyReadableStream(fileInputStream2, ms52);
        await ms52.flush();
        await ms52.close();
        let h2 = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms52.toArray())));
        expect(h1).toBe(h2);
        fileCommander.close();
    });

    it('testRawFile', async () => {
        await SalmonFSTestHelper.testRawFile();
    });

    it('testEncDecFile', async () => {
        await SalmonFSTestHelper.testEncDecFile();
    });
});