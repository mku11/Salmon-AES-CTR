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

import { IHashProvider } from "./ihash_provider.js";
import { IntegrityException } from "./integrity_exception.js";

/**
 * Provides HMAC SHA-256 hashing.
 */
export class HmacSHA256Provider implements IHashProvider {

    /**
     * Calculate HMAC SHA256 hash for a byte buffer.
     * @param {Uint8Array} hashKey The HMAC SHA256 key to use for hashing (32 bytes).
     * @param {Uint8Array} buffer The buffer to read the data from.
     * @param {number} offset The position reading will start from.
     * @param {number} count The count of bytes to be read.
     * @returns {Promise<Uint8Array>} The HMAC SHA256 hash.
     * @throws IntegrityException thrown if hash cannot be calculated
     */
    public async calc(hashKey: Uint8Array, buffer: Uint8Array, offset: number, count: number): Promise<Uint8Array> {
        try {
            const cryptoKey: CryptoKey = await crypto.subtle.importKey('raw', hashKey, { name: 'HMAC', hash: 'SHA-256' }, true, ['sign']);
            const hashValue: Uint8Array = new Uint8Array(await crypto.subtle.sign('HMAC', cryptoKey, buffer.slice(offset, offset + count)));
            return hashValue;
        } catch (ex) {
            throw new IntegrityException("Could not calculate HMAC", ex);
        }
    }
}