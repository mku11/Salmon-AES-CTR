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

import { SalmonGenerator } from "../salmon_generator.js";
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "../transform/salmon_aes256_ctr_transformer.js";
import { IHashProvider } from "./ihash_provider.js";
import { SalmonIntegrityException } from "./salmon_integrity_exception.js";

/**
 * Provide operations for calculating, storing, and verifying data integrity.
 * This class operates in chunks of data in buffers calculating the hash for each one.
 */
export class SalmonIntegrity {
    /**
     * Maximum chunk size for data integrity.
     */
    public static readonly MAX_CHUNK_SIZE: number = 8 * 1024 * 1024;
    /**
     * Default chunk size for integrity.
     */
    public static readonly DEFAULT_CHUNK_SIZE: number = 256 * 1024;

    /**
     * The chunk size to be used for integrity.
     */
    #chunkSize: number = -1;

    /**
     * Key to be used for integrity signing and validation.
     */
    readonly #key: Uint8Array | null = null;

    /**
     * Hash result size;
     */
    readonly #hashSize: number = 0;

    /**
     * The hash provider.
     */
    readonly #provider: IHashProvider;

    readonly #integrity: boolean;

    /**
     * Instantiate an object to be used for applying and verifying hash signatures for each of the data chunks.
     *
     * @param integrity True to enable integrity checks.
     * @param key       The key to use for hashing.
     * @param chunkSize The chunk size. Use 0 to enable integrity on the whole file (1 chunk).
     *                  Use a positive number to specify integrity chunks.
     * @param provider  Hash implementation provider.
     * @param hashSize The hash size.
     * @throws SalmonIntegrityException When integrity is comprimised
     * @throws SalmonSecurityException When security has failed
     */
    public constructor(integrity: boolean, key: Uint8Array | null, chunkSize: number | null = null, provider: IHashProvider, hashSize: number = 0) {
        if (chunkSize !== null && (chunkSize < 0 || (chunkSize > 0 && chunkSize < SalmonAES256CTRTransformer.BLOCK_SIZE)
            || (chunkSize > 0 && chunkSize % SalmonAES256CTRTransformer.BLOCK_SIZE != 0) || chunkSize > SalmonIntegrity.MAX_CHUNK_SIZE)) {
            throw new SalmonIntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                + SalmonAES256CTRTransformer.BLOCK_SIZE + " and less than: " + SalmonIntegrity.MAX_CHUNK_SIZE + " bytes");
        }
        if (integrity && key == null)
            throw new SalmonSecurityException("You need a hash key to use with integrity");
        if (integrity && (chunkSize === null || chunkSize == 0))
            this.#chunkSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
        else if (chunkSize != null && (integrity || chunkSize > 0)) // TODO: ToSync
            this.#chunkSize = chunkSize;
        if (hashSize < 0)
            throw new SalmonSecurityException("Hash size should be a positive number");
        this.#key = key;
        this.#provider = provider;
        this.#integrity = integrity;
        this.#hashSize = hashSize;
    }

    /**
     * Calculate hash of the data provided.
     *
     * @param provider    Hash implementation provider.
     * @param buffer      Data to calculate the hash.
     * @param offset      Offset of the buffer that the hashing calculation will start from
     * @param count       Length of the buffer that will be used to calculate the hash.
     * @param key         Key that will be used
     * @param includeData Additional data to be included in the calculation.
     * @return The hash.
     * @throws SalmonIntegrityException
     */
    // TODO: we should avoid the header data for performance?
    public static async calculateHash(provider: IHashProvider, buffer: Uint8Array, offset: number, count: number,
        key: Uint8Array, includeData: Uint8Array | null): Promise<Uint8Array> {

        let finalBuffer: Uint8Array = buffer;
        let finalOffset: number = offset;
        let finalCount: number = count;
        if (includeData != null) {
            finalBuffer = new Uint8Array(count + includeData.length);
            finalCount = count + includeData.length;
            for (let i = 0; i < includeData.length; i++)
                finalBuffer[i] = includeData[i];
            for (let i = 0; i < count; i++)
                finalBuffer[includeData.length + i] = buffer[offset + i];
            finalOffset = 0;
        }
        const hashValue: Uint8Array = await provider.calc(key, finalBuffer, finalOffset, finalCount);
        return hashValue;
    }

    /**
     * Get the total number of bytes for all hash signatures for data of a specific length.
     * @param length 		The length of the data.
     * @param chunkSize      The byte size of the stream chunk that will be used to calculate the hash.
     *                       The length should be fixed value except for the last chunk which might be lesser since we don't use padding
     * @param hashOffset     The hash key length that will be used as an offset.
     * @param hashLength     The hash length.
     * @return
     */
    public static getTotalHashDataLength(length: number, chunkSize: number,
        hashOffset: number, hashLength: number): number {
        // if the stream is using multiple chunks for integrity
        let chunks: number = Math.floor(length / (chunkSize + hashOffset));
        const rem: number = Math.floor(length % (chunkSize + hashOffset));
        if (rem > hashOffset)
            chunks++;
        return chunks * hashLength;
    }

    /**
     * Return the number of bytes that all hash signatures occupy for each chunk size
     *
     * @param count      Actual length of the real data int the base stream including header and hash signatures.
     * @param hashOffset The hash key length
     * @return The number of bytes all hash signatures occupy
     */
    public getHashDataLength(count: number, hashOffset: number): number {
        if (this.#chunkSize <= 0)
            return 0;
        return SalmonIntegrity.getTotalHashDataLength(count, this.#chunkSize, hashOffset, this.#hashSize);
    }

    /**
     * Get the chunk size.
     * @return The chunk size.
     */
    public getChunkSize(): number {
        return this.#chunkSize;
    }

    /**
     * Get the hash key.
     * @return The hash key.
     */
    public getKey(): Uint8Array {
        if (this.#key == null)
            throw new SalmonSecurityException("Key is missing");
        return this.#key;
    }

    /**
     * Get the integrity enabled option.
     * @return True if integrity is enabled.
     */
    public useIntegrity(): boolean {
        return this.#integrity;
    }

    /**
     * Generate a hash signatures for each data chunk.
     * @param buffer The buffer containing the data chunks.
     * @param includeHeaderData Include the header data in the first chunk.
     * @return The hash signatures.
     * @throws SalmonIntegrityException
     */
    public async generateHashes(buffer: Uint8Array, includeHeaderData: Uint8Array | null): Promise<Uint8Array[] | null> {
        if (!this.#integrity)
            return null;
        const hashes: Uint8Array[] = [];
        for (let i: number = 0; i < buffer.length; i += this.#chunkSize) {
            const len: number = Math.min(this.#chunkSize, buffer.length - i);
            hashes.push(await SalmonIntegrity.calculateHash(this.#provider, buffer, i, len, this.getKey(), i == 0 ? includeHeaderData : null));
        }
        return hashes;
    }

    /**
     * Get the hashes for each data chunk.
     * @param buffer The buffer that contains the data chunks.
     * @return The hash signatures.
     */
    public getHashes(buffer: Uint8Array): Uint8Array[] | null {
        if (!this.#integrity)
            return null;
        let hashes: Uint8Array[] = new Array();
        for (let i: number = 0; i < buffer.length; i += SalmonGenerator.HASH_KEY_LENGTH + this.#chunkSize) {
            let hash: Uint8Array = new Uint8Array(SalmonGenerator.HASH_KEY_LENGTH);
            for (let j = 0; j < SalmonGenerator.HASH_KEY_LENGTH; j++)
                hash[j] = buffer[i + j];
            hashes.push(hash);
        }
        return hashes;
    }

    /**
     * Verify the buffer chunks against the hash signatures.
     * @param hashes The hashes to verify.
     * @param buffer The buffer that contains the chunks to verify the hashes.
     * @param includeHeaderData
     * @throws SalmonIntegrityException
     */
    public async verifyHashes(hashes: Uint8Array[], buffer: Uint8Array, includeHeaderData: Uint8Array | null) {
        let chunk: number = 0;
        for (let i: number = 0; i < buffer.length; i += this.#chunkSize) {
            let nChunkSize: number = Math.min(this.#chunkSize, buffer.length - i);
            let hash: Uint8Array = await SalmonIntegrity.calculateHash(this.#provider, buffer, i, nChunkSize, this.getKey(), i == 0 ? includeHeaderData : null);
            for (let k: number = 0; k < hash.length; k++) {
                if (hash[k] != hashes[chunk][k]) {
                    throw new SalmonIntegrityException("Data corrupt or tampered");
                }
            }
            chunk++;
        }
    }
}