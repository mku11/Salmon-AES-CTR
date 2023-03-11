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

import java.util.Arrays;

public class SalmonKey {
    //TODO: could these be hosted inside secure storage like SecureString?
    private byte[] masterKey;
    private byte[] driveKey;
    private byte[] hmacKey;

    private byte[] salt;
    private int iterations;

    public void clear() {

        if (driveKey != null)
            Arrays.fill(driveKey, 0, driveKey.length, (byte) 0);
        driveKey = null;

        if (hmacKey != null)
            Arrays.fill(hmacKey, 0, hmacKey.length, (byte) 0);
        hmacKey = null;
    }

    //TODO: replace with Phantom References
    @SuppressWarnings("deprecation")
    @Override
    public void finalize() throws Throwable {
        clear();
        super.finalize();
    }

    /**
     * Function returns the encryption key that will be used to encrypt/decrypt the files
     */
    public byte[] getDriveKey() {
        return driveKey;
    }


    /**
     * Function returns the HMAC key that will be used to sign the file chunks
     */
    public byte[] getHMACKey() {
        return hmacKey;
    }

    public void setDriveKey(byte[] driveKey) {
        this.driveKey = driveKey;
    }

    public void setHmacKey(byte[] hmacKey) {
        this.hmacKey = hmacKey;
    }

    public byte[] getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(byte[] masterKey) {
        this.masterKey = masterKey;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public void setSalt(byte[] salt) {
        this.salt = salt;
    }

    public byte[] getSalt() {
        return salt;
    }
}
