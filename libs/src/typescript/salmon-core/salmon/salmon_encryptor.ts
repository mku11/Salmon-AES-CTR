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

import { BitConverter } from "../convert/bit_converter.js";
import { MemoryStream } from "../io/memory_stream.js";
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { SalmonStream } from "./io/salmon_stream.js";
import { EncryptionMode } from "./io/encryption_mode.js";
import { SalmonGenerator } from "./salmon_generator.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";

/**
 * Encrypts byte arrays.
 */
export class SalmonEncryptor {

    /**
     * The number of parallel threads to use.
     */
    private readonly threads: number;

    /**
     * Executor for parallel tasks.
     */
    private workers: Array<SharedWorker> | null = null;

    /**
     * The buffer size to use.
     */
    private readonly bufferSize: number;

    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads The number of threads to use.
     * @param bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    public constructor(threads: number = 0, bufferSize: number = 0) {
        if (threads == 0) {
            this.threads = 1;
        } else {
            this.threads = threads;
            this.workers = new Array(threads);
        }
        if (bufferSize == 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            this.bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
        } else {
            this.bufferSize = bufferSize;
        }
    }

    /**
     * Encrypts a byte array using the provided key and nonce.
     *
     * @param data            The byte array to be encrypted.
     * @param key             The AES key to be used.
     * @param nonce           The nonce to be used.
     * @param storeHeaderData True if you want to store a header data with the nonce. False if you store
     *                        the nonce external. Note that you will need to provide the nonce when decrypting.
     * @param integrity       True if you want to calculate and store hash signatures for each chunkSize.
     * @param hashKey         Hash key to be used for all chunks.
     * @param chunkSize       The chunk size.
     * @return The byte array with the encrypted data.
     * @throws SalmonSecurityException
     * @throws IOException
     * @throws SalmonIntegrityException
     */
    public async encrypt(data: Uint8Array, key: Uint8Array, nonce: Uint8Array,
        storeHeaderData: boolean,
        integrity: boolean = false, hashKey: Uint8Array | null = null, chunkSize: number | null = null): Promise<Uint8Array> {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;

        let outputStream: MemoryStream = new MemoryStream();
        let headerData: Uint8Array | null = null;
        if (storeHeaderData) {
            let magicBytes: Uint8Array = SalmonGenerator.getMagicBytes();
            await outputStream.write(magicBytes, 0, magicBytes.length);
            let version: number = SalmonGenerator.getVersion();
            let versionBytes: Uint8Array = new Uint8Array([version]);
            await outputStream.write(versionBytes, 0, versionBytes.length);
            let chunkSizeBytes: Uint8Array = BitConverter.toBytes(chunkSize, SalmonGenerator.CHUNK_SIZE_LENGTH);
            await outputStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);
            await outputStream.write(nonce, 0, nonce.length);
            outputStream.flush();
            headerData = outputStream.toArray();
        }

        let realSize: number = SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Encrypt,
            headerData, integrity, chunkSize, hashKey);
        let outData: Uint8Array = new Uint8Array(realSize);
        await outputStream.setPosition(0);
        await outputStream.read(outData, 0, outputStream.length());
        outputStream.close();

        if (this.threads == 1) {
            let inputStream: MemoryStream = new MemoryStream(data);
            await this.encryptData(inputStream, 0, data.length, outData,
                key, nonce, headerData, integrity, hashKey, chunkSize);
        } else {
            await this.encryptDataParallel(data, outData,
                key, hashKey, nonce, headerData,
                chunkSize, integrity);
        }
        return outData;
    }

    /**
     * Encrypt stream using parallel threads.
     *
     * @param data       The input data to be encrypted
     * @param outData    The output buffer with the encrypted data.
     * @param key        The AES key.
     * @param hashKey    The hash key.
     * @param nonce      The nonce to be used for encryption.
     * @param headerData The header data.
     * @param chunkSize  The chunk size.
     * @param integrity  True to apply integrity.
     */
    private async encryptDataParallel(data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array, headerData: Uint8Array | null,
        chunkSize: number | null, integrity: boolean): Promise<void> {

        let runningThreads: number = 1;
        let partSize: number = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        let minPartSize = SalmonAES256CTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = chunkSize;
        else if (integrity)
            minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = Math.ceil(data.length / this.threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = Math.floor(data.length / partSize);
        }

        await this.submitEncryptJobs(runningThreads, partSize,
            data, outData,
            key, hashKey, nonce, headerData,
            integrity, chunkSize);
    }

    /**
     * Submit encryption parallel jobs.
     *
     * @param runningThreads The number of threads to submit.
     * @param partSize       The data length of each part that belongs to each thread.
     * @param data           The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *                       thread will read each own part.
     * @param outData        The buffer of data containing the encrypted data.
     * @param key            The AES key.
     * @param hashKey        The hash key for integrity.
     * @param nonce          The nonce for the data.
     * @param headerData     The header data common to all parts.
     * @param integrity      True to apply the data integrity.
     * @param chunkSize      The chunk size.
     */
    private async submitEncryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        headerData: Uint8Array | null, integrity: boolean, chunkSize: number | null): Promise<void> {

        //const done: CountDownLatch = new CountDownLatch(runningThreads);
        var ex: Error;
        for (let i = 0; i < runningThreads; i++) {
            const index: number = i;
            //executor.submit(() -> {
            //    try {
            //        long start = partSize * index;
            //        long length;
            //        if (index == runningThreads - 1)
            //            length = data.length - start;
            //        else
            //            length = partSize;
            //        MemoryStream ins = new MemoryStream(data);
            //        encryptData(ins, start, length, outData, key, nonce, headerData, integrity, hashKey, chunkSize);
            //    } catch (Exception ex1) {
            //        ex.set(ex1);
            //    }
            //    done.countDown();
            //});
        }
        try {
            //done.await();
        } catch (ignored) { }

        //if (ex.get() != null) {
        //    try {
        //        throw ex.get();
        //    } catch (Exception e) {
        //        throw new RuntimeException(e);
        //    }
        //}
    }

    /**
     * Encrypt the data stream.
     *
     * @param inputStream The Stream to be encrypted.
     * @param start       The start position of the stream to be encrypted.
     * @param count       The number of bytes to be encrypted.
     * @param outData     The buffer with the encrypted data.
     * @param key         The AES key to be used.
     * @param nonce       The nonce to be used.
     * @param headerData  The header data to be used.
     * @param integrity   True to apply integrity.
     * @param hashKey     The key to be used for integrity application.
     * @param chunkSize   The chunk size.
     * @              Thrown if there is an error with the stream.
     * @throws SalmonSecurityException  Thrown if there is a security exception with the stream.
     * @throws SalmonIntegrityException Thrown if integrity cannot be applied.
     */
    private async encryptData(inputStream: MemoryStream, start: number, count: number, outData: Uint8Array,
        key: Uint8Array, nonce: Uint8Array, headerData: Uint8Array | null,
        integrity: boolean, hashKey: Uint8Array | null, chunkSize: number | null): Promise<void> {
        let outputStream: MemoryStream = new MemoryStream(outData);
        let stream: SalmonStream | null = null;
        try {
            await inputStream.setPosition(start);
            stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, outputStream, headerData,
                integrity, chunkSize, hashKey);
            stream.setAllowRangeWrite(true);
            await stream.setPosition(start);
            let totalChunkBytesRead: number = 0;
            // align to the chunk size if available
            let buffSize: number = Math.max(this.bufferSize, stream.getChunkSize());
            let buff: Uint8Array = new Uint8Array(buffSize);
            let bytesRead: number;
            while ((bytesRead = await inputStream.read(buff, 0, Math.min(buff.length, count - totalChunkBytesRead))) > 0
                && totalChunkBytesRead < count) {
                await stream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            stream.flush();
        } catch (ex) {
            console.error(ex);
            throw ex;
        } finally {
            outputStream.close();
            if (stream != null)
                stream.close();
            if (inputStream != null)
                inputStream.close();
        }
    }
}
