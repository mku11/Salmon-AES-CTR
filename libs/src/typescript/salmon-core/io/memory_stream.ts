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
    private static readonly CAPACITY_INCREMENT: number = 128 * 1024;

    /**
     * Buffer to store the data. This can be provided via the constructor.
     */
    private bytes: Uint8Array;

    /**
     * Current position of the stream.
     */
    private _position: number = 0;

    /**
     * Current capacity.
     */
    private _capacity: number = 0;

    /**
     * Current length of the stream.
     */
    private _length: number = 0;

    /**
     * Create a memory stream backed by an existing byte-array.
     * @param bytes
     */
    public constructor(bytes?: Uint8Array) {
        super();
        if (bytes != null && bytes != undefined) {
            this._length = bytes.length;
            this.bytes = bytes;
            this._capacity = bytes.length;
        } else {
            this.bytes = new Uint8Array(MemoryStream.CAPACITY_INCREMENT);
            this._capacity = MemoryStream.CAPACITY_INCREMENT;
        }
    }

    /**
     * @return Always True.
     */
    public override canRead(): boolean {
        return true;
    }

    /**
     * @return Always True.
     */
    public override canWrite(): boolean {
        return true;
    }

    /**
     * @return Always True.
     */
    public override canSeek(): boolean {
        return true;
    }

    /**
     *
     * @return The length of the stream.
     */
    public override length(): number {
        return this._length;
    }

    /**
     *
     * @return The position of the stream.
     * @throws IOException
     */
    public override getPosition(): number {
        return this._position;
    }

    /**
     * Changes the current position of the stream. For more options use seek() method.
     * @param value The new position of the stream.
     * @throws IOException
     */
    public override setPosition(value: number): void {
        this._position = value;
    }

    /**
     * Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
     * current capacity.
     * @param value
     * @throws IOException
     */
    public override setLength(value: number): void {
        this.checkAndResize(value);
        this._capacity = value;
    }

    /**
     * Read a sequence of bytes into the provided buffer.
     * @param buffer The buffer to write the bytes that are read from the stream.
     * @param offset The offset of the buffer that will be used to write the bytes.
     * @param count The length of the bytes that can be read from the stream and written to the buffer.
     * @return
     * @throws IOException
     */
    public override async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        let bytesRead: number = Math.min(this._length - this.getPosition(), count);
        for (let i = 0; i < bytesRead; i++)
            buffer[offset + i] = this.bytes[this._position + i];
        this.setPosition(this.getPosition() + bytesRead);
        if (bytesRead <= 0)
            return -1;
        return bytesRead;
    }

    /**
     * Write a sequence of bytes into the stream.
     * @param buffer The buffer that the bytes will be read from.
     * @param offset The position offset that will be used to read from the buffer.
     * @param count The number of bytes that will be written to the stream.
     * @throws IOException
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        this.checkAndResize(this._position + count);
        for (let i = 0; i < count; i++)
            this.bytes[this._position + i] = buffer[offset + i];
        this.setPosition(this.getPosition() + count);
    }

    /**
     * Check if there is no more space in the byte array and increase the capacity.
     * @param newLength The new length of the stream.
     */
    private checkAndResize(newLength: number): void {
        if (this._capacity < newLength) {
            let newCapacity: number = this._capacity + MemoryStream.CAPACITY_INCREMENT * ((newLength - this._capacity) / MemoryStream.CAPACITY_INCREMENT);
            if (newCapacity < newLength)
                newCapacity += MemoryStream.CAPACITY_INCREMENT;
            let nBytes: Uint8Array = new Uint8Array(newCapacity);
            for (let i = 0; i < this._capacity; i++)
                nBytes[i] = this.bytes[i];
            this._capacity = newCapacity;
            this.bytes = nBytes;
        }
        this._length = newLength;
    }

    /**
     * Seek to a position in the stream.
     * @param offset
     * @param origin Possible Values: Begin, Current, End
     * @return
     * @throws IOException
     */
    public override seek(offset: number, origin: SeekOrigin): number {
        let nPos: number = 0;
        if (origin == SeekOrigin.Begin) {
            nPos = offset;
        } else if (origin == SeekOrigin.Current) {
            nPos = this.getPosition() + offset;
        } else if (origin == SeekOrigin.End) {
            nPos = (this.bytes.length - offset);
        }
        this.checkAndResize(nPos);
        this.setPosition(nPos);
        return this.getPosition();
    }

    /**
     * Flush the stream. Not-Applicable for memory stream.
     */
    public override flush(): void {

    }

    /**
     * Close any resources the stream is using. Not-Applicable for memory stream.
     */

    public override close(): void {

    }

    /**
     * Convert the stream to an array:
     * @return A byte array containing the data from the stream.
     */
    public toArray(): Uint8Array {
        let nBytes: Uint8Array = new Uint8Array(this._length);
        for (let i = 0; i < this._length; i++)
            nBytes[i] = this.bytes[i];
        return nBytes;
    }
}