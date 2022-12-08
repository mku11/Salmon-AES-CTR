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
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.BitConverter;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;
import com.mku11.salmonfs.SalmonFileImporter;
import com.mku11.salmonfs.SalmonFileSearcher;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import org.junit.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    public static final long MAX_ENC_COUNTER = (long) Math.pow(256, 7);
    // a nonce ready to overflow if a new file is imported
    public static final byte[] TEXT_VAULT_MAX_FILE_NONCE = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };
    private static final int TEXT_ITERATIONS = 20;
    public static String TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    public static byte[] TEST_KEY_BYTES = TEST_KEY.getBytes(Charset.defaultCharset());
    public static String TEST_NONCE = "12345678"; // 8 bytes
    public static byte[] TEST_NONCE_BYTES = TEST_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    public static byte[] TEST_FILENAME_NONCE_BYTES = TEST_FILENAME_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    public static byte[] TEST_HMAC_KEY_BYTES = TEST_HMAC_KEY.getBytes(Charset.defaultCharset());
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
            is = new FileInputStream(realFile.getAbsolutePath());
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
                                       Boolean ApplyFileIntegrity, Boolean VerifyFileIntegrity,
                                       byte[] vaultNonce) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonDriveManager.setDriveLocation(vaultDir);
        if (!SalmonDriveManager.getDrive().hasConfig()) {
            SalmonDriveManager.getDrive().setPassword(pass);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } else {
            SalmonDriveManager.getDrive().authenticate(pass);
        }
        SalmonDriveManager.getDrive().setEnableIntegrityCheck(integrity);
        if (vaultNonce != null)
            SalmonDriveManager.getDrive().getKey().setVaultNonce(vaultNonce);

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
        SalmonDriveManager.setDriveLocation(vaultDir);
        if (!SalmonDriveManager.getDrive().hasConfig()) {
            SalmonDriveManager.getDrive().setPassword(pass);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } else {
            SalmonDriveManager.getDrive().authenticate(pass);
        }

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
        SalmonDriveManager.setDriveLocation(vaultDir);

        if (!SalmonDriveManager.getDrive().hasConfig()) {
            SalmonDriveManager.getDrive().setPassword(pass);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } else {
            SalmonDriveManager.getDrive().authenticate(pass);
        }

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
        long expectedBlock = decReader.position() / decReader.getBLOCK_SIZE();
        Assert.assertEquals(expectedBlock, decReader.getBlock());

        long counterBlock = BitConverter.toInt64(decReader.getCounter(), SalmonGenerator.getNonceLength(), SalmonGenerator.getBlockSize() - SalmonGenerator.getNonceLength());
        long expectedCounterValue = decReader.getBlock();
        Assert.assertEquals(expectedCounterValue, counterBlock);

        long nonce = BitConverter.toInt64(decReader.getCounter(), 0, SalmonGenerator.getNonceLength());
        long expectedNonce = BitConverter.toInt64(decReader.getNonce(), 0, SalmonGenerator.getNonceLength());
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
        String realFilePath = newFile.getRealFile().getAbsolutePath();

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
        Method incrementCounter = SalmonStream.class.getDeclaredMethod("incrementCounter", long.class);
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
}