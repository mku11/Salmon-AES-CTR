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
import { CopyOptions, IFile, MoveOptions } from "./ifile";
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
     * @returns {Promise<IVirtualFile[]>} The list of files
     */
    listFiles(): Promise<IVirtualFile[]>;

    /**
     * Get a file or directory under this directory
     * @param {string} filename The filename
     * @returns {Promise<IVirtualFile | null>} The virtual file
     */
    getChild(filename: string): Promise<IVirtualFile | null>;

    /**
     * Check if this is a file
     * @returns {Promise<boolean>} True if file.
     */
    isFile(): Promise<boolean>;

    /**
     * Check if this is a directory
     * @returns {Promise<boolean>} True if directory
     */
    isDirectory(): Promise<boolean>;

    /**
     * Get the path.
     * @returns {Promise<string>} The path
     */
    getPath(): Promise<string>;
    
    /**
     * Get the real path.
     * @returns {string} The path
     */
    getRealPath(): string;

    /**
     * Get the real file
     * @returns {IFile} The file
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
     * @returns {Promise<number>} The last date modified
     */
    getLastDateModified(): Promise<number>;

    /**
     * Get the file length
     * @returns {Promise<number>} The file length
     */
    getLength(): Promise<number>;

    /**
     * Check if it exists
     * @returns {Promise<boolean>} True if exists
     */
    exists(): Promise<boolean>;

    /**
     * Create a new directory under this directory
     * @param {string} dirName The name of the directory
     * @returns {Promise<IVirtualFile>} The new directory
     */
    createDirectory(dirName: string): Promise<IVirtualFile>;

    /**
     * Create a new file under this directory
     * @param {string} realFilename The name of the file
     * @returns {Promise<IVirtualFile>} The new file
     */
    createFile(realFilename: string): Promise<IVirtualFile>;

    /**
     * Rename this file or directory
     * @param {string} newFilename The new file name
     */
    rename(newFilename: string): Promise<void>;

    /**
     * Move this file.
     * @param {IVirtualFile} dir The destination directory
     * @param {MoveOptions} [options]
     * @returns {Promise<IVirtualFile>} The moved file
     */
    move(dir: IVirtualFile, options?: MoveOptions): Promise<IVirtualFile>;

    /**
     * Copy this file
     * @param {IVirtualFile} dir The destination directory
     * @param {CopyOptions} [options] The options
     * @returns {Promise<IVirtualFile>} The copied file
     */
    copy(dir: IVirtualFile, options?: CopyOptions): Promise<IVirtualFile>;

    /**
     * Copy recursively
     * @param {IVirtualFile} dest The destination directory
     * @param {VirtualRecursiveCopyOptions} [options] The options
     */
    copyRecursively(dest: IVirtualFile, options?: VirtualRecursiveCopyOptions): Promise<void>;

    /**
     * Move recursively
     * @param {IVirtualFile} dest The destination directory
     * @param {VirtualRecursiveMoveOptions} [options] The options
     */
    moveRecursively(dest: IVirtualFile, options?: VirtualRecursiveMoveOptions): Promise<void>;

    /**
     * Delete recursively
     * @param {VirtualRecursiveDeleteOptions} [options] The options
     */
    deleteRecursively(options?: VirtualRecursiveDeleteOptions): Promise<void>;
}

/**
 * Directory copy options (recursively)
 */
export class VirtualRecursiveCopyOptions {
    /**
     * Callback when file with same name exists
     */
    autoRename?: ((file: IVirtualFile) => Promise<string>);

    /**
     * True to autorename folders
     */
    autoRenameFolders: boolean = false;

    /**
     * Callback when file changes
     */
    onFailed?: ((file: IVirtualFile, ex: Error) => void);

    /**
     * Callback where progress changed
     */
    onProgressChanged?: ((file: IVirtualFile, position: number, length: number) => void);
}

/**
 * Directory move options (recursively)
 */
export class VirtualRecursiveMoveOptions {
    /**
     * Callback when file with the same name exists
     */
    autoRename?: ((file: IVirtualFile) => Promise<string>);

    /**
     * True to autorename folders
     */
    autoRenameFolders: boolean = false;

    /**
     * Callback when file failed
     */
    onFailed?: ((file: IVirtualFile, ex: Error) => void);
    
    /**
     * Callback when progress changes
     */
    onProgressChanged?: ((file: IVirtualFile, position: number, length: number) => void);
}


/**
 * Directory move options (recursively)
 */
export class VirtualRecursiveDeleteOptions {
    /**
     * Callback when file failed
     */
    onFailed?: ((file: IVirtualFile, ex: Error) => void);

    /**
     * Callback when progress changed
     */
    onProgressChanged?: ((file: IVirtualFile, position: number, length: number) => void);
}