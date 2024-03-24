var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _a, _SalmonDriveGenerator_iterations;
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
import { BitConverter } from "../../salmon-core/convert/bit_converter.js";
/**
 * Utility class generates internal secure properties for the drive.
 */
export class SalmonDriveGenerator {
    /**
     * Generate a Drive ID.
     * @return The Drive ID.
     */
    static generateDriveID() {
        return SalmonGenerator.getSecureRandomBytes(_a.DRIVE_ID_LENGTH);
    }
    /**
     * Generate a secure random authorization ID.
     * @return The authorization Id (16 bytes).
     */
    static generateAuthId() {
        return SalmonGenerator.getSecureRandomBytes(_a.AUTH_ID_SIZE);
    }
    /**
     * Generates a secure random combined key (drive key + hash key)
     * @return The length of the combined key.
     */
    static generateCombinedKey() {
        return SalmonGenerator.getSecureRandomBytes(_a.COMBINED_KEY_LENGTH);
    }
    /**
     * Generates the initial vector that will be used with the master key to encrypt the combined key (drive key + hash key)
     */
    static generateMasterKeyIV() {
        return SalmonGenerator.getSecureRandomBytes(_a.IV_LENGTH);
    }
    /**
     * Generates a salt.
     * @return The salt byte array.
     */
    static generateSalt() {
        return SalmonGenerator.getSecureRandomBytes(_a.SALT_LENGTH);
    }
    /**
     * Get the starting nonce that will be used for encrypt drive files and filenames.
     * @return A secure random byte array (8 bytes).
     */
    static getStartingNonce() {
        let bytes = new Uint8Array(SalmonGenerator.NONCE_LENGTH);
        return bytes;
    }
    /**
     * Get the default max nonce to be used for drives.
     * @return A secure random byte array (8 bytes).
     */
    static getMaxNonce() {
        return BitConverter.toBytes(Number.MAX_SAFE_INTEGER, 8);
    }
    /**
     * Returns the iterations used for deriving the combined key from
     * the text password
     * @return The current iterations for the key derivation.
     */
    static getIterations() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonDriveGenerator_iterations);
    }
    /**
     * Set the default iterations.
     * @param iterations
     */
    static setIterations(iterations) {
        __classPrivateFieldSet(_a, _a, iterations, "f", _SalmonDriveGenerator_iterations);
    }
}
_a = SalmonDriveGenerator;
/**
 * Initial vector length that will be used for encryption and master encryption of the combined key
 */
SalmonDriveGenerator.IV_LENGTH = 16;
/**
 * combined key is drive key + hash key.
 */
SalmonDriveGenerator.COMBINED_KEY_LENGTH = SalmonGenerator.KEY_LENGTH + SalmonGenerator.HASH_KEY_LENGTH;
/**
 * Salt length.
 */
SalmonDriveGenerator.SALT_LENGTH = 24;
/**
 * Drive ID size.
 */
SalmonDriveGenerator.DRIVE_ID_LENGTH = 16;
/**
 * Auth ID size
 */
SalmonDriveGenerator.AUTH_ID_SIZE = 16;
/**
 * Length for the iterations that will be stored in the encrypted data header.
 */
SalmonDriveGenerator.ITERATIONS_LENGTH = 4;
/**
 * Master key to encrypt the combined key we also use AES256.
 */
SalmonDriveGenerator.MASTER_KEY_LENGTH = 32;
/**
 * Global default iterations that will be used for the master key derivation.
 */
_SalmonDriveGenerator_iterations = { value: 65536 };
