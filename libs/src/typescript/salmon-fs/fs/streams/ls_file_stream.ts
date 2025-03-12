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
import { IRealFile } from "../file/ifile.js";
import { Base64 } from "../../../salmon-core/convert/base64.js";

/**
 * An advanced file stream implementation for localStorage files.
 * This class can be used to read and write small file in localStorage.
 */
export class LocalStorageFileStream extends RandomAccessStream {

    /**
     * The java file associated with this stream.
     */
    readonly #file: IRealFile;
    #stream: MemoryStream;
    #canWrite: boolean = false;
    #base64: Base64;

    /**
     * Construct a file stream from an LocalStorageFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The LocalStorageFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public constructor(file: IRealFile, mode: string) {
        super();
        this.#file = file;
        this.#base64 = new Base64();
        if (mode == "rw") {
            this.#canWrite = true;
            this.#stream = new MemoryStream();
        } else {
            let contents: string | null = localStorage.getItem(this.#file.getDisplayPath());
            if(contents == null)
                contents = "";
            this.#stream = new MemoryStream(this.#base64.decode(contents));
        }
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
        return await this.#stream.getPosition();
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setPosition(value: number): Promise<void> {
        await this.#stream.setPosition(value);
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async setLength(value: number): Promise<void> {
        
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
        return await this.#stream.read(buffer, offset, count);
    }

    /**
     * Write the data from the buffer provided into the stream.
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    public override async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        await this.#stream.write(buffer, offset, count);
    }

    /**
     * Seek to the offset provided.
     * @param offset The position to seek to.
     * @param origin The type of origin {@link RandomAccessStream.SeekOrigin}
     * @return The new position after seeking.
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
        let contents: string = this.#base64.encode(this.#stream.toArray());
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
