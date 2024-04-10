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
var _a, _SalmonNativeTransformer_nativeProxy;
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonNativeProxy } from "../bridge/salmon_native_proxy.js";
import { SalmonAES256CTRTransformer } from "./salmon_aes256_ctr_transformer.js";
/**
 * Generic Native AES transformer. Extend this with your specific
 * native transformer.
 */
export class SalmonNativeTransformer extends SalmonAES256CTRTransformer {
    /**
     * The native proxy to use for loading libraries for different platforms and operating systems.
     * @param {INativeProxy} proxy The proxy.
     */
    static setNativeProxy(proxy) {
        __classPrivateFieldSet(_a, _a, proxy, "f", _SalmonNativeTransformer_nativeProxy);
    }
    /**
     * The current proxy used for loading native library.
     * @returns {INativeProxy} The proxy.
     */
    static getNativeProxy() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonNativeTransformer_nativeProxy);
    }
    /**
     * Encrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return The number of bytes transformed.
     */
    async encryptData(srcBuffer, srcOffset, destBuffer, destOffset, count) {
        let key = this.getExpandedKey();
        let ctr = this.getCounter();
        if (key == null)
            throw new SalmonSecurityException("No key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        return __classPrivateFieldGet(_a, _a, "f", _SalmonNativeTransformer_nativeProxy).transform(key, ctr, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
    /**
     * Decrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return {Promise<number>} The number of bytes transformed.
     */
    async decryptData(srcBuffer, srcOffset, destBuffer, destOffset, count) {
        let key = this.getExpandedKey();
        let ctr = this.getCounter();
        if (key == null)
            throw new SalmonSecurityException("No key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        return __classPrivateFieldGet(_a, _a, "f", _SalmonNativeTransformer_nativeProxy).transform(key, ctr, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}
_a = SalmonNativeTransformer;
_SalmonNativeTransformer_nativeProxy = { value: new SalmonNativeProxy() };
