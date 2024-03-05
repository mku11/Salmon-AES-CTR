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
import { MemoryStream } from '../../lib/salmon-core/io/memory_stream.js';
import { SalmonGenerator } from '../../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../../lib/salmon-core/salmon/salmon_decryptor.js';
import { SalmonStream } from '../../lib/salmon-core/salmon/io/salmon_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/io/encryption_mode.js';
import { ReadableStreamWrapper } from '../../lib/salmon-core/io/readable_stream_wrapper.js';
import { TestHelper } from '../salmon-core/test_helper.js';
import { SalmonTextEncryptor } from '../../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../../lib/salmon-core/salmon/text/salmon_text_decryptor.js';
import { SalmonFile } from '../../lib/salmon-fs/salmonfs/salmon_file.js';
import { SalmonDrive } from '../../lib/salmon-fs/salmonfs/salmon_drive.js';
import { SalmonSequenceSerializer } from '../../lib/salmon-fs/sequence/salmon_sequence_serializer.js';
import { SalmonFileSequencer } from '../../lib/salmon-fs/sequence/salmon_file_sequencer.js';
import { SalmonFileImporter } from '../../lib/salmon-fs/utils/salmon_file_importer.js';
import { SalmonFileExporter } from '../../lib/salmon-fs/utils/salmon_file_exporter.js';
import { SalmonFileSearcher } from '../../lib/salmon-fs/utils/salmon_file_searcher.js';
import { SalmonFileReadableStream } from '../../lib/salmon-fs/salmonfs/salmon_file_readable_stream.js';
import { SalmonDefaultOptions } from '../../lib/salmon-core/salmon/salmon_default_options.js';

export const TestMode = {
    Local: { name: 'Local', ordinal: 0 },
    Node: { name: 'Node', ordinal: 1 },
    Http: { name: 'Http', ordinal: 2 },
}
export async function getFile(fp) { }
export function getFileStream(fl) { }
export function getSequenceSerializer() { return new SalmonSequenceSerializer(); }
export async function setTestMode(testMode) {
    if (testMode == TestMode.Local) {
        const { JsDrive } = await import('../../lib/salmon-fs/file/js_drive.js');
        const { JsFile } = await import('../../lib/salmon-fs/file/js_file.js');
        const { JsFileStream } = await import('../../lib/salmon-fs/file/js_file_stream.js');

        TsFsTestHelper.driveClassType = JsDrive;
        getFile = async (filepath, filename) => {
            if (TsFsTestHelper.TEST_ROOT_DIR_HANDLE == undefined)
                throw new Error("Please select the Test Folder to: " + TsFsTestHelper.TEST_ROOT_DIR);
            if(filepath instanceof FileSystemFileHandle) {
                return new JsFile(filepath);
            } else if(filepath instanceof FileSystemDirectoryHandle && typeof(filename) != 'undefined') {
                let dir = new JsFile(filepath);
                let file = await dir.getChild(filename);
                if(file == null)
                    file = new JsFile(null, dir, filename);
                return file;
            } else if(filepath instanceof JsFile) {
                if(typeof(filename) != 'undefined') {
                    let file = await filepath.getChild(filename);
                    if(file == null)
                        file = new JsFile(null, filepath, filename);
                    return file;
                } else {
                    return filepath;
                }
            } else if(typeof(filepath) == 'string') {
                if(typeof(filename) != 'undefined')
                    filepath += JsFile.separator + filename;
                let file = null;
                let parts = filepath.split(/\/|\\/).filter(i => i != "");
                for (let part of parts) {
                    if (file == null && part != TsFsTestHelper.TEST_ROOT_DIR_HANDLE.name)
                        continue;
                    if (file == null && part == TsFsTestHelper.TEST_ROOT_DIR_HANDLE.name)
                        file = new JsFile(TsFsTestHelper.TEST_ROOT_DIR_HANDLE);
                    else {
                        let child = await file.getChild(part);
                        if(child == null)
                            file = new JsFile(null, file, part);
                        else 
                            file = child;
                    }
                }
                if(file == null)
                    throw new Error("Test folder path does not match");
                return file;
            }
        }
        getFileStream = (filepath) => new JsFileStream(filepath);
    } else if (testMode == TestMode.Node) {
        const { JsNodeDrive } = await import('../../lib/salmon-fs/file/js_node_drive.js');
        const { JsNodeFile } = await import('../../lib/salmon-fs/file/js_node_file.js');
        const { JsNodeFileStream } = await import('../../lib/salmon-fs/file/js_node_file_stream.js');

        TsFsTestHelper.driveClassType = JsNodeDrive;
        getFile = async (filepath, filename) => {
            if(typeof(filename) != 'undefined')
                filepath += JsNodeFile.separator + filename
            return new JsNodeFile(filepath);
        }
        getFileStream = (file) => new JsNodeFileStream(file);
    } else if (testMode == TestMode.Http) {
        const { JsHttpDrive } = await import('../../lib/salmon-fs/file/js_http_drive.js');
        const { JsHttpFile } = await import('../../lib/salmon-fs/file/js_http_file.js');
        const { JsHttpFileStream } = await import('../../lib/salmon-fs/file/js_http_file_stream.js');

        TsFsTestHelper.driveClassType = JsHttpDrive;
        getFile = async (filepath) => {
            if(typeof(filename) != 'undefined')
                filepath += JsHttpFile.separator + filename
            return new JsHttpFile(filepath);
        }
        getFileStream = (file) => new JsHttpFileStream(file);
    }
}

// TODO: ToSync global importer/exporter

export class TsFsTestHelper {
    static driveClassType = null; // drive class type
    static TEST_ROOT_DIR_HANDLE; // for browser local file system testing
    static TEST_ROOT_DIR = "d:\\tmp\\";
    static TEST_OUTPUT_DIR = TsFsTestHelper.TEST_ROOT_DIR + "output\\";
    static TEST_VAULT_DIR = TsFsTestHelper.TEST_OUTPUT_DIR + "enc";
    static TEST_VAULT2_DIR = TsFsTestHelper.TEST_OUTPUT_DIR + "enc2";
    static TEST_EXPORT_AUTH_DIR = TsFsTestHelper.TEST_OUTPUT_DIR + "export\\";
    static TEST_DATA_DIR_FOLDER = TsFsTestHelper.TEST_ROOT_DIR + "testdata\\";
    static TEST_IMPORT_TINY_FILE = TsFsTestHelper.TEST_DATA_DIR_FOLDER + "tiny_test.txt";
    static TEST_IMPORT_SMALL_FILE = TsFsTestHelper.TEST_DATA_DIR_FOLDER + "small_test.zip";
    static TEST_IMPORT_MEDIUM_FILE = TsFsTestHelper.TEST_DATA_DIR_FOLDER + "medium_test.zip";
    static TEST_IMPORT_LARGE_FILE = TsFsTestHelper.TEST_DATA_DIR_FOLDER + "large_test.mp4";
    static TEST_IMPORT_HUGE_FILE = TsFsTestHelper.TEST_DATA_DIR_FOLDER + "huge.zip";
    static TEST_IMPORT_FILE = TsFsTestHelper.TEST_IMPORT_SMALL_FILE;

    static TEST_SEQUENCER_DIR = TsFsTestHelper.TEST_OUTPUT_DIR;
    static TEST_SEQUENCER_FILENAME = "fileseq.json";
    
    static TEST_EXPORT_FILENAME = "export.slma";

    static ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    static ENC_IMPORT_THREADS = 1;
    static ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    static ENC_EXPORT_THREADS = 1;

    static TEST_FILE_INPUT_STREAM_THREADS = 1;
    static TEST_USE_FILE_INPUT_STREAM = false;

    static ENABLE_LOG = true;
    static ENABLE_LOG_DETAILS = true;
    static ENABLE_FILE_PROGRESS = false;

    static TEST_SEQUENCER_FILE1 = "seq1.json";
    static TEST_SEQUENCER_FILE2 = "seq2.json";

    static TEST_HTTP_TINY_FILE = "tiny_test.txt";
    static TEST_HTTP_SMALL_FILE = "small_test.zip";
    static TEST_HTTP_MEDIUM_FILE = "medium_test.zip";
    static TEST_HTTP_LARGE_FILE = "large_test.mp4";
    static TEST_HTTP_FILE = TsFsTestHelper.TEST_HTTP_MEDIUM_FILE;

    static TEST_HTTP_TINY_FILE_SIZE = 27;
    static TEST_HTTP_TINY_FILE_CONTENTS = "This is a new file created.";
    static TEST_HTTP_TINY_FILE_CHKSUM = "69470e3c51279c8493be3f2e116a27ef620b3791cd51b27f924c589cb014eb92";

    static TEST_HTTP_SMALL_FILE_SIZE = 1814885;
    static TEST_HTTP_SMALL_FILE_CHKSUM = "c3a0ef1598711e35ba2ba54d60d3722ebe0369ad039df324391ff39263edabd4";


    static TEST_HTTP_LARGE_FILE_SIZE = 43315070;
    static TEST_HTTP_LARGE_FILE_CHKSUM = "3aaecd80a8fa3cbe6df8e79364af0412b7da6fa423d14c8c6bd332b32d7626b7";

    static TEST_HTTP_DATA256_FILE = "data256.dat";
    static TEST_HTTP_ENCDATA256_FILE = "encdata256.dat";
    static TEST_ENC_HTTP_FILE = "encfile.dat";

    static SERVER_URL = "http://localhost";
    static SERVER_TEST_URL = TsFsTestHelper.SERVER_URL + "/saltest/test";
    static SERVER_TEST_DATA_URL = TsFsTestHelper.SERVER_URL + "/saltest/test/data";
    static VAULT_DIR_URL = TsFsTestHelper.SERVER_TEST_DATA_URL + "/vault";

    static TEST_FILE = TsFsTestHelper.SERVER_TEST_DATA_URL + "/" + TsFsTestHelper.TEST_ENC_HTTP_FILE;
    static fileImporter;
    static fileExporter;

    static setTestDirHandle(testDirHandle) {
        TsFsTestHelper.TEST_ROOT_DIR_HANDLE = testDirHandle;
    }

    static initialize() {
        // TODO: ToSync global importer/exporter
        TsFsTestHelper.fileImporter = new SalmonFileImporter(TsFsTestHelper.ENC_IMPORT_BUFFER_SIZE, TsFsTestHelper.ENC_IMPORT_THREADS);
        TsFsTestHelper.fileExporter = new SalmonFileExporter(TsFsTestHelper.ENC_EXPORT_BUFFER_SIZE, TsFsTestHelper.ENC_EXPORT_THREADS);
    }

    static close() {
        TsFsTestHelper.fileImporter.close();
        TsFsTestHelper.fileExporter.close();
    }

    static async generateFolder(dirPath, name) {
        let time = Date.now();
        let dir;
        if(typeof(dirPath)=='string' && typeof(name)=='string')
            dir = await getFile(dirPath + "/" + name + "_" + time);
        else if(typeof(dirPath)=='string')
            dir = await getFile( dirPath + "_" + time);
        else if (dirPath instanceof FileSystemHandle)
            dir = await getFile(dirPath, name + "_" + time);
        if (!await dir.mkdir())
            throw new Error("Could not create dir");
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

        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, pass);
        let rootDir = await drive.getVirtualRoot();
        await rootDir.listFiles();

        let salmonRootDir = await drive.getVirtualRoot();

        let fileToImport = await getFile(importFile);
        let hashPreImport = await TsFsTestHelper.getChecksum(fileToImport);

        // import
        SalmonFileImporter.setEnableLog(TsFsTestHelper.ENABLE_LOG);
        SalmonFileImporter.setEnableLogDetails(TsFsTestHelper.ENABLE_LOG_DETAILS);
        let printImportProgress = async (position, length) => {
            if (TsFsTestHelper.ENABLE_FILE_PROGRESS)
                console.log("importing file: " + fileToImport.getBaseName() + ": " + position + "/" + length);

        }
        let salmonFile = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, applyFileIntegrity, printImportProgress);

        expect(salmonFile != null).toBeTruthy();
        expect(await salmonFile.exists()).toBeTruthy();
        let salmonFiles = await (await drive.getVirtualRoot()).listFiles();
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
        SalmonFileExporter.setEnableLog(TsFsTestHelper.ENABLE_LOG);
        SalmonFileExporter.setEnableLogDetails(TsFsTestHelper.ENABLE_LOG_DETAILS);
        let printExportProgress = async (position, length) => {
            if (TsFsTestHelper.ENABLE_FILE_PROGRESS)
                console.log("exporting file: " + await salmonFile.getBaseName() + ": " + position + "/" + length);

        }
        if (bitflip)
            await TsFsTestHelper.flipBit(salmonFile, flipPosition);

        let exportFile = await TsFsTestHelper.fileExporter.exportFile(salmonFile, await drive.getExportDir(), null, false, verifyFileIntegrity, printExportProgress);

        let hashPostExport = await TsFsTestHelper.getChecksum(exportFile);
        if (shouldBeEqual) {
            expect(hashPostExport).toBe(hashPreImport);
        }
    }

    static async importAndSearch(vaultDir, pass, importFile) {
        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, pass);
        let rootDir = await drive.getVirtualRoot();
        await rootDir.listFiles();
        let salmonRootDir = await drive.getVirtualRoot();
        let fileToImport = await getFile(importFile);
        let rbasename = fileToImport.getBaseName();

        // import
        let salmonFile = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);

        // trigger the cache to add the filename
        let basename = await salmonFile.getBaseName();

        expect(salmonFile != null).toBeTruthy();
        expect(await salmonFile.exists()).toBeTruthy();

        let searcher = new SalmonFileSearcher();
        let files = await searcher.search(salmonRootDir, basename, true, null, null);

        expect(files.length > 0).toBeTruthy();

        expect(basename).toBe(await files[0].getBaseName());

    }

    static async importAndCopy(vaultDir, pass, importFile, newDir, move) {
        let sequencer = new SalmonFileSequencer(await getFile(vaultDir, TsFsTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        let drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, pass);
        let rootDir = await drive.getVirtualRoot();
        rootDir.listFiles();
        let salmonRootDir = await drive.getVirtualRoot();
        let fileToImport = await getFile(importFile);
        let rbasename = fileToImport.getBaseName();

        // import
        let salmonFile = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);

        // trigger the cache to add the filename
        let basename = await salmonFile.getBaseName();

        expect(salmonFile != null).toBeTruthy();

        expect(await salmonFile.exists()).toBeTruthy();

        let checkSumBefore = await TsFsTestHelper.getChecksum(salmonFile.getRealFile());
        let newDir1 = await salmonRootDir.createDirectory(newDir);
        let newFile;
        if (move)
            newFile = await salmonFile.move(newDir1, null);
        else
            newFile = await salmonFile.copy(newDir1, null);

        expect(newFile != null).toBeTruthy();
        let checkSumAfter = await TsFsTestHelper.getChecksum(await newFile.getRealFile());

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
        filenameNonce, fileNonce, outputDir, flipBit, flipPosition, checkData) {
        // write file
        let realDir = await getFile(outputDir);
        let dir = new SalmonFile(realDir, null);
        let filename = "test_" + Date.now() + ".txt";
        let newFile = await dir.createFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            await newFile.setApplyIntegrity(true, hashKey, chunkSize);
        let stream = await newFile.getOutputStream();

        await stream.write(testBytes, 0, testBytes.length);
        await stream.flush();
        await stream.close();
        let realFilePath = await newFile.getRealFile().getAbsolutePath();

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
        let realFile = await getFile(realFilePath);
        let readFile = new SalmonFile(realFile, null);
        readFile.setEncryptionKey(key);
        readFile.setRequestedNonce(fileNonce);
        if (verifyIntegrity)
            await readFile.setVerifyIntegrity(true, hashKey);
        let inStream = await readFile.getInputStream();
        let textBytes = new Uint8Array(testBytes.length);
        await inStream.read(textBytes, 0, textBytes.length);
        await inStream.close();
        if (checkData)
            TestHelper.assertArrayEquals(testBytes, textBytes);
        return readFile;
    }

    static async exportAndImportAuth(vault, importFilePath) {
        let seqFile1 = await getFile(vault, TsFsTestHelper.TEST_SEQUENCER_FILE1);
        let seqFile2 = await getFile(vault, TsFsTestHelper.TEST_SEQUENCER_FILE2);

        // emulate 2 different devices with different sequencers
        let sequencer1 = new SalmonFileSequencer(seqFile1, getSequenceSerializer());
        let sequencer2 = new SalmonFileSequencer(seqFile2, getSequenceSerializer());

        // set to the first sequencer and create the vault
        let drive = await SalmonDrive.createDrive(vault, TsFsTestHelper.driveClassType, sequencer1, TestHelper.TEST_PASSWORD);
        // import a test file
        let salmonRootDir = await drive.getVirtualRoot();
        let fileToImport = await getFile(importFilePath);
        let salmonFileA1 = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        let nonceA1 = BitConverter.toLong(salmonFileA1.getRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.lock();

        // open with another device (different sequencer) and export auth id
        drive = await SalmonDrive.openDrive(vault, TsFsTestHelper.driveClassType, sequencer2);
        await drive.unlock(TestHelper.TEST_PASSWORD);
        let authID = await drive.getAuthID();
        let success = false;
        try {
            // import a test file should fail because not authorized
            salmonRootDir = await drive.getVirtualRoot();
            fileToImport = await getFile(importFilePath);
            await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            success = true;
        } catch (ignored) { }

        expect(success).toBeFalsy();
        drive.lock();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = await SalmonDrive.openDrive(vault, TsFsTestHelper.driveClassType, sequencer1);
        await drive.unlock(TestHelper.TEST_PASSWORD);
        await drive.exportAuthFile(authID, vault, TsFsTestHelper.TEST_EXPORT_FILENAME);
        let exportAuthFile = await getFile(vault, TsFsTestHelper.TEST_EXPORT_FILENAME);
        let salmonCfgFile = new SalmonFile(exportAuthFile, drive);
        let nonceCfg = BitConverter.toLong(await salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        salmonRootDir = await drive.getVirtualRoot();
        fileToImport = await getFile(importFilePath);
        let salmonFileA2 = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        let nonceA2 = BitConverter.toLong(await salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.lock();

        //reopen with second device(sequencer) and import auth file
        drive = await SalmonDrive.openDrive(vault, TsFsTestHelper.driveClassType, sequencer2);
        await drive.unlock(TestHelper.TEST_PASSWORD);
        await drive.importAuthFile(exportAuthFile);
        // now import a 3rd file
        salmonRootDir = await drive.getVirtualRoot();
        fileToImport = await getFile(importFilePath);
        let salmonFileB1 = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        let nonceB1 = BitConverter.toLong(await salmonFileB1.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        let salmonFileB2 = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        let nonceB2 = BitConverter.toLong(await salmonFileB2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.lock();

        expect(nonceCfg - 1).toBe(nonceA1);
        expect(nonceA2 - 2).toBe(nonceCfg);
        expect(nonceB1).not.toBe(nonceA2);
        expect(nonceB2 - 2).toBe(nonceB1);
    }

    static async testMaxFiles(vaultDir, seqFile, importFile, testMaxNonce, offset, shouldImport) {
        let importSuccess;
        try {
            class TestSalmonFileSequencer extends SalmonFileSequencer {
                async initSequence(driveID, authID, startNonce, maxNonce) {
                    let nMaxNonce = BitConverter.toLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                    await super.initSequence(driveID, authID, startNonce, maxNonce);
                }
            }
            let sequencer = new TestSalmonFileSequencer(seqFile, getSequenceSerializer());
            let drive;
            try {
                drive = await SalmonDrive.openDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer);
                await drive.unlock(TestHelper.TEST_PASSWORD);
            } catch (ex) {
                drive = await SalmonDrive.createDrive(vaultDir, TsFsTestHelper.driveClassType, sequencer, TestHelper.TEST_PASSWORD);
            }
            let rootDir = await drive.getVirtualRoot();
            await rootDir.listFiles();
            let salmonRootDir = await drive.getVirtualRoot();
            let fileToImport = await getFile(importFile);
            let salmonFile = await TsFsTestHelper.fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        } catch (ex) {
            console.error(ex);
            importSuccess = false;
        }

        expect(importSuccess).toBe(shouldImport);
    }

    static async testExamples() {
        let text = "This is a plaintext that will be used for testing";
        let testFile = "D:/tmp/file.txt";
        let tFile = await getFile(testFile);
        if (await tFile.exists())
            await tFile.delete();
        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        let encryptor = new SalmonEncryptor(1); // use more threads for parallel processing
        let decryptor = new SalmonDecryptor(1);
        let encBytes = await encryptor.encrypt(bytes, key, nonce, false);
        // decrypt byte array
        let decBytes = await decryptor.decrypt(encBytes, key, nonce, false);
        TestHelper.assertArrayEquals(bytes, decBytes);
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
        let decBuffer = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() before Read()
        let bytesRead = await decryptStream.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        let decString = new TextDecoder().decode(decBuffer.slice(0, bytesRead));
        console.log(decString);
        await decryptStream.close();
        await encInputStream.close();

        expect(decString).toBe(text);

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
        let encFile = new SalmonFile(await getFile(testFile), null);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        let stream = await encFile.getOutputStream();
        // encrypt data and write with a single call
        await stream.write(bytes, 0, bytes.length);
        await stream.flush();
        await stream.close();
        // decrypt an encrypted file
        let encFile2 = new SalmonFile(await getFile(testFile), null);
        encFile2.setEncryptionKey(key);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        let encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        let decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
        console.log(decString2);
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

        TestHelper.assertArrayEquals(data, decData);
    }

    static async getRealFileContents(filePath) {
        let file = await getFile(filePath);
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
        await reader.reset();
        await reader.skip(start);
        let buff = await reader.read();
        if (buff.value == undefined)
            throw new Error("Could not read from stream");
        for (let i = 0; i < length; i++) {
            buffer[readOffset + i] = buff.value[i];
        }
        let tdata = new Uint8Array(buffer.length);
        for (let i = 0; i < shouldReadLength; i++)
            tdata[readOffset + i] = data[start + i];
        TestHelper.assertArrayEquals(tdata, buffer);
    }

    static async shouldTestFileSequencer() {
        let file = await getFile(TsFsTestHelper.TEST_SEQUENCER_DIR + "\\" + TsFsTestHelper.TEST_SEQUENCER_FILENAME);
        if (await file.exists())
            await file.delete();
        let sequencer = new SalmonFileSequencer(file, getSequenceSerializer());
        await sequencer.initialize();

        await sequencer.createSequence("AAAA", "AAAA");
        await sequencer.initSequence("AAAA", "AAAA",
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
                count += await TsFsTestHelper.getChildrenCountRecursively(child);
            }
        }
        return count;
    }

    static async copyStream(src, dest) {
        let bufferSize = SalmonDefaultOptions.getBufferSize();
        let bytesRead;
        let buffer = new Uint8Array(bufferSize);
        while ((bytesRead = await src.read(buffer, 0, bufferSize)) > 0) {
            await dest.write(buffer, 0, bytesRead);
        }
        await dest.flush();
    }

    static async copyReadableStream(src, dest) {
        let bufferSize = SalmonDefaultOptions.getBufferSize();
        let bytesRead;
        let buffer = new Uint8Array(bufferSize);
        let reader = src.getReader();
        while ((bytesRead = await reader.read()) > 0) {
            await dest.write(buffer, 0, bytesRead);
        }
        await dest.flush();
    }


    static async testHttpReadonlyExamples() {

        let text = "This is a plaintext that will be used for testing";
        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        let encryptor = new SalmonEncryptor(1); // use 1 thread
        let decryptor = new SalmonDecryptor(1);
        let encBytes = await encryptor.encrypt(bytes, key, nonce, false);
        // decrypt byte array
        let decBytes = await decryptor.decrypt(encBytes, key, nonce, false);
        TestHelper.assertArrayEquals(decBytes, bytes);
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
        let decBuffer = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() before Read()
        let bytesRead = await decryptStream.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        let decString = new TextDecoder().decode(decBuffer.slice(0, bytesRead));
        await decryptStream.close();
        await encInputStream.close();
        expect(decString).toBe(text);

        // Example 4: decrypt a file from an HTTP URL (readonly)
        let httpText = "This is a file with some contents";
        let httpKey = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256-bit key
        let httpKeyBytes = new TextEncoder().encode(httpKey);
        let encFile2 = new SalmonFile(await getFile(TsFsTestHelper.TEST_FILE), null);
        encFile2.setEncryptionKey(httpKeyBytes);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        let encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        let decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
        await stream2.close();
        expect(decString2).toBe(httpText);

        // Example 5: or decrypt a file from an HTTP URL drive (readonly)
        let vaultDir = await getFile(TsFsTestHelper.VAULT_DIR_URL);
        let drive = await SalmonDrive.openDrive(vaultDir, TsFsTestHelper.driveClassType);
        await drive.unlock(TestHelper.TEST_PASSWORD);
        let virtualRoot = await drive.getVirtualRoot();
        let files = await virtualRoot.listFiles();
        console.log("Listing files in HTTP drive:\n");
        for (let i = 0; i < files.length; i++)
            console.log(await files[i].getBaseName() + "\n");
        console.log("\n");
        encFile2 = await virtualRoot.getChild(TsFsTestHelper.TEST_HTTP_TINY_FILE);
        stream2 = await encFile2.getInputStream();
        decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
        await stream2.close();
        expect(decString2).toBe(httpText);
    }

    static async shouldReadFile(urlPath, fileLength, fileContents, chksum) {
        let file = await getFile(urlPath);
        expect(await file.exists()).toBeTruthy();
        let length = await file.length();
        expect(length).toBe(fileLength);
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        let start = Date.now();
        await stream.copyTo(ms);
        let end = Date.now();
        await ms.flush();
        await ms.setPosition(0);
        let byteContents = ms.toArray();
        expect(byteContents.length).toBe(fileLength);
        await ms.close();
        await stream.close();
        let digest = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));
        expect(digest).toBe(chksum);

        if (fileContents != null) {
            let contents = new TextDecoder().decode(byteContents);
            expect(contents).toBe(fileContents);
        }
    }

    static async seekAndReadFile(data, file, isEncrypted = false,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {

        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            0, 32, 0, 32,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            220, 8, 2, 8,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            100, 2, 0, 2,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            6, 16, 0, 16,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            50, 40, 0, 40,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            124, 50, 0, 50,
            buffersCount, bufferSize, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, isEncrypted,
            250, 10, 0, 6,
            buffersCount, bufferSize, backOffset);
    }

    static async seekAndReadFileStream(data, file, isEncrypted = false,
        start, length, readOffset, shouldReadLength,
        buffersCount = 0, bufferSize = 0, backOffset = 0) {
        let buffer = new Uint8Array(length + readOffset);

        let stream = null;
        if (TsFsTestHelper.TEST_USE_FILE_INPUT_STREAM && isEncrypted) {
            // multi threaded
            stream = new SalmonFileReadableStream(file, buffersCount, bufferSize, TsFsTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        } else {
            let fileStream;
            if (isEncrypted) {
                fileStream = await file.getInputStream();
            } else {
                fileStream = getFileStream(file);
            }
            stream = new ReadableStreamWrapper(fileStream);
        }
        let reader = await stream.getReader();
        await stream.getReader().skip(start);
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
        stream.cancel();
        TestHelper.assertArrayEquals(tdata, buffer);
    }
}