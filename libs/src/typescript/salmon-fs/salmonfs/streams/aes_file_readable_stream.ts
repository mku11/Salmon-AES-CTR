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
import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import { AesFile } from "../file/aes_file.js";
import { CacheBuffer, fillBufferPart } from "./aes_file_readable_stream_helper.js";
import { IntegrityException } from "../../../salmon-core/salmon/integrity/integrity_exception.js";
import { AuthException } from "../auth/auth_exception.js";


/**
 * Implementation of a javascript ReadableStream for seeking and reading a SalmonFile.
 * This class provides a seekable source with parallel substreams and cached buffers
 * for performance.
 * Make sure you use setWorkerPath() with the correct worker script.
 */
export class AesFileReadableStream {

    // Default cache buffer should be high enough for some mpeg videos to work
    // the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    static readonly #DEFAULT_BUFFER_SIZE: number = 512 * 1024;
    // default threads is one but you can increase it
    static readonly #DEFAULT_THREADS: number = 1;
    static readonly #DEFAULT_BUFFERS: number = 3;
    static readonly #MAX_BUFFERS: number = 6;

    /**
     * Instantiate a seekable stream from an encrypted file source
     *
     * @param salmonFile   The source file.
     * @param buffersCount Number of buffers to use.
     * @param bufferSize   The length of each buffer.
     * @param threads      The number of threads/streams to source the file in parallel.
     * @param backOffset   The back offset.  Negative offset for the buffers. Some stream consumers might request data right before
     * the last request. We provide this offset so we don't make multiple requests for filling
     * the buffers ending up with too much overlapping data.
     */
    public static create(salmonFile: AesFile,
        buffersCount: number = 0, bufferSize: number = 0, threads: number = 0, backOffset: number = 0) {
        if (buffersCount == 0)
            buffersCount = AesFileReadableStream.#DEFAULT_BUFFERS;
        if (buffersCount > AesFileReadableStream.#MAX_BUFFERS)
            buffersCount = AesFileReadableStream.#MAX_BUFFERS;
        if (bufferSize == 0)
            bufferSize = AesFileReadableStream.#DEFAULT_BUFFER_SIZE;
        if (backOffset > 0)
            bufferSize += backOffset;
        if (threads == 0)
            threads = AesFileReadableStream.#DEFAULT_THREADS;

        let reader: ReadableStreamFileReader = new ReadableStreamFileReader(salmonFile,
            buffersCount, bufferSize, threads, backOffset);
        let readableStream: any = new ReadableStream({
            type: 'bytes',
            async pull(controller: any) {
                let buff = await reader.read();
                if (buff != null)
                    controller.enqueue(buff);
                else
                    controller.close();
            },
            async cancel(reason?: any): Promise<void> {
                await reader.cancel();
            }
        });
        readableStream.reset = function (): void {
            reader.reset();
        }
        readableStream.skip = async function (position: number): Promise<number> {
            return await reader.skip(position);
        }
        readableStream.getPositionStart = function (): number {
            return reader.getPositionStart();
        }
        readableStream.setPositionStart = async function (position: any): Promise<void> {
            await reader.setPositionStart(position);
        }
        readableStream.setPositionEnd = async function (position: any): Promise<void> {
            await reader.setPositionEnd(position);
        }
        readableStream.setWorkerPath = function (path: string) {
            reader.setWorkerPath(path);
        }
        readableStream.getWorkerPath = function (): string {
            return reader.getWorkerPath();
        }
        return readableStream;
    }
}

export class ReadableStreamFileReader {
    private workerPath = './lib/salmon-fs/salmonfs/streams/aes_file_readable_stream_worker.js';
    private readonly buffersCount: number;
    private readonly salmonFile: AesFile;
    private readonly cacheBufferSize: number;
    private readonly threads: number;
    private readonly backOffset: number;

    private buffers: (CacheBuffer | null)[] | null = null;
    private stream: AesStream | null = null;

    #promises: Promise<any>[] = [];

    #workers: any[] = [];

    // private ExecutorService executor;
    private position: number = 0;
    private size: number = 0;

    /**
     * We reuse the least recently used buffer. Since the buffer count is relative
     * small (see {@link #MAX_BUFFERS}) there is no need for a fast-access lru queue
     * so a simple linked list of keeping the indexes is adequately fast.
     */
    private lruBuffersIndex: number[] = [];

    constructor(salmonFile: AesFile,
        buffersCount: number, bufferSize: number, threads: number, backOffset: number) {
        this.salmonFile = salmonFile;
        this.buffersCount = buffersCount;
        this.cacheBufferSize = bufferSize;
        if(this.salmonFile.getRealFile().constructor.name === 'WSFile'){
			console.log("Multithreading for web service files is not supported, setting single thread");
			threads = 1;
        }
        this.threads = threads;
        this.backOffset = backOffset;
    }

    public async initialize(): Promise<void> {
        this.size = await this.salmonFile.getSize();
        this.positionStart = 0;
        this.positionEnd = this.size - 1;
        this.createBuffers();
        if (this.threads == 1) {
            await this.createStream();
        }
    }

    async read(): Promise<Uint8Array | null> {
        if (this.buffers == null)
            await this.initialize();
        let buff: Uint8Array = new Uint8Array(this.cacheBufferSize);
        let bytesRead: number = await this.readStream(buff, 0, buff.length);
        if (bytesRead <= 0)
            return null;
        return buff.slice(0, bytesRead);
    }


    /**
     * Method creates the parallel streams for reading from the file
     */
    private async createStream(): Promise<void> {
        this.stream = await this.salmonFile.getInputStream();
    }

    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    private createBuffers(): void {
        this.buffers = new Array(this.buffersCount);
        for (let i = 0; i < this.buffers.length; i++)
            this.buffers[i] = new CacheBuffer(this.cacheBufferSize);
    }

    /**
     * Skip a number of bytes.
     *
     * @param bytes the number of bytes to be skipped.
     * @return
     */
    public async skip(bytes: number): Promise<number> {
        if (this.buffers == null)
            await this.initialize();
        bytes += this.positionStart;
        let currPos: number = this.position;
        if (this.position + bytes > this.size)
            this.position = this.size;
        else
            this.position += bytes;
        return this.position - currPos;
    }

    public reset(): void {
        this.position = 0;
    }

    /**
     * Reads and decrypts the contents of an encrypted file
     *
     * @param buffer The buffer that will store the decrypted contents
     * @param offset The position on the buffer that the decrypted data will start
     * @param count  The length of the data requested
     */
    public async readStream(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        if (this.position >= this.positionEnd + 1)
            return -1;

        let minCount: number;
        let bytesRead: number;

        // truncate the count so getCacheBuffer() reports the correct buffer
        count = Math.floor(Math.min(count, this.size - this.position));

        let cacheBuffer: CacheBuffer | null = this.getCacheBuffer(this.position, count);
        if (cacheBuffer == null) {
            cacheBuffer = this.getAvailCacheBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return -1;
            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            let startPosition = this.position - this.backOffset;
            if (startPosition < 0)
                startPosition = 0;

            bytesRead = await this.fillBuffer(cacheBuffer, startPosition, this.cacheBufferSize);

            if (bytesRead <= 0)
                return -1;
            cacheBuffer.startPos = startPosition;
            cacheBuffer.count = bytesRead;
        }
        minCount = Math.min(count, Math.floor(cacheBuffer.count - this.position + cacheBuffer.startPos));
        let cOffset: number = Math.floor(this.position - cacheBuffer.startPos);
        for (let i = 0; i < minCount; i++)
            buffer[offset + i] = cacheBuffer.buffer[cOffset + i];
        this.position += minCount;
        return minCount;
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
     * @param bufferSize  The length of the data requested
     */
    private async fillBuffer(cacheBuffer: CacheBuffer, startPosition: number, bufferSize: number): Promise<number> {
        let bytesRead: number;
        if (this.threads == 1) {
            if (this.stream == null)
                return 0;
            bytesRead = await fillBufferPart(cacheBuffer, startPosition, 0, bufferSize, this.stream);
        } else {
            bytesRead = await this.fillBufferMulti(cacheBuffer, startPosition, bufferSize);
        }
        return bytesRead;
    }


    /**
     * Fill the buffer using parallel streams for performance
     *
     * @param cacheBuffer   The cache buffer that will store the decrypted data
     * @param startPosition The source file position the read will start from
     * @param bufferSize    The buffer size that will be used to read from the file
     */
    private async fillBufferMulti(cacheBuffer: CacheBuffer, startPosition: number, bufferSize: number): Promise<number> {
        let partSize: number = Math.ceil(bufferSize / this.threads);
        let bytesRead: number = 0;
        this.#promises = [];
        for (let i = 0; i < this.threads; i++) {
            this.#promises.push(new Promise(async (resolve, reject) => {
                let fileToReadHandle: any = await this.salmonFile.getRealFile().getPath();
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
                let length;
                if (i == this.threads - 1)
                    length = bufferSize - start;
                else
                    length = partSize;

                this.#workers[i].postMessage({
                    message: 'start',
                    index: i,
                    startPosition: startPosition,
                    fileToReadHandle: fileToReadHandle,
                    readFileClassType: this.salmonFile.getRealFile().constructor.name,
                    start: start, length: length,
                    key: this.salmonFile.getEncryptionKey(),
                    integrity: this.salmonFile.isIntegrityEnabled(),
                    hash_key: this.salmonFile.getHashKey(),
                    chunk_size: this.salmonFile.getRequestedChunkSize(),
                    cacheBufferSize: this.cacheBufferSize
                });
            }));
        }
        await Promise.all(this.#promises).then((results: any) => {
            for (let i = 0; i < results.length; i++) {
                bytesRead += results[i].chunkBytesRead;
                let chunkStart = results[i].start;
                for (let j = 0; j < results[i].chunkBytesRead; j++)
                    cacheBuffer.buffer[chunkStart + j] = results[i].cacheBuffer[chunkStart + j];
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
     * Returns an available cache buffer if there is none then reuse the least recently used one.
     */
    private getAvailCacheBuffer(): CacheBuffer | null {
        if (this.buffers == null)
            return null;
        if (this.lruBuffersIndex.length == this.buffersCount) {
            // getting least recently used buffer
            let index: number = this.lruBuffersIndex.pop() as number;
            // promote to the top
            delete this.lruBuffersIndex[index];
            this.lruBuffersIndex.unshift(index);
            return this.buffers[this.lruBuffersIndex.pop() as number];
        }
        for (let i = 0; i < this.buffers.length; i++) {
            let buffer: CacheBuffer | null = this.buffers[i];
            if (buffer != null && buffer.count == 0) {
                this.lruBuffersIndex.unshift(i);
                return buffer;
            }
        }
        if (this.buffers[this.buffers.length - 1] != null)
            return this.buffers[this.buffers.length - 1];
        else
            return null;
    }

    /**
     * Returns the buffer that contains the data requested.
     *
     * @param position The source file position of the data to be read
     */
    private getCacheBuffer(position: number, count: number): CacheBuffer | null {
        if (this.buffers == null)
            return null;
        for (let i = 0; i < this.buffers.length; i++) {
            let buffer: CacheBuffer | null = this.buffers[i];
            if (buffer != null && position >= buffer.startPos && position + count <= buffer.startPos + buffer.count) {
                // promote buffer to the front
                delete this.lruBuffersIndex[i];
                this.lruBuffersIndex.unshift(i);
                return buffer;
            }
        }
        return null;
    }

    private positionStart: number = 0;
    public getPositionStart(): number {
        return this.positionStart;
    }
    public async setPositionStart(pos: number): Promise<void> {
        if (this.buffers == null)
            await this.initialize();
        this.positionStart = pos;
    }
    private positionEnd: number = 0;
    public async setPositionEnd(pos: number): Promise<void> {
        if (this.buffers == null)
            await this.initialize();
        this.positionEnd = pos;
    }


    /**
     * Clear all buffers.
     */
    private clearBuffers(): void {
        if (this.buffers == null)
            return;
        for (let i = 0; i < this.buffers.length; i++) {
            let buffer = this.buffers[i];
            if (buffer != null)
                buffer.clear();
            this.buffers[i] = null;
        }
    }

    /**
     * Close all back streams.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    private async closeStream(): Promise<void> {
        if (this.stream != null)
            await this.stream.close();
        this.stream = null;
    }

    closed: Promise<undefined> = Promise.resolve(undefined);

    async cancel(reason?: any): Promise<void> {
        await this.closeStream();
        this.clearBuffers();
        for (let i = 0; i < this.#workers.length; i++) {
            this.#workers[i].postMessage({ message: 'close' });
            this.#workers[i].terminate();
            this.#workers[i] = null;
        }
    }

    setWorkerPath(path: string) {
        this.workerPath = path;
    }
    getWorkerPath(): string {
        return this.workerPath;
    }
}
