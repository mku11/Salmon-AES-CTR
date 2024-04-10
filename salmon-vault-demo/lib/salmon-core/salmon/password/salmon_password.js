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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _a, _SalmonPassword_ENABLE_SHA1, _SalmonPassword_pbkdfAlgo, _SalmonPassword_provider;
import { PbkdfAlgo } from "./pbkdf_algo.js";
import { SalmonDefaultPbkdfProvider } from "./salmon_default_pbkdf_provider.js";
import { SalmonPbkdfFactory } from "./salmon_pbkdf_factory.js";
/**
 * Generates security keys based on text passwords.
 */
export class SalmonPassword {
    /**
     * Returns the current global PBKDF algorithm.
     *
     * @return {PbkdfAlgo} The PBKDF algorithm to be used.
     */
    static getPbkdfAlgo() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonPassword_pbkdfAlgo);
    }
    /**
     * Set the global PDKDF algorithm to be used for key derivation.
     *
     * @param {PbkdfAlgo} pbkdfAlgo The Pbkdf algorithm
     */
    static setPbkdfAlgo(pbkdfAlgo) {
        __classPrivateFieldSet(_a, _a, pbkdfAlgo, "f", _SalmonPassword_pbkdfAlgo);
    }
    /**
     * Set the global PBKDF implementation to be used for text key derivation.
     *
     * @param {PbkdfType} pbkdfType The pbkdf implementation type.
     */
    static setPbkdfType(pbkdfType) {
        __classPrivateFieldSet(_a, _a, SalmonPbkdfFactory.create(pbkdfType), "f", _SalmonPassword_provider);
    }
    /**
     * Set the global PBKDF provider to be used for text key derivation.
     *
     * @param {ISalmonPbkdfProvider} pbkdfProvider The PBKDF provider.
     */
    static setPbkdfProvider(pbkdfProvider) {
        __classPrivateFieldSet(_a, _a, pbkdfProvider, "f", _SalmonPassword_provider);
    }
    /**
     * Derives the key from a text password
     *
     * @param {string} pass       The text password to be used
     * @param {Uint8Array} salt       The salt to be used for the key derivation
     * @param {number} iterations The number of iterations the key derivation algorithm will use
     * @param {number} length     The length of master key to return
     * @return {Promise<Uint8Array>} The derived master key.
     * @throws SalmonSecurityException Thrown when error with security
     */
    static async getMasterKey(pass, salt, iterations, length) {
        let masterKey = await _a.getKeyFromPassword(pass, salt, iterations, length);
        return masterKey;
    }
    /**
     * Function will derive a key from a text password
     *
     * @param {string} password    The password that will be used to derive the key
     * @param {Uint8Array} salt        The salt byte array that will be used together with the password
     * @param {number} iterations  The iterations to be used with Pbkdf2
     * @param {number} outputBytes The number of bytes for the key
     * @return {Promise<Uint8Array>} The derived key.
     * @throws SalmonSecurityException Thrown when error with security
     */
    static async getKeyFromPassword(password, salt, iterations, outputBytes) {
        if (__classPrivateFieldGet(_a, _a, "f", _SalmonPassword_pbkdfAlgo) == PbkdfAlgo.SHA1 && !__classPrivateFieldGet(_a, _a, "f", _SalmonPassword_ENABLE_SHA1))
            throw new Error("Cannot use SHA1, SHA1 is not secure anymore use SHA256!");
        return __classPrivateFieldGet(_a, _a, "f", _SalmonPassword_provider).getKey(password, salt, iterations, outputBytes, __classPrivateFieldGet(_a, _a, "f", _SalmonPassword_pbkdfAlgo));
    }
}
_a = SalmonPassword;
/**
 * WARNING! SHA1 is not secure anymore enable only if you know what you're doing!
 */
_SalmonPassword_ENABLE_SHA1 = { value: false };
/**
 * Global PBKDF algorithm option that will be used for the master key derivation.
 */
_SalmonPassword_pbkdfAlgo = { value: PbkdfAlgo.SHA256 };
/**
 * Pbkdf provider.
 */
_SalmonPassword_provider = { value: new SalmonDefaultPbkdfProvider() };
