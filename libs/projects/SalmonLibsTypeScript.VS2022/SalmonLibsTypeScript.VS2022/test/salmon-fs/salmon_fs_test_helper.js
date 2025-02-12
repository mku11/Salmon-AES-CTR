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
import { MemoryStream } from '../../lib/salmon-core/streams/memory_stream.js';
import { SalmonGenerator } from '../../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../../lib/salmon-core/salmon/salmon_decryptor.js';
import { SalmonStream } from '../../lib/salmon-core/salmon/streams/salmon_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/streams/encryption_mode.js';
import { SalmonRangeExceededException } from '../../lib/salmon-core/salmon/salmon_range_exceeded_exception.js';
import { ReadableStreamWrapper } from '../../lib/salmon-core/streams/readable_stream_wrapper.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';
import { SalmonTextEncryptor } from '../../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../../lib/salmon-core/salmon/text/salmon_text_decryptor.js';
import { SalmonFile } from '../../lib/salmon-fs/salmon/salmon_file.js';
import { SalmonDrive } from '../../lib/salmon-fs/salmon/salmon_drive.js';
import { SalmonSequenceSerializer } from '../../lib/salmon-fs/salmon/sequence/salmon_sequence_serializer.js';
import { SalmonFileSequencer } from '../../lib/salmon-fs/salmon/sequence/salmon_file_sequencer.js';
import { SalmonFileImporter } from '../../lib/salmon-fs/salmon/utils/salmon_file_importer.js';
import { SalmonFileExporter } from '../../lib/salmon-fs/salmon/utils/salmon_file_exporter.js';
import { FileSearcher } from '../../lib/salmon-fs/utils/file_searcher.js';
import { SalmonFileReadableStream } from '../../lib/salmon-fs/salmon/streams/salmon_file_readable_stream.js';
import { SalmonAuthConfig } from '../../lib/salmon-fs/salmon/salmon_auth_config.js';
import { JsHttpDrive } from '../../lib/salmon-fs/salmon/drive/js_http_drive.js';
import { Credentials, JsWSFile } from '../../lib/salmon-fs/file/js_ws_file.js';
import { JsWSDrive } from '../../lib/salmon-fs/salmon/drive/js_ws_drive.js';
import { JsHttpFile } from '../../lib/salmon-fs/file/js_http_file.js';
import { JsHttpFileStream } from '../../lib/salmon-fs/streams/js_http_file_stream.js';

export const TestMode = {
    Local: { name: 'Local', ordinal: 0 },
    Node: { name: 'Node', ordinal: 1 },
    Http: { name: 'Http', ordinal: 2 },
    WebService: { name: 'WebService', ordinal: 3 },
}
export const TestRunnerMode = {
    Browser: { name: 'Browser', ordinal: 0 },
    NodeJS: { name: 'NodeJS', ordinal: 1 }
}

let currTestMode = null;
let currTestRunnerMode = null;
export function getTestMode() {
	return currTestMode;
}
export function getTestRunnerMode() {
	return currTestRunnerMode;
}

export class SalmonFSTestHelper {
    // dirs
    static driveClassType = null; // drive class type
    static TEST_ROOT_DIR; // root dir for testing
    static TEST_INPUT_DIRNAME = "input";
    static TEST_OUTPUT_DIRNAME = "output";
    static TEST_VAULT_DIRNAME = "vault";
    static TEST_OPER_DIRNAME = "files";
    static TEST_EXPORT_AUTH_DIRNAME = "auth";
    static TEST_IMPORT_TINY_FILENAME = "tiny_test.txt";
    static TEST_IMPORT_SMALL_FILENAME = "small_test.dat";
    static TEST_IMPORT_MEDIUM_FILENAME = "medium_test.dat";
    static TEST_IMPORT_LARGE_FILENAME = "large_test.dat";
    static TEST_IMPORT_HUGE_FILENAME = "huge_test.dat";
    static TINY_FILE_CONTENTS = "This is a new file created.";
    static TEST_SEQ_DIRNAME = "seq";
    static TEST_SEQ_FILENAME = "fileseq.json";
    static TEST_EXPORT_AUTH_FILENAME = "export.slma";

    // Web service
    static WS_SERVER_URL = "http://localhost:8080";
	// static WS_SERVER_URL = "https://localhost:8443"; // for testing from the Web browser
    static WS_TEST_DIRNAME = "ws";
    static credentials = new Credentials("user", "password");
    
    // HTTP server (Read-only)
    static HTTP_SERVER_URL = "http://localhost";
    static HTTP_SERVER_VIRTUAL_URL = SalmonFSTestHelper.HTTP_SERVER_URL + "/saltest";
    static HTTP_TEST_DIRNAME = "httpserv";
    static HTTP_VAULT_DIRNAME = "vault";
    static HTTP_VAULT_DIR_URL = SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL 
        + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME + "/" + SalmonFSTestHelper.HTTP_VAULT_DIRNAME; 
    static HTTP_VAULT_FILES_DIR_URL = SalmonFSTestHelper.HTTP_VAULT_DIR_URL + "/fs"; 

    // performance
    static ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    static ENC_IMPORT_THREADS = 1;
    static ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    static ENC_EXPORT_THREADS = 1;
    static TEST_FILE_INPUT_STREAM_THREADS = 1;
    static TEST_USE_FILE_INPUT_STREAM = false;

    // progress
    static ENABLE_FILE_PROGRESS = false;

    // test dirs and files
    static TEST_INPUT_DIR;
    static TEST_OUTPUT_DIR;
    static TEST_IMPORT_TINY_FILE;
    static TEST_IMPORT_SMALL_FILE;
    static TEST_IMPORT_MEDIUM_FILE;
    static TEST_IMPORT_LARGE_FILE;
    static TEST_IMPORT_HUGE_FILE;
    static TEST_IMPORT_FILE;
    static WS_TEST_DIR;
    static HTTP_TEST_DIR;
    static HTTP_VAULT_DIR;
    static TEST_SEQ_DIR;
    static TEST_EXPORT_AUTH_DIR;
    static fileImporter;
    static fileExporter;
    static sequenceSerializer = new SalmonSequenceSerializer();
    
    // testDir can be a path or a fileHandle
	static async setTestParams(testDir,testMode,testRunnerMode) {
        currTestMode = testMode;
        currTestRunnerMode = testRunnerMode;

        if (testMode == TestMode.Local) {
            const { JsDrive } = await import('../../lib/salmon-fs/salmon/drive/js_drive.js');
            SalmonFSTestHelper.driveClassType = JsDrive;
        } else if (testMode == TestMode.Node) {
            const { JsNodeDrive } = await import('../../lib/salmon-fs/salmon/drive/js_node_drive.js');
            SalmonFSTestHelper.driveClassType = JsNodeDrive;
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.driveClassType = JsHttpDrive;
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.driveClassType = JsWSDrive;
        }

        if(currTestRunnerMode == TestRunnerMode.Browser) {
            const { JsFile } = await import('../../lib/salmon-fs/file/js_file.js');
            SalmonFSTestHelper.TEST_ROOT_DIR = new JsFile(testDir);
        } else if(currTestRunnerMode == TestRunnerMode.NodeJS) {
            const { JsNodeFile } = await import('../../lib/salmon-fs/file/js_node_file.js');
            SalmonFSTestHelper.TEST_ROOT_DIR = new JsNodeFile(testDir);
            if(!await SalmonFSTestHelper.TEST_ROOT_DIR.exists())
                await SalmonFSTestHelper.TEST_ROOT_DIR.mkdir();
        }

        console.log("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.getAbsolutePath());
        SalmonFSTestHelper.TEST_INPUT_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if(testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new JsWSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new JsHttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
		await SalmonFSTestHelper.createTestFiles();
        await SalmonFSTestHelper.createHttpVault();
	}
	
	static async createDir(parent, dirName) {
        let dir = await parent.getChild(dirName);
        if(!(await dir.exists()))
            await dir.mkdir();
        return dir;
	}
	
	static async createTestFiles() {
        SalmonFSTestHelper.TEST_IMPORT_TINY_FILE = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;

		await SalmonFSTestHelper.createFile(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,1024*1024);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,12*1024*1024);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE,48*1024*1024);
		// this.createFileRandomData(TEST_IMPORT_HUGE_FILE,512*1024*1024);
	}

    static async createHttpVault() {
        let httpVaultDir = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.HTTP_VAULT_DIRNAME);
        if(httpVaultDir && await httpVaultDir.exists())
            return;

        httpVaultDir.mkdir();
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(httpVaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let rootDir = await drive.getRoot();
        let importFiles = [SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
            SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,
            SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE
        ];
        let importer = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        for(let importFile of importFiles) {
            await importer.importFile(importFile, rootDir, null, false, true);
        }
        importer.close();
    }
	
	static async createFile(file, contents) {
		let stream = await file.getOutputStream();
		let data = new TextEncoder().encode(contents);
		await stream.write(data, 0, data.length);
		await stream.flush();
		await stream.close();
	}
	
	static async createFileRandomData(file, size) {
		if(await file.exists())
			return;
		let data = new Uint8Array(65536);
		let stream = await file.getOutputStream();
		let len = 0;
		while(size > 0) {
			crypto.getRandomValues(data);
			len = Math.min(size, data.length);
			await stream.write(data, 0, len);
			size -= len;
		}
		await stream.flush();
		await stream.close();
	}

    static initialize() {
        SalmonFSTestHelper.fileImporter = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new SalmonFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    static close() {
        SalmonFSTestHelper.fileImporter.close();
        SalmonFSTestHelper.fileExporter.close();
    }

    static async createSalmonFileSequencer() {
        // always create the sequencer files locally
        let seqDir = await this.generateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        let seqFile = await seqDir.getChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
        return new SalmonFileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
    }

    static async generateFolder(name, parent=SalmonFSTestHelper.TEST_OUTPUT_DIR, rand = true) {
        let dirName = name + (rand ? "_" + Date.now() : "");
        let dir = await parent.getChild(dirName);
        if(!await dir.exists())
            await dir.mkdir();
        console.log("generated folder: " + dir.getAbsolutePath())
        return dir;
    }

    static async getChecksum(realFile) {
        let is = null;
        let ms = new MemoryStream();
        try {
            is = await realFile.getInputStream();
            await is.copyTo(ms);
            let digest = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));
            return digest;
        } finally {
            if (ms != null)
                await ms.close();
            if (is != null)
                await is.close();
        }
    }

    static async importAndExport(vaultDir, pass, importFile,
        bitflip, flipPosition, shouldBeEqual,
        applyFileIntegrity, verifyFileIntegrity) {

        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        let rootDir = await drive.getRoot();
        await rootDir.listFiles();

        let fileToImport = importFile;
        let hashPreImport = await SalmonFSTestHelper.getChecksum(fileToImport);

        // import
        let printImportProgress = async (position, length) => {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                console.log("importing file: " + position + "/" + length);
        }
        let salmonFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, applyFileIntegrity, printImportProgress);
        expect(await salmonFile.exists()).toBeTruthy();
        // get fresh copy of the file
        salmonFile = (await rootDir.listFiles())[0];

        expect(salmonFile != null).toBeTruthy();
        expect(await salmonFile.exists()).toBeTruthy();
        let salmonFiles = await (await drive.getRoot()).listFiles();
        let realFileSize = await fileToImport.length();
        for (let file of salmonFiles) {
            if ((await file.getBaseName()) == fileToImport.getBaseName()) {
                if (shouldBeEqual) {
                    expect(await file.exists()).toBeTruthy();
                    let fileSize = await file.getSize();
                    expect(fileSize).toBe(realFileSize);
                }
            }
        }

        // export
        let printExportProgress = async (position, length) => {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                console.log("exporting file: " + position + "/" + length);
        }
        if (bitflip)
            await SalmonFSTestHelper.flipBit(salmonFile, flipPosition);
        let exportFile = await SalmonFSTestHelper.fileExporter.exportFile(salmonFile, await drive.getExportDir(), null, false, verifyFileIntegrity, printExportProgress);
        let hashPostExport = await SalmonFSTestHelper.getChecksum(exportFile);
        if (shouldBeEqual) {
            expect(hashPostExport).toBe(hashPreImport);
        }
    }

    
    static async openDrive(vaultDir, driveClassType, pass, sequencer){
        if (driveClassType == JsWSDrive) {
            // use the remote service instead
            return await JsWSDrive.open(vaultDir, pass, sequencer,
                    SalmonFSTestHelper.credentials.getServiceUser(), SalmonFSTestHelper.credentials.getServicePassword());
        } else
            return await SalmonDrive.openDrive(vaultDir, driveClassType, pass, sequencer);
    }
	
    static async createDrive(vaultDir, driveClassType, pass, sequencer) {
        if (driveClassType == JsWSDrive)
            return await JsWSDrive.create(vaultDir, pass, sequencer, 
                SalmonFSTestHelper.credentials.getServiceUser(), SalmonFSTestHelper.credentials.getServicePassword());
        else
            return await SalmonDrive.createDrive(vaultDir, driveClassType, pass, sequencer);
    }

    static async importAndSearch(vaultDir, pass, importFile) {
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        let rootDir = await drive.getRoot();
        await rootDir.listFiles();
        let fileToImport = importFile;
        let rbasename = fileToImport.getBaseName();

        // import
        let salmonFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        expect(salmonFile != null).toBeTruthy();
        expect(await salmonFile.exists()).toBeTruthy();

        // search
        let basename = await salmonFile.getBaseName();
        let searcher = new FileSearcher();
        let files = await searcher.search(rootDir, basename, true, null, null);
        expect(files.length > 0).toBeTruthy();
        expect(basename).toBe(await files[0].getBaseName());

    }

    static async importAndCopy(vaultDir, pass, importFile, newDir, move) {
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        let rootDir = await drive.getRoot();
        rootDir.listFiles();
        let fileToImport = importFile;
        let rbasename = fileToImport.getBaseName();

        // import
        let salmonFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);

        // trigger the cache to add the filename
        let basename = await salmonFile.getBaseName();

        expect(salmonFile != null).toBeTruthy();

        expect(await salmonFile.exists()).toBeTruthy();

        let checkSumBefore = await SalmonFSTestHelper.getChecksum(salmonFile.getRealFile());
        let newDir1 = await rootDir.createDirectory(newDir);
        let newFile;
        if (move)
            newFile = await salmonFile.move(newDir1, null);
        else
            newFile = await salmonFile.copy(newDir1, null);

        expect(newFile != null).toBeTruthy();
        let checkSumAfter = await SalmonFSTestHelper.getChecksum(await newFile.getRealFile());

        expect(checkSumAfter).toBe(checkSumBefore);

        expect(await newFile.getBaseName()).toBe(await salmonFile.getBaseName());
    }

    static async flipBit(salmonFile, position) {
        let stream = await salmonFile.getRealFile().getOutputStream();
        await stream.setPosition(position);
        await stream.write(new Uint8Array([1]), 0, 1);
        await stream.flush();
        await stream.close();
    }

    static async shouldCreateFileWithoutVault(testBytes, key, applyIntegrity, verifyIntegrity, chunkSize, hashKey,
        filenameNonce, fileNonce, flipBit, flipPosition, checkData) {
        // write file
        let realDir = await SalmonFSTestHelper.generateFolder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        let dir = new SalmonFile(realDir, null);
        let filename = "test_" + Date.now() + "." + flipPosition + ".txt";
        let newFile = await dir.createFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            await newFile.setApplyIntegrity(true, hashKey, chunkSize);
        let stream = await newFile.getOutputStream();

        await stream.write(testBytes, 0, testBytes.length);
        await stream.flush();
        await stream.close();
        let realFile = newFile.getRealFile();

        // tamper
        if (flipBit) {
            let realTmpFile = await newFile.getRealFile();
            let realStream = await realTmpFile.getOutputStream();
            await realStream.setPosition(flipPosition);
            await realStream.write(new Uint8Array([0]), 0, 1);
            await realStream.flush();
            await realStream.close();
        }

        // open file for read
        let readFile = new SalmonFile(realFile, null);
        readFile.setEncryptionKey(key);
        readFile.setRequestedNonce(fileNonce);
        if (verifyIntegrity)
            await readFile.setVerifyIntegrity(true, hashKey);
        else
        await readFile.setVerifyIntegrity(false, null);
        let inStream = await readFile.getInputStream();
        let textBytes = new Uint8Array(testBytes.length);
        await inStream.read(textBytes, 0, textBytes.length);
        await inStream.close();
        if (checkData)
            SalmonCoreTestHelper.assertArrayEquals(testBytes, textBytes);
        return readFile;
    }

    static async exportAndImportAuth(vault, importFilePath) {
        // emulate 2 different devices with different sequencers
        let sequencer1 = await SalmonFSTestHelper.createSalmonFileSequencer();
        let sequencer2 = await SalmonFSTestHelper.createSalmonFileSequencer();

        // set to the first sequencer and create the vault
        let drive = await SalmonFSTestHelper.createDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        let rootDir = await drive.getRoot();
        let fileToImport = importFilePath;
        let salmonFileA1 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        let nonceA1 = BitConverter.toLong(salmonFileA1.getRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        // open with another device (different sequencer) and export auth id
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        let authId = await drive.getAuthId();
        let success = false;
        try {
            // import a test file should fail because not authorized
            rootDir = await drive.getRoot();
            fileToImport = importFilePath;
            await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
            success = true;
        } catch (ignored) { }

        expect(success).toBeFalsy();
        drive.close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        let exportAuthDir = await this.generateFolder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR);
        let exportFile = await exportAuthDir.createFile(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        await SalmonAuthConfig.exportAuthFile(drive, authId, exportFile);
        let exportAuthFile = await exportAuthDir.getChild(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        let salmonCfgFile = new SalmonFile(exportAuthFile, drive);
        let nonceCfg = BitConverter.toLong(await salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        rootDir = await drive.getRoot();
        fileToImport = importFilePath;
        let salmonFileA2 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        let nonceA2 = BitConverter.toLong(await salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        //reopen with second device(sequencer) and import auth file
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        await SalmonAuthConfig.importAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = await drive.getRoot();
        fileToImport = importFilePath;
        let salmonFileB1 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        let nonceB1 = BitConverter.toLong(await salmonFileB1.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        let salmonFileB2 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        let nonceB2 = BitConverter.toLong(await salmonFileB2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        expect(nonceCfg - 1).toBe(nonceA1);
        expect(nonceA2 - 2).toBe(nonceCfg);
        expect(nonceB1).not.toBe(nonceA2);
        expect(nonceB2 - 2).toBe(nonceB1);
    }

    static async testMaxFiles(vaultDir, seqFile, importFile, testMaxNonce, offset, shouldImport) {
        let importSuccess;
        try {
            class TestSalmonFileSequencer extends SalmonFileSequencer {
                async initializeSequence(driveId, authId, startNonce, maxNonce) {
                    let nMaxNonce = BitConverter.toLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                    await super.initializeSequence(driveId, authId, startNonce, maxNonce);
                }
            }
            let sequencer = new TestSalmonFileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
            let drive;
            try {
                drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            } catch (ex) {
                drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            }
            let rootDir = await drive.getRoot();
            await rootDir.listFiles();
            let fileToImport = importFile;
            let salmonFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        } catch (ex) {
            console.error(ex);
            // TODO: check specific exception
            if(ex instanceof SalmonRangeExceededException)
                importSuccess = false;
        }

        expect(importSuccess).toBe(shouldImport);
    }

	static async testRaw() {
        let text = "This is a plaintext that will be used for testing";
		for(let i=0; i<18; i++)
			text += text;
		const BUFF_SIZE = 256 * 1024;
        let dir = await this.generateFolder("test");
		let filename = "file.txt";
		let testFile = await dir.createFile(filename);
        let bytes = new TextEncoder().encode(text);

        // write file
		let readFile = await dir.getChild(filename);
        let wstream = await testFile.getOutputStream();
		let idx = 0;
		while(idx < text.length) {
			let len = Math.min(BUFF_SIZE, text.length - idx);
			await wstream.write(bytes.slice(idx, idx + len), 0, len);
			idx += len;
		}
        await wstream.flush();
        await wstream.close();
        
		// read a file
		let writeFile = await dir.getChild(filename);
        let rstream = await writeFile.getInputStream();
		let readBuff = new Uint8Array(BUFF_SIZE);
		let bytesRead = 0;
		let lstream = new MemoryStream();
		while((bytesRead = await rstream.read(readBuff, 0, readBuff.length)) > 0) {
			await lstream.write(readBuff, 0, bytesRead);
		}
		let lbytes = lstream.toArray();
		let string = new TextDecoder().decode(lbytes);
        // console.log(string);
        await rstream.close();

        expect(string).toBe(text);
    }
	
    static async testEnc() {
        let text = "This is a plaintext that will be used for testing";
		for(let i=0; i<18; i++)
			text += text;
		const BUFF_SIZE = 256 * 1024;
        let dir = await this.generateFolder("test");
		let filename = "file.dat";
		let testFile = await dir.createFile(filename);
        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
		let wfile = await dir.getChild(filename);
        let encFile = new SalmonFile(wfile);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        let stream = await encFile.getOutputStream();
        // encrypt data and write with a single call
		let idx = 0;
		while(idx < text.length) {
			let len = Math.min(BUFF_SIZE, text.length - idx);
			await stream.write(bytes.slice(idx, idx + len), 0, len);
			idx += len;
		}
        await stream.flush();
        await stream.close();
		
        // decrypt an encrypted file
		let rfile = await dir.getChild(filename);
        let encFile2 = new SalmonFile(rfile);
        encFile2.setEncryptionKey(key);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(BUFF_SIZE);
		let lstream = new MemoryStream();
		let bytesRead = 0;
		// decrypt and read data with a single call, you can also Seek() to any position before Read()
		while((bytesRead = await stream2.read(decBuff, 0, decBuff.length)) > 0) {
			await lstream.write(decBuff, 0, bytesRead);
		}
		let lbytes = lstream.toArray();
        let decString2 = new TextDecoder().decode(lbytes);
        // console.log(decString2);
        await stream2.close();

        expect(decString2).toBe(text);
    }
	
    static async testExamples() {
        let text = "This is a plaintext that will be used for testing";
		for(let i=0; i<7; i++)
			text += text;
        let dir = await this.generateFolder("test");
		let filename = "file.dat";
		const BUFF_SIZE = 256 * 1024;
		let testFile = await dir.createFile(filename);
        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        let encryptor = new SalmonEncryptor(SalmonCoreTestHelper.TEST_ENC_THREADS,
                                               SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE); // use more threads for parallel processing
        let decryptor = new SalmonDecryptor(SalmonCoreTestHelper.TEST_DEC_THREADS,
                                               SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE);
        let encBytes = await encryptor.encrypt(bytes, key, nonce, false);
        // decrypt byte array
        let decBytes = await decryptor.decrypt(encBytes, key, nonce, false);
        SalmonCoreTestHelper.assertArrayEquals(bytes, decBytes);
        encryptor.close();
        decryptor.close();

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        let encText = await SalmonTextEncryptor.encryptString(text, key, nonce, true);
        // decrypt string
        let decText = await SalmonTextDecryptor.decryptString(encText, key, null, true);

        expect(decText).toBe(text);

        // Example 3: encrypt data to an output stream
        let encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        let encryptStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream,
            null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        await encryptStream.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encryptStream.flush();
        await encryptStream.close();
        await encOutStream.close();
        //decrypt a stream with encoded data
        let encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        let decryptStream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream,
            null, false, null, null);
        let decBuffer = new Uint8Array(text.length);
        // decrypt and read data with a single call, you can also Seek() before Read()
        let bytesRead = await decryptStream.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        let decString = new TextDecoder().decode(decBuffer.slice(0, bytesRead));
        // console.log(decString);
        await decryptStream.close();
        await encInputStream.close();

        expect(decString).toBe(text);

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
		testFile = await dir.getChild(filename);
        let encFile = new SalmonFile(testFile);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        let stream = await encFile.getOutputStream();
        // encrypt data and write with a single call
		let idx = 0;
		while(idx < text.length) {
			let len = Math.min(BUFF_SIZE, text.length - idx);
			await stream.write(bytes.slice(idx, idx+len), 0, len);
			idx += len;
		}
        await stream.flush();
        await stream.close();
        // decrypt an encrypted file
		testFile = await dir.getChild(filename);
        let encFile2 = new SalmonFile(testFile);
        encFile2.setEncryptionKey(key);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(BUFF_SIZE);
		let lstream = new MemoryStream();
		// decrypt and read data with a single call, you can also Seek() to any position before Read()
		while((bytesRead = await stream2.read(decBuff, 0, decBuff.length)) > 0) {
			await lstream.write(decBuff, 0, bytesRead);
		}
		let lbytes = lstream.toArray();
        let decString2 = new TextDecoder().decode(lbytes);
        // console.log(decString2);
        await stream2.close();

        expect(decString2).toBe(text);
    }

    static async encryptAndDecryptStream(data, key, nonce) {
        let encOutStream = new MemoryStream();
        let encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        let inputStream = new MemoryStream(data);
        await inputStream.copyTo(encryptor);
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encryptor.flush();
        await encryptor.close();
        await encOutStream.close();
        await inputStream.close();

        let encInputStream = new MemoryStream(encData);
        let decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        let outStream = new MemoryStream();
        await decryptor.copyTo(outStream);
        await outStream.setPosition(0);
        let decData = outStream.toArray();
        await decryptor.close();
        await encInputStream.close();
        await outStream.close();

        SalmonCoreTestHelper.assertArrayEquals(data, decData);
    }

    static async getRealFileContents(filePath) {
        let file = filePath;
        let ins = await file.getInputStream();
        let outs = new MemoryStream();
        await ins.copyTo(outs);
        await outs.setPosition(0);
        await outs.flush();
        await outs.close();
        return outs.toArray();
    }

    static async seekAndReadFileInputStream(data, fileInputStream, start, length, readOffset, shouldReadLength) {
        let buffer = new Uint8Array(length + readOffset);
        let reader = fileInputStream.getReader();
        await fileInputStream.reset();
        await fileInputStream.skip(start);
        let buff = await reader.read();
        if (buff.value == undefined)
            throw new Error("Could not read from stream");
        for (let i = 0; i < length; i++) {
            buffer[readOffset + i] = buff.value[i];
        }
        let tdata = new Uint8Array(buffer.length);
        for (let i = 0; i < shouldReadLength; i++)
            tdata[readOffset + i] = data[start + i];
        SalmonCoreTestHelper.assertArrayEquals(tdata, buffer);
        reader.releaseLock();
    }

    static async shouldTestFileSequencer() {
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        await sequencer.initialize();

        await sequencer.createSequence("AAAA", "AAAA");
        await sequencer.initializeSequence("AAAA", "AAAA",
            BitConverter.toBytes(1, 8),
            BitConverter.toBytes(4, 8));
        let nonce = await sequencer.nextNonce("AAAA");
        expect(BitConverter.toLong(nonce, 0, 8)).toBe(1);
        nonce = await sequencer.nextNonce("AAAA");
        expect(BitConverter.toLong(nonce, 0, 8)).toBe(2);
        nonce = await sequencer.nextNonce("AAAA");
        expect(BitConverter.toLong(nonce, 0, 8)).toBe(3);

        let caught = false;
        try {
            nonce = await sequencer.nextNonce("AAAA");
            expect(BitConverter.toLong(nonce, 0, 8)).toBe(5);
        }
        catch (ex) {
            console.error(ex);
            caught = true;
        }
        expect(caught).toBeTruthy();
    }

    static async getChildrenCountRecursively(realFile) {
        let count = 1;
        if (await realFile.isDirectory()) {
            for (let child of await realFile.listFiles()) {
                count += await SalmonFSTestHelper.getChildrenCountRecursively(child);
            }
        }
        return count;
    }

    static async copyStream(src, dest) {
        let bufferSize = src.getBufferSize();
        let bytesRead;
        let buffer = new Uint8Array(bufferSize);
        while ((bytesRead = await src.read(buffer, 0, bufferSize)) > 0) {
            await dest.write(buffer, 0, bytesRead);
        }
        await dest.flush();
    }

    static async copyReadableStream(src, dest) {
        let bufferSize = 512 * 1024;
        let bytesRead;
        let buffer = new Uint8Array(bufferSize);
        let reader = src.getReader();
        while ((bytesRead = await reader.read()) > 0) {
            await dest.write(buffer, 0, bytesRead);
        }
        await dest.flush();
        reader.releaseLock();
        src.cancel();
    }

    static async shouldReadFile(vaultPath, filename) {
        let localFile = await SalmonFSTestHelper.TEST_INPUT_DIR.getChild(filename);
        let localChkSum = await this.getChecksum(localFile);

        let vaultDir = vaultPath;
		let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        let root = await drive.getRoot();
        let file = await root.getChild(filename);
        expect(await file.exists()).toBeTruthy();
        
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        let start = Date.now();
        await stream.copyTo(ms);
        let end = Date.now();
        await ms.flush();
        await ms.setPosition(0);
        await ms.close();
        await stream.close();
        let digest = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));
        expect(digest).toBe(localChkSum);
    }

    static async seekAndReadHttpFile(data, file, isEncrypted = false,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            0, 32, 0, 32,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            220, 8, 2, 8,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            100, 2, 0, 2,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            6, 16, 0, 16,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            50, 40, 0, 40,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            124, 50, 0, 50,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            250, 10, 0, 10,
            buffersCount, bufferSize, backOffset);
    }

    // shouldReadLength should be equal to length
    // when checking Http files since the return buffer 
    // might give us more data than requested
    static async seekAndReadFileStream(data, file, isEncrypted = false,
        start, length, readOffset, shouldReadLength,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {
        let buffer = new Uint8Array(length + readOffset);

        let stream = null;
        if (SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM && isEncrypted) {
            // multi threaded
            stream = SalmonFileReadableStream.create(file, buffersCount, bufferSize, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        } else {
            let fileStream;
            if (isEncrypted) {
                fileStream = await file.getInputStream();
            } else {
                fileStream = new JsHttpFileStream(file);
            }
            stream = ReadableStreamWrapper.create(fileStream);
        }
        let reader = await stream.getReader();
        await stream.skip(start);
        let res = await reader.read();
        expect(res.value).toBeDefined();
        for (let i = 0; i < length; i++) {
            buffer[readOffset + i] = res.value[i];
        }
        let tdata = new Uint8Array(buffer.length);
        for (let i = 0; i < shouldReadLength; i++) {
            tdata[readOffset + i] = data[start + i];
        }
        console.log(tdata);
        console.log(buffer);
        reader.releaseLock();
        await stream.cancel();
        SalmonCoreTestHelper.assertArrayEquals(tdata, buffer);
    }
}