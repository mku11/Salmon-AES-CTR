package com.mku.salmon.text;
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

import com.mku.integrity.IntegrityException;
import com.mku.salmon.SalmonEncryptor;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.encode.SalmonEncoder;

import java.io.IOException;

/**
 * Utility class that encrypts and decrypts text strings.
 */
public class SalmonTextEncryptor {
    private static final SalmonEncryptor encryptor = new SalmonEncryptor();

    /**
     * Encrypts a text String using AES256 with the key and nonce provided.
     *
     * @param text  Text to be encrypted.
     * @param key   The encryption key to be used.
     * @param nonce The nonce to be used.
     * @param header Set to true to store a header with information like nonce and/or chunk size,
     *               otherwise you will have to store that information externally.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @return The encrypted string
     * @throws IOException Thrown if there is an IO error.
     */
    public static String encryptString(String text, byte[] key, byte[] nonce, boolean header)
            throws IOException {
        return encryptString(text, key, nonce, header, false, null, null);
    }

    /**
     * Encrypts a text String using AES256 with the key and nonce provided.
     *
     * @param text  Text to be encrypted.
     * @param key   The encryption key to be used.
     * @param nonce The nonce to be used.
     * @param header Set to true to store a header with information like nonce and/or chunk size,
     *               otherwise you will have to store that information externally.
     * @param integrity True if you want to calculate and store hash signatures for each chunkSize
     * @param hashKey Hash key to be used for all chunks.
     * @param chunkSize The chunk size.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @return The encrypted string.
     * @throws IOException Thrown if there is an IO error.
     */
    public static String encryptString(String text, byte[] key, byte[] nonce, boolean header,
                                       boolean integrity, byte[] hashKey, Integer chunkSize)
            throws IOException {
        byte[] bytes = text.getBytes("UTF-8");
        byte[] encBytes = encryptor.encrypt(bytes, key, nonce, header, integrity, hashKey, chunkSize);
        String encString = SalmonEncoder.getBase64().encode(encBytes).replace("\n", "");
        return encString;
    }
}
