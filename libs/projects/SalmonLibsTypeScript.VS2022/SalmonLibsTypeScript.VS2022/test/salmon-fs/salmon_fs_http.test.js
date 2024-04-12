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
import { JsHttpDrive } from '../../lib/salmon-fs/salmon/drive/js_http_drive.js';
import { SalmonDrive } from '../../lib/salmon-fs/salmon/salmon_drive.js';
import { SalmonFile } from '../../lib/salmon-fs/salmon/salmon_file.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';
import { getFile, getFileStream, SalmonFSTestHelper } from './salmon_fs_test_helper.js';
import { getTestMode, setTestMode, TestMode } from "./salmon_fs_test_helper.js";

describe('salmon-fs-http', () => {
    let oldTestMode = null;
    beforeAll(async () => {
		oldTestMode = await getTestMode();
		await setTestMode(TestMode.Http);
		
		//SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
		//SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
		
        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();
    });

    afterAll(async () => {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
		await setTestMode(oldTestMode);
    });

    beforeEach(() => {

    });

    it('shouldCatchNotAuthorizeNegative', async () => {
        let vaultDir = await getFile(SalmonFSTestHelper.VAULT_DIR_URL);
        let wrongPassword = false;
        try {
            let drive = await SalmonDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD);
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldAuthorizePositive', async () => {
        let wrongPassword = false;
        let vaultDir = await getFile(SalmonFSTestHelper.VAULT_DIR_URL);
        try {
            let drive = await SalmonDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
            let root = drive.getRoot();
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }

        expect(wrongPassword).toBeFalsy();
    });

    it('testHttpReadonlyExamples', async () => {
        await SalmonFSTestHelper.testHttpReadonlyExamples();
    });

    it('shouldReadFromRealFileTiny', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.SERVER_URL + "/" + SalmonFSTestHelper.TEST_HTTP_TINY_FILE,
            SalmonFSTestHelper.TEST_HTTP_TINY_FILE_SIZE, SalmonFSTestHelper.TEST_HTTP_TINY_FILE_CONTENTS, SalmonFSTestHelper.TEST_HTTP_TINY_FILE_CHKSUM);
    });

    it('shouldReadFromRealFileSmall', async () => {
        await SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.SERVER_URL + "/" + SalmonFSTestHelper.TEST_HTTP_SMALL_FILE,
            SalmonFSTestHelper.TEST_HTTP_SMALL_FILE_SIZE, null, SalmonFSTestHelper.TEST_HTTP_SMALL_FILE_CHKSUM);
    });

    it('shouldSeekAndReadRealFileStream', async () => {
        let urlPath = SalmonFSTestHelper.SERVER_TEST_DATA_URL + "/" + SalmonFSTestHelper.TEST_HTTP_DATA256_FILE;
        let file = await getFile(urlPath);
        let stream = getFileStream(file);
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        let data = ms.toArray();
        await ms.close();
        await stream.close();

        file = await getFile(urlPath);
        await SalmonFSTestHelper.seekAndReadFile(data, file, false, false, 3, 50, 12);
    });

    it('shouldSeekAndReadEncryptedFileStreamWithoutDrive', async () => {
        let urlPath = SalmonFSTestHelper.SERVER_TEST_DATA_URL + "/" + SalmonFSTestHelper.TEST_HTTP_ENCDATA256_FILE;
        let file = await getFile(urlPath);
        let encFile = new SalmonFile(file);
        let size = await encFile.getSize();
        expect(size).toBe(256);
        encFile.setEncryptionKey(SalmonCoreTestHelper.TEST_KEY_BYTES);
        await encFile.setVerifyIntegrity(true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        let encStream = await encFile.getInputStream();
        let length = await encStream.length();
        expect(length).toBe(256);
        let ms = new MemoryStream();
        await encStream.copyTo(ms);
        let data = ms.toArray();
        expect(data.length).toBe(256);
        await ms.close();
        await encStream.close();
        file = await getFile(urlPath);

        await SalmonFSTestHelper.seekAndReadFile(data, encFile, true, 3, 50, 12);
    });


    it('shouldSeekAndReadEncryptedFileStreamFromDrive', async () => {
        let vaultDir = await getFile(SalmonFSTestHelper.VAULT_DIR_URL);
        let drive = await SalmonDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        let root = await drive.getRoot();
        let encFile = await root.getChild("data256.dat");
        expect(await encFile.getBaseName()).toBe("data256.dat");
        let size = await encFile.getSize();
        expect(size).toBe(256);
        let encStream = await encFile.getInputStream();
        let length = await encStream.length();
        expect(length).toBe(256);
        let ms = new MemoryStream();
        await encStream.copyTo(ms);
        let data = ms.toArray();
        expect(data.length).toBe(256);
        await ms.close();
        await encStream.close();

        await SalmonFSTestHelper.seekAndReadFile(data, encFile, true, 3, 50, 12);
    });

    it('shouldListFilesFromDrive', async () => {
        let vaultDir = await getFile(SalmonFSTestHelper.VAULT_DIR_URL);
        let drive = await JsHttpDrive.open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD);
        let root = await drive.getRoot();
        let files = await root.listFiles();
        let filenames = [];
        for (let i = 0; i < files.length; i++) {
            let filename = await files[i].getBaseName();
            filenames.push(filename);
        }
        expect(files.length).toBe(14);
        expect(filenames.includes("data256.dat")).toBeTruthy();
        expect(filenames.includes("tiny_test.txt")).toBeTruthy();
        expect(filenames.includes("New Folder")).toBeTruthy();
    });

});