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
import { IRealFile } from "./ireal_file.js";

/**
 * An advanced file stream implementation for remote HTTP files.
 * This class can be used for random file access of remote files.
 */
export class JsHttpFileStream extends RandomAccessStream {

    /**
     * The java file associated with this stream.
     */
    readonly #file: IRealFile;

    #_position: number = 0;

    #_buffer: Uint8Array | null = null;
    #_bufferPosition: number = 0;

    #_stream: ReadableStream<Uint8Array> | null = null;
    #_reader: ReadableStreamDefaultReader<Uint8Array> | null = null;
    #_closed: boolean = false;

    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public constructor(file: IRealFile, mode: string) {
        super();
        this.#file = file;
        if (mode == "rw") {
            throw new Error("Unsupported Operation");
        }
    }

    async #getStream(): Promise<ReadableStream<Uint8Array>> {
        if(this.#_closed)
            throw new IOException("Stream is closed");
        if (this.#_stream == null) {
            let headers: any = null;
            if (this.#_position > 0) {
                let range: string = "bytes=" + this.#_position + "-";
                headers = { "Range": range };
                this.#_stream = (await (fetch(this.#file.getPath(), { headers: headers }))).body;
            } else {
                this.#_stream = (await (fetch(this.#file.getPath()))).body;
            }
        }
        if (this.#_stream == null)
            throw new IOException("Could not retrieve stream");
        return this.#_stream;
    }

    async #getReader(): Promise<ReadableStreamDefaultReader> {
        if (this.#_reader == null) {
            this.#_reader = (await this.#getStream()).getReader();
        }
        return this.#_reader;
    }

    /**
     * True if stream can read from file.
     * @return
     */
    public override async canRead(): Promise<boolean> {
        return true;
    }

    /**
     * True if stream can write to file.
     * @return
     */
    public override async canWrite(): Promise<boolean> {
        return false;
    }

    /**
     * True if stream can seek.
     * @return
     */
    public override async canSeek(): Promise<boolean> {
        return true;
    }

    /**
     * Get the length of the stream. This is the same as the backed file.
     * @return
     */
    public override async length(): Promise<number> {
        return await this.#file.length();
    }

    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException
     */
    public override async getPosition(): Promise<number> {
        return this.#_position;
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    public override async setPosition(value: number): Promise<void> {
        this.#_position = value;
        this.#reset();
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException
     */
    public override async setLength(value: number): Promise<void> {
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
    public override async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        let bytesRead: number = 0;
        if (this.#_buffer != null && this.#_bufferPosition < this.#_buffer.length) {
            for (; this.#_bufferPosition < this.#_buffer.length;) {
                buffer[offset + bytesRead++] = this.#_buffer[this.#_bufferPosition++];
                if (bytesRead == count)
                    break;
            }
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
                if (count == bytesRead) {
                    this.#_buffer = res.value;
                    this.#_bufferPosition = i;
                }
            } else {
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
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        let pos: number = this.#_position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.#file.length() - offset;

        await this.setPosition(pos);
        await this.#getStream();
        return this.#_position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override flush(): Promise<void> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException
     */
    public override async close(): Promise<void> {
        if (this.#_reader != null)
            await this.#_reader.cancel();
        this.#reset();
        this.#_closed = true;
    }

    #reset(): void {
        this.#_reader = null;
        this.#_stream = null;
        this.#_buffer = null;
        this.#_bufferPosition = 0;
    }
}
