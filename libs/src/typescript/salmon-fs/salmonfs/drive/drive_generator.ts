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
import { Generator } from "../../../salmon-core/salmon/generator.js";
import { BitConverter } from "../../../salmon-core/convert/bit_converter.js";

/**
 * Utility class generates internal secure properties for the drive.
 */
export class DriveGenerator {
    /**
     * Initial vector length that will be used for encryption and master encryption of the combined key
     */
    public static readonly IV_LENGTH: number = 16;
    /**
     * combined key is drive key + hash key.
     */
    public static readonly COMBINED_KEY_LENGTH: number = Generator.KEY_LENGTH + Generator.HASH_KEY_LENGTH;
    /**
     * Salt length.
     */
    public static readonly SALT_LENGTH: number = 24;
    /**
     * Drive ID size.
     */
    public static readonly DRIVE_ID_LENGTH: number = 16;
    /**
     * Auth ID size
     */
    public static readonly AUTH_ID_SIZE: number = 16;
    /**
     * Length for the iterations that will be stored in the encrypted data header.
     */
    public static readonly ITERATIONS_LENGTH: number = 4;
    /**
     * Master key to encrypt the combined key we also use AES256.
     */
    public static readonly MASTER_KEY_LENGTH: number = 32;

    static #iterations: number = 65536;

    /**
     * Generate a Drive ID.
     * @return {Uint8Array} The Drive ID.
     */
    public static generateDriveID(): Uint8Array {
        return Generator.getSecureRandomBytes(DriveGenerator.DRIVE_ID_LENGTH);
    }

    /**
     * Generate a secure random authorization ID.
     * @return {Uint8Array} The authorization Id (16 bytes).
     */
    public static generateAuthId(): Uint8Array {
        return Generator.getSecureRandomBytes(DriveGenerator.AUTH_ID_SIZE);
    }

    /**
     * Generates a secure random combined key (drive key + hash key)
     * @return {Uint8Array} The length of the combined key.
     */
    public static generateCombinedKey(): Uint8Array {
        return Generator.getSecureRandomBytes(DriveGenerator.COMBINED_KEY_LENGTH);
    }

    /**
     * Generates the initial vector that will be used with the master key to encrypt the combined key (drive key + hash key)
     * @returns {Uint8Array} The master key initial vector
     */
    public static generateMasterKeyIV(): Uint8Array {
        return Generator.getSecureRandomBytes(DriveGenerator.IV_LENGTH);
    }

    /**
     * Generates a salt.
     * @return {Uint8Array} The salt byte array.
     */
    public static generateSalt(): Uint8Array {
        return Generator.getSecureRandomBytes(DriveGenerator.SALT_LENGTH);
    }

    /**
     * Get the starting nonce that will be used for encrypt drive files and filenames.
     * @return {Uint8Array} A secure random byte array (8 bytes).
     */
    public static getStartingNonce(): Uint8Array {
        let bytes: Uint8Array = new Uint8Array(Generator.NONCE_LENGTH);
        return bytes;
    }

    /**
     * Get the default max nonce to be used for drives.
     * @return {Uint8Array} A secure random byte array (8 bytes).
     */
    public static getMaxNonce(): Uint8Array {
        return BitConverter.toBytes(Number.MAX_SAFE_INTEGER, 8);
    }

    /**
     * Returns the iterations used for deriving the combined key from
     * the text password
     * @return {number} The current iterations for the key derivation.
     */
    public static getIterations(): number {
        return DriveGenerator.#iterations;
    }

    /**
     * Set the default iterations.
     * @param {number} iterations The iterations
     */
    public static setIterations(iterations: number): void {
        DriveGenerator.#iterations = iterations;
    }
}
