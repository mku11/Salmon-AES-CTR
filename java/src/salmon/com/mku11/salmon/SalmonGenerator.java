package com.mku11.salmon;
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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.nio.charset.Charset;
import java.security.SecureRandom;

//TODO: support versioned formats for the stream header

/**
 * Utility class to be used with generating secure keys and initial vectors
 */
public class SalmonGenerator {
	private static final String MAGIC_BYTES = "SLM";
    public static final byte VERSION = 2;
    public static final int MAGIC_LENGTH = 3;
    public static final int VERSION_LENGTH = 1;
    public static final int ITERATIONS_LENGTH = 4;


    // should be 16 for AES256 the same as the iv
    public static final int BLOCK_SIZE = 16;
    // length for IV that will be used for encryption and master encryption of the combined key
    public static final int IV_LENGTH = 16;
    // encryption key length for AES256
    public static final int KEY_LENGTH = 32;
    // encryption key length for HMAC256
    public static final int HMAC_KEY_LENGTH = 32;
    // result of the SHA256 should always be 256 bits
    public static final int HMAC_RESULT_LENGTH = 32;
    // combined key is encryption key + HMAC key
    public static final int COMBINED_KEY_LENGTH = KEY_LENGTH + HMAC_KEY_LENGTH;
    // master key to encrypt the combined key we also use AES256
    public static final int MASTER_KEY_LENGTH = 32;
    // salt size
    public static final int SALT_LENGTH = 24;
    // vault nonce size
    public static final int NONCE_LENGTH = 8;

    // drive ID size
    public static final int DRIVE_ID_LENGTH = 16;

    // auth ID size
    public static final int AUTH_ID_SIZE = 16;

    public static final int CHUNKSIZE_LENGTH = 4;

    private static PbkdfType pbkdfType = PbkdfType.Default;


    // iterations for the text derived master key
    private static int iterations = 65536;

    public static byte[] generateDriveID() {
        return getSecureRandomBytes(DRIVE_ID_LENGTH);
    }

    public static byte[] getDefaultVaultNonce() {
        byte[] bytes = new byte[NONCE_LENGTH];
        return bytes;
    }

    public static byte[] getDefaultMaxVaultNonce() {
        return BitConverter.toBytes(Long.MAX_VALUE, 8);
    }

    public static byte[] generateAuthId() {
        return getSecureRandomBytes(AUTH_ID_SIZE);
    }


    public enum PbkdfType {
        Default
    }

    public static void setPbkdfType(PbkdfType pbkdfType) {
        SalmonGenerator.pbkdfType = pbkdfType;
    }

    /**
     * Gets the fixed magic bytes array
     */
    public static byte[] getMagicBytes() {
        return MAGIC_BYTES.getBytes(Charset.defaultCharset());
    }

    /**
     * Returns the Salmon format version
     */
    public static byte getVersion() {
        return VERSION;
    }

    /**
     * Returns the iterations used for deriving the combined key from
     * the text password
     */
    public static int getIterations() {
        return iterations;
    }

    public static void setIterations(int iterations) {
        SalmonGenerator.iterations = iterations;
    }

    /**
     * Returns a secure random byte array
     */
    public static byte[] getSecureRandomBytes(int size) {
        SecureRandom keyrnd = new SecureRandom();
        byte[] bytes = new byte[size];
        keyrnd.nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a secure random combined key (encryption key + HMAC key)
     */
    public static byte[] generateCombinedKey() {
        return getSecureRandomBytes(COMBINED_KEY_LENGTH);
    }

    /**
     * Derives the key from a text password
     *
     * @param pass       The text password to be used
     * @param salt       The salt to be used for the key derivation
     * @param iterations The number of iterations the key derivation alogrithm will use
     */
    public static byte[] getMasterKey(String pass, byte[] salt, int iterations) throws Exception {
        byte[] masterKey = SalmonGenerator.getKeyFromPassword(pass, salt, iterations, MASTER_KEY_LENGTH);
        return masterKey;
    }

    /**
     * Generates a salt
     */
    public static byte[] generateSalt() {
        return getSecureRandomBytes(SALT_LENGTH);
    }

    /**
     * Generates the initial vector used that will be used with the master key to encrypt the combined key
     */
    public static byte[] generateMasterKeyIV() {
        return getSecureRandomBytes(IV_LENGTH);
    }

    /**
     * Function will derive a key from a text password using Pbkdf2 with SHA256
     *
     * @param password    The password that will be used to derive the key
     * @param salt        The salt byte array that will be used together with the password
     * @param iterations  The iterations to be used with Pbkdf2
     * @param outputBytes The number of bytes for the key
     */
    public static byte[] getKeyFromPassword(String password, byte[] salt, int iterations, int outputBytes) throws Exception {
        //PBKDF2WithHmacSHA256 might not available for some devices
        if (pbkdfType == PbkdfType.Default) {
            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, outputBytes * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(keySpec).getEncoded();
        }
        throw new Exception("Unknown PBKDF type");
    }

    /**
     * Increase the sequential NONCE by a value of 1.
     * This implementation assumes that the NONCE length is 8 bytes or less so it can fit in a long.
     *
     * @param vaultNonce
     * @param maxVaultNonce
     * @return
     * @throws Exception
     */
    public static byte[] increaseNonce(byte[] vaultNonce, byte[] maxVaultNonce) throws Exception {
        long nonce = BitConverter.toLong(vaultNonce, 0, NONCE_LENGTH);
        long maxNonce = BitConverter.toLong(maxVaultNonce, 0, NONCE_LENGTH);
        nonce++;
        if (nonce <= 0 || nonce > maxNonce)
            throw new Exception("Cannot import file, vault exceeded maximum nonces");
        return BitConverter.toBytes(nonce, 8);
    }


    /**
     * Returns the middle nonce in the provided range.
     * Note: This assumes the nonce is 8 bytes, if you need to increase the nonce length
     * then the long transient variables will not hold and you will need to
     * override with your own implementation.
     *
     * @param startNonce
     * @param endNonce
     * @return
     */
    public static byte[] splitNonceRange(byte[] startNonce, byte[] endNonce) throws Exception {
        long start = BitConverter.toLong(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        long end = BitConverter.toLong(endNonce, 0, SalmonGenerator.NONCE_LENGTH);
        // we reserve some nonces
        if(end - start < 256)
            throw new Exception("Not enough nonces left");
        return BitConverter.toBytes(start + (end - start) / 2, SalmonGenerator.NONCE_LENGTH);
    }
}

