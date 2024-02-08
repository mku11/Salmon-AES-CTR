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

import { BitConverter } from '../lib/convert/bit_converter.js';
import { Base64 } from '../lib/convert/base64.js';
import { SalmonGenerator } from '../lib/salmon/salmon_generator.js';
import { HmacSHA256Provider } from '../lib/salmon/integrity/hmac_sha256_provider.js';
import { PBKDF_SHA256, PBKDF_SHA1, getPbkdfAlgoString } from '../lib/salmon/password/isalmon_pbkdf_provider.js';
import { SalmonDefaultPbkdfProvider } from '../lib/salmon/password/salmon_default_pbkdf_provider.js';
import { PbkdfAlgo } from '../lib/salmon/password/pbkdf_algo.js';
import { PbkdfType } from '../lib/salmon/password/pbkdf_type.js';
import { SalmonPassword } from '../lib/salmon/password/salmon_password.js';
import { MemoryStream } from '../lib/io/memory_stream.js';
import { EncryptionMode } from '../lib/salmon/io/encryption_mode.js';


import { SalmonIntegrityException } from '../lib/salmon/integrity/salmon_integrity_exception.js';
import { SalmonEncryptor } from '../lib/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon/salmon_decryptor.js';

import { SalmonTextEncryptor } from '../lib/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../lib/salmon/text/salmon_text_decryptor.js';

import { SalmonIntegrity } from '../lib/salmon/integrity/salmon_integrity.js';
import { SalmonStream } from '../lib/salmon/io/salmon_stream.js';
import { ProviderType } from '../lib/salmon/io/provider_type.js';
import { SalmonDefaultOptions } from '../lib/salmon/salmon_default_options.js';

import { TestHelper } from './test_helper.js';
import { SalmonSecurityException } from '../lib/salmon/salmon_security_exception.js';

const TEST_OUTPUT_DIR = "d:\\tmp\\output";
const TEST_VAULT_DIR = "d:\\tmp\\output\\enc";
const TEST_VAULT2_DIR = "d:\\tmp\\output\\enc2";
const TEST_EXPORT_AUTH_DIR = "d:\\tmp\\output\\export";
const TEST_IMPORT_TINY_FILE = "d:\\tmp\\testdata\\tiny_test.txt";
const TEST_IMPORT_SMALL_FILE = "d:\\tmp\\testdata\\small_test.zip";
const TEST_IMPORT_MEDIUM_FILE = "d:\\tmp\\testdata\\medium_test.zip";
const TEST_IMPORT_LARGE_FILE = "d:\\tmp\\testdata\\large_test.mp4";
const TEST_IMPORT_HUGE_FILE = "d:\\tmp\\testdata\\huge.zip";
const TEST_IMPORT_FILE = TEST_IMPORT_MEDIUM_FILE;

describe('salmon-core', () => {

    beforeEach(() => {
        SalmonStream.setAesProviderType(ProviderType.Default);
        SalmonDefaultOptions.setBufferSize(SalmonIntegrity.DEFAULT_CHUNK_SIZE);
    });

    it('shouldEncryptAndDecryptText', async () => {
        const plainText = TestHelper.TEST_TINY_TEXT;

        const encText = await SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        const decText = await SalmonTextDecryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        expect(decText).toBe(plainText);
    });

    it('shouldEncryptAndDecryptTextWithHeader', async () => {
        const plainText = TestHelper.TEST_TINY_TEXT;

        const encText = await SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        const decText = await SalmonTextDecryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, null, true);
        expect(decText).toBe(plainText);
    });

    it('shouldEncryptCatchNoKey', async () => {
        const plainText = TestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            await SalmonTextEncryptor.encryptString(plainText, null, TestHelper.TEST_NONCE_BYTES, true);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptCatchNoNonce', async () => {
        const plainText = TestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            await SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, null, true);
        } catch (ex) {
            console.error(ex);
            if (ex instanceof SalmonSecurityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptNoHeaderCatchNoNonce', async () => {

        const plainText = TestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            let encText = await SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
            await SalmonTextDecryptor.decryptString(encText, TestHelper.TEST_KEY_BYTES, null, false);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }
        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptCatchNoKey', async () => {

        const plainText = TestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            let encText = await SalmonTextEncryptor.encryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
            await SalmonTextDecryptor.decryptString(encText, null, TestHelper.TEST_NONCE_BYTES, true);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptAndDecryptTextCompatible', async () => {
        let plainText = TestHelper.TEST_TEXT;
        for (let i = 0; i < 1; i++)
            plainText += plainText;

        const bytes = new TextEncoder().encode(plainText);
        const encBytesDef = await TestHelper.defaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        const decBytesDef = await TestHelper.defaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(decBytesDef, bytes);
        let encBytes = await new SalmonEncryptor().encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(encBytes, encBytesDef);
        let decBytes = await new SalmonDecryptor().decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(decBytes, bytes);
    });

    it('shouldEncryptAndDecryptTextCompatibleWithIntegrity', async () => {
        let plainText = TestHelper.TEST_TEXT;
        for (let i = 0; i < 13; i++)
            plainText += plainText;

        const bytes = new TextEncoder().encode(plainText);
        const encBytesDef = await TestHelper.defaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        const decBytesDef = await TestHelper.defaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        const threads = 1;
        const chunkSize = 256 * 1024;
        assertArrayEquals(decBytesDef, bytes);
        const encBytes = await new SalmonEncryptor(threads).encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
            true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEqualsWithIntegrity(encBytesDef, encBytes, chunkSize);
        const decBytes = await new SalmonDecryptor(threads).decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
            true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEquals(decBytes, bytes);
    });

    function assertArrayEqualsWithIntegrity(buffer, bufferWithIntegrity, chunkSize) {
        let index = 0;
        for (let i = 0; i < buffer.length; i += chunkSize) {
            const nChunkSize = Math.min(chunkSize, buffer.length - i);
            const buff1 = new Uint8Array(chunkSize);
            for (let j = 0; j < nChunkSize; j++) {
                buff1[j] = buffer[i + j];
            }

            const buff2 = new Uint8Array(chunkSize);
            for (let j = 0; j < nChunkSize; j++) {
                buff2[j] = bufferWithIntegrity[index + SalmonGenerator.HASH_RESULT_LENGTH + j];
            }
            assertArrayEquals(buff2, buff1);
            index += nChunkSize + SalmonGenerator.HASH_RESULT_LENGTH;
        }
        expect(index).toBe(bufferWithIntegrity.length);
    }

    // jest's toEqual assertion is very slow
    function assertArrayEquals(arr1, arr2) {
        expect(arr1.length).toBe(arr2.length);
        for (let i = 0; i < arr1.length; i++) {
            if (arr1[i] != arr2[i])
                expect(false).toBe(true);
        }
    }

    it('shouldEncryptDecryptUsingStreamNoBuffersSpecified', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 0, 0,
            false, null, null, false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_DEC_BUFFER_SIZE,
            false, null, null, false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE + 3, TestHelper.TEST_DEC_BUFFER_SIZE + 3,
            false, null, null, false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamAlignedBuffer', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
            false, null, null, false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamDecNoAlignedBuffer', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            16 * 2, 16 * 2 + 3,
            false, null, null, false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
            false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
            false, null, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            0, 0,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
            64
        );
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            0, 0,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
            128
        );
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
            true, 0, TestHelper.TEST_HMAC_KEY_BYTES, false, null, 32);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            128, 128, true, 64, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative', async () => {
        let caught = false;
        try {
            await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative', async () => {

        let caught = false;
        try {
            await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned', async () => {
        await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
            true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
        );
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative', async () => {

        let caught = false;
        try {
            await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityNegative', async () => {
        let caught = false;
        try {
            TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                true, null, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative', async () => {
        let caught = false;
        try {
            await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                true, 32, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldNotReadFromStreamEncryptionMode', async () => {

        let testText = TestHelper.TEST_TEXT;

        let tBuilder = "";
        for (let i = 0; i < 10; i++) {
            tBuilder += testText;
        }
        const plainText = tBuilder;
        let caught = false;
        const inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs,
            null, false, null, null);
        try {
            await encWriter.copyTo(outs);
        } catch (ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();

        expect(caught).toBeTruthy();
    });

    it('shouldNotWriteToStreamDecryptionMode', async () => {
        let testText = TestHelper.TEST_TEXT;

        let tBuilder = "";
        for (let i = 0; i < 10; i++) {
            tBuilder += testText;
        }
        const plainText = tBuilder.toString();
        let caught = false;
        let inputBytes = new TextEncoder().encode(plainText);
        let encBytes = await TestHelper.encrypt(inputBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, false, 0, null, null);

        let ins = new MemoryStream(encBytes);
        let encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, EncryptionMode.Decrypt, ins,
            null, false, null, null);
        try {
            await ins.copyTo(encWriter);
        } catch (ex) {
            caught = true;
        }
        ins.close();
        encWriter.flush();
        encWriter.close();

        expect(caught).toBeTruthy();
    });

    //@Test
    //public void shouldSeekAndReadNoIntegrity()  {
    //    TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    //}

    //@Test
    //public void shouldSeekAndTestBlockAndCounter()  {
    //    TestHelper.seekTestCounterAndBlock(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    //}

    //@Test
    //public void shouldSeekAndReadWithIntegrity()  {
    //    TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 0, TestHelper.TEST_HMAC_KEY_BYTES);
    //}

    //@Test
    //public void shouldSeekAndReadWithIntegrityMultiChunks()  {
    //    TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 32, TestHelper.TEST_HMAC_KEY_BYTES);
    //}

    //@Test
    //public void shouldSeekAndWriteNoIntegrity()  {
    //    TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16,
    //            TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false, 0, null, true);
    //}

    //@Test
    //public void shouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative()  {
    //    boolean caught = false;
    //    try {
    //        TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
    //                TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false, 0, null, false);
    //    } catch (IOException ex) {
    //        if (ex.getCause() instanceof SalmonSecurityException)
    //            caught = true;
    //    }

    //    assertTrue(caught);

    //}

    //@Test
    //public void shouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative()  {
    //    boolean caught = false;
    //    try {
    //        TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //                5, TestHelper.TEST_TEXT_WRITE.length(), TestHelper.TEST_TEXT_WRITE, false,
    //                32, TestHelper.TEST_HMAC_KEY_BYTES, true);
    //    } catch (IOException ex) {
    //        if (ex.getCause() instanceof SalmonIntegrityException)
    //            caught = true;
    //    }

    //    assertTrue(caught);
    //}

    //@Test
    //public void shouldCatchCTROverflow() {
    //    boolean caught = false;
    //    try {
    //        TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER);
    //    } catch (Throwable throwable) {
    //        throwablconsole.error(e);
    //        if (throwable instanceof SalmonRangeExceededException || throwable instanceof IllegalArgumentException)
    //            caught = true;
    //    }

    //    assertTrue(caught);
    //}

    //@Test
    //public void shouldHoldCTRValue() {
    //    boolean caught = false;
    //    try {
    //        TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER - 1L);
    //    } catch (Throwable throwable) {
    //        throwablconsole.error(e);
    //        if (throwable instanceof SalmonRangeExceededException)
    //            caught = true;
    //    }

    //    assertFalse(caught);
    //}

    //@Test
    //public void shouldCalcHMac256()  {
    //    byte[] bytes = TestHelper.TEST_TEXT.getBytes(Charset.defaultCharset());
    //    byte[] hash = TestHelper.calculateHMAC(bytes, 0, bytes.length, TestHelper.TEST_HMAC_KEY_BYTES, null);
    //    for (byte b : hash) System.out.print(String.format("%02x", b) + " ");
    //    System.out.println();
    //}

    //@Test
    //public void shouldConvert() {
    //    int num1 = 12564;
    //    byte[] bytes = BitConverter.toBytes(num1, 4);
    //    int num2 = (int) BitConverter.toLong(bytes, 0, 4);

    //    assertEquals(num1, num2);

    //    long lnum1 = 56445783493L;
    //    bytes = BitConverter.toBytes(lnum1, 8);
    //    long lnum2 = BitConverter.toLong(bytes, 0, 8);

    //    assertEquals(lnum1, lnum2);

    //}

    //@Test
    //public void shouldEncryptAndDecryptArrayMultipleThreads()  {
    //    byte[] data = TestHelper.getRandArray(1 * 1024 * 1024 + 4);
    //    long t1 = Date.now();
    //    byte[] encData = new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false);
    //    long t2 = Date.now();
    //    byte[] decData = new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false);
    //    long t3 = Date.now();

    //    assertArrayEquals(data, decData);
    //    System.out.println("enc time: " + (t2 - t1));
    //    System.out.println("dec time: " + (t3 - t2));
    //}

    //@Test
    //public void shouldEncryptAndDecryptArrayMultipleThreadsIntegrity()  {
    //    SalmonDefaultOptions.setBufferSize(2 * 1024 * 1024);
    //    byte[] data = TestHelper.getRandArray(1 * 1024 * 1024 + 3);
    //    long t1 = Date.now();
    //    byte[] encData = new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
    //    long t2 = Date.now();
    //    byte[] decData = new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
    //    long t3 = Date.now();

    //    assertArrayEquals(data, decData);
    //    System.out.println("enc time: " + (t2 - t1));
    //    System.out.println("dec time: " + (t3 - t2));
    //}

    //@Test
    //public void shouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSize()  {
    //    byte[] data = TestHelper.getRandArray(1 * 1024 * 1024);
    //    long t1 = Date.now();
    //    byte[] encData = new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
    //    long t2 = Date.now();
    //    byte[] decData = new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
    //    long t3 = Date.now();

    //    assertArrayEquals(data, decData);
    //    System.out.println("enc time: " + (t2 - t1));
    //    System.out.println("dec time: " + (t3 - t2));
    //}

    //@Test
    //public void shouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader()  {
    //    byte[] data = TestHelper.getRandArraySame(129 * 1024);
    //    long t1 = Date.now();
    //    byte[] encData = new SalmonEncryptor().encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
    //    long t2 = Date.now();
    //    byte[] decData = new SalmonDecryptor().decrypt(encData, TestHelper.TEST_KEY_BYTES,
    //            null, true, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
    //    long t3 = Date.now();

    //    assertArrayEquals(data, decData);
    //    System.out.println("enc time: " + (t2 - t1));
    //    System.out.println("dec time: " + (t3 - t2));
    //}

    //@Test
    //public void shouldCopyMemory()  {
    //    TestHelper.copyMemory(4 * 1024 * 1024);
    //}

    //@Test
    //public void shouldCopyFromToMemoryStream()  {
    //    TestHelper.copyFromMemStream(1 * 1024 * 1024, 0);
    //    TestHelper.copyFromMemStream(1 * 1024 * 1024, 32768);
    //}

    //@Test
    //public void shouldCopyFromMemoryStreamToSalmonStream()  {
    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
    //            0);
    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
    //            32768);

    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
    //            0);
    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
    //            32768);

    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
    //            0);
    //    TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
    //            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
    //            true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
    //            32768);
    //}
});
