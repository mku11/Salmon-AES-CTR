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
    private static final byte VERSION = 1;
    private static final int MAGIC_LENGTH = 3;
    private static final int VERSION_LENGTH = 1;
    private static final int ITERATIONS_LENGTH = 4;
    // iterations for the text derived master key
    private static final int ITERATIONS = 65536;
    // should be 16 for AES256 the same as the iv
    private static final int BLOCK_SIZE = 16;
    // length for IV that will be used for encryption and master encryption of the combined key
    private static final int IV_LENGTH = 16;
    // encryption key length for AES256
    private static final int KEY_LENGTH = 32;
    // encryption key length for HMAC256
    private static final int HMAC_KEY_LENGTH = 32;
    // result of the SHA256 should always be 256 bits
    private static final int HMAC_RESULT_LENGTH = 32;
    // combined key is encryption key + HMAC key
    private static final int COMBINED_KEY_LENGTH = KEY_LENGTH + HMAC_KEY_LENGTH;
    // master key to encrypt the combined key we also use AES256
    private static final int MASTER_KEY_LENGTH = 32;
    // salt size
    private static final int SALT_LENGTH = 24;
    // vault nonce size
    private static final int NONCE_LENGTH = 8;
    private static final String MAGIC_BYTES = "SAL";
    private static PbkdfType pbkdfType = PbkdfType.Default;

    public enum PbkdfType {
        Default
    }

    public static void setPbkdfType(PbkdfType pbkdfType) {
        SalmonGenerator.pbkdfType = pbkdfType;
    }

    /**
     * Returns the byte length for the salt that will be used for encrypting the combined key (encryption key + HMAC key)
     
     */
    public static int getSaltLength() {
        return SALT_LENGTH;
    }

    /**
     * Returns the byte length for the combined key (encryption key + HMAC key)
     
     */
    public static int getCombinedKeyLength() {
        return COMBINED_KEY_LENGTH;
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
     * Returns the byte length that will store the version number
     
     */
    public static int getVersionLength() {
        return VERSION_LENGTH;
    }

    /**
     * Returns the byte length of the magic bytes
     
     */
    public static int getMagicBytesLength() {
        return MAGIC_LENGTH;
    }

    /**
     * Returns the byte length of the initial vector
     
     */
    public static int getIvLength() {
        return IV_LENGTH;
    }

    /**
     * Returns the iterations used for deriving the combined key from
     * the text password
     
     */
    public static int getIterations() {
        return ITERATIONS;
    }

    /**
     * Returns the byte length of the iterations that will be stored in the config file
     
     */
    public static int getIterationsLength() {
        return ITERATIONS_LENGTH;
    }

    /**
     * Returns the byte length of the HMAC key that will be stored in the file
     
     */
    public static int getHMACKeyLength() {
        return HMAC_KEY_LENGTH;
    }

    /**
     * Returns the byte length of the encryption key
     
     */
    public static int getKeyLength() {
        return KEY_LENGTH;
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
        return getSecureRandomBytes(getCombinedKeyLength());
    }

    /**
     * Derives the key from a text password
     * @param pass The text password to be used
     * @param salt The salt to be used for the key derivation
     * @param iterations The number of iterations the key derivation alogrithm will use
     
     */
    public static byte[] getMasterKey(String pass, byte[] salt, int iterations) throws Exception {
        byte[] masterKey = SalmonGenerator.getKeyFromPassword(pass, salt, iterations, getMasterKeyLength());
        return masterKey;
    }

    /**
     * Return the length of the master key in bytes
     
     */
    public static int getMasterKeyLength() {
        return MASTER_KEY_LENGTH;
    }

    /**
     * Generates a salt
     
     */
    public static byte[] generateSalt() {
        return getSecureRandomBytes(getSaltLength());
    }

    /**
     * Generates the initial vector used that will be used with the master key to encrypt the combined key
     
     */
    public static byte[] generateMasterKeyIV() {
        return getSecureRandomBytes(getIvLength());
    }

    /**
     * Function will derive a key from a text password using Pbkdf2 with SHA256
     * @param password The password that will be used to derive the key
     * @param salt The salt byte array that will be used together with the password
     * @param iterations The iterations to be used with Pbkdf2
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
     * Returns the HMAC signature length
     
     */
    public static int getHmacResultLength() {
        return HMAC_RESULT_LENGTH;
    }

    /**
     * Returns the HMAC signature length
     
     */
    public static int getBlockSize() {
        return BLOCK_SIZE;
    }

    /**
     * Returns the Vault Nonce Length
     
     */
    public static int getNonceLength() {
        return NONCE_LENGTH;
    }

}

