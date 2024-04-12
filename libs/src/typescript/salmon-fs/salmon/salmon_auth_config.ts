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
import { MemoryStream } from "../../salmon-core/streams/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/streams/salmon_stream.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonDrive } from "./salmon_drive.js";
import { SalmonFile } from "./salmon_file.js";
import { RandomAccessStream } from "../../salmon-core/streams/random_access_stream.js";
import { NonceSequence, Status } from "../sequence/nonce_sequence.js";
import { SequenceException } from "../sequence/sequence_exception.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { SalmonNonce } from "../../salmon-core/salmon/salmon_nonce.js";
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";

/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
export class SalmonAuthConfig {

    readonly #driveId: Uint8Array = new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH);
    readonly #authId: Uint8Array = new Uint8Array(SalmonDriveGenerator.AUTH_ID_SIZE);
    readonly #startNonce: Uint8Array = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
    readonly #maxNonce: Uint8Array = new Uint8Array(SalmonGenerator.NONCE_LENGTH);

    /**
     * Get the drive ID to grant authorization for.
     * @return
     */
    public getDriveId(): Uint8Array {
        return this.#driveId;
    }

    /**
     * Get the authorization ID for the target device.
     * @return
     */
    public getAuthId(): Uint8Array {
        return this.#authId;
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
        await ms.read(this.#driveId, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        await ms.read(this.#authId, 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        await ms.read(this.#startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        await ms.read(this.#maxNonce, 0, SalmonGenerator.NONCE_LENGTH);
        await ms.close();
    }

    /**
     * Write the properties of the auth configuration to a config file that will be imported by another device.
     * The new device will then be authorized editing operations ie: import, rename files, etc.
     * @param authConfigFile
     * @param drive The drive you want to create an auth config for.
     * @param targetAuthId Authorization ID of the target device.
     * @param targetStartingNonce Starting nonce for the target device.
     * @param targetMaxNonce Maximum nonce for the target device.
     * @throws Exception
     */
    static async #writeAuthFile(authConfigFile: IRealFile,
        drive: SalmonDrive,
        targetAuthId: Uint8Array,
        targetStartingNonce: Uint8Array, targetMaxNonce: Uint8Array,
        configNonce: Uint8Array): Promise<void> {
        let driveId: Uint8Array | null = drive.getDriveId();
        if (driveId == null)
            throw new Error("Could not write auth file, no drive id found");
        let salmonFile: SalmonFile = new SalmonFile(authConfigFile, drive);
        let stream: RandomAccessStream = await salmonFile.getOutputStream(configNonce);
        await SalmonAuthConfig.#writeToStream(stream, driveId, targetAuthId, targetStartingNonce, targetMaxNonce);
    }

    /**
     * Write authorization configuration to a SalmonStream.
     * @param stream The stream to write to.
     * @param driveId The drive id.
     * @param authId The auth id of the new device.
     * @param nextNonce The next nonce to be used by the new device.
     * @param maxNonce The max nonce to be used byte the new device.
     * @throws Exception
     */
    static async #writeToStream(stream: RandomAccessStream, driveId: Uint8Array, authId: Uint8Array,
        nextNonce: Uint8Array, maxNonce: Uint8Array): Promise<void> {
        let ms: MemoryStream = new MemoryStream();
        try {
            await ms.write(driveId, 0, driveId.length);
            await ms.write(authId, 0, authId.length);
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
    
    /**
     * Get the app drive pair configuration properties for this drive
     *
     * @param authFile The drive.
     * @param authFile The encrypted authorization file.
     * @return The decrypted authorization file.
     * @throws Exception
     */
    static async #getAuthConfig(drive: SalmonDrive, authFile: IRealFile): Promise<SalmonAuthConfig> {
        let salmonFile: SalmonFile = new SalmonFile(authFile, drive);
        let stream: SalmonStream = await salmonFile.getInputStream();
        let ms: MemoryStream = new MemoryStream();
        await stream.copyTo(ms);
        await ms.close();
        await stream.close();
        let driveConfig: SalmonAuthConfig = new SalmonAuthConfig();
        await driveConfig.init(ms.toArray());
        if (!await SalmonAuthConfig.#verifyAuthId(drive, driveConfig.getAuthId()))
            throw new SalmonSecurityException("Could not authorize this device, the authorization id does not match");
        return driveConfig;
    }

    
    /**
     * Verify the authorization id with the current drive auth id.
     *
     * @param authId The authorization id to verify.
     * @return
     * @throws Exception
     */
    static async #verifyAuthId(drive: SalmonDrive, authId: Uint8Array): Promise<boolean> {
        let authIdBytes: Uint8Array = await drive.getAuthIdBytes();
        return authId.every(async (val, index) => val === authIdBytes[index]);
    }

    /**
     * Import sequence into the current drive.
     *
     * @param {SalmonDrive} drive
     * @param {SalmonAuthConfig} authConfig
     * @throws Exception
     */
    static async #importSequence(drive: SalmonDrive, authConfig: SalmonAuthConfig): Promise<void> {
        let drvStr: string = BitConverter.toHex(authConfig.getDriveId());
        let authStr: string = BitConverter.toHex(authConfig.getAuthId());
        await drive.getSequencer().initializeSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
    }
    
    /**
     * Import the device authorization file.
     *
     * @param authConfigFile The filepath to the authorization file.
     * @throws Exception
     */
    public static async importAuthFile(drive: SalmonDrive, authConfigFile: IRealFile): Promise<void> {
        let driveId: Uint8Array | null = drive.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");

        let sequence: NonceSequence | null = await drive.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence != null && sequence.getStatus() == Status.Active)
            throw new Error("Device is already authorized");

        if (authConfigFile == null || !await authConfigFile.exists())
            throw new Error("Could not import file");

        let authConfig: SalmonAuthConfig = await SalmonAuthConfig.#getAuthConfig(drive, authConfigFile);

        let authIdBytes: Uint8Array = await drive.getAuthIdBytes();
        if (!authConfig.getAuthId().every((val, index) => val === authIdBytes[index])
            || !authConfig.getDriveId().every((val, index) => driveId != null && val == driveId[index])
        )
            throw new Error("Auth file doesn't match driveId or authId");

        await SalmonAuthConfig.#importSequence(drive, authConfig);
    }
    
    /**
     * @param targetAuthId The authorization id of the target device.
     * @param targetDir    The target dir the file will be written to.
     * @param filename     The filename of the auth config file.
     * @throws Exception
     */
    public static async exportAuthFile(drive: SalmonDrive, targetAuthId: string, file: IRealFile): Promise<void> {
        let driveId: Uint8Array | null = drive.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");

        let cfgNonce: Uint8Array | null = await drive.getSequencer().nextNonce(BitConverter.toHex(driveId));
        if (cfgNonce == null)
            throw new Error("Could not get config nonce");

        let sequence: NonceSequence | null = await drive.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence == null)
            throw new Error("Device is not authorized to export");
        if(await file.exists() && await file.length() > 0) {
            let outStream: RandomAccessStream | null = null;
            try {
				outStream = await file.getOutputStream();
				await outStream.setLength(0);
            } catch(ex) {
            } finally {
                if(outStream != null)
                    await outStream.close();
            }
        }
        let maxNonce: Uint8Array | null = sequence.getMaxNonce();
        if (maxNonce == null)
            throw new SequenceException("Could not get current max nonce");
        let nextNonce: Uint8Array | null = sequence.getNextNonce();
        if (nextNonce == null)
            throw new SequenceException("Could not get next nonce");
        let pivotNonce: Uint8Array = SalmonNonce.splitNonceRange(nextNonce, maxNonce);
        let authId: string | null = sequence.getAuthId();
        if(authId == null)
            throw new SequenceException("Could not get auth id");
        await drive.getSequencer().setMaxNonce(sequence.getId(), authId, pivotNonce);
        await SalmonAuthConfig.#writeAuthFile(file, drive,
            BitConverter.hexToBytes(targetAuthId),
            pivotNonce, maxNonce,
            cfgNonce);
    }

}