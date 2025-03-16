package com.mku.salmon.bridge;
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

/**
 * Proxy class for use with windows native library.
 */
public class NativeProxy implements INativeProxy {
    private static boolean loaded;

    /**
     * The dll name for the salmon library.
     */
    private final static String libraryName = "salmon";

    /**
     * Init the native code with AES implementation, and hash length options.
     *
     * @param aesImpl The AES implementation see ProviderType
     */
    private native static void init(int aesImpl);

    /**
     * Native Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key The key (32 bytes)
     * @param expandedKey The expanded key (240 bytes)
     */
    private native static void expandkey(byte[] key, byte[] expandedKey);

    /**
     * Transforms data using CTR mode. CTR mode is symmetric so you should use it for both encryption and decryption.
     * @param key The key
     * @param counter The counter
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The count of bytes to transform.
     * @return The number of bytes transformed
     */
    private native static int transform(byte[] key, byte[] counter,
                                               byte[] srcBuffer, int srcOffset,
                                               byte[] destBuffer, int destOffset, int count);

    /**
     * Proxy Init the native code with AES implementation, and hash length options.
     *
     * @param aesImpl AES implementation type (Aes Intrinsics = 1, Aes = 2, Aes GPU = 3)
     */
    public void salmonInit(int aesImpl) {
        loadLibrary();
        init(aesImpl);
    }

    /**
     * Load the native library
     */
    protected void loadLibrary() {
        if(loaded)
            return;
        try {
            System.loadLibrary(libraryName);
        } catch (Exception ex) {
            System.err.println(ex);
        }
        loaded = true;
    }

    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key The key
     * @param expandedKey The expanded key
     */
    public void salmonExpandKey(byte[] key, byte[] expandedKey) {
        expandkey(key, expandedKey);
    }

    /**
     * Proxy Transform the input byte array using AES-256 CTR mode
     *
     * @param key The key
     * @param counter The counter
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The count of bytes to be transform.
     * @return The transformed data.
     */
    public int salmonTransform(byte[] key, byte[] counter, byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count) {
        return transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}