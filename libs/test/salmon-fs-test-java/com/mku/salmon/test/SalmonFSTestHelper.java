package com.mku.salmon.test;

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

import com.mku.convert.BitConverter;
import com.mku.file.*;
import com.mku.func.BiConsumer;
import com.mku.salmon.*;
import com.mku.salmon.drive.JavaDrive;
import com.mku.salmon.drive.JavaHttpDrive;
import com.mku.salmon.drive.JavaWSDrive;
import com.mku.salmon.sequence.SalmonFileSequencer;
import com.mku.salmon.sequence.SalmonSequenceSerializer;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.SalmonFileInputStream;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.utils.SalmonFileCommander;
import com.mku.salmon.utils.SalmonFileExporter;
import com.mku.salmon.utils.SalmonFileImporter;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import com.mku.utils.FileSearcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

enum TestMode {
    Local,
    Http,
    WebService
}

public class SalmonFSTestHelper {
    public static TestMode currTestMode;
    // dirs
    static Class<?> driveClassType = null; // drive class type
    static IRealFile TEST_ROOT_DIR; // root dir for testing
    static String TEST_INPUT_DIRNAME = "input";
    static String TEST_OUTPUT_DIRNAME = "output";
    static String TEST_VAULT_DIRNAME = "vault";
    static String TEST_OPER_DIRNAME = "files";
    static String TEST_EXPORT_AUTH_DIRNAME = "auth";
    static String TEST_IMPORT_TINY_FILENAME = "tiny_test.txt";
    static String TEST_IMPORT_SMALL_FILENAME = "small_test.dat";
    static String TEST_IMPORT_MEDIUM_FILENAME = "medium_test.dat";
    static String TEST_IMPORT_LARGE_FILENAME = "large_test.dat";
    static String TEST_IMPORT_HUGE_FILENAME = "huge_test.dat";
    static String TINY_FILE_CONTENTS = "This is a new file created that will be used for testing encryption and decryption.";
    static String TEST_SEQ_DIRNAME = "seq";
    static String TEST_SEQ_FILENAME = "fileseq.xml";
    static String TEST_EXPORT_AUTH_FILENAME = "export.slma";

    // Web service
    static String WS_SERVER_URL = "http://localhost:8080";
    // static WS_SERVER_URL = "https://localhost:8443"; // for testing from the Web browser
    static String WS_TEST_DIRNAME = "ws";
    static JavaWSFile.Credentials credentials = new JavaWSFile.Credentials("user", "password");

    // HTTP server (Read-only)
    static String HTTP_SERVER_URL = "http://localhost";
    static String HTTP_SERVER_VIRTUAL_URL = SalmonFSTestHelper.HTTP_SERVER_URL + "/saltest";
    static String HTTP_TEST_DIRNAME = "httpserv";
    static String HTTP_VAULT_DIRNAME = "vault";
    static String HTTP_VAULT_DIR_URL = SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL
            + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME + "/" + SalmonFSTestHelper.HTTP_VAULT_DIRNAME;
    static String HTTP_VAULT_FILES_DIR_URL = SalmonFSTestHelper.HTTP_VAULT_DIR_URL + "/fs";

    // performance
    static int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    static int ENC_IMPORT_THREADS = 1;
    static int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    static int ENC_EXPORT_THREADS = 1;
    static int TEST_FILE_INPUT_STREAM_THREADS = 1;
    static boolean TEST_USE_FILE_INPUT_STREAM = false;

    // progress
    static boolean ENABLE_FILE_PROGRESS = false;

    // test dirs and files
    static IRealFile TEST_INPUT_DIR;
    static IRealFile TEST_OUTPUT_DIR;
    static IRealFile TEST_IMPORT_TINY_FILE;
    static IRealFile TEST_IMPORT_SMALL_FILE;
    static IRealFile TEST_IMPORT_MEDIUM_FILE;
    static IRealFile TEST_IMPORT_LARGE_FILE;
    static IRealFile TEST_IMPORT_HUGE_FILE;
    static IRealFile TEST_IMPORT_FILE;
    static IRealFile WS_TEST_DIR;
    static IRealFile HTTP_TEST_DIR;
    static IRealFile HTTP_VAULT_DIR;
    static IRealFile TEST_HTTP_TINY_FILE;
    static IRealFile TEST_HTTP_SMALL_FILE;
    static IRealFile TEST_HTTP_MEDIUM_FILE;
    static IRealFile TEST_HTTP_LARGE_FILE;
    static IRealFile TEST_HTTP_HUGE_FILE;
    static IRealFile TEST_HTTP_FILE;
    static IRealFile TEST_SEQ_DIR;
    static IRealFile TEST_EXPORT_AUTH_DIR;
    static SalmonFileImporter fileImporter;
    static SalmonFileExporter fileExporter;
    static SalmonSequenceSerializer sequenceSerializer = new SalmonSequenceSerializer();
    static final Random random = new Random(System.currentTimeMillis());

    public static void setTestParams(String testDir, TestMode testMode) throws Exception {
        currTestMode = testMode;

        if (testMode == TestMode.Local) {
            SalmonFSTestHelper.driveClassType = JavaDrive.class;
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.driveClassType = JavaHttpDrive.class;
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.driveClassType = JavaWSDrive.class;
        }

        SalmonFSTestHelper.TEST_ROOT_DIR = new JavaFile(testDir);
        if (!SalmonFSTestHelper.TEST_ROOT_DIR.exists())
            SalmonFSTestHelper.TEST_ROOT_DIR.mkdir();

        System.out.println("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.getAbsolutePath());

        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if (testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new JavaWSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new JavaHttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
        SalmonFSTestHelper.createTestFiles();
        SalmonFSTestHelper.createHttpFiles();
        SalmonFSTestHelper.createHttpVault();
    }

    public static IRealFile createDir(IRealFile parent, String dirName) {
        IRealFile dir = parent.getChild(dirName);
        if (!dir.exists())
            dir.mkdir();
        return dir;
    }

    static void createTestFiles() throws Exception {
        SalmonFSTestHelper.TEST_IMPORT_TINY_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;

        SalmonFSTestHelper.createFile(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE, 1024 * 1024);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE, 12 * 1024 * 1024);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE, 48 * 1024 * 1024);
//        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE, 512 * 1024 * 1024);
    }


    static void createHttpFiles() throws Exception {
        SalmonFSTestHelper.TEST_HTTP_TINY_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_SMALL_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_LARGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_HUGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_TINY_FILE;

        SalmonFSTestHelper.createFile(SalmonFSTestHelper.TEST_HTTP_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_SMALL_FILE, 1024 * 1024);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE, 12 * 1024 * 1024);
        SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_LARGE_FILE, 48 * 1024 * 1024);
        // SalmonFSTestHelper.createFileRandomData(SalmonFSTestHelper.TEST_HTTP_HUGE_FILE, 512*1024*1024);
    }

    static void createHttpVault() throws Exception {
        IRealFile httpVaultDir = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.HTTP_VAULT_DIRNAME);
        if (httpVaultDir != null && httpVaultDir.exists())
            return;

        httpVaultDir.mkdir();
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();
        SalmonDrive drive = SalmonFSTestHelper.createDrive(httpVaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        IRealFile[] importFiles = new IRealFile[]{SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,
                SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,
                SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE
        };
        SalmonFileImporter importer = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        for (IRealFile importFile : importFiles) {
            importer.importFile(importFile, rootDir, null, false, true, null);
        }
        importer.close();
    }

    public static void createFile(String path, String contents) throws Exception {
        IRealFile file = new JavaFile(path);
        RandomAccessStream stream = file.getOutputStream();
        byte[] data = contents.getBytes();
        stream.write(data, 0, data.length);
        stream.flush();
        stream.close();
    }

    public static void createFile(IRealFile file, String contents) throws Exception {
        RandomAccessStream stream = file.getOutputStream();
        byte[] data = contents.getBytes();
        stream.write(data, 0, data.length);
        stream.flush();
        stream.close();
    }

    public static void createFileRandomData(IRealFile file, long size) throws Exception {
        if (file.exists())
            return;
        byte[] data = new byte[65536];
        RandomAccessStream stream = file.getOutputStream();
        int len = 0;
        while (size > 0) {
            random.nextBytes(data);
            len = (int) Math.min(size, data.length);
            stream.write(data, 0, len);
            size -= len;
        }
        stream.flush();
        stream.close();
    }

    static void initialize() {
        SalmonFSTestHelper.fileImporter = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new SalmonFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    static void close() {
        SalmonFSTestHelper.fileImporter.close();
        SalmonFSTestHelper.fileExporter.close();
    }

    public static SalmonFileSequencer createSalmonFileSequencer() throws IOException {
        // always create the sequencer files locally
        IRealFile seqDir = generateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        IRealFile seqFile = seqDir.getChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
        return new SalmonFileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
    }

    public static IRealFile generateFolder(String name) {
        return generateFolder(name, TEST_OUTPUT_DIR);
    }

    public static IRealFile generateFolder(String name, IRealFile parent) {
        return generateFolder(name, parent, true);
    }

    public static IRealFile generateFolder(String name, IRealFile parent, boolean rand) {
        String dirName = name + (rand ? "_" + System.currentTimeMillis() : "");
        IRealFile dir = parent.getChild(dirName);
        if (!dir.exists())
            dir.mkdir();
        System.out.println("generated folder: " + dir.getAbsolutePath());
        return dir;
    }

    public static String getChecksum(IRealFile realFile) throws NoSuchAlgorithmException, IOException {
        InputStreamWrapper stream = new InputStreamWrapper(realFile.getInputStream());
        return getChecksumStream(stream);
    }

    public static String getChecksumStream(InputStream stream) throws NoSuchAlgorithmException, IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[256 * 1024];
            int bytesRead;
            while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & b));
                while (h.length() < 2)
                    h.insert(0, "0");
                hexString.append(h);
            }
            return hexString.toString();
        } finally {
            if (stream != null)
                stream.close();
        }
    }

    public static void importAndExport(IRealFile vaultDir, String pass, IRealFile importFile,
                                       boolean bitflip, long flipPosition, boolean shouldBeEqual,
                                       boolean applyFileIntegrity, boolean verifyFileIntegrity) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer();
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        rootDir.listFiles();

        IRealFile fileToImport = importFile;
        String hashPreImport = SalmonFSTestHelper.getChecksum(fileToImport);

        // import
        BiConsumer<Long, Long> printImportProgress = (position, length) -> {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                System.out.println("importing file: " + position + "/" + length);
        };
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, rootDir, null, false, applyFileIntegrity, printImportProgress);

        Integer chunkSize = salmonFile.getFileChunkSize();
        if (chunkSize != null && chunkSize > 0 && !verifyFileIntegrity)
            salmonFile.setVerifyIntegrity(false, null);

        assertTrue(salmonFile.exists());
        String hashPostImport = SalmonFSTestHelper.getChecksumStream(new InputStreamWrapper(salmonFile.getInputStream()));
        if (shouldBeEqual)
            assertEquals(hashPreImport, hashPostImport);

        // get fresh copy of the file
        salmonFile = (SalmonFile) rootDir.listFiles()[0];
        assertTrue(salmonFile != null);
        assertTrue(salmonFile.exists());

        IVirtualFile[] salmonFiles = rootDir.listFiles();
        long realFileSize = fileToImport.length();
        for (IVirtualFile file : salmonFiles) {
            if (file.getBaseName().equals(fileToImport.getBaseName())) {
                if (shouldBeEqual) {
                    assertTrue(file.exists());
                    long fileSize = file.getSize();
                    assertEquals(realFileSize, fileSize);
                }
            }
        }

        // export
        BiConsumer<Long, Long> printExportProgress = (position, length) -> {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                System.out.println("exporting file: " + position + "/" + length);
        };
        if (bitflip)
            flipBit(salmonFile, flipPosition);
        Integer chunkSize2 = salmonFile.getFileChunkSize();
        if (chunkSize2 != null && chunkSize2 > 0 && verifyFileIntegrity)
            salmonFile.setVerifyIntegrity(true, null);
        IRealFile exportFile = fileExporter.exportFile(salmonFile, drive.getExportDir(), null, false, verifyFileIntegrity, printExportProgress);

        String hashPostExport = SalmonFSTestHelper.getChecksum(exportFile);
        if (shouldBeEqual)
            assertEquals(hashPreImport, hashPostExport);
    }

    static SalmonDrive openDrive(IRealFile vaultDir, Class<?> driveClassType, String pass) throws IOException {
        return SalmonDrive.openDrive(vaultDir, driveClassType, pass, null);
    }

    static SalmonDrive openDrive(IRealFile vaultDir, Class<?> driveClassType, String pass, SalmonFileSequencer sequencer) throws IOException {
        if (driveClassType == JavaWSDrive.class) {
            // use the remote service instead
            return JavaWSDrive.open(vaultDir, pass, sequencer);
        } else
            return SalmonDrive.openDrive(vaultDir, driveClassType, pass, sequencer);
    }

    static SalmonDrive createDrive(IRealFile vaultDir, Class<?> driveClassType, String pass, SalmonFileSequencer sequencer) throws IOException {
        if (driveClassType == JavaWSDrive.class)
            return JavaWSDrive.create(vaultDir, pass, sequencer);
        else
            return SalmonDrive.createDrive(vaultDir, driveClassType, pass, sequencer);
    }

    public static void importAndSearch(IRealFile vaultDir, String pass, IRealFile importFile) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer();
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        IRealFile fileToImport = importFile;
        String rbasename = fileToImport.getBaseName();

        // import
        IVirtualFile salmonFile = fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        assertNotNull(salmonFile);
        assertTrue(salmonFile.exists());

        // search
        String basename = salmonFile.getBaseName();
        FileSearcher searcher = new FileSearcher();
        IVirtualFile[] files = searcher.search(rootDir, basename, true, null, null);
        assertTrue(files.length > 0);
        assertEquals(files[0].getBaseName(), basename);

    }

    public static void importAndCopy(IRealFile vaultDir, String pass, IRealFile importFile,
                                     int importBufferSize, int importThreads, String newDir, boolean move) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer();
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        rootDir.listFiles();
        IRealFile fileToImport = importFile;
        String rbasename = fileToImport.getBaseName();

        // import
        IVirtualFile salmonFile = fileImporter.importFile(fileToImport, rootDir, null, false,
                false, null);

        // trigger the cache to add the filename
        String basename = salmonFile.getBaseName();

        assertNotNull(salmonFile);

        assertTrue(salmonFile.exists());

        String checkSumBefore = getChecksum(salmonFile.getRealFile());
        IVirtualFile newDir1 = rootDir.createDirectory(newDir);
        IVirtualFile newFile;
        if (move)
            newFile = salmonFile.move(newDir1, null);
        else
            newFile = salmonFile.copy(newDir1, null);
        assertNotNull(newFile);

        IVirtualFile nNewFile = newDir1.getChild(newFile.getBaseName());
        String checkSumAfter = getChecksum(nNewFile.getRealFile());
        assertEquals(checkSumBefore, checkSumAfter);

        if (!move) {
            IVirtualFile file = rootDir.getChild(fileToImport.getBaseName());
            String checkSumOrigAfter = getChecksum(file.getRealFile());
            assertEquals(checkSumBefore, checkSumOrigAfter);
        }

        assertEquals(salmonFile.getBaseName(), newFile.getBaseName());
    }

    private static void flipBit(IVirtualFile salmonFile, long position) throws Exception {
        RandomAccessStream stream = salmonFile.getRealFile().getOutputStream();
        stream.setPosition(position);
        stream.write(new byte[]{1}, 0, 1);
        stream.flush();
        stream.close();
    }

    public static SalmonFile shouldCreateFileWithoutVault(byte[] testBytes, byte[] key, boolean applyIntegrity, boolean verifyIntegrity, int chunkSize, byte[] hashKey,
                                                          byte[] filenameNonce, byte[] fileNonce, boolean flipBit, int flipPosition, boolean checkData) throws Exception {
        // write file
        IRealFile realDir = SalmonFSTestHelper.generateFolder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        SalmonFile dir = new SalmonFile(realDir, null);
        String filename = "test_" + System.currentTimeMillis() + "." + flipPosition + ".txt";
        SalmonFile newFile = dir.createFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            newFile.setApplyIntegrity(true, hashKey, chunkSize);
        RandomAccessStream stream = newFile.getOutputStream();

        stream.write(testBytes, 0, testBytes.length);
        stream.flush();
        stream.close();
        IRealFile realFile = newFile.getRealFile();

        // tamper
        if (flipBit) {
            IRealFile realTmpFile = newFile.getRealFile();
            RandomAccessStream realStream = realTmpFile.getOutputStream();
            realStream.setPosition(flipPosition);
            realStream.write(new byte[]{0}, 0, 1);
            realStream.flush();
            realStream.close();
        }

        // open file for read
        SalmonFile readFile = new SalmonFile(realFile, null);
        readFile.setEncryptionKey(key);
        readFile.setRequestedNonce(fileNonce);
        if (verifyIntegrity)
            readFile.setVerifyIntegrity(true, hashKey);
        SalmonStream inStream = readFile.getInputStream();
        byte[] textBytes = new byte[testBytes.length];
        inStream.read(textBytes, 0, textBytes.length);
        inStream.close();
        if (checkData)
            assertArrayEquals(testBytes, textBytes);
        return readFile;
    }

    public static void exportAndImportAuth(IRealFile vault, IRealFile importFilePath) throws Exception {
        // emulate 2 different devices with different sequencers
        SalmonFileSequencer sequencer1 = createSalmonFileSequencer();
        SalmonFileSequencer sequencer2 = createSalmonFileSequencer();

        // set to the first sequencer and create the vault
        SalmonDrive drive = createDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        IVirtualFile rootDir = drive.getRoot();
        IRealFile fileToImport = importFilePath;
        SalmonFile salmonFileA1 = (SalmonFile) fileImporter.importFile(fileToImport, rootDir, null, false,
                false, null);
        long nonceA1 = BitConverter.toLong(salmonFileA1.getRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        // open with another device (different sequencer) and export auth id
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        String authId = drive.getAuthId();
        boolean success = false;
        try {
            // import a test file should fail because not authorized
            rootDir = drive.getRoot();
            fileToImport = importFilePath;
            fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
            success = true;
        } catch (Exception ignored) {

        }

        assertFalse(success);
        drive.close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        IRealFile exportAuthDir = generateFolder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR);
        IRealFile exportFile = exportAuthDir.createFile(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        SalmonAuthConfig.exportAuthFile(drive, authId, exportFile);
        IRealFile exportAuthFile = exportAuthDir.getChild(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        SalmonFile salmonCfgFile = new SalmonFile(exportAuthFile, drive);
        long nonceCfg = BitConverter.toLong(salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        rootDir = drive.getRoot();
        fileToImport = importFilePath;
        SalmonFile salmonFileA2 = fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        long nonceA2 = BitConverter.toLong(salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        //reopen with second device(sequencer) and import auth file
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        SalmonAuthConfig.importAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = drive.getRoot();
        fileToImport = importFilePath;
        SalmonFile salmonFileB1 = fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        long nonceB1 = BitConverter.toLong(salmonFileB1.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonFile salmonFileB2 = fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        long nonceB2 = BitConverter.toLong(salmonFileB2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        assertEquals(nonceA1, nonceCfg - 1);
        assertEquals(nonceCfg, nonceA2 - 2);
        assertNotEquals(nonceA2, nonceB1);
        assertEquals(nonceB1, nonceB2 - 2);
    }

    public static void testMaxFiles(IRealFile vaultDir, IRealFile seqFile, IRealFile importFile,
                                    byte[] testMaxNonce, long offset, boolean shouldImport) {
        boolean importSuccess = false;
        try {
            SalmonFileSequencer sequencer = new SalmonFileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer) {
                @Override
                public void initializeSequence(String driveId, String authId, byte[] startNonce, byte[] maxNonce)
                        throws IOException {
                    long nMaxNonce = BitConverter.toLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                    super.initializeSequence(driveId, authId, startNonce, maxNonce);
                }
            };
            SalmonDrive drive;
            try {
                drive = openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            } catch (Exception ex) {
                drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            }
            IVirtualFile rootDir = drive.getRoot();
            rootDir.listFiles();
            IVirtualFile salmonRootDir = drive.getRoot();
            IRealFile fileToImport = importFile;
            IVirtualFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        } catch (Exception ex) {
            if (ex instanceof SalmonRangeExceededException)
                importSuccess = false;
            ex.printStackTrace();
        }

        assertEquals(shouldImport, importSuccess);
    }

    public static void testRawFile() throws IOException {
        String text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IRealFile dir = generateFolder("test");
        String filename = "file.txt";
        IRealFile testFile = dir.createFile(filename);
        byte[] bytes = text.getBytes();

        // write to file
        RandomAccessStream wstream = testFile.getOutputStream();
        int idx = 0;
        while (idx < text.length()) {
            int len = Math.min(BUFF_SIZE, text.length() - idx);
            wstream.write(bytes, idx, len);
            idx += len;
        }
        wstream.flush();
        wstream.close();

        // read a file
        IRealFile writeFile = dir.getChild(filename);
        RandomAccessStream rstream = writeFile.getInputStream();
        byte[] readBuff = new byte[BUFF_SIZE];
        int bytesRead = 0;
        MemoryStream lstream = new MemoryStream();
        while ((bytesRead = rstream.read(readBuff, 0, readBuff.length)) > 0) {
            lstream.write(readBuff, 0, bytesRead);
        }
        byte[] lbytes = lstream.toArray();
        String string = new String(lbytes);
        // console.log(string);
        rstream.close();

        assertEquals(string, text);
    }

    public static void testEncDecFile() throws IOException {
        String text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IRealFile dir = generateFolder("test");
        String filename = "file.dat";
        IRealFile testFile = dir.createFile(filename);
        byte[] bytes = text.getBytes();
        byte[] key = SalmonGenerator.getSecureRandomBytes(32);
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        IRealFile wfile = dir.getChild(filename);
        SalmonFile encFile = new SalmonFile(wfile);
        nonce = SalmonGenerator.getSecureRandomBytes(8);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        RandomAccessStream stream = encFile.getOutputStream();
        int idx = 0;
        while (idx < text.length()) {
            int len = Math.min(BUFF_SIZE, text.length() - idx);
            stream.write(bytes, idx, len);
            idx += len;
        }
        stream.flush();
        stream.close();

        // decrypt an encrypted file
        IRealFile rfile = dir.getChild(filename);
        SalmonFile encFile2 = new SalmonFile(rfile);
        encFile2.setEncryptionKey(key);
        RandomAccessStream stream2 = encFile2.getInputStream();
        byte[] decBuff = new byte[BUFF_SIZE];
        MemoryStream lstream = new MemoryStream();
        int bytesRead = 0;

        while ((bytesRead = stream2.read(decBuff, 0, decBuff.length)) > 0) {
            lstream.write(decBuff, 0, bytesRead);
        }
        byte[] lbytes = lstream.toArray();
        String decString2 = new String(lbytes);
        stream2.close();

        assertEquals(decString2, text);
    }

    public static void encryptAndDecryptStream(byte[] data, byte[] key, byte[] nonce) throws Exception {
        MemoryStream encOutStream = new MemoryStream();
        SalmonStream encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        RandomAccessStream inputStream = new MemoryStream(data);
        inputStream.copyTo(encryptor);
        encOutStream.setPosition(0);
        byte[] encData = encOutStream.toArray();
        encryptor.flush();
        encryptor.close();
        encOutStream.close();
        inputStream.close();

        RandomAccessStream encInputStream = new MemoryStream(encData);
        SalmonStream decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        MemoryStream outStream = new MemoryStream();
        decryptor.copyTo(outStream);
        outStream.setPosition(0);
        byte[] decData = outStream.toArray();
        decryptor.close();
        encInputStream.close();
        outStream.close();

        assertArrayEquals(data, decData);
    }

    public static byte[] getRealFileContents(IRealFile filePath) throws Exception {
        IRealFile file = filePath;
        RandomAccessStream ins = file.getInputStream();
        MemoryStream outs = new MemoryStream();
        ins.copyTo(outs);
        outs.setPosition(0);
        outs.flush();
        outs.close();
        return outs.toArray();
    }

    public static void seekAndReadFileInputStream(byte[] data, SalmonFileInputStream fileInputStream,
                                                  int start, int length, int readOffset, int shouldReadLength) throws IOException {
        byte[] buffer = new byte[length + readOffset];
        fileInputStream.reset();
        fileInputStream.skip(start);
        int bytesRead = fileInputStream.read(buffer, readOffset, length);
        assertEquals(shouldReadLength, bytesRead);
        byte[] tdata = new byte[buffer.length];
        System.arraycopy(data, start, tdata, readOffset, shouldReadLength);
        assertArrayEquals(tdata, buffer);
    }

    public static void shouldTestFileSequencer() throws IOException {
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();

        sequencer.createSequence("AAAA", "AAAA");
        sequencer.initializeSequence("AAAA", "AAAA",
                BitConverter.toBytes(1, 8),
                BitConverter.toBytes(4, 8));
        byte[] nonce = sequencer.nextNonce("AAAA");
        assertEquals(1, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(2, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(3, BitConverter.toLong(nonce, 0, 8));

        boolean caught = false;
        try {
            nonce = sequencer.nextNonce("AAAA");
            assertEquals(5, BitConverter.toLong(nonce, 0, 8));
        } catch (SalmonRangeExceededException ex) {
            System.err.println(ex);
            caught = true;
        }
        assertTrue(caught);
    }

    public static int getChildrenCountRecursively(IRealFile realFile) {
        int count = 1;
        if (realFile.isDirectory()) {
            for (IRealFile child : realFile.listFiles()) {
                count += getChildrenCountRecursively(child);
            }
        }
        return count;
    }

    public static void copyStream(SalmonFileInputStream src, MemoryStream dest) throws IOException {
        int bufferSize = 256 * 1024;
        int bytesRead;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = src.read(buffer, 0, bufferSize)) > 0) {
            dest.write(buffer, 0, bytesRead);
        }
        dest.flush();
    }


    static void shouldReadFile(IRealFile vaultPath, String filename) throws NoSuchAlgorithmException, IOException {
        IRealFile localFile = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(filename);
        String localChkSum = getChecksum(localFile);

        IRealFile vaultDir = vaultPath;
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();
        SalmonDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile root = drive.getRoot();
        IVirtualFile file = root.getChild(filename);
        System.out.println("file size: " + file.getSize());
        System.out.println("file last modified: " + file.getLastDateTimeModified());
        assertTrue(file.exists());

        RandomAccessStream stream = file.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        ms.flush();
        ms.setPosition(0);
        String digest = SalmonFSTestHelper.getChecksumStream(new InputStreamWrapper(ms));
        ms.close();
        stream.close();
        assertEquals(digest, localChkSum);
    }

    static void seekAndReadHttpFile(byte[] data, IVirtualFile file, boolean isEncrypted,
                                    int buffersCount, int bufferSize, int backOffset) throws IOException {
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                0, 32, 0, 32,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                220, 8, 2, 8,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                100, 2, 0, 2,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                6, 16, 0, 16,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                50, 40, 0, 40,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                124, 50, 0, 50,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file, isEncrypted,
                250, 10, 0, 10,
                buffersCount, bufferSize, backOffset);
    }

    // shouldReadLength should be equal to length
    // when checking Http files since the return buffer
    // might give us more data than requested
    static void seekAndReadFileStream(byte[] data, IVirtualFile file, boolean isEncrypted,
                                      int start, int length, int readOffset, int shouldReadLength,
                                      int buffersCount, int bufferSize, int backOffset) throws IOException {
        byte[] buffer = new byte[length + readOffset];

        SalmonFileInputStream stream = null;
        if (SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM && isEncrypted) {
            // multi threaded
            stream = new SalmonFileInputStream((SalmonFile) file, buffersCount, bufferSize, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        } else {
            //TODO: support IRealFile streams
//            RandomAccessStream fileStream;
//            if (isEncrypted) {
//                fileStream =  file.getInputStream();
//            } else {
//                fileStream = new JavaHttpFileStream(file);
//            }
//            stream = new InputStreamWrapper(fileStream);
        }

        stream.skip(start);
        byte[] buff = new byte[length];
        int bytesRead = stream.read(buff, 0, length);
        for (int i = 0; i < bytesRead; i++) {
            buffer[readOffset + i] = buff[i];
        }
        byte[] tdata = new byte[buffer.length];
        for (int i = 0; i < shouldReadLength; i++) {
            tdata[readOffset + i] = data[start + i];
        }
        System.out.println(Arrays.toString(tdata));
        System.out.println(Arrays.toString(buffer));
        stream.close();
        assertArrayEquals(tdata, buffer);
    }

    public static void exportFiles(SalmonFile[] files, IRealFile dir) throws Exception {
        exportFiles(files, dir, 1);
    }

    public static void exportFiles(SalmonFile[] files, IRealFile dir, int threads) throws Exception {
        int bufferSize = 256 * 1024;
        SalmonFileCommander commander = new SalmonFileCommander(bufferSize, bufferSize, threads);

        List<String> hashPreExport = new ArrayList<>();
        for (SalmonFile file : files)
            hashPreExport.add(SalmonFSTestHelper.getChecksumStream(new InputStreamWrapper(file.getInputStream())));

        // export files
        IRealFile[] filesExported = commander.exportFiles(files, dir, false, true,
                (taskProgress) -> {
                    if (!SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                        return;
                    try {
                        System.out.println("file exporting: " + taskProgress.getFile().getBaseName() + ": "
                                + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
                    } catch (Exception e) {
                        System.err.println(e);
                    }
                }, IRealFile.autoRename, (sfile, ex) ->
                {
                    // file failed to import
                    System.err.println(ex);
                    try {
                        System.out.println("export failed: " + sfile.getBaseName() + "\n" + ex);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        System.out.println("Files exported");

        for (int i = 0; i < files.length; i++) {
            RandomAccessStream stream = filesExported[i].getInputStream();
            String hashPostImport = SalmonFSTestHelper.getChecksumStream(new InputStreamWrapper(stream));
            stream.close();
            assertEquals(hashPostImport, hashPreExport.get(i));
        }

        // close the file commander
        commander.close();
    }
}