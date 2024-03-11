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

import { RandomAccessStream, SeekOrigin } from "../../salmon-core/io/random_access_stream.js";
import { IRealFile } from "./ireal_file.js";

// File operations on the local file system may be slow due to
// web browser specificallly Chrome malware scans
// see: https://issues.chromium.org/issues/40743502

/**
 * An advanced file stream implementation for local files.
 * This class can be used for random file access of local files using the browser.
 */
export class JsFileStream extends RandomAccessStream {

    /**
     * The java file associated with this stream.
     */
    readonly #file: IRealFile;
    #fileBlob: File | null = null;
    #writablefileStream: FileSystemWritableFileStream | null = null;
    #_position: number = 0;
    #canWrite: boolean = false;

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
            this.#canWrite = true;
        }
    }
    async #getBlob(): Promise<any> {
        if (this.#fileBlob == null) {
            this.#fileBlob = await (await this.#file.getPath()).getFile();
        }
        return this.#fileBlob;
    }
    async #getStream(): Promise<any> {
        if (this.#writablefileStream == null) {
            let fileHandle: FileSystemFileHandle = await this.#file.getPath() as FileSystemFileHandle;
            let exists: boolean = await this.#file.exists();
            if (exists) {
                this.#writablefileStream = await fileHandle.createWritable({ keepExistingData: true });
            } else {
                let parent: IRealFile | null = await this.#file.getParent();
                if(parent == null)
                    throw new Error("Could not get parent");
                await parent.createFile(this.#file.getBaseName());
                this.#writablefileStream = await fileHandle.createWritable();
            }
        }
        return this.#writablefileStream;
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
        if (await this.canWrite()) {
            let stream: FileSystemWritableFileStream = (await this.#getStream());
            await stream.seek(this.#_position);
        }
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException
     */
    public override async setLength(value: number): Promise<void> {
        if(await this.canWrite()) {
            let stream: FileSystemWritableFileStream = await this.#getStream();
            await stream.truncate(0);
        } else {
            throw new Error("Stream is not writable");
        }
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
        let len: number = Math.min(count, buffer.length - offset);
        let blob: File = await this.#getBlob();
        len = Math.min(len, blob.size - this.#_position);
        let arr: Blob = blob.slice(this.#_position, this.#_position + len);
        let buff = new Uint8Array(await arr.arrayBuffer());
        for (let i = 0; i < buff.length; i++) {
            buffer[offset + i] = buff[i];
        }
        await this.setPosition(this.#_position + buff.length);
        return buff.length;
    }

    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        let stream: FileSystemWritableFileStream = await this.#getStream();
        await stream.write(buffer.slice(offset, offset + count));
        await this.setPosition(this.#_position + count);
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
        return this.#_position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public override async flush(): Promise<void> {
    }

    /**
     * Close this stream and associated resources.
     * @throws IOException
     */
    public override async close(): Promise<void> {
        if (this.#writablefileStream != null)
            await this.#writablefileStream.close();
    }
}
