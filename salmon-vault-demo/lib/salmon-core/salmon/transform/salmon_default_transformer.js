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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonDefaultTransformer_encSecretKey;
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./salmon_aes256_ctr_transformer.js";
/**
 * Salmon AES transformer based on the javax.crypto routines.
 */
export class SalmonDefaultTransformer extends SalmonAES256CTRTransformer {
    constructor() {
        super(...arguments);
        /**
         * Key spec for the initial nonce (counter).
         */
        _SalmonDefaultTransformer_encSecretKey.set(this, null);
    }
    /**
     * Initialize the default Java AES cipher transformer.
     * @param {Uint8Array} key The AES256 key to use.
     * @param {Uint8Array} nonce The nonce to use.
     * @throws SalmonSecurityException Thrown when error with security
     */
    async init(key, nonce) {
        await super.init(key, nonce);
        try {
            __classPrivateFieldSet(this, _SalmonDefaultTransformer_encSecretKey, await crypto.subtle.importKey("raw", key, "AES-CTR", false, ["encrypt", "decrypt"]), "f");
        }
        catch (e) {
            throw new SalmonSecurityException("Could not init AES transformer", e);
        }
    }
    /**
     * Encrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return {Promise<number>} The number of bytes transformed.
     * @throws SalmonSecurityException Thrown when error with security
     */
    async encryptData(srcBuffer, srcOffset, destBuffer, destOffset, count) {
        if (__classPrivateFieldGet(this, _SalmonDefaultTransformer_encSecretKey, "f") == null)
            throw new SalmonSecurityException("No key defined, run init first");
        try {
            let counter = this.getCounter();
            let data = new Uint8Array(await crypto.subtle.encrypt({
                name: "AES-CTR",
                counter: counter,
                length: 64,
            }, __classPrivateFieldGet(this, _SalmonDefaultTransformer_encSecretKey, "f"), srcBuffer));
            for (let i = 0; i < count; i++)
                destBuffer[destOffset + i] = data[srcOffset + i];
            return data.length;
        }
        catch (ex) {
            throw new SalmonSecurityException("Could not encrypt data: ", ex);
        }
    }
    /**
     * Decrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return {Promise<number>} The number of bytes transformed.
     * @throws SalmonSecurityException Thrown when error with security
     */
    async decryptData(srcBuffer, srcOffset, destBuffer, destOffset, count) {
        if (__classPrivateFieldGet(this, _SalmonDefaultTransformer_encSecretKey, "f") == null)
            throw new SalmonSecurityException("No key defined, run init first");
        try {
            let counter = this.getCounter();
            let data = new Uint8Array(await crypto.subtle.encrypt({
                name: "AES-CTR",
                counter: counter,
                length: 64,
            }, __classPrivateFieldGet(this, _SalmonDefaultTransformer_encSecretKey, "f"), srcBuffer));
            for (let i = 0; i < count; i++)
                destBuffer[destOffset + i] = data[srcOffset + i];
            return data.length;
        }
        catch (ex) {
            throw new SalmonSecurityException("Could not decrypt data: ", ex);
        }
    }
}
_SalmonDefaultTransformer_encSecretKey = new WeakMap();
