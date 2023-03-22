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

import java.nio.charset.Charset;

/**
 * Utility class that encrypts and decrypts text strings
 */
public class SalmonTextEncryptor {
    private static IBase64 base64;
    static {
        base64 = new Base64();
    }

    public static void setBase64(IBase64 base64) {
        SalmonTextEncryptor.base64 = base64;
    }
    /**
     * Decrypts a text String
     *
     * @param text  Text to be decrypted
     * @param key   The encryption key to be used
     * @param nonce The nonce to be used
     */
    // TODO: there is currently no integrity for filenames, is it worthy it?
    public static String decryptString(String text, byte[] key, byte[] nonce, boolean header) throws Exception {
        byte[] bytes = base64.decode(text);
        byte[] decBytes = SalmonEncryptor.decrypt(bytes, key, nonce, header);
        String decString = new String(decBytes, Charset.defaultCharset());
        return decString;

    }

    /**
     * Encrypts a text string
     *
     * @param text  Text to be encrypted
     * @param key   The encryption key to be used
     * @param nonce The nonce to be used
     */
    public static String encryptString(String text, byte[] key, byte[] nonce, boolean header) throws Exception {
        byte[] bytes = text.getBytes(Charset.defaultCharset());
        byte[] encBytes = SalmonEncryptor.encrypt(bytes, key, nonce, header);
        String encString = base64.encode(encBytes).replace("\n", "");
        return encString;
    }
}
