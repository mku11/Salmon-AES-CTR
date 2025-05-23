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

import { IOException } from "../../../salmon-core/streams/io_exception.js";
import { AesFile } from "../file/aes_file.js";
import { ReadableStreamWrapper, fillBufferPart } from "../../../salmon-core/streams/readable_stream_wrapper.js";
import { Buffer } from "../../../salmon-core/streams/buffer.js";
import { IntegrityException } from "../../../salmon-core/salmon/integrity/integrity_exception.js";
import { AuthException } from "../auth/auth_exception.js";
import { Generator } from "../../../salmon-core/salmon/generator.js";
import { HttpSyncClient } from "../../fs/file/http_sync_client.js";
import { Credentials } from "../../fs/file/credentials.js";
import { IFile } from "../../fs/file/ifile.js";
import { FileUtils } from "../../fs/drive/utils/file_utils.js";

/**
 * ReadableStream wrapper for seeking and reading an encrypted AesFile.
 * This class provides a seekable source with parallel streams and cached buffers
 * for performance.
 */
export class AesFileReadableStream extends ReadableStreamWrapper {
    // default threads is one but you can increase it
    static readonly #DEFAULT_THREADS: number = 1;

    #workerPath = './lib/salmon-fs/salmonfs/streams/aes_file_readable_stream_worker.js';
    #aesFile: AesFile | null = null;
    #threads: number = 0;
    #promises: Promise<any>[] = [];
    #workers: any[] = [];

    
    /**
     * Construct a wrapper do not use directly, use createFileReadableStream() instead.
     */
    protected constructor() {
        super();
    }

    /**
     * Creates a seekable stream from an encrypted file source
     *
     * @param {AesFile} aesFile   The source file.
     * @param {number} buffersCount Number of buffers to use.
     * @param {Uint8Array} bufferSize   The length of each buffer.
     * @param {number} threads      The number of threads/streams to read the file in parallel.
     * @param {number} backOffset   The backwards offset. Some media libraries might 
     * request data rewinding the stream just a few bytes backwards. This ensures those bytes 
     * are included so we don't reset the stream.
     */
    public static createFileReadableStream(aesFile: AesFile,
        buffersCount: number = 1, 
        bufferSize: number = 524288, 
        threads: number = 1, 
        backOffset: number = 32768) {
        let fileReadableStream: AesFileReadableStream = new AesFileReadableStream();
        fileReadableStream.setBufferCount(buffersCount);
        fileReadableStream.setBufferSize(bufferSize);
        fileReadableStream.setBackOffset(backOffset);
        fileReadableStream.#aesFile = aesFile;
        fileReadableStream.#threads = threads;

        let readableStream: any = ReadableStreamWrapper.createReadableStreamReader(fileReadableStream);
        readableStream.setWorkerPath = function (path: string) {
            fileReadableStream.setWorkerPath(path);
        }
        readableStream.getWorkerPath = function (): string {
            return fileReadableStream.getWorkerPath();
        }
        return readableStream;
    }

    protected async initialize(): Promise<void> {
        if(this.#aesFile == null)
            throw new Error("File is missing");
        this.setAlignSize(await this.#aesFile.getFileChunkSize() > 0 ? 
        await this.#aesFile.getFileChunkSize() : Generator.BLOCK_SIZE);
        this.setTotalSize(await this.#aesFile.getLength());
        await super.initialize();
        
        if (this.#threads == 0)
            this.#threads = AesFileReadableStream.#DEFAULT_THREADS;
        if((this.#threads & (this.#threads-1)) != 0)
            throw new Error("Threads needs to be a power of 2 (ie 1,2,4,8)");
        if(this.#threads == 1)
            this.setStream(await this.#aesFile.getInputStream());
        await this.setPositionEnd(this.getTotalSize() - 1);
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     * @param { Buffer } cacheBuffer The cache buffer that will store the decrypted contents
	 * @param { number } startPosition The start position
     * @param { number } length      The length of the data requested
     */
    protected async fillBuffer(cacheBuffer: Buffer, startPosition: number, length: number): Promise<number> {
        let bytesRead: number;
        if (this.#threads == 1) {
            let stream = this.getStream();
            if (stream == null)
                return 0;
            bytesRead = await fillBufferPart(cacheBuffer, startPosition, 0, length, stream);
        } else {
            bytesRead = await this.#fillBufferMulti(cacheBuffer, startPosition, length);
        }
        return bytesRead;
    }

    /**
     * Fill the buffer using parallel streams for performance
     */
    async #fillBufferMulti(cacheBuffer: Buffer, startPosition: number, totalBufferLength: number): Promise<number> {
        if(this.#aesFile == null)
            throw new Error("File is missing");
        let needsBackOffset = totalBufferLength == this.getBufferSize();
        let partSize;
        if(needsBackOffset) {
            partSize = Math.ceil((totalBufferLength - this.getBackOffset()) / this.#threads);
        } else {
            partSize = Math.ceil(totalBufferLength / this.#threads);
        }
        let bytesRead: number = 0;
        this.#promises = [];
        for (let i = 0; i < this.#threads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                if(this.#aesFile == null)
                    throw new Error("File is missing");
                let realFile: IFile = this.#aesFile.getRealFile();
                let readFileClassType: string = realFile.constructor.name;
                let fileToReadHandle: any = await realFile.getPath();
                let servicePath: string | null = await FileUtils.getServicePath(realFile);
                let credentials: Credentials | null = realFile.getCredentials();
                
                if (typeof process !== 'object') {

                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.getWorkerPath(), { type: 'module' });
                    this.#workers[i].addEventListener('message', (event: any) => {
                        resolve(event.data);
                    });
                    this.#workers[i].addEventListener('error', (event: any) => {
                        reject(event);
                    });
                } else {
                    const { Worker } = await import("worker_threads");
                    if (this.#workers[i] == null)
                        this.#workers[i] = new Worker(this.getWorkerPath());
                    this.#workers[i].on('message', (event: any) => {
                        if (event.message == 'complete') {
                            resolve(event);
                        } else if (event.message == 'error') {
                            reject(event);
                        }
                    });
                    this.#workers[i].on('error', (event: any) => {
                        reject(event);
                    });
                }

                let start = partSize * i;
                if(i > 0 && needsBackOffset) {
                    start += this.getBackOffset();
                }
                let length;
                if (i == 0 && needsBackOffset) {
                    length = partSize + this.getBackOffset();
                } else if (i == this.#threads - 1)
                    length = this.getBufferSize() - start;
                else
                    length = partSize;

                this.#workers[i].postMessage({
                    message: 'start',
                    index: i,
                    startPosition: startPosition,
                    fileToReadHandle: fileToReadHandle,
                    readFileClassType: readFileClassType,
                    start: start, length: length,
                    key: this.#aesFile.getEncryptionKey(),
                    integrity: this.#aesFile.isIntegrityEnabled(),
                    hash_key: this.#aesFile.getHashKey(),
                    chunk_size: this.#aesFile.getRequestedChunkSize(),
                    cacheBufferSize: this.getBufferSize(),
                    allowClearTextTraffic: HttpSyncClient.getAllowClearTextTraffic(),
                    servicePath: servicePath,
                    serviceUser: credentials?.getServiceUser(),
                    servicePassword: credentials?.getServicePassword()
                });
            }));
        }
        await Promise.all(this.#promises).then((results: any) => {
            for (let i = 0; i < results.length; i++) {
                bytesRead += results[i].chunkBytesRead;
                let chunkStart = results[i].start;
                for (let j = 0; j < results[i].chunkBytesRead; j++)
                    cacheBuffer.getData()[chunkStart + j] = results[i].cacheBuffer[chunkStart + j];
            }
        }).catch((err) => {
            // deserialize the error
            if (err.error != undefined) {
                if (err.type == 'IntegrityException')
                    err = new IntegrityException(err.error);
                else if (err.type == 'SalmonAuthException')
                    err = new AuthException(err.error);
                else
                    err = new Error(err.error);
            }
            console.error(err);
            throw new IOException("Error during read", err);
        });
        return bytesRead;
    }
    
    /**
     * Cancel the stream
     * @param {any} [reason] The reason
     */
    async cancel(reason?: any): Promise<void> {
        for (let i = 0; i < this.#workers.length; i++) {
            if(this.#workers[i]) {
                this.#workers[i].postMessage({ message: 'close' });
                this.#workers[i].terminate();
                this.#workers[i] = null;
            }
        }
        await super.cancel(reason);
    }

    /**
     * Set the worker path
     * @param {string} path The worker path
     */
    public setWorkerPath(path: string) {
        this.#workerPath = path;
    }

    /**
     * Get the worker path used for parallel streaming
     * @returns {string} The worker path
     */
    public getWorkerPath(): string {
        return this.#workerPath;
    }
}
