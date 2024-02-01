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

import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.io.SalmonStream;

/**
 * Salmon AES transformer implemented with AES intrinsics.
 */
public class SalmonAesIntrTransformer extends SalmonNativeTransformer {
    /**
     * The constant to pass to the native code while initializing.
     */
    public static final int AES_IMPL_AES_INTR = 1;

    /**
     * Initialize the native Aes intrinsics transformer.
     * @param key The AES key to use.
     * @param nonce The nonce to use.
     * @throws SalmonSecurityException
     */
    @Override
    public void init(byte[] key, byte[] nonce) throws SalmonSecurityException {
        getNativeProxy().salmonInit(AES_IMPL_AES_INTR);
        byte[] expandedKey = new byte[SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE];
        getNativeProxy().salmonExpandKey(key, expandedKey);
        setExpandedKey(expandedKey);
        super.init(key, nonce);
    }

    /**
     * Encrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     */
    @Override
    public int encryptData(byte[] srcBuffer, int srcOffset,
                           byte[] destBuffer, int destOffset, int count) {
        // AES intrinsics needs the expanded key
        return getNativeProxy().salmonTransform(getExpandedKey(), getCounter(),
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
    }

    /**
     * Decrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     */
    @Override
    public int decryptData(byte[] srcBuffer, int srcOffset,
                            byte[] destBuffer, int destOffset, int count) {
        // AES intrinsics needs the expanded key
        return getNativeProxy().salmonTransform(getExpandedKey(), getCounter(),
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
    }
}
