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

/**
 * Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
 * If the memory buffer is not specified then an internal resizable buffer will be created.
 */
export class MemoryStream extends RandomAccessStream {

    /**
     * Increment to resize to when capacity is exhausted.
     */
    public static readonly INITIAL_CAPACITY: number = 128 * 1024;

    /**
     * Buffer to store the data. This can be provided via the constructor.
     */
    #bytes: Uint8Array;

    /**
     * Current position of the stream.
     */
    #position: number = 0;

    /**
     * Current capacity.
     */
    #capacity: number = 0;

    /**
     * Current length of the stream.
     */
    #length: number = 0;

    /**
     * Create a memory stream.
     * @param {Uint8Array} bytes Optional existing byte array to use as backing buffer.
     * If omitted a new backing array will be created automatically.
     */
    public constructor(bytes: Uint8Array | null = null) {
        super();
        if (bytes != null) {
            this.#length = bytes.length;
            this.#bytes = bytes;
            this.#capacity = bytes.length;
        } else {
            this.#bytes = new Uint8Array(MemoryStream.INITIAL_CAPACITY);
            this.#capacity = MemoryStream.INITIAL_CAPACITY;
        }
    }

    /**
     * @return {Promise<boolean>} If the stream can be used for reading.
     */
    public override async canRead(): Promise<boolean> {
        return true;
    }

    /**
     * @return {Promise<boolean>} If the stream can be used for writing.
     */
    public override async canWrite(): Promise<boolean> {
        return true;
    }

    /**
     * @return {Promise<boolean>} If the stream is seekable.
     */
    public override async canSeek(): Promise<boolean> {
        return true;
    }

    /**
     *
     * @return {Promise<number>} The length of the stream.
     */
    public override async getLength(): Promise<number> {
        return this.#length;
    }

    /**
     *
     * @return {Promise<number>} The position of the stream.
     */
    public override async getPosition(): Promise<number> {
        return this.#position;
    }

    /**
     * Changes the current position of the stream. For more options use seek() method.
     * @param value The new position of the stream.
     */
    public override async setPosition(value: number): Promise<void> {
        this.#position = value;
    }

    /**
     * Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
     * current capacity.
     * @param {Promise<number>} value The new position of the stream.
     */
    public override async setLength(value: number): Promise<void> {
        this.#checkAndResize(value);
        this.#capacity = value;
    }

    /**
     * Read a sequence of bytes into the provided buffer.
     * @param {Uint8Array} buffer The buffer to write the bytes that are read from the stream.
     * @param {number} offset The offset of the buffer that will be used to write the bytes.
     * @param {number} count The length of the bytes that can be read from the stream and written to the buffer.
     * @return {Promise<number>} The number of bytes read.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        const bytesRead: number = Math.min(this.#length - await this.getPosition(), count);
        for (let i = 0; i < bytesRead; i++)
            buffer[offset + i] = this.#bytes[this.#position + i];
        await this.setPosition(await this.getPosition() + bytesRead);
        if (bytesRead <= 0)
            return -1;
        return bytesRead;
    }

    /**
     * Write a sequence of bytes into the stream.
     * @param {Uint8Array} buffer The buffer that the bytes will be read from.
     * @param {number} offset The position offset that will be used to read from the buffer.
     * @param {number} count The number of bytes that will be written to the stream.
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        this.#checkAndResize(this.#position + count);
        for (let i = 0; i < count; i++)
            this.#bytes[this.#position + i] = buffer[offset + i];
        await this.setPosition(await this.getPosition() + count);
    }

    /**
     * Check if there is no more space in the byte array and increase the capacity.
     * @param {number} newLength The new length of the stream.
     */
    #checkAndResize(newLength: number): void {
        if (this.#capacity < newLength) {
            let newCapacity: number = newLength * 2;
            if (newCapacity > Number.MAX_SAFE_INTEGER)
                throw new Error("Size too large");
            const nBytes: Uint8Array = new Uint8Array(newCapacity);
            for (let i = 0; i < this.#capacity; i++)
                nBytes[i] = this.#bytes[i];
            this.#capacity = newCapacity;
            this.#bytes = nBytes;
        }
        this.#length = newLength;
    }

    /**
     * Seek to a position in the stream.
     * @param {number} offset The offset to use.
     * @param {SeekOrigin.Begin} origin The origin type.
     * @return {Promise<number>} The position after the seeking was complete.
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        let nPos: number = 0;
        if (origin === SeekOrigin.Begin) {
            nPos = offset;
        } else if (origin === SeekOrigin.Current) {
            nPos = await this.getPosition() + offset;
        } else if (origin === SeekOrigin.End) {
            nPos = (this.#bytes.length - offset);
        }
        this.#checkAndResize(nPos);
        await this.setPosition(nPos);
        return await this.getPosition();
    }

    /**
     * Flush the stream. Not-Applicable for memory stream.
     */
    public override async flush(): Promise<void> {
        // nop
    }

    /**
     * Close any resources the stream is using. Not-Applicable for memory stream.
     */
    public override async close(): Promise<void> {
        // nop
    }

    /**
     * Convert the stream to an array:
     * @return {Uint8Array} A byte array containing the data from the stream.
     */
    public toArray(): Uint8Array {
        const nBytes: Uint8Array = new Uint8Array(this.#length);
		nBytes.set(this.#bytes.slice(0, this.#length));
        return nBytes;
    }
}