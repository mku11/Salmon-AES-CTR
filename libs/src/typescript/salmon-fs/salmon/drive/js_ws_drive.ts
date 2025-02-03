/*
MIT License

Copyright (c) 2025 Max Kas

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
import { Credentials, JsWSFile } from "../../file/js_ws_file.js";
import { INonceSequencer } from "../../sequence/inonce_sequencer.js";
import { SalmonFile } from "../salmon_file.js";

/**
 * SalmonDrive implementation for a Web Service-based drive. 
 * This provides a virtual drive implementation
 * that you can access read-only encrypted files remotely.
 * Use static methods open() or create() to create an instance.
 */
export class JsWSDrive extends SalmonDrive {
    private static serviceCredentials: Map<IRealFile, Credentials> = new Map();

    /**
     * Private constructor, use open() instead.
     */
    private constructor() {
        super();
    }

    /**
     * Helper method that opens and initializes a JsWSDrive
     * @param {IRealFile} dir The real directory that contains the drive.
     * @param {string} password Text password to use with this drive.
     * @param {ISalmonSequencer} sequencer Optional nonce sequencer that will be used for importing files.
     * @returns {Promise<SalmonDrive>} The drive.
     */
    public static async open(dir: IRealFile, password: string, sequencer: INonceSequencer | null = null, 
        serviceUser: string, servicePassword: string): Promise<SalmonDrive> {
        JsWSDrive.serviceCredentials.set(dir, new Credentials(serviceUser, servicePassword));
        return await SalmonDrive.openDrive(dir, JsWSDrive, password, sequencer);
    }
    
    /**
     * Helper method that creates and initializes a JsWSDrive
     * @param {IRealFile} dir The directory that will host the drive.
     * @param {string} password The password.
     * @param {ISalmonSequencer} sequencer The nonce sequencer that will be used for encryption.
     * @param {string} serviceUser The web service username
     * @param {string} servicePassword The web service password
     * @return {Promise<SalmonDrive>} The drive.
     * @throws IOException If error occurs during creating the drive.
     */
    static async create(dir: IRealFile, password: string, sequencer: INonceSequencer, serviceUser: string, servicePassword: string) {
        JsWSDrive.serviceCredentials.set(dir, new Credentials(serviceUser, servicePassword));
        return await SalmonDrive.createDrive(dir, JsWSDrive, password, sequencer);
    }

        
    /**
     * Get a private dir for sharing files with external applications.
     * @return {IRealFile} The private dir
     * @throws Exception
     */
    public getPrivateDir(): IRealFile {
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
     * @returns The encrypted file.
     */
    public override getFile(file: IRealFile): SalmonFile {
        return new SalmonFile(file, this);
    }

    public override async initialize(realRoot: IRealFile, createIfNotExists: boolean): Promise<void> {
        let credentials: Credentials | undefined = JsWSDrive.serviceCredentials.get(realRoot);
        if(credentials)
            (realRoot as JsWSFile).setCredentials(credentials);
        await super.initialize(realRoot, createIfNotExists);

    }
}