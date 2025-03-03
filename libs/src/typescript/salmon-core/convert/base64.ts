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

import { IBase64 } from './ibase64.js';

/**
 * Base64 encoder/decoder.
 */
export class Base64 implements IBase64 {
    /**
     * Decode a Base64 encoded string into a byte array.
     * @param {string} text String to be decoded
     * @returns {Uint8Array} Byte array of decoded data.
     */
    public decode(text: string): Uint8Array {
        return Uint8Array.from(atob(text), x => x.charCodeAt(0));
    }

    /**
     * Encode a byte array into a Base64 encoded string.
     * @param {Uint8Array} data Byte array to be encoded
     * @return {string} String of encoded data.
     */
    public encode(data: Uint8Array): string {
        return btoa(String.fromCodePoint(...data));
    }
}