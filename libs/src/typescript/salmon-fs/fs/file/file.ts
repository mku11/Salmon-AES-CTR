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
import { FileStream } from '../streams/file_stream.js';
import { IOException } from '../../../salmon-core/streams/io_exception.js';

/**
 * Salmon real local filesystem implementation for Javascript. This can be used only with the 
 * browser.
 */
export class File implements IFile {
    public static readonly separator: string = "/";

    #fileHandle: any;
    #parent: File | null;
    #name: string | null;

    /**
     * Instantiate a real file represented by the filepath provided.
     * @param {FileSystemHandle|null} fileHandle The fileHandle.
     */
    public constructor(fileHandle: FileSystemHandle | null, parent: File | null = null, name: string | null = null) {
        this.#fileHandle = fileHandle;
        this.#parent = parent;
        this.#name = name;
    }

    /**
     * Create a directory under this directory.
     * @param {string} dirName The name of the new directory.
     * @returns {Promise<IFile>} The newly created directory.
     */
    public async createDirectory(dirName: string): Promise<IFile> {
        try {
            let nDirHandle: FileSystemDirectoryHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
                .getDirectoryHandle(dirName, { create: false });
            if (nDirHandle)
                throw new Error("directory already exists");
        } catch (ex) { }

        let nDirHandle: FileSystemDirectoryHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
            .getDirectoryHandle(dirName, { create: true });
        let jsDir: File = new File(nDirHandle, this);
        return jsDir;
    }

    /**
     * Create a file under this directory.
     * @param {string} filename The name of the new file.
     * @returns {Promise<IFile>} The newly created file.
     * @throws IOException Thrown if there is an IO error.
     */
    public async createFile(filename: string): Promise<IFile> {
        let nFileHandle: FileSystemFileHandle = await (this.#fileHandle as FileSystemDirectoryHandle)
            .getFileHandle(filename, { create: true });
        let jsFile: File = new File(nFileHandle, this);
        return jsFile;
    }

    /**
     * Delete this file or directory.
     * @returns {Promise<boolean>} True if deletion is successful.
     */
    public async delete(): Promise<boolean> {
		if (this.#fileHandle  && this.#fileHandle.remove != undefined)
			this.#fileHandle.remove();
        else if (this.#parent)
			await (this.#parent.getPath() as FileSystemDirectoryHandle).removeEntry(this.getName(), {recursive: true});
        return !await this.exists();
    }

    /**
     * True if file or directory exists.
     * @returns {Promise<boolean>} True if exists
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
            if(nFileHandle)
                return true;
        } catch (ex) { }
        return false;
    }

    /**
     * Get the path of this file. For local filesystem see FileSystemHandle
     * @returns {FileSystemHandle} 
     */
    public getPath(): FileSystemHandle {
        return this.#fileHandle;
    }

    /**
     * Get the absolute path on the physical disk. For local file system this is the FileHandle.
     * @returns {any} The absolute path.
     */
    public getDisplayPath(): any {
        let filename = this.#fileHandle  ? this.#fileHandle.name : this.#name;
        if (this.#parent == null)
            return separator + (filename  ? filename : "");
        return this.#parent.getDisplayPath() + File.separator + filename;
    }

    /**
     * Get the name of this file or directory.
     * @returns {string} The name of this file or directory.
     */
    public getName(): string {
        if (this.#fileHandle == null && this.#name)
            return this.#name;
        else if (this.#fileHandle == null)
            throw new Error("Filehandle is not assigned");
        return this.#fileHandle.name;
    }

    /**
     * Get a stream for reading the file.
     * @returns {Promise<RandomAccessStream>} The stream to read from.
     * @throws FileNotFoundException
     */
    public async getInputStream(): Promise<RandomAccessStream> {
        let fileStream: FileStream = new FileStream(this, "r");
        return fileStream;
    }

    /**
     * Get a stream for writing to this file.
     * @returns {Promise<RandomAccessStream>} The stream to write to.
     * @throws FileNotFoundException
     */
    public async getOutputStream(): Promise<RandomAccessStream> {
        if (!await this.exists()) {
            let parent: IFile | null = await this.getParent();
            if (parent == null)
                throw new Error("Could not get parent");
            let nFile: IFile = await parent.createFile(this.getName());
            this.#fileHandle = nFile.getPath();
        }
        let fileStream: FileStream = new FileStream(this, "rw");
        return fileStream;
    }

    /**
     * Get the parent directory of this file or directory.
     * @returns {Promise<IFile | null>} The parent directory.
     */
    public async getParent(): Promise<IFile | null> {
        return this.#parent as IFile;
    }

    /**
     * True if this is a directory.
     * @returns {Promise<boolean>} True if directory.
     */
    public async isDirectory(): Promise<boolean> {
        return this.#fileHandle && this.#fileHandle.kind === 'directory';
    }

    /**
     * True if this is a file.
     * @returns {Promise<boolean>} True if file.
     */
    public async isFile(): Promise<boolean> {
        return this.#fileHandle && this.#fileHandle.kind === 'file';
    }

    /**
     * Get the last modified date on disk.
     * @returns {Promise<number>} The last date modified.
     */
    public async getLastDateModified(): Promise<number> {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await this.#fileHandle.getFile();
        return fileBlob.lastModified;
    }

    /**
     * Get the size of the file on disk.
     * @returns {Promise<number>} The file length
     */
    public async getLength(): Promise<number> {
        if (await this.isDirectory())
            return 0;
        let fileBlob = await this.#fileHandle.getFile();
        return fileBlob.size;
    }

    /**
     * Get the count of files and subdirectories
     * @returns {Promise<number>} The number of children
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
		let nFiles: IFile[] = [];
        for await (const [key, value] of this.#fileHandle.entries()) {
            let file: IFile = new File(value, this);
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
     * @param {IFile} newDir The target directory.
     * @param {MoveOptions} [options] The options
     * @returns {Promise<IFile>} The moved file. Use this file for subsequent operations instead of the original.
     * @throws IOException Thrown if there is an IO error.
     */
    public async move(newDir: IFile, options?: MoveOptions): Promise<IFile> {
        if(!options)
            options = new MoveOptions();
        let newName = options.newFilename  ? options.newFilename : this.getName();
        if (newDir == null || !await newDir.exists())
            throw new IOException("Target directory does not exist");
        let newFile: IFile | null = await newDir.getChild(newName);
        if (newFile && await newFile.exists())
            throw new IOException("Another file/directory already exists");

        if (typeof (this.#fileHandle.move) !== 'undefined') {
		if (options.onProgressChanged != null)
				options.onProgressChanged(0, await this.getLength());
            await this.#fileHandle.move(newDir.getPath(), newName);
			newFile = await newDir.getChild(newName) as File;
			if (options.onProgressChanged != null)
				options.onProgressChanged(await newFile.getLength(), await newFile.getLength());
			return newFile;            
        } else {
            let oldFilename: string = this.getName();
            let parent: IFile | null = await this.getParent();
            let copyOptions: CopyOptions = new CopyOptions();
            copyOptions.newFilename = newName;
            copyOptions.onProgressChanged = options.onProgressChanged;
            await this.copy(newDir, copyOptions);
            let newFile: IFile | null = await newDir.getChild(newName);
            if (newFile == null)
                throw new IOException("Could not move file");
            if (parent) {
                let oldFile: IFile | null = await parent.getChild(oldFilename);
                if (oldFile)
                    await oldFile.delete();
            }
            return newFile;
        }
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
        if (newFile  && await newFile.exists())
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
     * @returns {Promise<IFile | null>} The file or directory
     */
    public async getChild(filename: string): Promise<IFile | null> {
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
     * @param {string} newFilename The new name for the file or directory.
     * @returns {boolean} True if successfully renamed.
     */
    public async renameTo(newFilename: string): Promise<boolean> {
        if (typeof (this.#fileHandle.move) !== 'undefined')
            await this.#fileHandle.move(newFilename);
        else if (this.#parent == null) {
            return false;
        } else if(await this.isDirectory() && (await this.listFiles()).length > 0) {
            throw new Error("Cannot rename non-empty directory. Create a new directory manually and moveRecursively() instead");
        }else {
            let moveOptions: MoveOptions = new MoveOptions();
            moveOptions.newFilename = newFilename;
            let nFile: IFile = await this.move(this.#parent, moveOptions);
            this.#fileHandle = nFile.getPath();
        }
        return this.#fileHandle.name == newFilename;
    }

    /**
     * Create this directory under the current filepath.
     * @returns {boolean} True if created.
     */
    public async mkdir(): Promise<boolean> {
        if (this.#parent == null)
            return false;
        let dir: IFile = await this.#parent.createDirectory(this.getName());
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
     * @returns {string} The string
     */
    public toString(): string {
        return this.getDisplayPath();
    }
}
