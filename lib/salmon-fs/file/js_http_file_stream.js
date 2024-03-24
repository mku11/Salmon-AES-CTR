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
import { IOException } from "../../salmon-core/io/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../salmon-core/io/random_access_stream.js";
/**
 * An advanced file stream implementation for remote HTTP files.
 * This class can be used for random file access of remote files.
 */
export class JsHttpFileStream extends RandomAccessStream {
    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    constructor(file, mode) {
        super();
        this.position = 0;
        this.end_position = 0;
        this.buffer = null;
        this.bufferPosition = 0;
        this.stream = null;
        this.reader = null;
        this.closed = false;
        this.file = file;
        if (mode == "rw") {
            throw new Error("Unsupported Operation");
        }
    }
    async getStream() {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.stream == null) {
            let headers = {};
            let end = await this.length() - 1;
            if (end >= this.position + JsHttpFileStream.MAX_LEN_PER_REQUEST) {
                end = this.position + JsHttpFileStream.MAX_LEN_PER_REQUEST - 1;
                headers.range = "bytes=" + this.position + "-" + end;
            }
            else if (this.position > 0) {
                headers.range = "bytes=" + this.position + "-";
            }
            this.stream = (await (fetch(this.file.getPath(), { cache: "no-store", keepalive: true, headers: headers }))).body;
            this.end_position = end;
        }
        if (this.stream == null)
            throw new IOException("Could not retrieve stream");
        return this.stream;
    }
    async getReader() {
        if (this.reader == null) {
            this.reader = (await this.getStream()).getReader();
        }
        return this.reader;
    }
    /**
     * True if stream can read from file.
     * @return
     */
    async canRead() {
        return true;
    }
    /**
     * True if stream can write to file.
     * @return
     */
    async canWrite() {
        return false;
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
        return await this.file.length();
    }
    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException
     */
    async getPosition() {
        return this.position;
    }
    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    async setPosition(value) {
        this.position = value;
        await this.reset();
    }
    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException
     */
    async setLength(value) {
        throw new Error("Unsupported Operation");
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
        let bytesRead = 0;
        if (this.buffer != null && this.bufferPosition < this.buffer.length) {
            for (; this.bufferPosition < this.buffer.length;) {
                buffer[offset + bytesRead++] = this.buffer[this.bufferPosition++];
                if (bytesRead == count)
                    break;
            }
            this.position += bytesRead;
        }
        if (bytesRead < count && this.position == this.end_position - 1 && this.position < await this.file.length()) {
            await this.reset();
        }
        let reader = await this.getReader();
        let res = null;
        while (bytesRead < count) {
            res = await reader.read();
            if (res.value !== undefined) {
                let i = 0;
                let len = Math.min(res.value.length, count - bytesRead);
                for (; i < len; i++) {
                    buffer[offset + bytesRead++] = res.value[i];
                }
                this.position += len;
                if (count == bytesRead) {
                    this.buffer = res.value;
                    this.bufferPosition = i;
                }
            }
            else {
                break;
            }
        }
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
        throw new Error("Unsupported Operation");
    }
    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException
     */
    async seek(offset, origin) {
        let pos = this.position;
        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.file.length() - offset;
        await this.setPosition(pos);
        await this.getStream();
        return this.position;
    }
    /**
     * Flush the buffers to the associated file.
     */
    flush() {
        throw new Error("Unsupported Operation");
    }
    /**
     * Close this stream and associated resources.
     * @throws IOException
     */
    async close() {
        await this.reset();
        this.closed = true;
    }
    async reset() {
        var _a;
        if (this.reader != null) {
            if ((_a = this.stream) === null || _a === void 0 ? void 0 : _a.locked)
                this.reader.releaseLock();
        }
        this.reader = null;
        if (this.stream != null)
            await this.stream.cancel();
        this.stream = null;
        this.buffer = null;
        this.bufferPosition = 0;
    }
}
JsHttpFileStream.MAX_LEN_PER_REQUEST = 8 * 1024 * 1024;
