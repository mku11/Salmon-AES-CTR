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
import { JsNodeFileStream } from './js_node_file_stream.js';
import { IOException } from '../../salmon-core/io/io_exception.js';
import { MemoryStream } from '../../salmon-core/io/memory_stream.js';
import { mkdir, stat, readdir, rename, open, FileHandle, unlink, rmdir } from 'node:fs/promises';
import { Stats, existsSync, rmdirSync, unlinkSync } from 'node:fs';
import path from 'node:path';

/**
 * Salmon real local filesystem implementation for node js. This can be used only with node js.
 */
export class JsNodeFile implements IRealFile {
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
        let nDirPath: string = this.#filePath + JsNodeFile.separator + dirName;
        await mkdir(nDirPath);
        let dotNetDir: JsNodeFile = new JsNodeFile(nDirPath);
        return dotNetDir;
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException
     */
    public async createFile(filename: string): Promise<IRealFile> {
        let nFilepath: string = this.#filePath + JsNodeFile.separator + filename;
        let fd: FileHandle = await open(nFilepath, 'a');
        await fd.close();
        return new JsNodeFile(nFilepath);
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
        if (await this.isDirectory()) {
            let files: IRealFile[] = await this.listFiles();
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
     * @return
     */
    public async exists(): Promise<boolean> {
        let stats: Stats | null = null;
        try {
            let path: string = this.getAbsolutePath();
            stats = await stat(path);
            return stats.isFile() || stats.isDirectory();
        } catch (ex) { }
        return false;
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
        return path.basename(this.#filePath);
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: JsNodeFileStream = new JsNodeFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        let fileStream: JsNodeFileStream = new JsNodeFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile> {
        let parentFilePath: string = path.dirname(this.getAbsolutePath());
        return new JsNodeFile(parentFilePath);
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
        let stats: Stats = await stat(this.getAbsolutePath());
        return stats.isDirectory();
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        let stats: Stats = await stat(this.getAbsolutePath());
        return stats.isFile();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async lastModified(): Promise<number> {
        return await (await stat(this.getAbsolutePath())).mtime.getMilliseconds();
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async length(): Promise<number> {
        return await (await stat(this.getAbsolutePath())).size;
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
        let lFiles = await readdir(this.#filePath);
        for (const filename of lFiles) {
            let file: IRealFile = new JsNodeFile(this.#filePath + JsNodeFile.separator + filename);
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
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        let nFilePath: string = newDir.getAbsolutePath() + JsNodeFile.separator + newName;
        await rename(this.#filePath, nFilePath);
        return new JsNodeFile(nFilePath);
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
        newName = newName != null ? newName : this.getBaseName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            return newDir.createDirectory(newName);
        } else {
            newFile = await newDir.createFile(newName);
            let res: boolean = await copyFileContents(this, newFile, false, progressListener);
            return res ? newFile : null;
        }
    }

    /**
     * Get the file or directory under this directory with the provided name.
     * @param filename The name of the file or directory.
     * @return
     */
    public async getChild(filename: string): Promise<IRealFile | null> {
        if (await this.isFile())
            return null;
        let child: JsNodeFile = new JsNodeFile(this.#filePath + JsNodeFile.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        let nfile: string = await this.getParent() + JsNodeFile.separator + newFilename;
        await rename(this.#filePath, nfile);
        this.#filePath = nfile;
        return true;
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
        await mkdir(this.#filePath);
        return await this.exists();
    }

    /**
     * Returns a string representation of this object
     */
    public toString(): string {
        return this.#filePath;
    }
}
