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
var _SalmonIntegrity_chunkSize, _SalmonIntegrity_key, _SalmonIntegrity_hashSize, _SalmonIntegrity_provider, _SalmonIntegrity_integrity;
import { SalmonGenerator } from "../salmon_generator.js";
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "../transform/salmon_aes256_ctr_transformer.js";
import { IntegrityException } from "../../integrity/integrity_exception.js";
/**
 * Operations for calculating, storing, and verifying data integrity.
 * This class operates on chunks of byte arrays calculating hashes for each one.
 */
export class SalmonIntegrity {
    /**
     * Instantiate an object to be used for applying and verifying hash signatures for each of the data chunks.
     *
     * @param {boolean} integrity True to enable integrity checks.
     * @param {Uint8Array} key       The key to use for hashing.
     * @param {number | null} chunkSize The chunk size. Use 0 to enable integrity on the whole file (1 chunk).
     *                  Use a positive number to specify integrity chunks.
     * @param {IHashProvider} provider  Hash implementation provider.
     * @param {number} hashSize The hash size.
     * @throws IntegrityException When integrity is comprimised
     * @throws SalmonSecurityException When security has failed
     */
    constructor(integrity, key, chunkSize = null, provider, hashSize = 0) {
        /**
         * The chunk size to be used for integrity.
         */
        _SalmonIntegrity_chunkSize.set(this, -1);
        /**
         * Key to be used for integrity signing and validation.
         */
        _SalmonIntegrity_key.set(this, null);
        /**
         * Hash result size;
         */
        _SalmonIntegrity_hashSize.set(this, 0);
        /**
         * The hash provider.
         */
        _SalmonIntegrity_provider.set(this, void 0);
        _SalmonIntegrity_integrity.set(this, void 0);
        if (chunkSize !== null && (chunkSize < 0 || (chunkSize > 0 && chunkSize < SalmonAES256CTRTransformer.BLOCK_SIZE)
            || (chunkSize > 0 && chunkSize % SalmonAES256CTRTransformer.BLOCK_SIZE != 0) || chunkSize > SalmonIntegrity.MAX_CHUNK_SIZE)) {
            throw new IntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                + SalmonAES256CTRTransformer.BLOCK_SIZE + " and less than: " + SalmonIntegrity.MAX_CHUNK_SIZE + " bytes");
        }
        if (integrity && key == null)
            throw new SalmonSecurityException("You need a hash key to use with integrity");
        if (integrity && (chunkSize === null || chunkSize == 0))
            __classPrivateFieldSet(this, _SalmonIntegrity_chunkSize, SalmonIntegrity.DEFAULT_CHUNK_SIZE, "f");
        else if (chunkSize != null && (integrity || chunkSize > 0))
            __classPrivateFieldSet(this, _SalmonIntegrity_chunkSize, chunkSize, "f");
        if (hashSize < 0)
            throw new SalmonSecurityException("Hash size should be a positive number");
        __classPrivateFieldSet(this, _SalmonIntegrity_key, key, "f");
        __classPrivateFieldSet(this, _SalmonIntegrity_provider, provider, "f");
        __classPrivateFieldSet(this, _SalmonIntegrity_integrity, integrity, "f");
        __classPrivateFieldSet(this, _SalmonIntegrity_hashSize, hashSize, "f");
    }
    /**
     * Calculate hash of the data provided.
     *
     * @param {IHashProvider} provider    Hash implementation provider.
     * @param {Uint8Array} buffer      Data to calculate the hash.
     * @param {number} offset      Offset of the buffer that the hashing calculation will start from
     * @param {number} count       Length of the buffer that will be used to calculate the hash.
     * @param {Uint8Array} key         Key that will be used
     * @param {Uint8Array | null} includeData Additional data to be included in the calculation.
     * @return {Promise<Uint8Array>} The hash.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    static async calculateHash(provider, buffer, offset, count, key, includeData) {
        let finalBuffer = buffer;
        let finalOffset = offset;
        let finalCount = count;
        if (includeData != null) {
            finalBuffer = new Uint8Array(count + includeData.length);
            finalCount = count + includeData.length;
            for (let i = 0; i < includeData.length; i++)
                finalBuffer[i] = includeData[i];
            for (let i = 0; i < count; i++)
                finalBuffer[includeData.length + i] = buffer[offset + i];
            finalOffset = 0;
        }
        const hashValue = await provider.calc(key, finalBuffer, finalOffset, finalCount);
        return hashValue;
    }
    /**
     * Get the total number of bytes for all hash signatures for data of a specific length.
     * @param {number} length 		The length of the data.
     * @param {number} chunkSize      The byte size of the stream chunk that will be used to calculate the hash.
     *                       The length should be fixed value except for the last chunk which might be lesser since we don't use padding
     * @param {number} hashOffset     The hash key length that will be used as an offset.
     * @param {number} hashLength     The hash length.
     * @return {number} The total number of bytes for all hash signatures.
     */
    static getTotalHashDataLength(length, chunkSize, hashOffset, hashLength) {
        // if the stream is using multiple chunks for integrity
        let chunks = Math.floor(length / (chunkSize + hashOffset));
        const rem = Math.floor(length % (chunkSize + hashOffset));
        if (rem > hashOffset)
            chunks++;
        return chunks * hashLength;
    }
    /**
     * Return the number of bytes that all hash signatures occupy for each chunk size
     *
     * @param {number} count      Actual length of the real data int the base stream including header and hash signatures.
     * @param {number} hashOffset The hash key length
     * @return {number} The number of bytes all hash signatures occupy
     */
    getHashDataLength(count, hashOffset) {
        if (__classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f") <= 0)
            return 0;
        return SalmonIntegrity.getTotalHashDataLength(count, __classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f"), hashOffset, __classPrivateFieldGet(this, _SalmonIntegrity_hashSize, "f"));
    }
    /**
     * Get the chunk size.
     * @return {number} The chunk size.
     */
    getChunkSize() {
        return __classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f");
    }
    /**
     * Get the hash key.
     * @return {Uint8Array} The hash key.
     */
    getKey() {
        if (__classPrivateFieldGet(this, _SalmonIntegrity_key, "f") == null)
            throw new SalmonSecurityException("Key is missing");
        return __classPrivateFieldGet(this, _SalmonIntegrity_key, "f");
    }
    /**
     * Get the integrity enabled option.
     * @return {boolean} True if integrity is enabled.
     */
    useIntegrity() {
        return __classPrivateFieldGet(this, _SalmonIntegrity_integrity, "f");
    }
    /**
     * Generate a hash signatures for each data chunk.
     * @param {Uint8Array} buffer The buffer containing the data chunks.
     * @param {boolean} includeHeaderData Include the header data in the first chunk.
     * @return {Promise<Uint8Array[] | null>} The hash signatures.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    async generateHashes(buffer, includeHeaderData) {
        if (!__classPrivateFieldGet(this, _SalmonIntegrity_integrity, "f"))
            return null;
        const hashes = [];
        for (let i = 0; i < buffer.length; i += __classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f")) {
            const len = Math.min(__classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f"), buffer.length - i);
            hashes.push(await SalmonIntegrity.calculateHash(__classPrivateFieldGet(this, _SalmonIntegrity_provider, "f"), buffer, i, len, this.getKey(), i == 0 ? includeHeaderData : null));
        }
        return hashes;
    }
    /**
     * Get the hashes for each data chunk.
     * @param {Uint8Array} buffer The buffer that contains the data chunks.
     * @return {Uint8Array[] | null} The hash signatures.
     */
    getHashes(buffer) {
        if (!__classPrivateFieldGet(this, _SalmonIntegrity_integrity, "f"))
            return null;
        let hashes = new Array();
        for (let i = 0; i < buffer.length; i += SalmonGenerator.HASH_KEY_LENGTH + __classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f")) {
            let hash = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
            for (let j = 0; j < SalmonGenerator.HASH_KEY_LENGTH; j++)
                hash[j] = buffer[i + j];
            hashes.push(hash);
        }
        return hashes;
    }
    /**
     * Verify the buffer chunks against the hash signatures.
     * @param {Uint8Array[]} hashes The hashes to verify.
     * @param {number} buffer The buffer that contains the chunks to verify the hashes.
     * @param {boolean| null} includeHeaderData
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    async verifyHashes(hashes, buffer, includeHeaderData) {
        let chunk = 0;
        for (let i = 0; i < buffer.length; i += __classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f")) {
            let nChunkSize = Math.min(__classPrivateFieldGet(this, _SalmonIntegrity_chunkSize, "f"), buffer.length - i);
            let hash = await SalmonIntegrity.calculateHash(__classPrivateFieldGet(this, _SalmonIntegrity_provider, "f"), buffer, i, nChunkSize, this.getKey(), i == 0 ? includeHeaderData : null);
            for (let k = 0; k < hash.length; k++) {
                if (hash[k] != hashes[chunk][k]) {
                    throw new IntegrityException("Data corrupt or tampered");
                }
            }
            chunk++;
        }
    }
}
_SalmonIntegrity_chunkSize = new WeakMap(), _SalmonIntegrity_key = new WeakMap(), _SalmonIntegrity_hashSize = new WeakMap(), _SalmonIntegrity_provider = new WeakMap(), _SalmonIntegrity_integrity = new WeakMap();
/**
 * Maximum chunk size for data integrity.
 */
SalmonIntegrity.MAX_CHUNK_SIZE = 8 * 1024 * 1024;
/**
 * Default chunk size for integrity.
 */
SalmonIntegrity.DEFAULT_CHUNK_SIZE = 256 * 1024;
