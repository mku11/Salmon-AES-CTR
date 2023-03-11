package com.mku11.media;
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

import com.mku11.salmon.SalmonIntegrity;
import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmonfs.SalmonFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of a MediaDataSource for encrypted content.
 * This class provides a seekable source for the Media Player
 */
public class SalmonMediaDataSource extends InputStream {
    private static final String TAG = SalmonMediaDataSource.class.getName();
    private static boolean enableLog = false;

    // Default cache buffer should be high enough for some mpeg videos to work
	// the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    private static final int DEFAULT_MEDIA_CACHE_BUFFER_SIZE = 512 * 1024;

    // this offset is also needed to be aligned to the chunk size
    private static final int STREAM_OFFSET = 256 * 1024;

    // default threads is one but you can increase it
    private static final int DEFAULT_MEDIA_THREADS = 1;

    private CacheBuffer[] buffers = null;
    private SalmonStream[] streams;
    private final SalmonFile salmonFile;
    private final long streamSize;
    private final int cacheBufferSize;
    private final int threads;
    private ExecutorService executor;
    private boolean integrityFailed;
    private long position;

    /**
     * Construct a seekable source for the media player from an encrypted file source
     *
     * @param salmonFile
     * @param bufferSize
     * @param threads
     */
    public SalmonMediaDataSource(SalmonFile salmonFile, int bufferSize, int threads) throws Exception {
        this.salmonFile = salmonFile;
        this.streamSize = salmonFile.getSize();
        if (bufferSize == 0)
            bufferSize = DEFAULT_MEDIA_CACHE_BUFFER_SIZE;
        if (threads == 0)
            threads = DEFAULT_MEDIA_THREADS;
        this.cacheBufferSize = bufferSize;
        this.threads = threads;
        createBuffers();
        createStreams();
    }

    public static void setEnableLog(boolean value) {
        enableLog = value;
    }

    /**
     * Method creates the parallel streams for reading from the file
     */
    private void createStreams() {
        try {
            executor = Executors.newFixedThreadPool(threads);
            streams = new SalmonStream[threads];
            for (int i = 0; i < threads; i++) {
                streams[i] = salmonFile.getInputStream();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the media file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    private void createBuffers() {
        int buffersCount = 2;
        buffers = new CacheBuffer[buffersCount];
        for (int i = 0; i < buffers.length; i++)
            buffers[i] = new CacheBuffer(cacheBufferSize);
    }

    public long skip(long pos) {
        long currPos = this.position;
        if (pos >= this.streamSize)
            return 0;
        this.position += pos;
        return this.position - currPos;
    }

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
     * @param size   The length of the data requested
     */
    @Override
    public int read(byte[] buffer, int offset, int size) {
        //we return -1 for java
        try {
            if (position >= salmonFile.getSize())
                return -1;
        } catch (Exception e) {
            e.printStackTrace();
        }

        int minSize = 0;
        int bytesRead;
        try {
            CacheBuffer cacheBuffer = GetCacheBuffer(position);
            if (cacheBuffer == null) {
                cacheBuffer = GetAvailCacheBuffer();
                // for some media the player makes a second immediate request
                // in a position a few bytes before the first request. To make
                // sure we don't make 2 overlapping requests we start the buffer
                // a little before to the first request.
                long startPosition = position - STREAM_OFFSET;
                if (startPosition < 0)
                    startPosition = 0;

                bytesRead = FillBuffer(cacheBuffer, startPosition, offset, cacheBufferSize);

                if (bytesRead <= 0)
                    return bytesRead;
                cacheBuffer.startPos = startPosition;
                cacheBuffer.count = bytesRead;
            }
            minSize = Math.min(size, (int) (cacheBuffer.count - position + cacheBuffer.startPos));
            System.arraycopy(cacheBuffer.buffer, (int) (position - cacheBuffer.startPos), buffer, 0, minSize);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        position += minSize;
        return minSize;
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
     * @param offset      The position on the buffer that the decrypted data will start
     * @param bufferSize  The length of the data requested
     */
    private int FillBuffer(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize) throws InterruptedException, NoSuchAlgorithmException, DigestException {
        long start = 0;
        if (enableLog) {
            start = SalmonTime.currentTimeMillis();
        }
        int bytesRead;
        if (threads == 1) {
            bytesRead = FillBufferPart(cacheBuffer, startPosition, offset, bufferSize, streams[0]);
        } else {
            bytesRead = FillBufferMulti(cacheBuffer, startPosition, offset, bufferSize);
        }
        if (enableLog) {
            System.out.println("Total requested: " + bufferSize + ", Total Read: " + bytesRead + " bytes in: " + (SalmonTime.currentTimeMillis() - start) + " ms");
        }
        return bytesRead;
    }

    /**
     * Fills a cache buffer with the decrypted data from a part of an encrypted file served as a salmon stream
     *
     * @param cacheBuffer  The cache buffer that will store the decrypted contents
     * @param offset       The position on the buffer that the decrypted data will start
     * @param bufferSize   The length of the data requested
     * @param salmonStream The stream that will be used to read from
     */
    private int FillBufferPart(CacheBuffer cacheBuffer, long start, int offset, int bufferSize,
                               SalmonStream salmonStream) {
        // there is no need to pre test the SalmonFile for integrity we can start reading it
        // while we fill up our buffers. if we reach a chunk with a mismatch on the HMAC
        // there will be an integrity exception thrown.
        try {
            salmonStream.seek(start, AbsStream.SeekOrigin.Begin);
            int totalBytesRead = salmonStream.read(cacheBuffer.buffer, offset, bufferSize);
            return totalBytesRead;
        } catch (SalmonIntegrity.SalmonIntegrityException ex) {
            ex.printStackTrace();
            displayIntegrityErrorOnce();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Display a message when integrity test has failed
     */
    private void displayIntegrityErrorOnce() {
        if (!integrityFailed) {
            integrityFailed = true;
            //TODO: display to the user
//            new SalmonAlert(javafx.scene.control.Alert.AlertType.ERROR, "File corrupt or tampered").show();
        }
    }

    /**
     * Fill the buffer using parallel streams for performance
     *
     * @param cacheBuffer   The cache buffer that will store the decrypted data
     * @param startPosition The source file position the read will start from
     * @param offset        The start position on the cache buffer that the decrypted data will be stored
     * @param bufferSize    The buffer size that will be used to read from the file
     */
    private int FillBufferMulti(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize) throws InterruptedException, NoSuchAlgorithmException, DigestException {
        final int[] bytesRead = {0};
        // Multi threaded decryption jobs
        CountDownLatch countDownLatch = new CountDownLatch(threads);
        int partSize = (int) Math.ceil(bufferSize / (float) threads);
        for (int i = 0; i < threads; i++) {
            int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    int start = partSize * index;
                    int length = Math.min(partSize, bufferSize - start);
                    int chunkBytesRead = FillBufferPart(cacheBuffer, startPosition + start, offset + start, length,
                            streams[index]);
                    if (chunkBytesRead >= 0)
                        bytesRead[0] += chunkBytesRead;
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        return bytesRead[0];
    }

    /**
     * Returns an available cache buffer if there is none then it reuses the last one
     */
    private CacheBuffer GetAvailCacheBuffer() {
        for (int i = 0; i < buffers.length; i++) {
            CacheBuffer buffer = buffers[i];
            if (buffer.count == 0)
                return buffer;
        }
        return buffers[buffers.length - 1];
    }

    /**
     * Returns the buffer that contains the data requested.
     *
     * @param position The source file position of the data to be read
     */
    private CacheBuffer GetCacheBuffer(long position) {
        for (int i = 0; i < buffers.length; i++) {
            CacheBuffer buffer = buffers[i];
            if (position >= buffer.startPos && position < buffer.startPos + buffer.count)
                return buffer;
        }
        return null;
    }

    public long getSize() {
        try {
            return salmonFile.getSize();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return 0;
    }

    public void close() throws IOException {
        closeStreams();
        closeBuffers();
    }

    private void closeBuffers() {
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null)
                buffers[i].clear();
            buffers[i] = null;
        }
    }

    private void closeStreams() throws IOException {
        for (int i = 0; i < threads; i++) {
            if (streams[i] != null)
                streams[i].close();
            streams[i] = null;
        }
    }

    public void position(long valLong) {
        position = valLong;
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

        public CacheBuffer(int bufferSize) {
            buffer = new byte[bufferSize];
        }

        public void clear() {
            if (buffer != null)
                Arrays.fill(buffer, 0, buffer.length, (byte) 0);
        }
    }
}

