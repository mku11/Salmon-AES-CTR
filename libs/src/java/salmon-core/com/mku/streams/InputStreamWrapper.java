package com.mku.streams;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

/***
 * InputStream wrapper for RandomAccessStream.
 * Use this class to wrap any RandomAccessStream to a Java InputStream to use with 3rd party libraries.
 */
public class InputStreamWrapper extends InputStream {
    /**
     * Default cache buffer should be high enough for some mpeg videos to work
     * the cache buffers should be aligned to the AesFile chunk size for efficiency
     */
    public static final int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /**
     * The default buffer count
     */
    public static final int DEFAULT_BUFFERS = 1;

    /**
     * The default backwards buffer offset
     */
    public static final int DEFAULT_BACK_OFFSET = 32768;

    /**
     * The maximum allowed buffer count
     */
    protected static final int MAX_BUFFERS = 6;

    private int buffersCount;
    private Buffer[] buffers = null;

    /**
     * The buffer size
     */
    protected int bufferSize;

    public long getStreamPosition() {
        return streamPosition;
    }

    protected void setStreamPosition(long streamPosition) {
        this.streamPosition = streamPosition;
    }

    private long streamPosition;

    /**
     * The total size of the base stream
     */
    protected long totalSize;

    protected void setAlignSize(int alignSize) {
        this.alignSize = alignSize;
    }

    private int alignSize;

    /**
     * We reuse the least recently used buffer. Since the buffer count is relative
     * small (see {@link #MAX_BUFFERS}) there is no need for a fast-access lru queue
     * so a simple linked list of keeping the indexes is adequately fast.
     */
    private final LinkedList<Integer> lruBuffersIndex = new LinkedList<>();

    /**
     * Negative offset for the buffers. Some stream consumers might request data right before
     * the last request. We provide this offset so we don't make multiple requests for filling
     * the buffers ending up with too much overlapping data.
     */
    private int backOffset;

    private RandomAccessStream stream;

    /**
     * Instantiates an InputStreamWrapper from a RandomAccessStream.
     *
     * @param stream The stream that you want to wrap.
     */
    public InputStreamWrapper(RandomAccessStream stream) {
        this(stream, 1, DEFAULT_BUFFER_SIZE, DEFAULT_BACK_OFFSET, 0);
    }

	/**
     * Instantiates an InputStreamWrapper from a RandomAccessStream with buffer options.
     *
     * @param stream       The stream that you want to wrap.
     * @param buffersCount The number of buffers to use
     * @param bufferSize   The buffer size
     * @param backOffset   The back offset
     */
    public InputStreamWrapper(RandomAccessStream stream, int buffersCount, int bufferSize, int backOffset) {
		this(stream, buffersCount, bufferSize, backOffset, 0);
	}
		
    /**
     * Instantiates an InputStreamWrapper from a RandomAccessStream with buffer options.
     *
     * @param stream       The stream that you want to wrap.
     * @param buffersCount The number of buffers to use
     * @param bufferSize   The buffer size
     * @param backOffset   The back offset
     * @param alignSize    The align size
     */
    public InputStreamWrapper(RandomAccessStream stream, int buffersCount, int bufferSize, int backOffset, int alignSize) {
        this.stream = stream;
        if (stream != null) {
            this.totalSize = stream.getLength();
			try {
				this.streamPosition = stream.getPosition();
			} catch (Exception ex) {
				throw new RuntimeException("Could not get stream current position", ex);
			}
		}
        if (buffersCount <= 0)
            buffersCount = DEFAULT_BUFFERS;
        if (buffersCount > MAX_BUFFERS)
            buffersCount = MAX_BUFFERS;
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (backOffset < 0)
            backOffset = DEFAULT_BACK_OFFSET;

        if (alignSize <= 0 && stream != null)
            alignSize = stream.getAlignSize();

        // align the buffers for performance
        if (alignSize > 0) {
            if (backOffset > 0) {
                int nBackOffset = backOffset / alignSize * alignSize;
                if (nBackOffset < backOffset)
                    nBackOffset += alignSize;
                backOffset = nBackOffset;
            }

            int nBufferSize = bufferSize / alignSize * alignSize;
            if (nBufferSize < alignSize) {
                nBufferSize = alignSize;
            }
            if (nBufferSize < bufferSize) {
                nBufferSize += alignSize;
            }
            bufferSize = nBufferSize;
        }

        if (backOffset > 0) {
            bufferSize += backOffset;
            // we use a minimum 2 buffers since it is very likely
            // that the previous buffer in use will have the backoffset
            // data of the new one
            if (buffersCount == 1)
                buffersCount = 2;
        }

        this.buffersCount = buffersCount;
        this.bufferSize = bufferSize;
        this.backOffset = backOffset;
        this.positionStart = 0;
        this.positionEnd = totalSize - 1;
        this.alignSize = alignSize;

        createBuffers();
    }


    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    private void createBuffers() {
        buffers = new Buffer[buffersCount];
        for (int i = 0; i < buffers.length; i++)
            buffers[i] = new Buffer(bufferSize);
    }

    /**
     * Skip a number of bytes.
     *
     * @param bytes the number of bytes to be skipped.
     * @return The byte skipped
     */
    public long skip(long bytes) {
        bytes += positionStart;
        long currPos = this.streamPosition;
        if (this.streamPosition + bytes > this.totalSize)
            this.streamPosition = this.totalSize;
        else
            this.streamPosition += bytes;
        return this.streamPosition - currPos;
    }

    /**
     * Reset the stream.
     */
    @Override
    public void reset() {
        streamPosition = 0;
    }

    /**
     * Read a byte from the stream.
     *
     * @return The bytes read
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int bytesRead = read(buffer, 0, 1);
        streamPosition += bytesRead;
        return bytesRead;
    }

    public int getBackOffset() {
        return backOffset;
    }

    protected void setBackOffset(int backOffset) {
        this.backOffset = backOffset;
    }

    /**
     * Reads and decrypts the contents of an encrypted file
     *
     * @param buffer The buffer that will store the decrypted contents
     * @param offset The position on the buffer that the decrypted data will start
     * @param count  The length of the data requested
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        if (streamPosition >= positionEnd + 1)
            return -1;

        int minCount;
        int bytesRead;

        // truncate the count so getCacheBuffer() reports the correct buffer
        count = (int) Math.min(count, totalSize - streamPosition);

        Buffer cacheBuffer = getBuffer(streamPosition, count);
        if (cacheBuffer == null) {
            cacheBuffer = getAvailBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return 0;

            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            long startPosition = streamPosition;
            if (alignSize > 0) {
                startPosition = startPosition / alignSize * alignSize;
            }

            int length = bufferSize;

            // if we have the backoffset data in an existing buffer we don't include the backoffset
            // in the new request because we want to prevent network streams resetting.
            if (startPosition > 0 && !hasBackoffset(startPosition)) {
                startPosition -= backOffset;
            } else {
                length -= backOffset;
            }

            bytesRead = fillBuffer(cacheBuffer, startPosition, length);

            if (bytesRead <= 0)
                return bytesRead;
            cacheBuffer.setStartPos(startPosition);
            cacheBuffer.setCount(bytesRead);
        }

        // align the count also
        long end = streamPosition + count;
        int nCount = (int) (end / alignSize * alignSize - streamPosition);
        if (nCount > 0 && nCount < count) {
            count = nCount;
        }

        minCount = Math.min(count, (int) (cacheBuffer.getCount() - streamPosition + cacheBuffer.getStartPos()));
        System.arraycopy(cacheBuffer.getData(), (int) (streamPosition - cacheBuffer.getStartPos()), buffer, offset, minCount);

        streamPosition += minCount;
        return minCount;
    }

    private boolean hasBackoffset(long startPosition) {
        long pos = startPosition - backOffset;
        for (int i = 0; i < buffers.length; i++) {
            Buffer buffer = buffers[i];
            if (buffer != null && buffer.getCount() > 0
                    && buffer.getStartPos() <= pos
                    && startPosition <= buffer.getStartPos() + buffer.getCount()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer   The cache buffer that will store the decrypted contents
     * @param startPosition The start position
     * @param length        The length of the data requested
     * @return The number of bytes read
     * @throws IOException When IO error occurs
     */
    protected synchronized int fillBuffer(Buffer cacheBuffer, long startPosition, int length) throws IOException {
        int bytesRead = fillBufferPart(cacheBuffer, startPosition, 0, length, stream);
        return bytesRead;
    }

    /**
     * Fills a cache buffer with the decrypted data from a part of an encrypted file
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
     * @param start       The start position
     * @param offset      The offset
     * @param length      The length of the data requested
     * @param stream      The stream that will be used to read from
     * @return The number of total bytes read.
     * @throws IOException When IO error occurs
     */
    protected int fillBufferPart(Buffer cacheBuffer, long start, int offset, int length,
                                 RandomAccessStream stream) throws IOException {
        stream.seek(start, RandomAccessStream.SeekOrigin.Begin);
        int bytesRead;
        int totalBytesRead = 0;
        while ((bytesRead = stream.read(cacheBuffer.getData(), offset + totalBytesRead, length - totalBytesRead)) > 0) {
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    /**
     * Returns an available cache buffer if there is none then reuse the least recently used one.
     */
    private synchronized Buffer getAvailBuffer() {
        if (this.buffers == null)
            throw new Error("No buffers found");
        int index = -1;
        if (this.lruBuffersIndex.size() == this.buffersCount) {
            index = this.lruBuffersIndex.removeLast();
        } else {
            for (int i = 0; i < this.buffers.length; i++) {
                Buffer buff = this.buffers[i];
                if (buff != null && buff.getCount() == 0) {
                    index = i;
                    break;
                }
            }
        }
        if(index < 0)
            index = this.buffers.length - 1;

        this.lruBuffersIndex.addFirst(index);
        return this.buffers[index];
    }

    /**
     * Returns the buffer that contains the data requested.
     *
     * @param position The source file position of the data to be read
     */
    private synchronized Buffer getBuffer(long position, int count) {
        if (this.buffers == null)
            return null;
        for (int i = 0; i < buffers.length; i++) {
            Buffer buffer = buffers[i];
            if (buffer != null && position >= buffer.getStartPos()
                    && position + count <= buffer.getStartPos() + buffer.getCount()) {
                // promote buffer to the front
                lruBuffersIndex.remove((Integer) i);
                lruBuffersIndex.addFirst(i);
                return buffer;
            }
        }
        return null;
    }

    /**
     * Get the size of the stream.
     *
     * @return The size
     */
    public long getLength() {
        return positionEnd - positionStart + 1;
    }

    private long positionStart;

    /**
     * Get the start position for the stream.
     *
     * @return The start position.
     */
    public long getPositionStart() {
        return positionStart;
    }

    /**
     * Set the start position for the stream.
     *
     * @param pos The start position.
     */
    public void setPositionStart(long pos) {
        positionStart = pos;
    }

    private long positionEnd;

    /**
     * Get the end position for the stream.
     *
     * @return The end position.
     */
    public long getPositionEnd() {
        return positionEnd;
    }

    /**
     * Set the end position for the stream.
     *
     * @param pos The end position.
     */
    public void setPositionEnd(long pos) {
        positionEnd = pos;
    }

    /**
     * Get the buffers count
     *
     * @return The buffers count
     */
    protected int getBuffersCount() {
        return buffersCount;
    }

    /**
     * Set the buffers count
     *
     * @param buffersCount The buffers count
     */
    protected void setBuffersCount(int buffersCount) {
        this.buffersCount = buffersCount;
    }

    /**
     * Close the stream and associated backed streams and clear buffers.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    public void close() throws IOException {
        closeStream();
        clearBuffers();
    }

    /**
     * Clear all buffers.
     */
    private synchronized void clearBuffers() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null)
                buffers[i].clear();
            buffers[i] = null;
        }
    }

    private void closeStream() throws IOException {
        if (stream != null)
            stream.close();
        stream = null;
    }

    /**
     * Get the align size
     *
     * @return The align size
     */
    public int getAlignSize() {
        return alignSize;
    }
}
