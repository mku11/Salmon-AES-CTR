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

import { SecurityException } from "../security_exception.js";
import { ISalmonPbkdfProvider, getPbkdfAlgoString } from "./ipbkdf_provider.js";
import { PbkdfAlgo } from "./pbkdf_algo.js";

/**
 * Provides pbkdf algorithm.
 */
export class DefaultPbkdfProvider implements ISalmonPbkdfProvider {
    /**
     * Get a key derived from a text password
     * @param {string} password The text password.
     * @param {Uint8Array} salt The salt needs to be at least 24 bytes.
     * @param {number} iterations Iterations to use. Make sure you use a high number according to your hardware specs.
     * @param {number} outputBytes The length of the output key.
     * @param {PbkdfAlgo} pbkdfAlgo The hash algorithm to use.
     * @returns {Promise<Uint8Array>} The key.
     * @throws SalmonSecurityException Thrown when error with security
     */
    public async getKey(password: string, salt: Uint8Array, iterations: number, outputBytes: number, pbkdfAlgo: PbkdfAlgo): Promise<Uint8Array> {
        let pbkdfAlgoStr: string = getPbkdfAlgoString(pbkdfAlgo);
        let key: Uint8Array;
        try {
            let importKey: CryptoKey = await crypto.subtle.importKey("raw",
                new TextEncoder().encode(password), { name: "PBKDF2" }, false, ["deriveBits", "deriveKey"]);
            key = new Uint8Array(await crypto.subtle.deriveBits({
                name: "PBKDF2", salt, iterations: iterations, hash: pbkdfAlgoStr
            }, importKey, outputBytes * 8));
        } catch (e) {
            throw new SecurityException("Could not initialize pbkdf: " + e);
        }
        return key;
    }
}