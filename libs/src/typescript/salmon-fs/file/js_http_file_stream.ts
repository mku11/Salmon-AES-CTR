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
import { JsHttpFile } from "./js_http_file.js";

/**
 * An advanced Salmon File Stream implementation for java files.
 * This class is used internally for random file access of physical (real) files.
 */
export class JsHttpFileStream extends RandomAccessStream {

    /**
     * The java file associated with this stream.
     */
    private readonly file: JsHttpFile;

    private _position: number = 0;

    private _stream: ReadableStream<Uint8Array> | null = null;

    /**
     * Construct a file stream from an AndroidFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The AndroidFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public constructor(file: JsHttpFile, mode: string) {
        super();
        this.file = file;
        if (mode == "rw") {
            throw new Error("Unsupported Operation");
        }
    }

    public async getStream(): Promise<ReadableStream<Uint8Array> | null> {
        if (this._stream == null)
            this._stream = (await fetch(this.file.getPath())).body;
        return this._stream;
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
        return await this.file.length();
    }

    /**
     * Get the current position of the stream.
     * @return
     * @throws IOException
     */
    public override async getPosition(): Promise<number> {
        return this._position;
    }

    /**
     * Set the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    public override async setPosition(value: number): Promise<void> {
        // TODO: get a new stream with Byte-Range
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
        let pos: number = this._position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = await this.file.length() - offset;

        this._position = pos;
        return this._position;

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
        let stream: ReadableStream<Uint8Array> | null = await this.getStream();
        if (stream != null)
            await stream.cancel();
    }
}
