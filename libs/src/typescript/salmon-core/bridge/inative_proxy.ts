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
 * Interface to native libraries that provide AES-256 encryption in CTR mode.
 */
export interface INativeProxy {
    /**
     * Initializes the native library with the specified AES implementation.
     * @param {number} aesImpl
     */
    init(aesImpl: number): void;

    /**
     * Expands the specified AES encryption key.
     * @param {Uint8Array} The AES-256 encryption key (32 bytes)
     * @param {Uint8Array} expandedKey
     */
    expandKey(key: Uint8Array, expandedKey: Uint8Array): void;

    /**
     * Transforms data using CTR mode which is symmetric so you should use it for both encryption and decryption.
     * @param {Uint8Array} The AES-256 encryption key (32 bytes)
     * @param {number} The counter (16 bytes)
     * @param {Uint8Array} srcBuffer
     * @param {number} srcOffset
     * @param {Uint8Array} destBuffer
     * @param {number} destOffset
     * @param {number} count
     * @return {number} The number of bytes that were transformed.
     */
    transform(key: Uint8Array, counter: Uint8Array,
        srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): number;
}