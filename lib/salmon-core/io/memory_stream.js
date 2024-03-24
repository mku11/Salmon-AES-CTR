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
var _MemoryStream_instances, _a, _MemoryStream_CAPACITY_INCREMENT, _MemoryStream_bytes, _MemoryStream__position, _MemoryStream__capacity, _MemoryStream__length, _MemoryStream_checkAndResize;
import { RandomAccessStream, SeekOrigin } from "./random_access_stream.js";
/**
 * Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
 * If the memory buffer is not specified then an internal resizable buffer will be created.
 */
export class MemoryStream extends RandomAccessStream {
    /**
     * Create a memory stream backed by an existing byte-array.
     * @param bytes
     */
    constructor(bytes = null) {
        super();
        _MemoryStream_instances.add(this);
        /**
         * Buffer to store the data. This can be provided via the constructor.
         */
        _MemoryStream_bytes.set(this, void 0);
        /**
         * Current position of the stream.
         */
        _MemoryStream__position.set(this, 0);
        /**
         * Current capacity.
         */
        _MemoryStream__capacity.set(this, 0);
        /**
         * Current length of the stream.
         */
        _MemoryStream__length.set(this, 0);
        if (bytes != null) {
            __classPrivateFieldSet(this, _MemoryStream__length, bytes.length, "f");
            __classPrivateFieldSet(this, _MemoryStream_bytes, bytes, "f");
            __classPrivateFieldSet(this, _MemoryStream__capacity, bytes.length, "f");
        }
        else {
            __classPrivateFieldSet(this, _MemoryStream_bytes, new Uint8Array(__classPrivateFieldGet(_a, _a, "f", _MemoryStream_CAPACITY_INCREMENT)), "f");
            __classPrivateFieldSet(this, _MemoryStream__capacity, __classPrivateFieldGet(_a, _a, "f", _MemoryStream_CAPACITY_INCREMENT), "f");
        }
    }
    /**
     * @return Always True.
     */
    async canRead() {
        return true;
    }
    /**
     * @return Always True.
     */
    async canWrite() {
        return true;
    }
    /**
     * @return Always True.
     */
    async canSeek() {
        return true;
    }
    /**
     *
     * @return The length of the stream.
     */
    async length() {
        return __classPrivateFieldGet(this, _MemoryStream__length, "f");
    }
    /**
     *
     * @return The position of the stream.
     * @throws IOException
     */
    async getPosition() {
        return __classPrivateFieldGet(this, _MemoryStream__position, "f");
    }
    /**
     * Changes the current position of the stream. For more options use seek() method.
     * @param value The new position of the stream.
     * @throws IOException
     */
    async setPosition(value) {
        __classPrivateFieldSet(this, _MemoryStream__position, value, "f");
    }
    /**
     * Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
     * current capacity.
     * @param value
     * @throws IOException
     */
    async setLength(value) {
        __classPrivateFieldGet(this, _MemoryStream_instances, "m", _MemoryStream_checkAndResize).call(this, value);
        __classPrivateFieldSet(this, _MemoryStream__capacity, value, "f");
    }
    /**
     * Read a sequence of bytes into the provided buffer.
     * @param buffer The buffer to write the bytes that are read from the stream.
     * @param offset The offset of the buffer that will be used to write the bytes.
     * @param count The length of the bytes that can be read from the stream and written to the buffer.
     * @return
     * @throws IOException
     */
    async read(buffer, offset, count) {
        const bytesRead = Math.min(__classPrivateFieldGet(this, _MemoryStream__length, "f") - await this.getPosition(), count);
        for (let i = 0; i < bytesRead; i++)
            buffer[offset + i] = __classPrivateFieldGet(this, _MemoryStream_bytes, "f")[__classPrivateFieldGet(this, _MemoryStream__position, "f") + i];
        await this.setPosition(await this.getPosition() + bytesRead);
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
    async write(buffer, offset, count) {
        __classPrivateFieldGet(this, _MemoryStream_instances, "m", _MemoryStream_checkAndResize).call(this, __classPrivateFieldGet(this, _MemoryStream__position, "f") + count);
        for (let i = 0; i < count; i++)
            __classPrivateFieldGet(this, _MemoryStream_bytes, "f")[__classPrivateFieldGet(this, _MemoryStream__position, "f") + i] = buffer[offset + i];
        await this.setPosition(await this.getPosition() + count);
    }
    /**
     * Seek to a position in the stream.
     * @param offset
     * @param origin Possible Values: Begin, Current, End
     * @return
     * @throws IOException
     */
    async seek(offset, origin) {
        let nPos = 0;
        if (origin === SeekOrigin.Begin) {
            nPos = offset;
        }
        else if (origin === SeekOrigin.Current) {
            nPos = await this.getPosition() + offset;
        }
        else if (origin === SeekOrigin.End) {
            nPos = (__classPrivateFieldGet(this, _MemoryStream_bytes, "f").length - offset);
        }
        __classPrivateFieldGet(this, _MemoryStream_instances, "m", _MemoryStream_checkAndResize).call(this, nPos);
        await this.setPosition(nPos);
        return await this.getPosition();
    }
    /**
     * Flush the stream. Not-Applicable for memory stream.
     */
    async flush() {
        // noop
    }
    /**
     * Close any resources the stream is using. Not-Applicable for memory stream.
     */
    async close() {
        // noop
    }
    /**
     * Convert the stream to an array:
     * @return A byte array containing the data from the stream.
     */
    toArray() {
        const nBytes = new Uint8Array(__classPrivateFieldGet(this, _MemoryStream__length, "f"));
        for (let i = 0; i < __classPrivateFieldGet(this, _MemoryStream__length, "f"); i++)
            nBytes[i] = __classPrivateFieldGet(this, _MemoryStream_bytes, "f")[i];
        return nBytes;
    }
}
_a = MemoryStream, _MemoryStream_bytes = new WeakMap(), _MemoryStream__position = new WeakMap(), _MemoryStream__capacity = new WeakMap(), _MemoryStream__length = new WeakMap(), _MemoryStream_instances = new WeakSet(), _MemoryStream_checkAndResize = function _MemoryStream_checkAndResize(newLength) {
    if (__classPrivateFieldGet(this, _MemoryStream__capacity, "f") < newLength) {
        let newCapacity = newLength * 2;
        const nBytes = new Uint8Array(newCapacity);
        for (let i = 0; i < __classPrivateFieldGet(this, _MemoryStream__capacity, "f"); i++)
            nBytes[i] = __classPrivateFieldGet(this, _MemoryStream_bytes, "f")[i];
        __classPrivateFieldSet(this, _MemoryStream__capacity, newCapacity, "f");
        __classPrivateFieldSet(this, _MemoryStream_bytes, nBytes, "f");
    }
    __classPrivateFieldSet(this, _MemoryStream__length, newLength, "f");
};
/**
 * Increment to resize to when capacity is exhausted.
 */
_MemoryStream_CAPACITY_INCREMENT = { value: 128 * 1024 };
