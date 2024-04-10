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
import { MemoryStream } from "../streams/memory_stream.js";
import { SalmonStream } from "./streams/salmon_stream.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
/**
 * Decrypt the data.
 * @param {Uint8Array} data The data to be decrypted.
 * @param {number} start The start position of the stream to be decrypted.
 * @param {number} count The number of bytes to be decrypted.
 * @param {Uint8Array} outData The buffer with the decrypted data.
 * @param {Uint8Array} key The AES key to be used.
 * @param {Uint8Array} nonce The nonce to be used.
 * @param {Uint8Array | null} headerData The header data to be used.
 * @param {boolean} integrity True to verify integrity.
 * @param {Uint8Array | null} hashKey The hash key to be used for integrity verification.
 * @param {number} chunkSize The chunk size. Thrown if there is an error with the stream.
 * @returns {Promise<{ startPos: number, endPos: number }>} The number of bytes decrypted.
 * @throws SalmonSecurityException Thrown if there is a security exception with the stream.
 * @throws IntegrityException Thrown if the stream is corrupt or tampered with.
 */
export async function decryptData(data, start, count, outData, key, nonce, headerData, integrity, hashKey, chunkSize, bufferSize) {
    let stream = null;
    let inputStream = new MemoryStream(data);
    let outputStream = null;
    let startPos;
    try {
        outputStream = new MemoryStream(outData);
        await outputStream.setPosition(start);
        stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream, headerData, integrity, chunkSize, hashKey);
        await stream.setPosition(start);
        startPos = await outputStream.getPosition();
        let totalChunkBytesRead = 0;
        // align to the chunksize if available
        let buffSize = Math.max(bufferSize, stream.getChunkSize());
        // set the same buffer size for the internal stream
        stream.setBufferSize(buffSize);
        let buff = new Uint8Array(buffSize);
        let bytesRead;
        while ((bytesRead = await stream.read(buff, 0, Math.min(buff.length, count - totalChunkBytesRead))) > 0
            && totalChunkBytesRead < count) {
            await outputStream.write(buff, 0, bytesRead);
            totalChunkBytesRead += bytesRead;
        }
        await outputStream.flush();
    }
    catch (ex) {
        console.error(ex);
        throw new SalmonSecurityException("Could not decrypt data", ex);
    }
    finally {
        if (inputStream != null)
            await inputStream.close();
        if (stream != null)
            await stream.close();
        if (outputStream != null)
            await outputStream.close();
    }
    let endPos = await outputStream.getPosition();
    return { startPos: startPos, endPos: endPos };
}
