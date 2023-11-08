package com.mku.salmonfs;
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

import com.mku.io.RandomAccessStream;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.io.SalmonStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of a Java InputStream for seeking and reading a SalmonFile.
 * This class provides a seekable source with parallel substreams and cached buffers
 * for performance.
 */
public class SalmonFileInputStream extends InputStream {
    private static final String TAG = SalmonFileInputStream.class.getName();

    // Default cache buffer should be high enough for some mpeg videos to work
    // the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;

    // default threads is one but you can increase it
    private static final int DEFAULT_THREADS = 1;

    private static final int DEFAULT_BUFFERS = 3;

    private static final int MAX_BUFFERS = 6;

    private final int buffersCount;
    private CacheBuffer[] buffers = null;
    private SalmonStream[] streams;
    private final SalmonFile salmonFile;
    private final int cacheBufferSize;
    private final int threads;
    private ExecutorService executor;
    private long position;
    private long size;

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
    private final int backOffset;

    /**
     * Instantiate a seekable stream from an encrypted file source
     *
     * @param salmonFile   The source file.
     * @param buffersCount Number of buffers to use.
     * @param bufferSize   The length of each buffer.
     * @param threads      The number of threads/streams to source the file in parallel.
     * @param backOffset   The back offset.
     */
    public SalmonFileInputStream(SalmonFile salmonFile,
                                 int buffersCount, int bufferSize, int threads, int backOffset)
            throws IOException, SalmonIntegrityException {
        this.salmonFile = salmonFile;
        this.size = salmonFile.getSize();
        if (buffersCount == 0)
            buffersCount = DEFAULT_BUFFERS;
        if (buffersCount > MAX_BUFFERS)
            buffersCount = MAX_BUFFERS;
        if (bufferSize == 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (backOffset > 0)
            bufferSize += backOffset;
        if (threads == 0)
            threads = DEFAULT_THREADS;

        this.buffersCount = buffersCount;
        this.cacheBufferSize = bufferSize;
        this.threads = threads;
        this.backOffset = backOffset;
        this.positionStart = 0;
        this.positionEnd = size - 1;

        createBuffers();
        createStreams();
    }

    /**
     * Method creates the parallel streams for reading from the file
     */
    private void createStreams() throws IOException {
        executor = Executors.newFixedThreadPool(threads);
        streams = new SalmonStream[threads];
        try {
            for (int i = 0; i < threads; i++) {
                streams[i] = salmonFile.getInputStream();
            }
        } catch (SalmonSecurityException | SalmonIntegrityException ex) {
            throw new IOException("Could not create streams", ex);
        }
    }

    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    private void createBuffers() {
        buffers = new CacheBuffer[buffersCount];
        for (int i = 0; i < buffers.length; i++)
            buffers[i] = new CacheBuffer(cacheBufferSize);
    }

    /**
     * Skip a number of bytes.
     *
     * @param bytes the number of bytes to be skipped.
     * @return
     */
    public long skip(long bytes) {
        long currPos = this.position;
        if (this.position + bytes > this.size)
            this.position = this.size;
        else
            this.position += bytes;
        return this.position - currPos;
    }

    @Override
    public void reset() {
        position = 0;
    }

    /**
     * Read a byte from the stream.
     *
     * @return
     * @throws IOException
     */
    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];

        int bytesRead = read(buffer, 0, 1);
        position += bytesRead;
        return bytesRead;
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
        if (position >= positionEnd + 1)
            return -1;

        int minCount;
        int bytesRead;

        // truncate the count so getCacheBuffer() reports the correct buffer
        count = (int) Math.min(count, size - position);

        CacheBuffer cacheBuffer = getCacheBuffer(position, count);
        if (cacheBuffer == null) {
            cacheBuffer = getAvailCacheBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return -1;
            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            long startPosition = position - backOffset;
            if (startPosition < 0)
                startPosition = 0;

            bytesRead = fillBuffer(cacheBuffer, startPosition, cacheBufferSize);

            if (bytesRead <= 0)
                return -1;
            cacheBuffer.startPos = startPosition;
            cacheBuffer.count = bytesRead;
        }
        minCount = Math.min(count, (int) (cacheBuffer.count - position + cacheBuffer.startPos));
        System.arraycopy(cacheBuffer.buffer, (int) (position - cacheBuffer.startPos), buffer, offset, minCount);

        position += minCount;
        return minCount;
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
     * @param bufferSize  The length of the data requested
     */
    private synchronized int fillBuffer(CacheBuffer cacheBuffer, long startPosition, int bufferSize) throws IOException {
        int bytesRead;
        if (threads == 1) {
            bytesRead = fillBufferPart(cacheBuffer, startPosition, 0, bufferSize, streams[0]);
        } else {
            bytesRead = fillBufferMulti(cacheBuffer, startPosition, bufferSize);
        }
        return bytesRead;
    }

    /**
     * Fills a cache buffer with the decrypted data from a part of an encrypted file served as a salmon stream
     *
     * @param cacheBuffer  The cache buffer that will store the decrypted contents
     * @param bufferSize   The length of the data requested
     * @param salmonStream The stream that will be used to read from
     */
    private int fillBufferPart(CacheBuffer cacheBuffer, long start, int offset, int bufferSize,
                               SalmonStream salmonStream) throws IOException {
        salmonStream.seek(start, RandomAccessStream.SeekOrigin.Begin);
        int totalBytesRead = salmonStream.read(cacheBuffer.buffer, offset, bufferSize);
        return totalBytesRead;
    }

    /**
     * Fill the buffer using parallel streams for performance
     *
     * @param cacheBuffer   The cache buffer that will store the decrypted data
     * @param startPosition The source file position the read will start from
     * @param bufferSize    The buffer size that will be used to read from the file
     */
    private int fillBufferMulti(CacheBuffer cacheBuffer, long startPosition, int bufferSize) {
        final int[] bytesRead = {0};
        AtomicReference<IOException> ex = new AtomicReference<>();
        // Multithreaded decryption jobs
        CountDownLatch countDownLatch = new CountDownLatch(threads);
        int partSize = (int) Math.ceil(bufferSize / (float) threads);
        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                int start = partSize * index;
                int length;
                if (index == threads - 1)
                    length = bufferSize - start;
                else
                    length = partSize;
                try {
                    int chunkBytesRead = fillBufferPart(cacheBuffer, startPosition + start, start, length,
                            streams[index]);
                    if (chunkBytesRead >= 0)
                        bytesRead[0] += chunkBytesRead;
                } catch (IOException ex1) {
                    ex.set(ex1);
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                }
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException ignored) {
        }

        if (ex.get() != null) {
            try {
                throw ex.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return bytesRead[0];
    }

    /**
     * Returns an available cache buffer if there is none then reuse the least recently used one.
     */
    private synchronized CacheBuffer getAvailCacheBuffer() {
        if (lruBuffersIndex.size() == buffersCount) {
            // getting least recently used buffer
            int index = lruBuffersIndex.getLast();
            // promote to the top
            lruBuffersIndex.remove((Integer) index);
            lruBuffersIndex.addFirst(index);
            return buffers[lruBuffersIndex.getLast()];
        }
        for (int i = 0; i < buffers.length; i++) {
            CacheBuffer buffer = buffers[i];
            if (buffer != null && buffer.count == 0) {
                lruBuffersIndex.addFirst(i);
                return buffer;
            }
        }
        if (buffers[buffers.length - 1] != null)
            return buffers[buffers.length - 1];
        else
            return null;
    }

    /**
     * Returns the buffer that contains the data requested.
     *
     * @param position The source file position of the data to be read
     */
    private synchronized CacheBuffer getCacheBuffer(long position, int count) {
        for (int i = 0; i < buffers.length; i++) {
            CacheBuffer buffer = buffers[i];
            if (position >= buffer.startPos && position + count <= buffer.startPos + buffer.count) {
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
     * @return
     */
    public long getSize() {
        return positionEnd - positionStart + 1;
    }

    private long positionStart;
    public long getPositionStart() {
        return positionStart;
    }
    public void setPositionStart(long pos) {
        positionStart = pos;
    }
    private long positionEnd;
    public void setPositionEnd(long pos) {
        positionEnd = pos;
    }

    /**
     * Close the stream and associated backed streams and clear buffers.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        closeStreams();
        clearBuffers();
        executor.shutdownNow();
    }

    /**
     * Clear all buffers.
     */
    private void clearBuffers() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null)
                buffers[i].clear();
            buffers[i] = null;
        }
    }

    /**
     * Close all back streams.
     *
     * @throws IOException
     */
    private void closeStreams() throws IOException {
        for (int i = 0; i < threads; i++) {
            if (streams[i] != null)
                streams[i].close();
            streams[i] = null;
        }
    }

    /**
     * Class will be used to cache decrypted data that can later be read via the ReadAt() method
     * without requesting frequent decryption reads.
     */
    //TODO: replace the CacheBuffer with a MemoryStream to simplify the code
    public static class CacheBuffer {
        public byte[] buffer;
        public long startPos = 0;
        public long count = 0;

        /**
         * Instantiate a cache buffer.
         *
         * @param bufferSize
         */
        public CacheBuffer(int bufferSize) {
            buffer = new byte[bufferSize];
        }

        /**
         * Clear the buffer.
         */
        public void clear() {
            if (buffer != null)
                Arrays.fill(buffer, 0, buffer.length, (byte) 0);
        }
    }
}

