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

import { RandomAccessStream } from '../../../salmon-core/streams/random_access_stream.js';
import { IRealFile } from './ifile.js';
import { HttpFileStream } from '../streams/http_file_stream.js';
import { IOException } from '../../../salmon-core/streams/io_exception.js';
import { MemoryStream } from '../../../salmon-core/streams/memory_stream.js';

/**
 * Salmon RealFile implementation for Javascript.
 */
export class HttpFile implements IRealFile {
    public static readonly separator: string = "/";
    public static readonly SMALL_FILE_MAX_LENGTH: number = 128*1024;
	public static readonly BUFFER_LENGTH: number = 32*1024;

    filePath: string;
    response: Response | null = null;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    public constructor(path: string) {
        this.filePath = path;
    }

    async getResponse(): Promise<Response> {
        if (this.response == null) {
			let headers = new Headers();
			this.setDefaultHeaders(headers);
            this.response = (await fetch(this.filePath, 
			{method: 'HEAD', keepalive: true, headers: headers}));
			await this.#checkStatus(this.response, 200);
		}
        return this.response;
    }

    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IRealFile> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public createFile(filename: string): Promise<IRealFile> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * True if file or directory exists.
     * @return
     */
    public async exists(): Promise<boolean> {
        return (await this.getResponse()).status == 200 || (await this.getResponse()).status == 206;
    }

    /**
     * Get the absolute path on the physical disk. For javascript this is the same as the filepath.
     * @return The absolute path.
     */
    public getDisplayPath(): string {
        return this.filePath;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getName(): string {
        if (this.filePath == null)
            throw new Error("Filepath is not assigned");
        let nFilePath = this.filePath;
        if(nFilePath.endsWith("/"))
            nFilePath = nFilePath.substring(0,nFilePath.length-1);
        let basename: string | undefined = nFilePath.split(HttpFile.separator).pop();
        if (basename === undefined)
            throw new Error("Could not get basename");
        if (basename.includes("%")){
            basename = decodeURIComponent(basename);
        }
        return basename;
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: HttpFileStream = new HttpFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile> {
		let path: string = this.filePath;
		if(path.endsWith(HttpFile.separator))
			path = path.slice(0,-1);
        let parentFilePath: string = path.substring(0, path.lastIndexOf(HttpFile.separator));
        return new HttpFile(parentFilePath);
    }

    /**
     * Get the path of this file. For Javascript this is the same as the absolute filepath.
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
        return !await this.isDirectory();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async getLastDateModified(): Promise<number> {
        let headers: Headers = (await this.getResponse()).headers;
        let lastDateModified: string | null = headers.get("last-modified");
        if (lastDateModified == null) {
			lastDateModified = headers.get("date");
		}
		if (lastDateModified == null) {
			lastDateModified = "0";
		}
        let date: Date = new Date(lastDateModified);
        let lastModified = date.getTime();
        return lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async getLength(): Promise<number> {
        let res: Response = (await this.getResponse());
        if (res == null)
            throw new IOException("Could not get response");

        let length: number = 0;
        let lenStr: string | null = res.headers.get("content-length");
        if (lenStr != null) {
            length = parseInt(lenStr);
        }
        return length;
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
		if(await this.isDirectory()) {
			let files: IRealFile[] = [];
			let stream: RandomAccessStream = await this.getInputStream();
			let ms: MemoryStream = new MemoryStream();
			await stream.copyTo(ms);
			await ms.close();
			await stream.close();
			let contents: string = new TextDecoder().decode(ms.toArray());
			let matches = contents.matchAll(/HREF\=\"(.+?)\"/ig);
			for (const match of matches) {
				let filename: string = match[1];
				if (filename.includes(":") || filename.includes(".."))
					continue;
				if (filename.includes("%")){
					filename = decodeURIComponent(filename);
				}
				let file: IRealFile = new HttpFile(this.filePath + HttpFile.separator + filename);
				files.push(file);
			}
			return files;
		}
		return [];
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    public async getChild(filename: string): Promise<IRealFile | null> {
        if (await this.isFile())
            return null;
        let child: HttpFile = new HttpFile(this.filePath + HttpFile.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
        throw new Error("Unsupported Operation, readonly filesystem");
    }
	
	/**
     * Reset cached properties
     */
    public reset() {
		this.response = null;
	}

    /**
     * Returns a string representation of this object
     */
    public toString(): string {
        return this.filePath;
    }
	
	async #checkStatus(httpResponse: Response, status: number) {
        if (httpResponse.status != status)
            throw new IOException(httpResponse.status
                    + " " + httpResponse.statusText);
    }

    private setDefaultHeaders(headers: Headers) {
        headers.append("Cache", "no-store");
		headers.append("Connection", "keep-alive");
    }
}
