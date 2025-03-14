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
import { IFile, copyFileContents } from './ifile.js';
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
    public async createDirectory(dirName: string): Promise<IFile> {
        let nDirPath: string = this.#filePath + NodeFile.separator + dirName;
        await mkdir(nDirPath, { recursive: true });
        let dotNetDir: NodeFile = new NodeFile(nDirPath);
        return dotNetDir;
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
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
     * @return True if deletion is successful.
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
     * @return
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
     * Get the absolute path on the physical disk. For java this is the same as the filepath.
     * @return The absolute path.
     */
    public getDisplayPath(): string {
        return this.#filePath;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getName(): string {
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
        let fileStream: NodeFileStream = new NodeFileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        let fileStream: NodeFileStream = new NodeFileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IFile> {
        let parentFilePath: string = path.dirname(this.getDisplayPath());
        return new NodeFile(parentFilePath);
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
        let stats: Stats = await stat(this.getDisplayPath());
        return stats.isDirectory();
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        let stats: Stats = await stat(this.getDisplayPath());
        return stats.isFile();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async getLastDateModified(): Promise<number> {
        return await (await stat(this.getDisplayPath())).mtime.getMilliseconds();
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async getLength(): Promise<number> {
        return await (await stat(this.getDisplayPath())).size;
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
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     */
    public async move(newDir: IFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IFile> {
        newName = newName != null ? newName : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        let nFilePath: string = newDir.getDisplayPath() + NodeFile.separator + newName;
        await rename(this.#filePath, nFilePath);
        return new NodeFile(nFilePath);
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IFile | null> {
        newName = newName != null ? newName : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            let parent: IFile | null = await this.getParent();
            if(await this.getChildrenCount() > 0 || parent == null)
                throw new IOException("Could not copy directory use IFile copyRecursively() instead");
            return parent.createDirectory(newName);
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
    public async getChild(filename: string): Promise<IFile | null> {
        if (await this.isFile())
            return null;
        let child: NodeFile = new NodeFile(this.#filePath + NodeFile.separator + filename);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        let nfile: string = await this.getParent() + NodeFile.separator + newFilename;
        await rename(this.#filePath, nfile);
        this.#filePath = nfile;
        return true;
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
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
     */
    public toString(): string {
        return this.#filePath;
    }
}
