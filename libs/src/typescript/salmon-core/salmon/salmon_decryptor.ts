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
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { EncryptionMode } from "./io/encryption_mode.js";
import { SalmonStream } from "./io/salmon_stream.js";
import { SalmonHeader } from "./salmon_header.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";

/**
 * Utility class that decrypts byte arrays.
 */
export class SalmonDecryptor {

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
     * Instantiate a decryptor with parallel tasks and buffer size.
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
     * Decrypt a byte array using AES256 based on the provided key and nonce.
     * @param data The input data to be decrypted.
     * @param key The AES key to use for decryption.
     * @param nonce The nonce to use for decryption.
     * @param hasHeaderData The header data.
     * @param integrity Verify hash integrity in the data.
     * @param hashKey The hash key to be used for integrity.
     * @param chunkSize The chunk size.
     * @return The byte array with the decrypted data.
     * @ Thrown if there is a problem with decoding the array.
     * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
     * @throws IOException
     * @throws SalmonIntegrityException
     */
    public async decrypt(data: Uint8Array, key: Uint8Array, nonce: Uint8Array | null,
        hasHeaderData: boolean,
        integrity: boolean = false, hashKey: Uint8Array | null = null, chunkSize: number | null = null): Promise<Uint8Array> {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (!hasHeaderData && nonce == null)
            throw new SalmonSecurityException("Need to specify a nonce if the file doesn't have a header");

        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;

        let inputStream: MemoryStream = new MemoryStream(data);
        let header: SalmonHeader | null = null;
        let headerData: Uint8Array | null = null;
        if (hasHeaderData) {
            header = await SalmonHeader.parseHeaderData(inputStream);
            if (header.getChunkSize() > 0)
                integrity = true;
            chunkSize = header.getChunkSize();
            nonce = header.getNonce();
            headerData = header.getHeaderData();
        }
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        let realSize: number = SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Decrypt,
            headerData, integrity, chunkSize, hashKey);
        let outData: Uint8Array = new Uint8Array(realSize);

        if (this.threads == 1) {
            await this.decryptData(inputStream, 0, inputStream.length(), outData,
                key, nonce, headerData, integrity, hashKey, chunkSize);
        } else {
            await this.decryptDataParallel(data, outData,
                key, hashKey, nonce, headerData,
                chunkSize, integrity);
        }

        return outData;
    }

    /**
     * Decrypt stream using parallel threads.
     * @param data The input data to be decrypted
     * @param outData The output buffer with the decrypted data.
     * @param key The AES key.
     * @param hashKey The hash key.
     * @param nonce The nonce to be used for decryption.
     * @param headerData The header data.
     * @param chunkSize The chunk size.
     * @param integrity True to verify integrity.
     */
    private decryptDataParallel(data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array, headerData: Uint8Array | null,
        chunkSize: number | null, integrity: boolean): void {
        let runningThreads: number = 1;
        let partSize: number = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        let minPartSize: number = SalmonAES256CTRTransformer.BLOCK_SIZE;
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

        this.submitDecryptJobs(runningThreads, partSize,
            data, outData,
            key, hashKey, nonce, headerData,
            integrity, chunkSize);
    }

    /**
     * Submit decryption parallel jobs.
     * @param runningThreads The number of threads to submit.
     * @param partSize The data length of each part that belongs to each thread.
     * @param data The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *             thread will read each own part.
     * @param outData The buffer of data containing the decrypted data.
     * @param key The AES key.
     * @param hashKey The hash key for integrity validation.
     * @param nonce The nonce for the data.
     * @param headerData The header data common to all parts.
     * @param integrity True to verify the data integrity.
     * @param chunkSize The chunk size.
     */
    private submitDecryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        headerData: Uint8Array | null, integrity: boolean, chunkSize: number | null): void {
        // final CountDownLatch done = new CountDownLatch(runningThreads);
        //   AtomicReference<Exception> ex = new AtomicReference<>();
        //   for (int i = 0; i < runningThreads; i++) {
        //       final int index = i;
        //       executor.submit(() ->
        //       {
        //           try {
        //               long start = partSize * index;
        //               long length;
        //if(index == runningThreads - 1)
        //	length = data.length-start;
        //else
        //	length = partSize;
        //               MemoryStream ins = new MemoryStream(data);
        //               decryptData(ins, start, length, outData, key, nonce, headerData,
        //                       integrity, hashKey, chunkSize);
        //           } catch (Exception ex1) {
        //               ex.set(ex1);
        //           }
        //           done.countDown();
        //       });
        //   }

        try {
            //done.await();
        } catch (ignored) { }

        //if (ex != null) {
        //    try {
        //        throw ex.get();
        //    } catch (Exception e) {
        //        throw new RuntimeException(e);
        //    }
        //}
    }

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
    private async decryptData(inputStream: MemoryStream, start: number, count: number, outData: Uint8Array,
        key: Uint8Array, nonce: Uint8Array, headerData: Uint8Array | null,
        integrity: boolean, hashKey: Uint8Array | null, chunkSize: number | null): Promise<void> {
        let stream: SalmonStream | null = null;
        let outputStream: MemoryStream | null = null;
        try {
            outputStream = new MemoryStream(outData);
            outputStream.setPosition(start);
            stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                headerData, integrity, chunkSize, hashKey);
            await stream.setPosition(start);
            let totalChunkBytesRead: number = 0;
            // align to the chunksize if available
            let buffSize: number = Math.max(this.bufferSize, stream.getChunkSize());
            let buff: Uint8Array = new Uint8Array(buffSize);
            let bytesRead: number;
            while ((bytesRead = await stream.read(buff, 0, Math.min(buff.length, count - totalChunkBytesRead))) > 0
                && totalChunkBytesRead < count) {
                await outputStream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            outputStream.flush();
        } catch (ex) {
            console.error(ex);
            throw new SalmonSecurityException("Could not decrypt data", ex);
        } finally {
            if (inputStream != null)
                inputStream.close();
            if (stream != null)
                stream.close();
            if (outputStream != null)
                outputStream.close();
        }
    }
}
