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
import { INativeProxy } from "../bridge/inative_proxy.js";
import { NativeProxy } from "../bridge/native_proxy.js";
import { AESCTRTransformer } from "./aes_ctr_transformer.js";

/**
 * Generic Native AES transformer. Extend this with your specific 
 * native transformer.
 */ 
export class AesNativeTransformer extends AESCTRTransformer {
    static #nativeProxy: INativeProxy = new NativeProxy();

    /**
     * The native proxy to use for loading libraries for different platforms and operating systems.
     * @param {INativeProxy} proxy The proxy.
     */
    public static setNativeProxy(proxy: INativeProxy): void {
        AesNativeTransformer.#nativeProxy = proxy;
    }

    /**
     * The current proxy used for loading native library.
     * @returns {INativeProxy} The proxy.
     */
    public static getNativeProxy(): INativeProxy  {
        return AesNativeTransformer.#nativeProxy;
    }

    #implType: number;
    /**
     * 
     * @returns {number} The native implementation type see ProviderType enum
     */
    public getImplType(): number {
        return this.#implType;
    }
    /**
     * 
     * @param {number} implType The native implementation type see ProviderType enum
     */
    public setImplType(implType: number) {
        this.#implType = implType;
    }

    /**
     * Construct a SalmonNativeTransformer for using the native aes c library
     * @param {number} implType The AES native implementation see ProviderType enum
     */
    public constructor(implType: number) {
        super();
        this.#implType = implType;
    }

    /**
     * Initialize the native Aes intrinsics transformer.
     * @param {Uint8Array} key The AES key to use.
     * @param {Uint8Array} nonce The nonce to use.
     * @throws SalmonSecurityException Thrown when error with security
     */
    public async init(key: Uint8Array, nonce: Uint8Array): Promise<void> {
        await AesNativeTransformer.getNativeProxy().init(this.#implType);
        let expandedKey: Uint8Array = new Uint8Array(AESCTRTransformer.EXPANDED_KEY_SIZE);
        AesNativeTransformer.getNativeProxy().expandKey(key, expandedKey);
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
     * @returns {Promise<number>} The number of bytes transformed.
     */
    public override async encryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        let key: Uint8Array | null = this.getExpandedKey();
        let ctr: Uint8Array | null = this.getCounter();

        if (key == null)
            throw new SecurityException("No key found, run init first");
        if (ctr == null)
            throw new SecurityException("No counter found, run init first");
        return AesNativeTransformer.#nativeProxy.transform(key, ctr,
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
     * @returns {Promise<number>} The number of bytes transformed.
     */
    public override async decryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        let key: Uint8Array | null = this.getExpandedKey();
        let ctr: Uint8Array | null = this.getCounter();

        if (key == null)
            throw new SecurityException("No key found, run init first");
        if (ctr == null)
            throw new SecurityException("No counter found, run init first");
        return AesNativeTransformer.#nativeProxy.transform(key, ctr,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
    }
}