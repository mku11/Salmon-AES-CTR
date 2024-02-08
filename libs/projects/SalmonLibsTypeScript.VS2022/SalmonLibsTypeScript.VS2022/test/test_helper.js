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
import { MemoryStream } from '../lib/io/memory_stream.js';
import { SalmonGenerator } from '../lib/salmon/salmon_generator.js';
import { SalmonEncryptor } from '../lib/salmon/salmon_encryptor.js';
import { SalmonDecryptor } from '../lib/salmon/salmon_decryptor.js';
import { SalmonTransformerFactory } from '../lib/salmon/transform/salmon_transformer_factory.js';
import { HmacSHA256Provider } from '../lib/salmon/integrity/hmac_sha256_provider.js';
import { SalmonIntegrity } from '../lib/salmon/integrity/salmon_integrity.js';
import { RandomAccessStream } from '../lib/io/random_access_stream.js';
import { SalmonAES256CTRTransformer } from '../lib/salmon/transform/salmon_aes256_ctr_transformer.js';
import { SalmonStream} from '../lib/salmon/io/salmon_stream.js';
import { EncryptionMode } from '../lib/salmon/io/encryption_mode.js';

export class TestHelper {
    static TEST_ENC_BUFFER_SIZE = 512 * 1024;
    static TEST_DEC_BUFFER_SIZE = 512 * 1024;

    static TEST_PASSWORD = "test123";
    static TEST_FALSE_PASSWORD = "falsepass";
    static TEST_EXPORT_DIR = "export.slma";

    static MAX_ENC_COUNTER = Math.pow(256, 7);
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
        let decText1 = new TextDecoder().encode(encOuts2.toArray());
        encOuts2.close();
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
            encBytes[encBytes.length / 2] = 0;

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
        writer.flush();
        let bytes = outs.toArray();
        writer.close();
        ins.close();
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
        outs.flush();
        let bytes = outs.toArray();
        reader.close();
        outs.close();
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
        ins.close();
        encWriter.flush();
        encWriter.close();
        let encBytes = outs.toArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        let correctText;
        let decText;

        correctText = plainText.substring(0, 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, RandomAccessStream.SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(0, 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, 6, RandomAccessStream.SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(decReader.getPosition() + 4, decReader.getPosition() + 4 + 4);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 4, 4, RandomAccessStream.SeekOrigin.Current);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(decReader.getPosition() + 6, decReader.getPosition() + 6 + 4);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 6, 4, RandomAccessStream.SeekOrigin.Current);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(decReader.getPosition() + 10, decReader.getPosition() + 10 + 6);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 10, 6, RandomAccessStream.SeekOrigin.Current);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(12, 12 + 8);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 12, 8, RandomAccessStream.SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);

        correctText = plainText.substring(plainText.length() - 14, plainText.length() - 14 + 7);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 14, 7, RandomAccessStream.SeekOrigin.End);

        expect(decText).toBe(correctText);

        correctText = plainText.substring(plainText.length() - 27, plainText.length() - 27 + 12);
        decText = TestHelper.seekAndGetSubstringByRead(decReader, 27, 12, RandomAccessStream.SeekOrigin.End);

        expect(decText).toBe(correctText);
        TestHelper.testCounter(decReader);
        encIns.close();
        decReader.close();
    }

    static async seekTestCounterAndBlock(text, key, iv,
        integrity, chunkSize, hashKey) {

        let tBuilder = "";
        for (let i = 0; i < TestHelper.TEXT_ITERATIONS; i++) {
            tBuilder.append(text);
        }
        let plainText = tBuilder.to();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
            null, integrity, chunkSize, hashKey);
        await ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        let encBytes = outs.toArray();

        // Use SalmonStream to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        for (let i = 0; i < 100; i++) {
            await decReader.setPosition(decReader.getPosition() + 7);
            TestHelper.testCounter(decReader);
        }

        encIns.close();
        decReader.close();
    }

    static testCounter(decReader) {
        let expectedBlock = decReader.getPosition() / SalmonAES256CTRTransformer.BLOCK_SIZE;

        expect(decReader.getBlock()).toBe(expectedBlock);

        let counterBlock = BitConverter.toLong(decReader.getCounter(), SalmonGenerator.NONCE_LENGTH,
            SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
        let expectedCounterValue = decReader.getBlock();

        expect(counterBlock).toBe(expectedCounterValue);

        let nonce = BitConverter.toLong(decReader.getCounter(), 0, SalmonGenerator.NONCE_LENGTH);
        let expectedNonce = BitConverter.toLong(decReader.getNonce(), 0, SalmonGenerator.NONCE_LENGTH);

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
        ins.close();
        encWriter.flush();
        encWriter.close();
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
        await partialWriter.seek(alignedPosition, RandomAccessStream.SeekOrigin.Begin);
        await partialWriter.write(writeBytes, 0, count);
        partialWriter.close();
        pOuts.close();

        // Use SalmonStrem to read from cipher text and test if writing was successful
        let encIns = new MemoryStream(encBytes);
        let decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
            null, integrity, chunkSize, hashKey);
        let decText = TestHelper.seekAndGetSubstringByRead(decReader, 0, text.length(), RandomAccessStream.SeekOrigin.Begin);

        expect(decText.sub(0, seek)).toBe(text.sub(0, seek));

        expect(decText.sub(seek, seek + writeCount)).toBe(textToWrite);

        expect(decText.sub(seek + writeCount)).toBe(text.sub(seek + writeCount));
        TestHelper.testCounter(decReader);

        encIns.close();
        decReader.close();
    }

    static testCounterValue(text, key, nonce, counter) {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.Default);
        let testTextBytes = new TextEncoder().encode(text);
        let ms = new MemoryStream(testTextBytes);
        let stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms,
            null, false, null, null);
        stream.setAllowRangeWrite(true);

        // creating enormous files to test is overkill and since the law was made for man
        // we use reflection to test this.
        //TODO:
        //    Field transformerField = SalmonStream.class.getDeclaredField("transformer");
        //transformerField.setAccessible(true);
        //    SalmonAES256CTRTransformer transformer = (SalmonAES256CTRTransformer) transformerField.get(stream);

        //    Method incrementCounter = SalmonAES256CTRTransformer.class.getDeclaredMethod("increaseCounter", long.class);
        //incrementCounter.setAccessible(true);
        //try {
        //    incrementCounter.invoke(transformer, counter);
        //} catch (Exception ex) {
        //    if (ex.getCause() != null)
        //        throw ex.getCause();
        //}  ly {
        //    stream.close();
        //}
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

        expect(decData.sort()).toEqual(data);
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
        ms.setPosition(0);
        await ms.read(buff, 1, 4);
        console.log("read: " + buff);
    }

    static async copyFromMemStream(size, bufferSize) {
        let testData = TestHelper.getRandArray(size);
        let digest = crypto.subtle.digest("MD5", testData);

        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize, null);
        ms1.close();
        ms2.close();
        let data2 = ms2.toArray();

        expect(data2.length).toBe(testData.length);

        let digest2 = crypto.subtle.digest("MD5", data2);
        ms1.close();
        ms2.close();

        expect(digest2.sort()).toEqual(digest);

    }

    static async copyFromMemStreamToSalmonStream(size, key, nonce,
        integrity, chunkSize, hashKey,
        bufferSize) {

        let testData = TestHelper.getRandArray(size);
        let digest = crypto.subtle.digest("MD5", testData);

        // copy to a mem byte stream
        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize, null);
        ms1.close();

        // encrypt to a memory byte stream
        ms2.getPosition(0);
        let ms3 = new MemoryStream();
        let salmonStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms3,
            null, integrity, chunkSize, hashKey);
        // we always align the writes to the chunk size if we enable integrity
        if (integrity)
            bufferSize = salmonStream.getChunkSize();
        await ms2.copyTo(salmonStream, bufferSize, null);
        salmonStream.close();
        ms2.close();
        let encData = ms3.toArray();

        // decrypt
        ms3 = new MemoryStream(encData);
        ms3.getPosition(0);
        let ms4 = new MemoryStream();
        let salmonStream2 = new SalmonStream(key, nonce, EncryptionMode.Decrypt, ms3,
            null, integrity, chunkSize, hashKey);
        await salmonStream2.copyTo(ms4, bufferSize, null);
        salmonStream2.close();
        ms3.close();
        ms4.setPosition(0);
        let digest2 = crypto.subtle.digest("MD5", ms4.toArray());
        ms4.close();
    }

    static calculateHMAC(bytes, offset, length,
        hashKey, includeData) {
        let salmonIntegrity = new SalmonIntegrity(true, hashKey, null, new HmacSHA256Provider(),
            SalmonGenerator.HASH_RESULT_LENGTH);
        return SalmonIntegrity.calculateHash(TestHelper.hashProvider, bytes, offset, length, hashKey, includeData);
    }
}