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
import { JsHttpDrive } from '../../lib/salmon-fs/file/js_http_drive.js';
import { JsHttpFile } from '../../lib/salmon-fs/file/js_http_file.js';
import { JsHttpFileStream } from '../../lib/salmon-fs/file/js_http_file_stream.js';
import { InputStreamWrapper } from '../../lib/salmon-core/io/input_stream_wrapper.js';
import { TestHelper } from '../salmon-core/test_helper.js';
import { SalmonTextEncryptor } from '../../lib/salmon-core/salmon/text/salmon_text_encryptor.js';
import { SalmonTextDecryptor } from '../../lib/salmon-core/salmon/text/salmon_text_decryptor.js';
import { SalmonFile } from '../../lib/salmon-fs/salmonfs/salmon_file.js';
import { SalmonDriveManager } from '../../lib/salmon-fs/salmonfs/salmon_drive_manager.js';


export class TsFsTestHelper {
    static TEST_HTTP_TINY_FILE = "tiny_test.txt";
    static TEST_HTTP_SMALL_FILE = "small_test.zip";
    static TEST_HTTP_MEDIUM_FILE = "medium_test.zip";
    static TEST_HTTP_LARGE_FILE = "large_test.mp4";
    static TEST_HTTP_FILE = TsFsTestHelper.TEST_HTTP_MEDIUM_FILE;

    static TEST_HTTP_TINY_FILE_SIZE = 27;
    static TEST_HTTP_TINY_FILE_CONTENTS = "This is a new file created.";
    static TEST_HTTP_TINY_FILE_CHKSUM = "69470e3c51279c8493be3f2e116a27ef620b3791cd51b27f924c589cb014eb92";

    static TEST_HTTP_SMALL_FILE_SIZE = 1814885;
    static TEST_HTTP_SMALL_FILE_CHKSUM = "c3a0ef1598711e35ba2ba54d60d3722ebe0369ad039df324391ff39263edabd4";


    static TEST_HTTP_LARGE_FILE_SIZE = 43315070;
    static TEST_HTTP_LARGE_FILE_CHKSUM = "3aaecd80a8fa3cbe6df8e79364af0412b7da6fa423d14c8c6bd332b32d7626b7";

    static TEST_HTTP_DATA256_FILE = "data256.dat";
    static TEST_HTTP_ENCDATA256_FILE = "encdata256.dat";
    static TEST_ENC_HTTP_FILE = "encfile.dat";

    static SERVER_URL = "http://localhost";
    static SERVER_TEST_URL = TsFsTestHelper.SERVER_URL + "/saltest/test";
    static SERVER_TEST_DATA_URL = TsFsTestHelper.SERVER_URL + "/saltest/test/data";
    static VAULT_DIR_URL = TsFsTestHelper.SERVER_TEST_DATA_URL + "/vault";

    static TEST_FILE = TsFsTestHelper.SERVER_TEST_DATA_URL + "/" + TsFsTestHelper.TEST_ENC_HTTP_FILE;

    static async testExamples() {
		SalmonDriveManager.setVirtualDriveClass(JsHttpDrive);
		
        let text = "This is a plaintext that will be used for testing";
        let bytes = new TextEncoder().encode(text);
        let key = SalmonGenerator.getSecureRandomBytes(32); // 256-bit key
        let nonce = SalmonGenerator.getSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        let encBytes = await (new SalmonEncryptor().encrypt(bytes, key, nonce, false));
        // decrypt byte array
        let decBytes = await (new SalmonDecryptor().decrypt(encBytes, key, nonce, false));
        TestHelper.assertArrayEquals(decBytes, bytes);

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        let encText = await (SalmonTextEncryptor.encryptString(text, key, nonce, true));
        // decrypt string
        let decText = await (SalmonTextDecryptor.decryptString(encText, key, null, true));
        expect(decText).toBe(text);

        // Example 3: encrypt data to an output stream
        let encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.getSecureRandomBytes(8); // always get a fresh nonce!
        // pass the output stream to the SalmonStream
        let encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream,
            null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        await encryptor.write(bytes, 0, bytes.length);
        // encrypted data are now written to the encOutStream.
        await encOutStream.setPosition(0);
        let encData = encOutStream.toArray();
        await encryptor.flush();
        await encryptor.close();
        await encOutStream.close();
        //decrypt a stream with encoded data
        let encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        let decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream,
            null, false, null, null);
        let decBuffer = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() before Read()
        let bytesRead = await decryptor.read(decBuffer, 0, decBuffer.length);
        // encrypted data are now in the decBuffer
        let decString = new TextDecoder().decode(decBuffer.slice(0, bytesRead));
        await decryptor.close();
        await encInputStream.close();
        expect(decString).toBe(text);

        // Example 4: decrypt a file from an HTTP URL (readonly)
        let httpText = "This is a file with some contents";
        let httpKey = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256-bit key
        let httpKeyBytes = new TextEncoder().encode(httpKey);
        let encFile2 = new SalmonFile(new JsHttpFile(TsFsTestHelper.TEST_FILE), null);
        encFile2.setEncryptionKey(httpKeyBytes);
        let stream2 = await encFile2.getInputStream();
        let decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        let encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        let decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
        await stream2.close();
        expect(decString2).toBe(httpText);

        // Example 5: or decrypt a file from an HTTP URL drive (readonly)
        await SalmonDriveManager.openDrive(TsFsTestHelper.VAULT_DIR_URL);
        await SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        let virtualRoot = await SalmonDriveManager.getDrive().getVirtualRoot();
        let files = await virtualRoot.listFiles();
        console.log("Listing files in HTTP drive:\n");
        for(let i=0; i<files.length; i++)
			console.log(await files[i].getBaseName() + "\n");
            console.log("\n");
        encFile2 = await virtualRoot.getChild(TsFsTestHelper.TEST_HTTP_TINY_FILE);
        stream2 = await encFile2.getInputStream();
        decBuff = new Uint8Array(1024);
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        encBytesRead = await stream2.read(decBuff, 0, decBuff.length);
        decString2 = new TextDecoder().decode(decBuff.slice(0, encBytesRead));
        await stream2.close();
        expect(decString2).toBe(httpText);
    }

    static async shouldReadFile(urlPath, fileLength, fileContents, chksum) {
        let file = new JsHttpFile(urlPath);
        expect(await file.exists()).toBeTruthy();
        let length = await file.length();
        expect(length).toBe(fileLength);
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        let start = Date.now();
        await stream.copyTo(ms);
        let end = Date.now();
        await ms.flush();
        await ms.setPosition(0);
        let byteContents = ms.toArray();
        expect(byteContents.length).toBe(fileLength);
        await ms.close();
        await stream.close();
        let digest = BitConverter.toHex(new Uint8Array(await crypto.subtle.digest("SHA-256", ms.toArray())));
        expect(digest).toBe(chksum);

        if (fileContents != null) {
            let contents = new TextDecoder().decode(byteContents);
            expect(contents).toBe(fileContents);
        }
    }

    static async seekAndReadFile(data, file, useFileInputStream = false, isEncrypted = false,
        buffersCount = 0, bufferSize = 0, threads = 0, backOffset = 0) {

        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            0, 32, 0, 32,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            220, 8, 2, 8,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            100, 2, 0, 2,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            6, 16, 0, 16,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            50, 40, 0, 40,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            124, 50, 0, 50,
            buffersCount, bufferSize, threads, backOffset);
        await TsFsTestHelper.seekAndReadFileStream(data, file, useFileInputStream, isEncrypted,
            250, 10, 0, 6,
            buffersCount, bufferSize, threads, backOffset);
    }

    static async seekAndReadFileStream(data, file, useFileInputStream = false, isEncrypted = false,
        start, length, readOffset, shouldReadLength,
        buffersCount = 0, bufferSize = 0, threads = 0, backOffset = 0) {
        let buffer = new Uint8Array(length + readOffset);

        let stream;
        if (useFileInputStream && isEncrypted) {
            // multi threaded
            stream = new SalmonFileInputStream(file, buffersCount, bufferSize, threads, backOffset);
            await stream.skip(start);
        } else {
            let fileStream;
            if (isEncrypted) {
                fileStream = await file.getInputStream();
                await fileStream.setPosition(start);
            } else {
                fileStream = new JsHttpFileStream(file);
                await fileStream.setPosition(start);
            }
            stream = new InputStreamWrapper(fileStream);
        }
        let reader = await stream.getReader();
        let res = await reader.read();
        expect(res.value).toBeDefined();
        for (let i = 0; i < length; i++) {
            buffer[readOffset + i] = res.value[i];
        }
        let tdata = new Uint8Array(buffer.length);
        for (let i = 0; i < shouldReadLength; i++) {
            tdata[readOffset + i] = data[start + i];
        }
        TestHelper.assertArrayEquals(tdata, buffer);
    }

}