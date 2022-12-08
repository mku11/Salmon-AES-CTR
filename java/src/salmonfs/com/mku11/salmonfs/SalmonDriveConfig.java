package com.mku11.salmonfs;
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


import com.mku11.salmon.BitConverter;
import com.mku11.salmon.SalmonGenerator;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;

import java.io.IOException;
import java.util.Arrays;

/**
 * Virtual Drive Configuration
 */
public class SalmonDriveConfig {
    //TODO: support versioned formats for the file header
    byte[] magicBytes = new byte[SalmonGenerator.getMagicBytesLength()];
    byte[] version = new byte[SalmonGenerator.getVersionLength()];
    byte[] salt = new byte[SalmonGenerator.getSaltLength()];
    byte[] iterations = new byte[SalmonGenerator.getIterationsLength()];
    byte[] iv = new byte[SalmonGenerator.getIvLength()];
    byte[] encryptedKeysAndNonce = new byte[SalmonGenerator.getCombinedKeyLength() + SalmonGenerator.getNonceLength()];
    byte[] hmacSignature = new byte[SalmonGenerator.getHmacResultLength()];

    /**
     * Provide a class that hosts the properties of the vault config file
     *
     * @param contents The byte array that contains the contents of the config file
     */
    public SalmonDriveConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(magicBytes, 0, SalmonGenerator.getMagicBytesLength());
        ms.read(version, 0, SalmonGenerator.getVersionLength());
        ms.read(salt, 0, SalmonGenerator.getSaltLength());
        ms.read(iterations, 0, SalmonGenerator.getIterationsLength());
        ms.read(iv, 0, SalmonGenerator.getIvLength());
        ms.read(encryptedKeysAndNonce, 0, SalmonGenerator.getCombinedKeyLength() + SalmonGenerator.getNonceLength());
        ms.read(hmacSignature, 0, SalmonGenerator.getHmacResultLength());
        ms.close();
    }

    /**
     * Write the properties of a vault configuration to a config file
     *
     * @param configFile                   The configuration file that will be used to write the content into
     * @param magicBytes                   The magic bytes for the header
     * @param version                      The version of the file format
     * @param salt                         The salt that will be used for encryption of the combined key
     * @param iterations                   The iteration that will be used to derive the master key from a text password
     * @param keyIv                        The initial vector that was used with the master password to encrypt the combined key
     * @param encryptedCombinedKeyAndNonce The encrypted combined key and vault nonce
     * @param hmacSignature                The HMAC signature of the nonce
     */
    public static void writeDriveConfig(IRealFile configFile, byte[] magicBytes, byte version, byte[] salt, int iterations, byte[] keyIv,
                                        byte[] encryptedCombinedKeyAndNonce, byte[] hmacSignature) throws Exception {
        // construct the contents of the config file
        MemoryStream ms2 = new MemoryStream();
        ms2.write(magicBytes, 0, magicBytes.length);
        ms2.write(new byte[]{version}, 0, 1);
        ms2.write(salt, 0, salt.length);
        ms2.write(BitConverter.getBytes(iterations, 4), 0, 4); // sizeof( int)
        ms2.write(keyIv, 0, keyIv.length);
        ms2.write(encryptedCombinedKeyAndNonce, 0, encryptedCombinedKeyAndNonce.length);
        ms2.write(hmacSignature, 0, hmacSignature.length);
        ms2.flush();
        ms2.position(0);

        // we write the contents to the config file
        AbsStream outputStream = configFile.getOutputStream();
        ms2.copyTo(outputStream);
        outputStream.flush();
        outputStream.close();
        ms2.close();
    }

    public void clear() {
        Arrays.fill(magicBytes, 0, magicBytes.length, (byte) 0);
        Arrays.fill(version, 0, version.length, (byte) 0);
        Arrays.fill(salt, 0, salt.length, (byte) 0);
        Arrays.fill(iterations, 0, iterations.length, (byte) 0);
        Arrays.fill(iv, 0, iv.length, (byte) 0);
        Arrays.fill(encryptedKeysAndNonce, 0, encryptedKeysAndNonce.length, (byte) 0);
        Arrays.fill(hmacSignature, 0, hmacSignature.length, (byte) 0);
    }

    public byte[] getMagicBytes() {
        return magicBytes;
    }

    public byte[] getSalt() {
        return salt;
    }

    public int getIterations() {
        if (iterations == null)
            return 0;
        return BitConverter.toInt32(iterations, 0, 4);
    }

    public byte[] getEncryptedKeysAndNonce() {
        return encryptedKeysAndNonce;
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getHMACsignature() {
        return hmacSignature;
    }

}