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

import { IOException } from "../../../salmon-core/streams/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../../salmon-core/streams/random_access_stream.js";
import { IFile } from "../file/ifile.js";
import { Base64Utils } from '../../../salmon-core/salmon/encode/base64_utils.js';
import { HttpSyncClient } from '../file/http_sync_client.js';
import { Credentials } from '../file/credentials.js';

/**
 * An advanced file stream implementation for remote HTTP files.
 * This class can be used for random file access of remote files.
 */
export class HttpFileStream extends RandomAccessStream {
    static MAX_LEN_PER_REQUEST = 8 * 1024 * 1024;
    /**
     * The HTTP file associated with this stream.
     */
    #file: IFile;

    #position: number = 0;
    #end_position: number = 0;

	// fetch will response will download the whole contents internally
	// so we use our own "chunked" implementation with our own buffer
    #buffer: Uint8Array | null = null;
    #bufferPosition: number = 0;

    #stream: ReadableStream<Uint8Array> | null = null;
    #reader: ReadableStreamDefaultReader<Uint8Array> | null = null;
    #closed: boolean = false;

    /**
     * Construct a file stream from an HttpFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param {IFile} file The HttpFile that will be used to get the read/write stream
     * @param {string} mode The mode "r" for read "rw" for write
     */
    public constructor(file: IFile, mode: string) {
        super();
        this.#file = file;
        if (mode == "rw") {
            throw new Error("Unsupported Operation, readonly filesystem");
        }
    }

    async #getStream(): Promise<ReadableStream<Uint8Array>> {
        if (this.#closed)
            throw new IOException("Stream is closed");
        if (this.#stream == null) {
            let headers = new Headers();
			this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);
			let end = await this.getLength() - 1;
            let requestLength = HttpFileStream.MAX_LEN_PER_REQUEST;
            if (end == -1 || end >= this.#position + requestLength) {
                end = this.#position + requestLength - 1;
            }
            // fetch will read the whole content without streaming
            // so we want to specify the end if it's a range request
            // or it's a full request but we don't know the end
            if(this.#position > 0 || end == HttpFileStream.MAX_LEN_PER_REQUEST - 1)
			    headers.append("Range", "bytes=" + this.#position + "-" + end);
            let httpResponse = await HttpSyncClient.getResponse(this.#file.getPath(), { cache: "no-store", headers: headers });

            await this.#checkStatus(httpResponse, new Set([200, 206]));
            this.#stream = httpResponse.body;
            this.#end_position = end;
        }
        if (this.#stream == null)
            throw new IOException("Could not retrieve stream");
        return this.#stream;
    }

    async #getReader(): Promise<ReadableStreamDefaultReader> {
        if (this.#reader == null) {
            this.#reader = (await this.#getStream()).getReader();
        }
        return this.#reader;
    }

    /**
     * True if stream can read from file.
     * @returns {Promise<boolean>} True if it can read.
     */
    public override async canRead(): Promise<boolean> {
        return true;
    }

    /**
     * True if stream can write to file.
     * @returns {Promise<boolean>} True if it can write
     */
    public override async canWrite(): Promise<boolean> {
        return false;
    }

    /**
     * True if stream can seek.
     * @returns {Promise<boolean>} True if it can seek
     */
    public override async canSeek(): Promise<boolean> {
        return true;
    }

    /**
     * Get the length of the stream. This is the same as the backed file.
     * @returns {Promise<number>} The length
     */
    public override async getLength(): Promise<number> {
        return await this.#file.getLength();
    }

    /**
     * Get the current position of the stream.
     * @returns {Promise<number>} The position
     * @throws IOException Thrown if there is an IO error.
     */
    public override async getPosition(): Promise<number> {
        return this.#position;
    }

    /**
     * Set the current position of the stream.
     * @param {number} value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setPosition(value: number): Promise<void> {
		if(this.#position != value)
			await this.reset();
        this.#position = value;
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param {number} value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setLength(value: number): Promise<void> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Read data from the file stream into the buffer provided.
     * @param {Uint8Array} buffer The buffer to write the data.
     * @param {number} offset The offset of the buffer to start writing the data.
     * @param {number} count The maximum number of bytes to read from.
     * @returns {Promise<number>} The number of bytes read
     * @throws IOException Thrown if there is an IO error.
     */
    public override async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        let bytesRead: number = 0;
        if (this.#buffer  && this.#bufferPosition < this.#buffer.length) {
            for (; this.#bufferPosition < this.#buffer.length;) {
                buffer[offset + bytesRead++] = this.#buffer[this.#bufferPosition++];
                if (bytesRead == count)
                    break;
            }
            this.#position += bytesRead;
        }
        if(bytesRead < count && this.#position == this.#end_position + 1 && this.#position < await this.#file.getLength()) {
            await this.reset();
        }
        let reader: ReadableStreamDefaultReader = await this.#getReader();
        let res: ReadableStreamReadResult<any> | null = null;
        while (bytesRead < count) {
            res = await reader.read();
            if (res.value !== undefined) {
                let i = 0;
                let len = Math.min(res.value.length, count - bytesRead);
                for (; i < len; i++) {
                    buffer[offset + bytesRead++] = res.value[i];
                }
                this.#position += len;
                if (count == bytesRead) {
                    this.#buffer = res.value;
                    this.#bufferPosition = i;
                }
            } else {
                break;
            }
        }
        return bytesRead;
    }

    /**
     * Write the data from the buffer provided into the stream.
     * @param {Uint8Array} buffer The buffer to read the data from.
     * @param {number} offset The offset of the buffer to start reading the data.
     * @param {number} count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Seek to the offset provided.
     * @param {number} offset The position to seek to.
     * @param {SeekOrigin} origin The type of origin {@link SeekOrigin}
     * @returns {Promise<number>} The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        let pos: number = this.#position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.#file.getLength() - offset;

        await this.setPosition(pos);
        return this.#position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override flush(): Promise<void> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async close(): Promise<void> {
        await this.reset();
        this.#closed = true;
    }

	/**
     * Reset the stream.
     */
    public async reset(): Promise<void> {
        if (this.#reader) {
            if(this.#stream?.locked)
                this.#reader.releaseLock();
        }
        this.#reader = null;
        if(this.#stream)
            await this.#stream.cancel();
        this.#stream = null;
        this.#buffer = null;
        this.#bufferPosition = 0;
    }
	
    async #checkStatus(httpResponse: Response, status: Set<number>) {
        if (!status.has(httpResponse.status)) {
            throw new IOException(httpResponse.status
                    + " " + httpResponse.statusText);
            }
    }

    #setServiceAuth(headers: Headers) {
        let credentials: Credentials | null = this.#file.getCredentials();
        if(!credentials)
            return;
        headers.append('Authorization', 'Basic ' + Base64Utils.getBase64().encode(
            new TextEncoder().encode(credentials.getServiceUser() + ":" + credentials.getServicePassword())));
    }
    
    #setDefaultHeaders(headers: Headers) {
        headers.append("Cache", "no-store");
		headers.append("Connection", "close");
    }
}