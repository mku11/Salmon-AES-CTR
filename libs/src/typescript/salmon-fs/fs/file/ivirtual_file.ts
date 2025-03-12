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
import { IRealFile } from "./ifile";

export interface IVirtualFile {
    /*
     * A virtual file. Read-only operations are included. Since write operations can be implementation
     * specific ie for encryption they can be implemented by extending this class.
     */
    getInputStream(): Promise<RandomAccessStream>;
    getOutputStream(): Promise<RandomAccessStream>;
    listFiles(): Promise<IVirtualFile[]>;
    getChild(filename: string): Promise<IVirtualFile | null>;
    isFile(): Promise<boolean>;
    isDirectory(): Promise<boolean>;
    getPath(): Promise<string>;
    getRealPath(): string;
    getRealFile(): IRealFile;
    getName(): Promise<string>;
    getParent(): Promise<IVirtualFile | null>;
    delete(): void;
    mkdir(): void;
    getLastDateModified(): Promise<number>;
    getSize(): Promise<number>;
    exists(): Promise<boolean>;
    createDirectory(dirName: string): Promise<IVirtualFile>;
    createFile(realFilename: string): Promise<IVirtualFile>;
    rename(newFilename: string): Promise<void>;
    move(dir: IVirtualFile, OnProgressListener: ((position: number, length: number) => void) | null): Promise<IVirtualFile>;
    copy(dir: IVirtualFile, OnProgressListener: ((position: number, length: number) => void) | null): Promise<IVirtualFile>;
    copyRecursively(dest: IVirtualFile,
        autoRename: ((file: IVirtualFile) => Promise<string>) | null,
        autoRenameFolders: boolean,
        onFailed: ((file: IVirtualFile, ex: Error) => void) | null,
        progressListener: ((file: IVirtualFile, position: number, length: number) => void) | null): Promise<void>;
    moveRecursively(dest: IVirtualFile,
        autoRename: ((file: IVirtualFile) => Promise<string>) | null,
        autoRenameFolders: boolean,
        onFailed: ((file: IVirtualFile, ex: Error) => void) | null,
        progressListener: ((file: IVirtualFile, position: number, length: number) => void) | null): Promise<void>;
    deleteRecursively(
        onFailed: ((file: IVirtualFile, ex: Error) => void) | null,
        progressListener: ((file: IVirtualFile, position: number, length: number) => void) | null): Promise<void>;
}