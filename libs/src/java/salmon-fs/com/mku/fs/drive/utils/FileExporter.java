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
 * Exports IVirtualFile(s) from a VirtualDrive.
 */
public abstract class FileExporter {
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
     * Runs before export
     *
     * @param sourceFile The file that will be imported
     * @param integrity  If integrity verification is enabled
     * @throws IOException If there is a problem with the file preparation.
     */
    protected abstract void onPrepare(IVirtualFile sourceFile, boolean integrity) throws IOException;

    /**
     * Get the minimum part of file that can be exported in parallel.
     *
     * @param file The file
     * @return The number of bytes
     * @throws IOException If there was a problem calculating the size.
     */
    protected abstract long getMinimumPartSize(IVirtualFile file) throws IOException;

    /**
     * Initialize the exporter.
     *
     * @param bufferSize The buffer size to use while exporting
     * @param threads    The number of parallel threads to run
     */
    public void initialize(int bufferSize, int threads) {
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (threads <= 0)
            threads = DEFAULT_THREADS;
        this.bufferSize = bufferSize;
        this.threads = threads;
    }

    /**
     * Check if export is running
     *
     * @return True if running
     */
    public boolean isRunning() {
        return !stopped;
    }

    /**
     * Stop current export
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Export a file from the drive to the external directory path
     *
     * @param fileToExport The file that will be exported
     * @param exportDir    The external directory the file will be exported to
     * @return The exported file
     * @throws Exception Thrown if error occurs during export
     */
    public IFile exportFile(IVirtualFile fileToExport, IFile exportDir) throws Exception {
        return exportFile(fileToExport, exportDir, null);
    }

    /**
     * Export a file from the drive to the external directory path
     *
     * @param fileToExport The file that will be exported
     * @param exportDir    The external directory the file will be exported to
     * @param options      The options
     * @return The exported file
     * @throws Exception Thrown if error occurs during export
     */
    public IFile exportFile(IVirtualFile fileToExport, IFile exportDir, FileExportOptions options) throws Exception {
        if (options == null)
            options = new FileExportOptions();

        if (isRunning())
            throw new Exception("Another export is running");
        if (fileToExport.isDirectory())
            throw new Exception("Cannot export directory, use VirtualFileCommander instead");

        final IFile exportFile;
        String filename = options.filename != null ? options.filename : fileToExport.getName();
        try {
            stopped = false;
            final long[] totalBytesWritten = new long[]{0};
            failed = false;
            lastException = null;

            if (!exportDir.exists())
                exportDir.mkdir();
            exportFile = exportDir.createFile(filename);
            onPrepare(fileToExport, options.integrity);

            final long fileSize = fileToExport.getLength();
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = getMinimumPartSize(fileToExport);
            if (partSize > minPartSize && threads > 1) {
                partSize = (int) Math.ceil(fileSize / (float) threads);
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = (int) (fileSize / partSize);
            }

            if (runningThreads == 1) {
                exportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, options.onProgressChanged);
            } else {
                this.submitExportJobs(runningThreads, partSize, fileToExport, exportFile, totalBytesWritten,
                        options.integrity, options.onProgressChanged);
            }

            if (stopped)
                exportFile.delete();
            else if (options.deleteSource)
                fileToExport.getRealFile().delete();
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
        return exportFile;
    }

    private void submitExportJobs(int runningThreads, long partSize, IVirtualFile fileToExport, IFile exportFile, long[] totalBytesWritten, boolean integrity, BiConsumer<Long, Long> onProgress) throws IOException, InterruptedException {
        long fileSize = fileToExport.getLength();

        // we use a countdown latch which is better suited with executor than Thread.join.
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
                    exportFilePart(fileToExport, exportFile, start, length, totalBytesWritten, onProgress);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                done.countDown();
            });
        }
        done.await();
    }

    /**
     * Export a file part from the drive.
     *
     * @param fileToExport      The file the part belongs to
     * @param exportFile        The file to copy the exported part to
     * @param start             The start position on the file
     * @param count             The length of the bytes to be decrypted
     * @param totalBytesWritten The total bytes that were written to the external file
     */
    private void exportFilePart(IVirtualFile fileToExport, IFile exportFile, long start, long count,
                                long[] totalBytesWritten, BiConsumer<Long, Long> onProgress) throws IOException {
        long startTime = System.currentTimeMillis();
        long totalPartBytesWritten = 0;

        RandomAccessStream targetStream = null;
        RandomAccessStream sourceStream = null;

        try {
            targetStream = exportFile.getOutputStream();
            targetStream.setPosition(start);

            sourceStream = fileToExport.getInputStream();
            sourceStream.setPosition(start);

            int nBufferSize = bufferSize / sourceStream.getAlignSize() * sourceStream.getAlignSize();
            byte[] bytes = new byte[nBufferSize];

            int bytesRead;
            while ((bytesRead = sourceStream.read(bytes, 0, (int) Math.min((long) bytes.length,
                    count - totalPartBytesWritten))) > 0 && totalPartBytesWritten < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalPartBytesWritten += bytesRead;

                totalBytesWritten[0] += bytesRead;
                if (onProgress != null)
                    onProgress.accept(totalBytesWritten[0], fileToExport.getLength());
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
     * Close exporter and release resources.
     */
    public void close() {
        executor.shutdownNow();
    }

    /**
     * File importer options
     */
    public static class FileExportOptions {
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
