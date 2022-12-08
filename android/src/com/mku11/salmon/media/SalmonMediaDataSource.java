package com.mku11.salmon.media;
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

import android.app.Activity;
import android.media.MediaDataSource;
import android.util.Log;
import android.widget.Toast;

import com.mku.android.salmonvault.R;
import com.mku11.salmon.main.SalmonApplication;
import com.mku11.salmon.SalmonIntegrity;
import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmonfs.SalmonFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class provides a parallel processing seekable source for encrypted media content
 */
public class SalmonMediaDataSource extends MediaDataSource {
    private static final String TAG = SalmonMediaDataSource.class.getName();
    private static boolean enableLog = false;

    // Default cache buffer should be high enough for some mpeg videos to work
	// the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    private static final int DEFAULT_MEDIA_CACHE_BUFFER_SIZE = 512 * 1024;
    // this offset is also needed to be aligned to the chunk size
    private static final int STREAM_OFFSET = 256 * 1024;
    // default threads is one but you can increase it
    private static final int DEFAULT_MEDIA_THREADS = 2;
    private static final int BUFFERS = 2;

    private CacheBuffer[] buffers = null;
    private SalmonStream[] streams;
    private final Activity activity;
    private final SalmonFile salmonFile;
    private final long streamSize;
    private final int cacheBufferSize;
    private final int threads;
    private ExecutorService executor;
    private boolean integrityFailed;

    /**
     * Construct a seekable source for the media player from an encrypted file source
     *
     * @param activity   Activity that this data source will be used with. This is usually the activity the MediaPlayer is attached to
     * @param salmonFile SalmonFile that will be used as a source
     * @param bufferSize Buffer size
     * @param threads    Threads for parallel processing
     */
    public SalmonMediaDataSource(Activity activity, SalmonFile salmonFile,
                                 int bufferSize, int threads) throws Exception {
        this.activity = activity;
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
            Toast.makeText(SalmonApplication.getInstance().getApplicationContext(), "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Create cache buffers that will be used for sourcing the files.
     * These will help reducing multiple small decryption reads from the encrypted source.
     * The first buffer will be sourcing at the start of the media file where the header and indexing are
     * The rest of the buffers can be placed to whatever position the user slides to
     */
    private void createBuffers() {
        buffers = new CacheBuffer[BUFFERS];
        for (int i = 0; i < buffers.length; i++)
            buffers[i] = new CacheBuffer(cacheBufferSize);
    }

    /**
     * Decrypts and reads the contents of an encrypted file
     *
     * @param position The source file position the read will start from
     * @param buffer   The buffer that will store the decrypted contents
     * @param offset   The position on the buffer that the decrypted data will start
     * @param size     The length of the data requested
     */
    public int readAt(long position, byte[] buffer, int offset, int size) {
        if (position >= this.streamSize)
            return 0;
        int minSize = 0;
        int bytesRead;
        try {
            CacheBuffer cacheBuffer = getCacheBuffer(position);
            if (cacheBuffer == null) {
                cacheBuffer = getAvailCacheBuffer();
                // for some media the player makes a second immediate request
                // in a position a few bytes before the first request. To make
                // sure we don't make 2 overlapping requests we start the buffer
                // a little before to the first request.
                long startPosition = position - STREAM_OFFSET;
                if (startPosition < 0)
                    startPosition = 0;

                bytesRead = fillBuffer(cacheBuffer, startPosition, offset, cacheBufferSize);

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

        return minSize;
    }

    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
     * @param offset      The position on the buffer that the decrypted data will start
     * @param bufferSize  The length of the data requested
     */
    private int fillBuffer(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize) throws InterruptedException {
        long start = 0;
        if (enableLog) {
            start = SalmonTime.currentTimeMillis();
        }
        int bytesRead;
        if (threads == 1) {
            bytesRead = fillBufferPart(cacheBuffer, startPosition, offset, bufferSize, streams[0]);
        } else {
            bytesRead = fillBufferMulti(cacheBuffer, startPosition, offset, bufferSize);
        }
        if (enableLog) {
            Log.d(TAG, "Total requested: " + bufferSize + ", Total Read: " + bytesRead + " bytes in: " + (SalmonTime.currentTimeMillis() - start) + " ms");
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
    private int fillBufferPart(CacheBuffer cacheBuffer, long start, int offset, int bufferSize,
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
            if (activity != null) {
                activity.runOnUiThread(() -> Toast.makeText(SalmonApplication.getInstance().getApplicationContext(), activity.getString(R.string.FileCorrupOrTampered), Toast.LENGTH_LONG).show());
            }
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
    private int fillBufferMulti(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize) throws InterruptedException {
        final int[] bytesRead = {0};
        // Multi threaded decryption jobs
        CountDownLatch countDownLatch = new CountDownLatch(threads);
        int partSize = (int) Math.ceil(bufferSize / (float) threads);
        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                int start = partSize * index;
                int length = Math.min(partSize, bufferSize - start);
                int chunkBytesRead = fillBufferPart(cacheBuffer, startPosition + start, offset + start, length,
                        streams[index]);
                if (chunkBytesRead >= 0)
                    bytesRead[0] += chunkBytesRead;
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        return bytesRead[0];
    }

    /**
     * Returns an available cache buffer if there is none then it reuses the last one
     */
    private CacheBuffer getAvailCacheBuffer() {
        for (CacheBuffer buffer : buffers) {
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
    private CacheBuffer getCacheBuffer(long position) {
        for (CacheBuffer buffer : buffers) {
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

