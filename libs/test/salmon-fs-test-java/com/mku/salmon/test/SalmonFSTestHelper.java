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
import com.mku.file.IRealFile;
import com.mku.file.JavaWSFile;
import com.mku.func.BiConsumer;
import com.mku.salmon.drive.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.file.IVirtualFile;
import com.mku.salmon.drive.JavaWSDrive;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.RandomAccessStream;
import com.mku.streams.MemoryStream;
import com.mku.salmon.*;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.SalmonFile;
import com.mku.salmon.streams.SalmonFileInputStream;
import com.mku.salmon.sequence.SalmonFileSequencer;
import com.mku.salmon.sequence.SalmonSequenceSerializer;
import com.mku.salmon.utils.SalmonFileExporter;
import com.mku.salmon.utils.SalmonFileImporter;
import com.mku.sequence.INonceSequenceSerializer;
import com.mku.utils.FileSearcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonFSTestHelper {
    static Class<?> driveClassType = null; // drive class type
    static String TEST_ROOT_DIR = "d:\\tmp\\";
    static String TEST_OUTPUT_DIR = SalmonFSTestHelper.TEST_ROOT_DIR + "output\\";
    static String TEST_VAULT_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "enc";
    static String TEST_VAULT2_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "enc2";
    static String TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "export\\";
    static String TEST_DATA_DIR_FOLDER = SalmonFSTestHelper.TEST_ROOT_DIR + "testdata\\";
    static String TEST_IMPORT_TINY_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "tiny_test.txt";
    static String TEST_IMPORT_SMALL_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "small_test.zip";
    static String TEST_IMPORT_MEDIUM_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "medium_test.zip";
    static String TEST_IMPORT_LARGE_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "large_test.mp4";
    static String TEST_IMPORT_HUGE_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "huge.zip";
    static String TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

    static String TEST_SEQUENCER_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR;
    static String TEST_SEQUENCER_FILENAME = "fileseq.xml";

    static String TEST_EXPORT_FILENAME = "export.slma";

    public static String VAULT_HOST = "http://localhost:8080";
    public static String VAULT_URL = VAULT_HOST + ""; // same
    public static String VAULT_PASSWORD = "test";

    static int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    static int ENC_IMPORT_THREADS = 1;
    static int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    static int ENC_EXPORT_THREADS = 1;

    private static final int REAL_FILE_BUFFER_SIZE = 512 * 1024;

    static int TEST_FILE_INPUT_STREAM_THREADS = 1;
    static boolean TEST_USE_FILE_INPUT_STREAM = false;

    static boolean ENABLE_FILE_PROGRESS = false;

    public static String TEST_SEQUENCER_FILE1 = "seq1.xml";
    public static String TEST_SEQUENCER_FILE2 = "seq2.xml";

    public static HashMap<String, String> users;
    private static JavaWSFile.Credentials credentials1 = new JavaWSFile.Credentials("user", "password");

    static SalmonFileImporter fileImporter;
    static SalmonFileExporter fileExporter;

    public static INonceSequenceSerializer getSequenceSerializer() {
        return new SalmonSequenceSerializer();
    }

    static void setDriveClassType(Class<?> driveClassType) {
        SalmonFSTestHelper.driveClassType = driveClassType;
    }

    static void initialize() {
        // TODO: ToSync global importer/exporter
        SalmonFSTestHelper.fileImporter = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new SalmonFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    static void close() {
        SalmonFSTestHelper.fileImporter.close();
        SalmonFSTestHelper.fileExporter.close();
    }

    public static IRealFile generateFolder(String dirPath) {
        return generateFolder(dirPath, driveClassType);
    }

    public static IRealFile generateFolder(String dirPath, Class<?> driveClassType) {
        long time = System.currentTimeMillis();
        if (driveClassType == JavaWSDrive.class) {
            IRealFile dir = new JavaWSFile("/remote_" + time, SalmonFSTestHelper.VAULT_URL,
                    SalmonFSTestHelper.credentials1);
            if (!dir.mkdir())
                throw new RuntimeException("Could not generate folder");
            return dir;
        } else {
            File dir = new File(dirPath + "_" + time);
            if (!dir.mkdir())
                throw new RuntimeException("Could not generate folder");
            return new JavaFile(dir.getAbsolutePath());
        }
    }

    public static String getChecksum(IRealFile realFile) throws NoSuchAlgorithmException, IOException {
        RandomAccessStream stream = realFile.getInputStream();
        InputStreamWrapper isw = new InputStreamWrapper(stream);
        return getChecksum(isw, REAL_FILE_BUFFER_SIZE);
    }

    public static String getChecksum(InputStream inputStream, int bufferSize) throws NoSuchAlgorithmException, IOException {
        DigestInputStream dis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) > 0) {
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
            if (dis != null)
                dis.close();
            if (inputStream != null)
                inputStream.close();
        }
    }

    public static void importAndExport(IRealFile vaultDir, String pass, String importFile,
                                       boolean bitflip, long flipPosition, boolean shouldBeEqual,
                                       boolean applyFileIntegrity, boolean verifyFileIntegrity) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer(new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        JavaFile fileToImport = new JavaFile(importFile);
        String hashPreImport = SalmonFSTestHelper.getChecksum(fileToImport);

        // import
        BiConsumer<Long, Long> printImportProgress = (position, length) -> {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                System.out.println("importing file: " + position + "/" + length);
        };
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, rootDir, null, false, applyFileIntegrity, printImportProgress);
        assertTrue(salmonFile.exists());

        Integer chunkSize = salmonFile.getFileChunkSize();
        if (chunkSize != null && chunkSize > 0 && !verifyFileIntegrity)
            salmonFile.setVerifyIntegrity(false, null);
        SalmonStream sstream = salmonFile.getInputStream();
        String hashPostImport = SalmonFSTestHelper.getChecksum(new InputStreamWrapper(sstream), sstream.getBufferSize());
        if (shouldBeEqual) {
            assertEquals(hashPreImport, hashPostImport);
        }

        // get fresh copy of the file
        salmonFile = (SalmonFile) rootDir.listFiles()[0];

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
        IVirtualFile finalSalmonFile = salmonFile;
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
        if (shouldBeEqual) {
            assertEquals(hashPreImport, hashPostExport);
        }
    }

    static SalmonDrive createDrive(IRealFile vaultDir, Class<?> driveClassType, String pass, SalmonFileSequencer sequencer) throws IOException {
        if (driveClassType == JavaWSDrive.class)
            return JavaWSDrive.create(vaultDir, pass, sequencer, credentials1.getServiceUser(),
                    credentials1.getServicePassword());
        else
            return SalmonDrive.createDrive(vaultDir, driveClassType, pass, sequencer);
    }

    public static void importAndSearch(IRealFile vaultDir, String pass, String importFile) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer(new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        JavaFile fileToImport = new JavaFile(importFile);
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

    public static void importAndCopy(IRealFile vaultDir, String pass, String importFile,
                                     int importBufferSize, int importThreads, String newDir, boolean move) throws Exception {
        SalmonFileSequencer sequencer = createSalmonFileSequencer(vaultDir.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1), getSequenceSerializer());
        SalmonDrive drive = createDrive(vaultDir, SalmonFSTestHelper.driveClassType, pass, sequencer);
        IVirtualFile rootDir = drive.getRoot();
        rootDir.listFiles();
        JavaFile fileToImport = new JavaFile(importFile);
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
                                                          byte[] filenameNonce, byte[] fileNonce, String outputDir, boolean flipBit, int flipPosition, boolean checkData) throws Exception {
        // write file
        IRealFile realDir = new JavaFile(outputDir);
        SalmonFile dir = new SalmonFile(realDir, null);
        String filename = "test_" + System.currentTimeMillis() + ".txt";
        SalmonFile newFile = dir.createFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            newFile.setApplyIntegrity(true, hashKey, chunkSize);
        RandomAccessStream stream = newFile.getOutputStream();

        stream.write(testBytes, 0, testBytes.length);
        stream.flush();
        stream.close();
        String realFilePath = newFile.getRealFile().getPath();

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
        IRealFile realFile = new JavaFile(realFilePath);
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

    public static void exportAndImportAuth(IRealFile vault, String importFilePath) throws Exception {
        IRealFile seqFile1 = vault.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        IRealFile seqFile2 = vault.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE2);

        // emulate 2 different devices with different sequencers
        SalmonFileSequencer sequencer1 = createSalmonFileSequencer(seqFile1, getSequenceSerializer());
        SalmonFileSequencer sequencer2 = createSalmonFileSequencer(seqFile2, getSequenceSerializer());

        // set to the first sequencer and create the vault
        SalmonDrive drive = createDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        IVirtualFile rootDir = drive.getRoot();
        IRealFile fileToImport = new JavaFile(importFilePath);
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
            fileToImport = new JavaFile(importFilePath);
            fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
            success = true;
        } catch (Exception ignored) {

        }

        assertFalse(success);
        drive.close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        IRealFile exportFile = vault.getChild(SalmonFSTestHelper.TEST_EXPORT_FILENAME);
        SalmonAuthConfig.exportAuthFile(drive, authId, exportFile);
        IRealFile exportAuthFile = vault.getChild(SalmonFSTestHelper.TEST_EXPORT_FILENAME);
        SalmonFile salmonCfgFile = new SalmonFile(exportAuthFile, drive);
        long nonceCfg = BitConverter.toLong(salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        rootDir = drive.getRoot();
        fileToImport = new JavaFile(importFilePath);
        SalmonFile salmonFileA2 = (SalmonFile) fileImporter.importFile(fileToImport, rootDir, null, false, false, null);
        long nonceA2 = BitConverter.toLong(salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        drive.close();

        //reopen with second device(sequencer) and import auth file
        drive = openDrive(vault, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        SalmonAuthConfig.importAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = drive.getRoot();
        fileToImport = new JavaFile(importFilePath);
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

    public static void testMaxFiles(IRealFile vaultDir, IRealFile seqFile, String importFile,
                                    byte[] testMaxNonce, long offset, boolean shouldImport) {
        boolean importSuccess;
        try {
            SalmonFileSequencer sequencer = new SalmonFileSequencer(seqFile, getSequenceSerializer()) {
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
            JavaFile fileToImport = new JavaFile(importFile);
            IVirtualFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        } catch (Exception ex) {
            importSuccess = false;
            ex.printStackTrace();
        }

        assertEquals(shouldImport, importSuccess);
    }

    static SalmonDrive openDrive(IRealFile vaultDir, Class<?> driveClassType, String testPassword, SalmonFileSequencer sequencer) throws IOException {
        if (driveClassType == JavaWSDrive.class) {
            // use the remote service instead
            return JavaWSDrive.open(vaultDir, testPassword, sequencer,
                    credentials1.getServiceUser(), credentials1.getServicePassword());
        } else
            return SalmonDrive.openDrive(vaultDir, driveClassType, testPassword, sequencer);
    }

    public static void testExamples() throws Exception {
        String text = "This is a plaintext that will be used for testing";
        String testFile = "D:/tmp/file.txt";
        IRealFile tFile = new JavaFile(testFile);
        if (tFile.exists())
            tFile.delete();
        byte[] bytes = text.getBytes();
        byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        byte[] encBytes = new SalmonEncryptor().encrypt(bytes, key, nonce, false);
        // decrypt byte array
        byte[] decBytes = new SalmonDecryptor().decrypt(encBytes, key, nonce, false);

        assertArrayEquals(bytes, decBytes);

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
        // decrypt string
        String decText = SalmonTextDecryptor.decryptString(encText, key, null, true);

        assertEquals(text, decText);

        // Example 3: encrypt data to an output stream
        MemoryStream encOutStream = new MemoryStream(); // or any other writable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        SalmonStream encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream,
                null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        encryptor.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        encOutStream.setPosition(0);
        byte[] encData = encOutStream.toArray();
        encryptor.flush();
        encryptor.close();
        encOutStream.close();
        //decrypt a stream with encoded data
        RandomAccessStream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        SalmonStream decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream,
                null, false, null, null);
        byte[] decBuffer = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decryptor.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        String decString = new String(decBuffer, 0, bytesRead);
        System.out.println(decString);
        decryptor.close();
        encInputStream.close();

        assertEquals(text, decString);

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
        SalmonFile encFile = new SalmonFile(new JavaFile(testFile), null);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        RandomAccessStream stream = encFile.getOutputStream();
        // encrypt data and write with a single call
        stream.write(bytes, 0, bytes.length);
        stream.flush();
        stream.close();
        // decrypt an encrypted file
        SalmonFile encFile2 = new SalmonFile(new JavaFile(testFile), null);
        encFile2.setEncryptionKey(key);
        RandomAccessStream stream2 = encFile2.getInputStream();
        byte[] decBuff = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        int encBytesRead = stream2.read(decBuff, 0, decBuff.length);
        String decString2 = new String(decBuff, 0, encBytesRead);
        System.out.println(decString2);
        stream2.close();

        assertEquals(text, decString2);
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

    public static byte[] getRealFileContents(String filePath) throws Exception {
        IRealFile file = new JavaFile(filePath);
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
        IRealFile file = new JavaFile(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.exists())
            file.delete();
        SalmonFileSequencer sequencer = createSalmonFileSequencer(file,
                getSequenceSerializer());

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

    public static int GetChildrenCountRecursively(IRealFile realFile) {
        int count = 1;
        if (realFile.isDirectory()) {
            for (IRealFile child : realFile.listFiles()) {
                count += GetChildrenCountRecursively(child);
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

    public static SalmonFileSequencer createSalmonFileSequencer(IRealFile javaFile, INonceSequenceSerializer sequenceSerializer) throws IOException {
        if (driveClassType == JavaWSDrive.class) {
            // use a local sequencer for testing since the current path is remote
            IRealFile seqDir = generateFolder(TEST_SEQUENCER_DIR + "/seq", JavaDrive.class);
            IRealFile seqFile = seqDir.getChild(TEST_SEQUENCER_FILENAME);
            return new SalmonFileSequencer(seqFile, getSequenceSerializer());
        } else
            return new SalmonFileSequencer(javaFile, sequenceSerializer);
    }
}