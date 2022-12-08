package com.mku11.salmonfs;
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

import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.SalmonStream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalmonFileImporter {
    private static boolean enableLog;
    private static boolean enableLogDetails;
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;
    private static final int DEFAULT_THREADS = 1;
    private static final int MIN_FILE_SIZE = 2 * 1024 * 1024;
    private static final boolean enableMultiThread = true;

    /**
     * Informs when the task progress has changed
     */
    public OnProgressChanged onTaskProgressChanged;
    private int bufferSize;
    private int threads;
    private boolean stopped = true;
    private boolean failed = false;

    /**
     * Constructs a file importer that can be used to import files to the salmon vault
     *
     * @param bufferSize Buffer size to be used when encrypting files.
     *                   If using integrity this value has to be a multiple of the Chunk size.
     *                   If not using integrity it should be a multiple of the AES block size for better performance
     * @param threads
     */
    public SalmonFileImporter(int bufferSize, int threads, Boolean Integrity) {
        this.bufferSize = bufferSize;
        if (this.bufferSize == 0)
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        this.threads = threads;
        if (this.threads == 0)
            this.threads = DEFAULT_THREADS;
    }


    public static void setEnableLog(boolean value) {
        enableLog = value;
    }

    public static void setEnableLogDetails(boolean value) {
        enableLogDetails = value;
    }

    /**
     * Stops all current importing tasks
     */
    public void stop() {
        stopped = true;
    }

    public boolean isRunning() {
        return !stopped;
    }

    // TODO: functional interface
    public interface OnProgressChanged {
        void onProgressChanged(Object sender, long bytesRead, long totalBytesRead, String message);
    }

    /**
     * Imports a real file into the file vault.
     *
     * @param fileToImport The external file that will be imported
     * @param dir          The directory inside the vault that it will be imported
     * @param deleteSource If true delete the source file
     */
    public SalmonFile importFile(final IRealFile fileToImport, SalmonFile dir, boolean deleteSource,
                                 Boolean integrity, int fileCount, int totalFiles) throws Exception {
        //FIXME: multithreaded during import is causing issues
        if (!enableMultiThread && threads != 1)
            throw new UnsupportedOperationException("Multi threaded is not supported");

        long startTime = 0;
        stopped = false;
        if (enableLog) {
            startTime = SalmonTime.currentTimeMillis();
        }
        final long[] totalBytesRead = new long[]{0};
        failed = false;


        final SalmonFile salmonFile = dir.createFile(fileToImport.getBaseName());
        salmonFile.setAllowOverwrite(true);
        if (integrity != null)
            salmonFile.setApplyIntegrity(integrity, null, null);
        final long fileSize = fileToImport.length();
        int runningThreads = 1;
        long partSize = fileSize;

        if (fileSize > MIN_FILE_SIZE) {
            partSize = (int) Math.ceil(fileSize / (float) threads);
            // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
            long minimumPartSize = salmonFile.getMinimumPartSize();
            long rem = partSize % minimumPartSize;
            if (rem != 0)
                partSize += minimumPartSize - rem;

            runningThreads = (int) (fileSize / partSize);
            if (fileSize % partSize != 0)
                runningThreads++;
        }
        final CountDownLatch done = new CountDownLatch(runningThreads);
        ExecutorService executor = Executors.newFixedThreadPool(runningThreads);
        final long finalPartSize = partSize;
        for (int i = 0; i < runningThreads; i++) {
            final int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    long start = finalPartSize * index;
                    long length = Math.min(finalPartSize, fileSize - start);
                    try {
                        importFileChunk(fileToImport, salmonFile, start, length, totalBytesRead, fileCount, totalFiles);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    done.countDown();
                }
            });
        }
        done.await();
        if (stopped)
            salmonFile.getRealFile().delete();
        else {
            if (deleteSource)
                fileToImport.delete();
        }
        if (enableLog) {
            long total = SalmonTime.currentTimeMillis() - startTime;
            System.out.println("SalmonFileImporter AesType: " + SalmonStream.getProviderType() + " File: " + fileToImport.getBaseName()
                    + " imported and signed " + totalBytesRead[0] + " bytes in total time: " + total + " ms"
                    + ", avg speed: " + totalBytesRead[0] / (float) total + " bytes/sec");
        }
        if (stopped || failed) {
            stopped = true;
            return null;
        }
        stopped = true;
        return salmonFile;
    }

    /**
     * Import a file chunk into a file in the vault
     *
     * @param fileToImport   The external file that will be imported
     * @param salmonFile     The file that will be imported to
     * @param start          The start position of the byte data that will be imported
     * @param count          The length of the file content that will be imported
     * @param totalBytesRead The total bytes read from the external file
     */
    private void importFileChunk(IRealFile fileToImport, SalmonFile salmonFile,
                                 long start, long count, long[] totalBytesRead, int fileCount, int totalFiles) throws IOException {
        long startTime = SalmonTime.currentTimeMillis();
        long totalChunkBytesRead = 0;

        SalmonStream targetStream = null;
        AbsStream sourceStream = null;

        try {
            targetStream = salmonFile.getOutputStream();
            targetStream.position(start);

            sourceStream = fileToImport.getInputStream();
            sourceStream.position(start);

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            if (enableLogDetails) {
                System.out.println("SalmonFileImporter: FileChunk: " + salmonFile.getRealFile().getBaseName() + ": " + salmonFile.getBaseName()
                        + " start = " + start + " count = " + count);
            }
            while ((bytesRead = sourceStream.read(bytes, 0, Math.min(bytes.length, (int) (count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalChunkBytesRead += bytesRead;

                totalBytesRead[0] += bytesRead;
                notifyProgressListener(totalBytesRead[0], fileToImport.length(), (fileCount + 1) + "/" + totalFiles + " Importing File: " + fileToImport.getBaseName());
            }
            if (enableLogDetails) {
                long total = SalmonTime.currentTimeMillis() - startTime;
                System.out.println("SalmonFileImporter: File Chunk: " + fileToImport.getBaseName()
                        + " imported " + totalChunkBytesRead + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalChunkBytesRead / (float) total + " bytes/sec");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            failed = true;
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

    private void notifyProgressListener(long bytesRead, long totalBytes, String message) {
        if (onTaskProgressChanged != null)
            onTaskProgressChanged.onProgressChanged(this, bytesRead, totalBytes, message);
    }

}