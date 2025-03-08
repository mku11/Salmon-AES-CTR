package com.mku.salmon.transform;
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
import com.mku.salmon.RangeExceededException;
import com.mku.salmon.SecurityException;

/**
 * Abstract class for AES256 transformer implementations.
 *
 */
public abstract class AesCTRTransformer implements ICTRTransformer {

    /**
     * Standard expansion key size for AES256 only.
     */
    public static final int EXPANDED_KEY_SIZE = 240;

    /**
     * Salmon stream encryption block size, same as AES.
     */
    public static final int BLOCK_SIZE = 16;

    /**
     * Key to be used for AES transformation.
     */
    private byte[] key;

	/**
	 * Expanded key.
	 */
    private byte[] expandedKey = new byte[EXPANDED_KEY_SIZE];

    /**
     * Nonce to be used for CTR mode.
     */
    private byte[] nonce;

    /**
     * Current operation block.
     */
    private long block = 0;

    /**
     * Current operation counter.
     */
    private byte[] counter;

    /**
     * Resets the Counter and the block count.
     */
    public void resetCounter() {
        if (this.nonce == null)
            throw new SecurityException("No counter, run init first");
        counter = new byte[BLOCK_SIZE];
        System.arraycopy(nonce, 0, counter, 0, nonce.length);
        block = 0;
    }

    /**
     * Syncs the Counter based on what AES block position the stream is at.
     * The block count is already excluding the header and the hash signatures.
     */
    public void syncCounter(long position) {
        long currBlock = position / BLOCK_SIZE;
        resetCounter();
        increaseCounter(currBlock);
        block = currBlock;
    }

    /**
     * Increase the Counter
     * We use only big endianness for AES regardless of the machine architecture
     *
     * @param value value to increase counter by
     */
    protected void increaseCounter(long value) {
        if (this.counter == null || this.nonce == null)
            throw new SecurityException("No counter, run init first");
        if (value < 0)
            throw new IllegalArgumentException("Value should be positive");
        int index = BLOCK_SIZE - 1;
        int carriage = 0;
        while (index >= 0 && value + carriage > 0) {
            if (index <= BLOCK_SIZE - Generator.NONCE_LENGTH)
                throw new RangeExceededException("Current CTR max blocks exceeded");
            long val = (value + carriage) % 256;
            carriage = (int) (((counter[index] & 0xFF) + val) / 256);
            counter[index--] += (byte) val;
            value /= 256;
        }
    }

    /**
     * Initialize the transformer. Most common operations include precalculating expansion keys or
     * any other prior initialization for efficiency.
     * @param key The key
     * @param nonce The nonce
     * @throws SecurityException Thrown if there is a security exception
     */
    public void init(byte[] key, byte[] nonce) {
        this.key = key;
        this.nonce = nonce;
    }

    /**
     * Get the current Counter.
     * @return The current counter
     */
    public byte[] getCounter() {
        if (this.counter == null)
            throw new RuntimeException("No counter, run init() and resetCounter()");
        return counter;
    }

    /**
     * Get the current block.
     * @return The current block
     */
    public long getBlock() {
        return block;
    }

    /**
     * Get the current encryption key.
     * @return The encryption key
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Get the expanded key if available.
     * @return The expanded key
     */
    protected byte[] getExpandedKey() {
        return expandedKey;
    }

    /**
     * Get the nonce (initial counter)
     * @return The nonce
     */
    public byte[] getNonce() {
        return nonce;
    }

    /**
     * Set the expanded key. This should be called once during initialization phase.
     * @param expandedKey The expanded key
     */
    public void setExpandedKey(byte[] expandedKey) {
        this.expandedKey = expandedKey;
    }
}
