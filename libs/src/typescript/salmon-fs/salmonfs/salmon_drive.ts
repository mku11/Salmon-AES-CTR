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

import { HmacSHA256Provider } from "../../salmon-core/salmon/integrity/hmac_sha256_provider.js";
import { IHashProvider } from "../../salmon-core/salmon/integrity/ihash_provider.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { IRealFile } from "../file/ireal_file.js";
import { SalmonFile } from "./salmon_file.js";
import { SalmonKey } from "./salmon_key.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveConfig } from "./salmon_drive_config.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { EncryptionMode } from "../../salmon-core/salmon/io/encryption_mode.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { RandomAccessStream } from "../../salmon-core/io/random_access_stream.js";
import { SalmonDriveManager } from "./salmon_drive_manager.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonPassword } from "../../salmon-core/salmon/password/salmon_password.js";

/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Drive implementations needs to be realized together with {@link IRealFile}.
 */
export abstract class SalmonDrive {
    private static readonly DEFAULT_FILE_CHUNK_SIZE: number = 256 * 1024;

    private static configFilename: string = "vault.slmn";
    private static authConfigFilename: string = "auth.slma";
    private static virtualDriveDirectoryName: string = "fs";
    private static shareDirectoryName: string = "share";
    private static exportDirectoryName: string = "export";

    private defaultFileChunkSize: number = SalmonDrive.DEFAULT_FILE_CHUNK_SIZE;
    private key: SalmonKey | null = null;
    private driveID: Uint8Array | null = null;
    private realRoot: IRealFile | null = null;
    private virtualRoot: SalmonFile | null = null;

    private readonly hashProvider: IHashProvider = new HmacSHA256Provider();

    /**
     * Create a virtual drive at the directory path provided
     *
     * @param realRootPath The path of the real directory
     * @param createIfNotExists Create the drive if it does not exist
     */
    public constructor() {

    }

    public async init(realRootPath: string, createIfNotExists: boolean): Promise<void> {
        this.close();
        if (realRootPath == null)
            return;
        this.realRoot = this.getRealFile(realRootPath, true);
        if (!createIfNotExists && !this.hasConfig() && this.realRoot.getParent() != null && await (await this.realRoot.getParent()).exists()) {
            // try the parent if this is the filesystem folder 
            let originalRealRoot: IRealFile = this.realRoot;
            this.realRoot = await this.realRoot.getParent();
            if (!this.hasConfig()) {
                // revert to original
                this.realRoot = originalRealRoot;
            }
        }

        let virtualRootRealFile: IRealFile | null = await this.realRoot.getChild(SalmonDrive.virtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !virtualRootRealFile.exists())) {
            virtualRootRealFile = await this.realRoot.createDirectory(SalmonDrive.virtualDriveDirectoryName);
        }
        if (virtualRootRealFile == null)
            throw new Error("Could not create directory for the virtual file system");
        this.virtualRoot = this.createVirtualRoot(virtualRootRealFile);
        this.registerOnProcessClose();
        this.key = new SalmonKey();
    }

    /**
     * Get a file or directory from the current real filesystem. Used internally
     * for accessing files from the real filesystem.
     * @param filepath
     * @param isDirectory True if filepath corresponds to a directory.
     * @return
     */
    public abstract getRealFile(filepath: string, isDirectory: boolean): IRealFile;

    /**
     * Method is called when the user is authenticated
     */
    protected abstract onAuthenticationSuccess(): void;

    /**
     * Method is called when the user authentication has failed
     */
    protected abstract onAuthenticationError(): void;

    public static getConfigFilename(): string {
        return this.configFilename;
    }

    public static setConfigFilename(configFilename: string) {
        SalmonDrive.configFilename = configFilename;
    }

    public static getAuthConfigFilename(): string {
        return this.authConfigFilename;
    }

    public static setAuthConfigFilename(authConfigFilename: string): void {
        SalmonDrive.authConfigFilename = authConfigFilename;
    }

    public static getVirtualDriveDirectoryName(): string {
        return this.virtualDriveDirectoryName;
    }

    public static setVirtualDriveDirectoryName(virtualDriveDirectoryName: string): void {
        SalmonDrive.virtualDriveDirectoryName = virtualDriveDirectoryName;
    }

    public static getExportDirectoryName(): string {
        return SalmonDrive.exportDirectoryName;
    }

    public static setExportDirectoryName(exportDirectoryName: string): void {
        SalmonDrive.exportDirectoryName = exportDirectoryName;
    }

    public static getShareDirectoryName(): string {
        return this.shareDirectoryName;
    }

    public static setShareDirectoryName(shareDirectoryName: string): void {
        SalmonDrive.shareDirectoryName = shareDirectoryName;
    }

    /**
     * Clear sensitive information when app is close.
     */
    private registerOnProcessClose(): void {
        // TODO: exec close() on exit
    }

    /**
     * Return the default file chunk size
     * @return The default chunk size.
     */
    public getDefaultFileChunkSize(): number {
        return this.defaultFileChunkSize;
    }

    /**
     * Set the default file chunk size to be used with hash integrity.
     * @param fileChunkSize
     */
    public setDefaultFileChunkSize(fileChunkSize: number): void {
        this.defaultFileChunkSize = fileChunkSize;
    }

    /**
     * Return the encryption key that is used for encryption / decryption
     * @return
     */
    public getKey(): SalmonKey | null {
        return this.key;
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
    public async setPassword(pass: string): Promise<void> {
        await this.createConfig(pass);
    }

    /**
     * Initialize the drive virtual filesystem.
     */
    protected async initFS(): Promise<void> {
        if (this.realRoot == null)
            throw new SalmonSecurityException("Could not initialize virtual file system, make sure you run init first");
        let virtualRootRealFile: IRealFile | null = await this.realRoot.getChild(SalmonDrive.virtualDriveDirectoryName);
        if (virtualRootRealFile == null || !virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = await this.realRoot.createDirectory(SalmonDrive.virtualDriveDirectoryName);
            } catch (ex) {
                console.error(ex);
            }
        }
        if (virtualRootRealFile != null)
            this.virtualRoot = this.createVirtualRoot(virtualRootRealFile);
    }

    protected createVirtualRoot(virtualRootRealFile: IRealFile): SalmonFile {
        return new SalmonFile(virtualRootRealFile, this);
    }

    /**
     * Create a configuration file for the drive.
     *
     * @param password The new password to be saved in the configuration
     *                 This password will be used to derive the master key that will be used to
     *                 encrypt the combined key (encryption key + hash key)
     */
    //TODO: partial refactor to SalmonDriveConfig
    private async createConfig(password: string): Promise<void> {
        let key: SalmonKey | null = this.getKey();
        if (key == null || this.realRoot == null)
            throw new Error("Cannot create config, make sure you init the drive first");

        let driveKey: Uint8Array | null = key.getDriveKey();
        let hashKey: Uint8Array | null = key.getHashKey();

        let configFile: IRealFile | null = await this.realRoot.getChild(SalmonDrive.configFilename);

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
        configFile = await this.realRoot.createFile(SalmonDrive.configFilename);

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
            this.driveID = SalmonDriveGenerator.generateDriveID();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        let salt: Uint8Array = SalmonDriveGenerator.generateSalt();

        let iterations: number = SalmonDriveGenerator.getIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        let masterKeyIv: Uint8Array = SalmonDriveGenerator.generateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        let masterKey: Uint8Array = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

        if (driveKey == null || hashKey == null || this.driveID == null)
            throw new Error("Make sure you init the drive first");
        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        let ms: MemoryStream = new MemoryStream();
        let stream: SalmonStream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
            null, false, null, null);
        await stream.write(driveKey, 0, driveKey.length);
        await stream.write(hashKey, 0, hashKey.length);
        await stream.write(this.driveID, 0, this.driveID.length);
        await stream.flush();
        await stream.close();
        let encData: Uint8Array = ms.toArray();

        // generate the hash signature
        let hashSignature: Uint8Array = await SalmonIntegrity.calculateHash(this.hashProvider, encData, 0, encData.length, hashKey, null);

        await SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
            encData, hashSignature);
        this.setKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive) {
            // create a full sequence for nonces
            let authID: Uint8Array = SalmonDriveGenerator.generateAuthId();
            SalmonDriveManager.createSequence(this.driveID, authID);
            SalmonDriveManager.initSequence(this.driveID, authID);
        }
        await this.initFS();
    }

    /**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException
     */
    public getVirtualRoot(): SalmonFile | null {
        if (this.realRoot == null || !this.realRoot.exists())
            return null;
        if (!this.isAuthenticated())
            throw new SalmonAuthException("Not authenticated");
        if (this.virtualRoot == null)
            throw new SalmonSecurityException("No virtual root, make sure you init the drive first");
        return this.virtualRoot;
    }

    protected getRealRoot(): IRealFile | null {
        return this.realRoot;
    }

    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    public async authenticate(password: string): Promise<void> {
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

            let driveID: Uint8Array = new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH);
            await stream.read(driveID, 0, driveID.length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            await this.verifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            this.setKey(masterKey, driveKey, hashKey, iterations);
            this.driveID = driveID;
            await this.initFS();
            this.onAuthenticationSuccess();
        } catch (ex) {
            this.onAuthenticationError();
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
     * @param iterations
     */
    private setKey(masterKey: Uint8Array, driveKey: Uint8Array, hashKey: Uint8Array, iterations: number): void {
        if (this.key == null)
            throw new Error("You need to init the drive first");
        this.key.setMasterKey(masterKey);
        this.key.setDriveKey(driveKey);
        this.key.setHashKey(hashKey);
        this.key.setIterations(iterations);
    }

    /**
     * Verify that the hash signature is correct
     *
     * @param salmonConfig
     * @param data
     * @param hashKey
     */
    private async verifyHash(salmonConfig: SalmonDriveConfig, data: Uint8Array, hashKey: Uint8Array): Promise<void> {
        let hashSignature: Uint8Array = salmonConfig.getHashSignature();
        let hash: Uint8Array = await SalmonIntegrity.calculateHash(this.hashProvider, data, 0, data.length, hashKey, null);
        for (let i = 0; i < hashKey.length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Could not authenticate");
    }

    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @return
     * @throws Exception
     */
    getNextNonce(): Uint8Array {
        if (!this.isAuthenticated())
            throw new SalmonAuthException("Not authenticated");
        return SalmonDriveManager.getNextNonce(this);
    }

    /**
     * Returns true if password authentication has succeeded.
     */
    public isAuthenticated(): boolean {
        let key: SalmonKey | null = this.getKey();
        if (key == null)
            return false;
        let encKey: Uint8Array | null = key.getDriveKey();
        return encKey != null;
    }

    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param sourcePath The path of the file
     * @param bufferSize The buffer to be used when reading
     */
    public async getBytesFromRealFile(sourcePath: string, bufferSize: number): Promise<Uint8Array> {
        let file: IRealFile = this.getRealFile(sourcePath, false);
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
    private async getDriveConfigFile(): Promise<IRealFile | null> {
        if (this.realRoot == null || !this.realRoot.exists())
            return null;
        let file: IRealFile | null = await this.realRoot.getChild(SalmonDrive.configFilename);
        return file;
    }

    /**
     * Return the default external export dir that all file can be exported to.
     * @return The file on the real filesystem.
     */
    public async getExportDir(): Promise<IRealFile> {
        if (this.realRoot == null)
            throw new SalmonSecurityException("Cannot export, make sure you init the drive first");
        let virtualThumbnailsRealDir: IRealFile | null = await this.realRoot.getChild(SalmonDrive.exportDirectoryName);
        if (virtualThumbnailsRealDir == null || !virtualThumbnailsRealDir.exists())
            virtualThumbnailsRealDir = await this.realRoot.createDirectory(SalmonDrive.exportDirectoryName);
        return virtualThumbnailsRealDir;
    }

    /**
     * Return the configuration properties of this drive.
     */
    protected async getDriveConfig(): Promise<SalmonDriveConfig | null> {
        let configFile: IRealFile | null = await this.getDriveConfigFile();
        if (configFile == null || !configFile.exists())
            return null;
        let bytes: Uint8Array = await this.getBytesFromRealFile(configFile.getPath(), 0);
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
    public getDriveID(): Uint8Array | null {
        return this.driveID;
    }

    /**
     * Close the drive and associated resources.
     */
    public close(): void {
        this.realRoot = null;
        this.virtualRoot = null;
        this.driveID = null;
        if (this.key != null)
            this.key.clear();
        this.key = null;
    }
}