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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _a, _SalmonFileReadableStream_workerPath, _ReadableStreamFileReader_promises, _ReadableStreamFileReader_workers;
import { IOException } from "../../salmon-core/io/io_exception.js";
import { CacheBuffer, fillBufferPart } from "./salmon_file_readable_stream_helper.js";
import { SalmonIntegrityException } from "../../salmon-core/salmon/integrity/salmon_integrity_exception.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
/**
 * Implementation of a javascript ReadableStream for seeking and reading a SalmonFile.
 * This class provides a seekable source with parallel substreams and cached buffers
 * for performance.
 */
export class SalmonFileReadableStream {
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
    static create(salmonFile, buffersCount = 0, bufferSize = 0, threads = 0, backOffset = 0) {
        if (buffersCount == 0)
            buffersCount = _a.DEFAULT_BUFFERS;
        if (buffersCount > _a.MAX_BUFFERS)
            buffersCount = _a.MAX_BUFFERS;
        if (bufferSize == 0)
            bufferSize = _a.DEFAULT_BUFFER_SIZE;
        if (backOffset > 0)
            bufferSize += backOffset;
        if (threads == 0)
            threads = _a.DEFAULT_THREADS;
        let reader = new ReadableStreamFileReader(salmonFile, buffersCount, bufferSize, threads, backOffset);
        let readableStream = new ReadableStream({
            type: 'bytes',
            async pull(controller) {
                let buff = await reader.read();
                if (buff != null)
                    controller.enqueue(buff);
                else
                    controller.close();
            },
            async cancel(reason) {
                await reader.cancel();
            }
        });
        readableStream.reset = function () {
            reader.reset();
        };
        readableStream.skip = async function (position) {
            return await reader.skip(position);
        };
        readableStream.getPositionStart = function () {
            return reader.getPositionStart();
        };
        readableStream.setPositionStart = async function (position) {
            await reader.setPositionStart(position);
        };
        readableStream.setPositionEnd = async function (position) {
            await reader.setPositionEnd(position);
        };
        return readableStream;
    }
    static setWorkerPath(path) {
        __classPrivateFieldSet(_a, _a, path, "f", _SalmonFileReadableStream_workerPath);
    }
    static getWorkerPath() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonFileReadableStream_workerPath);
    }
}
_a = SalmonFileReadableStream;
_SalmonFileReadableStream_workerPath = { value: './lib/salmon-fs/salmonfs/salmon_file_readable_stream_worker.js' };
// Default cache buffer should be high enough for some mpeg videos to work
// the cache buffers should be aligned to the SalmonFile chunk size for efficiency
SalmonFileReadableStream.DEFAULT_BUFFER_SIZE = 512 * 1024;
// default threads is one but you can increase it
SalmonFileReadableStream.DEFAULT_THREADS = 1;
SalmonFileReadableStream.DEFAULT_BUFFERS = 3;
SalmonFileReadableStream.MAX_BUFFERS = 6;
export class ReadableStreamFileReader {
    constructor(salmonFile, buffersCount, bufferSize, threads, backOffset) {
        this.buffers = null;
        this.stream = null;
        _ReadableStreamFileReader_promises.set(this, []);
        _ReadableStreamFileReader_workers.set(this, []);
        // private ExecutorService executor;
        this.position = 0;
        this.size = 0;
        /**
         * We reuse the least recently used buffer. Since the buffer count is relative
         * small (see {@link #MAX_BUFFERS}) there is no need for a fast-access lru queue
         * so a simple linked list of keeping the indexes is adequately fast.
         */
        this.lruBuffersIndex = [];
        this.positionStart = 0;
        this.positionEnd = 0;
        this.closed = Promise.resolve(undefined);
        this.salmonFile = salmonFile;
        this.buffersCount = buffersCount;
        this.cacheBufferSize = bufferSize;
        this.threads = threads;
        this.backOffset = backOffset;
    }
    async initialize() {
        this.size = await this.salmonFile.getSize();
        this.positionStart = 0;
        this.positionEnd = this.size - 1;
        this.createBuffers();
        if (this.threads == 1) {
            await this.createStream();
        }
    }
    async read() {
        if (this.buffers == null)
            await this.initialize();
        let buff = new Uint8Array(this.cacheBufferSize);
        let bytesRead = await this.readStream(buff, 0, buff.length);
        if (bytesRead <= 0)
            return null;
        return buff.slice(0, bytesRead);
    }
    /**
     * Method creates the parallel streams for reading from the file
     */
    async createStream() {
        this.stream = await this.salmonFile.getInputStream();
    }
    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    createBuffers() {
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
    async skip(bytes) {
        if (this.buffers == null)
            await this.initialize();
        bytes += this.positionStart;
        let currPos = this.position;
        if (this.position + bytes > this.size)
            this.position = this.size;
        else
            this.position += bytes;
        return this.position - currPos;
    }
    reset() {
        this.position = 0;
    }
    /**
     * Reads and decrypts the contents of an encrypted file
     *
     * @param buffer The buffer that will store the decrypted contents
     * @param offset The position on the buffer that the decrypted data will start
     * @param count  The length of the data requested
     */
    async readStream(buffer, offset, count) {
        if (this.position >= this.positionEnd + 1)
            return -1;
        let minCount;
        let bytesRead;
        // truncate the count so getCacheBuffer() reports the correct buffer
        count = Math.floor(Math.min(count, this.size - this.position));
        let cacheBuffer = this.getCacheBuffer(this.position, count);
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
        let cOffset = Math.floor(this.position - cacheBuffer.startPos);
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
    async fillBuffer(cacheBuffer, startPosition, bufferSize) {
        let bytesRead;
        if (this.threads == 1) {
            if (this.stream == null)
                return 0;
            bytesRead = await fillBufferPart(cacheBuffer, startPosition, 0, bufferSize, this.stream);
        }
        else {
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
    async fillBufferMulti(cacheBuffer, startPosition, bufferSize) {
        let partSize = Math.ceil(bufferSize / this.threads);
        let bytesRead = 0;
        __classPrivateFieldSet(this, _ReadableStreamFileReader_promises, [], "f");
        for (let i = 0; i < this.threads; i++) {
            __classPrivateFieldGet(this, _ReadableStreamFileReader_promises, "f").push(new Promise(async (resolve, reject) => {
                let fileToReadHandle = await this.salmonFile.getRealFile().getPath();
                if (typeof process !== 'object') {
                    if (__classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i] == null)
                        __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i] = new Worker(SalmonFileReadableStream.getWorkerPath(), { type: 'module' });
                    __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].addEventListener('message', (event) => {
                        resolve(event.data);
                    });
                    __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].addEventListener('error', (event) => {
                        reject(event);
                    });
                }
                else {
                    const { Worker } = await import("worker_threads");
                    if (__classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i] == null)
                        __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i] = new Worker(SalmonFileReadableStream.getWorkerPath());
                    __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].on('message', (event) => {
                        if (event.message == 'complete') {
                            resolve(event);
                        }
                        else if (event.message == 'error') {
                            reject(event);
                        }
                    });
                    __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].on('error', (event) => {
                        reject(event);
                    });
                }
                let start = partSize * i;
                let length;
                if (i == this.threads - 1)
                    length = bufferSize - start;
                else
                    length = partSize;
                __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].postMessage({
                    message: 'start',
                    index: i,
                    startPosition: startPosition,
                    fileToReadHandle: fileToReadHandle,
                    readFileClassType: this.salmonFile.getRealFile().constructor.name,
                    start: start, length: length,
                    key: this.salmonFile.getEncryptionKey(),
                    integrity: this.salmonFile.getIntegrity(),
                    hash_key: this.salmonFile.getHashKey(),
                    chunk_size: this.salmonFile.getRequestedChunkSize(),
                    cacheBufferSize: this.cacheBufferSize
                });
            }));
        }
        await Promise.all(__classPrivateFieldGet(this, _ReadableStreamFileReader_promises, "f")).then((results) => {
            for (let i = 0; i < results.length; i++) {
                bytesRead += results[i].chunkBytesRead;
                let chunkStart = results[i].start;
                for (let j = 0; j < results[i].chunkBytesRead; j++)
                    cacheBuffer.buffer[chunkStart + j] = results[i].cacheBuffer[chunkStart + j];
            }
        }).catch((err) => {
            // deserialize the error
            if (err.error != undefined) {
                if (err.type == 'SalmonIntegrityException')
                    err = new SalmonIntegrityException(err.error);
                else if (err.type == 'SalmonAuthException')
                    err = new SalmonAuthException(err.error);
                else
                    err = new Error(err.error);
            }
            console.error(err);
            throw new IOException("Error during export", err);
        });
        return bytesRead;
    }
    /**
     * Returns an available cache buffer if there is none then reuse the least recently used one.
     */
    getAvailCacheBuffer() {
        if (this.buffers == null)
            return null;
        if (this.lruBuffersIndex.length == this.buffersCount) {
            // getting least recently used buffer
            let index = this.lruBuffersIndex.pop();
            // promote to the top
            delete this.lruBuffersIndex[index];
            this.lruBuffersIndex.unshift(index);
            return this.buffers[this.lruBuffersIndex.pop()];
        }
        for (let i = 0; i < this.buffers.length; i++) {
            let buffer = this.buffers[i];
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
    getCacheBuffer(position, count) {
        if (this.buffers == null)
            return null;
        for (let i = 0; i < this.buffers.length; i++) {
            let buffer = this.buffers[i];
            if (buffer != null && position >= buffer.startPos && position + count <= buffer.startPos + buffer.count) {
                // promote buffer to the front
                delete this.lruBuffersIndex[i];
                this.lruBuffersIndex.unshift(i);
                return buffer;
            }
        }
        return null;
    }
    getPositionStart() {
        return this.positionStart;
    }
    async setPositionStart(pos) {
        if (this.buffers == null)
            await this.initialize();
        this.positionStart = pos;
    }
    async setPositionEnd(pos) {
        if (this.buffers == null)
            await this.initialize();
        this.positionEnd = pos;
    }
    /**
     * Clear all buffers.
     */
    clearBuffers() {
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
     * @throws IOException
     */
    async closeStream() {
        if (this.stream != null)
            await this.stream.close();
        this.stream = null;
    }
    async cancel(reason) {
        await this.closeStream();
        this.clearBuffers();
        for (let i = 0; i < __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f").length; i++) {
            __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].postMessage({ message: 'close' });
            __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i].terminate();
            __classPrivateFieldGet(this, _ReadableStreamFileReader_workers, "f")[i] = null;
        }
    }
}
_ReadableStreamFileReader_promises = new WeakMap(), _ReadableStreamFileReader_workers = new WeakMap();
