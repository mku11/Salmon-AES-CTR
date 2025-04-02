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
import { CopyContentsOptions, CopyOptions, IFile, MoveOptions, copyFileContents } from './ifile.js';
import { NodeFileStream } from '../streams/node_file_stream.js';
import { IOException } from '../../../salmon-core/streams/io_exception.js';
import { mkdir, stat, readdir, rename, open, FileHandle } from 'node:fs/promises';
import { Stats, rmdirSync, unlinkSync } from 'node:fs';
import path from 'node:path';

/**
 * Salmon real local filesystem implementation for node js. This can be used only with node js.
 */
export class NodeFile implements IFile {
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
     * Create a directory under this directory.
     * @param {string} dirName The name of the new directory.
     * @returns {Promise<IFile>} The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IFile> {
        let nDirPath: string = this.#filePath + NodeFile.separator + dirName;
        await mkdir(nDirPath, { recursive: true });
        let dotNetDir: NodeFile = new NodeFile(nDirPath);
        return dotNetDir;
    }

    /**
     * Create a file under this directory.
     * @param {string} filename The name of the new file.
     * @returns {Promise<IFile>} The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IFile> {
        let nFilepath: string = this.#filePath + NodeFile.separator + filename;
        let fd: FileHandle = await open(nFilepath, 'a');
        await fd.close();
        return new NodeFile(nFilepath);
    }

    /**
     * Delete this file or directory.
     * @returns {Promise<boolean>} True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        if (await this.isDirectory()) {
            let files: IFile[] = await this.listFiles();
            for (let file of files) {
                await file.delete();
            }
            rmdirSync(this.#filePath);
        } else {
            unlinkSync(this.#filePath);
        }
        return !await this.exists();
    }

    /**
     * True if file or directory exists.
     * @returns {Promise<boolean>} True if exists
     */
    public async exists(): Promise<boolean> {
        let stats: Stats | null = null;
        try {
            let path: string = this.getDisplayPath();
            stats = await stat(path);
            return stats.isFile() || stats.isDirectory();
        } catch (ex) { }
        return false;
    }

    /**
     * Get the path of this file. For java this is the same as the absolute filepath.
     * @returns {string} The path
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
        return path.basename(this.#filePath);
    }

    /**
     * Get a stream for reading the file.
     * @returns {Promise<RandomAccessStream>} The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: NodeFileStream = new NodeFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @returns {Promise<RandomAccessStream>} The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        let fileStream: NodeFileStream = new NodeFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @returns {Promise<IFile>} The parent directory.
     */
    public async getParent(): Promise<IFile> {
        let parentFilePath: string = path.dirname(this.getDisplayPath());
        return new NodeFile(parentFilePath);
    }

    /**
     * True if this is a directory.
     * @returns {Promise<boolean>} True if directory
     */
    public async isDirectory(): Promise<boolean> {
        let stats: Stats = await stat(this.getDisplayPath());
        return stats.isDirectory();
    }

    /**
     * True if this is a file.
     * @returns {Promise<boolean>} True if file.
     */
    public async isFile(): Promise<boolean> {
        let stats: Stats = await stat(this.getDisplayPath());
        return stats.isFile();
    }

    /**
     * Get the last modified date on disk.
     * @returns {Promise<number>} The last date modified
     */
    public async getLastDateModified(): Promise<number> {
        return await (await stat(this.getDisplayPath())).mtime.getMilliseconds();
    }

    /**
     * Get the size of the file on disk.
     * @returns {Promise<number>} The file size
     */
    public async getLength(): Promise<number> {
        return await (await stat(this.getDisplayPath())).size;
    }

    /**
     * Get the count of files and subdirectories
     * @returns {Promise<number>} The number of files and subdirectories
     */
    public async getChildrenCount(): Promise<number> {
        return (await this.listFiles()).length;
    }
    /**
     * List all files under this directory.
     * @returns {Promise<IFile[]>} The list of files.
     */
    public async listFiles(): Promise<IFile[]> {
        let files: IFile[] = [];
        let lFiles = await readdir(this.#filePath);
        for (const filename of lFiles) {
            let file: IFile = new NodeFile(this.#filePath + NodeFile.separator + filename);
            files.push(file);
        }
        return files;
    }

    /**
     * Move this file or directory under a new directory.
     * @param {IFile} newDir The target directory.
     * @param {MoveOptions} [options] The options
     * @returns {Promise<IFile>} The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IFile, options?: MoveOptions): Promise<IFile> {
        if(!options)
            options = new MoveOptions();
        let newName = options.newFilename  ? options.newFilename : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile  && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        let nFilePath: string = newDir.getDisplayPath() + NodeFile.separator + newName;
        await rename(this.#filePath, nFilePath);
        return new NodeFile(nFilePath);
    }

    /**
     * Move this file or directory under a new directory.
     * @param {IFile} newDir    The target directory.
     * @param {CopyOptions} [options] The options
     * @returns {Promise<IFile | null>} The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IFile, options?: CopyOptions): Promise<IFile | null> {
        if(!options)
            options = new CopyOptions();
        let newName = options.newFilename  ? options.newFilename : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            let parent: IFile | null = await this.getParent();
            if(await this.getChildrenCount() > 0 || parent == null)
                throw new IOException("Could not copy directory use IFile copyRecursively() instead");
            return parent.createDirectory(newName);
        } else {
            newFile = await newDir.createFile(newName);
            let copyContentOptions: CopyContentsOptions = new CopyContentsOptions();
            copyContentOptions.onProgressChanged = options.onProgressChanged;
            let res: boolean = await copyFileContents(this, newFile, copyContentOptions);
            return res ? newFile : null;
        }
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param {string} filename The name of the file or directory.
     * @returns {Promise<IFile | null>} The child
     */
    public async getChild(filename: string): Promise<IFile | null> {
        if (await this.isFile())
            return null;
        let child: NodeFile = new NodeFile(this.#filePath + NodeFile.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param {string} newFilename The new name for the file or directory.
     * @returns {Promise<boolean>} True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        let nfile: string = await this.getParent() + NodeFile.separator + newFilename;
        await rename(this.#filePath, nfile);
        this.#filePath = nfile;
        return true;
    }

    /**
     * Create this directory under the current filepath.
     * @returns {Promise<boolean>} True if created.
     */
    public async mkdir(): Promise<boolean> {
        await mkdir(this.#filePath, { recursive: true });
        return await this.exists();
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
