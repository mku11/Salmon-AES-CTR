package com.mku.streams;

import java.util.Arrays;

/**
 * Buffer that can be used for buffered streams.
 */
public class Buffer {
    public byte[] getData() {
        return data;
    }

    /**
     * Set the data
     *
     * @param data The data
     */
    public void setData(byte[] data) {
        this.data = data;
    }

	/**
     * Get the start position
     *
     * @return The start position
     */
    public long getStartPos() {
        return startPos;
    }

    /**
     * Set the start position
     *
     * @param startPos The start position
     */
    public void setStartPos(long startPos) {
        this.startPos = startPos;
    }

	/**
     * Get the data count
     *
     * @return The data count
     */
    public long getCount() {
        return count;
    }

    /**
     * Set the data count
     *
     * @param count The data count
     */
    public void setCount(long count) {
        this.count = count;
    }

    private byte[] data;
    private long startPos = 0;
    private long count = 0;

    /**
     * Instantiate a buffer.
     *
     * @param bufferSize The buffer size
     */
    public Buffer(int bufferSize) {
        data = new byte[bufferSize];
    }

    /**
     * Clear the buffer.
     */
    public void clear() {
        if (data != null)
            Arrays.fill(data, 0, data.length, (byte) 0);
    }
}