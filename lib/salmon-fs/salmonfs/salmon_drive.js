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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonDrive_instances, _a, _SalmonDrive_DEFAULT_FILE_CHUNK_SIZE, _SalmonDrive_configFilename, _SalmonDrive_authConfigFilename, _SalmonDrive_virtualDriveDirectoryName, _SalmonDrive_shareDirectoryName, _SalmonDrive_exportDirectoryName, _SalmonDrive_defaultFileChunkSize, _SalmonDrive_key, _SalmonDrive_driveID, _SalmonDrive_realRoot, _SalmonDrive_virtualRoot, _SalmonDrive_hashProvider, _SalmonDrive_sequencer, _SalmonDrive_registerOnProcessClose, _SalmonDrive_verifyHash, _SalmonDrive_getDriveConfigFile, _SalmonDrive_createDriveInstance, _SalmonDrive_verifyAuthID, _SalmonDrive_importSequence, _SalmonDrive_createConfig;
import { HmacSHA256Provider } from "../../salmon-core/salmon/integrity/hmac_sha256_provider.js";
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { SalmonKey } from "./salmon_key.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveConfig } from "./salmon_drive_config.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { SalmonStream } from "../../salmon-core/salmon/io/salmon_stream.js";
import { EncryptionMode } from "../../salmon-core/salmon/io/encryption_mode.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonPassword } from "../../salmon-core/salmon/password/salmon_password.js";
import { VirtualDrive } from "../file/virtual_drive.js";
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
import { Status } from "../sequence/salmon_sequence.js";
import { SalmonAuthConfig } from "./salmon_auth_config.js";
import { SalmonSequenceException } from "../sequence/salmon_sequence_exception.js";
import { SalmonNonce } from "../../salmon-core/salmon/salmon_nonce.js";
import { SalmonFile } from "./salmon_file.js";
/**
 * Class provides an abstract virtual drive that can be extended for use with
 * any filesystem ie disk, net, cloud, etc.
 * Drive implementations needs to be realized together with {@link IRealFile}.
 */
export class SalmonDrive extends VirtualDrive {
    constructor() {
        super(...arguments);
        _SalmonDrive_instances.add(this);
        _SalmonDrive_defaultFileChunkSize.set(this, __classPrivateFieldGet(_a, _a, "f", _SalmonDrive_DEFAULT_FILE_CHUNK_SIZE));
        _SalmonDrive_key.set(this, null);
        _SalmonDrive_driveID.set(this, null);
        _SalmonDrive_realRoot.set(this, null);
        _SalmonDrive_virtualRoot.set(this, null);
        _SalmonDrive_hashProvider.set(this, new HmacSHA256Provider());
        _SalmonDrive_sequencer.set(this, null);
    }
    async initialize(realRoot, createIfNotExists) {
        this.lock();
        if (realRoot == null)
            return;
        __classPrivateFieldSet(this, _SalmonDrive_realRoot, realRoot, "f");
        let parent = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getParent();
        if (parent != null && !createIfNotExists && !await this.hasConfig() && await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getParent() != null && await parent.exists()) {
            // try the parent if this is the filesystem folder 
            let originalRealRoot = __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f");
            __classPrivateFieldSet(this, _SalmonDrive_realRoot, parent, "f");
            if (!await this.hasConfig()) {
                // revert to original
                __classPrivateFieldSet(this, _SalmonDrive_realRoot, originalRealRoot, "f");
            }
        }
        if (__classPrivateFieldGet(this, _SalmonDrive_realRoot, "f") == null)
            throw new Error("COuld not initialize root folder");
        let virtualRootRealFile = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getChild(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_virtualDriveDirectoryName));
        if (createIfNotExists && (virtualRootRealFile == null || !await virtualRootRealFile.exists())) {
            virtualRootRealFile = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").createDirectory(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_virtualDriveDirectoryName));
        }
        if (virtualRootRealFile == null)
            throw new Error("Could not create directory for the virtual file system");
        __classPrivateFieldSet(this, _SalmonDrive_virtualRoot, this.createVirtualRoot(virtualRootRealFile), "f");
        __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_registerOnProcessClose).call(this);
        __classPrivateFieldSet(this, _SalmonDrive_key, new SalmonKey(), "f");
    }
    static getConfigFilename() {
        return __classPrivateFieldGet(this, _a, "f", _SalmonDrive_configFilename);
    }
    static setConfigFilename(configFilename) {
        __classPrivateFieldSet(_a, _a, configFilename, "f", _SalmonDrive_configFilename);
    }
    static getAuthConfigFilename() {
        return __classPrivateFieldGet(this, _a, "f", _SalmonDrive_authConfigFilename);
    }
    static setAuthConfigFilename(authConfigFilename) {
        __classPrivateFieldSet(_a, _a, authConfigFilename, "f", _SalmonDrive_authConfigFilename);
    }
    static getVirtualDriveDirectoryName() {
        return __classPrivateFieldGet(this, _a, "f", _SalmonDrive_virtualDriveDirectoryName);
    }
    static setVirtualDriveDirectoryName(virtualDriveDirectoryName) {
        __classPrivateFieldSet(_a, _a, virtualDriveDirectoryName, "f", _SalmonDrive_virtualDriveDirectoryName);
    }
    static getExportDirectoryName() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonDrive_exportDirectoryName);
    }
    static setExportDirectoryName(exportDirectoryName) {
        __classPrivateFieldSet(_a, _a, exportDirectoryName, "f", _SalmonDrive_exportDirectoryName);
    }
    static getShareDirectoryName() {
        return __classPrivateFieldGet(this, _a, "f", _SalmonDrive_shareDirectoryName);
    }
    static setShareDirectoryName(shareDirectoryName) {
        __classPrivateFieldSet(_a, _a, shareDirectoryName, "f", _SalmonDrive_shareDirectoryName);
    }
    /**
     * Return the default file chunk size
     * @return The default chunk size.
     */
    getDefaultFileChunkSize() {
        return __classPrivateFieldGet(this, _SalmonDrive_defaultFileChunkSize, "f");
    }
    /**
     * Set the default file chunk size to be used with hash integrity.
     * @param fileChunkSize
     */
    setDefaultFileChunkSize(fileChunkSize) {
        __classPrivateFieldSet(this, _SalmonDrive_defaultFileChunkSize, fileChunkSize, "f");
    }
    /**
     * Return the encryption key that is used for encryption / decryption
     * @return
     */
    getKey() {
        return __classPrivateFieldGet(this, _SalmonDrive_key, "f");
    }
    /**
     * Return the virtual root directory of the drive.
     * @return
     * @throws SalmonAuthException
     */
    async getVirtualRoot() {
        if (__classPrivateFieldGet(this, _SalmonDrive_realRoot, "f") == null || !await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").exists())
            return null;
        if (!this.isUnlocked())
            throw new SalmonAuthException("Not authorized");
        if (__classPrivateFieldGet(this, _SalmonDrive_virtualRoot, "f") == null)
            throw new SalmonSecurityException("No virtual root, make sure you init the drive first");
        return __classPrivateFieldGet(this, _SalmonDrive_virtualRoot, "f");
    }
    getRealRoot() {
        return __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f");
    }
    /**
     * Verify if the user password is correct otherwise it throws a SalmonAuthException
     *
     * @param password The password.
     */
    async unlock(password) {
        let stream = null;
        try {
            if (password == null) {
                throw new SalmonSecurityException("Password is missing");
            }
            let salmonConfig = await this.getDriveConfig();
            if (salmonConfig == null)
                throw new SalmonSecurityException("Could not get drive config");
            let iterations = salmonConfig.getIterations();
            let salt = salmonConfig.getSalt();
            // derive the master key from the text password
            let masterKey = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);
            // get the master Key Iv
            let masterKeyIv = salmonConfig.getIv();
            // get the encrypted combined key and drive id
            let encData = salmonConfig.getEncryptedData();
            // decrypt the combined key (drive key + hash key) using the master key
            let ms = new MemoryStream(encData);
            stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms, null, false, null, null);
            let driveKey = new Uint8Array(SalmonGenerator.KEY_LENGTH);
            await stream.read(driveKey, 0, driveKey.length);
            let hashKey = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
            await stream.read(hashKey, 0, hashKey.length);
            let driveID = new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH);
            await stream.read(driveID, 0, driveID.length);
            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            await __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_verifyHash).call(this, salmonConfig, encData, hashKey);
            // set the combined key (drive key + hash key) and the drive nonce
            this.setKey(masterKey, driveKey, hashKey, iterations);
            __classPrivateFieldSet(this, _SalmonDrive_driveID, driveID, "f");
            await this.initFS();
            this.onUnlockSuccess();
        }
        catch (ex) {
            this.onUnlockError();
            throw ex;
        }
        finally {
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
    setKey(masterKey, driveKey, hashKey, iterations) {
        if (__classPrivateFieldGet(this, _SalmonDrive_key, "f") == null)
            throw new Error("You need to init the drive first");
        __classPrivateFieldGet(this, _SalmonDrive_key, "f").setMasterKey(masterKey);
        __classPrivateFieldGet(this, _SalmonDrive_key, "f").setDriveKey(driveKey);
        __classPrivateFieldGet(this, _SalmonDrive_key, "f").setHashKey(hashKey);
        __classPrivateFieldGet(this, _SalmonDrive_key, "f").setIterations(iterations);
    }
    /**
     * Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
     * @return
     * @throws Exception
     */
    async getNextNonce() {
        if (__classPrivateFieldGet(this, _SalmonDrive_sequencer, "f") == null)
            throw new SalmonAuthException("No sequencer found use setSequencer");
        if (!this.isUnlocked())
            throw new SalmonAuthException("Not authenticated");
        let driveId = this.getDriveID();
        if (driveId == null)
            throw new SalmonSecurityException("Could not get drive Id");
        return await __classPrivateFieldGet(this, _SalmonDrive_sequencer, "f").nextNonce(BitConverter.toHex(driveId));
    }
    /**
     * Returns true if password authorization has succeeded.
     */
    isUnlocked() {
        let key = this.getKey();
        if (key == null)
            return false;
        let encKey = key.getDriveKey();
        return encKey != null;
    }
    /**
     * Get the byte contents of a file from the real filesystem.
     *
     * @param sourcePath The path of the file
     * @param bufferSize The buffer to be used when reading
     */
    async getBytesFromRealFile(file, bufferSize) {
        let stream = await file.getInputStream();
        let ms = new MemoryStream();
        await stream.copyTo(ms, bufferSize, null);
        await ms.flush();
        await ms.setPosition(0);
        let byteContents = ms.toArray();
        await ms.close();
        await stream.close();
        return byteContents;
    }
    /**
     * Return the default external export dir that all file can be exported to.
     * @return The file on the real filesystem.
     */
    async getExportDir() {
        if (__classPrivateFieldGet(this, _SalmonDrive_realRoot, "f") == null)
            throw new SalmonSecurityException("Cannot export, make sure you init the drive first");
        let virtualThumbnailsRealDir = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getChild(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_exportDirectoryName));
        if (virtualThumbnailsRealDir == null || !await virtualThumbnailsRealDir.exists())
            virtualThumbnailsRealDir = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").createDirectory(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_exportDirectoryName));
        return virtualThumbnailsRealDir;
    }
    /**
     * Return the configuration properties of this drive.
     */
    async getDriveConfig() {
        let configFile = await __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_getDriveConfigFile).call(this);
        if (configFile == null || !await configFile.exists())
            return null;
        let bytes = await this.getBytesFromRealFile(configFile, 0);
        let driveConfig = new SalmonDriveConfig();
        await driveConfig.init(bytes);
        return driveConfig;
    }
    /**
     * Return true if the drive is already created and has a configuration file.
     */
    async hasConfig() {
        let salmonConfig = null;
        try {
            salmonConfig = await this.getDriveConfig();
        }
        catch (ex) {
            console.error(ex);
            return false;
        }
        return salmonConfig != null;
    }
    /**
     * Get the drive ID.
     * @return
     */
    getDriveID() {
        return __classPrivateFieldGet(this, _SalmonDrive_driveID, "f");
    }
    /**
     * Lock the drive and close associated resources.
     */
    lock() {
        __classPrivateFieldSet(this, _SalmonDrive_realRoot, null, "f");
        __classPrivateFieldSet(this, _SalmonDrive_virtualRoot, null, "f");
        __classPrivateFieldSet(this, _SalmonDrive_driveID, null, "f");
        if (__classPrivateFieldGet(this, _SalmonDrive_key, "f") != null)
            __classPrivateFieldGet(this, _SalmonDrive_key, "f").clear();
        __classPrivateFieldSet(this, _SalmonDrive_key, null, "f");
    }
    /**
     * Initialize the drive virtual filesystem.
     */
    async initFS() {
        if (__classPrivateFieldGet(this, _SalmonDrive_realRoot, "f") == null)
            throw new SalmonSecurityException("Could not initialize virtual file system, make sure you run init first");
        let virtualRootRealFile = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getChild(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_virtualDriveDirectoryName));
        if (virtualRootRealFile == null || !await virtualRootRealFile.exists()) {
            try {
                virtualRootRealFile = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").createDirectory(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_virtualDriveDirectoryName));
            }
            catch (ex) {
                console.error(ex);
            }
        }
        if (virtualRootRealFile != null)
            __classPrivateFieldSet(this, _SalmonDrive_virtualRoot, this.createVirtualRoot(virtualRootRealFile), "f");
    }
    getHashProvider() {
        return __classPrivateFieldGet(this, _SalmonDrive_hashProvider, "f");
    }
    setDriveID(driveID) {
        __classPrivateFieldSet(this, _SalmonDrive_driveID, driveID, "f");
    }
    /**
     * Set the drive location to an external directory.
     * This requires you previously use SetDriveClass() to provide a class for the drive
     *
     * @param dirPath The directory path that will be used for storing the contents of the drive
     */
    static async openDrive(dir, driveClassType, sequencer = null) {
        let drive = await __classPrivateFieldGet(_a, _a, "m", _SalmonDrive_createDriveInstance).call(_a, dir, false, driveClassType, sequencer);
        if (!await drive.hasConfig()) {
            throw new Error("Drive does not exist");
        }
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
    static async createDrive(dir, driveClassType, sequencer, password) {
        let drive = await __classPrivateFieldGet(_a, _a, "m", _SalmonDrive_createDriveInstance).call(_a, dir, true, driveClassType, sequencer);
        if (await drive.hasConfig())
            throw new SalmonSecurityException("Drive already exists");
        await drive.setPassword(password);
        return drive;
    }
    /**
     * Get the device authorization byte array for the current drive.
     *
     * @return
     * @throws Exception
     */
    async getAuthIDBytes() {
        let driveId = this.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let drvStr = BitConverter.toHex(driveId);
        let sequence = await this.getSequencer().getSequence(drvStr);
        if (sequence == null) {
            let authID = SalmonDriveGenerator.generateAuthId();
            await this.createSequence(driveId, authID);
        }
        sequence = await this.getSequencer().getSequence(drvStr);
        if (sequence == null)
            throw new Error("Could not get sequence");
        let authId = sequence.getAuthID();
        if (authId == null)
            throw new Error("Could not get auth id");
        return BitConverter.hexToBytes(authId);
    }
    /**
     * Import the device authorization file.
     *
     * @param filePath The filepath to the authorization file.
     * @throws Exception
     */
    async importAuthFile(authConfigFile) {
        let driveId = this.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let sequence = await this.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence != null && sequence.getStatus() == Status.Active)
            throw new Error("Device is already authorized");
        if (authConfigFile == null || !await authConfigFile.exists())
            throw new Error("Could not import file");
        let authConfig = await this.getAuthConfig(authConfigFile);
        let authIdBytes = await this.getAuthIDBytes();
        if (!authConfig.getAuthID().every((val, index) => val === authIdBytes[index])
            || !authConfig.getDriveID().every((val, index) => driveId != null && val == driveId[index]))
            throw new Error("Auth file doesn't match driveID or authID");
        await __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_importSequence).call(this, authConfig);
    }
    /**
     * Get the default auth config filename.
     *
     * @return
     */
    static getDefaultAuthConfigFilename() {
        return _a.getAuthConfigFilename();
    }
    /**
     * @param targetAuthID The authorization id of the target device.
     * @param targetDir    The target dir the file will be written to.
     * @param filename     The filename of the auth config file.
     * @throws Exception
     */
    async exportAuthFile(targetAuthID, file) {
        let driveId = this.getDriveID();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let cfgNonce = await this.getSequencer().nextNonce(BitConverter.toHex(driveId));
        if (cfgNonce == null)
            throw new Error("Could not get config nonce");
        let sequence = await this.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence == null)
            throw new Error("Device is not authorized to export");
        if (await file.exists() && await file.length() > 0) {
            let outStream = null;
            try {
                outStream = await file.getOutputStream();
                outStream.setLength(0);
            }
            catch (ex) {
            }
            finally {
                if (outStream != null)
                    outStream.close();
            }
        }
        let maxNonce = sequence.getMaxNonce();
        if (maxNonce == null)
            throw new SalmonSequenceException("Could not get current max nonce");
        let nextNonce = sequence.getNextNonce();
        if (nextNonce == null)
            throw new SalmonSequenceException("Could not get next nonce");
        let pivotNonce = SalmonNonce.splitNonceRange(nextNonce, maxNonce);
        let authID = sequence.getAuthID();
        if (authID == null)
            throw new SalmonSequenceException("Could not get auth id");
        await this.getSequencer().setMaxNonce(sequence.getDriveID(), authID, pivotNonce);
        await SalmonAuthConfig.writeAuthFile(file, this, BitConverter.hexToBytes(targetAuthID), pivotNonce, maxNonce, cfgNonce);
    }
    /**
     * Create a nonce sequence for the drive id and the authorization id provided. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID The driveID
     * @param authID  The authID
     * @throws Exception
     */
    async createSequence(driveID, authID) {
        let drvStr = BitConverter.toHex(driveID);
        let authStr = BitConverter.toHex(authID);
        await this.getSequencer().createSequence(drvStr, authStr);
    }
    /**
     * Initialize the nonce sequencer with the current drive nonce range. Should be called
     * once per driveID/authID combination.
     *
     * @param driveID Drive ID.
     * @param authID  Authorization ID.
     * @throws Exception
     */
    async initSequence(driveID, authID) {
        let startingNonce = SalmonDriveGenerator.getStartingNonce();
        let maxNonce = SalmonDriveGenerator.getMaxNonce();
        let drvStr = BitConverter.toHex(driveID);
        let authStr = BitConverter.toHex(authID);
        await this.getSequencer().initSequence(drvStr, authStr, startingNonce, maxNonce);
    }
    /**
     * Revoke authorization for this device. This will effectively terminate write operations on the current disk
     * by the current device. Warning: If you need to authorize write operations to the device again you will need
     * to have another device to export an authorization config file and reimport it.
     *
     * @throws Exception
     * @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
     */
    async revokeAuthorization() {
        let driveID = this.getDriveID();
        if (driveID == null)
            throw new Error("Could not get revoke, make sure you initialize the drive first");
        await this.getSequencer().revokeSequence(BitConverter.toHex(driveID));
    }
    /**
     * Get the app drive pair configuration properties for this drive
     *
     * @param authFile The encrypted authorization file.
     * @return The decrypted authorization file.
     * @throws Exception
     */
    async getAuthConfig(authFile) {
        let salmonFile = new SalmonFile(authFile, this);
        let stream = await salmonFile.getInputStream();
        let ms = new MemoryStream();
        await stream.copyTo(ms);
        await ms.close();
        await stream.close();
        let driveConfig = new SalmonAuthConfig();
        await driveConfig.init(ms.toArray());
        if (!await __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_verifyAuthID).call(this, driveConfig.getAuthID()))
            throw new SalmonSecurityException("Could not authorize this device, the authorization id does not match");
        return driveConfig;
    }
    /**
     * Get the authorization ID for the current device.
     *
     * @return
     * @throws SalmonSequenceException
     * @throws SalmonAuthException
     */
    async getAuthID() {
        return BitConverter.toHex(await this.getAuthIDBytes());
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
    async setPassword(pass) {
        await __classPrivateFieldGet(this, _SalmonDrive_instances, "m", _SalmonDrive_createConfig).call(this, pass);
    }
    /**
     * Get the nonce sequencer used for the current drive.
     *
     * @return
     */
    getSequencer() {
        if (__classPrivateFieldGet(this, _SalmonDrive_sequencer, "f") == null)
            throw new Error("Could not find a sequencer");
        return __classPrivateFieldGet(this, _SalmonDrive_sequencer, "f");
    }
}
_a = SalmonDrive, _SalmonDrive_defaultFileChunkSize = new WeakMap(), _SalmonDrive_key = new WeakMap(), _SalmonDrive_driveID = new WeakMap(), _SalmonDrive_realRoot = new WeakMap(), _SalmonDrive_virtualRoot = new WeakMap(), _SalmonDrive_hashProvider = new WeakMap(), _SalmonDrive_sequencer = new WeakMap(), _SalmonDrive_instances = new WeakSet(), _SalmonDrive_registerOnProcessClose = function _SalmonDrive_registerOnProcessClose() {
    // TODO: exec close() on exit
}, _SalmonDrive_verifyHash = 
/**
 * Verify that the hash signature is correct
 *
 * @param salmonConfig
 * @param data
 * @param hashKey
 */
async function _SalmonDrive_verifyHash(salmonConfig, data, hashKey) {
    let hashSignature = salmonConfig.getHashSignature();
    let hash = await SalmonIntegrity.calculateHash(__classPrivateFieldGet(this, _SalmonDrive_hashProvider, "f"), data, 0, data.length, hashKey, null);
    for (let i = 0; i < hashKey.length; i++)
        if (hashSignature[i] != hash[i])
            throw new SalmonAuthException("Could not authenticate");
}, _SalmonDrive_getDriveConfigFile = 
/**
 * Return the drive configuration file.
 */
async function _SalmonDrive_getDriveConfigFile() {
    if (__classPrivateFieldGet(this, _SalmonDrive_realRoot, "f") == null || !await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").exists())
        return null;
    let file = await __classPrivateFieldGet(this, _SalmonDrive_realRoot, "f").getChild(__classPrivateFieldGet(_a, _a, "f", _SalmonDrive_configFilename));
    return file;
}, _SalmonDrive_createDriveInstance = async function _SalmonDrive_createDriveInstance(dir, createIfNotExists, driveClassType, sequencer = null) {
    try {
        let drive = new driveClassType;
        await drive.initialize(dir, createIfNotExists);
        __classPrivateFieldSet(drive, _SalmonDrive_sequencer, sequencer, "f");
        if (__classPrivateFieldGet(drive, _SalmonDrive_sequencer, "f") != null)
            __classPrivateFieldGet(drive, _SalmonDrive_sequencer, "f").initialize();
        return drive;
    }
    catch (e) {
        console.error(e);
        throw new SalmonSecurityException("Could not create drive instance", e);
    }
}, _SalmonDrive_verifyAuthID = 
/**
 * Verify the authorization id with the current drive auth id.
 *
 * @param authID The authorization id to verify.
 * @return
 * @throws Exception
 */
async function _SalmonDrive_verifyAuthID(authID) {
    let authIdBytes = await this.getAuthIDBytes();
    return authID.every(async (val, index) => val === authIdBytes[index]);
}, _SalmonDrive_importSequence = 
/**
 * Import sequence into the current drive.
 *
 * @param authConfig
 * @throws Exception
 */
async function _SalmonDrive_importSequence(authConfig) {
    let drvStr = BitConverter.toHex(authConfig.getDriveID());
    let authStr = BitConverter.toHex(authConfig.getAuthID());
    await this.getSequencer().initSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
}, _SalmonDrive_createConfig = 
/**
 * Create a configuration file for the drive.
 *
 * @param password The new password to be saved in the configuration
 *                 This password will be used to derive the master key that will be used to
 *                 encrypt the combined key (encryption key + hash key)
 */
//TODO: partial refactor to SalmonDriveConfig
async function _SalmonDrive_createConfig(password) {
    let key = this.getKey();
    if (key == null)
        throw new Error("Cannot create config, no key found, make sure you init the drive first");
    let driveKey = key.getDriveKey();
    let hashKey = key.getHashKey();
    let realRoot = this.getRealRoot();
    if (realRoot == null)
        throw new Error("Cannot create config, no root found, make sure you init the drive first");
    let configFile = await realRoot.getChild(_a.getConfigFilename());
    if (driveKey == null && configFile != null && await configFile.exists())
        throw new SalmonAuthException("Not authenticated");
    // delete the old config file and create a new one
    if (configFile != null && await configFile.exists())
        await configFile.delete();
    configFile = await realRoot.createFile(_a.getConfigFilename());
    let magicBytes = SalmonGenerator.getMagicBytes();
    let version = SalmonGenerator.getVersion();
    // if this is a new config file derive a 512-bit key that will be split to:
    // a) drive encryption key (for encrypting filenames and files)
    // b) hash key for file integrity
    let newDrive = false;
    if (driveKey == null) {
        newDrive = true;
        driveKey = new Uint8Array(SalmonGenerator.KEY_LENGTH);
        hashKey = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
        let combKey = SalmonDriveGenerator.generateCombinedKey();
        for (let i = 0; i < SalmonGenerator.KEY_LENGTH; i++)
            driveKey[i] = combKey[i];
        for (let i = 0; i < SalmonGenerator.HASH_KEY_LENGTH; i++)
            driveKey[i] = combKey[SalmonGenerator.KEY_LENGTH + i];
        this.setDriveID(SalmonDriveGenerator.generateDriveID());
    }
    // Get the salt that we will use to encrypt the combined key (drive key + hash key)
    let salt = SalmonDriveGenerator.generateSalt();
    let iterations = SalmonDriveGenerator.getIterations();
    // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
    let masterKeyIv = SalmonDriveGenerator.generateMasterKeyIV();
    // create a key that will encrypt both the (drive key and the hash key)
    let masterKey = await SalmonPassword.getMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);
    let driveID = this.getDriveID();
    if (driveKey == null || hashKey == null || driveID == null)
        throw new Error("Make sure you init the drive first");
    // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
    let ms = new MemoryStream();
    let stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms, null, false, null, null);
    await stream.write(driveKey, 0, driveKey.length);
    await stream.write(hashKey, 0, hashKey.length);
    await stream.write(driveID, 0, driveID.length);
    await stream.flush();
    await stream.close();
    let encData = ms.toArray();
    // generate the hash signature
    let hashSignature = await SalmonIntegrity.calculateHash(this.getHashProvider(), encData, 0, encData.length, hashKey, null);
    await SalmonDriveConfig.writeDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv, encData, hashSignature);
    this.setKey(masterKey, driveKey, hashKey, iterations);
    if (newDrive) {
        // create a full sequence for nonces
        let authID = SalmonDriveGenerator.generateAuthId();
        await this.createSequence(driveID, authID);
        await this.initSequence(driveID, authID);
    }
    await this.initFS();
};
_SalmonDrive_DEFAULT_FILE_CHUNK_SIZE = { value: 256 * 1024 };
_SalmonDrive_configFilename = { value: "vault.slmn" };
_SalmonDrive_authConfigFilename = { value: "auth.slma" };
_SalmonDrive_virtualDriveDirectoryName = { value: "fs" };
_SalmonDrive_shareDirectoryName = { value: "share" };
_SalmonDrive_exportDirectoryName = { value: "export" };
