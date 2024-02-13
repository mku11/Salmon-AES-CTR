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

import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { SalmonDrive } from "./salmon_drive.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonSequence, Status } from "../sequence/salmon_sequence.js";
import { ISalmonSequencer } from "../sequence/isalmon_sequencer.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonAuthConfig } from "./salmon_auth_config.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { SalmonFile } from "./salmon_file.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonNonce } from "../../salmon-core/salmon/salmon_nonce.js";

/**
 * Manages the drive and nonce sequencer to be used.
 * Currently only one drive and one nonce sequencer are supported.
 */
export class SalmonDriveManager {
    private static driveClassType: any | null = null;
    private static drive: SalmonDrive | null = null;
    private static sequencer: ISalmonSequencer;

    /**
     * Set the global drive class. Currently only one drive is supported.
     *
     * @param driveClassType
     */
    public static setVirtualDriveClass(driveClassType: any): void {
        SalmonDriveManager.driveClassType = driveClassType;
    }

    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    public static getSequencer(): ISalmonSequencer {
        return SalmonDriveManager.sequencer;
    }

    /**
     * Set the nonce sequencer used for the current drive.
     *
     * @param sequencer
     */
    public static setSequencer(sequencer: ISalmonSequencer): void {
        SalmonDriveManager.sequencer = sequencer;
    }

    /**
     * Get the current virtual drive.
     */
    public static getDrive(): SalmonDrive | null {
        return this.drive;
    }

    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dirPath The directory path that will be used for storing the contents of the drive
     */
    public static async openDrive(dirPath: string): Promise<SalmonDrive> {
        this.closeDrive();
        let drive: SalmonDrive = await SalmonDriveManager.createDriveInstance(dirPath, false);
        if (!drive.hasConfig()) {
            throw new Error("Drive does not exist");
        }
        SalmonDriveManager.drive = drive;
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param dirPath  Directory to store the drive configuration and virtual filesystem.
     * @param password Master password to encrypt the drive configuration.
     * @return The newly created drive.
     * @throws SalmonIntegrityException
     * @throws SalmonSequenceException
     */
    public static async createDrive(dirPath: string, password: string): Promise<SalmonDrive> {
        this.closeDrive();
        let drive: SalmonDrive = await SalmonDriveManager.createDriveInstance(dirPath, true);
        if (await drive.hasConfig())
            throw new SalmonSecurityException("Drive already exists");
        SalmonDriveManager.drive = drive;
        await drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param dirPath The target directory where the drive is located.
     * @param createIfNotExists Create the drive if it does not exist
     * @return
     * @throws SalmonSecurityException
     */
    private static async createDriveInstance(dirPath: string, createIfNotExists: boolean): Promise<SalmonDrive> {
        try {
            let drive = new this.driveClassType;
            drive.init(dirPath, createIfNotExists);
            return drive;
        } catch (e) {
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
    }

    /**
     * Close the current drive.
     */
    public static closeDrive(): void {
        if (this.drive != null) {
            this.drive.close();
            this.drive = null;
        }
    }

    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    static getAuthIDBytes(): Uint8Array {
        let drive: SalmonDrive | null = this.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let drvStr: string = BitConverter.toHex(driveId);
        let sequence: SalmonSequence = this.sequencer.getSequence(drvStr);
        if (sequence == null) {
            let authID: Uint8Array = SalmonDriveGenerator.generateAuthId();
            this.createSequence(driveId, authID);
        }
        sequence = this.sequencer.getSequence(drvStr);
        return BitConverter.hexToBytes(sequence.getAuthID());
    }

    /**
     * Import the device authorization file.
     *
     * @param filePath The filepath to the authorization file.
     * @throws Exception
     */
    public static async importAuthFile(filePath: string): Promise<void> {
        let drive: SalmonDrive | null = this.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");

        let sequence: SalmonSequence = this.sequencer.getSequence(BitConverter.toHex(driveId));
        if (sequence != null && sequence.getStatus() == Status.Active)
            throw new Error("Device is already authorized");

        let authConfigFile: IRealFile = drive.getRealFile(filePath, false);
        if (authConfigFile == null || !authConfigFile.exists())
            throw new Error("Could not import file");

        let authConfig: SalmonAuthConfig = await this.getAuthConfig(authConfigFile);


        if (!authConfig.getAuthID().every((val, index) => val === SalmonDriveManager.getAuthIDBytes()[index])
            || !authConfig.getDriveID().every((val, index) => driveId != null && val == driveId[index])
        )
            throw new Error("Auth file doesn't match driveID or authID");

        SalmonDriveManager.importSequence(authConfig);
    }

    /**
     * Get the default auth config filename.
     *
     * @return
     */
    public static getDefaultAuthConfigFilename(): string {
        return SalmonDrive.getAuthConfigFilename();
    }

    /**
     * @param targetAuthID The authentication id of the target device.
     * @param targetDir    The target dir the file will be written to.
     * @param filename     The filename of the auth config file.
     * @throws Exception
     */
    public static async exportAuthFile(targetAuthID: string, targetDir: string, filename: string): Promise<void> {
        let drive: SalmonDrive | null = this.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");


        let cfgNonce: Uint8Array = this.sequencer.nextNonce(BitConverter.toHex(driveId));

        let sequence: SalmonSequence = this.sequencer.getSequence(BitConverter.toHex(driveId));
        if (sequence == null)
            throw new Error("Device is not authorized to export");
        let dir: IRealFile = drive.getRealFile(targetDir, true);
        let targetAppDriveConfigFile: IRealFile | null = await dir.getChild(filename);
        if (targetAppDriveConfigFile == null || !targetAppDriveConfigFile.exists())
            targetAppDriveConfigFile = await dir.createFile(filename);
        else if (targetAppDriveConfigFile != null && await targetAppDriveConfigFile.exists())
            throw new SalmonAuthException(filename + " already exists, delete this file or choose another directory");

        let pivotNonce: Uint8Array = SalmonNonce.splitNonceRange(sequence.getNextNonce(), sequence.getMaxNonce());
        this.sequencer.setMaxNonce(sequence.getDriveID(), sequence.getAuthID(), pivotNonce);
        await SalmonAuthConfig.writeAuthFile(targetAppDriveConfigFile, drive,
            BitConverter.hexToBytes(targetAuthID),
            pivotNonce, sequence.getMaxNonce(),
            cfgNonce);
    }

    /**
     * Get the next nonce for the drive. This operation IS atomic as per transaction.
     *
     * @param salmonDrive
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonRangeExceededException
     */
    public static getNextNonce(salmonDrive: SalmonDrive): Uint8Array {
        let driveId: Uint8Array | null = salmonDrive.getDriveID();
        if (driveId == null)
            throw new SalmonSecurityException("Could not get drive Id");
        return this.sequencer.nextNonce(BitConverter.toHex(driveId));
    }

    /**
     * Create a nonce sequence for the drive id and the authentication id provided. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID The driveID
     * @param authID  The authID
     * @throws Exception
     */
    static createSequence(driveID: Uint8Array, authID: Uint8Array): void {
        let drvStr: string = BitConverter.toHex(driveID);
        let authStr: string = BitConverter.toHex(authID);
        this.sequencer.createSequence(drvStr, authStr);
    }

    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID Drive ID.
     * @param authID  Authentication ID.
     * @throws Exception
     */
    static initSequence(driveID: Uint8Array, authID: Uint8Array): void {
        let startingNonce: Uint8Array = SalmonDriveGenerator.getStartingNonce();
        let maxNonce: Uint8Array = SalmonDriveGenerator.getMaxNonce();
        let drvStr: string = BitConverter.toHex(driveID);
        let authStr: string = BitConverter.toHex(authID);
        this.sequencer.initSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public static revokeAuthorization(): void {
        if (this.drive == null)
            throw new Error("No drive opened");
        let driveID: Uint8Array | null = this.drive.getDriveID();
        if (driveID == null)
            throw new Error("Could not get revoke, make sure you initialize the drive first");
        this.sequencer.revokeSequence(BitConverter.toHex(driveID));
    }

    /**
     * Verify the authentication id with the current drive auth id.
     *
     * @param authID The authentication id to verify.
     * @return
     * @throws Exception
     */
    private static verifyAuthID(authID: Uint8Array): boolean {
        return authID.every((val, index) => val === SalmonDriveManager.getAuthIDBytes()[index]);
    }

    /**
     * Import sequence into the current drive.
     *
     * @param authConfig
     * @throws Exception
     */
    private static importSequence(authConfig: SalmonAuthConfig): void {
        let drvStr: string = BitConverter.toHex(authConfig.getDriveID());
        let authStr: string = BitConverter.toHex(authConfig.getAuthID());
        this.sequencer.initSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
    }

    /**
     * Get the app drive pair configuration properties for this drive
     *
     * @param authFile The encrypted authentication file.
     * @return The decrypted authentication file.
     * @throws Exception
     */
    public static async getAuthConfig(authFile: IRealFile): Promise<SalmonAuthConfig> {
        let drive: SalmonDrive | null = this.getDrive();
        if (drive == null)
            throw new Error("Could not get auth config, no drive opened");
        let salmonFile: SalmonFile = new SalmonFile(authFile, drive);
        let stream: SalmonStream = await salmonFile.getInputStream();
        let ms: MemoryStream = new MemoryStream();
        await stream.copyTo(ms);
        await ms.close();
        await stream.close();
        let driveConfig: SalmonAuthConfig = new SalmonAuthConfig();
        await driveConfig.init(ms.toArray());
        if (!this.verifyAuthID(driveConfig.getAuthID()))
            throw new SalmonSecurityException("Could not authorize this device, the authentication id does not match");
        return driveConfig;
    }

    /**
     * Get the authentication ID for the current device.
     *
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonAuthException
     */
    public static getAuthID(): string {
        return BitConverter.toHex(this.getAuthIDBytes());
    }
}
