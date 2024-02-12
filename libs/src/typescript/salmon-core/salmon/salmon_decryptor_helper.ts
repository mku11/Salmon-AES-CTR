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

import { MemoryStream } from "../io/memory_stream.js";
import { SalmonStream } from "./io/salmon_stream.js";
import { EncryptionMode } from "./io/encryption_mode.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";

/**
 * Decrypt the data stream.
 * @param inputStream The Stream to be decrypted.
 * @param start The start position of the stream to be decrypted.
 * @param count The number of bytes to be decrypted.
 * @param outData The buffer with the decrypted data.
 * @param key The AES key to be used.
 * @param nonce The nonce to be used.
 * @param headerData The header data to be used.
 * @param integrity True to verify integrity.
 * @param hashKey The hash key to be used for integrity verification.
 * @param chunkSize The chunk size.
 * @  Thrown if there is an error with the stream.
 * @throws SalmonSecurityException Thrown if there is a security exception with the stream.
 * @throws SalmonIntegrityException Thrown if the stream is corrupt or tampered with.
 */
export async function decryptData(data: Uint8Array, start: number, count: number, outData: Uint8Array,
    key: Uint8Array, nonce: Uint8Array, headerData: Uint8Array | null,
    integrity: boolean, hashKey: Uint8Array | null, chunkSize: number | null, bufferSize: number): Promise<{ startPos: number, endPos: number }> {
    let stream: SalmonStream | null = null;
    let inputStream = new MemoryStream(data);
    let outputStream: MemoryStream | null = null;
    let startPos = null;
    try {
        outputStream = new MemoryStream(outData);
        await outputStream.setPosition(start);
        stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream,
            headerData, integrity, chunkSize, hashKey);
        await stream.setPosition(start);
        startPos = await outputStream.getPosition();
        let totalChunkBytesRead: number = 0;
        // align to the chunksize if available
        let buffSize: number = Math.max(bufferSize, stream.getChunkSize());
        let buff: Uint8Array = new Uint8Array(buffSize);
        let bytesRead: number;
        while ((bytesRead = await stream.read(buff, 0, Math.min(buff.length, count - totalChunkBytesRead))) > 0
            && totalChunkBytesRead < count) {
            await outputStream.write(buff, 0, bytesRead);
            totalChunkBytesRead += bytesRead;
        }
        await outputStream.flush();
    } catch (ex) {
        console.error(ex);
        throw new SalmonSecurityException("Could not decrypt data", ex);
    } finally {
        if (inputStream != null)
            await inputStream.close();
        if (stream != null)
            await stream.close();
        if (outputStream != null)
            await outputStream.close();
    }
    let endPos: number = await outputStream.getPosition();
    return { startPos: startPos, endPos: endPos };
}