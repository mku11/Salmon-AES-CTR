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

import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import java.util.Arrays;

/**
 * Utility class that encrypts and decrypts byte arrays
 */
public class SalmonEncryptor {

    /**
     * Decrypts a text String
     *
     * @param text  byte array to be decrypted
     * @param key   The encryption key to be used
     * @param nonce The nonce to be used
     */
    // TODO: there is currently no integrity for filenames, is it worthy it?
    public static byte[] decrypt(byte[] text, byte[] key, byte[] nonce, boolean header) throws Exception {
        if (key == null)
            throw new Exception("Need to specify a key");
        if (!header && nonce == null)
            throw new Exception("Need to specify a nonce if the file doesn't have a header");
        MemoryStream inputStream = new MemoryStream(text);
        byte[] headerData = null;
        if (header) {
            byte[] magicBytes = new byte[SalmonGenerator.getMagicBytesLength()];
            inputStream.read(magicBytes, 0, magicBytes.length);

            byte[] versionBytes = new byte[SalmonGenerator.getVersionLength()];
            inputStream.read(versionBytes, 0, versionBytes.length);
            nonce = new byte[SalmonGenerator.getNonceLength()];
            inputStream.read(nonce, 0, nonce.length);

            inputStream.position(0);
            headerData = new byte[magicBytes.length + versionBytes.length + nonce.length];
            inputStream.read(headerData, 0, headerData.length);
        }

        SalmonStream stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, inputStream, headerData, false, null, null);
        byte[] buffer = new byte[text.length];
        int bytesRead = stream.read(buffer, 0, buffer.length);
        inputStream.close();
        stream.close();
        return Arrays.copyOfRange(buffer, 0, bytesRead);
    }

    /**
     * Encrypts a text string
     *
     * @param text  Byte array to be encrypted
     * @param key   The encryption key to be used
     * @param nonce The nonce to be used
     */
    public static byte[] encrypt(byte[] text, byte[] key, byte[] nonce, boolean header) throws Exception {
        if (key == null || nonce == null)
            throw new Exception("Need to specify a key and nonce");
        MemoryStream outputStream = new MemoryStream();
        byte[] headerData = null;
        if (header) {
            byte[] magicBytes = SalmonGenerator.getMagicBytes();
            outputStream.write(magicBytes, 0, magicBytes.length);

            byte version = SalmonGenerator.getVersion();
            byte[] versionBytes = new byte[]{version};
            outputStream.write(versionBytes, 0, versionBytes.length);
            outputStream.write(nonce, 0, nonce.length);
            outputStream.flush();
            headerData = outputStream.toArray();
        }
        SalmonStream stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, outputStream,
                headerData, false, null, null);
        stream.write(text, 0, text.length);
        stream.flush();
        stream.close();

        byte[] decBytes = outputStream.toArray();
        outputStream.close();
        return decBytes;
    }
}
