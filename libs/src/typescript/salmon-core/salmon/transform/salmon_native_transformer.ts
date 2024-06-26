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
import { INativeProxy } from "../../bridge/inative_proxy.js";
import { SalmonNativeProxy } from "../bridge/salmon_native_proxy.js";
import { SalmonAES256CTRTransformer } from "./salmon_aes256_ctr_transformer.js";

/**
 * Generic Native AES transformer. Extend this with your specific 
 * native transformer.
 */ 
export class SalmonNativeTransformer extends SalmonAES256CTRTransformer {
    static #nativeProxy: INativeProxy = new SalmonNativeProxy();

    /**
     * The native proxy to use for loading libraries for different platforms and operating systems.
     * @param {INativeProxy} proxy The proxy.
     */
    public static setNativeProxy(proxy: INativeProxy): void {
        SalmonNativeTransformer.#nativeProxy = proxy;
    }

    /**
     * The current proxy used for loading native library.
     * @returns {INativeProxy} The proxy.
     */
    public static getNativeProxy(): INativeProxy  {
        return SalmonNativeTransformer.#nativeProxy;
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
    public override async encryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        let key: Uint8Array | null = this.getExpandedKey();
        let ctr: Uint8Array | null = this.getCounter();

        if (key == null)
            throw new SalmonSecurityException("No key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        return SalmonNativeTransformer.#nativeProxy.transform(key, ctr,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
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
    public override async decryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        let key: Uint8Array | null = this.getExpandedKey();
        let ctr: Uint8Array | null = this.getCounter();

        if (key == null)
            throw new SalmonSecurityException("No key found, run init first");
        if (ctr == null)
            throw new SalmonSecurityException("No counter found, run init first");
        return SalmonNativeTransformer.#nativeProxy.transform(key, ctr,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
    }
}