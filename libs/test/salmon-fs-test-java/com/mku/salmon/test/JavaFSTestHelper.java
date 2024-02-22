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
import com.mku.file.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.io.RandomAccessStream;
import com.mku.io.MemoryStream;
import com.mku.salmon.*;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import com.mku.salmonfs.SalmonDrive;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.salmonfs.SalmonFileInputStream;
import com.mku.sequence.SalmonFileSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.mku.sequence.SalmonSequenceSerializer;
import com.mku.utils.SalmonFileExporter;
import com.mku.utils.SalmonFileImporter;
import com.mku.utils.SalmonFileSearcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

public class JavaFSTestHelper {
	public static String TEST_SEQUENCER_DIR = "D:\\tmp\\output";
    public static String TEST_SEQUENCER_FILENAME = "fileseq.xml";


    public static final int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    public static final int ENC_IMPORT_THREADS = 2;
    public static final int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    public static final int ENC_EXPORT_THREADS = 2;

    public static String getChecksum(IRealFile realFile) throws NoSuchAlgorithmException, IOException {
        InputStream is = null;
        DigestInputStream dis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            is = new FileInputStream(realFile.getPath());
            dis = new DigestInputStream(is, md);
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
            if (is != null)
                is.close();
        }
    }

    public static void importAndExport(String vaultDir, String pass, String importFile,
                                       int importBufferSize, int importThreads, int exportBufferSize, int exportThreads, boolean integrity,
                                       boolean bitflip, long flipPosition, boolean shouldBeEqual,
                                       boolean applyFileIntegrity, boolean verifyFileIntegrity) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();

        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();

        JavaFile fileToImport = new JavaFile(importFile);
        String hashPreImport = JavaFSTestHelper.getChecksum(fileToImport);

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, applyFileIntegrity, null);

        assertNotNull(salmonFile);

        assertTrue(salmonFile.exists());
        SalmonFile[] salmonFiles = SalmonDriveManager.getDrive().getVirtualRoot().listFiles();
        long realFileSize = fileToImport.length();
        for (SalmonFile file : salmonFiles) {
            if (file.getBaseName().equals(fileToImport.getBaseName())) {
                if (shouldBeEqual) {

                    assertTrue(file.exists());
                    long fileSize = file.getSize();

                    assertEquals(realFileSize, fileSize);
                }
            }
        }

        // export
        SalmonFileExporter fileExporter = new SalmonFileExporter(exportBufferSize, exportThreads);
        if (bitflip)
            flipBit(salmonFile, flipPosition);

        IRealFile exportFile = fileExporter.exportFile(salmonFile, SalmonDriveManager.getDrive().getExportDir(), null, true, verifyFileIntegrity, null);

        String hashPostExport = JavaFSTestHelper.getChecksum(exportFile);
        if (shouldBeEqual) {

            assertEquals(hashPreImport, hashPostExport);
        }
    }

    public static void importAndSearch(String vaultDir, String pass, String importFile,
                                       int importBufferSize, int importThreads) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        JavaFile fileToImport = new JavaFile(importFile);
        String rbasename = fileToImport.getBaseName();

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);

        // trigger the cache to add the filename
        String basename = salmonFile.getBaseName();

        assertNotNull(salmonFile);

        assertTrue(salmonFile.exists());

        SalmonFileSearcher searcher = new SalmonFileSearcher();
        SalmonFile[] files = searcher.search(salmonRootDir, basename, true, null, null);

        assertTrue(files.length > 0);

        assertEquals(files[0].getBaseName(), basename);

    }

    public static void importAndCopy(String vaultDir, String pass, String importFile,
                                     int importBufferSize, int importThreads, String newDir, boolean move) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        JavaFile fileToImport = new JavaFile(importFile);
        String rbasename = fileToImport.getBaseName();

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);

        // trigger the cache to add the filename
        String basename = salmonFile.getBaseName();

        assertNotNull(salmonFile);

        assertTrue(salmonFile.exists());

        String checkSumBefore = getChecksum(salmonFile.getRealFile());
        SalmonFile newDir1 = salmonRootDir.createDirectory(newDir);
        SalmonFile newFile;
        if (move)
            newFile = salmonFile.move(newDir1, null);
        else
            newFile = salmonFile.copy(newDir1, null);

        assertNotNull(newFile);
        String checkSumAfter = getChecksum(newFile.getRealFile());

        assertEquals(checkSumBefore, checkSumAfter);

        assertEquals(salmonFile.getBaseName(), newFile.getBaseName());
    }

    private static void flipBit(SalmonFile salmonFile, long position) throws Exception {
        RandomAccessStream stream = salmonFile.getRealFile().getOutputStream();
        stream.position(position);
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
            realStream.position(flipPosition);
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
        if(checkData)
            assertArrayEquals(testBytes, textBytes);
        return readFile;
    }

    public static void exportAndImportAuth(String vault, String importFilePath) throws Exception {
        String exportAuthFilePath = vault + File.separator + TestHelper.TEST_EXPORT_DIR;
        String seqFile1 = vault + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        String seqFile2 = vault + "/" + TestHelper.TEST_SEQUENCER_FILE2;

        // emulate 2 different devices with different sequencers
        SalmonFileSequencer sequencer1 = new SalmonFileSequencer(new JavaFile(seqFile1), new SalmonSequenceSerializer());
        SalmonFileSequencer sequencer2 = new SalmonFileSequencer(new JavaFile(seqFile2), new SalmonSequenceSerializer());

        // set to the first sequencer and create the vault
        SalmonDriveManager.setSequencer(sequencer1);
        SalmonDriveManager.createDrive(vault, TestHelper.TEST_PASSWORD);
        SalmonDriveManager.getDrive().unlock(TestHelper.TEST_PASSWORD);
        // import a test file
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        IRealFile fileToImport = new JavaFile(importFilePath);
        SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0);
        SalmonFile salmonFileA1 = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        long nonceA1 = BitConverter.toLong(salmonFileA1.getRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        // open with another device (different sequencer) and export auth id
        SalmonDriveManager.setSequencer(sequencer2);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().unlock(TestHelper.TEST_PASSWORD);
        String authID = SalmonDriveManager.getAuthID();
        boolean success = false;
        try {
            // import a test file should fail because not authorized
            salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            fileToImport = new JavaFile(importFilePath);
            fileImporter = new SalmonFileImporter(0, 0);
            fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            success = true;
        } catch (Exception ignored) {

        }

        assertFalse(success);
        SalmonDriveManager.closeDrive();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        SalmonDriveManager.setSequencer(sequencer1);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().unlock(TestHelper.TEST_PASSWORD);
        SalmonDriveManager.exportAuthFile(authID, vault, TestHelper.TEST_EXPORT_DIR);
        IRealFile configFile = new JavaFile(exportAuthFilePath);
        SalmonFile salmonCfgFile = new SalmonFile(configFile, SalmonDriveManager.getDrive());
        long nonceCfg = BitConverter.toLong(salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        fileToImport = new JavaFile(importFilePath);
        fileImporter = new SalmonFileImporter(0, 0);
        SalmonFile salmonFileA2 = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        long nonceA2 = BitConverter.toLong(salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        //reopen with second device(sequencer) and import auth file
        SalmonDriveManager.setSequencer(sequencer2);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().unlock(TestHelper.TEST_PASSWORD);
        SalmonDriveManager.importAuthFile(exportAuthFilePath);
        // now import a 3rd file
        salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        fileToImport = new JavaFile(importFilePath);
        SalmonFile salmonFileB1 = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        long nonceB1 = BitConverter.toLong(salmonFileB1.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonFile salmonFileB2 = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
        long nonceB2 = BitConverter.toLong(salmonFileB2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        assertEquals(nonceA1, nonceCfg - 1);

        assertEquals(nonceCfg, nonceA2 - 2);

        assertNotEquals(nonceA2, nonceB1);

        assertEquals(nonceB1, nonceB2 - 2);
    }

    public static void testMaxFiles(String vaultDir, String seqFile, String importFile,
                                    byte[] testMaxNonce, long offset, boolean shouldImport) {
        boolean importSuccess;
        try {
            SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(seqFile), new SalmonSequenceSerializer()) {
                @Override
                public void initSequence(String driveID, String authID, byte[] startNonce, byte[] maxNonce)
                        throws SalmonSequenceException, IOException {
                    long nMaxNonce = BitConverter.toLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                    super.initSequence(driveID, authID, startNonce, maxNonce);
                }
            };
            SalmonDriveManager.setSequencer(sequencer);
            try {
                SalmonDrive drive = SalmonDriveManager.openDrive(vaultDir);
                drive.unlock(TestHelper.TEST_PASSWORD);
            } catch (Exception ex) {
                SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
            }
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
            SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            JavaFile fileToImport = new JavaFile(importFile);
            SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0);
            SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        } catch (Exception ex) {
            importSuccess = false;
            ex.printStackTrace();
        }

        assertEquals(shouldImport, importSuccess);
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
        MemoryStream encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        SalmonStream encryptor = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, encOutStream,
                null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        encryptor.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        encOutStream.position(0);
        byte[] encData = encOutStream.toArray();
        encryptor.flush();
        encryptor.close();
        encOutStream.close();
        //decrypt a stream with encoded data
        RandomAccessStream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        SalmonStream decryptor = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, encInputStream,
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
        SalmonStream encryptor = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, encOutStream);
        RandomAccessStream inputStream = new MemoryStream(data);
        inputStream.copyTo(encryptor);
        encOutStream.position(0);
        byte[] encData = encOutStream.toArray();
        encryptor.flush();
        encryptor.close();
        encOutStream.close();
        inputStream.close();

        RandomAccessStream encInputStream = new MemoryStream(encData);
        SalmonStream decryptor = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, encInputStream);
        MemoryStream outStream = new MemoryStream();
        decryptor.copyTo(outStream);
        outStream.position(0);
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
        outs.position(0);
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
	
	public static void shouldTestFileSequencer() throws SalmonSequenceException, IOException, SalmonRangeExceededException {
        IRealFile file = new JavaFile(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.exists())
            file.delete();
        SalmonFileSequencer sequencer = new SalmonFileSequencer(file,
            new SalmonSequenceSerializer());

        sequencer.createSequence("AAAA", "AAAA");
        sequencer.initSequence("AAAA", "AAAA",
            BitConverter.toBytes(1, 8),
                BitConverter.toBytes(4, 8));
        byte[] nonce = sequencer.nextNonce("AAAA");
        assertEquals(1, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(2, BitConverter.toLong(nonce, 0, 8));
        nonce = sequencer.nextNonce("AAAA");
        assertEquals(3, BitConverter.toLong(nonce, 0, 8));

        boolean caught = false;
        try
        {
            nonce = sequencer.nextNonce("AAAA");
            assertEquals(5, BitConverter.toLong(nonce, 0, 8));
        }
        catch (SalmonRangeExceededException ex)
        {
            System.err.println(ex);
            caught = true;
        }
        assertTrue(caught);
    }

    public static int GetChildrenCountRecursively(IRealFile realFile)
    {
        int count = 1;
        if (realFile.isDirectory())
        {
            for (IRealFile child : realFile.listFiles())
            {
                count += GetChildrenCountRecursively(child);
            }
        }
        return count;
    }

    public static void copyStream(SalmonFileInputStream src, MemoryStream dest) throws IOException {
        int bufferSize = SalmonDefaultOptions.getBufferSize();
        int bytesRead;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = src.read(buffer, 0, bufferSize)) > 0) {
            dest.write(buffer, 0, bytesRead);
        }
        dest.flush();
    }
}