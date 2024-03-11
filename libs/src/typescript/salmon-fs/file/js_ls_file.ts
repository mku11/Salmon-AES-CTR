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
import { IRealFile } from './ireal_file.js';
import { JsLocalStorageFileStream } from './js_ls_file_stream.js';

/**
 * Salmon localStorage implementation. This can be used to store small files.
 */
export class JsLocalStorageFile implements IRealFile {
    public static readonly separator: string = "/";
    public static readonly SMALL_FILE_MAX_LENGTH: number = 1 * 1024 * 1024;

    #filePath: string;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    public constructor(path: string) {
        this.#filePath = path;
    }

    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IRealFile> {
        throw new Error("Not supported");
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    public async createFile(filename: string): Promise<IRealFile> {
		let child: IRealFile = new JsLocalStorageFile(this.#filePath + JsLocalStorageFile.separator + filename);
		return child;
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        localStorage.removeItem(this.#filePath);
        return true;
    }

    /**
     * True if file or directory exists.
     * @return
     */
    public async exists(): Promise<boolean> {
        return localStorage.getItem(this.#filePath) != null;
    }

    /**
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    public getAbsolutePath(): string {
        return this.#filePath;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getBaseName(): string {
        if (this.#filePath == null)
            throw new Error("Filepath is not assigned");
        return this.#filePath.split(JsLocalStorageFile.separator).pop() as string;
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: JsLocalStorageFileStream = new JsLocalStorageFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        let fileStream: JsLocalStorageFileStream = new JsLocalStorageFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile> {
		let index: number = this.#filePath.lastIndexOf(JsLocalStorageFile.separator);
		let dirPath: string = this.#filePath.substring(0, index);
		let dir: IRealFile = new JsLocalStorageFile(dirPath);
        return dir;
    }

    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @return
     */
    public getPath(): string {
        return this.#filePath;
    }

    /**
     * True if this is a directory.
     * @return
     */
    public async isDirectory(): Promise<boolean> {
        return false;
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        return true;
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async lastModified(): Promise<number> {
        throw new Error("Not supported");
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async length(): Promise<number> {
        let contents: string | null = localStorage.getItem(this.#filePath);
        if(contents == null)
            return 0;
        return btoa(contents).length;
    }

    /**
     * Get the count of files and subdirectories
     * @return
     */
    public async getChildrenCount(): Promise<number> {
        throw new Error("Not supported");
    }
    /**
     * List all files under this directory.
     * @return The list of files.
     */
    public async listFiles(): Promise<IRealFile[]> {
        throw new Error("Not supported");
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        throw new Error("Not supported");
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException
     */
    public async copy(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile | null> {
        throw new Error("Not supported");
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    public async getChild(filename: string): Promise<IRealFile | null> {
        let child: IRealFile = new JsLocalStorageFile(this.#filePath + JsLocalStorageFile.separator + filename);
		return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        throw new Error("Not supported");
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
        throw new Error("Not supported");
    }

    /**
     * Returns a string representation of this object
     */
    public toString(): string {
        return this.#filePath;
    }
}
