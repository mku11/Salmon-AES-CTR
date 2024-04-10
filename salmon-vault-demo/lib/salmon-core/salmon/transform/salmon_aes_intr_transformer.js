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
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "./salmon_aes256_ctr_transformer.js";
import { SalmonNativeTransformer } from "./salmon_native_transformer.js";
/**
 * Salmon AES transformer implemented with AES intrinsics.
 */
export class SalmonAesIntrTransformer extends SalmonNativeTransformer {
    /**
     * Initialize the native Aes intrinsics transformer.
     * @param {Uint8Array} key The AES key to use.
     * @param {Uint8Array} nonce The nonce to use.
     * @throws SalmonSecurityException Thrown when error with security
     */
    async init(key, nonce) {
        SalmonAesIntrTransformer.getNativeProxy().init(SalmonAesIntrTransformer.AES_IMPL_AES_INTR);
        let expandedKey = new Uint8Array(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE);
        SalmonAesIntrTransformer.getNativeProxy().expandKey(key, expandedKey);
        this.setExpandedKey(expandedKey);
        await super.init(key, nonce);
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
        let expKey = this.getExpandedKey();
        let ctr = this.getCounter();
        if (expKey == null)
            throw new SalmonSecurityException("No expanded key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        // AES intrinsics needs the expanded key
        return SalmonAesIntrTransformer.getNativeProxy().transform(expKey, ctr, srcBuffer, srcOffset, destBuffer, destOffset, count);
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
        let expKey = this.getExpandedKey();
        let ctr = this.getCounter();
        if (expKey == null)
            throw new SalmonSecurityException("No expanded key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        // AES intrinsics needs the expanded key
        return SalmonAesIntrTransformer.getNativeProxy().transform(expKey, ctr, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}
/**
 * The constant to pass to the native code while initializing.
 */
SalmonAesIntrTransformer.AES_IMPL_AES_INTR = 1;
