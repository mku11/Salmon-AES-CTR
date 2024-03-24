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
import { SalmonDrive } from "../salmonfs/salmon_drive.js";
import { SalmonFile } from "../salmonfs/salmon_file.js";
/**
 * SalmonDrive implementation for standard javascript file API via HTTP. This provides a virtual drive implementation
 * that you can use to store and access encrypted files remotely.
 */
export class JsHttpDrive extends SalmonDrive {
    /**
     * Instantiate a virtual drive with the provided real filepath.
     * Encrypted files will be located under the {@link SalmonDrive#virtualDriveDirectoryName}.
     * @param realRoot The filepath to the location of the virtual drive.
     * @param createIfNotExists Create the drive if it doesn't exist.
     */
    constructor() {
        super();
    }
    static async open(dir, sequencer = null) {
        return await SalmonDrive.openDrive(dir, JsHttpDrive, sequencer);
    }
    static async create(dir, sequencer, password) {
        return await SalmonDrive.createDrive(dir, JsHttpDrive, sequencer, password);
    }
    /**
     * Get a private dir for sharing files with external applications.
     * @return
     * @throws Exception
     */
    static getPrivateDir() {
        throw new Error("Unsupported Operation");
    }
    /**
     * When authorization succeed.
     */
    onUnlockSuccess() {
    }
    /**
     * When authorization succeeds.
     */
    onUnlockError() {
    }
    createVirtualRoot(virtualRootRealFile) {
        return new SalmonFile(virtualRootRealFile, this);
    }
}
