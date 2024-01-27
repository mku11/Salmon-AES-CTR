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

import com.mku.func.BiConsumer;
import com.mku.io.RandomAccessStream;
import com.mku.salmon.io.SalmonStream;
import com.mku.file.IRealFile;
import com.mku.salmonfs.SalmonFile;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalmonFileExporter {
    /**
     * The global default buffer size to use when reading/writing on the SalmonStream.
     */
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /**
     * The global default threads to use.
     */
    private static final int DEFAULT_THREADS = 1;

    /**
     * Minimum file size to use parallelism. Anything less will use single thread.
     */
    private static final int MIN_FILE_SIZE = 2 * 1024 * 1024;

    /**
     * True if multithreading is enabled.
     */
    private static final boolean enableMultiThread = true;

    private static boolean enableLog;
    private static boolean enableLogDetails;

    /**
     * Current buffer size.
     */
    private final int bufferSize;

    /**
     * Current threads.
     */
    private final int threads;

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

    public SalmonFileExporter(int bufferSize, int threads) {
        if (bufferSize == 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (threads == 0)
            threads = DEFAULT_THREADS;
        this.bufferSize = bufferSize;
        this.threads = threads;
    }

    public static void setEnableLog(boolean value) {
        enableLog = value;
    }

    public static void setEnableLogDetails(boolean value) {
        enableLogDetails = value;
    }

    public boolean isRunning() {
        return !stopped;
    }

    /**
     *
     */
    public void stop() {
        stopped = true;
    }

    /**
     * Export a file from the drive to the external directory path
     *
     * @param fileToExport The file that will be exported
     * @param exportDir    The external directory the file will be exported to
     * @param filename     The filename to use
     * @param deleteSource Delete the source file when the export finishes successfully
     * @param integrity    True to verify integrity
     */
    public IRealFile exportFile(SalmonFile fileToExport, IRealFile exportDir, String filename,
                                boolean deleteSource, boolean integrity, BiConsumer<Long,Long> onProgress) throws Exception {

        if (isRunning())
            throw new Exception("Another export is running");
        if (fileToExport.isDirectory())
            throw new Exception("Cannot export directory, use SalmonFileCommander instead");

        final IRealFile exportFile;
        filename = filename != null ? filename : fileToExport.getBaseName();
        try {
            if (!enableMultiThread && threads != 1)
                throw new UnsupportedOperationException("Multithreading is not supported");

            long startTime = 0;
            stopped = false;
            if (enableLog) {
                startTime = System.currentTimeMillis();
            }
            final long[] totalBytesWritten = new long[]{0};
            failed = false;

            if (!exportDir.exists())
                exportDir.mkdir();
            exportFile = exportDir.createFile(filename);
            // we use the drive hash key for integrity verification
            fileToExport.setVerifyIntegrity(integrity, null);

            final long fileSize = fileToExport.getSize();
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = SalmonFileUtils.getMinimumPartSize(fileToExport);
            if (partSize > minPartSize) {
                partSize = (int) Math.ceil(fileSize / (float) threads);
                partSize -= partSize % minPartSize;
                runningThreads = (int) (fileSize / partSize);
            }

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
            if (stopped)
                exportFile.delete();
            else if (deleteSource)
                fileToExport.getRealFile().delete();
            if (lastException != null)
                throw lastException;
            if (enableLog) {
                long total = System.currentTimeMillis() - startTime;
                System.out.println("SalmonFileExporter AesType: " + SalmonStream.getAesProviderType()
                        + " File: " + fileToExport.getBaseName() + " verified and exported "
                        + totalBytesWritten[0] + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalBytesWritten[0] / (float) total + " bytes/sec");
            }
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

    /**
     * Export a file part from the drive.
     *
     * @param fileToExport      The file the part belongs to
     * @param exportFile        The file to copy the exported part to
     * @param start             The start position on the file
     * @param count             The length of the bytes to be decrypted
     * @param totalBytesWritten The total bytes that were written to the external file
     */
    private void exportFilePart(SalmonFile fileToExport, IRealFile exportFile, long start, long count,
                                long[] totalBytesWritten, BiConsumer<Long,Long> onProgress) throws IOException {
        long startTime = System.currentTimeMillis();
        long totalPartBytesWritten = 0;

        RandomAccessStream targetStream = null;
        RandomAccessStream sourceStream = null;

        try {
            targetStream = exportFile.getOutputStream();
            targetStream.position(start);

            sourceStream = fileToExport.getInputStream();
            sourceStream.position(start);

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            if (enableLogDetails) {
                System.out.println("SalmonFileExporter: FilePart: " + fileToExport.getRealFile().getBaseName() + ": "
                        + fileToExport.getBaseName() + " start = " + start + " count = " + count);
            }

            while ((bytesRead = sourceStream.read(bytes, 0, Math.min(bytes.length,
                    (int) (count - totalPartBytesWritten)))) > 0 && totalPartBytesWritten < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalPartBytesWritten += bytesRead;

                totalBytesWritten[0] += bytesRead;
                if(onProgress != null)
                    onProgress.accept(totalBytesWritten[0], fileToExport.getSize());
            }
            if (enableLogDetails) {
                long total = System.currentTimeMillis() - startTime;
                System.out.println("SalmonFileExporter: File Part: " + fileToExport.getBaseName() + " exported " + totalPartBytesWritten
                        + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalBytesWritten[0] / (float) total + " bytes/sec");
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
