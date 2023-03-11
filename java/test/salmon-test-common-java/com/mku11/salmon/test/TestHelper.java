package com.mku11.salmon.test;

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

import com.mku11.file.JavaDrive;
import com.mku11.file.JavaFile;
import com.mku11.salmon.SalmonEncryptor;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.SalmonTextEncryptor;
import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.BitConverter;
import com.mku11.salmonfs.*;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TestHelper {
    public static final int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    public static final int ENC_EXPORT_THREADS = 4;
    public static final int TEST_ENC_BUFFER_SIZE = 512 * 1024;
    public static final int TEST_DEC_BUFFER_SIZE = 512 * 1024;
    public static final String TEST_PASSWORD = "test123";
    public static final String TEST_FALSE_PASSWORD = "falsepass";
    public static final String TEST_EXPORT_DIR = "export.slma";

    public static final long MAX_ENC_COUNTER = (long) Math.pow(256, 7);
    // a nonce ready to overflow if a new file is imported
    public static final byte[] TEXT_VAULT_MAX_FILE_NONCE = {
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };
    public static final String TEST_SEQUENCER_FILE1 = "seq1.xml";
    public static final String TEST_SEQUENCER_FILE2 = "seq2.xml";

    private static final int TEXT_ITERATIONS = 20;
    public static String TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    public static byte[] TEST_KEY_BYTES = TEST_KEY.getBytes(Charset.defaultCharset());
    public static String TEST_NONCE = "12345678"; // 8 bytes
    public static byte[] TEST_NONCE_BYTES = TEST_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    public static byte[] TEST_FILENAME_NONCE_BYTES = TEST_FILENAME_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    public static byte[] TEST_HMAC_KEY_BYTES = TEST_HMAC_KEY.getBytes(Charset.defaultCharset());
    public static int TEST_PERF_SIZE = 5 * 1024 * 1024;
    public static String TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
    public static String TEST_TINY_TEXT = "test.txt";
    public static String TEST_TEXT = "This is another test that could be very long if used correct.";
    public static String TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

    public static String seekAndGetSubstringByRead(SalmonStream reader, int seek, int readCount, AbsStream.SeekOrigin seekOrigin) throws Exception {
        reader.seek(seek, seekOrigin);
        MemoryStream encOuts2 = new MemoryStream();

        byte[] bytes = new byte[readCount];
        int bytesRead;
        long totalBytesRead = 0;
        while (totalBytesRead < readCount && (bytesRead = reader.read(bytes, 0, bytes.length)) > 0) {
            // we skip the alignment offset and start reading the bytes we need
            encOuts2.write(bytes, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        String decText1 = new String(encOuts2.toArray(), Charset.defaultCharset());
        encOuts2.close();
        return decText1;
    }

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

    public static void encryptWriteDecryptRead(String text, byte[] key, byte[] iv,
                                               int encBufferSize, int decBufferSize, boolean testIntegrity, Integer chunkSize,
                                               byte[] hmacKey, boolean flipBits, String header, Integer maxTextLength) throws Exception {
        String testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        if (maxTextLength != null && maxTextLength < plainText.length())
            plainText = plainText.substring(0, maxTextLength);

        int headerLength = 0;
        if (header != null)
            headerLength = header.getBytes(Charset.defaultCharset()).length;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytes = encrypt(inputBytes, key, iv, encBufferSize,
                testIntegrity, chunkSize, hmacKey, header);
        if (flipBits)
            encBytes[encBytes.length / 2] = 0;

        // Use SalmonStrem to read from cipher byte array and MemoryStream to Write to byte array
        byte[] outputByte2 = decrypt(encBytes, key, iv, decBufferSize,
                testIntegrity, chunkSize, hmacKey, header != null ? headerLength :
                        null);
        String decText = new String(outputByte2, Charset.defaultCharset());

        System.out.println(plainText);
        System.out.println(decText);
        Assert.assertEquals(plainText, decText);
    }


    public static byte[] encrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 boolean integrity, Integer chunkSize, byte[] hmacKey,
                                 String header) throws Exception {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (header != null) {
            headerData = header.getBytes(Charset.defaultCharset());
            outs.write(headerData, 0, headerData.length);
        }
        SalmonStream writer = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                headerData, integrity, chunkSize, hmacKey);

        if (bufferSize == 0) // use the internal buffer size of the memorystream to copy
        {
            ins.writeTo(writer);
        } else { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = ins.read(buffer, 0, buffer.length)) > 0) {
                writer.write(buffer, 0, bytesRead);
            }
        }
        writer.flush();
        byte[] bytes = outs.toArray();
        writer.close();
        ins.close();
        return bytes;
    }

    public static byte[] decrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 Boolean integrity, Integer chunkSize, byte[] hmacKey,
                                 Integer headerLength) throws Exception {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (headerLength != null) {
            headerData = new byte[(int) headerLength];
            ins.read(headerData, 0, headerData.length);
        }
        SalmonStream reader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, ins,
                headerData, integrity, chunkSize, hmacKey);

        if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
        {
            reader.copyTo(outs);
        } else { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = reader.read(buffer, 0, buffer.length)) > 0) {
                outs.write(buffer, 0, bytesRead);
            }
        }
        outs.flush();
        byte[] bytes = outs.toArray();
        reader.close();
        outs.close();
        return bytes;
    }


    public static void importAndExport(String vaultDir, String pass, String importFile,
                                       int importBufferSize, int importThreads, int exportBufferSize, int exportThreads, boolean integrity,
                                       boolean bitflip, long flipPosition, boolean shouldBeEqual,
                                       Boolean ApplyFileIntegrity, Boolean VerifyFileIntegrity) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();

        SalmonDriveManager.getDrive().setEnableIntegrityCheck(integrity);

        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();

        JavaFile fileToImport = new JavaFile(importFile);
        String hashPreImport = TestHelper.getChecksum(fileToImport);

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads, null);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, false, ApplyFileIntegrity, 1, 1);
        Assert.assertNotNull(salmonFile);
        Assert.assertTrue(salmonFile.exists());
        SalmonFile[] salmonFiles = SalmonDriveManager.getDrive().getVirtualRoot().listFiles();
        long realFileSize = fileToImport.length();
        for (SalmonFile file : salmonFiles) {
            if (file.getBaseName().equals(fileToImport.getBaseName())) {
                if (shouldBeEqual) {
                    Assert.assertTrue(file.exists());
                    long fileSize = file.getSize();
                    Assert.assertEquals(realFileSize, fileSize);
                }
            }
        }

        // export
        SalmonFileExporter fileExporter = new SalmonFileExporter(exportBufferSize, exportThreads);
        if (bitflip)
            flipBit(salmonFile, flipPosition);

        IRealFile exportFile = fileExporter.exportFile(salmonFile, SalmonDriveManager.getDrive().getExportDir(), true, VerifyFileIntegrity, 1, 1);

        String hashPostExport = TestHelper.getChecksum(exportFile);
        if (shouldBeEqual) {
            Assert.assertEquals(hashPreImport, hashPostExport);
        }
    }

    public static void importAndSearch(String vaultDir, String pass, String importFile,
                                       int importBufferSize, int importThreads) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        JavaFile fileToImport = new JavaFile(importFile);
        String rbasename = fileToImport.getBaseName();

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads, null);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);

        // trigger the cache to add the filename
        String basename = salmonFile.getBaseName();

        Assert.assertNotNull(salmonFile);
        Assert.assertTrue(salmonFile.exists());

        SalmonFileSearcher searcher = new SalmonFileSearcher();
        SalmonFile[] files = searcher.search(salmonRootDir, basename, true, null);

        Assert.assertTrue(files.length > 0);
        Assert.assertEquals(files[0].getBaseName(), basename);

    }


    public static void importAndCopy(String vaultDir, String pass, String importFile,
                                     int importBufferSize, int importThreads, String newDir, boolean move) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);

        SalmonDriveManager.createDrive(vaultDir, pass);
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        JavaFile fileToImport = new JavaFile(importFile);
        String rbasename = fileToImport.getBaseName();

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads, null);
        SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);

        // trigger the cache to add the filename
        String basename = salmonFile.getBaseName();

        Assert.assertNotNull(salmonFile);
        Assert.assertTrue(salmonFile.exists());

        String checkSumBefore = getChecksum(salmonFile.getRealFile());
        SalmonFile newDir1 = salmonRootDir.createDirectory(newDir);
        SalmonFile newFile;
        if (move)
            newFile = salmonFile.move(newDir1, null);
        else
            newFile = salmonFile.copy(newDir1, null);
        Assert.assertNotNull(newFile);
        String checkSumAfter = getChecksum(newFile.getRealFile());
        Assert.assertEquals(checkSumBefore, checkSumAfter);
        Assert.assertEquals(salmonFile.getBaseName(), newFile.getBaseName());
    }

    private static void flipBit(SalmonFile salmonFile, long position) throws Exception {
        AbsStream stream = salmonFile.getRealFile().getOutputStream();
        stream.position(position);
        stream.write(new byte[]{1}, 0, 1);
        stream.flush();
        stream.close();
    }

    public static void seekAndRead(String text, byte[] key, byte[] iv,
                                   boolean integrity, int chunkSize, byte[] hmacKey) throws Exception {
        String testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hmacKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hmacKey);
        String correctText;
        String decText;

        correctText = plainText.substring(0, 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, AbsStream.SeekOrigin.Begin);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(0, 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, AbsStream.SeekOrigin.Begin);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.position() + 4, (int) decReader.position() + 4 + 4);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 4, 4, AbsStream.SeekOrigin.Current);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.position() + 6, (int) decReader.position() + 6 + 4);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 6, 4, AbsStream.SeekOrigin.Current);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.position() + 10, (int) decReader.position() + 10 + 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 10, 6, AbsStream.SeekOrigin.Current);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(12, 12 + 8);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 12, 8, AbsStream.SeekOrigin.Begin);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(plainText.length() - 14, plainText.length() - 14 + 7);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 14, 7, AbsStream.SeekOrigin.End);
        Assert.assertEquals(correctText, decText);

        correctText = plainText.substring(plainText.length() - 27, plainText.length() - 27 + 12);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 27, 12, AbsStream.SeekOrigin.End);
        Assert.assertEquals(correctText, decText);
        testCounter(decReader);
        encIns.close();
        decReader.close();
    }

    public static void seekTestCounterAndBlock(String text, byte[] key, byte[] iv,
                                               boolean integrity, int chunkSize, byte[] hmacKey) throws Exception {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(text);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hmacKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // Use SalmonStream to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hmacKey);
        for (int i = 0; i < 100; i++) {
            decReader.position(decReader.position() + 7);
            testCounter(decReader);
        }

        encIns.close();
        decReader.close();
    }

    private static void testCounter(SalmonStream decReader) throws IOException {
        long expectedBlock = decReader.position() / decReader.BLOCK_SIZE;
        Assert.assertEquals(expectedBlock, decReader.getBlock());

        long counterBlock = BitConverter.toLong(decReader.getCounter(), SalmonGenerator.NONCE_LENGTH,
                SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
        long expectedCounterValue = decReader.getBlock();
        Assert.assertEquals(expectedCounterValue, counterBlock);

        long nonce = BitConverter.toLong(decReader.getCounter(), 0, SalmonGenerator.NONCE_LENGTH);
        long expectedNonce = BitConverter.toLong(decReader.getNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        Assert.assertEquals(expectedNonce, nonce);
    }

    public static void seekAndWrite(String text, byte[] key, byte[] iv,
                                    long seek, int writeCount, String textToWrite, boolean alignToChunk,
                                    boolean integrity, int chunkSize, byte[] hmacKey,
                                    boolean setAllowRangeWrite
    ) throws Exception {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(text);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hmacKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // partial write
        byte[] writeBytes = textToWrite.getBytes(Charset.defaultCharset());
        MemoryStream pOuts = new MemoryStream(encBytes);
        SalmonStream partialWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, pOuts,
                null, integrity, chunkSize, hmacKey);
        long alignedPosition = seek;
        int alignOffset = 0;
        int count = writeCount;
        if (alignToChunk && partialWriter.getChunkSize() > 0) {
            int bytesRead;
            // if we have enabled integrity we align the position and the buffer
            if (seek % partialWriter.getChunkSize() != 0) {
                alignedPosition = seek / partialWriter.getChunkSize() * partialWriter.getChunkSize();
                alignOffset = (int) (seek % partialWriter.getChunkSize());
                count = count + alignOffset;

                if (count > partialWriter.getChunkSize() && count % partialWriter.getChunkSize() != 0)
                    count = count / partialWriter.getChunkSize() * partialWriter.getChunkSize() + partialWriter.getChunkSize();
                else if (count < partialWriter.getChunkSize())
                    count = partialWriter.getChunkSize();

            }

            // Read the whole chunk from the stream
            byte[] buffer = new byte[count];
            SalmonStream inputStream = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, new MemoryStream(encBytes),
                    null, integrity, chunkSize, hmacKey);
            inputStream.seek(alignedPosition, AbsStream.SeekOrigin.Begin);
            bytesRead = inputStream.read(buffer, 0, count);
            inputStream.close();
            System.arraycopy(writeBytes, 0, buffer, alignOffset, writeCount);
            writeBytes = buffer;
            count = bytesRead;
        }
        if (setAllowRangeWrite)
            partialWriter.setAllowRangeWrite(setAllowRangeWrite);
        partialWriter.seek(alignedPosition, AbsStream.SeekOrigin.Begin);
        partialWriter.write(writeBytes, 0, count);
        partialWriter.close();
        pOuts.close();


        // Use SalmonStrem to read from cipher text and test if the write was succesfull
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hmacKey);
        String decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, text.length(), AbsStream.SeekOrigin.Begin);

        Assert.assertEquals(text.substring(0, (int) seek), decText.substring(0, (int) seek));
        Assert.assertEquals(textToWrite, decText.substring((int) seek, (int) seek + writeCount));
        Assert.assertEquals(text.substring((int) seek + writeCount), decText.substring((int) seek + writeCount));
        testCounter(decReader);


        encIns.close();
        decReader.close();
    }

    public static void shouldCreateFileWithoutVault(String text, byte[] key, boolean applyIntegrity, boolean verifyIntegrity, int chunkSize, byte[] hmacKey,
                                                    byte[] filenameNonce, byte[] fileNonce, String outputDir, boolean flipBit, int flipPosition) throws Exception {
        // write file
        IRealFile realDir = new JavaFile(outputDir);
        SalmonFile dir = new SalmonFile(realDir, null);
        String filename = "test_" + SalmonTime.currentTimeMillis() + ".txt";
        SalmonFile newFile = dir.createFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            newFile.setApplyIntegrity(true, hmacKey, chunkSize);
        AbsStream stream = newFile.getOutputStream();
        byte[] testBytes = text.getBytes(Charset.defaultCharset());
        stream.write(testBytes, 0, testBytes.length);
        stream.flush();
        stream.close();
        String realFilePath = newFile.getRealFile().getPath();

        // tamper
        if (flipBit) {
            IRealFile realTmpFile = newFile.getRealFile();
            AbsStream realStream = realTmpFile.getOutputStream();
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
            readFile.setVerifyIntegrity(true, hmacKey);
        SalmonStream inStream = readFile.getInputStream();
        byte[] textBytes = new byte[testBytes.length];
        inStream.read(textBytes, 0, textBytes.length);
        String textString = new String(textBytes, Charset.defaultCharset());
        Assert.assertEquals(text, textString);
    }

    public static void testCounterValue(String text, byte[] key, byte[] nonce, long counter) throws Throwable {
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        byte[] testTextBytes = text.getBytes(Charset.defaultCharset());
        MemoryStream ms = new MemoryStream(testTextBytes);
        SalmonStream stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.setAllowRangeWrite(true);

        // creating enormous files to test is overkill and since the law was made for man and not the other way around
        // we resort to reflection to test this.
        Method incrementCounter = SalmonStream.class.getDeclaredMethod("increaseCounter", long.class);
        incrementCounter.setAccessible(true);
        try {
            incrementCounter.invoke(stream, counter);
        } catch (Exception ex) {
            if (ex.getCause() != null)
                throw ex.getCause();
        } finally {
            stream.close();
        }
    }

    public static byte[] defaultAESCTRTransform(byte[] plainText, byte[] testKeyBytes, byte[] testNonceBytes, boolean encrypt)
            throws Exception {

        if (testNonceBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(testNonceBytes, 0, tmp, 0, testNonceBytes.length);
            testNonceBytes = tmp;
        }
        SecretKeySpec key = new SecretKeySpec(testKeyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        IvParameterSpec ivSpec = new IvParameterSpec(testNonceBytes);
        // mode doesn't make a difference since the encryption is symmetrical
        if (encrypt)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        else
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText);
        return encrypted;
    }

    public static void exportAndImportAuth(String vault, String importFilePath) throws Exception {
        String exportAuthFilePath = vault + File.separator + TestHelper.TEST_EXPORT_DIR;
        String seqFile1 = vault + "/" + TEST_SEQUENCER_FILE1;
        String seqFile2 = vault + "/" + TEST_SEQUENCER_FILE2;

        // emulate 2 different devices with different sequencers
        FileSequencer sequencer1 = new FileSequencer(new JavaFile(seqFile1), new SalmonSequenceParser());
        FileSequencer sequencer2 = new FileSequencer(new JavaFile(seqFile2), new SalmonSequenceParser());

        // set to the first sequencer and create the vault
        SalmonDriveManager.setSequencer(sequencer1);
        SalmonDriveManager.createDrive(vault, TestHelper.TEST_PASSWORD);
        SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        // import a test file
        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        IRealFile fileToImport = SalmonDriveManager.getDrive().getFile(importFilePath, false);
        SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0, null);
        SalmonFile salmonFileA1 = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
        long nonceA1 = BitConverter.toLong(salmonFileA1.getRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        // open with another device (different sequencer) and export auth id
        SalmonDriveManager.setSequencer(sequencer2);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        String authID = SalmonDriveManager.getAuthID();
        boolean success = false;
        try {
            // import a test file should fail because not authorized
            salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            fileToImport = SalmonDriveManager.getDrive().getFile(importFilePath, false);
            fileImporter = new SalmonFileImporter(0, 0, null);
            fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
            success = true;
        } catch (Exception ignored) {

        }
        Assert.assertFalse(success);
        SalmonDriveManager.closeDrive();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        SalmonDriveManager.setSequencer(sequencer1);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        SalmonDriveManager.exportAuthFile(authID, vault, TestHelper.TEST_EXPORT_DIR);
        IRealFile configFile = SalmonDriveManager.getDrive().getFile(exportAuthFilePath, false);
        SalmonFile salmonCfgFile = new SalmonFile(configFile, SalmonDriveManager.getDrive());
        long nonceCfg = BitConverter.toLong(salmonCfgFile.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        fileToImport = SalmonDriveManager.getDrive().getFile(importFilePath, false);
        fileImporter = new SalmonFileImporter(0, 0, null);
        SalmonFile salmonFileA2 = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
        long nonceA2 = BitConverter.toLong(salmonFileA2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        //reopen with second device(sequencer) and import auth file
        SalmonDriveManager.setSequencer(sequencer2);
        SalmonDriveManager.openDrive(vault);
        SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        SalmonDriveManager.importAuthFile(exportAuthFilePath);
        // now import a 3rd file
        salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        fileToImport = SalmonDriveManager.getDrive().getFile(importFilePath, false);
        SalmonFile salmonFileB1 = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
        long nonceB1 = BitConverter.toLong(salmonFileB1.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonFile salmonFileB2 = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
        long nonceB2 = BitConverter.toLong(salmonFileB2.getFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
        SalmonDriveManager.closeDrive();

        Assert.assertEquals(nonceA1, nonceCfg - 1);
        Assert.assertEquals(nonceCfg, nonceA2 - 2);
        Assert.assertNotEquals(nonceA2, nonceB1);
        Assert.assertEquals(nonceB1, nonceB2 - 2);
    }

    public static void testMaxFiles(String vaultDir, String seqFile, String importFile,
                                    byte[] testMaxNonce, long offset, boolean shouldImport) {
        boolean importSuccess = true;
        try {
            FileSequencer sequencer = new FileSequencer(new JavaFile(seqFile), new SalmonSequenceParser()) {
                @Override
                public void initSequence(String driveID, String authID, byte[] startNonce, byte[] maxNonce) throws Exception {
                    long nMaxNonce = BitConverter.toLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                    startNonce = BitConverter.toBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                    maxNonce = BitConverter.toBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                    super.initSequence(driveID, authID, startNonce, maxNonce);
                }
            };
            SalmonDriveManager.setSequencer(sequencer);
            try {
                SalmonDrive drive = SalmonDriveManager.openDrive(vaultDir);
                drive.authenticate(TestHelper.TEST_PASSWORD);
            } catch (Exception ex) {
                SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
            }
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
            SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            JavaFile fileToImport = new JavaFile(importFile);
            SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0, null);
            SalmonFile salmonFile = fileImporter.importFile(fileToImport, salmonRootDir, false, null, 1, 1);
            if (salmonFile != null)
                importSuccess = true;
            else
                importSuccess = false;
        } catch (Exception ex) {
            importSuccess = false;
            ex.printStackTrace();
        }
        Assert.assertEquals(shouldImport, importSuccess);
    }

    public static void testExamples() throws Exception {
        String text = "This is a plaintext that will be used for testing";
        String testFile = "D:/tmp/file.txt";
        IRealFile tFile = new JavaFile(testFile);
        if (tFile.exists())
            tFile.delete();
        byte[] bytes = text.getBytes();
        byte[] key = SalmonGenerator.getSecureRandomBytes(32); // 256 bit key
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8); // 64 bit nonce

        // Example 1: encrypt byte array
        byte[] encBytes = SalmonEncryptor.encrypt(bytes, key, nonce, false);
        // decrypt byte array
        byte[] decBytes = SalmonEncryptor.decrypt(encBytes, key, nonce, false);
        Assert.assertArrayEquals(bytes, decBytes);

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
        // decrypt string
        String decText = SalmonTextEncryptor.decryptString(encText, key, null, true);
        Assert.assertEquals(text, decText);

        // Example 3: encrypt data to an output stream
        AbsStream encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        SalmonStream encrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, encOutStream,
                null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        encrypter.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        encOutStream.position(0);
        byte[] encData = ((MemoryStream) encOutStream).toArray();
        encrypter.flush();
        encrypter.close();
        encOutStream.close();
        //decrypt a stream with encoded data
        AbsStream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        SalmonStream decrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, encInputStream,
                null, false, null, null);
        byte[] decBuffer = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decrypter.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        String decString = new String(decBuffer, 0, bytesRead);
        System.out.println(decString);
        decrypter.close();
        encInputStream.close();
        Assert.assertEquals(text, decString);

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
        SalmonFile encFile = new SalmonFile(new JavaFile(testFile), null);
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);
        AbsStream stream = encFile.getOutputStream();
        // encrypt data and write with a single call
        stream.write(bytes, 0, bytes.length);
        stream.flush();
        stream.close();
        // decrypt an encrypted file
        SalmonFile encFile2 = new SalmonFile(new JavaFile(testFile), null);
        encFile2.setEncryptionKey(key);
        AbsStream stream2 = encFile2.getInputStream();
        byte[] decBuff = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        int encBytesRead = stream2.read(decBuff, 0, decBuff.length);
        String decString2 = new String(decBuff, 0, encBytesRead);
        System.out.println(decString2);
        stream2.close();
        Assert.assertEquals(text, decString2);
    }

    public static void encryptAndDecryptStream(byte[] data, byte[] key, byte[] nonce) throws Exception {
        AbsStream encOutStream = new MemoryStream();
        SalmonStream encrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, encOutStream);
        AbsStream inputStream = new MemoryStream(data);
        inputStream.copyTo(encrypter);
        encOutStream.position(0);
        byte[] encData = ((MemoryStream) encOutStream).toArray();
        encrypter.flush();
        encrypter.close();
        encOutStream.close();
        inputStream.close();

        AbsStream encInputStream = new MemoryStream(encData);
        SalmonStream decrypter = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, encInputStream);
        AbsStream outStream = new MemoryStream();
        decrypter.copyTo(outStream);
        outStream.position(0);
        byte[] decData = ((MemoryStream) outStream).toArray();
        decrypter.close();
        encInputStream.close();
        outStream.close();

        Assert.assertArrayEquals(data, decData);
    }

    public static byte[] getRealFileContents(String filePath) throws Exception {
        IRealFile file = new JavaFile(filePath);
        AbsStream ins = file.getInputStream();
        MemoryStream outs = new MemoryStream();
        ins.copyTo(outs);
        outs.position(0);
        outs.flush();
        outs.close();
        return outs.toArray();
    }

    public static byte[] getRandArray(int size)
    {
        Random random = new Random();
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    public static void encryptAndDecryptByteArray(int size) throws Exception {
        encryptAndDecryptByteArray(size, 1);
    }

    public static void encryptAndDecryptByteArray(int size, int threads) throws Exception {
        byte[] data = TestHelper.getRandArray(size);
        long t1 = SalmonTime.currentTimeMillis();
        byte[] encData = SalmonEncryptor.encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads);
        long t2 = SalmonTime.currentTimeMillis();
        byte[] decData = SalmonEncryptor.decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads);
        long t3 = SalmonTime.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    public static void encryptAndDecryptByteArrayDef(int size) throws Exception {
        byte[] data = TestHelper.getRandArray(size);
        long t1 = SalmonTime.currentTimeMillis();
        byte[] encData = TestHelper.defaultAESCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        long t2 = SalmonTime.currentTimeMillis();
        byte[] decData = TestHelper.defaultAESCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        long t3 = SalmonTime.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    public static void CopyMemory(int size) throws IOException {
        long t1 = SalmonTime.currentTimeMillis();
        byte[] data = TestHelper.getRandArray(size);
        long t2 = SalmonTime.currentTimeMillis();
        byte[] data1 = new byte[data.length];
        System.arraycopy(data, 0, data1, 0, data.length);
        long t3 = SalmonTime.currentTimeMillis();
        System.out.println("gen time: " + (t2 - t1));
        System.out.println("copy time: " + (t3 - t2));

        byte[] mem = new byte[16];
        MemoryStream ms = new MemoryStream(mem);
        ms.write(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 3, 2);
        byte[] output = ms.toArray();
        System.out.println("write: " + Arrays.toString(output));
        byte[] buff = new byte[16];
        ms.position(0);
        ms.read(buff, 1, 4);
        System.out.println("read: " + Arrays.toString(buff));
    }
}