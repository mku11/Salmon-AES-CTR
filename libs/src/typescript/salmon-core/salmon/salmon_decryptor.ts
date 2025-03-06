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
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { SalmonStream } from "./streams/salmon_stream.js";
import { SalmonHeader } from "./salmon_header.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";
import { decryptData } from "./salmon_decryptor_helper.js";

/**
 * Utility class that decrypts byte arrays.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class SalmonDecryptor {
    #workerPath = './lib/salmon-core/salmon/salmon_decryptor_worker.js';

    /**
     * The number of parallel threads to use.
     */
    readonly #threads: number;

    /**
     * The buffer size to use.
     */
    readonly #bufferSize: number;

    #promises: Promise<any>[] = [];
    #workers: any[] = [];

    /**
     * Instantiate a decryptor with parallel tasks and buffer size.
     *
     * @param {number} threads The number of threads to use.
     * @param {number} bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    public constructor(threads: number = 0, bufferSize: number = 0) {
        if (threads == 0) {
            this.#threads = 1;
        } else {
            this.#threads = threads;
        }
        if (bufferSize == 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            this.#bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
        } else {
            this.#bufferSize = bufferSize;
        }
    }

    /**
     * Decrypt a byte array using AES256 based on the provided key and nonce.
     * @param {Uint8Array} data The input data to be decrypted.
     * @param {Uint8Array} key The AES key to use for decryption.
     * @param {Uint8Array | null} nonce The nonce to use for decryption.
     * @param {boolean} hasHeaderData The header data.
     * @param {boolean} integrity Verify hash integrity in the data.
     * @param {Uint8Array | null} hashKey The hash key to be used for integrity.
     * @param {number | null} chunkSize The chunk size.
     * @return {Promise<Uint8Array>} The byte array with the decrypted data.
     * @ Thrown if there is a problem with decoding the array.
     * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
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

        let realSize: number = await SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Decrypt,
            headerData, integrity, chunkSize, hashKey);
        let outData: Uint8Array = new Uint8Array(realSize);

        if (this.#threads == 1) {
            await decryptData(data, 0, data.length, outData,
                key, nonce, headerData, integrity, hashKey, chunkSize, this.#bufferSize);
        } else {
            await this.#decryptDataParallel(data, outData,
                key, hashKey, nonce, headerData,
                chunkSize, integrity);
        }

        return outData;
    }

    /**
     * Decrypt stream using parallel threads.
     * @param {Uint8Array} data The input data to be decrypted
     * @param outData The output buffer with the decrypted data.
     * @param key The AES key.
     * @param hashKey The hash key.
     * @param nonce The nonce to be used for decryption.
     * @param headerData The header data.
     * @param chunkSize The chunk size.
     * @param integrity True to verify integrity.
     */
    async #decryptDataParallel(data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array, headerData: Uint8Array | null,
        chunkSize: number | null, integrity: boolean): Promise<void> {
        let runningThreads: number = 1;
        let partSize: number = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        let minPartSize: number = SalmonAES256CTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = chunkSize;
        else if (integrity)
            minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = Math.ceil(data.length / this.#threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = Math.floor(data.length / partSize);
        }

        await this.#submitDecryptJobs(runningThreads, partSize,
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
    async #submitDecryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        headerData: Uint8Array | null, integrity: boolean, chunkSize: number | null): Promise<void> {
        this.#promises = [];
        for (let i = 0; i < runningThreads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if (typeof process !== 'object') {
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.#workerPath, { type: 'module' });
                    this.#workers[i].removeEventListener('error', null);
				    this.#workers[i].removeEventListener('message', null);
                    this.#workers[i].addEventListener('message', (event: { data: unknown }) => {
                        if(event.data instanceof Error)
							reject(event.data);
						else
							resolve(event.data);
                    });
                    this.#workers[i].addEventListener('error', (event: any) => {
                        reject(event);
                    });
                } else {
                    const { Worker } = await import("worker_threads");
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.#workerPath);
                    this.#workers[i].removeAllListeners();
                    this.#workers[i].on('message', (event: any) => {
                        if(event instanceof Error)
							reject(event);
						else
							resolve(event);
                    });
                    this.#workers[i].on('error', (event: any) => {
                        reject(event);
                    });
                }

                let start: number = partSize * i;
                let length: number;
                if (i == runningThreads - 1)
                    length = data.length - start;
                else
                    length = partSize;

                this.#workers[i].postMessage({
                    index: i, data: data, out_size: outData.length, start: start, length: length, key: key, nonce: nonce,
                    headerData: headerData, integrity: integrity, hashKey: hashKey, chunkSize: chunkSize, bufferSize: this.#bufferSize
                });
            }));
        }
        await Promise.all(this.#promises).then((results: any) => {
            for (let i = 0; i < results.length; i++) {
                for (let j = results[i].startPos; j < results[i].endPos; j++) {
                    outData[j] = results[i].outData[j];
                }
            }
        }).catch((event) => {
			console.error(event);
			if(event instanceof Error) {
				throw event;
			} else {
				throw new Error("Could not run Worker, make sure you set the correct workerPath");
			}
        });
    }

    /**
     * Close the decryptor and release associated resources
     */
    public close(): void {
        for (let i = 0; i < this.#workers.length; i++) {
            this.#workers[i].terminate();
            this.#workers[i] = null;
        }
        this.#promises = [];
    }

    /**
     * Set the path where the decryptor worker. This needs to be a relative path starting from
     * the root of your main javascript app.
     * @param {string} path The path to the worker javascript.
     */
    public setWorkerPath(path: string) {
        this.#workerPath = path;
    }

    /**
     * Get the current path for the worker javascript.
     * @returns {string} The path to the worker javascript.
     */
    public getWorkerPath(): string {
        return this.#workerPath;
    }
}
