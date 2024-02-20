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

import { BitConverter } from '../../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../../lib/salmon-core/salmon/salmon_generator.js';
import { MemoryStream } from '../../lib/salmon-core/io/memory_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/io/encryption_mode.js';


import { SalmonIntegrityException } from '../../lib/salmon-core/salmon/integrity/salmon_integrity_exception.js';
import { SalmonEncryptor } from '../../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../../lib/salmon-core/salmon/salmon_decryptor.js';

import { SalmonTextEncryptor } from '../../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../../lib/salmon-core/salmon/text/salmon_text_decryptor.js';

import { SalmonIntegrity } from '../../lib/salmon-core/salmon/integrity/salmon_integrity.js';
import { SalmonStream } from '../../lib/salmon-core/salmon/io/salmon_stream.js';
import { ProviderType } from '../../lib/salmon-core/salmon/io/provider_type.js';
import { SalmonDefaultOptions } from '../../lib/salmon-core/salmon/salmon_default_options.js';

import { SalmonSecurityException } from '../../lib/salmon-core/salmon/salmon_security_exception.js';
import { SalmonRangeExceededException } from '../../lib/salmon-core/salmon/salmon_range_exceeded_exception.js'

import { TestHelper } from '../salmon-core/test_helper.js';
import { TsFsTestHelper } from './ts_fs_test_helper.js';

import { SalmonDriveManager } from '../../lib/salmon-fs/salmonfs/salmon_drive_manager.js';
import { JsHttpDrive } from '../../lib/salmon-fs/file/js_http_drive.js';
import { JsHttpFile } from '../../lib/salmon-fs/file/js_http_file.js';
import { JsHttpFileStream } from '../../lib/salmon-fs/file/js_http_file_stream.js';
import { SalmonFile } from '../../lib/salmon-fs/salmonfs/salmon_file.js';

SalmonDriveManager.setVirtualDriveClass(JsHttpDrive);

describe('salmon-fs-readonly', () => {

    beforeEach(() => {
        
    });

    it('shouldCatchNotAuthenticatedNegative', async () => {
        await SalmonDriveManager.openDrive(TsFsTestHelper.VAULT_DIR_URL);
        let wrongPassword = false;
        try {
            await SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_FALSE_PASSWORD);
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }
        expect(wrongPassword).toBeTruthy();
    });

    it('shouldAuthenticatePositive', async () => {
        let wrongPassword = false;
        try {
            await SalmonDriveManager.openDrive(TsFsTestHelper.VAULT_DIR_URL);
            await SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
            let virtualRoot = SalmonDriveManager.getDrive().getVirtualRoot();
        } catch (ex) {
            console.error(ex);
            wrongPassword = true;
        }

        expect(wrongPassword).toBeFalsy();
    });

    it('testExamples', async () => {
        TsFsTestHelper.testExamples();
    });
        
    it('shouldReadFromRealFileTiny', async () => {
        await TsFsTestHelper.shouldReadFile(TsFsTestHelper.SERVER_URL + "/" + TsFsTestHelper.TEST_HTTP_TINY_FILE,
            TsFsTestHelper.TEST_HTTP_TINY_FILE_SIZE, TsFsTestHelper.TEST_HTTP_TINY_FILE_CONTENTS, TsFsTestHelper.TEST_HTTP_TINY_FILE_CHKSUM);     
    });

    it('shouldReadFromRealFileSmall', async () => {
        await TsFsTestHelper.shouldReadFile(TsFsTestHelper.SERVER_URL + "/" + TsFsTestHelper.TEST_HTTP_SMALL_FILE,
            TsFsTestHelper.TEST_HTTP_SMALL_FILE_SIZE, TsFsTestHelper.TEST_HTTP_SMALL_FILE_CONTENTS, TsFsTestHelper.TEST_HTTP_SMALL_FILE_CHKSUM);
    });
    
    it('shouldReadFromRealFileLarge', async () => {
        await TsFsTestHelper.shouldReadFile(TsFsTestHelper.SERVER_URL + "/" + TsFsTestHelper.TEST_HTTP_LARGE_FILE,
            TsFsTestHelper.TEST_HTTP_LARGE_FILE_SIZE, null, TsFsTestHelper.TEST_HTTP_LARGE_FILE_CHKSUM);
    });

    it('shouldSeekAndReadRealFileStream', async () => {
        let urlPath = TsFsTestHelper.SERVER_TEST_DATA_URL + "/" + TsFsTestHelper.TEST_HTTP_DATA256_FILE;
        let file = new JsHttpFile(urlPath);
        let stream = new JsHttpFileStream(file);
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        let data = ms.toArray();
        await ms.close();
        await stream.close();

        file = new JsHttpFile(urlPath);
        await TsFsTestHelper.seekAndReadFile(data, file, false, false, 3, 50, 2, 12);
    });

    it('shouldSeekAndReadEncryptedFileStreamWithoutDrive', async () => {
        let urlPath = TsFsTestHelper.SERVER_TEST_DATA_URL + "/" + TsFsTestHelper.TEST_HTTP_ENCDATA256_FILE;
        let file = new JsHttpFile(urlPath);
        let encFile = new SalmonFile(file);
        let size = await encFile.getSize();
        expect(size).toBe(256);
        encFile.setEncryptionKey(TestHelper.TEST_KEY_BYTES);
        encFile.setVerifyIntegrity(true, TestHelper.TEST_HMAC_KEY_BYTES);
        let encStream = await encFile.getInputStream(); 
        let length = await encStream.length();
        expect(length).toBe(256);
        let ms = new MemoryStream();
        await encStream.copyTo(ms);
        let data = ms.toArray();
        expect(data.length).toBe(256);
        await ms.close();
        await encStream.close();
        file = new JsHttpFile(urlPath);

        await TsFsTestHelper.seekAndReadFile(data, encFile, false, true, 3, 50, 2, 12);
    });

    
    it('shouldSeekAndReadEncryptedFileStreamFromDrive', async () => {
        await SalmonDriveManager.openDrive(TsFsTestHelper.VAULT_DIR_URL);
        await SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        let virtualRoot = await SalmonDriveManager.getDrive().getVirtualRoot();
        let encFile = await virtualRoot.getChild("data256.dat");
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

        await TsFsTestHelper.seekAndReadFile(data, encFile, false, true, 3, 50, 2, 12);
    });

    it('shouldListFilesFromDrive', async () => {
        await SalmonDriveManager.openDrive(TsFsTestHelper.VAULT_DIR_URL);
        await SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        let virtualRoot = await SalmonDriveManager.getDrive().getVirtualRoot();
        let files = await virtualRoot.listFiles();
        let filenames = [];
        for(let i=0; i<files.length; i++) {
            let filename = await files[i].getBaseName();
            filenames.push(filename);
        }
        expect(files.length).toBe(2);
        expect(filenames.includes("data256.dat")).toBeTruthy();
        expect(filenames.includes("tiny_test.txt")).toBeTruthy();
    });
    
});