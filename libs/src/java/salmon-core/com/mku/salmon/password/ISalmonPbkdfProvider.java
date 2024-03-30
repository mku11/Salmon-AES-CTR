package com.mku.salmon.password;
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

import com.mku.salmon.SalmonSecurityException;

/**
 * Provides key derivation based on text passwords.
 */
public interface ISalmonPbkdfProvider {
    /**
     * Java Cipher key for SHA256. See javax.crypto.SecretKeyFactory.
     */
    String PBKDF_SHA256 = "PBKDF2WithHmacSHA256";
    /**
     * Java Cipher key for SHA1. See javax.crypto.SecretKeyFactory.
     * WARNING! SHA1 is considered insecure! Use PBKDF_SHA256 instead.
     */
    @Deprecated
    String PBKDF_SHA1 = "PBKDF2WithHmacSHA1";

    /**
     * Get the PBKDF java cipher algorigthm string.
     *
     * @param pbkdfAlgo The PBKDF algorithm to be used
     * @return The java cipher algorithm string. See javax.crypto.SecretKeyFactory.
     */
    @SuppressWarnings("deprecation")
    static String getPbkdfAlgoString(PbkdfAlgo pbkdfAlgo) {
        switch (pbkdfAlgo) {
            case SHA1:
                return ISalmonPbkdfProvider.PBKDF_SHA1;
            case SHA256:
                return ISalmonPbkdfProvider.PBKDF_SHA256;
        }
        throw new SalmonSecurityException("Unknown pbkdf algorithm");
    }

    /**
     * Get a key derived from a text password.
     * @param password The text password.
     * @param salt The salt needs to be at least 24 bytes.
     * @param iterations Iterations to use. Make sure you use a high number according to your hardware specs.
     * @param outputBytes The length of the output key.
     * @param pbkdfAlgo The hash algorithm to use.
     * @return The key.
     * @throws SalmonSecurityException
     */
    byte[] getKey(String password, byte[] salt, int iterations, int outputBytes, PbkdfAlgo pbkdfAlgo);
}
