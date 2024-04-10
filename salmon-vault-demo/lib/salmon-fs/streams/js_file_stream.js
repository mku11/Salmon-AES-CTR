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
var _JsFileStream_instances, _JsFileStream_file, _JsFileStream_fileBlob, _JsFileStream_writablefileStream, _JsFileStream__position, _JsFileStream_canWrite, _JsFileStream_getBlob, _JsFileStream_getStream;
import { RandomAccessStream, SeekOrigin } from "../../salmon-core/streams/random_access_stream.js";
// File operations on the local file system may be slow due to
// web browser specificallly Chrome malware scans
// see: https://issues.chromium.org/issues/40743502
/**
 * An advanced file stream implementation for local files.
 * This class can be used for random file access of local files using the browser.
 */
export class JsFileStream extends RandomAccessStream {
    /**
     * Construct a file stream from an JsFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JsFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    constructor(file, mode) {
        super();
        _JsFileStream_instances.add(this);
        /**
         * The java file associated with this stream.
         */
        _JsFileStream_file.set(this, void 0);
        _JsFileStream_fileBlob.set(this, null);
        _JsFileStream_writablefileStream.set(this, null);
        _JsFileStream__position.set(this, 0);
        _JsFileStream_canWrite.set(this, false);
        __classPrivateFieldSet(this, _JsFileStream_file, file, "f");
        if (mode == "rw") {
            __classPrivateFieldSet(this, _JsFileStream_canWrite, true, "f");
        }
    }
    /**
     * True if stream can read from file.
     * @return
     */
    async canRead() {
        return !__classPrivateFieldGet(this, _JsFileStream_canWrite, "f");
    }
    /**
     * True if stream can write to file.
     * @return
     */
    async canWrite() {
        return __classPrivateFieldGet(this, _JsFileStream_canWrite, "f");
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
        return await __classPrivateFieldGet(this, _JsFileStream_file, "f").length();
    }
    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    async getPosition() {
        return __classPrivateFieldGet(this, _JsFileStream__position, "f");
    }
    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    async setPosition(value) {
        __classPrivateFieldSet(this, _JsFileStream__position, value, "f");
        if (await this.canWrite()) {
            let stream = (await __classPrivateFieldGet(this, _JsFileStream_instances, "m", _JsFileStream_getStream).call(this));
            try {
                if (await __classPrivateFieldGet(this, _JsFileStream_file, "f").length() < value)
                    await stream.truncate(value);
                else
                    await stream.seek(__classPrivateFieldGet(this, _JsFileStream__position, "f"));
            }
            catch (ex) {
                console.error(ex);
                throw ex;
            }
        }
    }
    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    async setLength(value) {
        if (await this.canWrite()) {
            let stream = await __classPrivateFieldGet(this, _JsFileStream_instances, "m", _JsFileStream_getStream).call(this);
            await stream.truncate(0);
        }
        else {
            throw new Error("Stream is not writable");
        }
    }
    /**
     * Read data from the file stream into the buffer provided.
     * @param buffer The buffer to write the data.
     * @param offset The offset of the buffer to start writing the data.
     * @param count The maximum number of bytes to read from.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    async read(buffer, offset, count) {
        let len = Math.min(count, buffer.length - offset);
        let blob = await __classPrivateFieldGet(this, _JsFileStream_instances, "m", _JsFileStream_getBlob).call(this);
        len = Math.min(len, blob.size - __classPrivateFieldGet(this, _JsFileStream__position, "f"));
        let arr = blob.slice(__classPrivateFieldGet(this, _JsFileStream__position, "f"), __classPrivateFieldGet(this, _JsFileStream__position, "f") + len);
        let buff = new Uint8Array(await arr.arrayBuffer());
        for (let i = 0; i < buff.length; i++) {
            buffer[offset + i] = buff[i];
        }
        await this.setPosition(__classPrivateFieldGet(this, _JsFileStream__position, "f") + buff.length);
        return buff.length;
    }
    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    async write(buffer, offset, count) {
        let stream = await __classPrivateFieldGet(this, _JsFileStream_instances, "m", _JsFileStream_getStream).call(this);
        await stream.write(buffer.slice(offset, offset + count));
        await this.setPosition(__classPrivateFieldGet(this, _JsFileStream__position, "f") + count);
    }
    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    async seek(offset, origin) {
        let pos = __classPrivateFieldGet(this, _JsFileStream__position, "f");
        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await __classPrivateFieldGet(this, _JsFileStream_file, "f").length() - offset;
        await this.setPosition(pos);
        return __classPrivateFieldGet(this, _JsFileStream__position, "f");
    }
    /**
     * Flush the buffers to the associated file.
     */
    async flush() {
    }
    /**
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    async close() {
        if (__classPrivateFieldGet(this, _JsFileStream_writablefileStream, "f") != null)
            await __classPrivateFieldGet(this, _JsFileStream_writablefileStream, "f").close();
    }
}
_JsFileStream_file = new WeakMap(), _JsFileStream_fileBlob = new WeakMap(), _JsFileStream_writablefileStream = new WeakMap(), _JsFileStream__position = new WeakMap(), _JsFileStream_canWrite = new WeakMap(), _JsFileStream_instances = new WeakSet(), _JsFileStream_getBlob = async function _JsFileStream_getBlob() {
    if (__classPrivateFieldGet(this, _JsFileStream_fileBlob, "f") == null) {
        let fileHandle = await __classPrivateFieldGet(this, _JsFileStream_file, "f").getPath();
        __classPrivateFieldSet(this, _JsFileStream_fileBlob, await fileHandle.getFile(), "f");
    }
    return __classPrivateFieldGet(this, _JsFileStream_fileBlob, "f");
}, _JsFileStream_getStream = async function _JsFileStream_getStream() {
    if (__classPrivateFieldGet(this, _JsFileStream_writablefileStream, "f") == null) {
        let fileHandle = await __classPrivateFieldGet(this, _JsFileStream_file, "f").getPath();
        let exists = await __classPrivateFieldGet(this, _JsFileStream_file, "f").exists();
        if (exists) {
            __classPrivateFieldSet(this, _JsFileStream_writablefileStream, await fileHandle.createWritable({ keepExistingData: true }), "f");
        }
        else {
            let parent = await __classPrivateFieldGet(this, _JsFileStream_file, "f").getParent();
            if (parent == null)
                throw new Error("Could not get parent");
            await parent.createFile(__classPrivateFieldGet(this, _JsFileStream_file, "f").getBaseName());
            __classPrivateFieldSet(this, _JsFileStream_writablefileStream, await fileHandle.createWritable(), "f");
        }
    }
    return __classPrivateFieldGet(this, _JsFileStream_writablefileStream, "f");
};
