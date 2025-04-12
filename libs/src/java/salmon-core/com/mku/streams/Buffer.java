package com.mku.streams;

import java.util.Arrays;

/**
 * Buffer that can be used for buffered streams.
 */
public class Buffer {
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getStartPos() {
        return startPos;
    }

    public void setStartPos(long startPos) {
        this.startPos = startPos;
    }

    public long getCount() {
        return count;
    }

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