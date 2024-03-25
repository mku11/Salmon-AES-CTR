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

import { SalmonDefaultOptions } from "../salmon/salmon_default_options.js";
import { IOException } from "./io_exception.js";

/**
 * Base class for read-write seekable streams.
 */
export abstract class RandomAccessStream {

    /**
     * True if the stream is readable.
     * @return {Promise<boolean>} True if can read.
     */
    public abstract canRead(): Promise<boolean>;

    /**
     * True if the stream is writeable.
     * @return {Promise<boolean>} True if can write.
     */
    public abstract canWrite(): Promise<boolean>;

    /**
     * True if the stream is seekable.
     * @return {Promise<boolean>} True if can seek.
     */
    public abstract canSeek(): Promise<boolean>;

    /**
     * Get the length of the stream.
     * @return {Promise<number>} The length of the stream.
     */
    public abstract length(): Promise<number>;

    /**
     * Get the current position of the stream.
     * @return {Promise<number>} The current position.
     * @throws IOException
     */
    public abstract getPosition(): Promise<number>;

    /**
     * Change the current position of the stream.
     * @param {number} value The new position.
     * @throws IOException
     */
    public abstract setPosition(value: number): Promise<void>;

    /**
     * Set the length of this stream.
     * @param {number} value The new length.
     * @throws IOException
     */
    public abstract setLength(value: number): Promise<void>;

    /**
     *
     * @param {Uint8Array} buffer The buffer to read into.
     * @param {number} offset The offset to start reading into the buffer.
     * @param {number} count The number of bytes that were read. If the stream reached the end return -1.
     * @return {Promise<number>} The number of bytes read.
     * @throws IOException
     */
    public abstract read(buffer: Uint8Array, offset: number, count: number): Promise<number>;

    /**
     * Write the contents of the buffer to this stream.
     * @param {Uint8Array} buffer The buffer to read the contents from.
     * @param {number} offset The position the reading will start from.
     * @param {number} count The count of bytes to be read from the buffer.
     * @throws IOException
     */
    public abstract write(buffer: Uint8Array, offset: number, count: number): Promise<void>;

    /**
     * Seek to a specific position in the stream.
     * @param {number} position The offset to use.
     * @param {SeekOrigin} origin The origin type.
     * @return {Promise<number>} The position after the seeking was complete.
     * @throws IOException
     */
    public abstract seek(offset: number, origin: SeekOrigin): Promise<number>;

    /**
     * Flush buffers.
     */
    public abstract flush(): Promise<void>;

    /**
     * Close the stream and associated resources.
     * @throws IOException
     */
    public abstract close(): Promise<void>;

    /**
     * Write stream contents to another stream.
     * @param {RandomAccessStream} stream The target stream.
     * @param {number | null} bufferSize The buffer size to be used when copying.
     * @param {((position: number, length: number) => void) | null} progressListener The listener to notify when progress changes.
     * @throws IOException
     */
    public async copyTo(stream: RandomAccessStream, bufferSize: number | null = null, 
        progressListener: ((position: number, length: number) => void) | null = null): Promise<void> {
        if (!(await this.canRead()))
            throw new IOException("Target stream not readable");
        if (!(await stream.canWrite()))
            throw new IOException("Target stream not writable");
        if (bufferSize == null || bufferSize <= 0) {
            bufferSize = SalmonDefaultOptions.getBufferSize(); // TODO: remove ref to Salmon
        }
        let bytesRead: number;
        const pos: number = await this.getPosition();
        const buffer: Uint8Array = new Uint8Array(bufferSize);
        while ((bytesRead = await this.read(buffer, 0, bufferSize)) > 0) {
            await stream.write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener(await this.getPosition(), await this.length());
        }
        await stream.flush();
        await this.setPosition(pos);
    }
}

/**
 * Used to identify the start offset for seeking to a stream.
 */
export enum SeekOrigin {
    /**
     * Start from the beginning of the stream.
     */
    Begin,
    /**
     * Start from the current position of the stream.
     */
    Current,
    /**
     * Start from the end of the stream.
     */
    End
}