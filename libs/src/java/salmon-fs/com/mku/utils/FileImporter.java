package com.mku.utils;
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

import com.mku.file.IVirtualFile;
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;
import com.mku.file.IRealFile;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class FileImporter {
    /**
     * The global default buffer size to use when reading/writing on streams.
     */
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /**
     * The global default threads to use.
     */
    private static final int DEFAULT_THREADS = 1;

    /**
     * True if multithreading is enabled.
     */
    private static final boolean enableMultiThread = true;

    /**
     * Current buffer size.
     */
    private int bufferSize;

    /**
     * Current threads.
     */
    private int threads;

    /**
     * True if last job was stopped by the user.
     */
    private boolean stopped = true;

    /**
     * Failed if last job was failed.
     */
    private boolean failed = false;

    /**
     * Last exception occurred.
     */
    private Exception lastException;

    /**
     * The executor to be used for running parallel exports.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    protected abstract void onPrepare(IVirtualFile importedFile, boolean integrity) throws IOException;

    protected abstract long getMinimumPartSize(IVirtualFile file) throws IOException;

    /**
     * Constructs a file importer that can be used to import files to the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads The threads to use for import
     */
    public void initialize(int bufferSize, int threads) {
        this.bufferSize = bufferSize;
        if (this.bufferSize == 0)
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        this.threads = threads;
        if (this.threads == 0)
            this.threads = DEFAULT_THREADS;
    }

    /**
     * Stops all current importing tasks
     */
    public void stop() {
        stopped = true;
    }

    /**
     * True if importer is currently running a job.
     *
     * @return True if running
     */
    public boolean isRunning() {
        return !stopped;
    }

    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in to the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @param filename The filename to use
     * @param deleteSource If true delete the source file.
	 * @param integrity    Apply data integrity
	 * @param onProgress   Progress to notify
     * @return The imported file
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile importFile(IRealFile fileToImport, IVirtualFile dir, String filename,
                                   boolean deleteSource, boolean integrity, BiConsumer<Long,Long> onProgress) throws Exception {
        if (isRunning())
            throw new Exception("Another import is running");
        if (fileToImport.isDirectory())
            throw new Exception("Cannot import directory, use SalmonFileCommander instead");

        filename = filename != null ? filename : fileToImport.getBaseName();
        final long[] totalBytesRead = new long[]{0};
        final IVirtualFile importedFile;
        try {
            if (!enableMultiThread && threads != 1)
                throw new UnsupportedOperationException("Multithreading is not supported");
            stopped = false;
            failed = false;
            lastException = null;

            importedFile = dir.createFile(filename);
            this.onPrepare(importedFile, integrity);

            final long fileSize = fileToImport.length();
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = getMinimumPartSize(importedFile);
            if (partSize > minPartSize && threads > 1) {
                partSize = (int) Math.ceil(fileSize / (float) threads);
				if(partSize > minPartSize)
					partSize -= partSize % minPartSize;
				else
					partSize = minPartSize;
                runningThreads = (int) (fileSize / partSize);
            }

            // we use a countdown latch which is better suited with executor than Thread.join or CompletableFuture.
            final CountDownLatch done = new CountDownLatch(runningThreads);
            final long finalPartSize = partSize;
            final int finalRunningThreads = runningThreads;
            for (int i = 0; i < runningThreads; i++) {
                final int index = i;
                executor.submit(() -> {
                    long start = finalPartSize * index;
                    long length;
                    if (index == finalRunningThreads - 1)
                        length = fileSize - start;
                    else
                        length = finalPartSize;
                    try {
                        importFilePart(fileToImport, importedFile, start, length, totalBytesRead, onProgress);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    done.countDown();
                });
            }
            done.await();
            if (stopped)
                importedFile.getRealFile().delete();
            else if (deleteSource)
                fileToImport.delete();
            if (lastException != null)
                throw lastException;
        } catch (Exception ex) {
            ex.printStackTrace();
            failed = true;
            stopped = true;
            throw ex;
        }
        if (stopped || failed) {
            stopped = true;
            return null;
        }
        stopped = true;
        return importedFile;
    }

    /**
     * Import a file part into a file in the drive.
     *
     * @param fileToImport   The external file that will be imported
     * @param salmonFile     The file that will be imported to
     * @param start          The start position of the byte data that will be imported
     * @param count          The length of the file content that will be imported
     * @param totalBytesRead The total bytes read from the external file
	 * @param onProgress 	 Progress observer
     */
    private void importFilePart(IRealFile fileToImport, IVirtualFile salmonFile,
                                long start, long count, long[] totalBytesRead, BiConsumer<Long,Long> onProgress) throws IOException {
        long totalPartBytesRead = 0;

        RandomAccessStream targetStream = null;
        RandomAccessStream sourceStream = null;

        try {
            targetStream = salmonFile.getOutputStream();
            targetStream.setPosition(start);

            sourceStream = fileToImport.getInputStream();
            sourceStream.setPosition(start);

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = sourceStream.read(bytes, 0, (int) Math.min((long) bytes.length, count - totalPartBytesRead))) > 0
                    && totalPartBytesRead < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalPartBytesRead += bytesRead;

                totalBytesRead[0] += bytesRead;
                if (onProgress != null)
                    onProgress.accept(totalBytesRead[0], fileToImport.length());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            failed = true;
            lastException = ex;
            stop();
        } finally {
            if (targetStream != null) {
                targetStream.flush();
                targetStream.close();
            }
            if (sourceStream != null) {
                sourceStream.close();
            }
        }
    }

    @Override
    protected void finalize() {
        close();
    }

    public void close() {
        executor.shutdownNow();
    }
}