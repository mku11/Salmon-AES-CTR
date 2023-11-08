package com.mku.salmonfs;
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

import com.mku.convert.BitConverter;
import com.mku.salmon.SalmonGenerator;

/**
 * Utility class generates internal secure properties for the drive.
 */
public class SalmonDriveGenerator {
    /**
     * Initial vector length that will be used for encryption and master encryption of the combined key
     */
    public static final int IV_LENGTH = 16;
    /**
     * combined key is drive key + hash key.
     */
    public static final int COMBINED_KEY_LENGTH = SalmonGenerator.KEY_LENGTH + SalmonGenerator.HASH_KEY_LENGTH;
    /**
     * Salt length.
     */
    public static final int SALT_LENGTH = 24;
    /**
     * Drive ID size.
     */
    public static final int DRIVE_ID_LENGTH = 16;
    /**
     * Auth ID size
     */
    public static final int AUTH_ID_SIZE = 16;
    /**
     * Length for the iterations that will be stored in the encrypted data header.
     */
    public static final int ITERATIONS_LENGTH = 4;
    /**
     * Master key to encrypt the combined key we also use AES256.
     */
    public static final int MASTER_KEY_LENGTH = 32;

    /**
     * Global default iterations that will be used for the master key derivation.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static int iterations = 65536;

    /**
     * Generate a Drive ID.
     * @return The Drive ID.
     */
    public static byte[] generateDriveID() {
        return SalmonGenerator.getSecureRandomBytes(DRIVE_ID_LENGTH);
    }

    /**
     * Generate a secure random authentication ID.
     * @return The authentication Id (16 bytes).
     */
    public static byte[] generateAuthId() {
        return SalmonGenerator.getSecureRandomBytes(AUTH_ID_SIZE);
    }

    /**
     * Generates a secure random combined key (drive key + hash key)
     * @return The length of the combined key.
     */
    public static byte[] generateCombinedKey() {
        return SalmonGenerator.getSecureRandomBytes(COMBINED_KEY_LENGTH);
    }

    /**
     * Generates the initial vector that will be used with the master key to encrypt the combined key (drive key + hash key)
     */
    public static byte[] generateMasterKeyIV() {
        return SalmonGenerator.getSecureRandomBytes(IV_LENGTH);
    }

    /**
     * Generates a salt.
     * @return The salt byte array.
     */
    public static byte[] generateSalt() {
        return SalmonGenerator.getSecureRandomBytes(SALT_LENGTH);
    }

    /**
     * Get the starting nonce that will be used for encrypt drive files and filenames.
     * @return A secure random byte array (8 bytes).
     */
    public static byte[] getStartingNonce() {
        byte[] bytes = new byte[SalmonGenerator.NONCE_LENGTH];
        return bytes;
    }

    /**
     * Get the default max nonce to be used for drives.
     * @return A secure random byte array (8 bytes).
     */
    public static byte[] getMaxNonce() {
        return BitConverter.toBytes(Long.MAX_VALUE, 8);
    }

    /**
     * Returns the iterations used for deriving the combined key from
     * the text password
     * @return The current iterations for the key derivation.
     */
    public static int getIterations() {
        return iterations;
    }

    /**
     * Set the default iterations.
     * @param iterations
     */
    public static void setIterations(int iterations) {
        SalmonDriveGenerator.iterations = iterations;
    }

}
