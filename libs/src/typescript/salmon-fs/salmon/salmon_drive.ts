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

import { HmacSHA256Provider } from "../../salmon-core/integrity/hmac_sha256_provider.js";
import { IHashProvider } from "../../salmon-core/integrity/ihash_provider.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonDriveKey } from "./salmon_drive_key.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveConfig } from "./salmon_drive_config.js";
import { MemoryStream } from "../../salmon-core/streams/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/streams/salmon_stream.js";
import { EncryptionMode } from "../../salmon-core/salmon/streams/encryption_mode.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { RandomAccessStream } from "../../salmon-core/streams/random_access_stream.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonPassword } from "../../salmon-core/salmon/password/salmon_password.js";
import { VirtualDrive } from "../drive/virtual_drive.js";
import { INonceSequencer } from "../sequence/inonce_sequencer.js";
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { NonceSequence } from "../sequence/nonce_sequence.js";
import { IVirtualFile } from "../file/ivirtual_file.js";

/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Each drive implementation needs a corresponding implementation of {@link IRealFile}.
 */
export abstract class SalmonDrive extends VirtualDrive {
    static readonly #DEFAULT_FILE_CHUNK_SIZE: number = 256 * 1024;

    static #configFilename: string = "vault.slmn";
    static #authConfigFilename: string = "auth.slma";
    static #virtualDriveDirectoryName: string = "fs";
    static #shareDirectoryName: string = "share";
    static #exportDirectoryName: string = "export";

    #defaultFileChunkSize: number = SalmonDrive.#DEFAULT_FILE_CHUNK_SIZE;
    #key: SalmonDriveKey | null = null;
    #driveId: Uint8Array | null = null;
    #realRoot: IRealFile | null = null;
    #virtualRoot: IVirtualFile | null = null;

    readonly #hashProvider: IHashProvider = new HmacSHA256Provider();
    #sequencer: INonceSequencer | null = null;

    public async initialize(realRoot: IRealFile, createIfNotExists: boolean): Promise<void> {
        this.close();
        if (realRoot == null)
            return;
        this.#realRoot = realRoot;
        let parent: IRealFile | null = await this.#realRoot.getParent();
        if (parent != null && !createIfNotExists && ! await this.hasConfig() && await this.#realRoot.getParent() != null && await parent.exists()) {
            // try the parent if this is the filesystem folder 
            let originalRealRoot: IRealFile = this.#realRoot;
            this.#realRoot = parent;
            if (! await this.hasConfig()) {
                // revert to original
                this.#realRoot = originalRealRoot;
            }
        }
        if (this.#realRoot == null)
            throw new Error("Could not initialize root folder");

        let virtualRootRealFile: IRealFile | null = await this.#realRoot.getChild(SalmonDrive.#virtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !await virtualRootRealFile.exists())) {
            virtualRootRealFile = await this.#realRoot.createDirectory(SalmonDrive.#virtualDriveDirectoryName);
        }
        if (virtualRootRealFile == null)
            throw new Error("Could not create directory for the virtual file system");

        this.#virtualRoot = this.getFile(virtualRootRealFile);
        this.#registerOnProcessClose();
        this.#key = new SalmonDriveKey();
    }

    public static getConfigFilename(): string {
        return this.#configFilename;
    }

    public static setConfigFilename(configFilename: string) {
        SalmonDrive.#configFilename = configFilename;
    }

    public static getAuthConfigFilename(): string {
        return this.#authConfigFilename;
    }

    public static setAuthConfigFilename(authConfigFilename: string): void {
        SalmonDrive.#authConfigFilename = authConfigFilename;
    }

    public static getVirtualDriveDirectoryName(): string {
        return this.#virtualDriveDirectoryName;
    }

    public static setVirtualDriveDirectoryName(virtualDriveDirectoryName: string): void {
        SalmonDrive.#virtualDriveDirectoryName = virtualDriveDirectoryName;
    }

    public static getExportDirectoryName(): string {
        return SalmonDrive.#exportDirectoryName;
    }

    public static setExportDirectoryName(exportDirectoryName: string): void {
        SalmonDrive.#exportDirectoryName = exportDirectoryName;
    }

    public static getShareDirectoryName(): string {
        return this.#shareDirectoryName;
    }

    public static setShareDirectoryName(shareDirectoryName: string): void {
        SalmonDrive.#shareDirectoryName = shareDirectoryName;
    }

    /**
     * Clear sensitive information when app is close.
     */
    #registerOnProcessClose(): void {
        // TODO: exec close() on exit
    }

    /**
     * Return the default file chunk size
     * @return The default chunk size.
     */
    public getDefaultFileChunkSize(): number {
        return this.#defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with hash integrity.
     * @param fileChunkSize
     */
    public setDefaultFileChunkSize(fileChunkSize: number): void {
        this.#defaultFileChunkSize = fileChunkSize;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     * @return
     */
    public getKey(): SalmonDriveKey | null {
        return this.#key;
    }

    /**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException Thrown when error during authorization
     */
    public async getRoot(): Promise<IVirtualFile | null> {
        if (this.#realRoot == null || !await this.#realRoot.exists())
            return null;
        if (this.#virtualRoot == null)
            throw new SalmonSecurityException("No virtual root, make sure you init the drive first");
        return this.#virtualRoot;
    }

    public getRealRoot(): IRealFile | null {
        return this.#realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    async #unlock(password: string): Promise<void> {
        let stream: SalmonStream | null = null;
        try {
            if (password == null) {
                throw new SalmonSecurityException("Password is missing");
            }
            let salmonConfig: SalmonDriveConfig | null = await this.getDriveConfig();
            if (salmonConfig == null)
                throw new SalmonSecurityException("Could not get drive config");

            let iterations: number = salmonConfig.getIterations();
            let salt: Uint8Array = salmonConfig.getSalt();

            // derive the master key from the text password
            let masterKey: Uint8Array = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            let masterKeyIv: Uint8Array = salmonConfig.getIv();

            // get the encrypted combined key and drive id
            let encData: Uint8Array = salmonConfig.getEncryptedData();

            // decrypt the combined key (drive key + hash key) using the master key
            let ms: MemoryStream = new MemoryStream(encData);
            stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms,
                null, false, null, null);

            let driveKey: Uint8Array = new Uint8Array(SalmonGenerator.KEY_LENGTH);
            await stream.read(driveKey, 0, driveKey.length);

            let hashKey: Uint8Array = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
            await stream.read(hashKey, 0, hashKey.length);

            let driveId: Uint8Array = new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH);
            await stream.read(driveId, 0, driveId.length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            await this.#verifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            this.setKey(masterKey, driveKey, hashKey, iterations);
            this.#driveId = driveId;
            await this.initFS();
            this.onUnlockSuccess();
        } catch (ex) {
            this.onUnlockError();
            throw ex;
        } finally {
            if (stream != null)
                await stream.close();
        }
    }

    /**
     * Sets the key properties.
     * @param masterKey The master key.
     * @param driveKey The drive key used for enc/dec of files and filenames.
     * @param hashKey The hash key used for data integrity.
     * @param iterations The iterations
     */
    public setKey(masterKey: Uint8Array, driveKey: Uint8Array, hashKey: Uint8Array, iterations: number): void {
        if (this.#key == null)
            throw new Error("You need to init the drive first");
        this.#key.setMasterKey(masterKey);
        this.#key.setDriveKey(driveKey);
        this.#key.setHashKey(hashKey);
        this.#key.setIterations(iterations);
    }

    /**
     * Verify that the hash signature is correct
     *
     * @param salmonConfig The drive configuration
     * @param data The data
     * @param hashKey The hash key
     */
    async #verifyHash(salmonConfig: SalmonDriveConfig, data: Uint8Array, hashKey: Uint8Array): Promise<void> {
        let hashSignature: Uint8Array = salmonConfig.getHashSignature();
        let hash: Uint8Array = await SalmonIntegrity.calculateHash(this.#hashProvider, data, 0, data.length, hashKey, null);
        for (let i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Wrong Password");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @return
     * @throws Exception
     */
    async getNextNonce(): Promise<Uint8Array | null> {
        if (this.#sequencer == null)
            throw new SalmonAuthException("No sequencer found");
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new SalmonSecurityException("Could not get drive Id");
        return await this.#sequencer.nextNonce(BitConverter.toHex(driveId));
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param file The file
     * @param bufferSize The buffer to be used when reading
     */
    public async getBytesFromRealFile(file: IRealFile, bufferSize: number): Promise<Uint8Array> {
        let stream: RandomAccessStream = await file.getInputStream();
        let ms: MemoryStream = new MemoryStream();
        await stream.copyTo(ms, bufferSize, null);
        await ms.flush();
        await ms.setPosition(0);
        let byteContents: Uint8Array = ms.toArray();
        await ms.close();
        await stream.close();
        return byteContents;
    }

    /**
     * Return the drive configuration file.
     */
    async #getDriveConfigFile(): Promise<IRealFile | null> {
        if (this.#realRoot == null || !await this.#realRoot.exists())
            return null;
        let file: IRealFile | null = await this.#realRoot.getChild(SalmonDrive.#configFilename);
        return file;
    }

    /**
     * Return the default external export dir that all file can be exported to.
     * @return The file on the real filesystem.
     */
    public async getExportDir(): Promise<IRealFile> {
        if (this.#realRoot == null)
            throw new SalmonSecurityException("Cannot export, make sure you init the drive first");
        let exportDir: IRealFile | null = await this.#realRoot.getChild(SalmonDrive.#exportDirectoryName);
        if (exportDir == null || !await exportDir.exists())
            exportDir = await this.#realRoot.createDirectory(SalmonDrive.#exportDirectoryName);
        return exportDir;
    }

    /**
     * Return the configuration properties of this drive.
     */
    protected async getDriveConfig(): Promise<SalmonDriveConfig | null> {
        let configFile: IRealFile | null = await this.#getDriveConfigFile();
        if (configFile == null || !await configFile.exists())
            return null;
        let bytes: Uint8Array = await this.getBytesFromRealFile(configFile, 0);
        let driveConfig: SalmonDriveConfig = new SalmonDriveConfig();
        await driveConfig.init(bytes);
        return driveConfig;
    }

    /**
     * Return true if the drive is already created and has a configuration file.
     */
    public async hasConfig(): Promise<boolean> {
        let salmonConfig: SalmonDriveConfig | null = null;
        try {
            salmonConfig = await this.getDriveConfig();
        } catch (ex) {
            console.error(ex);
            return false;
        }
        return salmonConfig != null;
    }

    /**
     * Get the drive ID.
     * @return
     */
    public getDriveId(): Uint8Array | null {
        return this.#driveId;
    }

    /**
     * Close the drive and associated resources.
     */
    public close(): void {
        this.#realRoot = null;
        this.#virtualRoot = null;
        this.#driveId = null;
        if (this.#key != null)
            this.#key.clear();
        this.#key = null;
    }
    
    /**
     * Initialize the drive virtual filesystem.
     */
    public async initFS(): Promise<void> {
        if (this.#realRoot == null)
            throw new SalmonSecurityException("Could not initialize virtual file system, make sure you run init first");
        let virtualRootRealFile: IRealFile | null = await this.#realRoot.getChild(SalmonDrive.#virtualDriveDirectoryName);
        if (virtualRootRealFile == null || !await virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = await this.#realRoot.createDirectory(SalmonDrive.#virtualDriveDirectoryName);
            } catch (ex) {
                console.error(ex);
            }
        }
        if (virtualRootRealFile != null)
            this.#virtualRoot = this.getFile(virtualRootRealFile);
    }

    public getHashProvider(): IHashProvider {
        return this.#hashProvider;
    }
    
    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dir The directory path that will be used for storing the contents of the drive
     * @param driveClassType The driver class type (ie JsDrive).
     * @param password Text password to encrypt the drive configuration.
     * @param sequencer The sequencer to use.
     */
    public static async openDrive(dir: IRealFile, driveClassType: any, password: string, sequencer: INonceSequencer | null = null): Promise<SalmonDrive> {
        let drive: SalmonDrive = await SalmonDrive.#createDriveInstance(dir, false, driveClassType, sequencer);
        if (!await drive.hasConfig()) {
            throw new Error("Drive does not exist");
        }
        await drive.#unlock(password);
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param dir  Directory to store the drive configuration and virtual filesystem.
     * @param driveClassType The driver class type (ie JsDrive).
     * @param password Text password to encrypt the drive configuration.
     * @param sequencer The sequencer to use.
     * @return The newly created drive.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public static async createDrive(dir: IRealFile, driveClassType: any, password: string, sequencer: INonceSequencer): Promise<SalmonDrive> {
        let drive: SalmonDrive = await SalmonDrive.#createDriveInstance(dir, true, driveClassType, sequencer);
        if (await drive.hasConfig())
            throw new SalmonSecurityException("Drive already exists");
        await drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param dirPath The target directory where the drive is located.
     * @param createIfNotExists Create the drive if it does not exist
     * @return
     * @throws SalmonSecurityException Thrown when error with security
     */
    static async #createDriveInstance(dir: IRealFile, createIfNotExists: boolean, 
        driveClassType: any, sequencer: INonceSequencer | null = null): Promise<SalmonDrive> {
        try {
            let drive: SalmonDrive = new driveClassType;
            await drive.initialize(dir, createIfNotExists);
            drive.#sequencer = sequencer;
            if(drive.#sequencer != null)
                await drive.#sequencer.initialize();
            return drive;
        } catch (e) {
            console.error(e);
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
    }

    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    public async getAuthIdBytes(): Promise<Uint8Array> {
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let drvStr: string = BitConverter.toHex(driveId);
        let sequence: NonceSequence | null = await this.getSequencer().getSequence(drvStr);
        if (sequence == null) {
            let authId: Uint8Array = SalmonDriveGenerator.generateAuthId();
            await this.createSequence(driveId, authId);
        }
        sequence = await this.getSequencer().getSequence(drvStr);
        if (sequence == null)
            throw new Error("Could not get sequence");
        let authId: string | null = sequence.getAuthId();
        if (authId == null)
            throw new Error("Could not get auth id");
        return BitConverter.hexToBytes(authId);
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
     * Create a nonce sequence for the drive id and the authorization id provided. Should be called
     * once per driveId/authId combination.
     *
     * @param driveId The driveId
     * @param authId  The authId
     * @throws Exception
     */
    async createSequence(driveId: Uint8Array, authId: Uint8Array): Promise<void> {
        let drvStr: string = BitConverter.toHex(driveId);
        let authStr: string = BitConverter.toHex(authId);
        await this.getSequencer().createSequence(drvStr, authStr);
    }

    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveId/authId combination.
     *
     * @param driveId Drive ID.
     * @param authId  Authorization ID.
     * @throws Exception
     */
    async initializeSequence(driveId: Uint8Array, authId: Uint8Array): Promise<void> {
        let startingNonce: Uint8Array = SalmonDriveGenerator.getStartingNonce();
        let maxNonce: Uint8Array = SalmonDriveGenerator.getMaxNonce();
        let drvStr: string = BitConverter.toHex(driveId);
        let authStr: string = BitConverter.toHex(authId);
        await this.getSequencer().initializeSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public async revokeAuthorization(): Promise<void> {
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new Error("Could not get revoke, make sure you initialize the drive first");
        await this.getSequencer().revokeSequence(BitConverter.toHex(driveId));
    }

    /**
     * Get the authorization ID for the current device.
     *
     * @return
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws SalmonAuthException Thrown when error during authorization
     */
    public async getAuthId(): Promise<string> {
        return BitConverter.toHex(await this.getAuthIdBytes());
    }

    /**
     * Create a configuration file for the drive.
     *
     * @param password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */
    //TODO: partial refactor to SalmonDriveConfig
    async #createConfig(password: string): Promise<void> {
        let key: SalmonDriveKey | null = this.getKey();
        if (key == null)
            throw new Error("Cannot create config, no key found, make sure you init the drive first");
        let driveKey: Uint8Array | null = key.getDriveKey();
        let hashKey: Uint8Array | null = key.getHashKey();
        let realRoot: IRealFile | null = this.getRealRoot();
        if (realRoot == null)
            throw new Error("Cannot create config, no root found, make sure you init the drive first");
        let configFile: IRealFile | null = await realRoot.getChild(SalmonDrive.getConfigFilename());

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
            this.#driveId = SalmonDriveGenerator.generateDriveID();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        let salt: Uint8Array = SalmonDriveGenerator.generateSalt();

        let iterations: number = SalmonDriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        let masterKeyIv: Uint8Array = SalmonDriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        let masterKey: Uint8Array = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

        let driveId: Uint8Array | null = this.getDriveId();
        if (driveKey == null || hashKey == null || driveId == null)
            throw new Error("Make sure you init the drive first");
        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        let ms: MemoryStream = new MemoryStream();
        let stream: SalmonStream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
            null, false, null, null);
        await stream.write(driveKey, 0, driveKey.length);
        await stream.write(hashKey, 0, hashKey.length);
        await stream.write(driveId, 0, driveId.length);
        await stream.flush();
        await stream.close();
        let encData: Uint8Array = ms.toArray();

        // generate the hash signature
        let hashSignature: Uint8Array = await SalmonIntegrity.calculateHash(this.getHashProvider(), encData, 0, encData.length, hashKey, null);

        await SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
            encData, hashSignature);
            this.setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            let authId: Uint8Array = SalmonDriveGenerator.generateAuthId();
            await this.createSequence(driveId, authId);
            await this.initializeSequence(driveId, authId);
        }
        await this.initFS();
    }

    /**
     * Change the user password.
     * @param pass The new password.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonAuthException Thrown when error during authorization
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public async setPassword(pass: string): Promise<void> {
        await this.#createConfig(pass);
    }

    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    public getSequencer(): INonceSequencer {
        if(this.#sequencer == null)
            throw new Error("Could not find a sequencer");
        return this.#sequencer;
    }
}