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

import { MemoryStream } from "../../simple-io/streams/memory_stream.js";
import { Integrity } from "./integrity/integrity.js";
import { EncryptionMode } from "./streams/encryption_mode.js";
import { EncryptionFormat } from "./streams/encryption_format.js";
import { Header } from "./header.js";
import { AesStream } from "./streams/aes_stream.js";
import { SecurityException } from "./security_exception.js";
import { AESCTRTransformer } from "./transform/aes_ctr_transformer.js";
import { decryptData } from "./decryptor_helper.js";
import { Platform, PlatformType } from "../platform/platform.js";

/**
 * Utility class that decrypts byte arrays.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class Decryptor {
    #workerPath: string = './lib/salmon-core/salmon/decryptor_worker.js';

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
    public constructor(threads: number = 1, bufferSize: number = 0) {
        if (threads <= 0)
            threads = 1;
        this.#threads = threads;
        if (bufferSize <= 0) {
            // we use the chunks size as default this keeps buffers aligned in case
            // integrity is enabled.
            bufferSize = Integrity.DEFAULT_CHUNK_SIZE;
        }
        this.#bufferSize = bufferSize;
    }

    /**
     * Decrypt a byte array using AES256 based on the provided key and nonce.
     * @param {Uint8Array} data The input data to be decrypted.
     * @param {Uint8Array} key The AES key to use for decryption.
     * @param {Uint8Array | null} nonce The nonce to use for decryption.
     * @param {EncryptionFormat} format      The format to use, see {@link EncryptionFormat}
     * @param {boolean} integrity Verify hash integrity in the data.
     * @param {Uint8Array | null} hashKey The hash key to be used for integrity.
     * @param {number} chunkSize The chunk size.
     * @returns {Promise<Uint8Array>} The byte array with the decrypted data.
     * @ Thrown if there is a problem with decoding the array.
     * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public async decrypt(data: Uint8Array, key: Uint8Array, nonce: Uint8Array | null = null,
        format: EncryptionFormat = EncryptionFormat.Salmon,
        integrity: boolean = true, hashKey: Uint8Array | null = null, chunkSize: number = 0): Promise<Uint8Array> {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (format == EncryptionFormat.Generic && nonce == null)
            throw new SecurityException("Need to specify a nonce if the file doesn't have a header");

        let inputStream: MemoryStream = new MemoryStream(data);
        if (format == EncryptionFormat.Salmon) {
            let header: Header | null = await Header.readHeaderData(inputStream);
            if (header)
                chunkSize = header.getChunkSize();
        } else if (integrity) {
            chunkSize = chunkSize <= 0 ? Integrity.DEFAULT_CHUNK_SIZE : chunkSize;
        } else {
            chunkSize = 0;
        }

        let realSize: number = await AesStream.getOutputSize(EncryptionMode.Decrypt, data.length, format, chunkSize);

        let outData: Uint8Array = new Uint8Array(realSize);

        if (this.#threads == 1) {
            await decryptData(data, 0, realSize, outData,
                key, nonce, format, integrity, hashKey, chunkSize, this.#bufferSize);
        } else {
            await this.#decryptDataParallel(data, outData,
                key, hashKey, nonce, format,
                chunkSize, integrity);
        }

        return outData;
    }

    /**
     * Decrypt stream using parallel threads.
     */
    async #decryptDataParallel(data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array | null, 
        format: EncryptionFormat,
        chunkSize: number, integrity: boolean): Promise<void> {
        let runningThreads: number = 1;
        let partSize: number = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        let minPartSize: number = AESCTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize)
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

        await this.#submitDecryptJobs(runningThreads, partSize,
            data, outData,
            key, hashKey, nonce, format,
            integrity, chunkSize);
    }

    /**
     * Submit decryption parallel jobs.
     */
    async #submitDecryptJobs(runningThreads: number, partSize: number, data: Uint8Array, outData: Uint8Array,
        key: Uint8Array, hashKey: Uint8Array | null, nonce: Uint8Array | null,
        format: EncryptionFormat, integrity: boolean, chunkSize: number): Promise<void> {
        this.#promises = [];
        for (let i = 0; i < runningThreads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if (Platform.getPlatform() == PlatformType.Browser) {
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
                    format: format, integrity: integrity, hashKey: hashKey, chunkSize: chunkSize, bufferSize: this.#bufferSize
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
