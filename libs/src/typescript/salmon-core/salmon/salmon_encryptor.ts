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
import { MemoryStream } from "../streams/memory_stream.js";
import { SalmonIntegrity } from "./integrity/salmon_integrity.js";
import { SalmonStream } from "./streams/salmon_stream.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { SalmonGenerator } from "./salmon_generator.js";
import { SalmonSecurityException } from "./salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./transform/salmon_aes256_ctr_transformer.js";
import { encryptData } from "./salmon_encryptor_helper.js";

/**
 * Encrypts byte arrays.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class SalmonEncryptor {
    #workerPath = './lib/salmon-core/salmon/salmon_encryptor_worker.js';

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
     * Instantiate an encryptor with parallel tasks and buffer size.
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
     * Encrypts a byte array using the provided key and nonce.
     *
     * @param {Uint8Array} data            The byte array to be encrypted.
     * @param {Uint8Array} key             The AES key to be used.
     * @param {Uint8Array} nonce           The nonce to be used.
     * @param {boolean} storeHeaderData True if you want to store a header data with the nonce. False if you store
     *                        the nonce external. Note that you will need to provide the nonce when decrypting.
     * @param {boolean} integrity       True if you want to calculate and store hash signatures for each chunkSize.
     * @param {Uint8Array} hashKey         Hash key to be used for all chunks.
     * @param {number} chunkSize       The chunk size.
     * @return {Promise<Uint8Array>} The byte array with the encrypted data.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
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
            await outputStream.flush();
            headerData = outputStream.toArray();
        }

        let realSize: number = await SalmonStream.getActualSize(data, key, nonce, EncryptionMode.Encrypt,
            headerData, integrity, chunkSize, hashKey);
        let outData: Uint8Array = new Uint8Array(realSize);
        await outputStream.setPosition(0);
        await outputStream.read(outData, 0, await outputStream.length());
        await outputStream.close();

        if (this.#threads == 1) {
            await encryptData(data, 0, data.length, outData,
                key, nonce, headerData, integrity, hashKey, chunkSize, this.#bufferSize);
        } else {
            await this.#encryptDataParallel(data, outData,
                key, hashKey, nonce, headerData,
                chunkSize, integrity);
        }
        return outData;
    }

    /**
     * Encrypt stream using parallel threads.
     *
     * @param {Uint8Array} data       The input data to be encrypted
     * @param {Uint8Array} outData    The output buffer with the encrypted data.
     * @param {Uint8Array} key        The AES key.
     * @param {Uint8Array | null} hashKey    The hash key.
     * @param {Uint8Array} nonce      The nonce to be used for encryption.
     * @param {Uint8Array | null} headerData The header data.
     * @param {number} chunkSize  The chunk size.
     * @param {boolean} integrity  True to apply integrity.
     */
    async #encryptDataParallel(data: Uint8Array, outData: Uint8Array,
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
            partSize = Math.ceil(data.length / this.#threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = Math.floor(data.length / partSize);
        }

        await this.#submitEncryptJobs(runningThreads, partSize, data, outData, key, hashKey, nonce, headerData, integrity, chunkSize);
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
    async #submitEncryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        headerData: Uint8Array | null, integrity: boolean, chunkSize: number | null): Promise<void> {
        this.#promises = [];
        for (let i = 0; i < runningThreads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if (typeof process !== 'object') {
                    this.#workers[i] = new Worker(this.#workerPath, { type: 'module' });
                    this.#workers[i].addEventListener('message', (event: { data: unknown }) => {
                        resolve(event.data);
                    });
                    this.#workers[i].addEventListener('error', (event: any) => {
                        reject(event);
                    });
                } else {
                    const { Worker } = await import("worker_threads");
                    this.#workers[i] = new Worker(this.#workerPath);
                    this.#workers[i].on('message', (event: any) => {
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
        }).catch((err) => {
            console.error(err);
            throw err;
        });
    }

    public close(): void {
        for(let i=0; i<this.#workers.length; i++) {
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
