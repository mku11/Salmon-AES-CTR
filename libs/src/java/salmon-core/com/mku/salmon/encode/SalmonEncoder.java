package com.mku.salmon.encode;
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

import com.mku.convert.Base64;
import com.mku.convert.IBase64;

/**
 * Provides generic encoder (ie Base64).
 */
public class SalmonEncoder {

    static {
        SalmonEncoder.base64 = new Base64();
    }

    /**
     * Current global Base64 implementation for encrypting/decrypting text strings. To change use setBase64().
     */
    private static IBase64 base64;

    /**
     * Change the current global Base64 implementation.
     * @param base64 The new Base64 implementation.
     */
    public static void setBase64(IBase64 base64) {
        SalmonEncoder.base64 = base64;
    }

    /**
     * Get the global default Base64 implementation.
     * @return The Base64 implementation.
     */
    public static IBase64 getBase64() {
        return base64;
    }
}
