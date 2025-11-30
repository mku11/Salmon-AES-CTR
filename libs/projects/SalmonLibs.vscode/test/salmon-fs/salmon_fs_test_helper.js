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

import { BitConverter } from '../../lib/simple-io/convert/bit_converter.js';
import { MemoryStream } from '../../lib/simple-io/streams/memory_stream.js';
import { RandomAccessStream } from '../../lib/simple-io/streams/random_access_stream.js';
import { Generator } from '../../lib/salmon-core/salmon/generator.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/streams/encryption_mode.js';
import { RangeExceededException } from '../../lib/salmon-core/salmon/range_exceeded_exception.js';
import { ReadableStreamWrapper } from '../../lib/simple-io/streams/readable_stream_wrapper.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';
import { AesFile } from '../../lib/salmon-fs/salmonfs/file/aes_file.js';
import { AesDrive } from '../../lib/salmon-fs/salmonfs/drive/aes_drive.js';
import { SequenceSerializer } from '../../lib/salmon-core/salmon/sequence/sequence_serializer.js';
import { FileSequencer } from '../../lib/salmon-fs/salmonfs/sequence/file_sequencer.js';
import { AesFileImporter } from '../../lib/salmon-fs/salmonfs/drive/utils/aes_file_importer.js';
import { AesFileExporter } from '../../lib/salmon-fs/salmonfs/drive/utils/aes_file_exporter.js';
import { AesFileCommander } from '../../lib/salmon-fs/salmonfs/drive/utils/aes_file_commander.js';
import { autoRenameFile as autoRenameFile } from '../../lib/salmon-fs/../simple-fs/fs/file/ifile.js';
import { FileSearcher, SearchOptions} from '../../lib/salmon-fs/../simple-fs/fs/drive/utils/file_searcher.js';
import { AesFileReadableStream } from '../../lib/salmon-fs/salmonfs/streams/aes_file_readable_stream.js';
import { AuthConfig } from '../../lib/salmon-fs/salmonfs/auth/auth_config.js';
import { HttpDrive } from '../../lib/salmon-fs/salmonfs/drive/http_drive.js';
import { WSFile } from '../../lib/salmon-fs/../simple-fs/fs/file/ws_file.js';
import { Credentials } from '../../lib/salmon-fs/../simple-fs/fs/file/credentials.js';
import { WSDrive } from '../../lib/salmon-fs/salmonfs/drive/ws_drive.js';
import { HttpFile } from '../../lib/salmon-fs/../simple-fs/fs/file/http_file.js';
import { HttpSyncClient } from '../../lib/salmon-fs/../simple-fs/fs/file/http_sync_client.js';
import { FileImportOptions } from '../../lib/salmon-fs/../simple-fs/fs/drive/utils/file_importer.js';
import { FileExportOptions } from '../../lib/salmon-fs/../simple-fs/fs/drive/utils/file_exporter.js';
import { BatchExportOptions } from '../../lib/salmon-fs/../simple-fs/fs/drive/utils/file_commander.js';
import { Platform, PlatformType } from '../../lib/simple-io/platform/platform.js';

export const TestMode = {
    Local: { name: 'Local', ordinal: 0 },
    Node: { name: 'Node', ordinal: 1 },
    Http: { name: 'Http', ordinal: 2 },
    WebService: { name: 'WebService', ordinal: 3 },
}

let currTestMode = null;
export function getTestMode() {
	return currTestMode;
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
    static TEST_EXPORT_DIRNAME = "export";
    static TEST_IMPORT_TINY_FILENAME = "tiny_test.txt";
    static TEST_IMPORT_SMALL_FILENAME = "small_test.dat";
    static TEST_IMPORT_MEDIUM_FILENAME = "medium_test.dat";
    static TEST_IMPORT_LARGE_FILENAME = "large_test.dat";
    static TEST_IMPORT_HUGE_FILENAME = "huge_test.dat";
    static TINY_FILE_CONTENTS = "This is a new file created that will be used for testing encryption and decryption.";
    static TEST_SEQ_DIRNAME = "seq";
    static TEST_SEQ_FILENAME = "fileseq.json";
    static TEST_EXPORT_AUTH_FILENAME = "export.slma";

    // Web service
	static WS_SERVER_DEFAULT_URL = "http://localhost:8080";
    // static WS_SERVER_DEFAULT_URL = "https://localhost:8443"; // for testing from the Web browser
	static WS_SERVER_URL = SalmonFSTestHelper.WS_SERVER_DEFAULT_URL;
    static WS_TEST_DIRNAME = "ws";
    static credentials = new Credentials("user", "password");
    
    // HTTP server (Read-only)
	static HTTP_SERVER_DEFAULT_URL = "http://localhost";
    static HTTP_SERVER_URL = SalmonFSTestHelper.HTTP_SERVER_DEFAULT_URL;
    static HTTP_TEST_DIRNAME = "httpserv";
    static HTTP_VAULT_DIRNAME = "vault";
	static HTTP_SERVER_VIRTUAL_URL;
    static HTTP_VAULT_DIR_URL;
    static HTTP_VAULT_FILES_DIR_URL;
    static httpCredentials = new Credentials("user", "password");

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
    static TEST_HTTP_TINY_FILE;
    static TEST_HTTP_SMALL_FILE;
    static TEST_HTTP_MEDIUM_FILE;
    static TEST_HTTP_LARGE_FILE;
    static TEST_HTTP_HUGE_FILE;
    static TEST_HTTP_FILE;
    static TEST_SEQ_DIR;
    static TEST_EXPORT_AUTH_DIR;
    static TEST_EXPORT_DIR;
    static fileImporter;
    static fileExporter;
    static sequenceSerializer = new SequenceSerializer();
    
    // testDir can be a path or a fileHandle
	static async setTestParams(testDir, testMode) {
        currTestMode = testMode;

        if (testMode == TestMode.Local) {
            const { Drive } = await import('../../lib/salmon-fs/salmonfs/drive/drive.js');
            SalmonFSTestHelper.driveClassType = Drive;
        } else if (testMode == TestMode.Node) {
            const { NodeDrive } = await import('../../lib/salmon-fs/salmonfs/drive/node_drive.js');
            SalmonFSTestHelper.driveClassType = NodeDrive;
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.driveClassType = HttpDrive;
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.driveClassType = WSDrive;
        }

        if(Platform.getPlatform() == PlatformType.Browser) {
            if(!(testDir instanceof FileSystemDirectoryHandle))
                throw new Error("Select a valid test directory");
            const { File } = await import('../../lib/salmon-fs/../simple-fs/fs/file/file.js');
            SalmonFSTestHelper.TEST_ROOT_DIR = new File(testDir);
        } else if(Platform.getPlatform() == PlatformType.NodeJs) {
            const { NodeFile } = await import('../../lib/salmon-fs/../simple-fs/fs/file/node_file.js');
            SalmonFSTestHelper.TEST_ROOT_DIR = new NodeFile(testDir);
            if(!await SalmonFSTestHelper.TEST_ROOT_DIR.exists())
                await SalmonFSTestHelper.TEST_ROOT_DIR.mkdir();
        }

		SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL = SalmonFSTestHelper.HTTP_SERVER_URL + "/test";
		SalmonFSTestHelper.HTTP_VAULT_DIR_URL = SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL 
			+ "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME + "/" + SalmonFSTestHelper.HTTP_VAULT_DIRNAME;
		SalmonFSTestHelper.HTTP_VAULT_FILES_DIR_URL = SalmonFSTestHelper.HTTP_VAULT_DIR_URL + "/fs"; 
		
        console.log("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.getDisplayPath());
        SalmonFSTestHelper.TEST_INPUT_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if(testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new WSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = await SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL, this.httpCredentials);
		await SalmonFSTestHelper.createTestFiles();
        await SalmonFSTestHelper.createHttpFiles();
        await SalmonFSTestHelper.createHttpVault();

        HttpSyncClient.setAllowClearTextTraffic(true); // only for testing purposes
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

    static async createHttpFiles() {
        SalmonFSTestHelper.TEST_HTTP_TINY_FILE = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_SMALL_FILE = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_LARGE_FILE = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_HUGE_FILE = await SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_TINY_FILE;

		await SalmonFSTestHelper.createFile(SalmonFSTestHelper.TEST_HTTP_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_SMALL_FILE,1024*1024);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE,12*1024*1024);
		await SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_LARGE_FILE,48*1024*1024);
		// this.createFileRandomData(TEST_HTTP_HUGE_FILE,512*1024*1024);
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
            SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,
            SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE,
        ];
        let importer = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        let importOptions = new FileImportOptions();
        importOptions.integrity = true;
        for(let importFile of importFiles) {
            await importer.importFile(importFile, rootDir, importOptions);
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
        SalmonFSTestHelper.fileImporter = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new AesFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    static close() {
		if(SalmonFSTestHelper.fileImporter)
			SalmonFSTestHelper.fileImporter.close();
		if(SalmonFSTestHelper.fileExporter)
			SalmonFSTestHelper.fileExporter.close();
    }

    static async createSalmonFileSequencer() {
        // always create the sequencer files locally
        let seqDir = await this.generateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        let seqFile = await seqDir.getChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
        return new FileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
    }

    static async generateFolder(name, parent=SalmonFSTestHelper.TEST_OUTPUT_DIR, rand = true) {
        let dirName = name + (rand ? "_" + Date.now() 
			+ "" + + Math.round(Math.random()*100): "");
        let dir = await parent.getChild(dirName);
        if(!await dir.exists())
            await dir.mkdir();
        console.log("generated folder: " + dir.getDisplayPath());
        return dir;
    }

    static async getChecksum(file) {
		let stream = await file.getInputStream()
        return SalmonCoreTestHelper.getChecksumStream(stream)
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
        let importOptions = new FileImportOptions();
        importOptions.integrity = applyFileIntegrity;
        importOptions.onProgressChanged = printImportProgress;
        let aesFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir, importOptions);
		
		// get fresh copy of the file
        // TODO: for remote files the output stream should clear all cached file properties
        //instead of having to get a new file
        aesFile = await rootDir.getChild(await aesFile.getName());
		
		let chunkSize = await aesFile.getFileChunkSize();
        if (chunkSize == 0 || !verifyFileIntegrity)
            await aesFile.setVerifyIntegrity(false);
		else
			await aesFile.setVerifyIntegrity(true);
		
        expect(await aesFile.exists()).toBeTruthy();
        let hashPostImport = await SalmonCoreTestHelper.getChecksumStream(await aesFile.getInputStream());
        if (shouldBeEqual)
            expect(hashPreImport).toBe(hashPostImport);
		
        expect(aesFile).toBeTruthy();
        expect(await aesFile.exists()).toBeTruthy();
		
        let aesFiles = await (await drive.getRoot()).listFiles();
        let realFileSize = await fileToImport.getLength();
        for (let file of aesFiles) {
            if ((await file.getName()) == fileToImport.getName()) {
                if (shouldBeEqual) {
                    expect(await file.exists()).toBeTruthy();
                    let fileSize = await file.getLength();
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
            await SalmonFSTestHelper.flipBit(aesFile, flipPosition);
		let chunkSize2 = await aesFile.getFileChunkSize();
        if (chunkSize2 > 0 && verifyFileIntegrity)
            await aesFile.setVerifyIntegrity(true);
		else
			await aesFile.setVerifyIntegrity(false);
        let exportOptions = new FileExportOptions();
        exportOptions.integrity = verifyFileIntegrity;
        exportOptions.onProgressChanged = printExportProgress;
        let exportDir = await SalmonFSTestHelper.generateFolder("export", SalmonFSTestHelper.TEST_EXPORT_DIR, true);
        let exportFile = await SalmonFSTestHelper.fileExporter.exportFile(aesFile, exportDir, exportOptions);
        let hashPostExport = await SalmonFSTestHelper.getChecksum(exportFile);
        if (shouldBeEqual) {
            expect(hashPostExport).toBe(hashPreImport);
        }
    }

    
    static async openDrive(vaultDir, driveClassType, pass, sequencer){
        if (driveClassType == WSDrive) {
            // use the remote service instead
            return await WSDrive.open(vaultDir, pass, sequencer,
                    SalmonFSTestHelper.credentials.getServiceUser(), SalmonFSTestHelper.credentials.getServicePassword());
        } else
            return await AesDrive.openDrive(vaultDir, driveClassType, pass, sequencer);
    }
	
    static async createDrive(vaultDir, driveClassType, pass, sequencer) {
        if (driveClassType == WSDrive)
            return await WSDrive.create(vaultDir, pass, sequencer, 
                SalmonFSTestHelper.credentials.getServiceUser(), SalmonFSTestHelper.credentials.getServicePassword());
        else
            return await AesDrive.createDrive(vaultDir, driveClassType, pass, sequencer);
    }

    static async importAndSearch(vaultDir, pass, importFile) {
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        let rootDir = await drive.getRoot();
        await rootDir.listFiles();
        let fileToImport = importFile;
        let rbasename = fileToImport.getName();

        // import
        let aesFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
        expect(aesFile).toBeTruthy();
        expect(await aesFile.exists()).toBeTruthy();

        // search
        let basename = await aesFile.getName();
        let searcher = new FileSearcher();
		let searchOptions = new SearchOptions();
		searchOptions.anyTerm = true;
        let files = await searcher.search(rootDir, basename, searchOptions);
        expect(files.length > 0).toBeTruthy();
        expect(basename).toBe(await files[0].getName());

    }

    static async importAndCopy(vaultDir, pass, importFile, newDir, move) {
        let sequencer = await SalmonFSTestHelper.createSalmonFileSequencer();
        let drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        let rootDir = await drive.getRoot();
        rootDir.listFiles();
        let fileToImport = importFile;
        let rbasename = fileToImport.getName();

        // import
        let aesFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);

        // trigger the cache to add the filename
        let basename = await aesFile.getName();

        expect(aesFile).toBeTruthy();

        expect(await aesFile.exists()).toBeTruthy();

        let checkSumBefore = await SalmonFSTestHelper.getChecksum(aesFile.getRealFile());
        let newDir1 = await rootDir.createDirectory(newDir);
        let newFile;
        if (move)
            newFile = await aesFile.move(newDir1, null);
        else
            newFile = await aesFile.copy(newDir1, null);

        expect(newFile).toBeTruthy();
        let checkSumAfter = await SalmonFSTestHelper.getChecksum(await newFile.getRealFile());

        expect(checkSumAfter).toBe(checkSumBefore);

        expect(await newFile.getName()).toBe(await aesFile.getName());
    }

    static async flipBit(aesFile, position) {
        let stream = await aesFile.getRealFile().getOutputStream();
        await stream.setPosition(position);
        await stream.write(new Uint8Array([1]), 0, 1);
        await stream.flush();
        await stream.close();
    }

    static async shouldCreateFileWithoutVault(testBytes, key, applyIntegrity, verifyIntegrity, chunkSize, hashKey,
        filenameNonce, fileNonce, flipBit, flipPosition, checkData) {
        // write file
        let realDir = await SalmonFSTestHelper.generateFolder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        let dir = new AesFile(realDir);
        let filename = "test_" + Date.now() + "." + flipPosition + ".txt";
        let newFile = await dir.createFile(filename, key, filenameNonce, fileNonce);
        console.log("new file: " + await newFile.getPath());
        if (applyIntegrity)
            await newFile.setApplyIntegrity(true, hashKey, chunkSize);
        else
            await newFile.setApplyIntegrity(false);
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
        let readFile = new AesFile(realFile);
        readFile.setEncryptionKey(key);
        readFile.setRequestedNonce(fileNonce);
        if (verifyIntegrity)
            await readFile.setVerifyIntegrity(true, hashKey);
        else
			await readFile.setVerifyIntegrity(false);
        let inStream = await readFile.getInputStream();
        let textBytes = new Uint8Array(testBytes.length);
        await inStream.read(textBytes, 0, textBytes.length);
        await inStream.close();
        if (checkData)
            await SalmonCoreTestHelper.assertLargeArrayEquals(testBytes, textBytes);
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
        let aesFile1 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
        let nonceA1 = BitConverter.toLong(aesFile1.getRequestedNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        // open with another device (different sequencer) and export auth id
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        let authId = await drive.getAuthId();
        let success = false;
        try {
            // import a test file should fail because not authorized
            rootDir = await drive.getRoot();
            fileToImport = importFilePath;
            await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
            success = true;
        } catch (ex) { 
            console.log("Caught:", ex.message);
        }

        expect(success).toBeFalsy();
        drive.close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        let exportAuthDir = await this.generateFolder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR);
        let exportFile = await exportAuthDir.createFile(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        await AuthConfig.exportAuthFile(drive, authId, exportFile);
        let exportAuthFile = await exportAuthDir.getChild(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        let salmonCfgFile = new AesFile(exportAuthFile, drive);
        let nonceCfg = BitConverter.toLong(await salmonCfgFile.getFileNonce(), 0, Generator.NONCE_LENGTH);
        // import another test file
        rootDir = await drive.getRoot();
        fileToImport = importFilePath;
        let aesFileA2 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
        let nonceA2 = BitConverter.toLong(await aesFileA2.getFileNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        //reopen with second device(sequencer) and import auth file
        drive = await SalmonFSTestHelper.openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        await AuthConfig.importAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = await drive.getRoot();
        fileToImport = importFilePath;
        let aesFileB1 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
        let nonceB1 = BitConverter.toLong(await aesFileB1.getFileNonce(), 0, Generator.NONCE_LENGTH);
        let aesFileB2 = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
        let nonceB2 = BitConverter.toLong(await aesFileB2.getFileNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        expect(nonceCfg - 1).toBe(nonceA1);
        expect(nonceA2 - 2).toBe(nonceCfg);
        expect(nonceB1).not.toBe(nonceA2);
        expect(nonceB2 - 2).toBe(nonceB1);
    }

    static async testMaxFiles(vaultDir, seqFile, importFile, testMaxNonce, offset, shouldImport) {
        let importSuccess;
        try {
            class TestFileSequencer extends FileSequencer {
                async initializeSequence(driveId, authId, startNonce, maxNonce) {
                    let nMaxNonce = BitConverter.toLong(testMaxNonce, 0, Generator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, Generator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, Generator.NONCE_LENGTH);
                    await super.initializeSequence(driveId, authId, startNonce, maxNonce);
                }
            }
            let sequencer = new TestFileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
            let drive;
            try {
                drive = await SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            } catch (ex) {
                drive = await SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            }
            let rootDir = await drive.getRoot();
            await rootDir.listFiles();
            let fileToImport = importFile;
            let aesFile = await SalmonFSTestHelper.fileImporter.importFile(fileToImport, rootDir);
            importSuccess = aesFile != null;
        } catch (ex) {
            // TODO: check specific exception
            if(ex instanceof RangeExceededException)
                importSuccess = false;
            if(importSuccess == shouldImport)
                console.log("Caught:", ex.message);
            else
                console.error(ex);
        }

        expect(importSuccess).toBe(shouldImport);
    }
        
    static async testRawFile() {
        let text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        const BUFF_SIZE = 16;
        let dir = await this.generateFolder("test");
        let filename = "file.txt";
        let testFile = await dir.createFile(filename);
        let bytes = new TextEncoder().encode(text);

        // write to file
        let wstream = await testFile.getOutputStream();
        let idx = 0;
        while (idx < text.length) {
            let len = Math.min(BUFF_SIZE, text.length - idx);
            await wstream.write(bytes, idx, len);
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
        while ((bytesRead = await rstream.read(readBuff, 0, readBuff.length)) > 0) {
            await lstream.write(readBuff, 0, bytesRead);
        }
        let lbytes = lstream.toArray();
        let string = new TextDecoder().decode(lbytes);
        // console.log(string);
        await rstream.close();

        expect(string).toBe(text);
    }

    static async testEncDecFile() {
        let text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        const BUFF_SIZE = 16;
        let dir = await this.generateFolder("test");
        let filename = "file.dat";
        let testFile = await dir.createFile(filename);
        let bytes = new TextEncoder().encode(text);
        let key = Generator.getSecureRandomBytes(32);
        let nonce = Generator.getSecureRandomBytes(8);

        let wfile = await dir.getChild(filename);
        let encFile = new AesFile(wfile);
        nonce = Generator.getSecureRandomBytes(8);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        let stream = await encFile.getOutputStream();
        let idx = 0;
        while (idx < text.length) {
            let len = Math.min(BUFF_SIZE, text.length - idx);
            await stream.write(bytes, idx, len);
            idx += len;
        }
        await stream.flush();
        await stream.close();

        // decrypt an encrypted file
        let rfile = await dir.getChild(filename);
        let encFile2 = new AesFile(rfile);
        encFile2.setEncryptionKey(key);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(BUFF_SIZE);
        let lstream = new MemoryStream();
        let bytesRead = 0;

        while ((bytesRead = await stream2.read(decBuff, 0, decBuff.length)) > 0) {
            await lstream.write(decBuff, 0, bytesRead);
        }
        let lbytes = lstream.toArray();
        let decString2 = new TextDecoder().decode(lbytes);
        await stream2.close();

        expect(decString2).toBe(text);
    }
    
    static async encryptAndDecryptStream(data, key, nonce) {
        let encOutStream = new MemoryStream();
        let encryptor = new AesStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        let inputStream = new MemoryStream(data);
        await inputStream.copyTo(encryptor);
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encryptor.flush();
        await encryptor.close();
        await encOutStream.close();
        await inputStream.close();

        let encInputStream = new MemoryStream(encData);
        let decryptor = new AesStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        let outStream = new MemoryStream();
        await decryptor.copyTo(outStream);
        await outStream.setPosition(0);
        let decData = outStream.toArray();
        await decryptor.close();
        await encInputStream.close();
        await outStream.close();

        await SalmonCoreTestHelper.assertLargeArrayEquals(data, decData);
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
        let buff;
        let totalBytesRead = 0;
        while (totalBytesRead < length && (buff = await reader.read())) {
            if(!buff.value)
                break;
            let len = Math.min(length - totalBytesRead, buff.value.length);
            for (let i = 0; i < len; i++) {
                buffer[readOffset + totalBytesRead + i] = buff.value[i];
            }
            totalBytesRead += len;
        }
        
        let tdata = new Uint8Array(buffer.length);
        for (let i = 0; i < shouldReadLength; i++)
            tdata[readOffset + i] = data[start + i];
        await SalmonCoreTestHelper.assertLargeArrayEquals(tdata, buffer);
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
            console.log("Caught:", ex.message);
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
        let bufferSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
        let bytesRead;
        let buffer = new Uint8Array(bufferSize);
        while ((bytesRead = await src.read(buffer, 0, bufferSize)) > 0) {
            await dest.write(buffer, 0, bytesRead);
        }
        await dest.flush();
    }

    static async copyReadableStream(src, dest) {
        let reader = src.getReader();
        let buff;
        let totalBytesRead = 0;
        while (buff = await reader.read()) {
            if(!buff.value)
                break;
            await dest.write(buff.value, 0, buff.value.length);
            totalBytesRead += buff.value.length;
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
        console.log("file size: " + await file.getLength());
		console.log("file last modified: " + await file.getLastDateModified());
        expect(await file.exists()).toBeTruthy();
        
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        await ms.flush();
        await ms.setPosition(0);
        await ms.close();
        await stream.close();
        // console.log("Text: ")
        // console.log(new TextDecoder().decode(ms.toArray()));
        let digest = await SalmonCoreTestHelper.getChecksumStream(ms);
        expect(digest).toBe(localChkSum);
    }

    static async seekAndReadHttpFile(data, file,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            0, 32, 0, 32,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            220, 8, 2, 8,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            100, 2, 0, 2,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            6, 16, 0, 16,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            50, 40, 0, 40,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            124, 50, 0, 50,
            buffersCount, bufferSize, backOffset);
        await SalmonFSTestHelper.seekAndReadFileStream(data, file,
            250, 10, 0, 10,
            buffersCount, bufferSize, backOffset);
    }

    // shouldReadLength should be equal to length
    // when checking Http files since the return buffer 
    // might give us more data than requested
    static async seekAndReadFileStream(data, file,
        start, length, readOffset, shouldReadLength,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {
        let buffer = new Uint8Array(length + readOffset);

        let stream = null;
        if (SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM) {
            // multi threaded
            stream = AesFileReadableStream.createFileReadableStream(file, buffersCount, bufferSize, 
                SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        } else {
            let fileStream = await file.getInputStream();
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
        await SalmonCoreTestHelper.assertLargeArrayEquals(tdata, buffer);
    }

    static async exportFiles(files, dir, threads = 1) {
		let bufferSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
		let commander = new AesFileCommander(bufferSize, bufferSize, SalmonFSTestHelper.ENC_EXPORT_THREADS);
        commander.importFiles

		// set the correct worker paths for multithreading
		// commander.getFileImporter().setWorkerPath( '../lib/salmon-fs/salmon/utils/salmon_file_importer_worker.js');
		// commander.getFileExporter().setWorkerPath( '../lib/salmon-fs/salmon/utils/salmon_file_exporter_worker.js');
		
        let hashPreExport = [];
        for(let file of files)
            hashPreExport.push(await SalmonFSTestHelper.getChecksum(file));

        // export files
        let exportOptions = new BatchExportOptions();
        exportOptions.deleteSource = false;
        exportOptions.integrity = true;
        exportOptions.autoRename = autoRenameFile;
        exportOptions.onFailed = async (sfile, ex) => {
            // file failed to import
            console.error(ex);
            console.log("export failed: " + await sfile.getName() + "\n" + ex.stack);
        };
        exportOptions.onProgressChanged = async (taskProgress) => {
            if(!SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                return;
            try {
                console.log( "file exporting: " + await taskProgress.getFile().getName() + ": "
                + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes"  );
            } catch (e) {
                console.error(e);
            }
        };
        let filesExported = await commander.exportFiles(files, dir, exportOptions);
        console.log("Files exported");

        for(let i = 0; i < files.length; i++) {
            let stream = await filesExported[i].getInputStream();
            let hashPostImport = await SalmonCoreTestHelper.getChecksumStream(stream);
            await stream.close();
            expect(hashPostImport).toBe(hashPreExport[i]);
        }

		// close the file commander
        commander.close();
	}
}