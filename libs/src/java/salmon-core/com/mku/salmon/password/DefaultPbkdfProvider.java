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

import com.mku.salmon.SecurityException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Provides pbkdf functions.
 */
public class DefaultPbkdfProvider implements IPbkdfProvider {
    /**
     * Get a key derived from a text password
     * @param password The text password.
     * @param salt The salt (at least 24 bytes).
     * @param iterations Iterations to use. Make sure you use a high number according to your hardware specs.
     * @param outputBytes The length of the output key.
     * @param pbkdfAlgo The PBKDF algorithm to use
     * @return The key.
     * @throws SecurityException Thrown if there is a security exception
     */
    public byte[] getKey(String password, byte[] salt, int iterations, int outputBytes, PbkdfAlgo pbkdfAlgo) {
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, outputBytes * 8);
        String pbkdfAlgoStr = IPbkdfProvider.getPbkdfAlgoString(pbkdfAlgo);
        byte[] key;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(pbkdfAlgoStr);
            key = factory.generateSecret(keySpec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new SecurityException("Could not initialize pbkdf: " + e);
        }
        return key;
    }
}
