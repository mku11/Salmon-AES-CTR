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
import { CopyOptions, IFile, MoveOptions } from './ifile.js';
import { LocalStorageFileStream } from '../streams/ls_file_stream.js';

/**
 * Salmon localStorage implementation. This can be used to store small files.
 */
export class LocalStorageFile implements IFile {
    public static readonly separator: string = "/";

    #filePath: string;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param {string} path The filepath.
     */
    public constructor(path: string) {
        this.#filePath = path;
    }

    /**
     * Create a directory under this directory. Not supported.
     * @param {string} dirName The name of the new directory.
     * @returns {Promise<IFile>} The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IFile> {
        throw new Error("Not supported");
    }

    /**
     * Create a file under this directory.
     * @param {string} filename The name of the new file.
     * @returns {Promise<IFile>} The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IFile> {
		let child: IFile = new LocalStorageFile(this.#filePath + LocalStorageFile.separator + filename);
		return child;
    }

    /**
     * Delete this file or directory.
     * @returns {Promise<boolean>} True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        localStorage.removeItem(this.#filePath);
        return true;
    }

    /**
     * True if file or directory exists.
     * @returns {Promise<boolean>} True if exists
     */
    public async exists(): Promise<boolean> {
        return localStorage.getItem(this.#filePath) != null;
    }

    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @returns {string}  The file path
     */
    public getPath(): string {
        return this.#filePath;
    }

    /**
     * Get the display path on the physical disk. For java this is the same as the filepath.
     * @returns {string} The display path.
     */
    public getDisplayPath(): string {
        return this.#filePath;
    }

    /**
     * Get the name of this file or directory.
     * @returns {string} The name of this file or directory.
     */
    public getName(): string {
        if (this.#filePath == null)
            throw new Error("Filepath is not assigned");
        return this.#filePath.split(LocalStorageFile.separator).pop() as string;
    }

    /**
     * Get a stream for reading the file.
     * @returns {Promise<RandomAccessStream>} The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: LocalStorageFileStream = new LocalStorageFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @returns {Promise<RandomAccessStream>} The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        let fileStream: LocalStorageFileStream = new LocalStorageFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @returns {Promise<IFile>} The parent directory.
     */
    public async getParent(): Promise<IFile> {
		let index: number = this.#filePath.lastIndexOf(LocalStorageFile.separator);
		let dirPath: string = this.#filePath.substring(0, index);
		let dir: IFile = new LocalStorageFile(dirPath);
        return dir;
    }

    /**
     * True if this is a directory.
     * @returns {Promise<boolean>} True if directory
     */
    public async isDirectory(): Promise<boolean> {
        return false;
    }

    /**
     * True if this is a file.
     * @returns {Promise<boolean>} True if file
     */
    public async isFile(): Promise<boolean> {
        return true;
    }

    /**
     * Get the last modified date on disk. Not supported.
     * @returns {Promise<number>} Last date modified
     */
    public async getLastDateModified(): Promise<number> {
        throw new Error("Not supported");
    }

    /**
     * Get the size of the file on disk.
     * @returns {Promise<number>} The size
     */
    public async getLength(): Promise<number> {
        let contents: string | null = localStorage.getItem(this.#filePath);
        if(contents == null)
            return 0;
        return btoa(contents).length;
    }

    /**
     * Get the count of files and subdirectories. Not supported.
     * @returns {Promise<number>} The number of files and subdirectories.
     */
    public async getChildrenCount(): Promise<number> {
        throw new Error("Not supported");
    }
    /**
     * List all files under this directory. Not supported.
     * @returns {Promise<IFile[]>} The list of files.
     */
    public async listFiles(): Promise<IFile[]> {
        throw new Error("Not supported");
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     * @param {IFile} newDir The target directory.
     * @param {MoveOptions} [options] The options.
     * @returns {Promise<IFile>} The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IFile, options?: MoveOptions): Promise<IFile> {
        throw new Error("Not supported");
    }

    /**
     * Move this file or directory under a new directory. Not supported.
     * @param {IFile} newDir    The target directory.
     * @param {CopyOptions} [options] The options
     * @returns {Promise<IFile | null>} The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IFile, options?: CopyOptions): Promise<IFile | null> {
        throw new Error("Not supported");
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param {string} filename The name of the file or directory.
     * @returns {Promise<IFile | null>} The child
     */
    public async getChild(filename: string): Promise<IFile | null> {
        let child: IFile = new LocalStorageFile(this.#filePath + LocalStorageFile.separator + filename);
		return child;
    }

    /**
     * Rename the current file or directory. Not supported.
     * @param {string} newFilename The new name for the file or directory.
     * @returns {Promise<boolean>} True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        throw new Error("Not supported");
    }

    /**
     * Create this directory under the current filepath.
     * @returns {boolean} True if created.
     */
    public async mkdir(): Promise<boolean> {
        // no-op
        return true;
    }
	
	/**
     * Reset cached properties
     */
    public reset() {
		
	}

    /**
     * Returns a string representation of this object
     * @returns {string} The string
     */
    public toString(): string {
        return this.#filePath;
    }
}
