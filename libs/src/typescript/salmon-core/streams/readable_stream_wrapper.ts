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

import { RandomAccessStream, SeekOrigin } from "./random_access_stream.js";
import { Buffer } from "./buffer.js";

/**
 * Fills a cache buffer with the decrypted data from a part of an encrypted file.
 * Do not use directly, use ReadableStreamWrapper.create() instead.
 *
 * @param {Buffer} cacheBuffer  The cache buffer that will store the decrypted contents
 * @param {Uint8Array} length   The length of the data requested
 * @param {RandomAccessStream} stream The stream that will be used to read from
 */
export async function fillBufferPart(cacheBuffer: Buffer, start: number, offset: number, length: number,
    stream: RandomAccessStream): Promise<number> {
    await stream.seek(start, SeekOrigin.Begin);
    let bytesRead = 0;
    let totalBytesRead = 0;
    while ((bytesRead = await stream.read(cacheBuffer.getData(), offset + totalBytesRead, length - totalBytesRead)) > 0) {
        totalBytesRead += bytesRead;
    }
    return totalBytesRead;
}

/***
 * ReadableStream wrapper for RandomAccessStream.
 * Use this class to wrap any RandomAccessStream to a JavaScript ReadableStream to use with 3rd party libraries.
 */
export class ReadableStreamWrapper {
    
    // Default cache buffer should be high enough for some mpeg videos to work
    // the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    public static readonly DEFAULT_BUFFER_SIZE: number = 512 * 1024;

    /**
     * The default buffer count
     */
    public static readonly DEFAULT_BUFFERS: number = 1;

    /**
     * The default backwards buffer offset
     */
    public static readonly DEFAULT_BACK_OFFSET = 32768;

    /**
     * The maximum allowed buffer count
     */
    protected static readonly MAX_BUFFERS: number = 6;

    #buffers: (Buffer | null)[] | null = null;
    #stream: RandomAccessStream | null = null;
    #readableStream: ReadableStream | null = null;
    #streamPosition: number = 0;
    #totalSize: number = 0;

    #buffersCount: number = 0;
    #bufferSize: number = 0;
    #backOffset: number = 0;
    #alignSize: number = 0;
    
    /**
     * We reuse the least recently used buffer. Since the buffer count is relative
     * small (see {@link #MAX_BUFFERS}) there is no need for a fast-access lru queue
     * so a simple linked list of keeping the indexes is adequately fast.
     */
    lruBuffersIndex: number[] = [];

    protected setStream(stream: RandomAccessStream) {
        this.#stream = stream;
    }

    public getStream() : RandomAccessStream | null {
        return this.#stream;
    }

    public getBackOffset() : number {
        return this.#backOffset;
    }

    protected setBackOffset(backOffset: number) {
        this.#backOffset = backOffset;
    }

    public getTotalSize() : number {
        return this.#totalSize;
    }

    protected setTotalSize(totalSize: number) {
        this.#totalSize = totalSize;
    }

    protected setAlignSize(alignSize: number) {
        this.#alignSize = alignSize;
    }

    public getBufferSize() : number {
        return this.#bufferSize;
    }

    protected setBufferSize(bufferSize: number) {
        this.#bufferSize = bufferSize;
    }
    
    public getBufferCount() : number {
        return this.#buffersCount;
    }

    protected setBufferCount(buffersCount: number) {
        this.#buffersCount = buffersCount;
    }

    /**
     * Creates an ReadableStreamWrapper from a RandomAccessStream.
     * @param {RandomAccessStream | null} stream The stream that you want to wrap.
     */
    public static createReadableStream(stream: RandomAccessStream,
                buffersCount: number = ReadableStreamWrapper.DEFAULT_BUFFERS, 
                bufferSize: number = ReadableStreamWrapper.DEFAULT_BUFFER_SIZE, 
                backOffset: number = ReadableStreamWrapper.DEFAULT_BACK_OFFSET, 
                alignSize: number = 0) {
        let readableStreamWrapper: ReadableStreamWrapper = new ReadableStreamWrapper();
        readableStreamWrapper.#stream = stream;
        readableStreamWrapper.#buffersCount = buffersCount;
        readableStreamWrapper.#bufferSize = bufferSize;
        readableStreamWrapper.#backOffset = backOffset;
        readableStreamWrapper.#alignSize = alignSize;
        let readableStream: any = ReadableStreamWrapper.createReadableStreamReader(readableStreamWrapper);
        return readableStream;
    }

    protected static createReadableStreamReader(streamWrapper: ReadableStreamWrapper): ReadableStream {
        let resetting: boolean = false;
        let readableStream: any = new ReadableStream({
            type: 'bytes',
            async pull(controller: any) {
                if(resetting) {
                    controller.enqueue(new Uint8Array([0]));
                    return;
                }
                if (streamWrapper.#buffers == null)
                    await streamWrapper.initialize();
                let size: number = streamWrapper.getBufferSize();
                let buffer: Uint8Array = new Uint8Array(size);
                let bytesRead = await streamWrapper.read(buffer, 0, buffer.length);
                if(bytesRead > 0)
                    controller.enqueue(buffer.slice(0, bytesRead));
                if (bytesRead <= 0) {
                    controller.close();
                }
            }
        });
        let streamGetReader: any = readableStream.getReader;
        let reader: any;
        readableStream.getReader = function(options?: any) : ReadableStreamDefaultReader {
            reader = streamGetReader.apply(readableStream, options);
            return reader;
        }
        readableStream.reset = async function(): Promise<void> {
            // make sure the queue is emptied
            if(reader) {
                resetting = true;
                while(true) {
                    let chunk = await reader.read();
                    if(!chunk || !chunk.value
                        || (chunk.value.length == 1 && chunk.value[0] == 0))
                        break;
                }
                resetting = false;
            }
            await streamWrapper.reset();
        }
        readableStream.cancel = async function (reason?: any): Promise<void> {
            await streamWrapper.cancel(reason);
        }
        readableStream.skip = async function (position: number): Promise<number> {
            return await streamWrapper.skip(position);
        }
        readableStream.getPositionStart = function (): number {
            return streamWrapper.getPositionStart();
        }
        readableStream.setPositionStart = async function (position: any): Promise<void> {
            await streamWrapper.setPositionStart(position);
        }
        readableStream.setPositionEnd = async function (position: any): Promise<void> {
            await streamWrapper.setPositionEnd(position);
        }
        streamWrapper.#readableStream = readableStream;
        return readableStream;
    }

    protected async initialize() : Promise<void> {
        if (this.#stream != null) {
            this.#totalSize = await this.#stream.getLength();
			try {
				this.#streamPosition = await this.#stream.getPosition();
			} catch (ex) {
				throw new Error("Could not get stream current position: " + ex);
			}
		}
        if (this.#buffersCount <= 0)
            this.#buffersCount = ReadableStreamWrapper.DEFAULT_BUFFERS;
        if (this.#buffersCount > ReadableStreamWrapper.MAX_BUFFERS)
            this.#buffersCount = ReadableStreamWrapper.MAX_BUFFERS;
        if (this.#bufferSize <= 0)
            this.#bufferSize = ReadableStreamWrapper.DEFAULT_BUFFER_SIZE;
        if (this.#backOffset < 0)
            this.#backOffset = ReadableStreamWrapper.DEFAULT_BACK_OFFSET;

        if (this.#alignSize <= 0 && this.#stream != null)
            this.#alignSize = this.#stream.getAlignSize();

        // align the buffers for performance
        if (this.#alignSize > 0) {
            if (this.#backOffset > 0) {
                let nBackOffset = Math.floor(this.#backOffset / this.#alignSize) * this.#alignSize;
                if (nBackOffset < this.#backOffset)
                    nBackOffset += this.#alignSize;
                this.#backOffset = nBackOffset;
            }

            let nBufferSize = Math.floor(this.#bufferSize / this.#alignSize) * this.#alignSize;
            if (nBufferSize < this.#alignSize) {
                nBufferSize = this.#alignSize;
            }
            if (nBufferSize < this.#bufferSize) {
                nBufferSize += this.#alignSize;
            }
            this.#bufferSize = nBufferSize;
        }

        if (this.#backOffset > 0) {
            this.#bufferSize += this.#backOffset;
            // we use a minimum 2 buffers since it is very likely
            // that the previous buffer in use will have the backoffset
            // data of the new one
            if (this.#buffersCount == 1)
                this.#buffersCount = 2;
        }
        this.#positionStart = 0;
        this.#positionEnd = this.#totalSize - 1;

        this.#createBuffers();
    }

    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    #createBuffers(): void {
        this.#buffers = new Array(this.#buffersCount);
        for (let i = 0; i < this.#buffers.length; i++)
            this.#buffers[i] = new Buffer(this.#bufferSize);
    }

    /**
     * Skip a number of bytes.
     *
     * @param {number} bytes the number of bytes to be skipped.
     * @returns {Promise<number>} The new position
     */
    public async skip(bytes: number): Promise<number> {
        if (this.#buffers == null)
            await this.initialize();
        bytes += this.#positionStart;
        let currPos: number = this.#streamPosition;
        if (this.#streamPosition + bytes > this.#totalSize)
            this.#streamPosition = this.#totalSize;
        else
            this.#streamPosition += bytes;
        return this.#streamPosition - currPos;
    }

    /**
     * Reset the stream
     */
    public reset(): void {
        this.#streamPosition = 0;
    }

    /**
     * Reads and decrypts the contents of an encrypted file
     */
    protected async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        if (this.#buffers == null)
            await this.initialize();

        if (this.#streamPosition >= this.#positionEnd + 1)
            return -1;

        let minCount: number;
        let bytesRead: number;

        // truncate the count so getCacheBuffer() reports the correct buffer
        count = Math.floor(Math.min(count, this.#totalSize - this.#streamPosition));

        let cacheBuffer: Buffer | null = this.#getCacheBuffer(this.#streamPosition, count);
        if (cacheBuffer == null) {
            cacheBuffer = this.#getAvailCacheBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return 0;

            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            let startPosition = this.#streamPosition;
            if (this.#alignSize > 0) {
                startPosition = Math.floor(startPosition / this.#alignSize) * this.#alignSize;
            }

            let length = this.#bufferSize;

            // if we have the backoffset data in an existing buffer we don't include the backoffset
            // in the new request because we want to prevent network streams resetting.
            if (startPosition > 0 && !this.#hasBackoffset(startPosition)) {
                startPosition -= this.#backOffset;
            } else {
                length -= this.#backOffset;
            }

            bytesRead = await this.fillBuffer(cacheBuffer, startPosition, length);
            if (bytesRead <= 0)
                return bytesRead;
            cacheBuffer.setStartPos(startPosition);
            cacheBuffer.setCount(bytesRead);
        }
        minCount = Math.min(count, Math.floor(cacheBuffer.getCount() - this.#streamPosition + cacheBuffer.getStartPos()));
        let cOffset: number = Math.floor(this.#streamPosition - cacheBuffer.getStartPos());
        for (let i = 0; i < minCount; i++)
            buffer[offset + i] = cacheBuffer.getData()[cOffset + i];
        
        this.#streamPosition += minCount;
        return minCount;
    }

    
    #hasBackoffset(startPosition: number) : boolean{
        let pos = startPosition - this.#backOffset;
        if(this.#buffers == null)
            throw new Error("Buffers are not initialized");

        for (let i = 0; i < this.#buffers.length; i++) {
            let buffer: Buffer | null = this.#buffers[i];
            if (buffer != null && buffer.getCount() > 0
                    && buffer.getStartPos() <= pos
                    && startPosition <= buffer.getStartPos() + buffer.getCount()) {
                return true;
            }
        }
        return false;
    }


    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     * @param { Buffer } cacheBuffer The cache buffer that will store the decrypted contents
	 * @param { number } startPosition The start position
     * @param { number } length      The length of the data requested
     */
    protected async fillBuffer(cacheBuffer: Buffer, startPosition: number, length: number): Promise<number> {
        let bytesRead: number;
        if (this.#stream == null)
            return 0;
        bytesRead = await fillBufferPart(cacheBuffer, startPosition, 0, length, this.#stream);
        return bytesRead;
    }

    /**
     * Returns an available cache buffer if there is none then reuse the least recently used one.
     */
    #getAvailCacheBuffer(): Buffer | null {
        if (this.#buffers == null)
            throw new Error("No buffers found");
        let index: number = -1;
        if (this.lruBuffersIndex.length == this.#buffersCount) {
            index = this.lruBuffersIndex[this.lruBuffersIndex.length-1];
            this.lruBuffersIndex.pop();
        } else {
            for (let i = 0; i < this.#buffers.length; i++) {
                let buff: Buffer | null = this.#buffers[i];
                if (buff && buff.getCount() == 0) {
                    index = i;
                    break;
                }
            }
        }

        if(index < 0)
            index = this.#buffers.length - 1;
        
        this.lruBuffersIndex.unshift(index);
        return this.#buffers[index];
    }

    /**
     * Returns the buffer that contains the data requested.
     *
     * @param {number} position The source file position of the data to be read
     * @param {number} count The number of bytes to read
     */
    #getCacheBuffer(position: number, count: number): Buffer | null {
        if (this.#buffers == null)
            return null;
        for (let i = 0; i < this.#buffers.length; i++) {
            let buffer: Buffer | null = this.#buffers[i];
            if (buffer  && position >= buffer.getStartPos() && position + count <= buffer.getStartPos() + buffer.getCount()) {
                // promote buffer to the front
                let index = -1;
                for(let k = 0; k < this.lruBuffersIndex.length; k++) {
                    if(this.lruBuffersIndex[k] == i) {
                        index = k;
                        break;
                    }
                }
                if(index >= 0)
                    this.lruBuffersIndex.splice(index,1);
                this.lruBuffersIndex.unshift(i);
                return buffer;
            }
        }
        return null;
    }

    #positionStart: number = 0;
    /**
     * Get the start position of the stream
     * @returns {number} The start position of the stream
     */
    public getPositionStart(): number {
        return this.#positionStart;
    }

    /**
     * Set the start position of the stream.
     * @param {number} pos The start position
     */
    public async setPositionStart(pos: number): Promise<void> {
        if (this.#buffers == null)
            await this.initialize();
        this.#positionStart = pos;
    }
    #positionEnd: number = 0;

    /**
     * Set the end position of the stream
     * @param {number} pos The end position of the stream
     */
    public async setPositionEnd(pos: number): Promise<void> {
        if (this.#buffers == null)
            await this.initialize();
        this.#positionEnd = pos;
    }

    /**
     * Clear all buffers.
     */
    #clearBuffers(): void {
        if (this.#buffers == null)
            return;
        for (let i = 0; i < this.#buffers.length; i++) {
            let buffer = this.#buffers[i];
            if (buffer)
                buffer.clear();
            this.#buffers[i] = null;
        }
    }

    /**
     * Close all back streams.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    async #closeStream(): Promise<void> {
        if (this.#stream)
            await this.#stream.close();
        this.#stream = null;
    }

    closed: Promise<undefined> = Promise.resolve(undefined);

    /**
     * Cancel the stream
     * @param {any} [reason] The reason
     */
    protected async cancel(reason?: any): Promise<void> {
        await this.#closeStream();
        this.#clearBuffers();
    }
}