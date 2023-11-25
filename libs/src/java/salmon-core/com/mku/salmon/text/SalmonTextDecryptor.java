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

import java.io.IOException;
import com.mku.salmon.*;
import com.mku.salmon.encode.SalmonEncoder;
import com.mku.salmon.integrity.SalmonIntegrityException;

import java.nio.charset.Charset;

/**
 * Utility class that encrypts and decrypts text strings.
 */
public class SalmonTextDecryptor {

    private static final SalmonDecryptor decryptor = new SalmonDecryptor();

    /**
     * Decrypts a text String using AES256 with the key and nonce provided.
     *
     * @param text  Text to be decrypted.
     * @param key   The encryption key to be used.
     * @param nonce The nonce to be used, set only if header=false.
     * @param header Set to true if you encrypted the string with encrypt(header=true), set only if nonce=null
     *               otherwise you will have to provide the original nonce.
     * @return The decrypted text.
     * @throws IOException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     */
    public static String decryptString(String text, byte[] key, byte[] nonce, boolean header) 
		throws IOException, SalmonSecurityException, SalmonIntegrityException {
        return decryptString(text, key, nonce, header, false, null, null);
    }

    /**
     * Decrypts a text String using AES256 with the key and nonce provided.
     *
     * @param text  Text to be decrypted.
     * @param key   The encryption key to be used.
     * @param nonce The nonce to be used, set only if header=false.
     * @param header Set to true if you encrypted the string with encrypt(header=true), set only if nonce=null
     *               otherwise you will have to provide the original nonce.
     * @param integrity True if you want to calculate and store hash signatures for each chunkSize
     * @param hashKey Hash key to be used for all chunks.
     * @param chunkSize The chunk size.
     * @return The decrypted text.
     * @throws IOException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     */
    public static String decryptString(String text, byte[] key, byte[] nonce, boolean header,
                                       boolean integrity, byte[] hashKey, Integer chunkSize)
            throws IOException, SalmonSecurityException, SalmonIntegrityException {
        byte[] bytes = SalmonEncoder.getBase64().decode(text);
        byte[] decBytes = decryptor.decrypt(bytes, key, nonce, header, integrity, hashKey, chunkSize);
        String decString = new String(decBytes, Charset.defaultCharset());
        return decString;
    }
}
