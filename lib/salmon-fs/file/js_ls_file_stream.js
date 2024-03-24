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
var _JsLocalStorageFileStream_file, _JsLocalStorageFileStream_stream, _JsLocalStorageFileStream_canWrite, _JsLocalStorageFileStream_base64;
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { RandomAccessStream } from "../../salmon-core/io/random_access_stream.js";
import { Base64 } from "../../salmon-core/convert/base64.js";
/**
 * An advanced file stream implementation for localStorage files.
 * This class can be used to read and write small file in localStorage.
 */
export class JsLocalStorageFileStream extends RandomAccessStream {
    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    constructor(file, mode) {
        super();
        /**
         * The java file associated with this stream.
         */
        _JsLocalStorageFileStream_file.set(this, void 0);
        _JsLocalStorageFileStream_stream.set(this, void 0);
        _JsLocalStorageFileStream_canWrite.set(this, false);
        _JsLocalStorageFileStream_base64.set(this, void 0);
        __classPrivateFieldSet(this, _JsLocalStorageFileStream_file, file, "f");
        __classPrivateFieldSet(this, _JsLocalStorageFileStream_base64, new Base64(), "f");
        if (mode == "rw") {
            __classPrivateFieldSet(this, _JsLocalStorageFileStream_canWrite, true, "f");
            __classPrivateFieldSet(this, _JsLocalStorageFileStream_stream, new MemoryStream(), "f");
        }
        else {
            let contents = localStorage.getItem(__classPrivateFieldGet(this, _JsLocalStorageFileStream_file, "f").getAbsolutePath());
            if (contents == null)
                contents = "";
            __classPrivateFieldSet(this, _JsLocalStorageFileStream_stream, new MemoryStream(__classPrivateFieldGet(this, _JsLocalStorageFileStream_base64, "f").decode(contents)), "f");
        }
    }
    /**
     * True if stream can read from file.
     * @return
     */
    async canRead() {
        return !__classPrivateFieldGet(this, _JsLocalStorageFileStream_canWrite, "f");
    }
    /**
     * True if stream can write to file.
     * @return
     */
    async canWrite() {
        return __classPrivateFieldGet(this, _JsLocalStorageFileStream_canWrite, "f");
    }
    /**
     * True if stream can seek.
     * @return
     */
    async canSeek() {
        return true;
    }
    /**
     * Get the length of the stream. This is the same as the backed file.
     * @return
     */
    async length() {
        return await __classPrivateFieldGet(this, _JsLocalStorageFileStream_file, "f").length();
    }
    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException
     */
    async getPosition() {
        return await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").getPosition();
    }
    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    async setPosition(value) {
        await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").setPosition(value);
    }
    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException
     */
    async setLength(value) {
    }
    /**
     * Read data from the file stream into the buffer provided.
     * @param buffer The buffer to write the data.
     * @param offset The offset of the buffer to start writing the data.
     * @param count The maximum number of bytes to read from.
     * @return
     * @throws IOException
     */
    async read(buffer, offset, count) {
        return await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").read(buffer, offset, count);
    }
    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException
     */
    async write(buffer, offset, count) {
        await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").write(buffer, offset, count);
    }
    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException
     */
    async seek(offset, origin) {
        await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").seek(offset, origin);
        return await __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").getPosition();
    }
    /**
     * Flush the buffers to the associated file.
     */
    async flush() {
        let contents = __classPrivateFieldGet(this, _JsLocalStorageFileStream_base64, "f").encode(__classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").toArray());
        let key = __classPrivateFieldGet(this, _JsLocalStorageFileStream_file, "f").getAbsolutePath();
        localStorage.setItem(key, contents);
    }
    /**
     * Close this stream and associated resources.
     * @throws IOException
     */
    async close() {
        this.flush();
        __classPrivateFieldGet(this, _JsLocalStorageFileStream_stream, "f").close();
    }
}
_JsLocalStorageFileStream_file = new WeakMap(), _JsLocalStorageFileStream_stream = new WeakMap(), _JsLocalStorageFileStream_canWrite = new WeakMap(), _JsLocalStorageFileStream_base64 = new WeakMap();
