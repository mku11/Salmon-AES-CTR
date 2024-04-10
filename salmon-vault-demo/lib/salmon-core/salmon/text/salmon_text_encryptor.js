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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _a, _SalmonTextEncryptor_encryptor;
import { SalmonEncryptor } from "../salmon_encryptor.js";
import { SalmonEncoder } from "../encode/salmon_encoder.js";
/**
 * Utility class that encrypts and decrypts text strings.
 */
export class SalmonTextEncryptor {
    /**
     * Encrypts a text String using AES256 with the key and nonce provided.
     *
     * @param {string} text  Text to be encrypted.
     * @param {Uint8Array} key   The encryption key to be used.
     * @param {Uint8Array} nonce The nonce to be used.
     * @param {boolean} header Set to true to store a header with information like nonce and/or chunk size,
     *               otherwise you will have to store that information externally.
     * @param {boolean} integrity True if you want to calculate and store hash signatures for each chunkSize
     * @param {Uint8Array | null} hashKey Hash key to be used for all chunks.
     * @param {Promise<string>} chunkSize The chunk size.
     * @returns {Promise<string>} The encrypted string.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    static async encryptString(text, key, nonce, header, integrity = false, hashKey = null, chunkSize = null) {
        let bytes = new TextEncoder().encode(text);
        let encBytes = await __classPrivateFieldGet(this, _a, "f", _SalmonTextEncryptor_encryptor).encrypt(bytes, key, nonce, header, integrity, hashKey, chunkSize);
        let encString = SalmonEncoder.getBase64().encode(encBytes).replace("\n", "");
        return encString;
    }
}
_a = SalmonTextEncryptor;
_SalmonTextEncryptor_encryptor = { value: new SalmonEncryptor() };
