/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions;

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

import { RandomAccessStream } from "../../../salmon-core/streams/random_access_stream"
import { IFile } from "./ifile";
/*
* A virtual file.
*/
export interface IVirtualFile {
    /**
     * Get an input stream.
     */
    getInputStream(): Promise<RandomAccessStream>;

    /**
     * Get an output stream.
     */
    getOutputStream(): Promise<RandomAccessStream>;

    /**
     * List the files under this directory
     */
    listFiles(): Promise<IVirtualFile[]>;

    /**
     * Get a file or directory under this directory
     * @param filename The filename
     * @returns The virtual file
     */
    getChild(filename: string): Promise<IVirtualFile | null>;

    /**
     * Check if this is a file
     */
    isFile(): Promise<boolean>;

    /**
     * Check if this is a directory
     */
    isDirectory(): Promise<boolean>;

    /**
     * Get the path.
     */
    getPath(): Promise<string>;
    
    /**
     * Get the real path.
     */
    getRealPath(): string;

    /**
     * Get the real file
     */
    getRealFile(): IFile;

    /**
     * Get the name.
     * @returns {Promise<string>}
     */
    getName(): Promise<string>;

    /**
     * Get the parent.
     * @returns {Promise<IVirtualFile | null} The parent
     */
    getParent(): Promise<IVirtualFile | null>;

    /**
     * Delete this file.
     */
    delete(): void;

    /**
     * Create this directory.
     */
    mkdir(): void;

    /**
     * Get the last date modified
     */
    getLastDateModified(): Promise<number>;

    /**
     * Get the file length
     */
    getLength(): Promise<number>;

    /**
     * Check if it exists
     */
    exists(): Promise<boolean>;

    /**
     * Create a new directory under this directory
     * @param dirName The name of the directory
     * @returns {Promise<IVirtualFile>} The new directory
     */
    createDirectory(dirName: string): Promise<IVirtualFile>;

    /**
     * Create a new file under this directory
     * @param realFilename The name of the file
     * @returns {Promise<IVirtualFile>} The new file
     */
    createFile(realFilename: string): Promise<IVirtualFile>;

    /**
     * Rename this file or directory
     * @param newFilename The new file name
     */
    rename(newFilename: string): Promise<void>;

    /**
     * Move this file.
     * @param dir The destination directory
     * @param OnProgressListener 
     */
    move(dir: IVirtualFile, OnProgressListener: ((position: number, length: number) => void) | null): Promise<IVirtualFile>;

    /**
     * Copy this file
     * @param dir The destination directory
     * @param OnProgressListener 
     */
    copy(dir: IVirtualFile, OnProgressListener: ((position: number, length: number) => void) | null): Promise<IVirtualFile>;

    /**
     * Copy recursively
     * @param {IVirtualFile} dest The destination directory
     * @param {VirtualRecursiveCopyOptions| null} options The options
     */
    copyRecursively(dest: IVirtualFile, options: VirtualRecursiveCopyOptions | null): Promise<void>;

    /**
     * Move recursively
     * @param {IVirtualFile} dest The destination directory
     * @param {VirtualRecursiveMoveOptions| null} options The options
     */
    moveRecursively(dest: IVirtualFile, options: VirtualRecursiveMoveOptions| null): Promise<void>;

    /**
     * Delete recursively
     * @param {VirtualRecursiveDeleteOptions| null} options The options
     */
    deleteRecursively(options: VirtualRecursiveDeleteOptions| null): Promise<void>;
}

/**
 * Directory copy options (recursively)
 */
export class VirtualRecursiveCopyOptions {
    /**
     * Callback when file with same name exists
     */
    autoRename: ((file: IVirtualFile) => Promise<string>) | null = null;

    /**
     * True to autorename folders
     */
    autoRenameFolders: boolean = false;

    /**
     * Callback when file changes
     */
    onFailed: ((file: IVirtualFile, ex: Error) => void) | null = null;

    /**
     * Callback where progress changed
     */
    onProgressChanged: ((file: IVirtualFile, position: number, length: number) => void) | null = null;
}

/**
 * Directory move options (recursively)
 */
export class VirtualRecursiveMoveOptions {
    /**
     * Callback when file with the same name exists
     */
    autoRename: ((file: IVirtualFile) => Promise<string>) | null = null;

    /**
     * True to autorename folders
     */
    autoRenameFolders: boolean = false;

    /**
     * Callback when file failed
     */
    onFailed: ((file: IVirtualFile, ex: Error) => void) | null = null;
    
    /**
     * Callback when progress changes
     */
    onProgressChanged: ((file: IVirtualFile, position: number, length: number) => void) | null = null;
}


/**
 * Directory move options (recursively)
 */
export class VirtualRecursiveDeleteOptions {
    /**
     * Callback when file failed
     */
    onFailed: ((file: IVirtualFile, ex: Error) => void) | null = null;

    /**
     * Callback when progress changed
     */
    onProgressChanged: ((file: IVirtualFile, position: number, length: number) => void) | null = null;
}