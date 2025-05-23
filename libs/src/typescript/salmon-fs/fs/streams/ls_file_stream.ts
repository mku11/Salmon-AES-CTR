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

import { MemoryStream } from "../../../salmon-core/streams/memory_stream.js";
import { RandomAccessStream, SeekOrigin } from "../../../salmon-core/streams/random_access_stream.js";
import { IFile } from "../file/ifile.js";
import { Base64Utils } from '../../../salmon-core/salmon/encode/base64_utils.js';

/**
 * An advanced file stream implementation for localStorage files.
 * This class can be used to read and write small file in localStorage.
 */
export class LocalStorageFileStream extends RandomAccessStream {

    /**
     * The virtual local storage file associated with this stream.
     */
    readonly #file: IFile;
    #stream: MemoryStream;
    #canWrite: boolean = false;

    /**
     * Construct a file stream from an LocalStorageFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param {IFile} file The LocalStorageFile that will be used to get the read/write stream
     * @param {string} mode The mode "r" for read "rw" for write
     */
    public constructor(file: IFile, mode: string) {
        super();
        this.#file = file;
        if (mode == "rw") {
            this.#canWrite = true;
            this.#stream = new MemoryStream();
        } else {
            let contents: string | null = localStorage.getItem(this.#file.getDisplayPath());
            if(contents == null)
                contents = "";
            this.#stream = new MemoryStream(Base64Utils.getBase64().decode(contents));
        }
    }


    /**
     * True if stream can read from file.
     * @returns {Promise<boolean>} True if it can read.
     */
    public override async canRead(): Promise<boolean> {
        return !this.#canWrite;
    }

    /**
     * True if stream can write to file.
     * @returns {Promise<boolean>} True if it can write.
     */
    public override async canWrite(): Promise<boolean> {
        return this.#canWrite;
    }

    /**
     * True if stream can seek.
     * @returns {Promise<boolean>} True if it can seek.
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
        return await this.#stream.getPosition();
    }

    /**
     * Set the current position of the stream.
     * @param {number} value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setPosition(value: number): Promise<void> {
        await this.#stream.setPosition(value);
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param {number} value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setLength(value: number): Promise<void> {
        // nop
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
        return await this.#stream.read(buffer, offset, count);
    }

    /**
     * Write the data from the buffer provided into the stream.
     * @param {Uint8Array} buffer The buffer to read the data from.
     * @param {number} offset The offset of the buffer to start reading the data.
     * @param {number} count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        await this.#stream.write(buffer, offset, count);
    }

    /**
     * Seek to the offset provided.
     * @param {number} offset The position to seek to.
     * @param {SeekOrigin} origin The type of origin {@link SeekOrigin}
     * @returns {Promise<number>} The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async seek(offset: number, origin: SeekOrigin): Promise<number> {
        await this.#stream.seek(offset, origin);
        return await this.#stream.getPosition(); 
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override async flush(): Promise<void> {
        let contents: string = Base64Utils.getBase64().encode(this.#stream.toArray());
        let key: string = this.#file.getDisplayPath();
        localStorage.setItem(key, contents);
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async close(): Promise<void> {
        await this.flush();
        await this.#stream.close();
    }
}
