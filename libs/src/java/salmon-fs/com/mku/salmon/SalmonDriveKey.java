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

import java.util.Arrays;

/**
 * Encryption keys and properties.
 */
public class SalmonDriveKey {

    private byte[] masterKey;
    private byte[] driveKey;
    private byte[] hashKey;
    private int iterations;

    /**
     * Clear the properties from memory.
     */
    public void clear() {

        if (driveKey != null)
            Arrays.fill(driveKey, 0, driveKey.length, (byte) 0);
        driveKey = null;

        if (hashKey != null)
            Arrays.fill(hashKey, 0, hashKey.length, (byte) 0);
        hashKey = null;

        if(masterKey != null)
            Arrays.fill(masterKey, 0, masterKey.length, (byte) 0);
        masterKey = null;
        
        iterations = 0;
    }

    /**
     * Finalize.
     * @throws Throwable Thrown if error occurs during finalization
     */
    //TODO: replace with Phantom References
    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }

    /**
     * Function returns the encryption key that will be used to encrypt/decrypt the files
     * @return The encryption key
     */
    public byte[] getDriveKey() {
        return driveKey;
    }

    /**
     * Function returns the hash key that will be used to sign the file chunks
     * @return The hash key
     */
    public byte[] getHashKey() {
        return hashKey;
    }

    /**
     * Set the drive key.
     * @param driveKey The drive key
     */
    public void setDriveKey(byte[] driveKey) {
        this.driveKey = driveKey;
    }

    /**
     * Set the hash key.
     * @param hashKey The hash key
     */
    public void setHashKey(byte[] hashKey) {
        this.hashKey = hashKey;
    }

    /**
     * Get the master key.
     * @return The master key
     */
    public byte[] getMasterKey() {
        return masterKey;
    }

    /**
     * Set the master key.
     * @param masterKey The master key
     */
    public void setMasterKey(byte[] masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * Get the number of iterations for the master key derivation.
     * @return The iterations
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * Set the number of iterations for the master key derivation.
     * @param iterations The iterations
     */
    public void setIterations(int iterations) {
        this.iterations = iterations;
    }
}
