package com.mku.salmon;
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

import com.mku.salmon.integrity.IntegrityException;
import com.mku.streams.RandomAccessStream;
import com.mku.streams.MemoryStream;
import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.transform.AesCTRTransformer;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class that decrypts byte arrays.
 */
public class Decryptor {

    /**
     * The number of parallel threads to use.
     */
    private final int threads;

    /**
     * Executor for parallel tasks.
     */
    private ExecutorService executor;

    /**
     * The buffer size to use.
     */
    private final int bufferSize;

    /**
     * Instantiate an encryptor.
     */
    public Decryptor() {
        this.threads = 1;
        // we use the chunks size as default this keeps buffers aligned in case
        // integrity is enabled.
        this.bufferSize = Integrity.DEFAULT_CHUNK_SIZE;
    }

    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads The number of threads to use.
     */
    public Decryptor(int threads) {
        this.threads = threads;
        executor = Executors.newFixedThreadPool(threads);
        this.bufferSize = Integrity.DEFAULT_CHUNK_SIZE;
    }

    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads    The number of threads to use.
     * @param bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    public Decryptor(int threads, int bufferSize) {
        this.threads = threads;
        executor = Executors.newFixedThreadPool(threads);
        this.bufferSize = bufferSize;
    }

    /**
     * Decrypts a byte array using parallel threads.
     *
     * @param data          The input data to be decrypted.
     * @param key           The AES key to use for decryption.
     * @param nonce         The nonce to use for decryption.
     * @param hasHeaderData The header data.
     * @return The output buffer containing the decrypted data.
     * @throws IOException              Thrown if there is an error with the stream.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public byte[] decrypt(byte[] data, byte[] key, byte[] nonce, boolean hasHeaderData)
            throws IOException {
        return decrypt(data, key, nonce, hasHeaderData, false, null, null);
    }

    /**
     * Decrypt a byte array using AES256 based on the provided key and nonce.
     *
     * @param data          The input data to be decrypted.
     * @param key           The AES key to use for decryption.
     * @param nonce         The nonce to use for decryption.
     * @param hasHeaderData The header data.
     * @param integrity     Verify hash integrity in the data.
     * @param hashKey       The hash key to be used for integrity.
     * @param chunkSize     The chunk size.
     * @return The byte array with the decrypted data.
     * @throws IOException              Thrown if there is a problem with decoding the array.
     * @throws SecurityException  Thrown if the key and nonce are not provided.
     * @throws IOException Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public byte[] decrypt(byte[] data, byte[] key, byte[] nonce,
                          boolean hasHeaderData,
                          boolean integrity, byte[] hashKey, Integer chunkSize)
            throws IOException {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (!hasHeaderData && nonce == null)
            throw new SecurityException("Need to specify a nonce if the file doesn't have a header");

        if (integrity)
            chunkSize = chunkSize == null ? Integrity.DEFAULT_CHUNK_SIZE : chunkSize;

        MemoryStream inputStream = new MemoryStream(data);
        Header header;
        byte[] headerData = null;
        if (hasHeaderData) {
            header = Header.parseHeaderData(inputStream);
            if (header.getChunkSize() > 0)
                integrity = true;
            chunkSize = header.getChunkSize();
            nonce = header.getNonce();
            headerData = header.getHeaderData();
        }
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        int realSize = (int) AesStream.getActualSize(data, key, nonce, EncryptionMode.Decrypt,
                headerData, integrity, chunkSize, hashKey);
        byte[] outData = new byte[realSize];

        if (threads == 1) {
            decryptData(inputStream, 0, inputStream.length(), outData,
                    key, nonce, headerData, integrity, hashKey, chunkSize);
        } else {
            decryptDataParallel(data, outData,
                    key, hashKey, nonce, headerData,
                    chunkSize, integrity);
        }

        return outData;
    }

    /**
     * Decrypt stream using parallel threads.
     *
     * @param data       The input data to be decrypted
     * @param outData    The output buffer with the decrypted data.
     * @param key        The AES key.
     * @param hashKey    The hash key.
     * @param nonce      The nonce to be used for decryption.
     * @param headerData The header data.
     * @param chunkSize  The chunk size.
     * @param integrity  True to verify integrity.
     */
    private void decryptDataParallel(byte[] data, byte[] outData,
                                     byte[] key, byte[] hashKey, byte[] nonce, byte[] headerData,
                                     Integer chunkSize, boolean integrity) {
        int runningThreads = 1;
        long partSize = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        long minPartSize = AesCTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = (long) chunkSize;
        else if (integrity)
            minPartSize = Integrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = (int) Math.ceil(data.length / (float) threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = (int) (data.length / partSize);
        }

        submitDecryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, headerData,
                integrity, chunkSize);
    }

    /**
     * Submit decryption parallel jobs.
     *
     * @param runningThreads The number of threads to submit.
     * @param partSize       The data length of each part that belongs to each thread.
     * @param data           The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *                       thread will read each own part.
     * @param outData        The buffer of data containing the decrypted data.
     * @param key            The AES key.
     * @param hashKey        The hash key for integrity validation.
     * @param nonce          The nonce for the data.
     * @param headerData     The header data common to all parts.
     * @param integrity      True to verify the data integrity.
     * @param chunkSize      The chunk size.
     */
    private void submitDecryptJobs(int runningThreads, long partSize,
                                   byte[] data, byte[] outData,
                                   byte[] key, byte[] hashKey, byte[] nonce, byte[] headerData,
                                   boolean integrity, Integer chunkSize) {
        final CountDownLatch done = new CountDownLatch(runningThreads);
        AtomicReference<Exception> ex = new AtomicReference<>();
        for (int i = 0; i < runningThreads; i++) {
            final int index = i;
            executor.submit(() ->
            {
                try {
                    long start = partSize * index;
                    long length;
                    if (index == runningThreads - 1)
                        length = data.length - start;
                    else
                        length = partSize;
                    MemoryStream ins = new MemoryStream(data);
                    decryptData(ins, start, length, outData, key, nonce, headerData,
                            integrity, hashKey, chunkSize);
                } catch (Exception ex1) {
                    ex.set(ex1);
                }
                done.countDown();
            });
        }

        try {
            done.await();
        } catch (InterruptedException ignored) {
        }

        if (ex.get() != null) {
            try {
                throw ex.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Decrypt the data stream.
     *
     * @param inputStream The Stream to be decrypted.
     * @param start       The start position of the stream to be decrypted.
     * @param count       The number of bytes to be decrypted.
     * @param outData     The buffer with the decrypted data.
     * @param key         The AES key to be used.
     * @param nonce       The nonce to be used.
     * @param headerData  The header data to be used.
     * @param integrity   True to verify integrity.
     * @param hashKey     The hash key to be used for integrity verification.
     * @param chunkSize   The chunk size.
     * @throws IOException              Thrown if there is an error with the stream.
     * @throws SecurityException  Thrown if there is a security exception with the stream.
     * @throws IntegrityException Thrown if the stream is corrupt or tampered with.
     */
    private void decryptData(RandomAccessStream inputStream, long start, long count, byte[] outData,
                             byte[] key, byte[] nonce,
                             byte[] headerData, boolean integrity, byte[] hashKey, Integer chunkSize)
            throws IOException {
        AesStream stream = null;
        MemoryStream outputStream = null;
        try {
            outputStream = new MemoryStream(outData);
            outputStream.setPosition(start);
            stream = new AesStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                    headerData, integrity, chunkSize, hashKey);
            stream.setPosition(start);
            long totalChunkBytesRead = 0;
            // align to the chunksize if available
            int buffSize = Math.max(bufferSize, stream.getChunkSize());
			// set the same buffer size for the internal stream
			stream.setBufferSize(buffSize);
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = stream.read(buff, 0, Math.min(buff.length, (int) (count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count) {
                outputStream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            outputStream.flush();
        } catch (IOException | SecurityException | IntegrityException ex) {
            ex.printStackTrace();
            throw new SecurityException("Could not decrypt data", ex);
        } finally {
            if (inputStream != null)
                inputStream.close();
            if (stream != null)
                stream.close();
            if (outputStream != null)
                outputStream.close();
        }
    }

    /**
     * Close the decryptor and release associated resources
     */
    public void close() {
        if (executor != null)
            executor.shutdownNow();
    }

    @Override
    protected void finalize() {
        close();
    }
}
