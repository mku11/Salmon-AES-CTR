package com.mku.salmon.transform;
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
     * @param aesImpl
     */
    private native static void init(int aesImpl);

    /**
     * Native Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     *
     * @param key
     * @param expandedKey
     */
    private native static void expandkey(byte[] key, byte[] expandedKey);

    /**
     * Native transform of the input byte array using AES-256 CTR mode
     *
     * @param key
     * @param counter
     * @param srcBuffer
     * @param srcOffset
     * @param destBuffer
     * @param destOffset
     * @param count
     * @return
     */
    private native static int transform(byte[] key, byte[] counter,
                                               byte[] srcBuffer, int srcOffset,
                                               byte[] destBuffer, int destOffset, int count);

    /**
     * Proxy Init the native code with AES implementation, and hash length options.
     *
     * @param aesImpl
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
     * @param key
     * @param expandedKey
     */
    public void salmonExpandKey(byte[] key, byte[] expandedKey) {
        expandkey(key, expandedKey);
    }

    /**
     * Proxy Transform the input byte array using AES-256 CTR mode
     *
     * @param key
     * @param counter
     * @param srcBuffer
     * @param srcOffset
     * @param destBuffer
     * @param destOffset
     * @param count
     * @return
     */
    public int salmonTransform(byte[] key, byte[] counter, byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count) {
        return transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}