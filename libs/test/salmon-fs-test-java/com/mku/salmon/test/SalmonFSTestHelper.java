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
import com.mku.fs.drive.utils.FileCommander;
import com.mku.fs.drive.utils.FileExporter;
import com.mku.fs.drive.utils.FileImporter;
import com.mku.fs.drive.utils.FileSearcher;
import com.mku.fs.file.*;
import com.mku.func.BiConsumer;
import com.mku.salmon.Generator;
import com.mku.salmon.RangeExceededException;
import com.mku.salmon.sequence.INonceSequenceSerializer;
import com.mku.salmon.sequence.SequenceSerializer;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmonfs.auth.AuthConfig;
import com.mku.salmonfs.drive.AesDrive;
import com.mku.salmonfs.drive.Drive;
import com.mku.salmonfs.drive.HttpDrive;
import com.mku.salmonfs.drive.WSDrive;
import com.mku.salmonfs.drive.utils.AesFileCommander;
import com.mku.salmonfs.drive.utils.AesFileExporter;
import com.mku.salmonfs.drive.utils.AesFileImporter;
import com.mku.salmonfs.file.AesFile;
import com.mku.salmonfs.sequence.FileSequencer;
import com.mku.salmonfs.streams.AesFileInputStream;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

enum TestMode {
    Local,
    Http,
    WebService
}

public class SalmonFSTestHelper {
    public static TestMode currTestMode;
    // dirs
    public static Class<?> driveClassType = null; // drive class type
    static IFile TEST_ROOT_DIR; // root dir for testing
    static String TEST_INPUT_DIRNAME = "input";
    static String TEST_OUTPUT_DIRNAME = "output";
    static String TEST_VAULT_DIRNAME = "vault";
    static String TEST_OPER_DIRNAME = "files";
    static String TEST_EXPORT_AUTH_DIRNAME = "auth";
    static String TEST_EXPORT_DIRNAME = "export";
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
    static String WS_SERVER_DEFAULT_URL = "http://localhost:8080";
    // static WS_SERVER_DEFAULT_URL = "https://localhost:8443"; // for testing from the Web browser
    static String WS_SERVER_URL = System.getProperty("WS_SERVER_URL") != null && !System.getProperty("WS_SERVER_URL").equals("") ?
            System.getProperty("WS_SERVER_URL") : WS_SERVER_DEFAULT_URL;
    static String WS_TEST_DIRNAME = "ws";
    static WSFile.Credentials credentials = new WSFile.Credentials("user", "password");

    // HTTP server (Read-only)
//    static String HTTP_SERVER_DEFAULT_URL = "http://localhost:8000";
    static String HTTP_SERVER_DEFAULT_URL = "http://localhost";
    static String HTTP_SERVER_URL = System.getProperty("HTTP_SERVER_URL") != null && !System.getProperty("HTTP_SERVER_URL").equals("") ?
            System.getProperty("HTTP_SERVER_URL") : HTTP_SERVER_DEFAULT_URL;
    static String HTTP_SERVER_VIRTUAL_URL = SalmonFSTestHelper.HTTP_SERVER_URL + "/test";
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
    public static boolean ENABLE_FILE_PROGRESS = false;

    // test dirs and files
    static IFile TEST_INPUT_DIR;
    static IFile TEST_OUTPUT_DIR;
    static IFile TEST_IMPORT_TINY_FILE;
    static IFile TEST_IMPORT_SMALL_FILE;
    static IFile TEST_IMPORT_MEDIUM_FILE;
    static IFile TEST_IMPORT_LARGE_FILE;
    static IFile TEST_IMPORT_HUGE_FILE;
    static IFile TEST_IMPORT_FILE;
    static IFile WS_TEST_DIR;
    static IFile HTTP_TEST_DIR;
    static IFile HTTP_VAULT_DIR;
    static IFile TEST_HTTP_TINY_FILE;
    static IFile TEST_HTTP_SMALL_FILE;
    static IFile TEST_HTTP_MEDIUM_FILE;
    static IFile TEST_HTTP_LARGE_FILE;
    static IFile TEST_HTTP_HUGE_FILE;
    static IFile TEST_HTTP_FILE;
    static IFile TEST_SEQ_DIR;
    static IFile TEST_EXPORT_AUTH_DIR;
    static IFile TEST_EXPORT_DIR;
    static AesFileImporter fileImporter;
    static AesFileExporter fileExporter;
    public static INonceSequenceSerializer sequenceSerializer = new SequenceSerializer();
    static final Random random = new Random(0); // seed with zero for predictable results

    public static void setTestParams(String testDir, TestMode testMode) throws Exception {
        currTestMode = testMode;

        if (testMode == TestMode.Local) {
            SalmonFSTestHelper.driveClassType = Drive.class;
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.driveClassType = HttpDrive.class;
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.driveClassType = WSDrive.class;
        }

        SalmonFSTestHelper.TEST_ROOT_DIR = new File(testDir);
        if (!SalmonFSTestHelper.TEST_ROOT_DIR.exists())
            SalmonFSTestHelper.TEST_ROOT_DIR.mkdir();

        System.out.println("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.getDisplayPath());

        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if (testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new WSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
        SalmonFSTestHelper.createTestFiles();
        SalmonFSTestHelper.createHttpFiles();
        SalmonFSTestHelper.createHttpVault();
    }

    public static IFile createDir(IFile parent, String dirName) {
        IFile dir = parent.getChild(dirName);
        if (dir == null || !dir.exists())
            dir = parent.createDirectory(dirName);
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
        IFile httpVaultDir = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.HTTP_VAULT_DIRNAME);
        if (httpVaultDir != null && httpVaultDir.exists())
            return;

        httpVaultDir.mkdir();
        FileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.createDrive(httpVaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        IFile[] importFiles = new IFile[]{SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,
                SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,
                SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE
        };
        AesFileImporter importer = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        AesFileImporter.FileImportOptions importOptions = new AesFileImporter.FileImportOptions();
        importOptions.integrity = true;
        for (IFile importFile : importFiles) {
            importer.importFile(importFile, rootDir, importOptions);
        }
        importer.close();
    }

    public static void createFile(IFile file, String contents) throws Exception {
        if (file.exists())
            return;
        RandomAccessStream stream = file.getOutputStream();
        byte[] data = contents.getBytes();
        stream.write(data, 0, data.length);
        stream.flush();
        stream.close();
    }

    public static void createFileRandomData(IFile file, long size) throws Exception {
        if (file.exists())
            return;
        byte[] data = new byte[65536];
        RandomAccessStream stream = file.getOutputStream();
        int len = 0;
        Random rand = new Random(0); // seed with zero for predictable results
        while (size > 0) {
            rand.nextBytes(data);
            len = (int) Math.min(size, data.length);
            stream.write(data, 0, len);
            size -= len;
        }
        stream.flush();
        stream.close();
    }

    static void initialize() {
        SalmonFSTestHelper.fileImporter = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new AesFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    static void close() {
        if (SalmonFSTestHelper.fileImporter != null)
            SalmonFSTestHelper.fileImporter.close();
        if (SalmonFSTestHelper.fileExporter != null)
            SalmonFSTestHelper.fileExporter.close();
    }

    public static FileSequencer createSalmonFileSequencer() throws IOException {
        // always create the sequencer files locally
        IFile seqDir = generateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        IFile seqFile = seqDir.getChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
        return new FileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
    }

    public static IFile generateFolder(String name) {
        return generateFolder(name, TEST_OUTPUT_DIR);
    }

    public static IFile generateFolder(String name, IFile parent) {
        return generateFolder(name, parent, true);
    }

    public static IFile generateFolder(String name, IFile parent, boolean rand) {
        String dirName = name + (rand ? "_" + System.currentTimeMillis() : "");
        IFile dir = parent.getChild(dirName);
        if (!dir.exists())
            dir = parent.createDirectory(dirName);
        System.out.println("generated folder: " + dir.getDisplayPath());
        return dir;
    }

    public static String getChecksum(IFile realFile) throws NoSuchAlgorithmException, IOException {
        InputStream stream = realFile.getInputStream().asReadStream();
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
            String hexString = BitConverter.toHex(digest);
            return hexString;
        } finally {
            if (stream != null)
                stream.close();
        }
    }

    public static void importAndExport(IFile vaultDir, String pass, IFile importFile,
                                       boolean bitflip, long flipPosition, boolean shouldBeEqual,
                                       boolean applyFileIntegrity, boolean verifyFileIntegrity) throws Exception {
        FileSequencer sequencer = createSalmonFileSequencer();
        AesDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        rootDir.listFiles();

        IFile fileToImport = importFile;
        String hashPreImport = SalmonFSTestHelper.getChecksum(fileToImport);

        // import
        BiConsumer<Long, Long> printImportProgress = (position, length) -> {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                System.out.println("importing file: " + position + "/" + length);
        };
        FileImporter.FileImportOptions importOptions = new FileImporter.FileImportOptions();
        importOptions.integrity = applyFileIntegrity;
        importOptions.onProgressChanged = printImportProgress;
        AesFile aesFile = fileImporter.importFile(fileToImport, rootDir, importOptions);

        int chunkSize = aesFile.getFileChunkSize();
        if (chunkSize == 0 || !verifyFileIntegrity)
            aesFile.setVerifyIntegrity(false);
        else
            aesFile.setVerifyIntegrity(true);

        assertTrue(aesFile.exists());
        String hashPostImport = SalmonFSTestHelper.getChecksumStream(aesFile.getInputStream().asReadStream());
        if (shouldBeEqual)
            assertEquals(hashPreImport, hashPostImport);

        // get fresh copy of the file
        aesFile = (AesFile) rootDir.listFiles()[0];
        assertTrue(aesFile != null);
        assertTrue(aesFile.exists());

        IVirtualFile[] salmonFiles = rootDir.listFiles();
        long realFileSize = fileToImport.getLength();
        for (IVirtualFile file : salmonFiles) {
            if (file.getName().equals(fileToImport.getName())) {
                if (shouldBeEqual) {
                    assertTrue(file.exists());
                    long fileSize = file.getLength();
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
            flipBit(aesFile, flipPosition);
        int chunkSize2 = aesFile.getFileChunkSize();
        if (chunkSize2 > 0 && verifyFileIntegrity)
            aesFile.setVerifyIntegrity(true);
        else
            aesFile.setVerifyIntegrity(false);
        FileExporter.FileExportOptions exportOptions = new FileExporter.FileExportOptions();
        exportOptions.integrity = verifyFileIntegrity;
        exportOptions.onProgressChanged = printExportProgress;

        IFile exportDir = SalmonFSTestHelper.generateFolder("export", SalmonFSTestHelper.TEST_EXPORT_DIR, false);
        IFile exportFile = fileExporter.exportFile(aesFile, exportDir, exportOptions);
        String hashPostExport = SalmonFSTestHelper.getChecksum(exportFile);
        if (shouldBeEqual)
            assertEquals(hashPreImport, hashPostExport);
    }

    static AesDrive openDrive(IFile vaultDir, Class<?> driveClassType, String pass) throws IOException {
        return AesDrive.openDrive(vaultDir, driveClassType, pass, null);
    }

    static AesDrive openDrive(IFile vaultDir, Class<?> driveClassType, String pass, FileSequencer sequencer) throws IOException {
        if (driveClassType == WSDrive.class) {
            // use the remote service instead
            return WSDrive.open(vaultDir, pass, sequencer);
        } else
            return AesDrive.openDrive(vaultDir, driveClassType, pass, sequencer);
    }

    static AesDrive createDrive(IFile vaultDir, Class<?> driveClassType, String pass, FileSequencer sequencer) throws IOException {
        if (driveClassType == WSDrive.class)
            return WSDrive.create(vaultDir, pass, sequencer);
        else
            return AesDrive.createDrive(vaultDir, driveClassType, pass, sequencer);
    }

    public static void importAndSearch(IFile vaultDir, String pass, IFile importFile) throws Exception {
        FileSequencer sequencer = createSalmonFileSequencer();
        AesDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        IFile fileToImport = importFile;
        String rbasename = fileToImport.getName();

        // import
        IVirtualFile salmonFile = fileImporter.importFile(fileToImport, rootDir);
        assertNotNull(salmonFile);
        assertTrue(salmonFile.exists());

        // search
        String basename = salmonFile.getName();
        FileSearcher searcher = new FileSearcher();
        FileSearcher.SearchOptions searchOptions = new FileSearcher.SearchOptions();
        searchOptions.anyTerm = true;
        IVirtualFile[] files = searcher.search(rootDir, basename, searchOptions);
        assertTrue(files.length > 0);
        assertEquals(files[0].getName(), basename);

    }

    public static void importAndCopy(IFile vaultDir, String pass, IFile importFile,
                                     int importBufferSize, int importThreads, String newDir, boolean move) throws Exception {
        FileSequencer sequencer = createSalmonFileSequencer();
        AesDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        rootDir.listFiles();
        IFile fileToImport = importFile;
        String rbasename = fileToImport.getName();

        // import
        IVirtualFile salmonFile = fileImporter.importFile(fileToImport, rootDir);

        // trigger the cache to add the filename
        String basename = salmonFile.getName();

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

        IVirtualFile nNewFile = newDir1.getChild(newFile.getName());
        String checkSumAfter = getChecksum(nNewFile.getRealFile());
        assertEquals(checkSumBefore, checkSumAfter);

        if (!move) {
            IVirtualFile file = rootDir.getChild(fileToImport.getName());
            String checkSumOrigAfter = getChecksum(file.getRealFile());
            assertEquals(checkSumBefore, checkSumOrigAfter);
        }

        assertEquals(salmonFile.getName(), newFile.getName());
    }

    private static void flipBit(IVirtualFile salmonFile, long position) throws Exception {
        RandomAccessStream stream = salmonFile.getRealFile().getOutputStream();
        stream.setPosition(position);
        stream.write(new byte[]{1}, 0, 1);
        stream.flush();
        stream.close();
    }

    public static AesFile shouldCreateFileWithoutVault(byte[] testBytes, byte[] key, boolean applyIntegrity, boolean verifyIntegrity, int chunkSize, byte[] hashKey,
                                                       byte[] filenameNonce, byte[] fileNonce, boolean flipBit, int flipPosition, boolean checkData) throws Exception {
        // write file
        IFile realDir = SalmonFSTestHelper.generateFolder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        AesFile dir = new AesFile(realDir);
        String filename = "test_" + System.currentTimeMillis() + "." + flipPosition + ".txt";
        AesFile newFile = dir.createFile(filename, key, filenameNonce, fileNonce);
        System.out.println("new file: " + newFile.getPath());
        if (applyIntegrity)
            newFile.setApplyIntegrity(true, hashKey, chunkSize);
        else
            newFile.setApplyIntegrity(false);
        RandomAccessStream stream = newFile.getOutputStream();

        stream.write(testBytes, 0, testBytes.length);
        stream.flush();
        stream.close();
        IFile realFile = newFile.getRealFile();

        // tamper
        if (flipBit) {
            IFile realTmpFile = newFile.getRealFile();
            RandomAccessStream realStream = realTmpFile.getOutputStream();
            realStream.setPosition(flipPosition);
            realStream.write(new byte[]{0}, 0, 1);
            realStream.flush();
            realStream.close();
        }

        // open file for read
        AesFile readFile = new AesFile(realFile);
        readFile.setEncryptionKey(key);
        readFile.setRequestedNonce(fileNonce);
        if (verifyIntegrity)
            readFile.setVerifyIntegrity(true, hashKey);
        else
            readFile.setVerifyIntegrity(false);
        AesStream inStream = readFile.getInputStream();
        byte[] textBytes = new byte[testBytes.length];
        inStream.read(textBytes, 0, textBytes.length);
        inStream.close();
        if (checkData)
            assertArrayEquals(testBytes, textBytes);
        return readFile;
    }

    public static void exportAndImportAuth(IFile vault, IFile importFilePath) throws Exception {
        // emulate 2 different devices with different sequencers
        FileSequencer sequencer1 = createSalmonFileSequencer();
        FileSequencer sequencer2 = createSalmonFileSequencer();

        // set to the first sequencer and create the vault
        AesDrive drive = createDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        IVirtualFile rootDir = drive.getRoot();
        IFile fileToImport = importFilePath;
        AesFile aesFileA1 = (AesFile) fileImporter.importFile(fileToImport, rootDir);
        long nonceA1 = BitConverter.toLong(aesFileA1.getRequestedNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        // open with another device (different sequencer) and export auth id
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        String authId = drive.getAuthId();
        boolean success = false;
        try {
            // import a test file should fail because not authorized
            rootDir = drive.getRoot();
            fileToImport = importFilePath;
            fileImporter.importFile(fileToImport, rootDir);
            success = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        assertFalse(success);
        drive.close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        IFile exportAuthDir = generateFolder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR);
        IFile exportFile = exportAuthDir.createFile(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        AuthConfig.exportAuthFile(drive, authId, exportFile);
        IFile exportAuthFile = exportAuthDir.getChild(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        AesFile salmonCfgFile = new AesFile(exportAuthFile, drive);
        long nonceCfg = BitConverter.toLong(salmonCfgFile.getFileNonce(), 0, Generator.NONCE_LENGTH);
        // import another test file
        rootDir = drive.getRoot();
        fileToImport = importFilePath;
        AesFile aesFileA2 = fileImporter.importFile(fileToImport, rootDir);
        long nonceA2 = BitConverter.toLong(aesFileA2.getFileNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        //reopen with second device(sequencer) and import auth file
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        AuthConfig.importAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = drive.getRoot();
        fileToImport = importFilePath;
        AesFile aesFileB1 = fileImporter.importFile(fileToImport, rootDir);
        long nonceB1 = BitConverter.toLong(aesFileB1.getFileNonce(), 0, Generator.NONCE_LENGTH);
        AesFile aesFileB2 = fileImporter.importFile(fileToImport, rootDir);
        long nonceB2 = BitConverter.toLong(aesFileB2.getFileNonce(), 0, Generator.NONCE_LENGTH);
        drive.close();

        assertEquals(nonceA1, nonceCfg - 1);
        assertEquals(nonceCfg, nonceA2 - 2);
        assertNotEquals(nonceA2, nonceB1);
        assertEquals(nonceB1, nonceB2 - 2);
    }

    public static void testMaxFiles(IFile vaultDir, IFile seqFile, IFile importFile,
                                    byte[] testMaxNonce, long offset, boolean shouldImport) {
        boolean importSuccess = false;
        try {
            FileSequencer sequencer = new FileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer) {
                @Override
                public void initializeSequence(String driveId, String authId, byte[] startNonce, byte[] maxNonce)
                        throws IOException {
                    long nMaxNonce = BitConverter.toLong(testMaxNonce, 0, Generator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, Generator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, Generator.NONCE_LENGTH);
                    super.initializeSequence(driveId, authId, startNonce, maxNonce);
                }
            };
            AesDrive drive;
            try {
                drive = openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            } catch (Exception ex) {
                drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            }
            IVirtualFile rootDir = drive.getRoot();
            rootDir.listFiles();
            IVirtualFile salmonRootDir = drive.getRoot();
            IFile fileToImport = importFile;
            IVirtualFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir);
            importSuccess = salmonFile != null;
        } catch (Exception ex) {
            if (ex instanceof RangeExceededException)
                importSuccess = false;
            ex.printStackTrace();
        }

        assertEquals(shouldImport, importSuccess);
    }

    public static void testRawTextFile() throws IOException {
        String text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IFile dir = generateFolder("test");
        String filename = "file.txt";
        IFile testFile = dir.createFile(filename);
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
        IFile writeFile = dir.getChild(filename);
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

    public static void testRawFile() throws IOException {
        IFile dir = generateFolder("test");
        String filename = "file.dat";
        IFile testFile = dir.createFile(filename);

        // write to file
        RandomAccessStream stream = SalmonFSTestHelper.TEST_IMPORT_FILE.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        byte[] mbytes = ms.toArray();
        ms.close();

        stream.setPosition(0);
        RandomAccessStream wstream = testFile.getOutputStream();
        int bytesRead;
        byte[] buff = new byte[32768];
        while ((bytesRead = stream.read(buff, 0, buff.length)) > 0) {
            wstream.write(buff, 0, bytesRead);
        }
        wstream.flush();
        wstream.close();
        stream.close();

        // read a file
        IFile writeFile = dir.getChild(filename);
        long pos = writeFile.getLength() / 2;
        RandomAccessStream rstream = writeFile.getInputStream();
        rstream.setPosition(pos);
        byte[] readBuff = new byte[32768];
        MemoryStream lstream = new MemoryStream();
        while ((bytesRead = rstream.read(readBuff, 0, readBuff.length)) > 0) {
            lstream.write(readBuff, 0, bytesRead);
        }
        byte[] lbytes = lstream.toArray();
        rstream.close();

        byte[] mbytes1 = new byte[mbytes.length - (int) pos];
        System.arraycopy(mbytes, (int) pos, mbytes1, 0, mbytes1.length);
        assertArrayEquals(mbytes1, lbytes);
    }

    public static void testEncDecFile() throws IOException {
        String text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IFile dir = generateFolder("test");
        String filename = "file.dat";
        IFile testFile = dir.createFile(filename);
        byte[] bytes = text.getBytes();
        byte[] key = Generator.getSecureRandomBytes(32);
        byte[] nonce = Generator.getSecureRandomBytes(8);

        IFile wfile = dir.getChild(filename);
        AesFile encFile = new AesFile(wfile);
        nonce = Generator.getSecureRandomBytes(8);
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
        IFile rfile = dir.getChild(filename);
        AesFile encFile2 = new AesFile(rfile);
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
        AesStream encryptor = new AesStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        RandomAccessStream inputStream = new MemoryStream(data);
        inputStream.copyTo(encryptor);
        encOutStream.setPosition(0);
        byte[] encData = encOutStream.toArray();
        encryptor.flush();
        encryptor.close();
        encOutStream.close();
        inputStream.close();

        RandomAccessStream encInputStream = new MemoryStream(encData);
        AesStream decryptor = new AesStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        MemoryStream outStream = new MemoryStream();
        decryptor.copyTo(outStream);
        outStream.setPosition(0);
        byte[] decData = outStream.toArray();
        decryptor.close();
        encInputStream.close();
        outStream.close();

        assertArrayEquals(data, decData);
    }

    public static byte[] getRealFileContents(IFile filePath) throws Exception {
        IFile file = filePath;
        RandomAccessStream ins = file.getInputStream();
        MemoryStream outs = new MemoryStream();
        ins.copyTo(outs);
        outs.setPosition(0);
        outs.flush();
        outs.close();
        return outs.toArray();
    }

    public static void seekAndReadFileInputStream(byte[] data, AesFileInputStream fileInputStream,
                                                  int start, int length, int readOffset, int shouldReadLength) throws IOException {
        byte[] buffer = new byte[length + readOffset];
        fileInputStream.reset();
        fileInputStream.skip(start);
        int totalBytesRead = 0;
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer, readOffset + totalBytesRead, length - totalBytesRead)) > 0) {
            totalBytesRead += bytesRead;
        }
        assertEquals(shouldReadLength, totalBytesRead);
        byte[] tdata = new byte[buffer.length];
        System.arraycopy(data, start, tdata, readOffset, shouldReadLength);
        assertArrayEquals(tdata, buffer);
    }

    public static void shouldTestFileSequencer() throws IOException {
        FileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();

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
        } catch (RangeExceededException ex) {
            System.err.println(ex);
            caught = true;
        }
        assertTrue(caught);
    }

    public static int getChildrenCountRecursively(IFile realFile) {
        int count = 1;
        if (realFile.isDirectory()) {
            for (IFile child : realFile.listFiles()) {
                count += getChildrenCountRecursively(child);
            }
        }
        return count;
    }

    public static void copyStream(AesFileInputStream src, MemoryStream dest) throws IOException {
        int bufferSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
        int bytesRead;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = src.read(buffer, 0, bufferSize)) > 0) {
            dest.write(buffer, 0, bytesRead);
        }
        dest.flush();
    }


    static void shouldReadFile(IFile vaultPath, String filename) throws NoSuchAlgorithmException, IOException {
        IFile localFile = SalmonFSTestHelper.TEST_INPUT_DIR.getChild(filename);
        String localChkSum = getChecksum(localFile);

        IFile vaultDir = vaultPath;
        FileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile root = drive.getRoot();
        IVirtualFile file = root.getChild(filename);
        System.out.println("file size: " + file.getLength());
        System.out.println("file last modified: " + file.getLastDateModified());
        assertTrue(file.exists());

        RandomAccessStream stream = file.getInputStream();
        String digest = SalmonFSTestHelper.getChecksumStream(stream.asReadStream());
        stream.close();
        assertEquals(digest, localChkSum);
    }

    static void seekAndReadHttpFile(byte[] data, AesFile file,
                                    int buffersCount, int bufferSize, int backOffset) throws IOException {
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                0, 32, 0, 32,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                220, 8, 2, 8,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                100, 2, 0, 2,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                6, 16, 0, 16,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                50, 40, 0, 40,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                124, 50, 0, 50,
                buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.seekAndReadFileStream(data, file,
                250, 10, 0, 10,
                buffersCount, bufferSize, backOffset);
    }

    // shouldReadLength should be equal to length
    // when checking Http files since the return buffer
    // might give us more data than requested
    static void seekAndReadFileStream(byte[] data, AesFile file,
                                      int start, int length, int readOffset, int shouldReadLength,
                                      int buffersCount, int bufferSize, int backOffset) throws IOException {
        byte[] buffer = new byte[length + readOffset];

        InputStream stream = null;
        if (SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM) {
            // multi threaded
            stream = new AesFileInputStream(file, buffersCount, bufferSize, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        } else {
            stream = file.getInputStream().asReadStream();
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

    public static void exportFiles(AesFile[] files, IFile dir) throws Exception {
        int bufferSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
        AesFileCommander commander = new AesFileCommander(bufferSize, bufferSize, SalmonFSTestHelper.ENC_EXPORT_THREADS);

        List<String> hashPreExport = new ArrayList<>();
        for (AesFile file : files)
            hashPreExport.add(SalmonFSTestHelper.getChecksumStream(new InputStreamWrapper(file.getInputStream())));

        // export files
        FileCommander.BatchExportOptions exportOptions = new FileCommander.BatchExportOptions();
        exportOptions.deleteSource = false;
        exportOptions.integrity = true;
        exportOptions.autoRename = IFile.autoRename;
        exportOptions.onFailed = (sfile, ex) ->
        {
            // file failed to import
            System.err.println(ex);
            try {
                System.out.println("export failed: " + sfile.getName() + "\n" + ex);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        exportOptions.onProgressChanged = (taskProgress) -> {
            if (!SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                return;
            try {
                System.out.println("file exporting: " + taskProgress.getFile().getName() + ": "
                        + taskProgress.getProcessedBytes() + "/" + taskProgress.getTotalBytes() + " bytes");
            } catch (Exception e) {
                System.err.println(e);
            }
        };
        IFile[] filesExported = commander.exportFiles(files, dir, exportOptions);
        System.out.println("Files exported");

        for (int i = 0; i < files.length; i++) {
            RandomAccessStream stream = filesExported[i].getInputStream();
            String hashPostImport = SalmonFSTestHelper.getChecksumStream(stream.asReadStream());
            stream.close();
            assertEquals(hashPostImport, hashPreExport.get(i));
        }

        // close the file commander
        commander.close();
    }
}