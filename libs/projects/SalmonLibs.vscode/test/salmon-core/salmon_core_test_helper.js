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

import { BitConverter } from '../../lib/simple-io/convert/bit_converter.js';
import { MemoryStream } from '../../lib/simple-io/streams/memory_stream.js';
import { Generator } from '../../lib/salmon-core/salmon/generator.js';
import { Encryptor } from '../../lib/salmon-core/salmon/encryptor.js';
import { Decryptor } from '../../lib/salmon-core/salmon/decryptor.js';
import { TransformerFactory } from '../../lib/salmon-core/salmon/transform/transformer_factory.js';
import { HmacSHA256Provider } from '../../lib/salmon-core/salmon/integrity/hmac_sha256_provider.js';
import { Integrity } from '../../lib/salmon-core/salmon/integrity/integrity.js';
import { SeekOrigin } from '../../lib/simple-io/streams/random_access_stream.js';
import { AESCTRTransformer } from '../../lib/salmon-core/salmon/transform/aes_ctr_transformer.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { ProviderType } from '../../lib/salmon-core/salmon/streams/provider_type.js';
import { EncryptionMode } from '../../lib/salmon-core/salmon/streams/encryption_mode.js';
import { EncryptionFormat } from '../../lib/salmon-core/salmon/streams/encryption_format.js';
import { Platform, OSType } from '../../lib/simple-io/platform/platform.js';
import { NativeProxy } from '../../lib/salmon-core/salmon/bridge/native_proxy.js';
import { WebGPU } from '../../lib/salmon-core/salmon/bridge/webgpu.js';

export class SalmonCoreTestHelper {
    static TEST_ENC_BUFFER_SIZE = 512 * 1024;
    static TEST_ENC_THREADS = 1;
    static TEST_DEC_BUFFER_SIZE = 512 * 1024;
    static TEST_DEC_THREADS = 1;

    static TEST_PASSWORD = "test123";
    static TEST_FALSE_PASSWORD = "falsepass";

    // Javascript has its own limit on safe math 
    static MAX_ENC_COUNTER = Math.min(Math.pow(256, 7), Number.MAX_SAFE_INTEGER + 1);
    // a nonce ready to overflow if a new file is imported
    static TEXT_VAULT_MAX_FILE_NONCE = BitConverter.toBytes(Number.MAX_SAFE_INTEGER + 1, 8);

    static TEXT_ITERATIONS = 1;
    static TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    static TEST_KEY_BYTES = new TextEncoder().encode(SalmonCoreTestHelper.TEST_KEY);
    static TEST_NONCE = "12345678"; // 8 bytes
    static TEST_NONCE_BYTES = new TextEncoder().encode(SalmonCoreTestHelper.TEST_NONCE);
    static TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    static TEST_FILENAME_NONCE_BYTES = new TextEncoder().encode(SalmonCoreTestHelper.TEST_FILENAME_NONCE);
    static TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    static TEST_HMAC_KEY_BYTES = new TextEncoder().encode(SalmonCoreTestHelper.TEST_HMAC_KEY);

    static TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
    static TEST_TINY_TEXT = "test.txt";
    static TEST_TEXT = "This is another test that could be very long if used correctly.";
    static TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

    static hashProvider;
    static encryptor;
    static decryptor;

	static prjPath = "../../../../projects";
    static winPath = "/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll";
    // static macPath = "/salmon-libs-xcode-macos/salmon/DerivedData/salmon/Build/Products/Release/libsalmon.dylib";
    static macPath = "/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/libsalmon.dylib";
    static linuxPath = "/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/libsalmon.so";
	
    static initialize() {
        let enableGPU = PARAMS["AES_PROVIDER_TYPE"] == "AesGPU";
        WebGPU.enable(enableGPU);
        WebGPU.enableLog(enableGPU);
        
        SalmonCoreTestHelper.hashProvider = new HmacSHA256Provider();
        SalmonCoreTestHelper.encryptor = new Encryptor(SalmonCoreTestHelper.TEST_ENC_THREADS);
        SalmonCoreTestHelper.decryptor = new Decryptor(SalmonCoreTestHelper.TEST_DEC_THREADS);
		
		let platformOS = Platform.getOS();
		switch(platformOS) {
			case OSType.Linux:
				NativeProxy.setLibraryPath(SalmonCoreTestHelper.prjPath + SalmonCoreTestHelper.linuxPath);
				break;
			case OSType.Windows:
				NativeProxy.setLibraryPath(SalmonCoreTestHelper.prjPath + SalmonCoreTestHelper.winPath);
				break;
			case OSType.Darwin:
				NativeProxy.setLibraryPath(SalmonCoreTestHelper.prjPath + SalmonCoreTestHelper.macPath);
				break;
		}
    }
	
    static close() {
		if(SalmonCoreTestHelper.encryptor)
			SalmonCoreTestHelper.encryptor.close();
		if(SalmonCoreTestHelper.decryptor)
			SalmonCoreTestHelper.decryptor.close();
    }

    static getEncryptor() {
        return SalmonCoreTestHelper.encryptor;
    }

    static getDecryptor() {
        return SalmonCoreTestHelper.decryptor;
    }

    static isGPUEnabled() {
		return SalmonCoreTestHelper.ENABLE_GPU;
	}

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
        hashKey, flipBits, maxTextLength) {
        let testText = text;

        let tBuilder = "";
        for (let i = 0; i < SalmonCoreTestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += testText;
        }
        let plainText = tBuilder;
        if (maxTextLength != null && maxTextLength < plainText.length)
            plainText = plainText.substring(0, maxTextLength);

        let inputBytes = new TextEncoder().encode(plainText);
        let encBytes = await SalmonCoreTestHelper.encrypt(inputBytes, key, iv, encBufferSize,
            testIntegrity, chunkSize, hashKey);
        if (flipBits)
            encBytes[Math.floor(encBytes.length / 2)] = 0;

        // Use AesStream to read from cipher byte array and MemoryStream to Write to byte array
        let outputByte2 = await SalmonCoreTestHelper.decrypt(encBytes, key, iv, decBufferSize,
            testIntegrity, chunkSize, hashKey);
        let decText = new TextDecoder().decode(outputByte2);

        console.log(plainText);
        console.log(decText);

        expect(decText).toBe(plainText);
    }

    static async encrypt(inputBytes, key, iv, bufferSize,
        integrity, chunkSize, hashKey) {
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let writer = new AesStream(key, iv, EncryptionMode.Encrypt, outs,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);

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
        integrity, chunkSize, hashKey) {
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let reader = new AesStream(key, iv, EncryptionMode.Decrypt, ins,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);

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
        for (let i = 0; i < SalmonCoreTestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += testText;
        }
        let plainText = tBuilder;

        // Use AesStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new AesStream(key, iv, EncryptionMode.Encrypt, outs,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new AesStream(key, iv, EncryptionMode.Decrypt, encIns,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        let correctText;
        let decText;

        correctText = plainText.substring(0, 6);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(0, 6);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 4, await decReader.getPosition() + 4 + 4);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 4, 4, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 6, await decReader.getPosition() + 6 + 4);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 6, 4, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(await decReader.getPosition() + 10, await decReader.getPosition() + 10 + 6);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 10, 6, SeekOrigin.Current);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(12, 12 + 8);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 12, 8, SeekOrigin.Begin);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);

        correctText = plainText.substring(plainText.length - 14, plainText.length - 14 + 7);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 14, 7, SeekOrigin.End);

        expect(decText).toBe(correctText);

        correctText = plainText.substring(plainText.length - 27, plainText.length - 27 + 12);
        decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 27, 12, SeekOrigin.End);

        expect(decText).toBe(correctText);
        await SalmonCoreTestHelper.testCounter(decReader);
        await encIns.close();
        await decReader.close();
    }

    static async seekTestCounterAndBlock(text, key, iv,
        integrity, chunkSize, hashKey) {

        let tBuilder = "";
        for (let i = 0; i < SalmonCoreTestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += text;
        }
        const plainText = tBuilder;

        // Use AesStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new AesStream(key, iv, EncryptionMode.Encrypt, outs,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // Use AesStream to read from cipher text and seek and read to different positions in the stream
        let encIns = new MemoryStream(encBytes);
        let decReader = new AesStream(key, iv, EncryptionMode.Decrypt, encIns,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        for (let i = 0; i < 100; i++) {
            await decReader.setPosition(await decReader.getPosition() + 7);
            await SalmonCoreTestHelper.testCounter(decReader);
        }

        await encIns.close();
        await decReader.close();
    }

    static async testCounter(decReader) {
        let expectedBlock = Math.floor(await decReader.getPosition() / AESCTRTransformer.BLOCK_SIZE);

        expect(await decReader.getBlock()).toBe(expectedBlock);

        let counterBlock = BitConverter.toLong(await decReader.getCounter(), Generator.NONCE_LENGTH,
            Generator.BLOCK_SIZE - Generator.NONCE_LENGTH);
        let expectedCounterValue = await decReader.getBlock();

        expect(counterBlock).toBe(expectedCounterValue);

        let nonce = BitConverter.toLong(await decReader.getCounter(), 0, Generator.NONCE_LENGTH);
        let expectedNonce = BitConverter.toLong(await decReader.getNonce(), 0, Generator.NONCE_LENGTH);

        expect(nonce).toBe(expectedNonce);
    }

    static async seekAndWrite(text, key, iv,
        seek, writeCount, textToWrite,
        integrity, chunkSize, hashKey,
        setAllowRangeWrite
    ) {

        let tBuilder = "";
        for (let i = 0; i < SalmonCoreTestHelper.TEXT_ITERATIONS; i++) {
            tBuilder += text;
        }
        let plainText = tBuilder;

        // Use AesStream read from text byte array and MemoryStream to Write to byte array
        let inputBytes = new TextEncoder().encode(plainText);
        let ins = new MemoryStream(inputBytes);
        let outs = new MemoryStream();
        let encWriter = new AesStream(key, iv, EncryptionMode.Encrypt, outs,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        await ins.copyTo(encWriter);
        await ins.close();
        await encWriter.flush();
        await encWriter.close();
        let encBytes = outs.toArray();

        // partial write
        let writeBytes = new TextEncoder().encode(textToWrite);
        let pOuts = new MemoryStream(encBytes);
        let partialWriter = new AesStream(key, iv, EncryptionMode.Encrypt, pOuts,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
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
        let decReader = new AesStream(key, iv, EncryptionMode.Decrypt, encIns,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        let decText = await SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, text.length, SeekOrigin.Begin);

        expect(decText.substring(0, seek)).toBe(text.substring(0, seek));

        expect(decText.substring(seek, seek + writeCount)).toBe(textToWrite);

        expect(decText.substring(seek + writeCount)).toBe(text.substring(seek + writeCount));
        await SalmonCoreTestHelper.testCounter(decReader);

        await encIns.close();
        await decReader.close();
    }

    static async testCounterValue(text, key, nonce, counter) {
        let ms = new MemoryStream();
        let stream = new AesStream(key, nonce, EncryptionMode.Encrypt, ms,
            EncryptionFormat.Salmon);
        stream.setAllowRangeWrite(true);

        // WORKAROUND: first we need to run an operation that will init the transformer
        let currCounter = await stream.getCounter();

        // creating enormous files to test is overkill and since the law was made for man
        // we execute a "private" method
        try {
            stream.getTransformer().increaseCounter(counter);
        } catch (ex) {
            console.error(ex);
            if (typeof ex.getCause !== 'undefined' && ex.getCause())
                throw ex.getCause();
            else
                throw ex;
        } finally {
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
        let transformer = TransformerFactory.create(providerType);
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
        // for simple random data Math.random is very slow 
        // so we use the crypto, note: there is a limit of 2^16 bytes
        let data = new Uint8Array(size);
        let buffer = new Uint8Array(1 << 16);
        let count = 0;
        while(count < size) {
            crypto.getRandomValues(buffer);
            for (let i = 0; i < buffer.length && i + count < size; i++) {
                data[i + count] = buffer[i];
            }
            count += buffer.length;
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

    static async encryptAndDecryptByteArray(size, enableLog) {
        let data = SalmonCoreTestHelper.getRandArray(size);
        await SalmonCoreTestHelper.encryptAndDecryptByteArray2(data, enableLog);
    }

    static async encryptAndDecryptByteArray2(data, enableLog) {
        let t1 = Date.now();
        let encData = await SalmonCoreTestHelper.getEncryptor().encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        let t2 = Date.now();
        let decData = await SalmonCoreTestHelper.getDecryptor().decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        let t3 = Date.now();

        await SalmonCoreTestHelper.assertLargeArrayEquals(decData, data);
        if (enableLog) {
            console.log("enc time: " + (t2 - t1));
            console.log("dec time: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static async encryptAndDecryptByteArrayNative(size, enableLog) {
        let data = SalmonCoreTestHelper.getRandArray(size);
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative2(data, enableLog);
    }

    static async encryptAndDecryptByteArrayNative2(data, enableLog) {
        let t1 = Date.now();
        let encData = await SalmonCoreTestHelper.nativeCTRTransform(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true,
            AesStream.getAesProviderType());
        let t2 = Date.now();
        let decData = await SalmonCoreTestHelper.nativeCTRTransform(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
            AesStream.getAesProviderType());
        let t3 = Date.now();

        await SalmonCoreTestHelper.assertLargeArrayEquals(decData, data);
        if (enableLog) {
            console.log("enc time: " + (t2 - t1));
            console.log("dec time: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static async encryptAndDecryptByteArrayDef(size, enableLog) {
        let data = SalmonCoreTestHelper.getRandArray(size);
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayDef2(data, enableLog);
    }

    static async encryptAndDecryptByteArrayDef2(data, enableLog) {
        let t1 = Date.now();
        let encData = await SalmonCoreTestHelper.defaultAESCTRTransform(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        let t2 = Date.now();
        let decData = await SalmonCoreTestHelper.defaultAESCTRTransform(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        let t3 = Date.now();

        await SalmonCoreTestHelper.assertLargeArrayEquals(decData, data);
        if (enableLog) {
            console.log("enc: " + (t2 - t1));
            console.log("dec: " + (t3 - t2));
            console.log("Total: " + (t3 - t1));
        }
    }

    static async copyMemory(size) {
        let t1 = Date.now();
        let data = SalmonCoreTestHelper.getRandArray(size);
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
        let testData = SalmonCoreTestHelper.getRandArray(size);
        let digest = await SalmonCoreTestHelper.getChecksumArray(testData);

        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize);
        await ms1.close();
        await ms2.close();
        let data2 = ms2.toArray();

        expect(data2.length).toBe(testData.length);

        let digest2 = await SalmonCoreTestHelper.getChecksumArray(data2);
        await ms1.close();
        await ms2.close();

        expect(digest2).toBe(digest);
    }

    static async copyFromMemStreamToSalmonStream(size, key, nonce,
        integrity, chunkSize, hashKey,
        bufferSize) {

        let testData = SalmonCoreTestHelper.getRandArray(size);
        let digest = await SalmonCoreTestHelper.getChecksumArray(testData);

        // copy to a mem byte stream
        let ms1 = new MemoryStream(testData);
        let ms2 = new MemoryStream();
        await ms1.copyTo(ms2, bufferSize);
        await ms1.close();

        // encrypt to a memory byte stream
        await ms2.setPosition(0);
        let ms3 = new MemoryStream();
        let aesStream = new AesStream(key, nonce, EncryptionMode.Encrypt, ms3,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        // we always align the writes to the chunk size if we enable integrity
        if (integrity)
            bufferSize = aesStream.getChunkSize();
        await ms2.copyTo(aesStream, bufferSize, null);
        await aesStream.close();
        await ms2.close();
        let encData = ms3.toArray();
        await ms3.close();

        // decrypt
        ms3 = new MemoryStream(encData);
        await ms3.setPosition(0);
        let ms4 = new MemoryStream();
        let aesStream2 = new AesStream(key, nonce, EncryptionMode.Decrypt, ms3,
            EncryptionFormat.Salmon, integrity, hashKey, chunkSize);
        await aesStream2.copyTo(ms4, bufferSize, null);
        await aesStream2.close();
        await ms3.close();
        await ms4.setPosition(0);
        let digest2 = await SalmonCoreTestHelper.getChecksumArray(ms4.toArray());

        expect(digest2).toBe(digest);
        await ms4.close();
    }

    static calculateHMAC(bytes, offset, length, hashKey, includeData) {
        return Integrity.calculateHash(SalmonCoreTestHelper.hashProvider, bytes, offset, length, hashKey, includeData);
    }

    static assertArrayEquals(arr1, arr2) {
        for (let i = 0; i < arr1.length; i++) {
            if (arr1[i] != arr2[i])
                expect(false).toBe(true);
        }
    }

    static async assertLargeArrayEquals(arr1, arr2) {
        // jest's toEqual assertion and simple != operator 
        // are both very slow for large arrays
        // so we validate the checksum first
        // and only if there is a mismatch we get the details
        expect(arr1.length).toBe(arr2.length);
        let hash1 = await SalmonCoreTestHelper.getChecksumArray(arr1);
        let hash2 = await SalmonCoreTestHelper.getChecksumArray(arr2);
        if(hash1 !== hash2) {
            assertArrayEquals(arr1, arr2);
        }
    }

    static async getChecksumArray(data) {
        let buffer = await crypto.subtle.digest("SHA-256", data);
        let arr = new Uint8Array(buffer);
        let digest = BitConverter.toHex(arr);
        return digest;
    }

    static async getChecksumStream(stream) {
        let ms = new MemoryStream();
        try {
            await stream.copyTo(ms);
            let data = ms.toArray();
            let digest = SalmonCoreTestHelper.getChecksumArray(data);
            return digest;
        } finally {
            if (ms)
                await ms.close();
            if (stream) {
                await stream.close();
            }
        }
    }
    
}