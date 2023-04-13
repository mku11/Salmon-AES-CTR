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
import com.mku11.salmon.BitConverter;
import com.mku11.salmon.MaxFileSizeExceededException;
import com.mku11.salmon.SalmonEncryptor;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.SalmonIntegrity;
import com.mku11.salmon.SalmonSecurityException;
import com.mku11.salmon.SalmonTextEncryptor;
import com.mku11.salmon.streams.InputStreamWrapper;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;
//import com.mku11.salmon.vault.sequencer.WinClientSequencer;
import com.mku11.salmonfs.*;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class SalmonJavaTestRunner {

    public static final int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    public static final int ENC_IMPORT_THREADS = 4;
    // set to false out if you're running test cases for android
    public static final boolean enableNativeLib = true;
    private static final String TEST_OUTPUT_DIR = "d:\\tmp\\output";
    private static final String TEST_VAULT_DIR = "d:\\tmp\\output\\enc";
    private static final String TEST_VAULT2_DIR = "d:\\tmp\\output\\enc2";
    private static final String TEST_EXPORT_AUTH_DIR = "d:\\tmp\\output\\export";
    private static final String TEST_IMPORT_TINY_FILE = "d:\\tmp\\testdata\\tiny_test.txt";
    private static final String TEST_IMPORT_SMALL_FILE = "d:\\tmp\\testdata\\small_test.zip";
    private static final String TEST_IMPORT_MEDIUM_FILE = "d:\\tmp\\testdata\\medium_test.zip";
    private static final String TEST_IMPORT_LARGE_FILE = "d:\\tmp\\testdata\\large_test.mp4";
    private static final String TEST_IMPORT_HUGE_FILE = "d:\\tmp\\testdata\\huge.zip";
    private static final String TEST_IMPORT_FILE = TEST_IMPORT_SMALL_FILE;

    static {
        if (enableNativeLib) {
            System.loadLibrary("salmon");
        }
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
//        SalmonStream.setEnableLogDetails(true);
        SalmonGenerator.setPbkdfType(SalmonGenerator.PbkdfType.Default);
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
    }

    @Test
    public void shouldEncryptAndDecryptText() throws Exception {
        String plainText = TestHelper.TEST_TINY_TEXT;

        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        String encText = SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        String decText = SalmonTextEncryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertEquals(plainText, decText);
    }

    @Test
    public void shouldEncryptAndDecryptTextWithHeader() throws Exception {
        String plainText = TestHelper.TEST_TINY_TEXT;

        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        String encText = SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        String decText = SalmonTextEncryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, null, true);
        Assert.assertEquals(plainText, decText);
    }

    @Test
    public void shouldEncryptCatchNoKey() throws Exception {
        String plainText = TestHelper.TEST_TINY_TEXT;
        boolean caught = false;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        try {
            SalmonTextEncryptor.encryptString(plainText, null, TestHelper.TEST_NONCE_BYTES, true);
        } catch (Exception ex) {
            ex.printStackTrace();
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldEncryptCatchNoNonce() {
        String plainText = TestHelper.TEST_TINY_TEXT;
        boolean caught = false;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        try {
            SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, null, true);
        } catch (Exception ex) {
            ex.printStackTrace();
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptNoHeaderCatchNoNonce() {
        String plainText = TestHelper.TEST_TINY_TEXT;
        boolean caught = false;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        try {
            String encText = SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
            SalmonTextEncryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, null, false);
        } catch (Exception ex) {
            ex.printStackTrace();
            caught = true;
        }
        Assert.assertTrue(caught);
    }


    @Test
    public void shouldEncryptDecryptCatchNoKey() {
        String plainText = TestHelper.TEST_TINY_TEXT;
        boolean caught = false;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        try {
            String encText = SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
            SalmonTextEncryptor.decryptString(encText, null, TestHelper.TEST_NONCE_BYTES, true);
        } catch (Exception ex) {
            ex.printStackTrace();
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldEncryptAndDecryptTextCompatible() throws Exception {
        String plainText = TestHelper.TEST_TEXT;
        for (int i = 0; i < 15; i++)
            plainText += plainText;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = TestHelper.defaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = TestHelper.defaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = SalmonEncryptor.encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(encBytesDef, encBytes);
        byte[] decBytes = SalmonEncryptor.decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(bytes, decBytes);
    }

    @Test
    public void shouldEncryptAndDecryptAesInstrTextCompatible() throws Exception {
        String plainText = TestHelper.TEST_TEXT;
        for (int i = 0; i < 8; i++)
            plainText += plainText;
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = TestHelper.defaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = TestHelper.defaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = SalmonEncryptor.encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(encBytesDef, encBytes);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamNoBuffersSpecified() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 0, 0,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_DEC_BUFFER_SIZE,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE + 3, TestHelper.TEST_DEC_BUFFER_SIZE + 3,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamAlignedBuffer() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamDecNoAlignedBuffer() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                16 * 2, 16 * 2 + 3,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamEncNoAlignedBuffer() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                16 * 2 + 3, 16 * 2,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                0, 0,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
                64
        );
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                0, 0,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
                128
        );
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                true, 0, TestHelper.TEST_HMAC_KEY_BYTES, false, null, 32);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                128, 128, true, 64, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);

    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);
    }


    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);


    }


    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned() throws Exception {
        TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
        );
    }


    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);


    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldNotReadFromStreamEncryptionMode() throws Exception {
        String testText = TestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        boolean caught = false;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Encrypt, outs,
                null, false, null, null);
        try {
            encWriter.copyTo(outs);
        } catch (Exception ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldNotWriteToStreamDecryptionMode() throws Exception {
        String testText = TestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        boolean caught = false;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytes = TestHelper.encrypt(inputBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, false, 0, null, null);

        MemoryStream ins = new MemoryStream(encBytes);
        SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Decrypt, ins,
                null, false, null, null);
        try {
            ins.copyTo(encWriter);
        } catch (Exception ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldSeekAndReadNoIntegrity() throws Exception {
        TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    }


    @Test
    public void shouldSeekAndTestBlockAndCounter() throws Exception {
        TestHelper.seekTestCounterAndBlock(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    }


    @Test
    public void shouldSeekAndReadWithIntegrity() throws Exception {
        TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                true, 0, TestHelper.TEST_HMAC_KEY_BYTES);
    }

    @Test
    public void shouldSeekAndReadWithIntegrityMultiChunks() throws Exception {
        TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                true, 32, TestHelper.TEST_HMAC_KEY_BYTES);
    }

    @Test
    public void shouldSeekAndWriteNoIntegrity() throws Exception {
        TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
                TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false, false, 0, null, true);
    }

    @Test
    public void shouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
                    TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false, false, 0, null, false);
        } catch (SalmonSecurityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);

    }

    @Test
    public void shouldSeekAndWriteWithIntegrityAligned() throws Exception {
        TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, true,
                true, 0, TestHelper.TEST_HMAC_KEY_BYTES, true);
    }

    @Test
    public void shouldSeekAndWriteWithIntegrityAlignedMultiChunks() throws Exception {
        TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, true,
                true, 32, TestHelper.TEST_HMAC_KEY_BYTES, true);
    }

    @Test
    public void shouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative() throws Exception {
        boolean caught = false;
        try {
            TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    5, TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, true);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);
    }

    @Test
    public void shouldAuthenticateNegative() throws Exception {
        String vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        try {
            SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_FALSE_PASSWORD);
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }
        Assert.assertTrue(wrongPassword);
    }

    @Test
    public void shouldCatchNotAuthenticatedNegative() throws Exception {
        String vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonDriveManager.closeDrive();
        try {
            SalmonDriveManager.openDrive(vaultDir);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }
        Assert.assertTrue(wrongPassword);

    }

    @Test
    public void shouldAuthenticatePositive() throws Exception {
        String vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        FileSequencer sequencer = new FileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceParser());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonDriveManager.closeDrive();
        try {
            SalmonDriveManager.openDrive(vaultDir);
            SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
            SalmonDriveManager.getDrive().getVirtualRoot();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }
        Assert.assertFalse(wrongPassword);
    }

    @Test
    public void shouldImportAndExportNoIntegrityBitFlipDataNoCatch() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    false, true, 24 + 10, true, null, null);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndExportNoIntegrity() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    false, false, 0, true, false,
                    false);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndSearchFiles() throws Exception {
        TestHelper.importAndSearch(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS);
    }

    @Test
    public void shouldImportAndCopyFile() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndCopy(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, "subdir", false);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndMoveFile() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndCopy(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, "subdir", true);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipData() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, null,
                    null);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertTrue(integrityFailed);
    }


    @Test
    public void shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch() {
        boolean integrityFailed = false;
        boolean failed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, false,
                    null
            );
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        } catch (Exception ex) {
            failed = true;
        }
        Assert.assertFalse(integrityFailed);
        Assert.assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportNoAppliedIntegrityYesVerifyIntegrityNoBitFlipDataShouldCatch() {
        boolean failed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, false,
                    false, true);
        } catch (Exception ex) {
            failed = true;
        }
        Assert.assertTrue(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch() throws Exception {
        boolean failed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, true, 36, false,
                    true, false);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            failed = true;
        }
        Assert.assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrity() throws Exception {
        boolean failed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, false);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            failed = true;
        }
        Assert.assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipHeader() throws Exception {
        boolean integrityFailed = false;
        try {
            TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                    true, true, 20, false,
                    null, null);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            integrityFailed = true;
        }
        Assert.assertTrue(integrityFailed);
    }

    @Test
    public void shouldImportAndExportIntegrity() throws Exception {
        boolean importSuccess;
        try {
            for (int i = 0; i < 10; i++)
                TestHelper.importAndExport(JavaTestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                        ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS,
                        TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                        true, false, 0, true,
                        null, null);
            importSuccess = true;
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            ex.printStackTrace();
            importSuccess = false;
        }
        Assert.assertTrue(importSuccess);

    }


    @Test
    public void shouldCatchVaultMaxFiles() {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);

        String vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        String seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;

        TestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        TestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        TestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = JavaTestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        TestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    @Test
    public void shouldCreateFileWithoutVault() throws Exception {
        TestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR, false, -1);
    }

    @Test
    public void shouldCatchCTROverflow() {
        boolean caught = false;
        try {
            TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            if (throwable instanceof MaxFileSizeExceededException || throwable instanceof IllegalArgumentException)
                caught = true;
        }
        Assert.assertTrue(caught);
    }


    @Test
    public void shouldHoldCTRValue() {
        boolean caught = false;
        try {
            TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER - 1L);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            if (throwable instanceof MaxFileSizeExceededException)
                caught = true;
        }
        Assert.assertFalse(caught);
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught() throws Exception {

        boolean caught = false;
        try {
            TestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                    true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR,
                    true, 45);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        }
        Assert.assertTrue(caught);
    }


    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught() {

        boolean caught = false;
        boolean failed = false;
        try {
            TestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    TestHelper.TEST_HMAC_KEY_BYTES, TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TEST_OUTPUT_DIR, true, 45);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        } catch (Exception ex) {
            failed = true;
        }
        Assert.assertFalse(caught);
        Assert.assertFalse(failed);
    }


    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual() throws Exception {

        boolean caught = false;
        boolean failed = false;
        try {
            TestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TEST_OUTPUT_DIR,
                    true, 24 + 32 + 5);
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            caught = true;
        } catch (Error ex) {
            failed = true;
        }
        Assert.assertFalse(caught);
        Assert.assertTrue(failed);
    }

    @Test
    public void shouldCalcHMac256() throws Exception {
        byte[] bytes = TestHelper.TEST_TEXT.getBytes(Charset.defaultCharset());
        byte[] hmac = SalmonIntegrity.calculateHMAC(bytes, 0, bytes.length, TestHelper.TEST_HMAC_KEY_BYTES, null);
        for (byte b : hmac) System.out.print(String.format("%02x", b) + " ");
        System.out.println();
    }

    @Test
    public void shouldConvert() {
        int num1 = 12564;
        byte[] bytes = BitConverter.toBytes(num1, 4);
        int num2 = (int) BitConverter.toLong(bytes, 0, 4);
        Assert.assertEquals(num1, num2);


        long lnum1 = 56445783493L;
        bytes = BitConverter.toBytes(lnum1, 8);
        long lnum2 = BitConverter.toLong(bytes, 0, 8);
        Assert.assertEquals(lnum1, lnum2);

    }

    @Test
    public void shouldExportAndImportAuth() throws Exception {
        String vault = JavaTestHelper.generateFolder(TEST_VAULT_DIR);
        String importFilePath = TEST_IMPORT_TINY_FILE;
        TestHelper.exportAndImportAuth(vault, importFilePath);
    }

    @Test
    public void testServerSid() throws Exception {
//        boolean res = WinClientSequencer.isServiceAdmin(JavaTestHelper.TEST_SERVICE_PIPE_NAME);
//        Assert.assertTrue(res);
//        Thread.sleep(2000);
    }

    @Test
    public void shouldConnectAndDisconnectToService() throws Exception {
//        for(int i=0; i<12; i++) {
//            SalmonDriveManager.setSequencer(new WinClientSequencer(JavaTestHelper.TEST_SERVICE_PIPE_NAME));
//            SalmonDriveManager.getSequencer().close();
//            Thread.sleep(1000);
//        }
    }


    @Test
    public void shouldParseXML() throws XPathExpressionException {
        XPathFactory factory = XPathFactory.newInstance();
        XPath xPath = factory.newXPath();
        String contents = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<drives>" +
                "<drive driveID=\"86259681097afcc9a56f75664ea8d3dd\" authID=\"46d34ff19deab97fa898b0d1553adc47\" status=\"Active\" nextNonce=\"AAAAAAAAAAA=\" maxNonce=\"f/////////8=\" />" +
                "<drive driveID=\"86259681097afcc9a56f75664ea8d3d1\" authID=\"46d34ff19deab97fa898b0d1553adc47\" status=\"Active\" nextNonce=\"AAAAAAAAAAA=\" maxNonce=\"f/////////8=\" />" +
                "</drives>";
        InputSource inputXML = new InputSource(new StringReader(contents));
        Node drives = (Node) xPath.evaluate("/drives", inputXML, XPathConstants.NODE);
        Assert.assertEquals(2, drives.getChildNodes().getLength());
    }

    @Test
    public void testExamples() throws Exception {
        TestHelper.testExamples();
    }


    @Test
    public void ShouldEncryptAndDecrtypStream() throws Exception {
        byte[] data = TestHelper.getRealFileContents(TEST_IMPORT_FILE);
        TestHelper.encryptAndDecryptStream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
    }

    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceDef() throws Exception {
        System.out.println("SalmonStream Def");
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        TestHelper.encryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE);
    }

    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceDefParallel() throws Exception {
        System.out.println("SalmonStream Def");
        SalmonStream.setProviderType(SalmonStream.ProviderType.Default);
        TestHelper.encryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE, 4);
    }

    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceIntr() throws Exception {
        System.out.println("SalmonStream Intr");
        SalmonStream.setProviderType(SalmonStream.ProviderType.AesIntrinsics);
        TestHelper.encryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE);
    }

    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceIntrParallel() throws Exception {
        System.out.println("SalmonStream Intr");
        SalmonStream.setProviderType(SalmonStream.ProviderType.AesIntrinsics);
        TestHelper.encryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE, 4);
    }


    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceDefault() throws Exception {
        System.out.println("Default");
        TestHelper.encryptAndDecryptByteArrayDef(TestHelper.TEST_PERF_SIZE);
    }

    @Test
    public void ShouldEncryptAndDecrtyptStreamPerformanceTinyAes() throws Exception {
        System.out.println("SalmonStream TinyAES");
        SalmonStream.setProviderType(SalmonStream.ProviderType.TinyAES);
        TestHelper.encryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE);
    }

    @Test
    public void ShouldEncryptAndDecrtyptArrayMultipleThreads() throws Exception {
        byte[] data = TestHelper.getRandArray(1 * 1024 * 1024);
        //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonEncryptor.encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 
        2);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonEncryptor.decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 
        2);
        long t3 = System.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrity() throws Exception {
        SalmonEncryptor.setBufferSize(2 * 1024 * 1024);
        byte[] data = TestHelper.getRandArray(1 * 1024 * 1024 + 3);
        //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonEncryptor.encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, 2, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonEncryptor.decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, 2, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = System.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrityCustomChunkSize() throws Exception {
        byte[] data = TestHelper.getRandArray(1 * 1024 * 1024);
        //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonEncryptor.encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, 2, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonEncryptor.decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, 2, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t3 = System.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader() throws Exception {
        byte[] data = TestHelper.getRandArray(1 * 1024 * 1024);
        //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonEncryptor.encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                true, 2, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonEncryptor.decrypt(encData, TestHelper.TEST_KEY_BYTES, null, true,
                1, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = System.currentTimeMillis();
        Assert.assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }


    @Test
    public void ShouldCopyMemory() throws IOException {
        TestHelper.CopyMemory(4 * 1024 * 1024);
    }

}
