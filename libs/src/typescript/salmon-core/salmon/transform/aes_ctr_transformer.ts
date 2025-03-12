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

import { Generator } from "../generator.js";
import { RangeExceededException } from "../range_exceeded_exception.js";
import { SecurityException } from "../security_exception.js";
import { ISalmonCTRTransformer } from "./ictr_transformer.js";

/**
 * Abstract class for AES256 transformer implementations.
 *
 */
export abstract class AESCTRTransformer implements ISalmonCTRTransformer {

    /**
     * Standard expansion key size for AES256 only.
     */
    public static readonly EXPANDED_KEY_SIZE: number = 240;

    /**
     * Encrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return The number of bytes transformed.
     */
    public abstract encryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;

    /**
     * Decrypt the data.
     * @param {Uint8Array} srcBuffer The source byte array.
     * @param {number} srcOffset The source byte offset.
     * @param {Uint8Array} destBuffer The destination byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count The number of bytes to transform.
     * @return {Promise<number>} The number of bytes transformed.
     */
    public abstract decryptData(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): Promise<number>;
    
    /**
     * Salmon stream encryption block size, same as AES.
     */
    public static readonly BLOCK_SIZE: number = 16;

    /**
     * Key to be used for AES transformation.
     */
    #key: Uint8Array | null = null;

    /**
     * Expanded key.
     */
    #expandedKey: Uint8Array = new Uint8Array(AESCTRTransformer.EXPANDED_KEY_SIZE);

    /**
     * Nonce to be used for CTR mode.
     */
    #nonce: Uint8Array | null = null;

    /**
     * Current operation block.
     */
    #block: number = 0;

    /**
     * Current operation counter.
     */
    #counter: Uint8Array | null = null;

    /**
     * Resets the Counter and the block count.
     */
    public resetCounter(): void {
        if (this.#nonce == null)
            throw new SecurityException("No counter, run init first");
        this.#counter = new Uint8Array(AESCTRTransformer.BLOCK_SIZE);
        for (let i = 0; i < this.#nonce.length; i++)
            this.#counter[i] = this.#nonce[i];
        this.#block = 0;
    }

    /**
     * Syncs the Counter based on what AES block position the stream is at.
     * The block count is already excluding the header and the hash signatures.
     * @param {number} position The new position to sync to
     */
    public syncCounter(position: number): void {
        let currBlock: number = Math.floor(position / AESCTRTransformer.BLOCK_SIZE);
        this.resetCounter();
        this.increaseCounter(currBlock);
        this.#block = currBlock;
    }

    /**
     * Increase the Counter
     * We use only big endianness for AES regardless of the machine architecture
     *
     * @param {number} value value to increase counter by
     */
    public increaseCounter(value: number): void {
        if (this.#counter == null || this.#nonce == null)
            throw new SecurityException("No counter, run init first");
        if (value < 0)
            throw new Error("Value should be positive");
        // Javascript has its own limit for safe integer math
        if (value > Number.MAX_SAFE_INTEGER)
            throw new RangeExceededException("Current CTR max safe blocks exceeded");
        let index: number = AESCTRTransformer.BLOCK_SIZE - 1;
        let carriage: number = 0;
        while (index >= 0 && value + carriage > 0) {
            if (index <= AESCTRTransformer.BLOCK_SIZE - Generator.NONCE_LENGTH)
                throw new RangeExceededException("Current CTR max blocks exceeded");
            let val: number = (value + carriage) % 256;
            carriage = Math.floor(((this.#counter[index] & 0xFF) + val) / 256);
            this.#counter[index--] += val;
            value = Math.floor(value / 256);
        }
    }

    /**
     * Initialize the transformer. Most common operations include precalculating expansion keys or
     * any other prior initialization for efficiency.
     * @param {Uint8Array} key The key
     * @param {Uint8Array} nonce The nonce
     * @throws SalmonSecurityException Thrown when error with security
     */
    public async init(key: Uint8Array, nonce: Uint8Array): Promise<void> {
        this.#key = key;
        this.#nonce = nonce;
    }

    /**
     * Get the current counter.
     * @return {Uint8Array} The current counter.
     */
    public getCounter(): Uint8Array {
        if (this.#counter == null)
            throw new Error("No counter, run init() and resetCounter()");
        return this.#counter;
    }

    /**
     * Get the current block.
     * @return {number} The current block.
     */
    public getBlock(): number {
        return this.#block;
    }

    /**
     * Get the current encryption key.
     * @return {Uint8Array | null} The encryption key.
     */
    public getKey(): Uint8Array | null{
        return this.#key;
    }

    /**
     * Get the expanded key if available.
     * @return {Uint8Array | null} The expanded key.
     */
    protected getExpandedKey(): Uint8Array | null {
        return this.#expandedKey;
    }

    /**
     * Get the nonce (initial counter)
     * @return {Uint8Array | null} The nonce.
     */
    public getNonce(): Uint8Array | null{
        return this.#nonce;
    }

    /**
     * Set the expanded key. This should be called once during initialization phase.
     * @param {Uint8Array} expandedKey The expanded key
     */
    public setExpandedKey(expandedKey: Uint8Array): void {
        this.#expandedKey = expandedKey;
    }
}