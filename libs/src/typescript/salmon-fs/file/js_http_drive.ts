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
import { IRealFile } from "./ireal_file.js";
import { SalmonFile } from "../salmonfs/salmon_file.js";
import { VirtualFile } from "./virtual_file.js";
import { ISalmonSequencer } from "../sequence/isalmon_sequencer.js";

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
    public constructor() {
        super();
    }

    public static async open(dir: IRealFile, sequencer: ISalmonSequencer | null = null): Promise<SalmonDrive> {
        return await SalmonDrive.openDrive(dir, JsHttpDrive, sequencer);
    }
    
    public static async create(dir: IRealFile, sequencer: ISalmonSequencer, password: string): Promise<SalmonDrive> {
        return await SalmonDrive.createDrive(dir, JsHttpDrive, sequencer, password);
    }

    /**
     * Get a private dir for sharing files with external applications.
     * @return
     * @throws Exception
     */
    public static getPrivateDir(): string {
        throw new Error("Unsupported Operation");
    }

    /**
     * When authorization succeed.
     */
    protected override onUnlockSuccess(): void {

    }

    /**
     * When authorization succeeds.
     */
    protected override onUnlockError(): void {

    }

    protected createVirtualRoot(virtualRootRealFile: IRealFile): VirtualFile {
        return new SalmonFile(virtualRootRealFile, this);
    }
}