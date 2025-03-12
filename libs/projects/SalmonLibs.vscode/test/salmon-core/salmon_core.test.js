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
import { MemoryStream } from '../../lib/salmon-core/streams/memory_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/streams/encryption_mode.js';
import { IntegrityException } from '../../lib/salmon-core/salmon/integrity/integrity_exception.js';
import { TextEncryptor } from '../../lib/salmon-core/salmon/text/text_encryptor.js';
import { TextDecryptor } from '../../lib/salmon-core/salmon/text/text_decryptor.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { EncryptionFormat } from '../../lib/salmon-core/salmon/streams/encryption_format.js';
import { ProviderType } from '../../lib/salmon-core/salmon/streams/provider_type.js';
import { SalmonCoreTestHelper } from './salmon_core_test_helper.js';
import { SecurityException } from '../../lib/salmon-core/salmon/security_exception.js';
import { RangeExceededException } from '../../lib/salmon-core/salmon/range_exceeded_exception.js';

describe('salmon-core', () => {
    beforeAll(() => {
        // see jest.setup.js and browser.setup.js for setting params

        // only default provider is supported
        AesStream.setAesProviderType(ProviderType.Default);
        SalmonCoreTestHelper.initialize();
    });

    afterAll(() => {
        console.log("closing");
        SalmonCoreTestHelper.close();
    });

    it('shouldEncryptAndDecryptText', async () => {
        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;

        const encText = await TextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        const decText = await TextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        expect(decText).toBe(plainText);
    });

    it('shouldEncryptAndDecryptTextWithHeader', async () => {
        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;

        const encText = await TextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        const decText = await TextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES);
        expect(decText).toBe(plainText);
    });

    it('shouldEncryptCatchNoKey', async () => {
        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            await TextEncryptor.encryptString(plainText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptCatchNoNonce', async () => {
        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            await TextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, null);
        } catch (ex) {
            console.error(ex);
            if (ex instanceof SecurityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptNoHeaderCatchNoNonce', async () => {

        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            let encText = await TextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
            await TextDecryptor.decryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, null, EncryptionFormat.Generic);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }
        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptCatchNoKey', async () => {

        const plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        let caught = false;

        try {
            let encText = await TextEncryptor.encryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
            await TextDecryptor.decryptString(encText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        } catch (ex) {
            console.error(ex);
            caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptAndDecryptTextCompatible', async () => {
        let plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (let i = 0; i < 14; i++)
            plainText += plainText;

        const bytes = new TextEncoder().encode(plainText);
        const encBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(bytes,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        const decBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        SalmonCoreTestHelper.assertArrayEquals(decBytesDef, bytes);
        let encBytes = await SalmonCoreTestHelper.getEncryptor().encrypt(bytes,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);

        SalmonCoreTestHelper.assertArrayEquals(encBytes, encBytesDef);
        let decBytes = await SalmonCoreTestHelper.getDecryptor().decrypt(encBytes,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);

        SalmonCoreTestHelper.assertArrayEquals(decBytes, bytes);
    });

    it('shouldEncryptDecryptUsingStreamNoBuffersSpecified', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 0, 0,
            false, 0, null, false, null);
    });

    it('shouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE,
            false, 0, null, false, null);
    });

    it('shouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE + 3, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE + 3,
            false, 0, null, false, null);
    });

    it('shouldEncryptDecryptUsingStreamAlignedBuffer', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
            false, 0, null, false, null);
    });

    it('shouldEncryptDecryptUsingStreamDecNoAlignedBuffer', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            16 * 2, 16 * 2 + 3,
            false, 0, null, false, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            false, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            false, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            0, 0,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, SalmonCoreTestHelper.TEST_HEADER,
            64
        );
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            0, 0,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, SalmonCoreTestHelper.TEST_HEADER,
            128
        );
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, 32);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            128, 128, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative', async () => {

        let caught = false;
        try {
            await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned', async () => {
        await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT,
             SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
            true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative', async () => {

        let caught = false;
        try {
            await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityNegative', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.encryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldNotReadFromStreamEncryptionMode', async () => {

        let testText = SalmonCoreTestHelper.TEST_TEXT;

        let tBuilder = "";
        for (let i = 0; i < 10; i++) {
            tBuilder += testText;
        }
        const plainText = tBuilder;
        let caught = false;
        const inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, 
            SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs,
            EncryptionFormat.Salmon);
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
        let testText = SalmonCoreTestHelper.TEST_TEXT;

        let tBuilder = "";
        for (let i = 0; i < 10; i++) {
            tBuilder += testText;
        }
        const plainText = tBuilder;
        let caught = false;
        let inputBytes = new TextEncoder().encode(plainText);
        let encBytes = await SalmonCoreTestHelper.encrypt(inputBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, 
            SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, 
            false, 0, null);

        let ins = new MemoryStream(encBytes);
        let encWriter = new AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
             EncryptionMode.Decrypt, ins, EncryptionFormat.Salmon);
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
        await SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 
            false, 0, null);
    });

    it('shouldSeekAndTestBlockAndCounter', async () => {
        await SalmonCoreTestHelper.seekTestCounterAndBlock(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 
            false, 0, null);
    });

    it('shouldSeekAndReadWithIntegrity', async () => {

        await SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    });

    it('shouldSeekAndReadWithIntegrityMultiChunks', async () => {
        await SalmonCoreTestHelper.seekAndRead(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    });

    it('shouldSeekAndWriteNoIntegrity', async () => {

        await SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16,
            SalmonCoreTestHelper.TEST_TEXT_WRITE.length, SalmonCoreTestHelper.TEST_TEXT_WRITE,
            false, 0, null, true);
    });

    it('shouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 5,
                SalmonCoreTestHelper.TEST_TEXT_WRITE.length, SalmonCoreTestHelper.TEST_TEXT_WRITE, 
                false, 0, null, false);
        } catch (ex) {
            if (ex.getCause() instanceof SecurityException)
                caught = true;
        }

        expect(caught).toBeTruthy();

    });

    it('shouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.seekAndWrite(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, 
                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                5, SalmonCoreTestHelper.TEST_TEXT_WRITE.length, SalmonCoreTestHelper.TEST_TEXT_WRITE, false,
                32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true);
        } catch (ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldCatchCTROverflow', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.testCounterValue(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 
                SalmonCoreTestHelper.MAX_ENC_COUNTER);
        } catch (throwable) {
            console.error(throwable);
            if (throwable instanceof RangeExceededException || throwable instanceof Error)
                caught = true;
        }

        expect(caught).toBeTruthy();
    });

    it('shouldHoldCTRValue', async () => {
        let caught = false;
        try {
            await SalmonCoreTestHelper.testCounterValue(SalmonCoreTestHelper.TEST_TEXT, 
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.MAX_ENC_COUNTER - 2);
        } catch (throwable) {
            console.error(throwable);
            if (throwable instanceof RangeExceededException)
                caught = true;
        }

        expect(caught).toBeFalsy();
    });

    it('shouldCalcHMac256', async () => {
        const bytes = await new TextEncoder().encode(SalmonCoreTestHelper.TEST_TEXT);
        const hash = await SalmonCoreTestHelper.calculateHMAC(bytes, 0, bytes.length, 
            SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
        console.log(BitConverter.toHex(hash));
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

    it('shouldEncryptAndDecryptArrayGeneric', async () => {
        const data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024 + 4);
        const t1 = Date.now();
        const encData = await SalmonCoreTestHelper.getEncryptor().encrypt(data, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Generic);
        const t2 = Date.now();
        const decData = await SalmonCoreTestHelper.getDecryptor().decrypt(encData, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Generic);
        const t3 = Date.now();

        SalmonCoreTestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldEncryptAndDecryptArrayIntegrity', async () => {
        const data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024 + 3);
        let t1 = Date.now();
        const encData = await SalmonCoreTestHelper.getEncryptor().encrypt(data, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        let t2 = Date.now();
        const decData = await SalmonCoreTestHelper.getDecryptor().decrypt(encData, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        const t3 = Date.now();
        1
        SalmonCoreTestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });

    it('shouldEncryptAndDecryptArrayIntegrityCustomChunkSize', async () => {
        const data = SalmonCoreTestHelper.getRandArray(1 * 1024 * 1024);
        const t1 = Date.now();
        const encData = await SalmonCoreTestHelper.getEncryptor().encrypt(data, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32);
        const t2 = Date.now();
        const decData = await SalmonCoreTestHelper.getDecryptor().decrypt(encData, 
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 32);
        const t3 = Date.now();

        SalmonCoreTestHelper.assertArrayEquals(data, decData);
        console.log("enc time: " + (t2 - t1));
        console.log("dec time: " + (t3 - t2));
    });


    it('shouldCopyMemory', async () => {
        await SalmonCoreTestHelper.copyMemory(4 * 1024 * 1024);
    });

    it('shouldCopyFromToMemoryStream', async () => {
        await SalmonCoreTestHelper.copyFromMemStream(1 * 1024 * 1024, 0);
        await SalmonCoreTestHelper.copyFromMemStream(1 * 1024 * 1024, 32768);
    });

    it('shouldCopyFromMemoryStreamToAesStream', async () => {
        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            32768);

        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 256 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 256 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            32768);

        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            0);
        await SalmonCoreTestHelper.copyFromMemStreamToAesStream(1 * 1024 * 1024,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
            32768);
    });
});
