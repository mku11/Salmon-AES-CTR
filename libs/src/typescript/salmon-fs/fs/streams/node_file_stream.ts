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
import { truncate } from 'node:fs/promises';
import { openSync } from "node:fs";
import fs from "fs";

/**
 * An advanced file stream implementation for local files.
 * This class can be used for random file access of local files using node js.
 */
export class NodeFileStream extends RandomAccessStream {

    /**
     * The java file associated with this stream.
     */
    readonly #file: IFile;

    #_position: number = 0;

    #_buffer: Uint8Array | null = null;
    #_bufferPosition: number = 0;

    #fd: number = 0;
    #_closed: boolean = false;

    #canWrite: boolean = false;

    /**
     * Construct a file stream from an NodeFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The NodeFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public constructor(file: IFile, mode: string) {
        super();
        this.#file = file;
        if (mode == "rw") {
            this.#canWrite = true;
        }
    }

    async #getFd(): Promise<number> {
        if (this.#_closed)
            throw new IOException("Stream is closed");
        if (this.#fd == 0) {
            if (await this.canRead()) {
                this.#fd = openSync(this.#file.getPath(), "r");
            } else if (await this.canWrite()) {
                if(!await this.#file.exists()) {
                    let fdt: number = openSync(this.#file.getPath(), 'a');
                    fs.closeSync(fdt);
                }
                this.#fd = openSync(this.#file.getPath(), "r+");
            }
        }
        if (this.#fd == null)
            throw new IOException("Could not retrieve file descriptor");
        return this.#fd;
    }

    /**
     * True if stream can read from file.
     * @return
     */
    public override async canRead(): Promise<boolean> {
        return !this.#canWrite;
    }

    /**
     * True if stream can write to file.
     * @return
     */
    public override async canWrite(): Promise<boolean> {
        return this.#canWrite;
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
    public override async getLength(): Promise<number> {
        return await this.#file.getLength();
    }

    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public override async getPosition(): Promise<number> {
        return this.#_position;
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setPosition(value: number): Promise<void> {
		if(this.#_position != value)
			this.#reset();
        this.#_position = value;
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setLength(value: number): Promise<void> {
        await truncate(this.#file.getDisplayPath(), value);
    }

    /**
     * Read data from the file stream into the buffer provided.
     * @param buffer The buffer to write the data.
     * @param offset The offset of the buffer to start writing the data.
     * @param count The maximum number of bytes to read from.
     * @return
     * @throws IOException Thrown if there is an IO error.
     */
    public override async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        let fd: number = await this.#getFd();
        let bytesRead: number = fs.readSync(fd, buffer, offset, count, this.#_position);
        this.#_position += bytesRead;
        return bytesRead;
    }

    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        let fd: number = await this.#getFd();
        let bytesWritten: number = fs.writeSync(fd, buffer, offset, count, this.#_position);
        this.#_position += bytesWritten;
    }

    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        let pos: number = this.#_position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.#file.getLength() - offset;

        await this.setPosition(pos);
        return this.#_position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override async flush(): Promise<void> {
        if (await this.canWrite()) {
            if (this.#fd != null) {
                // nop
            }
        }
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async close(): Promise<void> {
        if (this.#fd)
            fs.close(this.#fd);
        this.#reset();
        this.#_closed = true;
    }

    #reset(): void {
        this.#_buffer = null;
        this.#_bufferPosition = 0;
    }
}
