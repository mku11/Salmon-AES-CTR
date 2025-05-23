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

import com.mku.salmon.streams.ProviderType;

/**
 * Interface to native libraries that provide AES-256 encryption in CTR mode.
 */
public interface INativeProxy
{
    /**
     * Initializes the native library with the specified AES implementation.
     * @param aesImpl The AES implementation, see {@link ProviderType} for possible values
     */
    void salmonInit(int aesImpl);

    /**
     * Expands the specified AES encryption key.
     * @param key The AES-256 encryption key (32 bytes)
     * @param expandedKey The expanded key (240 bytes)
     */
    void salmonExpandKey(byte[] key, byte[] expandedKey);

    /**
     * Transforms data using CTR mode. CTR mode is symmetric so you should use it for both encryption and decryption.
     * @param key The AES-256 encryption key (32 bytes)
     * @param counter The counter (16 bytes)
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The count of bytes to transform.
     * @return The number of bytes transformed.
     */
    int salmonTransform(byte[] key, byte[] counter,
                               byte[] srcBuffer, int srcOffset,
                               byte[] destBuffer, int destOffset, int count);
}