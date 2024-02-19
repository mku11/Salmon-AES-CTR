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

import { RandomAccessStream } from '../../salmon-core/io/random_access_stream.js';
import { IRealFile, copyFileContents } from './ireal_file.js';
import { JsHttpFileStream } from './js_http_file_stream.js';
import { IOException } from '../../salmon-core/io/io_exception.js';
import { MemoryStream } from '../../salmon-core/io/memory_stream.js';

/**
 * Salmon RealFile implementation for Java.
 */
export class JsHttpFile implements IRealFile {
    public static readonly separator: string = "/";
    public static readonly SMALL_FILE_MAX_LENGTH: number = 1 * 1024 * 1024;

    private filePath: string;
    private _response: Response | null = null;
    private _length: number | null = null;
    private _lastModified: number | null = null;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    public constructor(path: string) {
        this.filePath = path;
    }

    private async getResponse(): Promise<Response> {
        if (this._response == null)
            this._response = (await fetch(this.filePath));
        return this._response;
    }

    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IRealFile> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    public createFile(filename: string): Promise<IRealFile> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        throw new Error("Unsupported Operation");
    }

    /**
     * True if file or directory exists.
     * @return
     */
    public async exists(): Promise<boolean> {
        return (await this.getResponse()).status == 200 || (await this.getResponse()).status == 206;
    }

    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    public async getAbsolutePath(): Promise<string> {
        return this.filePath;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getBaseName(): string {
        if (this.filePath == null)
            throw new Error("Filepath is not assigned");
        let basename: string | undefined = this.filePath.split(JsHttpFile.separator).pop();
        if (basename === undefined)
            throw new Error("Could not get basename");
        return basename;
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: JsHttpFileStream = new JsHttpFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public getOutputStream(): Promise<RandomAccessStream> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile> {
        let parentFilePath: string = new URL(".", this.filePath).toString();
        return new JsHttpFile(parentFilePath);
    }

    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @return
     */
    public getPath(): string {
        return this.filePath;
    }

    /**
     * True if this is a directory.
     * @return
     */
    public async isDirectory(): Promise<boolean> {
        let res: Response = (await this.getResponse());
        if (res == null)
            throw new Error("Could not get response");
        if (res.headers == null)
            throw new Error("Could not get headers");
        let contentType: string | null = res.headers.get("Content-Type");
        if (contentType == null)
            throw new Error("Could not get content type");
        return contentType.startsWith("text/html");
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        return await !this.isDirectory();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async lastModified(): Promise<number> {
        if (this._lastModified != null)
            return this._lastModified;
        let headers: Headers = (await this.getResponse()).headers;
        let lastDateModified: string | null = headers.get("last-modified");
        if (lastDateModified == null)
            throw new Error("Could not get last modified");
        let date: Date = new Date(lastDateModified);
        this._lastModified = date.getTime();
        return this._lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async length(): Promise<number> {
        if (this._length != null)
            return this._length;
        let res: Response = (await this.getResponse());
        if (res == null)
            throw new IOException("Could not get response");

        let lenStr: string | null = res.headers.get("content-length");
        if (lenStr != null) {
            this._length = parseInt(lenStr);
        }
        else {
            if (res.body == null)
                throw new IOException("Could not get length from content. No response body.");
            let totalLength: number = 0;
            let buff: Uint8Array;
            let reader: ReadableStreamDefaultReader = await res.body.getReader();
            while (true) {
                let readResult: ReadableStreamReadResult<any> = await reader.read();
                if (readResult.value === undefined || readResult.value.length == 0)
                    break;
                totalLength += readResult.value.length;
                if (totalLength > JsHttpFile.SMALL_FILE_MAX_LENGTH) {
                    throw new IOException("Could not get length from file. If this is a large file make sure the server responds with a Content-Length");
                }
            }
            this._length = totalLength;
        }
        return this._length;
    }

    /**
     * Get the count of files and subdirectories
     * @return
     */
    public async getChildrenCount(): Promise<number> {
        return (await this.listFiles()).length;
    }
    /**
     * List all files under this directory.
     * @return The list of files.
     */
    public async listFiles(): Promise<IRealFile[]> {
        let files: IRealFile[] = [];
        let stream: RandomAccessStream = await this.getInputStream();
        let ms: MemoryStream = new MemoryStream();
        await stream.copyTo(ms);
        await ms.close();
        let contents: string = new TextDecoder().decode(ms.toArray());
        let matches = contents.matchAll(/HREF\=\"(.+?)\"/ig);
        for (const match of matches) {
            let filename: string = match[1];
            if (filename.includes(":") || filename.includes(".."))
                continue;
            if (filename.includes("%2")){
                filename = decodeURIComponent(filename);
            }
            let file: IRealFile = new JsHttpFile(this.filePath + JsHttpFile.separator + filename);
            files.push(file);
        }
        return files;
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException
     */
    public async copy(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    public async getChild(filename: string): Promise<IRealFile | null> {
        if (await this.isFile())
            return null;
        let child: JsHttpFile = new JsHttpFile(this.filePath + JsHttpFile.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
        throw new Error("Unsupported Operation");
    }

    /**
     * Returns a string representation of this object
     */
    public toString(): string {
        return this.filePath;
    }
}
