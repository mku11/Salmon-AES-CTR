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
import { SalmonKey } from "./salmon_key.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { SalmonPassword } from "../../salmon-core/salmon/password/salmon_password.js";
import { EncryptionMode } from "../../salmon-core/salmon/io/encryption_mode.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveConfig } from "./salmon_drive_config.js";

/**
 * Manages the drive and nonce sequencer to be used.
 * Currently only one drive and one nonce sequencer are supported.
 */
export class SalmonDriveManager {
    static #driveClassType: any | null = null;
    static #drive: SalmonDrive | null = null;
    static #sequencer: ISalmonSequencer;

    /**
     * Set the global drive class. Currently only one drive is supported.
     *
     * @param driveClassType
     */
    public static setVirtualDriveClass(driveClassType: any): void {
        SalmonDriveManager.#driveClassType = driveClassType;
    }

    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    public static getSequencer(): ISalmonSequencer {
        return SalmonDriveManager.#sequencer;
    }

    /**
     * Set the nonce sequencer used for the current drive.
     *
     * @param sequencer
     */
    public static setSequencer(sequencer: ISalmonSequencer): void {
        SalmonDriveManager.#sequencer = sequencer;
    }

    /**
     * Get the current virtual drive.
     */
    public static getDrive(): SalmonDrive | null {
        return SalmonDriveManager.#drive;
    }

    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dirPath The directory path that will be used for storing the contents of the drive
     */
    public static async openDrive(dirPath: string): Promise<SalmonDrive> {
        this.closeDrive();
        let drive: SalmonDrive = await SalmonDriveManager.#createDriveInstance(dirPath, false);
        if (!await drive.hasConfig()) {
            throw new Error("Drive does not exist");
        }
        SalmonDriveManager.#drive = drive;
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
        let drive: SalmonDrive = await SalmonDriveManager.#createDriveInstance(dirPath, true);
        if (await drive.hasConfig())
            throw new SalmonSecurityException("Drive already exists");
        SalmonDriveManager.#drive = drive;
        await SalmonDriveManager.setPassword(drive, password);
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
    static async #createDriveInstance(dirPath: string, createIfNotExists: boolean): Promise<SalmonDrive> {
        try {
            let drive: SalmonDrive = new this.#driveClassType;
            await drive.init(dirPath, createIfNotExists);
            return drive;
        } catch (e) {
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
    }

    /**
     * Close the current drive.
     */
    public static closeDrive(): void {
        if (SalmonDriveManager.#drive != null) {
            SalmonDriveManager.#drive.close();
            SalmonDriveManager.#drive = null;
        }
    }

    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    static getAuthIDBytes(): Uint8Array {
        let drive: SalmonDrive | null = SalmonDriveManager.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let drvStr: string = BitConverter.toHex(driveId);
        let sequence: SalmonSequence = this.#sequencer.getSequence(drvStr);
        if (sequence == null) {
            let authID: Uint8Array = SalmonDriveGenerator.generateAuthId();
            this.createSequence(driveId, authID);
        }
        sequence = this.#sequencer.getSequence(drvStr);
        return BitConverter.hexToBytes(sequence.getAuthID());
    }

    /**
     * Import the device authorization file.
     *
     * @param filePath The filepath to the authorization file.
     * @throws Exception
     */
    public static async importAuthFile(filePath: string): Promise<void> {
        let drive: SalmonDrive | null = SalmonDriveManager.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");

        let sequence: SalmonSequence = this.#sequencer.getSequence(BitConverter.toHex(driveId));
        if (sequence != null && sequence.getStatus() == Status.Active)
            throw new Error("Device is already authorized");

        let authConfigFile: IRealFile = drive.getRealFile(filePath, false);
        if (authConfigFile == null || !await authConfigFile.exists())
            throw new Error("Could not import file");

        let authConfig: SalmonAuthConfig = await this.getAuthConfig(authConfigFile);


        if (!authConfig.getAuthID().every((val, index) => val === SalmonDriveManager.getAuthIDBytes()[index])
            || !authConfig.getDriveID().every((val, index) => driveId != null && val == driveId[index])
        )
            throw new Error("Auth file doesn't match driveID or authID");

        SalmonDriveManager.#importSequence(authConfig);
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
        let drive: SalmonDrive | null = SalmonDriveManager.getDrive();
        if (drive == null)
            throw new Error("No drive opened");
        let driveId: Uint8Array | null = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");


        let cfgNonce: Uint8Array = this.#sequencer.nextNonce(BitConverter.toHex(driveId));

        let sequence: SalmonSequence = this.#sequencer.getSequence(BitConverter.toHex(driveId));
        if (sequence == null)
            throw new Error("Device is not authorized to export");
        let dir: IRealFile = drive.getRealFile(targetDir, true);
        let targetAppDriveConfigFile: IRealFile | null = await dir.getChild(filename);
        if (targetAppDriveConfigFile == null || !await targetAppDriveConfigFile.exists())
            targetAppDriveConfigFile = await dir.createFile(filename);
        else if (targetAppDriveConfigFile != null && await targetAppDriveConfigFile.exists())
            throw new SalmonAuthException(filename + " already exists, delete this file or choose another directory");

        let pivotNonce: Uint8Array = SalmonNonce.splitNonceRange(sequence.getNextNonce(), sequence.getMaxNonce());
        this.#sequencer.setMaxNonce(sequence.getDriveID(), sequence.getAuthID(), pivotNonce);
        await SalmonAuthConfig.writeAuthFile(targetAppDriveConfigFile, drive,
            BitConverter.hexToBytes(targetAuthID),
            pivotNonce, sequence.getMaxNonce(),
            cfgNonce);
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
        this.#sequencer.createSequence(drvStr, authStr);
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
        this.#sequencer.initSequence(drvStr, authStr, startingNonce, maxNonce);
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
        if (SalmonDriveManager.#drive == null)
            throw new Error("No drive opened");
        let driveID: Uint8Array | null = SalmonDriveManager.#drive.getDriveID();
        if (driveID == null)
            throw new Error("Could not get revoke, make sure you initialize the drive first");
        this.#sequencer.revokeSequence(BitConverter.toHex(driveID));
    }

    /**
     * Verify the authentication id with the current drive auth id.
     *
     * @param authID The authentication id to verify.
     * @return
     * @throws Exception
     */
    static #verifyAuthID(authID: Uint8Array): boolean {
        return authID.every((val, index) => val === SalmonDriveManager.getAuthIDBytes()[index]);
    }

    /**
     * Import sequence into the current drive.
     *
     * @param authConfig
     * @throws Exception
     */
    static #importSequence(authConfig: SalmonAuthConfig): void {
        let drvStr: string = BitConverter.toHex(authConfig.getDriveID());
        let authStr: string = BitConverter.toHex(authConfig.getAuthID());
        this.#sequencer.initSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
    }

    /**
     * Get the app drive pair configuration properties for this drive
     *
     * @param authFile The encrypted authentication file.
     * @return The decrypted authentication file.
     * @throws Exception
     */
    public static async getAuthConfig(authFile: IRealFile): Promise<SalmonAuthConfig> {
        let drive: SalmonDrive | null = SalmonDriveManager.getDrive();
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
        if (!this.#verifyAuthID(driveConfig.getAuthID()))
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


    /**
     * Create a configuration file for the drive.
     *
     * @param password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */
    //TODO: partial refactor to SalmonDriveConfig
    static async #createConfig(drive: SalmonDrive, password: string): Promise<void> {
        let key: SalmonKey | null = drive.getKey();
        if (key == null)
            throw new Error("Cannot create config, no key found, make sure you init the drive first");
        let driveKey: Uint8Array | null = key.getDriveKey();
        let hashKey: Uint8Array | null = key.getHashKey();
        let realRoot: IRealFile | null = drive.getRealRoot();
        if (realRoot == null)
            throw new Error("Cannot create config, no root found, make sure you init the drive first");
        let configFile: IRealFile | null = await realRoot.getChild(SalmonDrive.getConfigFilename());

        // if it's an existing config that we need to update with
        // the new password then we prefer to be authenticate
        // TODO: we should probably call Authenticate() rather than assume
        //  that the key != null. Though the user can anyway manually delete the config file
        //  so it doesn't matter.
        if (driveKey == null && configFile != null && await configFile.exists())
            throw new SalmonAuthException("Not authenticated");

        // delete the old config file and create a new one
        if (configFile != null && await configFile.exists())
            await configFile.delete();
        configFile = await realRoot.createFile(SalmonDrive.getConfigFilename());

        let magicBytes: Uint8Array = SalmonGenerator.getMagicBytes();

        let version: number = SalmonGenerator.getVersion();

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        let newDrive: boolean = false;
        if (driveKey == null) {
            newDrive = true;
            driveKey = new Uint8Array(SalmonGenerator.KEY_LENGTH);
            hashKey = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
            let combKey: Uint8Array = SalmonDriveGenerator.generateCombinedKey();
            for (let i = 0; i < SalmonGenerator.KEY_LENGTH; i++)
                driveKey[i] = combKey[i];
            for (let i = 0; i < SalmonGenerator.HASH_KEY_LENGTH; i++)
                driveKey[i] = combKey[SalmonGenerator.KEY_LENGTH + i];
            drive.setDriveID(SalmonDriveGenerator.generateDriveID());
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        let salt: Uint8Array = SalmonDriveGenerator.generateSalt();

        let iterations: number = SalmonDriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        let masterKeyIv: Uint8Array = SalmonDriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        let masterKey: Uint8Array = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

        let driveID: Uint8Array | null = drive.getDriveID();
        if (driveKey == null || hashKey == null || driveID == null)
            throw new Error("Make sure you init the drive first");
        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        let ms: MemoryStream = new MemoryStream();
        let stream: SalmonStream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
            null, false, null, null);
        await stream.write(driveKey, 0, driveKey.length);
        await stream.write(hashKey, 0, hashKey.length);
        await stream.write(driveID, 0, driveID.length);
        await stream.flush();
        await stream.close();
        let encData: Uint8Array = ms.toArray();

        // generate the hash signature
        let hashSignature: Uint8Array = await SalmonIntegrity.calculateHash(drive.getHashProvider(), encData, 0, encData.length, hashKey, null);

        await SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
            encData, hashSignature);
        drive.setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            let authID: Uint8Array = SalmonDriveGenerator.generateAuthId();
            SalmonDriveManager.createSequence(driveID, authID);
            SalmonDriveManager.initSequence(driveID, authID);
        }
        await drive.initFS();
    }

    /**
     * Change the user password.
     * @param pass The new password.
     * @throws IOException
     * @throws SalmonAuthException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     * @throws SalmonSequenceException
     */
    public static async setPassword(drive: SalmonDrive, pass: string): Promise<void> {
        await SalmonDriveManager.#createConfig(drive, pass);
    }
}
