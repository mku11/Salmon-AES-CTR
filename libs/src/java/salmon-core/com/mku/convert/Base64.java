package com.mku.convert;
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
 * Java default implementation of Base64 encoder/decoder.
 */
public class Base64 implements IBase64 {
    /**
     * Decode text from Base64 string.
     * @param text String to be decoded
     * @return Byte array of decoded data.
     */
    public byte[] decode(String text){
        return java.util.Base64.getDecoder().decode(text);
    }

    /**
     * Encode byte array to a text string.
     * @param data Byte array to be encoded.
     * @return String of encoded data.
     */
    public String encode(byte [] data) {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
}