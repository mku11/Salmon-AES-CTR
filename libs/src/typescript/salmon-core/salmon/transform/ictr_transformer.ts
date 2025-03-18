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
 * Contract for the encryption/decryption transformers.
 * Note that Counter mode needs to be supported.
 */
export interface ISalmonCTRTransformer {

    /**
     * Initialize the transformer.
     * @param {Uint8Array} key The AES key to use.
     * @param {Uint8Array} nonce The nonce to use.
     * @throws SalmonSecurityException Thrown when error with security
     */
    init(key: Uint8Array, nonce: Uint8Array): Promise<void>;

    /**
     * Encrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @returns {Promise<number>} The number of bytes transformed.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    encryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;

    /**
     * Decrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @returns {Promise<number>} The number of bytes transformed.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    decryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;

    /**
     * Get the current counter.
     * @returns {Uint8Array | null} The current counter.
     */
    getCounter(): Uint8Array | null;

    /**
     * Get the current encryption key.
     * @returns {Uint8Array | null} The encryption key
     */
    getKey(): Uint8Array | null;

    /**
     * Get the current block.
     * @returns {number} The block
     */
    getBlock(): number;

    /**
     * Get the nonce (initial counter) to be used for the data.
     * @returns {Uint8Array | null} The nonce
     */
    getNonce(): Uint8Array | null;

    /**
     * Reset the counter to the nonce (initial counter).
     */
    resetCounter(): void;

    /**
     * Calculate the value of the counter based on the current block. After an encryption
     * operation (ie sync or read) the block will be incremented. This method calculates
     * the Counter.
     * @param {number} position The position to sync to
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    syncCounter(position: number): void;
}