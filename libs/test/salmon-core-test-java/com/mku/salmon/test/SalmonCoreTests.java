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
import com.mku.streams.MemoryStream;
import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.SalmonSecurityException;
import com.mku.integrity.IntegrityException;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonCoreTests {

    @BeforeAll
    static void beforeAll() {
		int threads = System.getProperty("ENC_THREADS") != null && !System.getProperty("ENC_THREADS").equals("") ?
			Integer.parseInt(System.getProperty("ENC_THREADS")) : 1;
		
		System.out.println("threads: " + threads);
		//SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        //SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;
		
		SalmonCoreTestHelper.initialize();
		
		ProviderType providerType = ProviderType.Default;
		String aesProviderType = System.getProperty("AES_PROVIDER_TYPE");
		if(aesProviderType != null && !aesProviderType.equals(""))
			providerType = ProviderType.valueOf(aesProviderType);
		System.out.println("ProviderType: " + providerType);

		SalmonStream.setAesProviderType(providerType);
    }

    @AfterAll
    static void afterAll() {
        SalmonCoreTestHelper.close();
    }

    @Test
    public void shouldEncryptAndDecryptText() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;

        String encText = SalmonTextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        String decText = SalmonTextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        assertEquals(plainText, decText);
    }

    @Test
    public void shouldEncryptAndDecryptTextWithHeader() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;

        String encText = SalmonTextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        String decText = SalmonTextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, null, true);
        assertEquals(plainText, decText);
    }

    @Test
    public void shouldEncryptCatchNoKey() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        boolean caught = false;

        try {
            SalmonTextEncryptor.encryptString(plainText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        } catch (SalmonSecurityException ex) {
            // ex.printStackTrace();
            caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptCatchNoNonce() {
        String plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        boolean caught = false;

        try {
            SalmonTextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, null, true);
        } catch (SalmonSecurityException ex) {
            // ex.printStackTrace();
            caught = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptNoHeaderCatchNoNonce() {
        String plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        boolean caught = false;

        try {
            String encText = SalmonTextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
            SalmonTextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, null, false);
        } catch (Exception ex) {
            // ex.printStackTrace();
            caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptCatchNoKey() {
        String plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        boolean caught = false;

        try {
            String encText = SalmonTextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
            SalmonTextDecryptor.decryptString(encText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        } catch (Exception ex) {
            // ex.printStackTrace();
            caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptAndDecryptTextCompatible() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;
//        for (int i = 0; i < 13; i++)
//            plainText += plainText;

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = SalmonCoreTestHelper.getEncryptor().encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(encBytesDef, encBytes);
        byte[] decBytes = SalmonCoreTestHelper.getDecryptor().decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(bytes, decBytes);
    }

    @Test
    public void shouldEncryptAndDecryptTextCompatibleWithIntegrity() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        int threads = 1;
        int chunkSize = 256 * 1024;
        assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = SalmonCoreTestHelper.getEncryptor().encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEqualsWithIntegrity(encBytesDef, encBytes, chunkSize);
        byte[] decBytes = SalmonCoreTestHelper.getDecryptor().decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEquals(bytes, decBytes);
    }

    private void assertArrayEqualsWithIntegrity(byte[] buffer, byte[] bufferWithIntegrity, int chunkSize) {
        int index = 0;
        for (int i = 0; i < buffer.length; i += chunkSize) {
            int nChunkSize = Math.min(chunkSize, buffer.length - i);
            byte[] buff1 = new byte[chunkSize];
            System.arraycopy(buffer, i, buff1, 0, nChunkSize);

            byte[] buff2 = new byte[chunkSize];
            System.arraycopy(bufferWithIntegrity, index + SalmonGenerator.HASH_RESULT_LENGTH, buff2, 0, nChunkSize);

            assertArrayEquals(buff1, buff2);
            index += nChunkSize + SalmonGenerator.HASH_RESULT_LENGTH;
        }
        assertEquals(bufferWithIntegrity.length, index);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamNoBuffersSpecified() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 0, 0,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE + 3, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE + 3,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamAlignedBuffer() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamDecNoAlignedBuffer() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                16 * 2, 16 * 2 + 3,
                false, null, null, false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                false, null, null);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                0, 0,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, SalmonCoreTestHelper.TEST_HEADER,
                64
        );
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                0, 0,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, SalmonCoreTestHelper.TEST_HEADER,
                128
        );
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, 32);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                128, 128, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, null);

    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, null);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);

    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned() throws Exception {
        SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, null
        );
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);

    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldNotReadFromStreamEncryptionMode() throws Exception {
        String testText = SalmonCoreTestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        boolean caught = false;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs,
                null, false, null, null);
        try {
            encWriter.copyTo(outs);
        } catch (Exception ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();

        assertTrue(caught);
    }

    @Test
    public void shouldNotWriteToStreamDecryptionMode() throws Exception {
        String testText = SalmonCoreTestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        boolean caught = false;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytes = SalmonCoreTestHelper.encrypt(inputBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, false, 0, null, null);

        MemoryStream ins = new MemoryStream(encBytes);
        SalmonStream encWriter = new SalmonStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionMode.Decrypt, ins,
                null, false, null, null);
        try {
            ins.copyTo(encWriter);
        } catch (Exception ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();

        assertTrue(caught);
    }

    @Test
    public void shouldSeekAndReadNoIntegrity() throws Exception {
        SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false, 0, null);
    }

    @Test
    public void shouldSeekAndTestBlockAndCounter() throws Exception {
        SalmonCoreTestHelper.seekTestCounterAndBlock(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false, 0, null);
    }

    @Test
    public void shouldSeekAndReadWithIntegrity() throws Exception {
        SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    }

    @Test
    public void shouldSeekAndReadWithIntegrityMultiChunks() throws Exception {
        SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    }

    @Test
    public void shouldSeekAndWriteNoIntegrity() throws Exception {
        SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16,
                SalmonCoreTestHelper.TEST_TEXT_WRITE.length(), SalmonCoreTestHelper.TEST_TEXT_WRITE, false, 0, null, true);
    }

    @Test
    public void shouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 5,
                    SalmonCoreTestHelper.TEST_TEXT_WRITE.length(), SalmonCoreTestHelper.TEST_TEXT_WRITE, false, 0, null, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonSecurityException)
                caught = true;
        }

        assertTrue(caught);

    }

    @Test
    public void shouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative() throws Exception {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    5, SalmonCoreTestHelper.TEST_TEXT_WRITE.length(), SalmonCoreTestHelper.TEST_TEXT_WRITE, false,
                    32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldCatchCTROverflow() {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.testCounterValue(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.MAX_ENC_COUNTER);
        } catch (Throwable throwable) {
            // throwable.printStackTrace();
            if (throwable instanceof SalmonRangeExceededException || throwable instanceof IllegalArgumentException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldHoldCTRValue() {
        boolean caught = false;
        try {
            SalmonCoreTestHelper.testCounterValue(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.MAX_ENC_COUNTER - 1L);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            if (throwable instanceof SalmonRangeExceededException)
                caught = true;
        }

        assertFalse(caught);
    }

    @Test
    public void shouldCalcHMac256() throws Exception {
        byte[] bytes = SalmonCoreTestHelper.TEST_TEXT.getBytes(Charset.defaultCharset());
        byte[] hash = SalmonCoreTestHelper.calculateHMAC(bytes, 0, bytes.length, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
        for (byte b : hash) System.out.print(String.format("%02x", b) + " ");
        System.out.println();
    }

    @Test
    public void shouldConvert() {
        int num1 = 12564;
        byte[] bytes = BitConverter.toBytes(num1, 4);
        int num2 = (int) BitConverter.toLong(bytes, 0, 4);

        assertEquals(num1, num2);

        long lnum1 = 56445783493L;
        bytes = BitConverter.toBytes(lnum1, 8);
        long lnum2 = BitConverter.toLong(bytes, 0, 8);

        assertEquals(lnum1, lnum2);

    }

    @Test
    public void ShouldEncryptAndDecryptArrayMultipleThreads() throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024 + 4);
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.getEncryptor().encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.getDecryptor().decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrity() throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024 + 3);
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.getEncryptor().encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.getDecryptor().decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSize() throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024);
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.getEncryptor().encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.getDecryptor().decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader() throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArraySame(129 * 1024);
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.getEncryptor().encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.getDecryptor().decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES,
                null, true, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        System.out.println("enc time: " + (t2 - t1));
        System.out.println("dec time: " + (t3 - t2));
    }

    @Test
    public void ShouldCopyMemory() throws IOException {
        SalmonCoreTestHelper.CopyMemory(4 * 1024 * 1024);
    }

    @Test
    public void ShouldCopyFromToMemoryStream() throws Exception {
        SalmonCoreTestHelper.copyFromMemStream(1 * 1024 * 1024, 0);
        SalmonCoreTestHelper.copyFromMemStream(1 * 1024 * 1024, 32768);
    }

    @Test
    public void ShouldCopyFromMemoryStreamToSalmonStream() throws Exception {
        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                0);
        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, null, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                32768);

        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 256 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                0);
        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 256 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                32768);

        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                0);
        SalmonCoreTestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                32768);
    }
}
