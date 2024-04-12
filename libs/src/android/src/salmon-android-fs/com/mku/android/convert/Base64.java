package com.mku.android.convert;
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

import com.mku.convert.IBase64;

/**
 * Base64 Proxy for Android.
 */
public class Base64 implements IBase64 {
    /**
     * Decode a string.
     * @param text String to be converted.
     * @return The bytes with the decoded data.
     */
    public byte[] decode(String text){
        return android.util.Base64.decode(text, android.util.Base64.NO_WRAP);
    }

    /**
     * Encode a string.
     * @param data The byte array to be converted.
     * @return The string with the text encoded
     */
    public String encode(byte [] data) {
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }
}