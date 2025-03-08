package com.mku.salmonfs.drive;
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

import com.mku.fs.file.IRealFile;
import com.mku.convert.BitConverter;
import com.mku.salmon.Generator;
import com.mku.streams.RandomAccessStream;
import com.mku.streams.MemoryStream;

import java.io.IOException;
import java.util.Arrays;

/**
 * Represents a configuration file for a drive. The properties are encrypted in the file
 * with a master key which is password derived.
 */
public class DriveConfig {
    //TODO: support versioned formats for the file header
    final byte[] magicBytes = new byte[Generator.MAGIC_LENGTH];
    final byte[] version = new byte[Generator.VERSION_LENGTH];
    final byte[] salt = new byte[DriveGenerator.SALT_LENGTH];
    final byte[] iterations = new byte[DriveGenerator.ITERATIONS_LENGTH];
    final byte[] iv = new byte[DriveGenerator.IV_LENGTH];
    final byte[] encryptedData = new byte[DriveGenerator.COMBINED_KEY_LENGTH + DriveGenerator.DRIVE_ID_LENGTH];
    final byte[] hashSignature = new byte[Generator.HASH_RESULT_LENGTH];

    /**
     * Provide a class that hosts the properties of the drive config file
     *
     * @param contents The byte array that contains the contents of the config file
     * @throws IOException Thrown if there is an IO error.
     */
    public DriveConfig(byte[] contents) throws IOException {
        MemoryStream ms = new MemoryStream(contents);
        ms.read(magicBytes, 0, Generator.MAGIC_LENGTH);
        ms.read(version, 0, Generator.VERSION_LENGTH);
        ms.read(salt, 0, DriveGenerator.SALT_LENGTH);
        ms.read(iterations, 0, DriveGenerator.ITERATIONS_LENGTH);
        ms.read(iv, 0, DriveGenerator.IV_LENGTH);
        ms.read(encryptedData, 0, DriveGenerator.COMBINED_KEY_LENGTH + DriveGenerator.AUTH_ID_SIZE);
        ms.read(hashSignature, 0, Generator.HASH_RESULT_LENGTH);
        ms.close();
    }

    /**
     * Write the properties of a drive to a config file
     *
     * @param configFile                   The configuration file that will be used to write the content into
     * @param magicBytes                   The magic bytes for the header
     * @param version                      The version of the file format
     * @param salt                         The salt that will be used for encryption of the combined key
     * @param iterations                   The iteration that will be used to derive the master key from a text password
     * @param keyIv                        The initial vector that was used with the master password to encrypt the combined key
     * @param encryptedData The encrypted combined key and drive id
     * @param hashSignature                The hash signature of the drive id
     * @throws IOException Thrown if there is an IO error.
     */
    public static void writeDriveConfig(IRealFile configFile, byte[] magicBytes, byte version, byte[] salt,
                                        int iterations, byte[] keyIv,
                                        byte[] encryptedData, byte[] hashSignature) throws IOException {
        // construct the contents of the config file
        MemoryStream ms2 = new MemoryStream();
        ms2.write(magicBytes, 0, magicBytes.length);
        ms2.write(new byte[]{version}, 0, 1);
        ms2.write(salt, 0, salt.length);
        ms2.write(BitConverter.toBytes(iterations, 4), 0, 4); // sizeof( int)
        ms2.write(keyIv, 0, keyIv.length);
        ms2.write(encryptedData, 0, encryptedData.length);
        ms2.write(hashSignature, 0, hashSignature.length);
        ms2.flush();
        ms2.setPosition(0);

        // we write the contents to the config file
        RandomAccessStream outputStream = configFile.getOutputStream();
        ms2.copyTo(outputStream);
        outputStream.flush();
        outputStream.close();
        ms2.close();
    }

    /**
     * Clear properties.
     */
    public void clear() {
        Arrays.fill(magicBytes, 0, magicBytes.length, (byte) 0);
        Arrays.fill(version, 0, version.length, (byte) 0);
        Arrays.fill(salt, 0, salt.length, (byte) 0);
        Arrays.fill(iterations, 0, iterations.length, (byte) 0);
        Arrays.fill(iv, 0, iv.length, (byte) 0);
        Arrays.fill(encryptedData, 0, encryptedData.length, (byte) 0);
        Arrays.fill(hashSignature, 0, hashSignature.length, (byte) 0);
    }

    /**
     * Get the magic bytes from the config file.
     * @return The magic bytes
     */
    public byte[] getMagicBytes() {
        return magicBytes;
    }

    /**
     * Get the salt to be used for the password key derivation.
     * @return The salt
     */
    public byte[] getSalt() {
        return salt;
    }

    /**
     * Get the iterations to be used for the key derivation.
     * @return The iterations
     */
    public int getIterations() {
        if (iterations == null)
            return 0;
        return (int) BitConverter.toLong(iterations, 0, DriveGenerator.ITERATIONS_LENGTH);
    }

    /**
     * Get encrypted data using the master key: drive key, hash key, drive id.
     * @return The encrypted data
     */
    byte[] getEncryptedData() {
        return encryptedData;
    }

    /**
     * Get the initial vector that was used to encrypt this drive configuration.
     * @return The initial vector
     */
    public byte[] getIv() {
        return iv;
    }

    /**
     * Get the hash signature that was used to sign this drive configuration.
     * @return The hash signature
     */
    public byte[] getHashSignature() {
        return hashSignature;
    }
}