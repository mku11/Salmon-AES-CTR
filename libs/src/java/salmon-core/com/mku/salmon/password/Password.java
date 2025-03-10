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

/**
 * Generates security keys based on text passwords.
 */
public class Password {

    /**
     * Global PBKDF algorithm option that will be used for the master key derivation.
     */
    private static PbkdfAlgo pbkdfAlgo = PbkdfAlgo.SHA256;

    /**
     * Pbkdf provider.
     */
    private static IPbkdfProvider provider = new DefaultPbkdfProvider();

    /**
     * Returns the current global PBKDF algorithm.
     *
     * @return The PBKDF algorithm to be used.
     */
    public static PbkdfAlgo getPbkdfAlgo() {
        return pbkdfAlgo;
    }

    /**
     * Set the global PDKDF algorithm to be used for key derivation.
     *
     * @param pbkdfAlgo The PBKDF algorithm to use
     */
    public static void setPbkdfAlgo(PbkdfAlgo pbkdfAlgo) {
        Password.pbkdfAlgo = pbkdfAlgo;
    }

    /**
     * Set the global PBKDF implementation to be used for text key derivation.
     *
     * @param pbkdfType The PBKDF implementation to use
     */
    public static void setPbkdfType(PbkdfType pbkdfType) {
        provider = PbkdfFactory.create(pbkdfType);
    }
	
	/**
     * Set the global PBKDF provider to be used for text key derivation.
     *
     * @param pbkdfProvider The PBKDF provider
     */
    public static void setPbkdfProvider(IPbkdfProvider pbkdfProvider) {
        provider = pbkdfProvider;
    }

    /**
     * Derives the key from a text password
     *
     * @param pass       The text password to be used
     * @param salt       The salt to be used for the key derivation
     * @param iterations The number of iterations the key derivation algorithm will use
	 * @param length     The length of master key to return
     * @return The derived master key.
     * @throws SecurityException Thrown if there is a security exception
     */
    public static byte[] getMasterKey(String pass, byte[] salt, int iterations, int length) {
        byte[] masterKey = getKeyFromPassword(pass, salt, iterations, length);
        return masterKey;
    }

    /**
     * Function will derive a key from a text password
     *
     * @param password    The password that will be used to derive the key
     * @param salt        The salt byte array that will be used together with the password
     * @param iterations  The iterations to be used with Pbkdf2
     * @param outputBytes The number of bytes for the key
     * @return The derived key.
     * @throws SecurityException Thrown if there is a security exception
     */
    public static byte[] getKeyFromPassword(String password, byte[] salt, int iterations, int outputBytes) {
        return provider.getKey(password, salt, iterations, outputBytes, pbkdfAlgo);
    }

}

