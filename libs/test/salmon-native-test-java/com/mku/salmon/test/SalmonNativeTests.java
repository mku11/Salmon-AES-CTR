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

import com.mku.salmon.*;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.password.PbkdfType;
import com.mku.salmon.password.SalmonPassword;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SalmonNativeTests {

    static int ENC_THREADS = 1;
    static int DEC_THREADS = 1;

    static {
        SalmonPassword.setPbkdfType(PbkdfType.Default);
    }

    @BeforeEach
    public void init() {
        SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);
        // SalmonStream.setAesProviderType(SalmonStream.ProviderType.TinyAES);
    }

    @Test
    public void shouldEncryptAndDecryptNativeTextCompatible() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;//.substring(1, 1 + 2*16+2);
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);

        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        assertArrayEquals(bytes, decBytesDef);

        byte[] encBytes = SalmonCoreTestHelper.nativeCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true,
                SalmonStream.getAesProviderType());
        assertArrayEquals(encBytesDef, encBytes);
        byte[] decBytes = SalmonCoreTestHelper.nativeCTRTransform(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                SalmonStream.getAesProviderType());
        assertArrayEquals(bytes, decBytes);
    }

    @Test
    public void shouldEncryptAndDecryptNativeStreamTextCompatible() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 2; i++)
            plainText += plainText;
        plainText = plainText.substring(0, 16);

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        assertArrayEquals(bytes, decBytesDef);

        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        assertArrayEquals(encBytesDef, encBytes);

        byte[] decBytes = new SalmonDecryptor(DEC_THREADS).decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        assertArrayEquals(bytes, decBytes);
    }

    @Test
    public void shouldEncryptAndDecryptNativeStreamReadBuffersNotAlignedTextCompatible() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 3; i++)
            plainText += plainText;

        plainText = plainText.substring(0, 64 + 6);

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        assertArrayEquals(bytes, decBytesDef);

        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        assertArrayEquals(encBytesDef, encBytes);
        SalmonDecryptor decryptor = new SalmonDecryptor(DEC_THREADS, 32 + 2);
        byte[] decBytes = decryptor.decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        assertArrayEquals(bytes, decBytes);
    }

    @Test
    public void shouldEncryptAndDecryptNativeStreamCompatibleWithIntegrity() throws Exception {
        String plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        int chunkSize = 256 * 1024;
        assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEqualsWithIntegrity(encBytesDef, encBytes, chunkSize);
        byte[] decBytes = new SalmonDecryptor(DEC_THREADS).decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
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
}
