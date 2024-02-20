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

import { BitConverter } from '../../lib/salmon-core/convert/bit_converter.js';
import { SalmonGenerator } from '../../lib/salmon-core/salmon/salmon_generator.js';
import { MemoryStream } from '../../lib/salmon-core/io/memory_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/io/encryption_mode.js';


import { SalmonIntegrityException } from '../../lib/salmon-core/salmon/integrity/salmon_integrity_exception.js';
import { SalmonEncryptor } from '../../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../../lib/salmon-core/salmon/salmon_decryptor.js';

import { SalmonTextEncryptor } from '../../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../../lib/salmon-core/salmon/text/salmon_text_decryptor.js';

import { SalmonIntegrity } from '../../lib/salmon-core/salmon/integrity/salmon_integrity.js';
import { SalmonStream } from '../../lib/salmon-core/salmon/io/salmon_stream.js';
import { ProviderType } from '../../lib/salmon-core/salmon/io/provider_type.js';
import { SalmonDefaultOptions } from '../../lib/salmon-core/salmon/salmon_default_options.js';

import { TestHelper } from './test_helper.js';
import { SalmonSecurityException } from '../../lib/salmon-core/salmon/salmon_security_exception.js';
import { SalmonRangeExceededException } from '../../lib/salmon-core/salmon/salmon_range_exceeded_exception.js';



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

    beforeEach(async () => {
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

        TestHelper.assertArrayEquals(decBytesDef, bytes);
        let encBytes = await new SalmonEncryptor().encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        TestHelper.assertArrayEquals(encBytes, encBytesDef);
        let decBytes = await new SalmonDecryptor().decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        TestHelper.assertArrayEquals(decBytes, bytes);
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
        TestHelper.assertArrayEquals(decBytesDef, bytes);
        const encBytes = await new SalmonEncryptor(threads).encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
            true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        assertArrayEqualsWithIntegrity(encBytesDef, encBytes, chunkSize);
        const decBytes = await new SalmonDecryptor(threads).decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
            true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        TestHelper.assertArrayEquals(decBytes, bytes);
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
            TestHelper.assertArrayEquals(buff2, buff1);
            index += nChunkSize + SalmonGenerator.HASH_RESULT_LENGTH;
        }
        expect(index).toBe(bufferWithIntegrity.length);
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
            await TestHelper.encryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
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
        await ins.close();
        await encWriter.flush();
        await encWriter.close();

        expect(caught).toBeTruthy();
    });

    it('shouldNotWriteToStreamDecryptionMode', async () => {
        let testText = TestHelper.TEST_TEXT;

        let tBuilder = "";
        for (let i = 0; i < 10; i++) {
            tBuilder += testText;
        }
        const plainText = tBuilder;
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
        await ins.close();
        await encWriter.flush();
        await encWriter.close();

        expect(caught).toBeTruthy();
    });

    it('shouldSeekAndReadNoIntegrity', async () => {
        await TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    });

    it('shouldSeekAndTestBlockAndCounter', async () => {
        await TestHelper.seekTestCounterAndBlock(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    });

    it('shouldSeekAndReadWithIntegrity', async () => {

        await TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 0, TestHelper.TEST_HMAC_KEY_BYTES);
    });

    it('shouldSeekAndReadWithIntegrityMultiChunks', async () => {
        await TestHelper.seekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 32, TestHelper.TEST_HMAC_KEY_BYTES);
    });

    it('shouldSeekAndWriteNoIntegrity', async () => {

        await TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16,
            TestHelper.TEST_TEXT_WRITE.length, TestHelper.TEST_TEXT_WRITE, false, 0, null, true);
    });

    it('shouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative', async () => {
        let caught = false;
        try {
            await TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
                TestHelper.TEST_TEXT_WRITE.length, TestHelper.TEST_TEXT_WRITE, false, 0, null, false);
        } catch (ex) {
            if (ex.getCause() instanceof SalmonSecurityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative', async () => {
        let caught = false;
        try {
            await TestHelper.seekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.length, TestHelper.TEST_TEXT_WRITE, false,
                32, TestHelper.TEST_HMAC_KEY_BYTES, true);
        } catch (ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldCatchCTROverflow', async () => {
        let caught = false;
        try {
            await TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER);
        } catch (throwable) {
            console.error(throwable);
            if (throwable instanceof SalmonRangeExceededException || throwable instanceof Error)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldHoldCTRValue', async () => {
        let caught = false;
        try {
            await TestHelper.testCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER - 2);
        } catch (throwable) {
            console.error(throwable);
            if (throwable instanceof SalmonRangeExceededException)
                caught = true;
        }

        expect(caught).toBeFalsy();
    });

    it('shouldCalcHMac256', async () => {
        const bytes = await new TextEncoder().encode(TestHelper.TEST_TEXT);
        const hash = await TestHelper.calculateHMAC(bytes, 0, bytes.length, TestHelper.TEST_HMAC_KEY_BYTES, null);
        console.log(BitConverter.toHex(hash)); // TODO: ToSync
        console.log();
    });

    it('shouldConvert', async () => {
        const num1 = 12564;
        let bytes = BitConverter.toBytes(num1, 4);
        const num2 = BitConverter.toLong(bytes, 0, 4);

        expect(num2).toBe(num1);

        const lnum1 = 56445783493;
        bytes = BitConverter.toBytes(lnum1, 8);
        const lnum2 = BitConverter.toLong(bytes, 0, 8);

        expect(lnum2).toBe(lnum1);

    });

    it('shouldEncryptAndDecryptArrayMultipleThreads', async () => {
        const data = TestHelper.getRandArray(1 * 1024 * 1024 + 4);
        const t1 = Date.now();
        const encData = await new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false);
        const t2 = Date.now();
        const decData = await new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false);
        const t3 = Date.now();

        TestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldEncryptAndDecryptArrayMultipleThreadsIntegrity', async () => {
        SalmonDefaultOptions.setBufferSize(2 * 1024 * 1024);
        const data = TestHelper.getRandArray(1 * 1024 * 1024 + 3);
        let t1 = Date.now();
        const encData = await new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        let t2 = Date.now();
        const decData = await new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        const t3 = Date.now();
        1
        TestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSize', async () => {
        const data = TestHelper.getRandArray(1 * 1024 * 1024);
        const t1 = Date.now();
        const encData = await new SalmonEncryptor(2).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        const t2 = Date.now();
        const decData = await new SalmonDecryptor(2).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        const t3 = Date.now();

        TestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader', async () => {
        const data = TestHelper.getRandArraySame(129 * 1024);
        const t1 = Date.now();
        const encData = await new SalmonEncryptor().encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        const t2 = Date.now();
        const decData = await new SalmonDecryptor().decrypt(encData, TestHelper.TEST_KEY_BYTES,
            null, true, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        const t3 = Date.now();

        TestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldCopyMemory', async () => {
        await TestHelper.copyMemory(4 * 1024 * 1024);
    });

    it('shouldCopyFromToMemoryStream', async () => {
        await TestHelper.copyFromMemStream(1 * 1024 * 1024, 0);
        await TestHelper.copyFromMemStream(1 * 1024 * 1024, 32768);
    });

    it('shouldCopyFromMemoryStreamToSalmonStream', async () => {
        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, null, TestHelper.TEST_HMAC_KEY_BYTES,
            32768);

        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
            32768);

        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await TestHelper.copyFromMemStreamToSalmonStream(1 * 1024 * 1024,
            TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
            32768);
    });
});
