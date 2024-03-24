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
var _JsNodeFileStream_instances, _JsNodeFileStream_file, _JsNodeFileStream__position, _JsNodeFileStream__buffer, _JsNodeFileStream__bufferPosition, _JsNodeFileStream_fd, _JsNodeFileStream__closed, _JsNodeFileStream_canWrite, _JsNodeFileStream_getFd, _JsNodeFileStream_reset;
import { IOException } from "../../salmon-core/io/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../salmon-core/io/random_access_stream.js";
import { truncate } from 'node:fs/promises';
import { openSync } from "node:fs";
import fs from "fs";
/**
 * An advanced file stream implementation for local files.
 * This class can be used for random file access of local files using node js.
 */
export class JsNodeFileStream extends RandomAccessStream {
    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    constructor(file, mode) {
        super();
        _JsNodeFileStream_instances.add(this);
        /**
         * The java file associated with this stream.
         */
        _JsNodeFileStream_file.set(this, void 0);
        _JsNodeFileStream__position.set(this, 0);
        _JsNodeFileStream__buffer.set(this, null);
        _JsNodeFileStream__bufferPosition.set(this, 0);
        _JsNodeFileStream_fd.set(this, 0);
        _JsNodeFileStream__closed.set(this, false);
        _JsNodeFileStream_canWrite.set(this, false);
        __classPrivateFieldSet(this, _JsNodeFileStream_file, file, "f");
        if (mode == "rw") {
            __classPrivateFieldSet(this, _JsNodeFileStream_canWrite, true, "f");
        }
    }
    /**
     * True if stream can read from file.
     * @return
     */
    async canRead() {
        return !__classPrivateFieldGet(this, _JsNodeFileStream_canWrite, "f");
    }
    /**
     * True if stream can write to file.
     * @return
     */
    async canWrite() {
        return __classPrivateFieldGet(this, _JsNodeFileStream_canWrite, "f");
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
        return await __classPrivateFieldGet(this, _JsNodeFileStream_file, "f").length();
    }
    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException
     */
    async getPosition() {
        return __classPrivateFieldGet(this, _JsNodeFileStream__position, "f");
    }
    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    async setPosition(value) {
        __classPrivateFieldSet(this, _JsNodeFileStream__position, value, "f");
        __classPrivateFieldGet(this, _JsNodeFileStream_instances, "m", _JsNodeFileStream_reset).call(this);
    }
    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException
     */
    async setLength(value) {
        await truncate(__classPrivateFieldGet(this, _JsNodeFileStream_file, "f").getAbsolutePath(), value);
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
        let fd = await __classPrivateFieldGet(this, _JsNodeFileStream_instances, "m", _JsNodeFileStream_getFd).call(this);
        let bytesRead = fs.readSync(fd, buffer, offset, count, __classPrivateFieldGet(this, _JsNodeFileStream__position, "f"));
        __classPrivateFieldSet(this, _JsNodeFileStream__position, __classPrivateFieldGet(this, _JsNodeFileStream__position, "f") + bytesRead, "f");
        return bytesRead;
    }
    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException
     */
    async write(buffer, offset, count) {
        let fd = await __classPrivateFieldGet(this, _JsNodeFileStream_instances, "m", _JsNodeFileStream_getFd).call(this);
        let bytesWritten = fs.writeSync(fd, buffer, offset, count, __classPrivateFieldGet(this, _JsNodeFileStream__position, "f"));
        __classPrivateFieldSet(this, _JsNodeFileStream__position, __classPrivateFieldGet(this, _JsNodeFileStream__position, "f") + bytesWritten, "f");
    }
    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException
     */
    async seek(offset, origin) {
        let pos = __classPrivateFieldGet(this, _JsNodeFileStream__position, "f");
        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await __classPrivateFieldGet(this, _JsNodeFileStream_file, "f").length() - offset;
        await this.setPosition(pos);
        return __classPrivateFieldGet(this, _JsNodeFileStream__position, "f");
    }
    /**
     * Flush the buffers to the associated file.
     */
    async flush() {
        if (await this.canWrite()) {
            if (__classPrivateFieldGet(this, _JsNodeFileStream_fd, "f") != null) {
                // nop
            }
        }
    }
    /**
     * Close this stream and associated resources.
     * @throws IOException
     */
    async close() {
        if (__classPrivateFieldGet(this, _JsNodeFileStream_fd, "f"))
            fs.close(__classPrivateFieldGet(this, _JsNodeFileStream_fd, "f"));
        __classPrivateFieldGet(this, _JsNodeFileStream_instances, "m", _JsNodeFileStream_reset).call(this);
        __classPrivateFieldSet(this, _JsNodeFileStream__closed, true, "f");
    }
}
_JsNodeFileStream_file = new WeakMap(), _JsNodeFileStream__position = new WeakMap(), _JsNodeFileStream__buffer = new WeakMap(), _JsNodeFileStream__bufferPosition = new WeakMap(), _JsNodeFileStream_fd = new WeakMap(), _JsNodeFileStream__closed = new WeakMap(), _JsNodeFileStream_canWrite = new WeakMap(), _JsNodeFileStream_instances = new WeakSet(), _JsNodeFileStream_getFd = async function _JsNodeFileStream_getFd() {
    if (__classPrivateFieldGet(this, _JsNodeFileStream__closed, "f"))
        throw new IOException("Stream is closed");
    if (__classPrivateFieldGet(this, _JsNodeFileStream_fd, "f") == 0) {
        if (await this.canRead()) {
            __classPrivateFieldSet(this, _JsNodeFileStream_fd, openSync(__classPrivateFieldGet(this, _JsNodeFileStream_file, "f").getPath(), "r"), "f");
        }
        else if (await this.canWrite()) {
            if (!await __classPrivateFieldGet(this, _JsNodeFileStream_file, "f").exists()) {
                let fdt = openSync(__classPrivateFieldGet(this, _JsNodeFileStream_file, "f").getPath(), 'a');
                fs.closeSync(fdt);
            }
            __classPrivateFieldSet(this, _JsNodeFileStream_fd, openSync(__classPrivateFieldGet(this, _JsNodeFileStream_file, "f").getPath(), "r+"), "f");
        }
    }
    if (__classPrivateFieldGet(this, _JsNodeFileStream_fd, "f") == null)
        throw new IOException("Could not retrieve file descriptor");
    return __classPrivateFieldGet(this, _JsNodeFileStream_fd, "f");
}, _JsNodeFileStream_reset = function _JsNodeFileStream_reset() {
    __classPrivateFieldSet(this, _JsNodeFileStream__buffer, null, "f");
    __classPrivateFieldSet(this, _JsNodeFileStream__bufferPosition, 0, "f");
};
