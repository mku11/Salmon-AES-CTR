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

import { SalmonDrive } from "../salmon_drive.js";
import { IRealFile } from "../../file/ireal_file.js";
import { SalmonFile } from "../salmon_file.js";
import { IVirtualFile } from "../../file/ivirtual_file.js";
import { INonceSequencer } from "../../sequence/inonce_sequencer.js";

/**
 * SalmonDrive implementation for an HTTP-based drive. 
 * This provides a virtual drive implementation
 * that you can access read-only encrypted files remotely.
 */
export class JsHttpDrive extends SalmonDrive {

    /**
     * Private constructor, use open() instead.
     */
    private constructor() {
        super();
    }

    /**
     * Helper method that opens and initializes a JsDrive
     * @param {IRealFile} dir The directory that will host the drive.
     * @param {ISalmonSequencer} sequencer The nonce sequencer that will be used for encryption.
     * @returns {Promise<SalmonDrive>} The drive.
     */
    public static async open(dir: IRealFile, sequencer: INonceSequencer | null = null): Promise<SalmonDrive> {
        return await SalmonDrive.openDrive(dir, JsHttpDrive, sequencer);
    }
    
    /**
     * Get a private dir for sharing files with external applications.
     * @return
     * @throws Exception
     */
    public getPrivateDir(): string {
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
     * Get the virtual root directory for this drive.
     * @param {IRealFile} virtualRootRealFile 
     * @returns {IVirtualFile} The root directory.
     */
    protected getVirtualRoot(virtualRootRealFile: IRealFile): IVirtualFile {
        return new SalmonFile(virtualRootRealFile, this);
    }
}