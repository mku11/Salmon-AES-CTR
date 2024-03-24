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
var _SalmonAuthConfig_driveID, _SalmonAuthConfig_authID, _SalmonAuthConfig_startNonce, _SalmonAuthConfig_maxNonce;
import { SalmonGenerator } from "../../salmon-core/salmon/salmon_generator.js";
import { MemoryStream } from "../../salmon-core/io/memory_stream.js";
import { SalmonIntegrity } from "../../salmon-core/salmon/integrity/salmon_integrity.js";
import { SalmonDriveGenerator } from "./salmon_drive_generator.js";
import { SalmonAuthException } from "./salmon_auth_exception.js";
import { SalmonFile } from "./salmon_file.js";
/**
 * Device Authorization Configuration. This represents the authorization that will be provided
 * to the target device to allow writing operations for a virtual drive.
 */
export class SalmonAuthConfig {
    /**
     * Get the drive ID to grant authorization for.
     * @return
     */
    getDriveID() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_driveID, "f");
    }
    /**
     * Get the authorization ID for the target device.
     * @return
     */
    getAuthID() {
        return __classPrivateFieldGet(this, _SalmonAuthConfig_authID, "f");
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
        _SalmonAuthConfig_driveID.set(this, new Uint8Array(SalmonDriveGenerator.DRIVE_ID_LENGTH));
        _SalmonAuthConfig_authID.set(this, new Uint8Array(SalmonDriveGenerator.AUTH_ID_SIZE));
        _SalmonAuthConfig_startNonce.set(this, new Uint8Array(SalmonGenerator.NONCE_LENGTH));
        _SalmonAuthConfig_maxNonce.set(this, new Uint8Array(SalmonGenerator.NONCE_LENGTH));
    }
    async init(contents) {
        let ms = new MemoryStream(contents);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_driveID, "f"), 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_authID, "f"), 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_startNonce, "f"), 0, SalmonGenerator.NONCE_LENGTH);
        await ms.read(__classPrivateFieldGet(this, _SalmonAuthConfig_maxNonce, "f"), 0, SalmonGenerator.NONCE_LENGTH);
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
    static async writeAuthFile(authConfigFile, drive, targetAuthID, targetStartingNonce, targetMaxNonce, configNonce) {
        let driveId = drive.getDriveID();
        if (driveId == null)
            throw new Error("Could not write auth file, no drive id found");
        let salmonFile = new SalmonFile(authConfigFile, drive);
        let stream = await salmonFile.getOutputStream(configNonce);
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
    static async writeToStream(stream, driveID, authID, nextNonce, maxNonce) {
        let ms = new MemoryStream();
        try {
            await ms.write(driveID, 0, driveID.length);
            await ms.write(authID, 0, authID.length);
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
    }
}
_SalmonAuthConfig_driveID = new WeakMap(), _SalmonAuthConfig_authID = new WeakMap(), _SalmonAuthConfig_startNonce = new WeakMap(), _SalmonAuthConfig_maxNonce = new WeakMap();
