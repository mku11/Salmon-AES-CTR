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
 * Proxy interface for use with native libraries for different platforms and operating systems.
 */
public interface INativeProxy
{
    /**
     * Proxy Init the native code with AES implementation, and hash length options.
     * @param aesImpl
     */
    void salmonInit(int aesImpl);

    /**
     * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
     * for AES 256.
     * @param key
     * @param expandedKey
     */
    void salmonExpandKey(byte[] key, byte[] expandedKey);

    /**
     * Proxy Transform the input byte array using AES-256 CTR mode
     * @param key
     * @param counter
     * @param srcBuffer
     * @param srcOffset
     * @param destBuffer
     * @param destOffset
     * @param count
     * @return
     */
    int salmonTransform(byte[] key, byte[] counter,
                               byte[] srcBuffer, int srcOffset,
                               byte[] destBuffer, int destOffset, int count);
}