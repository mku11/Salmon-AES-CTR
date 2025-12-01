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

import { Integrity } from "./integrity/integrity.js";
import { AesStream } from "./streams/aes_stream.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { SecurityException } from "./security_exception.js";
import { AESCTRTransformer } from "./transform/aes_ctr_transformer.js";
import { encryptData } from "./encryptor_helper.js";
import { EncryptionFormat } from "./streams/encryption_format.js";
import { Platform, PlatformType } from "../../simple-io/platform/platform.js";

/**
 * Encrypts byte arrays.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class Encryptor {
    #workerPath: string = "";

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
    public constructor(threads: number = 1, bufferSize: number = 0) {
        if (threads <= 0) {
            this.#threads = 1;
        } else {
            this.#threads = threads;
        }
        if (bufferSize <= 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            this.#bufferSize = Integrity.DEFAULT_CHUNK_SIZE;
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
     * @param {EncryptionFormat} format    The format to use, see {@link EncryptionFormat}
     * @param {boolean} integrity       True if you want to calculate and store hash signatures for each chunkSize.
     * @param {Uint8Array} hashKey         Hash key to be used for all chunks.
     * @param {number} chunkSize       The chunk size.
     * @returns {Promise<Uint8Array>} The byte array with the encrypted data.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public async encrypt(data: Uint8Array, key: Uint8Array, nonce: Uint8Array,
        format: EncryptionFormat = EncryptionFormat.Salmon,
        integrity: boolean = false, hashKey: Uint8Array | null = null,
        chunkSize: number = 0): Promise<Uint8Array> {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        if (integrity)
            chunkSize = chunkSize <= 0 ? Integrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;

        let realSize: number = await AesStream.getOutputSize(EncryptionMode.Encrypt, data.length, format, chunkSize);
        let outData: Uint8Array = new Uint8Array(realSize);

        if (this.#threads == 1) {
            await encryptData(data, 0, data.length, outData,
                key, nonce, format, integrity, hashKey, chunkSize, this.#bufferSize);
        } else {
            await this.#encryptDataParallel(data, outData,
                key, hashKey, nonce, format,
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
     * @param {EncryptionFormat} format      The format to use, see {@link EncryptionFormat}
     * @param {number} chunkSize  The chunk size.
     * @param {boolean} integrity  True to apply integrity.
     */
    async #encryptDataParallel(data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        format: EncryptionFormat,
        chunkSize: number, integrity: boolean): Promise<void> {

        let runningThreads: number = 1;
        let partSize: number = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        let minPartSize = AESCTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize > 0)
            minPartSize = chunkSize;
        else if (integrity)
            minPartSize = Integrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = Math.ceil(data.length / this.#threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = Math.floor(data.length / partSize);
            if (runningThreads > this.#threads)
                runningThreads = this.#threads;
        }

        await this.#submitEncryptJobs(runningThreads, partSize,
            data, outData,
            key, hashKey, nonce, format,
            integrity, chunkSize);
    }

    /**
     * Submit encryption parallel jobs.
     *
     * @param {number} runningThreads The number of threads to submit.
     * @param {number} partSize       The data length of each part that belongs to each thread.
     * @param {Uint8Array} data           The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *                       thread will read each own part.
     * @param {Uint8Array} outData        The buffer of data containing the encrypted data.
     * @param {Uint8Array} key            The AES key.
     * @param {Uint8Array} hashKey        The hash key for integrity.
     * @param {Uint8Array} nonce          The nonce for the data.
     * @param {EncryptionFormat} format      The format to use, see {@link EncryptionFormat}
     * @param {boolean} integrity      True to apply the data integrity.
     * @param {number} chunkSize      The chunk size.
     */
    async #submitEncryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array,
        format: EncryptionFormat, integrity: boolean, chunkSize: number): Promise<void> {
        this.#promises = [];
        if (!this.#workerPath)
            this.#workerPath = await Platform.getAbsolutePath("encryptor_worker.js", import.meta.url);
        for (let i = 0; i < runningThreads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if (Platform.getPlatform() == PlatformType.Browser) {
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.#workerPath, { type: 'module' });
                    this.#workers[i].removeEventListener('error', null);
                    this.#workers[i].removeEventListener('message', null);
                    this.#workers[i].addEventListener('message', (event: { data: unknown }) => {
                        if (event.data instanceof Error)
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
                        if (event.data instanceof Error)
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
                    format: format, integrity: integrity, hashKey: hashKey, chunkSize: chunkSize, bufferSize: this.#bufferSize
                });
            }));
        }
        await Promise.all(this.#promises).then((results: any) => {
            for (let i = 0; i < results.length; i++) {
                let startPos = i == 0 ? 0 : results[i].startPos;
                for (let j = startPos; j < results[i].endPos; j++) {
                    outData[j] = results[i].outData[j];
                }
            }
        }).catch((event) => {
            console.log("Encryptor Error:", event);
            if (event instanceof Error) {
                throw event;
            } else {
                throw new Error("Could not run Worker, make sure you set the correct workerPath");
            }
        });
    }

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
