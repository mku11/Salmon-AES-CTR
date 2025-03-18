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

import { AesDrive } from "./aes_drive.js";
import { IFile } from "../../fs/file/ifile.js";
import { AesFile } from "../file/aes_file.js";

/**
 * SalmonDrive implementation for an HTTP-based drive. 
 * This provides a virtual drive implementation
 * that you can access read-only encrypted files remotely.
 * Use static methods open() or create() to create an instance.
 */
export class HttpDrive extends AesDrive {

    /**
     * Private constructor, use open() instead.
     */
    private constructor() {
        super();
    }

    /**
     * Helper method that opens and initializes a Drive
     * @param {IFile} dir The real directory that contains the drive.
     * @param {string} password Text password to use with this drive.
     * @returns {Promise<AesDrive>} The drive.
     */
    public static async open(dir: IFile, password: string): Promise<AesDrive> {
        return await AesDrive.openDrive(dir, HttpDrive, password);
    }
    
    /**
     * Get a private dir for sharing files with external applications.
     * @returns {IFile} The private dir
     * @throws Exception
     */
    public getPrivateDir(): IFile {
        throw new Error("Unsupported Operation");
    }

    /**
     * When authorization succeed.
     */
    public override onUnlockSuccess(): void {
        console.log("drive unlocked");
    }

    /**
     * When authorization fails.
     */
    public override onUnlockError(): void {
        console.error("drive failed to unlock");
    }

    
    /**
     * 
     * @param file The real file.
     * @returns {AesFile} The encrypted file.
     */
    public override getVirtualFile(file: IFile): AesFile {
        return new AesFile(file, this);
    }
}