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
import { AESCTRTransformer } from "./aes_ctr_transformer.js";

/**
 * Salmon AES transformer based on the javax.crypto routines.
 */
export class AesDefaultTransformer extends AESCTRTransformer {

    /**
     * Key spec for the initial nonce (counter).
     */
    #encSecretKey: CryptoKey | null = null;

    /**
     * Initialize the default Java AES cipher transformer.
     * @param {Uint8Array} key The AES256 key to use.
     * @param {Uint8Array} nonce The nonce to use.
     * @throws SalmonSecurityException Thrown when error with security
     */
    public async init(key: Uint8Array, nonce: Uint8Array): Promise<void> {
        await super.init(key, nonce);
        try {
            this.#encSecretKey = await crypto.subtle.importKey(
                "raw", key, "AES-CTR", false, ["encrypt", "decrypt"]);
        } catch (e) {
            throw new SecurityException("Could not init AES transformer", e);
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
    public async encryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        if (this.#encSecretKey == null)
            throw new SecurityException("No key defined, run init first");
        try {
            let counter: Uint8Array = this.getCounter();
            let data = new Uint8Array(await crypto.subtle.encrypt(
                {
                    name: "AES-CTR",
                    counter: counter,
                    length: 64,
                },
                this.#encSecretKey,
                srcBuffer,
            ));
            for (let i = 0; i < count; i++)
                destBuffer[destOffset + i] = data[srcOffset + i];
            return data.length;
        } catch (ex) {
            throw new SecurityException("Could not encrypt data: ", ex);
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
    public async decryptData(srcBuffer: Uint8Array, srcOffset: number,
        destBuffer: Uint8Array, destOffset: number, count: number): Promise<number> {
        if (this.#encSecretKey == null)
            throw new SecurityException("No key defined, run init first");
        try {
            let counter: Uint8Array = this.getCounter();
            let data = new Uint8Array(await crypto.subtle.encrypt(
                {
                    name: "AES-CTR",
                    counter: counter,
                    length: 64,
                },
                this.#encSecretKey,
                srcBuffer,
            ));
            for (let i = 0; i < count; i++)
                destBuffer[destOffset + i] = data[srcOffset + i];
            return data.length;
        } catch (ex) {
            throw new SecurityException("Could not decrypt data: ", ex);
        }
    }
}