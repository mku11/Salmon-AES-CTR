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
import { MemoryStream } from '../../lib/salmon-core/io/memory_stream.js';
import { SalmonGenerator } from '../../lib/salmon-core/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../../lib/salmon-core/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../../lib/salmon-core/salmon/salmon_decryptor.js';
import { SalmonTransformerFactory } from '../../lib/salmon-core/salmon/transform/salmon_transformer_factory.js';
import { HmacSHA256Provider } from '../../lib/salmon-core/salmon/integrity/hmac_sha256_provider.js';
import { SalmonIntegrity } from '../../lib/salmon-core/salmon/integrity/salmon_integrity.js';
import { SeekOrigin } from '../../lib/salmon-core/io/random_access_stream.js';
import { SalmonAES256CTRTransformer } from '../../lib/salmon-core/salmon/transform/salmon_aes256_ctr_transformer.js';
import { SalmonStream } from '../../lib/salmon-core/salmon/io/salmon_stream.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/io/encryption_mode.js';
import { ProviderType } from '../../lib/salmon-core/salmon/io/provider_type.js';

export class TestHelper {
    static TEST_ENC_BUFFER_SIZE = 512 * 1024;
    static TEST_DEC_BUFFER_SIZE = 512 * 1024;

    static TEST_PASSWORD = "test123";
    static TEST_FALSE_PASSWORD = "falsepass";
    static TEST_EXPORT_DIR = "export.slma";

    // Javascript has its own limit on safe math 
    static MAX_ENC_COUNTER = Math.min(Math.pow(256, 7), Number.MAX_SAFE_INTEGER + 1);
    // a nonce ready to overflow if a new file is imported
    static TEXT_VAULT_MAX_FILE_NONCE = new Uint8Array([
        0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
    ]);
    static TEST_SEQUENCER_FILE1 = "seq1.xml";
    static TEST_SEQUENCER_FILE2 = "seq2.xml";

    static TEXT_ITERATIONS = 1;
    static TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    static TEST_KEY_BYTES = new TextEncoder().encode(TestHelper.TEST_KEY);
    static TEST_NONCE = "12345678"; // 8 bytes
    static TEST_NONCE_BYTES = new TextEncoder().encode(TestHelper.TEST_NONCE);
    static TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    static TEST_FILENAME_NONCE_BYTES = new TextEncoder().encode(TestHelper.TEST_FILENAME_NONCE);
    static TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    static TEST_HMAC_KEY_BYTES = new TextEncoder().encode(TestHelper.TEST_HMAC_KEY);

    static TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
    static TEST_TINY_TEXT = "test.txt";
    static TEST_TEXT = "This is another test that could be very long if used correctly.";
    static TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

    static hashProvider = new HmacSHA256Provider();

    static async seekAndGetSubstringByRead(reader, seek, readCount, seekOrigin) {
        await reader.seek(seek, seekOrigin);
        let encOuts2 = new MemoryStream();

        let bytes = new Uint8Array(readCount);
        let bytesRead;
        let totalBytesRead = 0;
        while (totalBytesRead < readCount && (bytesRead = await reader.read(bytes, 0, bytes.length)) > 0) {
            // we skip the alignment offset and start reading the bytes we need
            await encOuts2.write(bytes, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        let decText1 = new TextDecoder().decode(encOuts2.toArray());
        await encOuts2.close();
        return decText1;
    }

    static async encryptWriteDecryptRead(text, key, iv,
        encBufferSize, decBufferSize, testIntegrity, chunkSize,
        hashKey, flipBits, header, maxTextLength) {
        let testText = text;

        let tBuilder = "";
        for (let i = 0; i < TestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += testText;
        }
        let plainText = tBuilder;
        if (maxTextLength != null && maxTextLength < plainText.length)
            plainText = plainText.substring(0, maxTextLength);

        let headerLength = 0;
        if (header != null)
            headerLength = new TextEncoder().encode(header).length;
        let inputBytes = new TextEncoder().encode(plainText);
        let encBytes = await TestHelper.encrypt(inputBytes, key, iv, encBufferSize,
            testIntegrity, chunkSize, hashKey, header);
        if (flipBits)
            encBytes[Math.floor(encBytes.length / 2)] = 0;

        // Use SalmonStream to read from cipher byte array and MemoryStream to Write to byte array
        let outputByte2 = await TestHelper.decrypt(encBytes, key, iv, decBufferSize,
            testIntegrity, chunkSize, hashKey, header != null ? headerLength :
            null);
        let decText = new TextDecoder().decode(outputByte2);

        console.log(plainText);
        console.log(decText);

        expect(decText).toBe(plainText);
    }

    static async encrypt(inputBytes, key, iv, bufferSize,
        integrity, chunkSize, hashKey,
        header) {
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let headerData = null;
        if (header != null) {
            headerData = new TextEncoder().encode(header);
            await outs.write(headerData, 0, headerData.length);
        }
        let writer = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
            headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffer size of the memorystream to copy
        {
            await ins.copyTo(writer);
        } else { // use our manual buffer to test
            let bytesRead;
            let buffer = new Uint8Array(bufferSize);
            while ((bytesRead = await ins.read(buffer, 0, buffer.length)) > 0) {
                await writer.write(buffer, 0, bytesRead);
            }
        }
        await writer.flush();
        let bytes = outs.toArray();
        await writer.close();
        await ins.close();
        return bytes;
    }

    static async decrypt(inputBytes, key, iv, bufferSize,
        integrity, chunkSize, hashKey,
        headerLength) {
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let headerData = null;
        if (headerLength != null) {
            headerData = new Uint8Array(headerLength);
            await ins.read(headerData, 0, headerData.length);
        }
        let reader = new SalmonStream(key, iv, EncryptionMode.Decrypt, ins,
            headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
        {
            await reader.copyTo(outs);
        } else { // use our manual buffer to test
            let bytesRead;
            let buffer = new Uint8Array(bufferSize);
            while ((bytesRead = await reader.read(buffer, 0, buffer.length)) > 0) {
                await outs.write(buffer, 0, bytesRead);
            }
        }
        await outs.flush();
        let bytes = outs.toArray();
        await reader.close();
        await outs.close();
        return bytes;
    }

    static async seekAndRead(text, key, iv,
        integrity, chunkSize, hashKey) {
        let testText = text;

        let tBuilder = "";
        for (let i = 0; i < TestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += testText;
        }
        let plainText = tBuilder;

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
            null, integrity, chunkSize, hashKey);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        let correctText;
        let decText;

        correctText = plainText.substring(0, 6);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(0, 6);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 4, await decReader.getPosition() + 4 + 4);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 4, 4, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 6, await decReader.getPosition() + 6 + 4);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 6, 4, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 10, await decReader.getPosition() + 10 + 6);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 10, 6, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(12, 12 + 8);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 12, 8, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);

        correctText = plainText.substring(plainText.length - 14, plainText.length - 14 + 7);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 14, 7, SeekOrigin.End);

        expect(decText).toBe(correctText);

        correctText = plainText.substring(plainText.length - 27, plainText.length - 27 + 12);
        decText = await TestHelper.seekAndGetSubstringByRead(decReader, 27, 12, SeekOrigin.End);

        expect(decText).toBe(correctText);
        await TestHelper.testCounter(decReader);
        await encIns.close();
        await decReader.close();
    }

    static async seekTestCounterAndBlock(text, key, iv,
        integrity, chunkSize, hashKey) {

        let tBuilder = "";
        for (let i = 0; i < TestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += text;
        }
        const plainText = tBuilder;

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
            null, integrity, chunkSize, hashKey);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // Use SalmonStream to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        for (let i = 0; i < 100; i++) {
            await decReader.setPosition(await decReader.getPosition() + 7);
            await TestHelper.testCounter(decReader);
        }

        await encIns.close();
        await decReader.close();
    }

    static async testCounter(decReader) {
        let expectedBlock = Math.floor(await decReader.getPosition() / SalmonAES256CTRTransformer.BLOCK_SIZE);

        expect(await decReader.getBlock()).toBe(expectedBlock);

        let counterBlock = BitConverter.toLong(await decReader.getCounter(), SalmonGenerator.NONCE_LENGTH,
            SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
        let expectedCounterValue = await decReader.getBlock();

        expect(counterBlock).toBe(expectedCounterValue);

        let nonce = BitConverter.toLong(await decReader.getCounter(), 0, SalmonGenerator.NONCE_LENGTH);
        let expectedNonce = BitConverter.toLong(await decReader.getNonce(), 0, SalmonGenerator.NONCE_LENGTH);

        expect(nonce).toBe(expectedNonce);
    }

    static async seekAndWrite(text, key, iv,
        seek, writeCount, textToWrite,
        integrity, chunkSize, hashKey,
        setAllowRangeWrite
    ) {

        let tBuilder = "";
        for (let i = 0; i < TestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += text;
        }
        let plainText = tBuilder;

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
            null, integrity, chunkSize, hashKey);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // partial write
        let writeBytes = new TextEncoder().encode(textToWrite);
        let pOuts = new MemoryStream(encBytes);
        let partialWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, pOuts,
            null, integrity, chunkSize, hashKey);
        let alignedPosition = seek;
        let alignOffset = 0;
        let count = writeCount;

        // set to allow rewrite
        if (setAllowRangeWrite)
            partialWriter.setAllowRangeWrite(setAllowRangeWrite);
        await partialWriter.seek(alignedPosition, SeekOrigin.Begin);
        await partialWriter.write(writeBytes, 0, count);
        await partialWriter.close();
        await pOuts.close();

        // Use SalmonStrem to read from cipher text and test if writing was successful
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        let decText = await TestHelper.seekAndGetSubstringByRead(decReader, 0, text.length, SeekOrigin.Begin);

        expect(decText.substring(0, seek)).toBe(text.substring(0, seek));

        expect(decText.substring(seek, seek + writeCount)).toBe(textToWrite);

        expect(decText.substring(seek + writeCount)).toBe(text.substring(seek + writeCount));
        await TestHelper.testCounter(decReader);

        await encIns.close();
        await decReader.close();
    }

    static async testCounterValue(text, key, nonce, counter) {
        SalmonStream.setAesProviderType(ProviderType.Default);
        let testTextBytes = new TextEncoder().encode(text);
        let ms = new MemoryStream(testTextBytes);
        let stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms,
            null, false, null, null);
        stream.setAllowRangeWrite(true);

        // WORKAROUND: first we need to run an operation that will init the transformer
        let currCounter = await stream.getCounter();

        // creating enormous files to test is overkill and since the law was made for man
        // we execute a "private" method
        try {
            stream.getTransformer().increaseCounter(counter);
        } catch (ex) {
            console.error(ex);
            if (typeof ex.getCause !== 'undefined' && ex.getCause() != null)
                throw ex.getCause();
            else
                throw ex;
        }  finally {
            await stream.close();
        }
    }

    static async defaultAESCTRTransform(plainText, testKeyBytes, testNonceBytes, encrypt) {

        if (testNonceBytes.length < 16) {
            let tmp = new Uint8Array(16);
            for (let i = 0; i < testNonceBytes.length; i++)
                tmp[i] = testNonceBytes[i];
            testNonceBytes = tmp;
        }
        let encSecretKey = await crypto.subtle.importKey(
            "raw", testKeyBytes, "AES-CTR", false, ["encrypt", "decrypt"]);
        let encrypted;
        // mode doesn't make a difference since the encryption is symmetrical
        if (encrypt) {
            encrypted = new Uint8Array(await crypto.subtle.encrypt(
                {
                    name: "AES-CTR",
                    counter: testNonceBytes,
                    length: 64,
                },
                encSecretKey,
                plainText,
            ));
        } else {
            encrypted = new Uint8Array(await crypto.subtle.decrypt(
                {
                    name: "AES-CTR",
                    counter: testNonceBytes,
                    length: 64,
                },
                encSecretKey,
                plainText,
            ));
        }
        return encrypted;
    }

    static async nativeCTRTransform(input, testKeyBytes, testNonceBytes,
        encrypt, providerType) {
        if (testNonceBytes.length < 16) {
            let tmp = new Uint8Array(16);
            for (let i = 0; i < testNonceBytes.length; i++)
                tmp[i] = testNonceBytes[i];
            testNonceBytes = tmp;
        }
        let transformer = SalmonTransformerFactory.create(providerType);
        await transformer.init(testKeyBytes, testNonceBytes);
        let output = new Uint8Array(input.length);
        transformer.resetCounter();
        transformer.syncCounter(0);
        if (encrypt)
            await transformer.encryptData(input, 0, output, 0, input.length);
        else
            await transformer.decryptData(input, 0, output, 0, input.length);
        return output;
    }

    static getRandArray(size) {
        let data = new Uint8Array(size);
        for (let i = 0; i < size; i++) {
            data[i] = Math.floor(Math.random() * 255);
        }
        return data;
    }

    static getRandArraySame(size) {
        // WORKAROUND: cannot seed random so we'll use an increment instead
        let data = new Uint8Array(size);
        for (let i = 0; i < size; i++) {
            data[i] = i % 256;
        }
        return data;
    }

    static encryptAndDecryptByteArray(size, threads = 1, enableLog) {
        let data = TestHelper.getRandArray(size);
        TestHelper.encryptAndDecryptByteArray2(data, threads, enableLog);
    }

    static encryptAndDecryptByteArray2(data, threads = 1, enableLog) {
        let t1 = Date.now();
        let encData = new SalmonEncryptor(threads).encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        let t2 = Date.now();
        let decData = new SalmonDecryptor(threads).decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        let t3 = Date.now();

        expect(decData).toBe(data);
        if (enableLog) {
            console.log("enc time: " + (t2 - t1));
            console.log("dec time: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static encryptAndDecryptByteArrayNative(size, enableLog) {
        let data = TestHelper.getRandArray(size);
        TestHelper.encryptAndDecryptByteArrayNative2(data, enableLog);
    }

    static encryptAndDecryptByteArrayNative2(data, enableLog) {
        let t1 = Date.now();
        let encData = TestHelper.nativeCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true,
            SalmonStream.getAesProviderType());
        let t2 = Date.now();
        let decData = TestHelper.nativeCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
            SalmonStream.getAesProviderType());
        let t3 = Date.now();

        expect(decData).toBe(data);
        if (enableLog) {
            console.log("enc time: " + (t2 - t1));
            console.log("dec time: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static encryptAndDecryptByteArrayDef(size, enableLog) {
        let data = TestHelper.getRandArray(size);
        TestHelper.encryptAndDecryptByteArrayDef2(data, enableLog);
    }

    static encryptAndDecryptByteArrayDef2(data, enableLog) {
        let t1 = Date.now();
        let encData = TestHelper.defaultAESCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        let t2 = Date.now();
        let decData = TestHelper.defaultAESCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        let t3 = Date.now();

        TestHelper.assertArrayEquals(decData, data);
        if (enableLog) {
            console.log("enc: " + (t2 - t1));
            console.log("dec: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static async copyMemory(size) {
        let t1 = Date.now();
        let data = TestHelper.getRandArray(size);
        let t2 = Date.now();
        let t3 = Date.now();
        console.log("gen time: " + (t2 - t1));
        console.log("copy time: " + (t3 - t2));

        let mem = new Uint8Array(16);
        let ms = new MemoryStream(mem);
        await ms.write(new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]), 3, 2);
        let output = ms.toArray();
        console.log("write: " + output);
        let buff = new Uint8Array(16);
        await ms.setPosition(0);
        await ms.read(buff, 1, 4);
        console.log("read: " + buff);
    }

    static async copyFromMemStream(size, bufferSize) {
        let testData = TestHelper.getRandArray(size);
        let digest = await crypto.subtle.digest("SHA-256", testData);

        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize, null);
        await ms1.close();
        await ms2.close();
        let data2 = ms2.toArray();

        expect(data2.length).toBe(testData.length);

        let digest2 = await crypto.subtle.digest("SHA-256", data2);
        await ms1.close();
        await ms2.close();

        TestHelper.assertArrayEquals(digest2, digest);

    }

    static async copyFromMemStreamToSalmonStream(size, key, nonce,
        integrity, chunkSize, hashKey,
        bufferSize) {

        let testData = TestHelper.getRandArray(size);
        let digest = await crypto.subtle.digest("SHA-256", testData); // TODO: ToSync MD5 not avail in subtle

        // copy to a mem byte stream
        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize, null);
        await ms1.close();

        // encrypt to a memory byte stream
        await ms2.setPosition(0);
        let ms3 = new MemoryStream();
        let salmonStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms3,
            null, integrity, chunkSize, hashKey);
        // we always align the writes to the chunk size if we enable integrity
        if (integrity)
            bufferSize = salmonStream.getChunkSize();
        await ms2.copyTo(salmonStream, bufferSize, null);
        await salmonStream.close();
        await ms2.close();
        let encData = ms3.toArray();
        await ms3.close();

        // decrypt
        ms3 = new MemoryStream(encData);
        await ms3.setPosition(0);
        let ms4 = new MemoryStream();
        let salmonStream2 = new SalmonStream(key, nonce, EncryptionMode.Decrypt, ms3,
            null, integrity, chunkSize, hashKey);
        await salmonStream2.copyTo(ms4, bufferSize, null);
        await salmonStream2.close();
        await ms3.close();
        await ms4.setPosition(0);
        let digest2 = await crypto.subtle.digest("SHA-256", ms4.toArray());
        await ms4.close();
    }

    static calculateHMAC(bytes, offset, length,
        hashKey, includeData) {
        let salmonIntegrity = new SalmonIntegrity(true, hashKey, null, new HmacSHA256Provider(),
            SalmonGenerator.HASH_RESULT_LENGTH);
        return SalmonIntegrity.calculateHash(TestHelper.hashProvider, bytes, offset, length, hashKey, includeData);
    }


    // jest's toEqual assertion is very slow
    static assertArrayEquals(arr1, arr2) {
        expect(arr1.length).toBe(arr2.length);
        for (let i = 0; i < arr1.length; i++) {
            if (arr1[i] != arr2[i])
                expect(false).toBe(true);
        }
    }
}