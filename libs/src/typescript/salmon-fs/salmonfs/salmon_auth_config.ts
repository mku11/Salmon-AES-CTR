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

import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonDrive } from "./salmon_drive.js";
import { SalmonFile } from "./salmon_file.js";

/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
export class SalmonAuthConfig {

    readonly #driveID: Uint8Array = new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH);
    readonly #authID: Uint8Array = new Uint8Array(SalmonDriveGenerator.AUTH_ID_SIZE);
    readonly #startNonce: Uint8Array = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
    readonly #maxNonce: Uint8Array = new Uint8Array(SalmonGenerator.NONCE_LENGTH);

    /**
     * Get the drive ID to grant authorization for.
     * @return
     */
    public getDriveID(): Uint8Array {
        return this.#driveID;
    }

    /**
     * Get the authorization ID for the target device.
     * @return
     */
    public getAuthID(): Uint8Array {
        return this.#authID;
    }

    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    public getStartNonce(): Uint8Array {
        return this.#startNonce;
    }

    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    public getMaxNonce(): Uint8Array {
        return this.#maxNonce;
    }

    /**
     * Instantiate a class with the properties of the authorization config file.
     * @param contents The byte array that contains the contents of the auth config file.
     */
    public constructor() {

    }

    public async init(contents: Uint8Array): Promise<void> {
        let ms: MemoryStream = new MemoryStream(contents);
        await ms.read(this.#driveID, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        await ms.read(this.#authID, 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        await ms.read(this.#startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        await ms.read(this.#maxNonce, 0, SalmonGenerator.NONCE_LENGTH);
        await ms.close();
    }

    /**
     * Write the properties of the auth configuration to a config file that will be imported by another device.
     * The new device will then be authorized editing operations ie: import, rename files, etc.
     * @param authConfigFile
     * @param drive The drive you want to create an auth config for.
     * @param targetAuthID Authorization ID of the target device.
     * @param targetStartingNonce Starting nonce for the target device.
     * @param targetMaxNonce Maximum nonce for the target device.
     * @throws Exception
     */
    public static async writeAuthFile(authConfigFile: IRealFile,
        drive: SalmonDrive,
        targetAuthID: Uint8Array,
        targetStartingNonce: Uint8Array, targetMaxNonce: Uint8Array,
        configNonce: Uint8Array): Promise<void> {
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not write auth file, no drive id found");
        let salmonFile: SalmonFile = new SalmonFile(authConfigFile, drive);
        let stream: SalmonStream = await salmonFile.getOutputStream(configNonce);
        await SalmonAuthConfig.writeToStream(stream, driveId, targetAuthID, targetStartingNonce, targetMaxNonce);
    }

    /**
     * Write authorization configuration to a SalmonStream.
     * @param stream The stream to write to.
     * @param driveID The drive id.
     * @param authID The auth id of the new device.
     * @param nextNonce The next nonce to be used by the new device.
     * @param maxNonce The max nonce to be used byte the new device.
     * @throws Exception
     */
    public static async writeToStream(stream: SalmonStream, driveID: Uint8Array, authID: Uint8Array,
        nextNonce: Uint8Array, maxNonce: Uint8Array): Promise<void> {
        let ms: MemoryStream = new MemoryStream();
        try {
            await ms.write(driveID, 0, driveID.length);
            await ms.write(authID, 0, authID.length);
            await ms.write(nextNonce, 0, nextNonce.length);
            await ms.write(maxNonce, 0, maxNonce.length);
            let content: Uint8Array = ms.toArray();
            let buffer: Uint8Array = new Uint8Array(SalmonIntegrity.DEFAULT_CHUNK_SIZE);
            for (let i = 0; i < content.length; i++)
                buffer[i] = content[i];
            await stream.write(buffer, 0, content.length);
        } catch (ex) {
            console.error(ex);
            throw new SalmonAuthException("Could not write auth config", ex);
        } finally {
            await ms.close();
            await stream.flush();
            await stream.close();
        }
    }
}