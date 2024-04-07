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

import com.mku.bridge.INativeProxy;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.bridge.NativeProxy;

/**
 * Generic Native AES transformer. Extend this with your specific 
 * native transformer.
 */ 
public class SalmonNativeTransformer extends SalmonAES256CTRTransformer {
    private static INativeProxy nativeProxy = new NativeProxy();

    /**
     * The native proxy to use for loading libraries for different platforms and operating systems.
     * @param proxy The proxy
     */
    public static void setNativeProxy(INativeProxy proxy) {
        nativeProxy = proxy;
    }

    public static INativeProxy getNativeProxy() {
        return nativeProxy;
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
        if (getKey() == null) //TODO: ToSync
            throw new SalmonSecurityException("No key found, run init first");
        if (getCounter() == null) //TODO: ToSync
            throw new SalmonSecurityException("No counter found, run init first");
        return nativeProxy.salmonTransform(getKey(), getCounter(),
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
        if (getKey() == null) //TODO: ToSync
            throw new SalmonSecurityException("No key found, run init first");
        if (getCounter() == null) //TODO: ToSync
            throw new SalmonSecurityException("No counter found, run init first");
        return nativeProxy.salmonTransform(getKey(), getCounter(),
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
    }
}
