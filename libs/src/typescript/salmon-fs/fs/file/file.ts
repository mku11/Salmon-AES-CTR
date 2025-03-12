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
import { IRealFile, copyFileContents, moveRecursively } from './ifile.js';
import { FileStream } from '../streams/file_stream.js';
import { IOException } from '../../../salmon-core/streams/io_exception.js';

/**
 * Salmon real local filesystem implementation for Javascript. This can be used only with the 
 * browser.
 */
export class File implements IRealFile {
    public static readonly separator: string = "/";
    public static readonly SMALL_FILE_MAX_LENGTH: number = 1 * 1024 * 1024;

    #fileHandle: any;
    #parent: File | null;
    #name: string | null;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param path The filepath.
     */
    public constructor(fileHandle: FileSystemHandle | null, parent: File | null = null, name: string | null = null) {
        this.#fileHandle = fileHandle;
        this.#parent = parent;
        this.#name = name;
    }

    /**
     * Create a directory under this directory.
     * @param dirName The name of the new directory.
     * @return The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IRealFile> {
        try {
            let nDirHandle: FileSystemDirectoryHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
                .getDirectoryHandle(dirName, { create: false });
            if (nDirHandle != null)
                throw new Error("directory already exists");
        } catch (ex) { }

        let nDirHandle: FileSystemDirectoryHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
            .getDirectoryHandle(dirName, { create: true });
        let jsDir: File = new File(nDirHandle, this);
        return jsDir;
    }

    /**
     * Create a file under this directory.
     * @param filename The name of the new file.
     * @return The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IRealFile> {
        let nFileHandle: FileSystemFileHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
            .getFileHandle(filename, { create: true });
        let jsFile: File = new File(nFileHandle, this);
        return jsFile;
    }

    /**
     * Delete this file or directory.
     * @return True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
		if (this.#fileHandle != null && this.#fileHandle.remove != undefined)
			this.#fileHandle.remove();
        else if (this.#parent != null)
			await (this.#parent.getPath() as FileSystemDirectoryHandle).removeEntry(this.getName(), {recursive: true});
        return !await this.exists();
    }

    /**
     * True if file or directory exists.
     * @return
     */
    public async exists(): Promise<boolean> {
        // if this is the root handle we assume it always exists
        if (this.#fileHandle == null)
            return false;
        if (this.#parent == null)
            return true;
        try {
            let nFileHandle: FileSystemHandle | null = null;
            try {
                nFileHandle = await (this.#parent.getPath() as FileSystemDirectoryHandle).getFileHandle(this.getName(), { create: false });
            } catch (ex) { }
            if (nFileHandle == null) {
                try {
                    nFileHandle = await (this.#parent.getPath() as FileSystemDirectoryHandle).getDirectoryHandle(this.getName(), { create: false });
                } catch (ex) { }
            }
            if(nFileHandle != null)
                return true;
        } catch (ex) { }
        return false;
    }

    /**
     * Get the absolute path on the physical disk. For js local file system this is the FileHandle.
     * @return The absolute path.
     */
    public getDisplayPath(): any {
        let filename = this.#fileHandle != null ? this.#fileHandle.name : this.#name;
        if (this.#parent == null)
            return "/" + (filename != null ? filename : "");
        return this.#parent.getDisplayPath() + File.separator + filename;
    }

    /**
     * Get the name of this file or directory.
     * @return The name of this file or directory.
     */
    public getName(): string {
        if (this.#fileHandle == null && this.#name != null)
            return this.#name;
        else if (this.#fileHandle == null)
            throw new Error("Filehandle is not assigned");
        return this.#fileHandle.name;
    }

    /**
     * Get a stream for reading the file.
     * @return The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: FileStream = new FileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @return The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        if (!await this.exists()) {
            let parent: IRealFile | null = await this.getParent();
            if (parent == null)
                throw new Error("Could not get parent");
            let nFile: IRealFile = await parent.createFile(this.getName());
            this.#fileHandle = nFile.getPath();
        }
        let fileStream: FileStream = new FileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @return The parent directory.
     */
    public async getParent(): Promise<IRealFile | null> {
        return this.#parent as IRealFile;
    }

    /**
     * Get the path of this file. For js local filesystem this is a relative path.
     * @return
     */
    public getPath(): FileSystemHandle {
        return this.#fileHandle;
    }

    /**
     * True if this is a directory.
     * @return
     */
    public async isDirectory(): Promise<boolean> {
        return this.#fileHandle != null && this.#fileHandle.kind == 'directory';
    }

    /**
     * True if this is a file.
     * @return
     */
    public async isFile(): Promise<boolean> {
        return this.#fileHandle != null && !await this.isDirectory();
    }

    /**
     * Get the last modified date on disk.
     * @return
     */
    public async getLastDateModified(): Promise<number> {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await this.#fileHandle.getFile();
        return fileBlob.lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @return
     */
    public async getLength(): Promise<number> {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await this.#fileHandle.getFile();
        return fileBlob.size;
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
		let nFiles: IRealFile[] = [];
        for await (const [key, value] of this.#fileHandle.entries()) {
            let file: IRealFile = new File(value, this);
			if(await file.isFile())
				nFiles.push(file);
			else
				files.push(file);
        }
		files = files.concat(nFiles);
        return files;
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir The target directory.
     * @param newName The new filename
     * @param progressListener Observer to notify when progress changes.
     * @return The moved file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async move(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile> {
        newName = newName != null ? newName : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exist");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (typeof (this.#fileHandle.move) !== 'undefined') {
            await this.#fileHandle.move(newDir.getPath(), newName);
            return await newDir.getChild(newName) as File;
        } else {
            let oldFilename: string = this.getName();
            let parent: IRealFile | null = await this.getParent();
            await this.copy(newDir, newName, progressListener);
            let newFile: IRealFile | null = await newDir.getChild(newName);
            if (newFile == null)
                throw new IOException("Could not move file");
            if (parent != null) {
                let oldFile: IRealFile | null = await parent.getChild(oldFilename);
                if (oldFile != null)
                    await oldFile.delete();
            }
            return newFile;
        }
    }

    /**
     * Move this file or directory under a new directory.
     * @param newDir    The target directory.
     * @param newName   New filename
     * @param progressListener Observer to notify when progress changes.
     * @return The copied file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async copy(newDir: IRealFile, newName: string | null = null, progressListener: ((position: number, length: number) => void) | null = null): Promise<IRealFile | null> {
        newName = newName != null ? newName : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exists");
        let newFile: IRealFile | null = await newDir.getChild(newName);
        if (newFile != null && await newFile.exists())
            throw new IOException("Another file/directory already exists");
        if (await this.isDirectory()) {
            let parent: IRealFile | null = await this.getParent();
            if(await this.getChildrenCount() > 0 || parent == null)
                throw new IOException("Could not copy directory use IRealFile copyRecursively() instead");
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
    public async getChild(filename: string): Promise<IRealFile | null> {
        if (await this.isFile())
            throw new Error("Parent is a file");
        let nFileHandle: FileSystemHandle | null = null;
        try {
            nFileHandle = await (this.#fileHandle as FileSystemDirectoryHandle).getFileHandle(filename, { create: false });
        } catch (ex) { }
        if (nFileHandle == null) {
            try {
                nFileHandle = await (this.#fileHandle as FileSystemDirectoryHandle).getDirectoryHandle(filename, { create: false });
            } catch (ex) { }
        }
        if (nFileHandle == null)
            return new File(null, this, filename);
        let child: File = new File(nFileHandle, this);
        return child;
    }

    /**
     * Rename the current file or directory.
     * @param newFilename The new name for the file or directory.
     * @return True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        if (typeof (this.#fileHandle.move) !== 'undefined')
            await this.#fileHandle.move(newFilename);
        else if (this.#parent == null) {
            return false;
        } else if(await this.isDirectory() && (await this.listFiles()).length > 0) {
            throw new Error("Cannot rename non-empty directory. Create a new directory manually and moveRecursively() instead");
        }else {
            let nFile: IRealFile = await this.move(this.#parent, newFilename);
            this.#fileHandle = nFile.getPath();
        }
        return this.#fileHandle.name == newFilename;
    }

    /**
     * Create this directory under the current filepath.
     * @return True if created.
     */
    public async mkdir(): Promise<boolean> {
        if (this.#parent == null)
            return false;
        let dir: IRealFile = await this.#parent.createDirectory(this.getName());
        this.#fileHandle = dir.getPath();
        return await dir.exists();
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
        return this.getName();
    }
}
