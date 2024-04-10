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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _a, _SalmonAuthConfig_driveId, _SalmonAuthConfig_authId, _SalmonAuthConfig_startNonce, _SalmonAuthConfig_maxNonce, _SalmonAuthConfig_writeAuthFile, _SalmonAuthConfig_writeToStream, _SalmonAuthConfig_getAuthConfig, _SalmonAuthConfig_verifyAuthId, _SalmonAuthConfig_importSequence;
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { MemoryStream } from "../../salmon-core/streams/memory_stream.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonFile } from "./salmon_file.js";
import { Status } from "../sequence/nonce_sequence.js";
import { SequenceException } from "../sequence/sequence_exception.js";
import { SalmonSecurityException } from "../../salmon-core/salmon/salmon_security_exception.js";
import { SalmonNonce } from "../../salmon-core/salmon/salmon_nonce.js";
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
export class SalmonAuthConfig {
    /**
     * Get the drive ID to grant authorization for.
     * @return
     */
    getDriveId() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_driveId, "f");
    }
    /**
     * Get the authorization ID for the target device.
     * @return
     */
    getAuthId() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_authId, "f");
    }
    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    getStartNonce() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_startNonce, "f");
    }
    /**
     * Get the nonce maximum value the target device will use.
     * @return
     */
    getMaxNonce() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_maxNonce, "f");
    }
    /**
     * Instantiate a class with the properties of the authorization config file.
     * @param contents The byte array that contains the contents of the auth config file.
     */
    constructor() {
        _SalmonAuthConfig_driveId.set(this, new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH));
        _SalmonAuthConfig_authId.set(this, new Uint8Array(SalmonDriveGenerator.AUTH_ID_SIZE));
        _SalmonAuthConfig_startNonce.set(this, new Uint8Array(SalmonGenerator.NONCE_LENGTH));
        _SalmonAuthConfig_maxNonce.set(this, new Uint8Array(SalmonGenerator.NONCE_LENGTH));
    }
    async init(contents) {
        let ms = new MemoryStream(contents);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_driveId, "f"), 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_authId, "f"), 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_startNonce, "f"), 0, SalmonGenerator.NONCE_LENGTH);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_maxNonce, "f"), 0, SalmonGenerator.NONCE_LENGTH);
        await ms.close();
    }
    /**
     * Import the device authorization file.
     *
     * @param authConfigFile The filepath to the authorization file.
     * @throws Exception
     */
    static async importAuthFile(drive, authConfigFile) {
        let driveId = drive.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let sequence = await drive.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence != null && sequence.getStatus() == Status.Active)
            throw new Error("Device is already authorized");
        if (authConfigFile == null || !await authConfigFile.exists())
            throw new Error("Could not import file");
        let authConfig = await __classPrivateFieldGet(_a, _a, "m", _SalmonAuthConfig_getAuthConfig).call(_a, drive, authConfigFile);
        let authIdBytes = await drive.getAuthIdBytes();
        if (!authConfig.getAuthId().every((val, index) => val === authIdBytes[index])
            || !authConfig.getDriveId().every((val, index) => driveId != null && val == driveId[index]))
            throw new Error("Auth file doesn't match driveId or authId");
        await __classPrivateFieldGet(_a, _a, "m", _SalmonAuthConfig_importSequence).call(_a, drive, authConfig);
    }
    /**
     * @param targetAuthId The authorization id of the target device.
     * @param targetDir    The target dir the file will be written to.
     * @param filename     The filename of the auth config file.
     * @throws Exception
     */
    static async exportAuthFile(drive, targetAuthId, file) {
        let driveId = drive.getDriveId();
        if (driveId == null)
            throw new Error("Could not get drive id, make sure you init the drive first");
        let cfgNonce = await drive.getSequencer().nextNonce(BitConverter.toHex(driveId));
        if (cfgNonce == null)
            throw new Error("Could not get config nonce");
        let sequence = await drive.getSequencer().getSequence(BitConverter.toHex(driveId));
        if (sequence == null)
            throw new Error("Device is not authorized to export");
        if (await file.exists() && await file.length() > 0) {
            let outStream = null;
            try {
                outStream = await file.getOutputStream();
                await outStream.setLength(0);
            }
            catch (ex) {
            }
            finally {
                if (outStream != null)
                    await outStream.close();
            }
        }
        let maxNonce = sequence.getMaxNonce();
        if (maxNonce == null)
            throw new SequenceException("Could not get current max nonce");
        let nextNonce = sequence.getNextNonce();
        if (nextNonce == null)
            throw new SequenceException("Could not get next nonce");
        let pivotNonce = SalmonNonce.splitNonceRange(nextNonce, maxNonce);
        let authId = sequence.getAuthId();
        if (authId == null)
            throw new SequenceException("Could not get auth id");
        await drive.getSequencer().setMaxNonce(sequence.getId(), authId, pivotNonce);
        await __classPrivateFieldGet(_a, _a, "m", _SalmonAuthConfig_writeAuthFile).call(_a, file, drive, BitConverter.hexToBytes(targetAuthId), pivotNonce, maxNonce, cfgNonce);
    }
}
_a = SalmonAuthConfig, _SalmonAuthConfig_driveId = new WeakMap(), _SalmonAuthConfig_authId = new WeakMap(), _SalmonAuthConfig_startNonce = new WeakMap(), _SalmonAuthConfig_maxNonce = new WeakMap(), _SalmonAuthConfig_writeAuthFile = async function _SalmonAuthConfig_writeAuthFile(authConfigFile, drive, targetAuthId, targetStartingNonce, targetMaxNonce, configNonce) {
    let driveId = drive.getDriveId();
    if (driveId == null)
        throw new Error("Could not write auth file, no drive id found");
    let salmonFile = new SalmonFile(authConfigFile, drive);
    let stream = await salmonFile.getOutputStream(configNonce);
    await __classPrivateFieldGet(_a, _a, "m", _SalmonAuthConfig_writeToStream).call(_a, stream, driveId, targetAuthId, targetStartingNonce, targetMaxNonce);
}, _SalmonAuthConfig_writeToStream = async function _SalmonAuthConfig_writeToStream(stream, driveId, authId, nextNonce, maxNonce) {
    let ms = new MemoryStream();
    try {
        await ms.write(driveId, 0, driveId.length);
        await ms.write(authId, 0, authId.length);
        await ms.write(nextNonce, 0, nextNonce.length);
        await ms.write(maxNonce, 0, maxNonce.length);
        let content = ms.toArray();
        let buffer = new Uint8Array(SalmonIntegrity.DEFAULT_CHUNK_SIZE);
        for (let i = 0; i < content.length; i++)
            buffer[i] = content[i];
        await stream.write(buffer, 0, content.length);
    }
    catch (ex) {
        console.error(ex);
        throw new SalmonAuthException("Could not write auth config", ex);
    }
    finally {
        await ms.close();
        await stream.flush();
        await stream.close();
    }
}, _SalmonAuthConfig_getAuthConfig = async function _SalmonAuthConfig_getAuthConfig(drive, authFile) {
    let salmonFile = new SalmonFile(authFile, drive);
    let stream = await salmonFile.getInputStream();
    let ms = new MemoryStream();
    await stream.copyTo(ms);
    await ms.close();
    await stream.close();
    let driveConfig = new _a();
    await driveConfig.init(ms.toArray());
    if (!await __classPrivateFieldGet(_a, _a, "m", _SalmonAuthConfig_verifyAuthId).call(_a, drive, driveConfig.getAuthId()))
        throw new SalmonSecurityException("Could not authorize this device, the authorization id does not match");
    return driveConfig;
}, _SalmonAuthConfig_verifyAuthId = async function _SalmonAuthConfig_verifyAuthId(drive, authId) {
    let authIdBytes = await drive.getAuthIdBytes();
    return authId.every(async (val, index) => val === authIdBytes[index]);
}, _SalmonAuthConfig_importSequence = async function _SalmonAuthConfig_importSequence(drive, authConfig) {
    let drvStr = BitConverter.toHex(authConfig.getDriveId());
    let authStr = BitConverter.toHex(authConfig.getAuthId());
    await drive.getSequencer().initializeSequence(drvStr, authStr, authConfig.getStartNonce(), authConfig.getMaxNonce());
};
