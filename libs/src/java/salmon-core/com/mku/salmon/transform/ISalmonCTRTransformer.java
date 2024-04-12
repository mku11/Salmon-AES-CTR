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

import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.SalmonSecurityException;

/**
 * Contract for the encryption/decryption transformers.
 * Note that Counter mode needs to be supported.
 */
public interface ISalmonCTRTransformer {

    /**
     * Initialize the transformer.
     * @param key The AES key to use.
     * @param nonce The nonce to use.
     * @throws SalmonSecurityException Thrown if there is a security exception
     */
    void init(byte[] key, byte[] nonce);

    /**
     * Encrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     * @throws SalmonSecurityException Thrown if there is a security exception
     * @throws SalmonRangeExceededException Thrown if the nonce exceeds its range
     */
    int encryptData(byte[] srcBuffer, int srcOffset,
                    byte[] destBuffer, int destOffset, int count);

    /**
     * Decrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     * @throws SalmonSecurityException Thrown if there is a security exception
     * @throws SalmonRangeExceededException Thrown if the nonce exceeds its range
     */
    int decryptData(byte[] srcBuffer, int srcOffset,
                    byte[] destBuffer, int destOffset, int count);

    /**
     * Get the current counter.
     * @return The counter
     */
    byte[] getCounter();

    /**
     * Get the current encryption key.
     * @return The key
     */
    byte[] getKey();

    /**
     * Get the current block.
     * @return The block
     */
    long getBlock();

    /**
     * Get the nonce (initial counter) to be used for the data.
     * @return The nonce
     */
    byte[] getNonce();

    /**
     * Reset the counter to the nonce (initial counter).
     */
    void resetCounter();

    /**
     * Calculate the value of the counter based on the current block. After an encryption
     * operation (ie sync or read) the block will be incremented. This method calculates
     * the Counter.
     * @param position The position to sync the counter to
     * @throws SalmonRangeExceededException Thrown if the nonce exceeds its range
     */
    void syncCounter(long position);
}

