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

import { HmacSHA256Provider } from "../../../salmon-core/salmon/integrity/hmac_sha256_provider.js";
import { IHashProvider } from "../../../salmon-core/salmon/integrity/ihash_provider.js";
import { Generator } from "../../../salmon-core/salmon/generator.js";
import { IFile } from "../../fs/file/ifile.js";
import { DriveKey } from "./drive_key.js";
import { DriveGenerator } from "./drive_generator.js";
import { Integrity } from "../../../salmon-core/salmon/integrity/integrity.js";
import { DriveConfig } from "./drive_config.js";
import { MemoryStream } from "../../../salmon-core/streams/memory_stream.js";
import { AesStream } from "../../../salmon-core/salmon/streams/aes_stream.js";
import { EncryptionMode } from "../../../salmon-core/salmon/streams/encryption_mode.js";
import { EncryptionFormat } from "../../../salmon-core/salmon/streams/encryption_format.js";
import { SecurityException } from "../../../salmon-core/salmon/security_exception.js";
import { RandomAccessStream } from "../../../salmon-core/streams/random_access_stream.js";
import { AuthException } from "../auth/auth_exception.js";
import { Password } from "../../../salmon-core/salmon/password/password.js";
import { VirtualDrive } from "../../fs/drive/virtual_drive.js";
import { INonceSequencer } from "../../../salmon-core/salmon/sequence/inonce_sequencer.js";
import { BitConverter } from "../../../salmon-core/convert/bit_converter.js";
import { NonceSequence } from "../../../salmon-core/salmon/sequence/nonce_sequence.js";
import { IVirtualFile } from "../../fs/file/ivirtual_file.js";

/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Each drive implementation needs a corresponding implementation of {@link IFile}.
 */
export abstract class AesDrive extends VirtualDrive {
    static readonly #DEFAULT_FILE_CHUNK_SIZE: number = 256 * 1024;

    static #configFilename: string = "vault.slmn";
    static #authConfigFilename: string = "auth.slma";
    static #virtualDriveDirectoryName: string = "fs";
    static #shareDirectoryName: string = "share";
    static #exportDirectoryName: string = "export";

    #defaultFileChunkSize: number = AesDrive.#DEFAULT_FILE_CHUNK_SIZE;
    #key: DriveKey | null = null;
    #driveId: Uint8Array | null = null;
    #realRoot: IFile | null = null;
    #virtualRoot: IVirtualFile | null = null;

    readonly #hashProvider: IHashProvider = new HmacSHA256Provider();
    #sequencer: INonceSequencer | undefined;

    public async initialize(realRoot: IFile, createIfNotExists: boolean): Promise<void> {
        this.close();
        if (realRoot == null)
            return;
        this.#realRoot = realRoot;
        let parent: IFile | null = await this.#realRoot.getParent();
        if (parent && !createIfNotExists && ! await this.hasConfig() && await this.#realRoot.getParent() && await parent.exists()) {
            // try the parent if this is the filesystem folder 
            let originalRealRoot: IFile = this.#realRoot;
            this.#realRoot = parent;
            if (! await this.hasConfig()) {
                // revert to original
                this.#realRoot = originalRealRoot;
            }
        }
        if (this.#realRoot == null)
            throw new Error("Could not initialize root folder");

        let virtualRootRealFile: IFile | null = await this.#realRoot.getChild(AesDrive.#virtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !await virtualRootRealFile.exists())) {
            virtualRootRealFile = await this.#realRoot.createDirectory(AesDrive.#virtualDriveDirectoryName);
        }
        if (virtualRootRealFile == null)
            throw new Error("Could not create directory for the virtual file system");

        this.#virtualRoot = this.getVirtualFile(virtualRootRealFile);
        this.#registerOnProcessClose();
        this.#key = new DriveKey();
    }

    public static getConfigFilename(): string {
        return this.#configFilename;
    }

    public static setConfigFilename(configFilename: string) {
        AesDrive.#configFilename = configFilename;
    }

    public static getAuthConfigFilename(): string {
        return this.#authConfigFilename;
    }

    public static setAuthConfigFilename(authConfigFilename: string): void {
        AesDrive.#authConfigFilename = authConfigFilename;
    }

    public static getVirtualDriveDirectoryName(): string {
        return this.#virtualDriveDirectoryName;
    }

    public static setVirtualDriveDirectoryName(virtualDriveDirectoryName: string): void {
        AesDrive.#virtualDriveDirectoryName = virtualDriveDirectoryName;
    }

    public static getExportDirectoryName(): string {
        return AesDrive.#exportDirectoryName;
    }

    public static setExportDirectoryName(exportDirectoryName: string): void {
        AesDrive.#exportDirectoryName = exportDirectoryName;
    }

    public static getShareDirectoryName(): string {
        return this.#shareDirectoryName;
    }

    public static setShareDirectoryName(shareDirectoryName: string): void {
        AesDrive.#shareDirectoryName = shareDirectoryName;
    }

    /**
     * Clear sensitive information when app is close.
     */
    #registerOnProcessClose(): void {
        // TODO: exec close() on exit
    }

    /**
     * Return the default file chunk size
     * @returns {number} The default chunk size.
     */
    public getDefaultFileChunkSize(): number {
        return this.#defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with hash integrity.
     * @param {number} fileChunkSize
     */
    public setDefaultFileChunkSize(fileChunkSize: number): void {
        this.#defaultFileChunkSize = fileChunkSize;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     * @returns {DriveKey | null} The drive key
     */
    public getKey(): DriveKey | null {
        return this.#key;
    }

    /**
     * Return the virtual root directory of the drive.
     * @returns {Promise<IVirtualFile | null>} The virtual file
     * @throws SalmonAuthException Thrown when error during authorization
     */
    public async getRoot(): Promise<IVirtualFile | null> {
        if (this.#realRoot == null || !await this.#realRoot.exists())
            return null;
        if (this.#virtualRoot == null)
            throw new SecurityException("No virtual root, make sure you init the drive first");
        return this.#virtualRoot;
    }

    public getRealRoot(): IFile | null {
        return this.#realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param {string} password The password.
     */
    async #unlock(password: string): Promise<void> {
        let stream: AesStream | null = null;
        try {
            if (password == null) {
                throw new SecurityException("Password is missing");
            }
            let salmonConfig: DriveConfig | null = await this.getDriveConfig();
            if (salmonConfig == null)
                throw new SecurityException("Could not get drive config");

            let iterations: number = salmonConfig.getIterations();
            let salt: Uint8Array = salmonConfig.getSalt();

            // derive the master key from the text password
            let masterKey: Uint8Array = await Password.getMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            let masterKeyIv: Uint8Array = salmonConfig.getIv();

            // get the encrypted combined key and drive id
            let encData: Uint8Array = salmonConfig.getEncryptedData();

            // decrypt the combined key (drive key + hash key) using the master key
            let ms: MemoryStream = new MemoryStream(encData);
            stream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms, EncryptionFormat.Generic);

            let driveKey: Uint8Array = new Uint8Array(Generator.KEY_LENGTH);
            await stream.read(driveKey, 0, driveKey.length);

            let hashKey: Uint8Array = new Uint8Array(Generator.HASH_KEY_LENGTH);
            await stream.read(hashKey, 0, hashKey.length);

            let driveId: Uint8Array = new Uint8Array(DriveGenerator.DRIVE_ID_LENGTH);
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
            if (stream)
                await stream.close();
        }
    }

    /**
     * Sets the key properties.
     * @param {Uint8Array} masterKey The master key.
     * @param {Uint8Array} driveKey The drive key used for enc/dec of files and filenames.
     * @param {Uint8Array} hashKey The hash key used for data integrity.
     * @param {number} iterations The iterations
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
     * @param {DriveConfig} salmonConfig The drive configuration
     * @param {Uint8Array} data The data
     * @param {Uint8Array} hashKey The hash key
     */
    async #verifyHash(salmonConfig: DriveConfig, data: Uint8Array, hashKey: Uint8Array): Promise<void> {
        let hashSignature: Uint8Array = salmonConfig.getHashSignature();
        let hash: Uint8Array = await Integrity.calculateHash(this.#hashProvider, data, 0, data.length, hashKey, null);
        for (let i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new AuthException("Wrong password");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @returns {Promise<Uint8Array | null>} The next nonce.
     * @throws Exception
     */
    async getNextNonce(): Promise<Uint8Array | null> {
        if (!this.#sequencer)
            throw new AuthException("No sequencer found");
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new SecurityException("Could not get drive Id");
        return await this.#sequencer.nextNonce(BitConverter.toHex(driveId));
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param {IFile} file The file
     * @param {Uint8Array} bufferSize The buffer to be used when reading
     */
    public async getBytesFromRealFile(file: IFile, bufferSize: number): Promise<Uint8Array> {
        let stream: RandomAccessStream = await file.getInputStream();
        let ms: MemoryStream = new MemoryStream();
        await stream.copyTo(ms, bufferSize);
        await ms.flush();
        await ms.setPosition(0);
        let byteContents: Uint8Array = ms.toArray();
        await ms.close();
        await stream.close();
        return byteContents;
    }

    /**
     * Return the drive configuration file.
     * @returns {Promise<IFile | null>} The file
     */
    async #getDriveConfigFile(): Promise<IFile | null> {
        if (this.#realRoot == null || !await this.#realRoot.exists())
            return null;
        let file: IFile | null = await this.#realRoot.getChild(AesDrive.#configFilename);
        return file;
    }

    /**
     * Return the default external export dir that all file can be exported to.
     * @returns {Promise<IFile>} The file on the real filesystem.
     */
    public async getExportDir(): Promise<IFile> {
        if (this.#realRoot == null)
            throw new SecurityException("Cannot export, make sure you init the drive first");
        let exportDir: IFile | null = await this.#realRoot.getChild(AesDrive.#exportDirectoryName);
        if (exportDir == null || !await exportDir.exists())
            exportDir = await this.#realRoot.createDirectory(AesDrive.#exportDirectoryName);
        return exportDir;
    }

    /**
     * Return the configuration properties of this drive.
     * @returns {Promise<DriveConfig | null>} The configuration
     */
    protected async getDriveConfig(): Promise<DriveConfig | null> {
        let configFile: IFile | null = await this.#getDriveConfigFile();
        if (configFile == null || !await configFile.exists())
            return null;
        let bytes: Uint8Array = await this.getBytesFromRealFile(configFile, 0);
        let driveConfig: DriveConfig = new DriveConfig();
        await driveConfig.init(bytes);
        return driveConfig;
    }

    /**
     * Return true if the drive is already created and has a configuration file.
     * @returns {Promise<boolean>} True if configuration file was found
     */
    public async hasConfig(): Promise<boolean> {
        let salmonConfig: DriveConfig | null = null;
        try {
            salmonConfig = await this.getDriveConfig();
        } catch (ex) {
            console.error(ex);
            return false;
        }
        return salmonConfig != null;
    }

    /**
     * Get the drive id.
     * @returns {Uint8Array | null} The drive id.
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
        if (this.#key)
            this.#key.clear();
        this.#key = null;
    }

    /**
     * Initialize the drive virtual filesystem.
     */
    public async initFS(): Promise<void> {
        if (this.#realRoot == null)
            throw new SecurityException("Could not initialize virtual file system, make sure you run init first");
        let virtualRootRealFile: IFile | null = await this.#realRoot.getChild(AesDrive.#virtualDriveDirectoryName);
        if (virtualRootRealFile == null || !await virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = await this.#realRoot.createDirectory(AesDrive.#virtualDriveDirectoryName);
            } catch (ex) {
                console.error(ex);
            }
        }
        if (virtualRootRealFile)
            this.#virtualRoot = this.getVirtualFile(virtualRootRealFile);
    }

    /**
     * Get the has provider for this drive.
     * @returns {IHashProvider} The hash provider
     */
    public getHashProvider(): IHashProvider {
        return this.#hashProvider;
    }

    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param {IFile} dir The directory path that will be used for storing the contents of the drive
     * @param {any} driveClassType The driver class type (ie Drive).
     * @param {string} password Text password to encrypt the drive configuration.
     * @param {INonceSequencer} [sequencer] The sequencer to use.
     * @returns {Promise<AesDrive>} The drive
     */
    public static async openDrive(dir: IFile, driveClassType: any, password: string, sequencer?: INonceSequencer): Promise<AesDrive> {
        let drive: AesDrive = await AesDrive.#createDriveInstance(dir, false, driveClassType, sequencer);
        if (!await drive.hasConfig()) {
            throw new Error("Drive does not exist");
        }
        await drive.#unlock(password);
        return drive;
    }

    /**
     * Create a new drive in the provided location.
     *
     * @param {IFile} dir  Directory to store the drive configuration and virtual filesystem.
     * @param {any} driveClassType The driver class type (ie Drive).
     * @param {string} password Text password to encrypt the drive configuration.
     * @param {INonceSequencer} sequencer The sequencer to use.
     * @returns {Promise<AesDrive>} The newly created drive.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SequenceException Thrown if error with the nonce sequence
     */
    public static async createDrive(dir: IFile, driveClassType: any, password: string, sequencer: INonceSequencer): Promise<AesDrive> {
        let drive: AesDrive = await AesDrive.#createDriveInstance(dir, true, driveClassType, sequencer);
        if (await drive.hasConfig())
            throw new SecurityException("Drive already exists");
        await drive.setPassword(password);
        return drive;
    }

    /**
     * Create a drive instance.
     *
     * @param {IFile} dir The target directory where the drive is located.
     * @param {boolean} createIfNotExists Create the drive if it does not exist
     * @returns {Promise<AesDrive>} The drive
     * @throws SalmonSecurityException Thrown when error with security
     */
    static async #createDriveInstance(dir: IFile, createIfNotExists: boolean,
        driveClassType: any, sequencer?: INonceSequencer): Promise<AesDrive> {
        try {
            let drive: AesDrive = new driveClassType;
            await drive.initialize(dir, createIfNotExists);
            drive.#sequencer = sequencer;
            if (drive.#sequencer)
                await drive.#sequencer.initialize();
            return drive;
        } catch (e) {
            console.error(e);
            throw new SecurityException("Could not create drive instance", e);
        }
    }

    /**
     * Get the device authorization byte array for the current drive.
     *
     * @returns {Promise<Uint8Array>} The byte array with the auth id
     * @throws Exception If error occurs during retrieval
     */
    public async getAuthIdBytes(): Promise<Uint8Array> {
        if(!this.#sequencer)
            throw new Error("No sequencer defined");
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let drvStr: string = BitConverter.toHex(driveId);
        let sequence: NonceSequence | null = await this.#sequencer.getSequence(drvStr);
        if (sequence == null) {
            let authId: Uint8Array = DriveGenerator.generateAuthId();
            await this.createSequence(driveId, authId);
        }
        sequence = await this.#sequencer.getSequence(drvStr);
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
     * @returns {string} The authorization configuration file name.
     */
    public static getDefaultAuthConfigFilename(): string {
        return AesDrive.getAuthConfigFilename();
    }

    /**
     * Create a nonce sequence for the drive id and the authorization id provided. Should be called
     * once per driveId/authId combination.
     *
     * @param {Uint8Array} driveId The driveId
     * @param {Uint8Array} authId  The authId
     * @throws Exception If error occurs during creation
     */
    async createSequence(driveId: Uint8Array, authId: Uint8Array): Promise<void> {
        if(!this.#sequencer)
            throw new Error("No sequencer defined");
        let drvStr: string = BitConverter.toHex(driveId);
        let authStr: string = BitConverter.toHex(authId);
        await this.#sequencer.createSequence(drvStr, authStr);
    }

    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveId/authId combination.
     *
     * @param {Uint8Array} driveId Drive ID.
     * @param {Uint8Array} authId  Authorization ID.
     * @throws Exception If error occurs during initialization
     */
    async initializeSequence(driveId: Uint8Array, authId: Uint8Array): Promise<void> {
        if(!this.#sequencer)
            throw new Error("No sequencer defined");
        let startingNonce: Uint8Array = DriveGenerator.getStartingNonce();
        let maxNonce: Uint8Array = DriveGenerator.getMaxNonce();
        let drvStr: string = BitConverter.toHex(driveId);
        let authStr: string = BitConverter.toHex(authId);
        await this.#sequencer.initializeSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception If error occurs during revoke.
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    public async revokeAuthorization(): Promise<void> {
        if(!this.#sequencer)
            throw new Error("No sequencer defined");
        let driveId: Uint8Array | null = this.getDriveId();
        if (driveId == null)
            throw new Error("Could not get revoke, make sure you initialize the drive first");
        await this.#sequencer.revokeSequence(BitConverter.toHex(driveId));
    }

    /**
     * Get the authorization ID for the current device.
     *
     * @returns {Promise<string>} The auth id
     * @throws SequenceException Thrown if error with the nonce sequence
     * @throws SalmonAuthException Thrown when error during authorization
     */
    public async getAuthId(): Promise<string> {
        return BitConverter.toHex(await this.getAuthIdBytes());
    }

    /**
     * Create a configuration file for the drive.
     *
     * @param {string} password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */
    //TODO: partial refactor to SalmonDriveConfig
    async #createConfig(password: string): Promise<void> {
        let key: DriveKey | null = this.getKey();
        if (key == null)
            throw new Error("Cannot create config, no key found, make sure you init the drive first");
        let driveKey: Uint8Array | null = key.getDriveKey();
        let hashKey: Uint8Array | null = key.getHashKey();
        let realRoot: IFile | null = this.getRealRoot();
        if (realRoot == null)
            throw new Error("Cannot create config, no root found, make sure you init the drive first");
        let configFile: IFile | null = await this.getConfigFile(realRoot);

        if (driveKey == null && configFile && await configFile.exists())
            throw new AuthException("Not authenticated");

        // delete the old config file and create a new one
        if (configFile && await configFile.exists())
            await configFile.delete();
        configFile = await this.createConfigFile(realRoot);
        if (configFile == null)
            throw new AuthException("Could not crete config file");

        let magicBytes: Uint8Array = Generator.getMagicBytes();

        let version: number = Generator.getVersion();

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        let newDrive: boolean = false;
        if (driveKey == null) {
            newDrive = true;
            driveKey = new Uint8Array(Generator.KEY_LENGTH);
            hashKey = new Uint8Array(Generator.HASH_KEY_LENGTH);
            let combKey: Uint8Array = DriveGenerator.generateCombinedKey();
            for (let i = 0; i < Generator.KEY_LENGTH; i++)
                driveKey[i] = combKey[i];
            for (let i = 0; i < Generator.HASH_KEY_LENGTH; i++)
                driveKey[i] = combKey[Generator.KEY_LENGTH + i];
            this.#driveId = DriveGenerator.generateDriveID();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        let salt: Uint8Array = DriveGenerator.generateSalt();

        let iterations: number = DriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        let masterKeyIv: Uint8Array = DriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        let masterKey: Uint8Array = await Password.getMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

        let driveId: Uint8Array | null = this.getDriveId();
        if (driveKey == null || hashKey == null || driveId == null)
            throw new Error("Make sure you init the drive first");
        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        let ms: MemoryStream = new MemoryStream();
        let stream: AesStream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms, EncryptionFormat.Generic);
        await stream.write(driveKey, 0, driveKey.length);
        await stream.write(hashKey, 0, hashKey.length);
        await stream.write(driveId, 0, driveId.length);
        await stream.flush();
        await stream.close();
        let encData: Uint8Array = ms.toArray();

        // generate the hash signature
        let hashSignature: Uint8Array = await Integrity.calculateHash(this.getHashProvider(), encData, 0, encData.length, hashKey, null);

        await DriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
            encData, hashSignature);
        this.setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            let authId: Uint8Array = DriveGenerator.generateAuthId();
            await this.createSequence(driveId, authId);
            await this.initializeSequence(driveId, authId);
        }
        await this.initFS();
    }

    /**
     * Change the user password.
     * @param {string} pass The new password.
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
     * @returns {INonceSequencer | undefined} The nonce sequencer
     */
    public getSequencer(): INonceSequencer | undefined {
        if (!this.#sequencer)
            throw new Error("Could not find a sequencer");
        return this.#sequencer;
    }

    /**
     * Set the nonce sequencer used for the current drive.
     *
     * @param {INonceSequencer | undefined} sequencer The nonce sequencer
     */
    public setSequencer(sequencer: INonceSequencer | undefined) {
        this.#sequencer = sequencer;
    }

    /**
     * Create the config file for this drive. By default the config file is placed in the real root of the vault.
     * You can override this with your own location, make sure you also override getConfigFile().
     * @param {IFile} realRoot The real root directory of the vault
     * @returns {Promise<IFile>} The config file that was created
     */
    public async createConfigFile(realRoot: IFile): Promise<IFile> {
        let configFile: IFile = await realRoot.createFile(AesDrive.getConfigFilename());
        return configFile;
    }

    /**
     * Get the config file for this drive. By default the config file is placed in the real root of the vault.
     * You can override this with your own location.
     * @param {IFile} realRoot The real root directory of the vault
     * @returns {Promise<IFile | null>} The config file that will be used for this drive.
     */
    public async getConfigFile(realRoot: IFile): Promise<IFile | null> {
        let configFile: IFile | null = await realRoot.getChild(AesDrive.getConfigFilename());
        return configFile;
    }
}