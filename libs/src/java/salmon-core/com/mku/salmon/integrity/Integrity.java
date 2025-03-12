package com.mku.salmon.integrity;
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

import com.mku.salmon.Generator;
import com.mku.salmon.SecurityException;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.transform.AesCTRTransformer;

import java.util.LinkedList;
import java.util.List;

/**
 * Provide operations for calculating, storing, and verifying data integrity.
 * This class operates on chunks of byte arrays calculating hashes for each one.
 */
public class Integrity {
    /**
     * Maximum chunk size for data integrity.
     */
    public static final int MAX_CHUNK_SIZE = 8 * 1024 * 1024;
    /**
     * Default chunk size for integrity.
     */
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;

    /**
     * The chunk size to be used for integrity.
     */
    private int chunkSize = -1;

    /**
     * Key to be used for integrity signing and validation.
     */
    private final byte[] key;

    /**
     * Hash result size;
     */
    private final int hashSize;

    /**
     * The hash provider.
     */
    private final IHashProvider provider;

    private final boolean integrity;

    /**
     * Instantiate an object to be used for applying and verifying hash signatures for each of the data chunks.
     *
     * @param integrity True to enable integrity checks.
     * @param key       The key to use for hashing.
     * @param chunkSize The chunk size. Use 0 to enable integrity on the whole file (1 chunk).
     *                  Use a positive number to specify integrity chunks.
     * @param provider  Hash implementation provider.
     * @param hashSize  The hash size.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws SecurityException  When security has failed
     */
    public Integrity(boolean integrity, byte[] key, int chunkSize, IHashProvider provider, int hashSize) {
        if (chunkSize < 0 || (chunkSize > 0 && chunkSize < AesCTRTransformer.BLOCK_SIZE)
                || (chunkSize > 0 && chunkSize % AesCTRTransformer.BLOCK_SIZE != 0) || chunkSize > MAX_CHUNK_SIZE) {
            throw new IntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                    + AesCTRTransformer.BLOCK_SIZE + " and less than: " + Integrity.MAX_CHUNK_SIZE + " bytes");
        }
        if (integrity && key == null)
            throw new SecurityException("You need a hash to use with integrity");
        if (integrity && chunkSize == 0)
            this.chunkSize = DEFAULT_CHUNK_SIZE;
        else if (integrity || chunkSize > 0)
            this.chunkSize = chunkSize;
        if (hashSize < 0)
            throw new SecurityException("Hash size should be a positive number");
        this.key = key;
        this.provider = provider;
        this.integrity = integrity;
        this.hashSize = hashSize;
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
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    //TODO: we should avoid the header data for performance?
    public static byte[] calculateHash(IHashProvider provider, byte[] buffer, int offset, int count,
                                       byte[] key, byte[] includeData) {

        byte[] finalBuffer = buffer;
        int finalOffset = offset;
        int finalCount = count;
        if (includeData != null) {
            finalBuffer = new byte[count + includeData.length];
            finalCount = count + includeData.length;
            System.arraycopy(includeData, 0, finalBuffer, 0, includeData.length);
            System.arraycopy(buffer, offset, finalBuffer, includeData.length, count);
            finalOffset = 0;
        }
        byte[] hashValue = provider.calc(key, finalBuffer, finalOffset, finalCount);
        return hashValue;
    }

    /**
     * Get the total number of bytes for all hash signatures for data of a specific length.
     *
     * @param length     The length of the data.
     * @param chunkSize  The byte size of the stream chunk that will be used to calculate the hash.
     *                   The length should be fixed value except for the last chunk which might be lesser since we don't use padding
     * @param hashOffset The hash key length that will be used as an offset.
     * @param hashLength The hash length.
     * @return The total hash data length
     */
    public static long getTotalHashDataLength(EncryptionMode mode, long length, int chunkSize,
                                              int hashOffset, int hashLength) {
        if (mode == EncryptionMode.Decrypt) {
            int chunks = (int) Math.floor(length / (chunkSize + hashOffset));
            int rem = (int) (length % (chunkSize + hashOffset));
            if (rem > hashOffset)
                chunks++;
            return (long) chunks * hashLength;
        } else {
            int chunks = (int) Math.floor(length / chunkSize);
            int rem = (int) (length % chunkSize);
            if (rem > hashOffset)
                chunks++;
            return (long) chunks * hashLength;
        }
    }

    /**
     * Return the number of bytes that all hash signatures occupy for each chunk size
     *
     * @param count      Actual length of the real data int the base stream including header and hash signatures.
     * @param hashOffset The hash key length
     * @return The number of bytes all hash signatures occupy
     */
    public long getHashDataLength(long count, int hashOffset) {
        if (chunkSize <= 0)
            return 0;
        return Integrity.getTotalHashDataLength(EncryptionMode.Decrypt, count, chunkSize, hashOffset, hashSize);
    }

    /**
     * Get the chunk size.
     *
     * @return The chunk size.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Get the hash key.
     *
     * @return The hash key.
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Get the integrity enabled option.
     *
     * @return True if integrity is enabled.
     */
    public boolean useIntegrity() {
        return integrity;
    }

    /**
     * Generate a hash signatures for each data chunk.
     *
     * @param buffer            The buffer containing the data chunks.
     * @param includeHeaderData Include the header data in the first chunk.
     * @return The hash signatures.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public byte[][] generateHashes(byte[] buffer, byte[] includeHeaderData) {
        if (!integrity)
            return null;
        List<byte[]> hashes = new LinkedList<>();
        for (int i = 0; i < buffer.length; i += chunkSize) {
            int len = Math.min(chunkSize, buffer.length - i);
            hashes.add(calculateHash(provider, buffer, i, len, getKey(), i == 0 ? includeHeaderData : null));
        }
        return hashes.toArray(new byte[][]{});
    }

    /**
     * Get the hashes for each data chunk.
     *
     * @param buffer The buffer that contains the data chunks.
     * @return The hash signatures.
     */
    public byte[][] getHashes(byte[] buffer) {
        if (!integrity)
            return null;
        List<byte[]> hashes = new LinkedList<>();
        for (int i = 0; i < buffer.length; i += Generator.HASH_KEY_LENGTH + chunkSize) {
            byte[] hash = new byte[Generator.HASH_KEY_LENGTH];
            System.arraycopy(buffer, i, hash, 0, Generator.HASH_KEY_LENGTH);
            hashes.add(hash);
        }
        return hashes.toArray(new byte[][]{});
    }

    /**
     * Verify the buffer chunks against the hash signatures.
     *
     * @param hashes            The hashes to verify.
     * @param buffer            The buffer that contains the chunks to verify the hashes.
     * @param includeHeaderData The header data to include
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public void verifyHashes(byte[][] hashes, byte[] buffer, byte[] includeHeaderData) {
        int chunk = 0;
        for (int i = 0; i < buffer.length; i += chunkSize) {
            int nChunkSize = Math.min(chunkSize, buffer.length - i);
            byte[] hash = calculateHash(provider, buffer, i, nChunkSize, getKey(), i == 0 ? includeHeaderData : null);
            for (int k = 0; k < hash.length; k++) {
                if (hash[k] != hashes[chunk][k]) {
                    throw new IntegrityException("Data corrupt or tampered");
                }
            }
            chunk++;
        }
    }
}