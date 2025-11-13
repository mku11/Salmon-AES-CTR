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

import { MemoryStream } from '../../lib/simple-io/streams/memory_stream.js';
import { HttpDrive } from '../../lib/salmon-fs/salmonfs/drive/http_drive.js';
import { HttpFile } from '../../lib/salmon-fs/../simple-fs/fs/file/http_file.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { ProviderType } from '../../lib/salmon-core/salmon/streams/provider_type.js';
import { AesDrive } from '../../lib/salmon-fs/salmonfs/drive/aes_drive.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';
import { getTestMode, SalmonFSTestHelper, TestMode } from './salmon_fs_test_helper.js';

describe('salmon-httpfs', () => {
    let oldTestMode = null;
    beforeAll(async () => {
        // make sure you run an HTTP server
		oldTestMode = SalmonFSTestHelper.currTestMode;
        let testDir = PARAMS["TEST_DIR"];
        // use TestMode: Http only
        let testMode = TestMode.Http;
        let threads = PARAMS["ENC_THREADS"] != undefined && PARAMS["ENC_THREADS"] !== "" ?
                parseInt(PARAMS["ENC_THREADS"]) : 1;

        await SalmonFSTestHelper.setTestParams(testDir, testMode);

        console.log("testDir: " + testDir);
        console.log("testMode: " + testMode.name);
        console.log("threads: " + threads);
        console.log("http server url: " + SalmonFSTestHelper.HTTP_SERVER_URL);
        console.log("HTTP_VAULT_DIR_URL: " + SalmonFSTestHelper.HTTP_VAULT_DIR_URL);

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();

        let providerType = ProviderType.Default;
        let aesProviderType = PARAMS["AES_PROVIDER_TYPE"];
        if (aesProviderType != undefined && aesProviderType !== "")
            providerType = ProviderType[aesProviderType];
        console.log("ProviderType: " + ProviderType[providerType]);

        AesStream.setAesProviderType(providerType);
    });

    afterAll(async () => {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
		
		if (oldTestMode)
			await SalmonFSTestHelper.setTestParams(await SalmonFSTestHelper.TEST_ROOT_DIR.getPath(), oldTestMode);
    });

    it('shouldCatchNotAuthorizeNegative', async () => {
        let vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        let wrongPassword = false;
        try {
            let drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD);
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldAuthorizePositive', async () => {
        let wrongPassword = false;
        let vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        try {
            let drive = await AesDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
            let root = drive.getRoot();
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }

        expect(wrongPassword).toBeFalsy();
    });

    it('shouldReadFromFileTiny', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
    });

    it('shouldReadFromFileSmall', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
    });

    it('shouldReadFromFileMedium', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
    });

    it('shouldReadFromFileLarge', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
    });

    it('shouldSeekAndReadEncryptedFileStreamFromDrive', async () => {
        let vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        let drive = await AesDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        let root = await drive.getRoot();
        let encFile = await root.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);

        let encStream = await encFile.getInputStream();
        let ms = new MemoryStream();
        await encStream.copyTo(ms);
        let data = ms.toArray();
        await ms.close();
        await encStream.close();
        await SalmonFSTestHelper.seekAndReadHttpFile(data, encFile, 3, 50, 12);
    });

    it('shouldListFilesFromDrive', async () => {
        let vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
		console.log("vaultDir: " + vaultDir.getPath());
        let drive = await HttpDrive.open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD);
        let root = await drive.getRoot();
        let files = await root.listFiles();
        let filenames = [];
        for (let i = 0; i < files.length; i++) {
            let filename = await files[i].getName();
            filenames.push(filename);
        }
        expect(files.length).toBe(4);
        expect(filenames.includes(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME)).toBeTruthy();
        expect(filenames.includes(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME)).toBeTruthy();
        expect(filenames.includes(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME)).toBeTruthy();
        expect(filenames.includes(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME)).toBeTruthy();
    });

    it('shouldExportFileFromDrive', async () => {
        let vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        let drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        let file = await (await drive.getRoot()).getChild(await SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        let exportDir = await SalmonFSTestHelper.generateFolder("export_http", SalmonFSTestHelper.TEST_EXPORT_DIR, false);
        let localFile = await exportDir.getChild(await SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        if(await localFile.exists())
            await localFile.delete();
        await SalmonFSTestHelper.exportFiles([file], exportDir);
        drive.close();
    });

    it('shouldReadRawFile', async () => {
        let localFile = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(await SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        console.log("reading: " + localFile.getDisplayPath());
        let localChkSum = await SalmonFSTestHelper.getChecksum(localFile);
        let httpRoot = new HttpFile(SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME, 
            SalmonFSTestHelper.httpCredentials);
        let httpFile = await httpRoot.getChild(await SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        let stream = await httpFile.getInputStream();
        let digest = await SalmonCoreTestHelper.getChecksumStream(stream);
        await stream.close();
        console.log(digest + " vs " + localChkSum);
        expect(digest).toBe(localChkSum);
    });
});