package com.mku.salmon;
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

import java.nio.charset.Charset;
import java.security.SecureRandom;

/**
 * Utility class that generates secure random byte arrays.
 */
public class Generator {
    /**
     * Version.
     */
    public static final byte VERSION = 2;

    /**
     * Lenght for the magic bytes.
     */
    public static final int MAGIC_LENGTH = 3;

    /**
     * Length for the Version in the data header.
     */
    public static final int VERSION_LENGTH = 1;

    /**
     * Should be 16 for AES256 the same as the iv.
     */
    public static final int BLOCK_SIZE = 16;

    /**
     * Encryption key length for AES256.
     */
    public static final int KEY_LENGTH = 32;

    /**
     * HASH Key length for integrity, currently we use HMAC SHA256.
     */
    public static final int HASH_KEY_LENGTH = 32;

    /**
     * Hash signature size for integrity, currently we use HMAC SHA256.
     */
    public static final int HASH_RESULT_LENGTH = 32;

    /**
     * Nonce size.
     */
    public static final int NONCE_LENGTH = 8;

    /**
     * Chunk size format length.
     */
    public static final int CHUNK_SIZE_LENGTH = 4;

    /**
     * Magic bytes.
     */
    private static final String MAGIC_BYTES = "SLM";

    /**
     * Gets the fixed magic bytes array
     * @return The magic bytes
     */
    public static byte[] getMagicBytes() {
        return MAGIC_BYTES.getBytes(Charset.defaultCharset());
    }

    /**
     * Returns the current Salmon format version.
     * @return The version number
     */
    public static byte getVersion() {
        return VERSION;
    }

    /**
     * Returns a secure random byte array. To be used when generating keys, initial vectors, and nonces.
     * @param size The size of the byte array.
     * @return The random secure byte array.
     */
    public static byte[] getSecureRandomBytes(int size) {
        SecureRandom sRandom = new SecureRandom();
        byte[] bytes = new byte[size];
        sRandom.nextBytes(bytes);
        return bytes;
    }
}

