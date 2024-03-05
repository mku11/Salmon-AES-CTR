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

import { RandomAccessStream } from "../../salmon-core/io/random_access_stream"
import { IRealFile } from "./ireal_file";

export abstract class VirtualFile {
    /*
     * A virtual file. Read-only operations are included. Since write operations can be implementation
     * specific ie for encryption they can be implemented by extending this class.
     */

    public abstract getInputStream(): Promise<RandomAccessStream>;

    public abstract getOutputStream(nonce: Uint8Array): Promise<RandomAccessStream>;


    public abstract listFiles(): Promise<VirtualFile[]>;


    public abstract getChild(filename: string): Promise<VirtualFile | null>;


    public abstract isFile(): Promise<boolean>;


    public abstract isDirectory(): Promise<boolean>;


    public abstract getPath(): Promise<string>;


    public abstract getRealPath(): string;


    public abstract getRealFile(): IRealFile;


    public abstract getBaseName(): Promise<string>;


    public abstract getParent(): Promise<VirtualFile | null>;


    public abstract delete(): void;


    public abstract mkdir(): void;


    public abstract getLastDateTimeModified(): Promise<number>;


    public abstract getSize(): Promise<number>;


    public abstract exists(): Promise<boolean>;

    public abstract createDirectory(dirName: string, key: Uint8Array | null, dirNameNonce: Uint8Array | null): Promise<VirtualFile>;

    public abstract createFile(realFilename: string): Promise<VirtualFile>;
}