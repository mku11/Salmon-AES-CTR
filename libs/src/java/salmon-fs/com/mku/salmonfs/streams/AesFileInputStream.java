package com.mku.salmonfs.streams;
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
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.file.AesFile;
import com.mku.streams.Buffer;
import com.mku.streams.InputStreamWrapper;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * InputStream wrapper for seeking and reading an encrypted AesFile.
 * This class provides a seekable source with parallel streams and cached buffers
 * for performance.
 */
public class AesFileInputStream extends InputStreamWrapper {
    private static final int DEFAULT_THREADS = 1;
    private AesStream[] streams;
    private final AesFile salmonFile;
    private final int threads;
    private ExecutorService executor;


	/**
     * Instantiate a seekable stream from an encrypted file source
     *
     * @param salmonFile   The source file.
     * @throws IOException Thrown if there is an IO error.
     */
    public AesFileInputStream(AesFile salmonFile) throws IOException {
		this(salmonFile, 1, 0, 1, 32768);
	}
    /**
     * Instantiate a seekable stream from an encrypted file source
     *
     * @param salmonFile   The source file.
     * @param buffersCount Number of buffers to use.
     * @param bufferSize   The length of each buffer.
     * @param threads      The number of threads/streams to source the file in parallel.
     * @param backOffset   The back offset.
     * @throws IOException Thrown if there is an IO error.
     */
    public AesFileInputStream(AesFile salmonFile, int buffersCount, int bufferSize, int threads, int backOffset) throws IOException {
        super(null, buffersCount, bufferSize, backOffset, salmonFile.getFileChunkSize() > 0 ? salmonFile.getFileChunkSize() : Generator.BLOCK_SIZE);
        this.salmonFile = salmonFile;
        this.setTotalSize(salmonFile.getLength());
        if (threads == 0)
            threads = DEFAULT_THREADS;
        if((threads & (threads-1)) != 0)
            throw new RuntimeException("Threads needs to be a power of 2 (ie 1,2,4,8)");
        this.threads = threads;
        this.setPositionEnd(getTotalSize() - 1);
        createStreams();
    }

    /**
     * Method creates the parallel streams for reading from the file
     */
    private void createStreams() throws IOException {
        executor = Executors.newFixedThreadPool(threads);
        streams = new AesStream[threads];
        try {
            for (int i = 0; i < threads; i++) {
                streams[i] = salmonFile.getInputStream();
            }
        } catch (SecurityException | IntegrityException ex) {
            throw new IOException("Could not create streams", ex);
        }
    }


    /**
     * Fills a cache buffer with the decrypted data from the encrypted source file.
     *
     * @param cacheBuffer The cache buffer that will store the decrypted contents
	 * @param startPosition The start position
     * @param length      The length of the data requested
     */
    @Override
    protected synchronized int fillBuffer(Buffer cacheBuffer, long startPosition, int length) throws IOException {
        int bytesRead;
        if (threads == 1) {
            bytesRead = fillBufferPart(cacheBuffer, startPosition, 0, length, streams[0]);
        } else {
            bytesRead = fillBufferMulti(cacheBuffer, startPosition, length);
        }
        return bytesRead;
    }


    /**
     * Fill the buffer using parallel streams for performance
     *
     * @param cacheBuffer       The cache buffer that will store the decrypted data
     * @param startPosition     The source file position the read will start from
     * @param totalBufferLength The buffer size that will be used to read from the file
     */
    private int fillBufferMulti(Buffer cacheBuffer, long startPosition, int totalBufferLength) {
        final int[] bytesRead = {0};
        AtomicReference<IOException> ex = new AtomicReference<>();
        // Multithreaded decryption jobs
        CountDownLatch countDownLatch = new CountDownLatch(threads);
        boolean needsBackOffset = totalBufferLength == this.getBufferSize();
        int partSize;
        if(needsBackOffset) {
            partSize = (int) Math.ceil((totalBufferLength - getBackOffset()) / (float) threads);
        } else {
            partSize = (int) Math.ceil(totalBufferLength / (float) threads);
        }

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                int start = partSize * index;
                if(index > 0 && needsBackOffset) {
                    start += getBackOffset();
                }
                int length;
                if (index == 0 && needsBackOffset) {
                    length = partSize + getBackOffset();
                } else if (index == threads - 1)
                    length = this.getBufferSize() - start;
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
     * Close the stream and associated backed streams and clear buffers.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    public void close() throws IOException {
        closeStreams();
        executor.shutdownNow();
        super.close();
    }

    /**
     * Close all back streams.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    private synchronized void closeStreams() throws IOException {
        for (int i = 0; i < threads; i++) {
            if (streams[i] != null)
                streams[i].close();
            streams[i] = null;
        }
    }
}

