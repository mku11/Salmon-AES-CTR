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

import { AesDrive } from "../drive/aes_drive.js";
import { IFile } from "../../../simple-fs/fs/file/ifile.js";
import { INonceSequencer } from "../../../salmon-core/salmon/sequence/inonce_sequencer.js";
import { AesFile } from "../file/aes_file.js";

/**
 * Drive implementation for standard javascript local file API. This provides a virtual drive implementation
 * that you can use to store and access encrypted files locally using your browser. 
 * Supported only by Chrome browser.
 * Use static methods open() or create() to create an instance.
 */
export class Drive extends AesDrive {

    /**
     * Private constructor, use open() and create() instead.
     */
    private constructor() {
        super();
    }

    /**
     * Helper method that opens and initializes a Drive
     * @param {IFile} dir The real directory that contains the drive.
     * @param {string} password Text password to use with this drive.
     * @param {ISalmonSequencer} [sequencer] Optional nonce sequencer that will be used for importing files.
     * @returns {Promise<AesDrive>} The drive.
     */
    public static async open(dir: IFile, password: string, sequencer?: INonceSequencer): Promise<AesDrive> {
        return await AesDrive.openDrive(dir, Drive, password, sequencer);
    }

    /**
     * Helper method that creates and initializes a Drive
     * @param {IFile} dir The real directory that will contain the drive.
     * @param {string} password Text password to use with this drive.
     * @param {ISalmonSequencer} sequencer The nonce sequencer that will be used for encryption.
     * @returns {Promise<AesDrive>} The drive.
     */
    public static async create(dir: IFile,  password: string, sequencer: INonceSequencer): Promise<AesDrive> {
        return await AesDrive.createDrive(dir, Drive, password, sequencer);
    }

    /**
     * Get a private dir for sharing files with external applications.
     * @returns {IFile} The private directory.
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
     * @param {IFile} file The real file.
     * @returns {AesFile} The encrypted file.
     */
    public override getVirtualFile(file: IFile): AesFile {
        return new AesFile(file, this);
    }
}