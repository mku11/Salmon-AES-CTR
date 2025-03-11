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

import com.mku.salmon.Decryptor;
import com.mku.salmon.Encryptor;
import com.mku.salmon.password.Password;
import com.mku.salmon.password.PbkdfType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.EncryptionFormat;
import com.mku.salmon.streams.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SalmonNativeTests {

    static int ENC_THREADS = 1;
    static int DEC_THREADS = 1;

    static {
        Password.setPbkdfType(PbkdfType.Default);
    }

    @BeforeEach
    public void init() {
        ProviderType providerType = ProviderType.Aes;
        String aesProviderType = System.getProperty("AES_PROVIDER_TYPE");
        if (aesProviderType != null && !aesProviderType.equals(""))
            providerType = ProviderType.valueOf(aesProviderType);
        int threads = System.getProperty("ENC_THREADS") != null && !System.getProperty("ENC_THREADS").equals("") ?
                Integer.parseInt(System.getProperty("ENC_THREADS")) : 1;

        System.out.println("ProviderType: " + providerType);
        System.out.println("threads: " + threads);

        AesStream.setAesProviderType(providerType);
        ENC_THREADS = threads;
        DEC_THREADS = threads;
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
                AesStream.getAesProviderType());
        assertArrayEquals(encBytesDef, encBytes);
        byte[] decBytes = SalmonCoreTestHelper.nativeCTRTransform(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                AesStream.getAesProviderType());
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
        byte[] decBytesDef = SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        assertArrayEquals(bytes, decBytesDef);

        byte[] encBytes = new Encryptor(ENC_THREADS).encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        assertArrayEquals(encBytesDef, encBytes);

        byte[] decBytes = new Decryptor(DEC_THREADS).decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
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

        byte[] encBytes = new Encryptor(ENC_THREADS).encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        assertArrayEquals(encBytesDef, encBytes);
        Decryptor decryptor = new Decryptor(DEC_THREADS, 32 + 2);
        byte[] decBytes = decryptor.decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        assertArrayEquals(bytes, decBytes);
    }
}
