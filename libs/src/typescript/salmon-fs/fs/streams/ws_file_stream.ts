/*
MIT License

Copyright (c) 2025 Max Kas

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
import { Base64 } from '../../../salmon-core/convert/base64.js';
import { MemoryStream } from '../../../salmon-core/streams/memory_stream.js';
import { RandomAccessStream, SeekOrigin } from "../../../salmon-core/streams/random_access_stream.js";
import { WSFile } from "../file/ws_file.js";

/**
 * File stream implementation for Web Service files.
 * This class can be used for random file access of remote files.
 */
export class WSFileStream extends RandomAccessStream {
    static readonly #PATH: string = "path";
    static readonly #POSITION: string = "position";
    static readonly #LENGTH: string = "length";
    static MAX_LEN_PER_REQUEST = 8 * 1024 * 1024;
    /**
     * The web service file associated with this stream.
     */
    readonly file: WSFile;

    #canWrite: boolean = false;
    position: number = 0;
    end_position: number = 0;

    buffer: Uint8Array | null = null;
    bufferPosition: number = 0;

    readStream: ReadableStream<Uint8Array> | null = null;
    writeStream: WritableStream<Uint8Array> | null = null;
    reader: ReadableStreamDefaultReader<Uint8Array> | null = null;
    writer: WritableStreamDefaultWriter<Uint8Array> | null = null;
    closed: boolean = false;

    /**
     * Construct a file stream from an WSFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param {WSFile} file The WSFile that will be used to get the read/write stream
     * @param {string} mode The mode "r" for read "rw" for write
     */
    public constructor(file: WSFile, mode: string) {
        super();
        this.file = file;
        this.#canWrite = mode == "rw";
    }

    async getInputStream(): Promise<ReadableStream<Uint8Array>> {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.readStream == null) {
            let headers: Headers = new Headers();
            this.#setDefaultHeaders(headers);
            this.#setServiceAuth(headers);

            let end = await this.getLength() - 1;
            if (end >= this.position + WSFileStream.MAX_LEN_PER_REQUEST) {
                end = this.position + WSFileStream.MAX_LEN_PER_REQUEST - 1;
            }
            let httpResponse: Response | null = null;
            httpResponse = await fetch(this.file.getServicePath() + "/api/get" 
                + "?" + WSFileStream.#PATH + "=" + encodeURIComponent(this.file.getPath())
                + "&" + WSFileStream.#POSITION + "=" + this.position.toString(), 
                { method: 'GET', headers: headers });
            await this.#checkStatus(httpResponse, this.position > 0 ? 206 : 200);
			this.readStream = httpResponse.body;
            this.end_position = end;
        }
        if (this.readStream == null)
            throw new IOException("Could not retrieve stream");
        return this.readStream;
    }

    async getReader(): Promise<ReadableStreamDefaultReader> {
        if (this.reader == null) {
            this.reader = (await this.getInputStream()).getReader();
        }
        return this.reader;
    }
    
    async getOutputStream(): Promise<WritableStream<Uint8Array>> {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.writeStream == null) {
            let startPosition: number = await this.getPosition();
            const boundary = "*******";
            let header = "--"+boundary+"\r\n"; 
            header += "Content-Disposition: form-data; name=\"file\"; filename=\""+ this.file.getName() + "\"\r\n";
            header += "\r\n";
			let headerData = new TextEncoder().encode(header);
            
            let footer = "\r\n--"+boundary+"--";
            let footerData = new TextEncoder().encode(footer);

            // the new js stream API with HTTP2 doesn't seem very reliable 
			// especially when we use a ReadableStream with push controller
			// so we manually chunk it to blobs	
            let body: MemoryStream = new MemoryStream();
			await body.write(headerData, 0, headerData.length);
			let sstream = this;
			async function send() {
				await body.write(footerData, 0, footerData.length);
				let headers: Headers = new Headers();
				sstream.#setDefaultHeaders(headers);
				headers.append("Content-Type", "multipart/form-data;boundary="+boundary);
				sstream.#setServiceAuth(headers);
				let httpResponse: Response | null = null;
				let data: Uint8Array = body.toArray();
				
				httpResponse = await fetch(sstream.file.getServicePath() + "/api/upload" 
					+ "?" + WSFileStream.#PATH + "=" + encodeURIComponent(sstream.file.getPath())
					+ "&" + WSFileStream.#POSITION + "=" + startPosition.toString(), 
					{ method: 'POST', body: new Blob([data]), headers: headers } as RequestInit);
				await sstream.#checkStatus(httpResponse, startPosition > 0 ? 206 : 200);
				body = new MemoryStream();
				await body.write(headerData, 0, headerData.length);
				startPosition+=data.length - headerData.length - footerData.length;
			}
            this.writeStream = new WritableStream({
                async write(data) {
					await body.write(data, 0, data.length);
					if(await body.getLength() - headerData.length >= WSFileStream.MAX_LEN_PER_REQUEST) {
						await send();
					}
                },
                abort() {
                    
                },
                async close(){
					if(await body.getLength() - headerData.length > 0) {
						await send();
					}
                }
            });
        }
        if (this.writeStream == null)
            throw new IOException("Could not retrieve stream");
        return this.writeStream;
    }
    
    async #getWriter(): Promise<WritableStreamDefaultWriter> {
        if (this.writer == null) {
            this.writer = (await this.getOutputStream()).getWriter();
        }
        return this.writer;
    }

    /**
     * True if stream can read from file.
     * @returns {Promise<boolean>} True if it can read
     */
    public override async canRead(): Promise<boolean> {
        return !this.#canWrite;;
    }

    /**
     * True if stream can write to file.
     * @returns {Promise<boolean>} True if it can write
     */
    public override async canWrite(): Promise<boolean> {
        return this.#canWrite;
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
        return await this.file.getLength();
    }

    /**
     * Get the current position of the stream.
     * @returns {Promise<number>} The position
     * @throws IOException Thrown if there is an IO error.
     */
    public override async getPosition(): Promise<number> {
        return this.position;
    }

    /**
     * Set the current position of the stream.
     * @param {number} value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setPosition(value: number): Promise<void> {
        if(this.position != value)
			await this.reset();
        this.position = value;
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param {number} value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setLength(value: number): Promise<void> {
        let headers: Headers = new Headers();
        this.#setDefaultHeaders(headers);
        this.#setServiceAuth(headers);
        let params: URLSearchParams = new URLSearchParams();
        params.append(WSFileStream.#PATH, this.file.getPath());
        params.append(WSFileStream.#LENGTH, value.toString());
        let httpResponse: Response | null = null;
        httpResponse = await fetch(this.file.getServicePath() + "/api/setLength", 
		{ method: 'PUT', body: params, headers: headers });
        await this.#checkStatus(httpResponse, 200);
        await this.reset();
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
        if (this.buffer  && this.bufferPosition < this.buffer.length) {
            for (; this.bufferPosition < this.buffer.length;) {
                buffer[offset + bytesRead++] = this.buffer[this.bufferPosition++];
                if (bytesRead == count)
                    break;
            }
            this.position += bytesRead;
        }
        if(bytesRead < count && this.position == this.end_position + 1 && this.position < await this.file.getLength()) {
            await this.reset();
        }
        let reader: ReadableStreamDefaultReader = await this.getReader();
        let res: ReadableStreamReadResult<any> | null = null;
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
        let writer: WritableStreamDefaultWriter = await this.#getWriter();
        await writer.write(buffer.slice(offset, offset + count));
        this.position += Math.min(buffer.length, count);
    }

    /**
     * Seek to the offset provided.
     * @param {number} offset The position to seek to.
     * @param {SeekOrigin} origin The type of origin {@link SeekOrigin}
     * @returns {Promise<number>} The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        let pos: number = this.position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.file.getLength() - offset;

        await this.setPosition(pos);
        return this.position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override async flush(): Promise<void> {
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async close(): Promise<void> {
        await this.reset();
        this.closed = true;
    }

    /**
     * Reset the stream
     */
    async reset(): Promise<void> {
        if (this.reader) {
            if(this.readStream?.locked)
                this.reader.releaseLock();
        }
        this.reader = null;
        if(this.readStream)
            await this.readStream.cancel();
        this.readStream = null;

        if (this.writer) {
            if(this.writeStream?.locked)
                this.writer.releaseLock();
        }
        this.writer = null;
        if(this.writeStream)
            await this.writeStream.close();
        this.writeStream = null;
        
        this.buffer = null;
        this.bufferPosition = 0;
		this.file.reset();
    }
    
    #setServiceAuth(headers: Headers) {
        if(!this.file.getCredentials())
            return;
        headers.append('Authorization', 'Basic ' + new Base64().encode(
            new TextEncoder().encode(this.file.getCredentials()?.getServiceUser() + ":" + this.file.getCredentials()?.getServicePassword())));
    }
    
    async #checkStatus(httpResponse: Response, status: number) {
        if (httpResponse.status != status)
            throw new IOException(httpResponse.status
                    + " " + httpResponse.statusText);
    }

    #setDefaultHeaders(headers: Headers) {
        headers.append("Cache", "no-store");
    }
}