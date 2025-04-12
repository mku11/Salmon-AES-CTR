package com.mku.fs.drive.utils;
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

import com.mku.fs.file.IFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Import IFile(s) into a VirtualDrive.
 */
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
     * Current buffer size.
     */
    private int bufferSize;

    /**
     * Current threads.
     */
    private int threads;

    /**
     * Check if last job was stopped by the user.
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

    /**
     * Runs before import
     *
     * @param targetFile The target file imported
     * @param integrity  If integrity verification is enabled
     * @throws IOException If there is a problem with the file preparation.
     */
    protected abstract void onPrepare(IVirtualFile targetFile, boolean integrity) throws IOException;

    /**
     * Get the minimum part of file that can be imported in parallel.
     *
     * @param file The file
     * @return The number of bytes
     * @throws IOException If there was a problem calculating the size.
     */
    protected abstract long getMinimumPartSize(IVirtualFile file) throws IOException;

    /**
     * Constructs a file importer that can be used to import files into the drive
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads    The threads to use for import
     */
    public void initialize(int bufferSize, int threads) {
        this.bufferSize = bufferSize;
        if (this.bufferSize <= 0)
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        this.threads = threads;
        if (this.threads <= 0)
            this.threads = DEFAULT_THREADS;
    }

    /**
     * Stops all current importing tasks
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Check if importer is currently running a job.
     *
     * @return True if running
     */
    public boolean isRunning() {
        return !stopped;
    }

    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in into the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @return The imported file
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile importFile(IFile fileToImport, IVirtualFile dir) throws Exception {
        return importFile(fileToImport, null);
    }

    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in into the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @param options      The options
     * @return The imported file
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile importFile(IFile fileToImport, IVirtualFile dir, FileImportOptions options) throws Exception {
        if (options == null)
            options = new FileImportOptions();

        if (isRunning())
            throw new Exception("Another import is running");
        if (fileToImport.isDirectory())
            throw new Exception("Cannot import directory, use AesFileCommander instead");

        String filename = options.filename != null ? options.filename : fileToImport.getName();
        final long[] totalBytesRead = new long[]{0};
        final IVirtualFile importedFile;
        try {
            stopped = false;
            failed = false;
            lastException = null;

            importedFile = dir.createFile(filename);
            this.onPrepare(importedFile, options.integrity);

            final long fileSize = fileToImport.getLength();
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = getMinimumPartSize(importedFile);
            if (partSize > minPartSize && threads > 1) {
                partSize = (int) Math.ceil(fileSize / (float) threads);
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = (int) (fileSize / partSize);
            }

            if (runningThreads == 1) {
                importFilePart(fileToImport, importedFile, 0, fileSize, totalBytesRead, options.onProgressChanged);
            } else {
                this.submitImportJobs(runningThreads, partSize, fileToImport, importedFile, totalBytesRead,
                        options.integrity, options.onProgressChanged);
            }

            if (stopped)
                importedFile.getRealFile().delete();
            else if (options.deleteSource)
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

    private void submitImportJobs(int runningThreads, long partSize, IFile fileToImport, IVirtualFile importedFile, long[] totalBytesRead, boolean integrity, BiConsumer<Long, Long> onProgress) throws InterruptedException {
        long fileSize = fileToImport.getLength();

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
    }

    /**
     * Import a file part into a file in the drive.
     *
     * @param fileToImport   The external file that will be imported
     * @param aesFile        The file that will be imported to
     * @param start          The start position of the byte data that will be imported
     * @param count          The length of the file content that will be imported
     * @param totalBytesRead The total bytes read from the external file
     * @param onProgress     Progress observer
     */
    private void importFilePart(IFile fileToImport, IVirtualFile aesFile,
                                long start, long count, long[] totalBytesRead, BiConsumer<Long, Long> onProgress) throws IOException {
        long totalPartBytesRead = 0;

        RandomAccessStream targetStream = null;
        RandomAccessStream sourceStream = null;

        try {
            targetStream = aesFile.getOutputStream();
            targetStream.setPosition(start);

            sourceStream = fileToImport.getInputStream();
            sourceStream.setPosition(start);

            int nBufferSize = bufferSize / targetStream.getAlignSize() * targetStream.getAlignSize();
            byte[] bytes = new byte[nBufferSize];

            int bytesRead;
            while ((bytesRead = sourceStream.read(bytes, 0, (int) Math.min((long) bytes.length, count - totalPartBytesRead))) > 0
                    && totalPartBytesRead < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalPartBytesRead += bytesRead;

                totalBytesRead[0] += bytesRead;
                if (onProgress != null)
                    onProgress.accept(totalBytesRead[0], fileToImport.getLength());
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

    /**
     * Stops the current import
     */
    public void close() {
        executor.shutdownNow();
    }


    /**
     * File importer options
     */
    public static class FileImportOptions {
        /**
         * Override the filename
         */
        public String filename;

        /**
         * Delete the source file after completion.
         */
        public boolean deleteSource = false;

        /**
         * True to enable integrity.
         */
        public boolean integrity = false;

        /**
         * Callback when progress changes
         */
        public BiConsumer<Long, Long> onProgressChanged;
    }
}