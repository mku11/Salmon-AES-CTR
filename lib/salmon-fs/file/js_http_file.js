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
import { JsHttpFileStream } from './js_http_file_stream.js';
import { IOException } from '../../salmon-core/io/io_exception.js';
import { MemoryStream } from '../../salmon-core/io/memory_stream.js';
/**
 * Salmon RealFile implementation for Java.
 */
export class JsHttpFile {
    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    constructor(path) {
        this.response = null;
        this.filePath = path;
    }
    async getResponse() {
        if (this.response == null)
            this.response = (await fetch(this.filePath, { method: 'HEAD' }));
        return this.response;
    }
    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    async createDirectory(dirName) {
        throw new Error("Unsupported Operation");
    }
    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    createFile(filename) {
        throw new Error("Unsupported Operation");
    }
    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    async delete() {
        throw new Error("Unsupported Operation");
    }
    /**
     * True if file or directory exists.
     * @return
     */
    async exists() {
        return (await this.getResponse()).status == 200 || (await this.getResponse()).status == 206;
    }
    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    getAbsolutePath() {
        return this.filePath;
    }
    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    getBaseName() {
        if (this.filePath == null)
            throw new Error("Filepath is not assigned");
        let nFilePath = this.filePath;
        if (nFilePath.endsWith("/"))
            nFilePath = nFilePath.substring(0, nFilePath.length - 1);
        let basename = nFilePath.split(JsHttpFile.separator).pop();
        if (basename === undefined)
            throw new Error("Could not get basename");
        if (basename.includes("%")) {
            basename = decodeURIComponent(basename);
        }
        return basename;
    }
    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    async getInputStream() {
        let fileStream = new JsHttpFileStream(this, "r");
        return fileStream;
    }
    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    getOutputStream() {
        throw new Error("Unsupported Operation");
    }
    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    async getParent() {
        let path = this.filePath;
        if (path.endsWith(JsHttpFile.separator))
            path = path.slice(0, -1);
        let parentFilePath = path.substring(0, path.lastIndexOf(JsHttpFile.separator));
        return new JsHttpFile(parentFilePath);
    }
    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @return
     */
    getPath() {
        return this.filePath;
    }
    /**
     * True if this is a directory.
     * @return
     */
    async isDirectory() {
        let res = (await this.getResponse());
        if (res == null)
            throw new Error("Could not get response");
        if (res.headers == null)
            throw new Error("Could not get headers");
        let contentType = res.headers.get("Content-Type");
        if (contentType == null)
            throw new Error("Could not get content type");
        return contentType.startsWith("text/html");
    }
    /**
     * True if this is a file.
     * @return
     */
    async isFile() {
        return !await this.isDirectory();
    }
    /**
     * Get the last modified date on disk.
     * @return
     */
    async lastModified() {
        let headers = (await this.getResponse()).headers;
        let lastDateModified = headers.get("last-modified");
        if (lastDateModified == null) {
            lastDateModified = headers.get("date");
        }
        if (lastDateModified == null) {
            lastDateModified = "0";
        }
        let date = new Date(lastDateModified);
        let lastModified = date.getTime();
        return lastModified;
    }
    /**
     * Get the size of the file on disk.
     * @return
     */
    async length() {
        let res = (await this.getResponse());
        if (res == null)
            throw new IOException("Could not get response");
        let length = 0;
        let lenStr = res.headers.get("content-length");
        if (lenStr != null) {
            length = parseInt(lenStr);
        }
        else {
            res = (await fetch(this.filePath, { method: 'GET' }));
            if (res.body == null)
                throw new IOException("Could not get length from content. No response body.");
            let totalLength = 0;
            let reader = await res.body.getReader();
            while (true) {
                let readResult = await reader.read();
                if (readResult.value === undefined || readResult.value.length == 0)
                    break;
                totalLength += readResult.value.length;
                if (totalLength > JsHttpFile.SMALL_FILE_MAX_LENGTH) {
                    throw new IOException("Could not get length from file. If this is a large file make sure the server responds with a Content-Length");
                }
            }
            length = totalLength;
            reader.releaseLock();
        }
        return length;
    }
    /**
     * Get the count of files and subdirectories
     * @return
     */
    async getChildrenCount() {
        return (await this.listFiles()).length;
    }
    /**
     * List all files under this directory.
     * @return The list of files.
     */
    async listFiles() {
        let files = [];
        let stream = await this.getInputStream();
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        await ms.close();
        let contents = new TextDecoder().decode(ms.toArray());
        let matches = contents.matchAll(/HREF\=\"(.+?)\"/ig);
        for (const match of matches) {
            let filename = match[1];
            if (filename.includes(":") || filename.includes(".."))
                continue;
            if (filename.includes("%")) {
                filename = decodeURIComponent(filename);
            }
            let file = new JsHttpFile(this.filePath + JsHttpFile.separator + filename);
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
    async move(newDir, newName = null, progressListener = null) {
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
    async copy(newDir, newName = null, progressListener = null) {
        throw new Error("Unsupported Operation");
    }
    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    async getChild(filename) {
        if (await this.isFile())
            return null;
        let child = new JsHttpFile(this.filePath + JsHttpFile.separator + filename);
        return child;
    }
    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    async renameTo(newFilename) {
        throw new Error("Unsupported Operation");
    }
    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    async mkdir() {
        throw new Error("Unsupported Operation");
    }
    /**
     * Returns a string representation of this object
     */
    toString() {
        return this.filePath;
    }
}
JsHttpFile.separator = "/";
JsHttpFile.SMALL_FILE_MAX_LENGTH = 1 * 1024 * 1024;
