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

public class SalmonFileExporter {
    private static boolean enableLog = false;
    private static boolean enableLogDetails = false;
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;
    private static final int DEFAULT_THREADS = 1;
    private static final int MIN_FILE_SIZE = 2 * 1024 * 1024;
    private static final boolean enableMultiThread = true;

    public OnProgressChanged onTaskProgressChanged;

    private final int bufferSize;
    private final int threads;
    private boolean stopped = true;
    private boolean failed = false;


    private Exception lastException;

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

    public interface OnProgressChanged {
        void onProgressChanged(SalmonFile file, long bytesRead, long totalBytesRead, String message);
    }

    public void stop() {
        stopped = true;
    }

    /**
     * Export a file from the vault to the external directory path
     *
     * @param fileToExport The file that will be exported
     * @param exportDir    The external directory the file will be exported to
     * @param deleteSource Delete the source file when the export finishes successfully
     */
    public IRealFile exportFile(final SalmonFile fileToExport, IRealFile exportDir, boolean deleteSource,
                                Boolean integrity, int fileCount, int totalFiles) throws Exception {
        final IRealFile exportFile;

        try {
            if (!enableMultiThread && threads != 1)
                throw new UnsupportedOperationException("Multi threaded is not supported");

            long startTime = 0;
            stopped = false;
            if (enableLog) {
                startTime = SalmonTime.currentTimeMillis();
            }
            final long[] totalBytesWritten = new long[]{0};
            failed = false;

            if (!exportDir.exists())
                exportDir.mkdir();
            String targetFileName = fileToExport.getBaseName();
            IRealFile tfile = exportDir.getChild(targetFileName);
            if (tfile != null && tfile.exists()) {
                String filename = SalmonDriveManager.getDrive().getFileNameWithoutExtension(targetFileName);
                String ext = SalmonDriveManager.getDrive().getExtensionFromFileName(targetFileName);
                targetFileName = filename + "_" + SalmonTime.currentTimeMillis() + "." + ext;
            }
            exportFile = exportDir.createFile(targetFileName);

            if (integrity != null)
                fileToExport.setVerifyIntegrity(integrity, null);

            final long fileSize = fileToExport.getSize();
            long partSize = fileSize;
            int runningThreads = 1;
            if (fileSize > MIN_FILE_SIZE) {
                partSize = (int) Math.ceil(fileSize / (float) threads);

                // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
                long minPartSize = fileToExport.getMinimumPartSize();

                // calculate the last chunk size
                long rem = partSize % minPartSize;
                if (rem != 0)
                    partSize += minPartSize - rem;

                runningThreads = (int) (fileSize / partSize);
                if (fileSize % partSize != 0)
                    runningThreads++;
            }
            final CountDownLatch done = new CountDownLatch(runningThreads);
            ExecutorService executor = Executors.newFixedThreadPool(runningThreads);
            final long finalPartSize = partSize;
            for (int i = 0; i < runningThreads; i++) {
                final int index = i;
                executor.submit(() -> {
                    long start = finalPartSize * index;
                    long length = Math.min(finalPartSize, fileSize - start);
                    try {
                        exportFilePart(fileToExport, exportFile, start, length, totalBytesWritten, fileCount, totalFiles);
                    } catch (IOException e) {
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
                long total = SalmonTime.currentTimeMillis() - startTime;
                System.out.println("SalmonFileExporter AesType: " + SalmonStream.getProviderType() + " File: " + fileToExport.getBaseName() + " verified and exported "
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
     * Export a file chunk from the vault
     *
     * @param fileToExport      The file the part belongs to
     * @param exportFile        The file to copy the exported part to
     * @param start             The start position on the file
     * @param count             The length of the bytes to be decrypted
     * @param totalBytesWritten The total bytes that were written to the external file
     */
    private void exportFilePart(SalmonFile fileToExport, IRealFile exportFile, long start, long count,
                                long[] totalBytesWritten, int fileCount, int totalFiles) throws IOException {
        long startTime = SalmonTime.currentTimeMillis();
        long totalChunkBytesWritten = 0;

        AbsStream targetStream = null;
        AbsStream sourceStream = null;

        try {
            targetStream = exportFile.getOutputStream();
            targetStream.position(start);

            sourceStream = fileToExport.getInputStream();
            sourceStream.position(start);

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            if (enableLogDetails) {
                System.out.println("SalmonFileExporter: FileChunk: " + fileToExport.getRealFile().getBaseName() + ": "
                        + fileToExport.getBaseName() + " start = " + start + " count = " + count);
            }

            while ((bytesRead = sourceStream.read(bytes, 0, Math.min(bytes.length,
                    (int) (count - totalChunkBytesWritten)))) > 0 && totalChunkBytesWritten < count) {
                if (stopped)
                    break;

                targetStream.write(bytes, 0, bytesRead);
                totalChunkBytesWritten += bytesRead;

                totalBytesWritten[0] += bytesRead;
                notifyProgressListener(fileToExport, totalBytesWritten[0], fileToExport.getSize(), fileCount + "/" + totalFiles + " Exporting File: "
                        + fileToExport.getBaseName());
            }
            if (enableLogDetails) {
                long total = SalmonTime.currentTimeMillis() - startTime;
                System.out.println("SalmonFileExporter: File Chunk: " + fileToExport.getBaseName() + " exported " + totalChunkBytesWritten
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

    private void notifyProgressListener(SalmonFile file, long bytesRead, long totalBytes, String message) {
        if (onTaskProgressChanged != null)
            onTaskProgressChanged.onProgressChanged(file, bytesRead, totalBytes, message);
    }
}
