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
    byte[] magicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];
    byte[] version = new byte[SalmonGenerator.VERSION_LENGTH];
    byte[] salt = new byte[SalmonGenerator.SALT_LENGTH];
    byte[] iterations = new byte[SalmonGenerator.ITERATIONS_LENGTH];
    byte[] iv = new byte[SalmonGenerator.IV_LENGTH];
    byte[] encryptedData = new byte[SalmonGenerator.COMBINED_KEY_LENGTH + SalmonGenerator.DRIVE_ID_LENGTH];
    byte[] hmacSignature = new byte[SalmonGenerator.HMAC_RESULT_LENGTH];

    /**
     * Provide a class that hosts the properties of the vault config file
     *
     * @param contents The byte array that contains the contents of the config file
     */
    public SalmonDriveConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(magicBytes, 0, SalmonGenerator.MAGIC_LENGTH);
        ms.read(version, 0, SalmonGenerator.VERSION_LENGTH);
        ms.read(salt, 0, SalmonGenerator.SALT_LENGTH);
        ms.read(iterations, 0, SalmonGenerator.ITERATIONS_LENGTH);
        ms.read(iv, 0, SalmonGenerator.IV_LENGTH);
        ms.read(encryptedData, 0, SalmonGenerator.COMBINED_KEY_LENGTH + SalmonGenerator.AUTH_ID_SIZE);
        ms.read(hmacSignature, 0, SalmonGenerator.HMAC_RESULT_LENGTH);
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
     * @param encryptedData The encrypted combined key and drive id
     * @param hmacSignature                The HMAC signature of the drive id
     */
    public static void writeDriveConfig(IRealFile configFile, byte[] magicBytes, byte version, byte[] salt, int iterations, byte[] keyIv,
                                        byte[] encryptedData, byte[] hmacSignature) throws Exception {
        // construct the contents of the config file
        MemoryStream ms2 = new MemoryStream();
        ms2.write(magicBytes, 0, magicBytes.length);
        ms2.write(new byte[]{version}, 0, 1);
        ms2.write(salt, 0, salt.length);
        ms2.write(BitConverter.toBytes(iterations, 4), 0, 4); // sizeof( int)
        ms2.write(keyIv, 0, keyIv.length);
        ms2.write(encryptedData, 0, encryptedData.length);
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
        Arrays.fill(encryptedData, 0, encryptedData.length, (byte) 0);
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
        return (int) BitConverter.toLong(iterations, 0, SalmonGenerator.ITERATIONS_LENGTH);
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getHMACsignature() {
        return hmacSignature;
    }

}