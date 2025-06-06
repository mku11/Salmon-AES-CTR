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

import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.EncryptionFormat;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.transform.AesCTRTransformer;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encrypts byte arrays.
 */
public class Encryptor {

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
    public Encryptor() {
        this(1, Integrity.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads The number of threads to use.
     */
    public Encryptor(int threads) {
		this(threads, Integrity.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Instantiate an encryptor with parallel tasks and buffer size.
     *
     * @param threads    The number of threads to use.
     * @param bufferSize The buffer size to use. It is recommended for performance  to use
     *                   a multiple of the chunk size if you enabled integrity
     *                   otherwise a multiple of the AES block size (16 bytes).
     */
    public Encryptor(int threads, int bufferSize) {
		if(threads <= 0)
			threads = 1;
        this.threads = threads;
		if(threads > 1)
			executor = Executors.newFixedThreadPool(threads);
        this.bufferSize = bufferSize;
    }

    /**
     * Encrypt a byte array and embedded the nonce.
     *
     * @param data  The data to encrypt.
     * @param key   The key to use.
     * @param nonce Nonce to use.
     * @return The encrypted data
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException        Thrown if there is an IO error.
     */
    public byte[] encrypt(byte[] data, byte[] key, byte[] nonce)
            throws IOException {
        return encrypt(data, key, nonce, EncryptionFormat.Salmon, false, null, 0);
    }

    /**
     * Encrypt a byte array.
     *
     * @param data   The data to encrypt.
     * @param key    The key to use.
     * @param nonce  Nonce to use.
     * @param format The format to use, see {@link EncryptionFormat}
     * @return The encrypted data
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException        Thrown if there is an IO error.
     */
    public byte[] encrypt(byte[] data, byte[] key, byte[] nonce, EncryptionFormat format)
            throws IOException {
        return encrypt(data, key, nonce, format, false, null, 0);
    }


    /**
     * Encrypts a byte array using the nonce, the integrity hash key.
     *
     * @param data      The byte array to be encrypted.
     * @param key       The AES key to be used.
     * @param nonce     The nonce to be used.
     * @param format    The format to use, see {@link EncryptionFormat}
     * @param integrity True if you want to calculate and store hash signatures for each chunkSize.
     * @param hashKey   Hash key to be used for all chunks.
     * @return The byte array with the encrypted data.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IOException        Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public byte[] encrypt(byte[] data, byte[] key, byte[] nonce, EncryptionFormat format,
                          boolean integrity, byte[] hashKey)
            throws IOException {
        return encrypt(data, key, nonce, format, integrity, hashKey, 0);
    }

    /**
     * Encrypts a byte array using the nonce, the integrity hash key, and the chunk size.
     *
     * @param data      The byte array to be encrypted.
     * @param key       The AES key to be used.
     * @param nonce     The nonce to be used.
     * @param format    The format to use, see {@link EncryptionFormat}
     * @param integrity True if you want to calculate and store hash signatures for each chunkSize.
     * @param hashKey   Hash key to be used for all chunks.
     * @param chunkSize The chunk size.
     * @return The byte array with the encrypted data.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IOException        Thrown if there is an IO error.
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public byte[] encrypt(byte[] data, byte[] key, byte[] nonce, EncryptionFormat format,
                          boolean integrity, byte[] hashKey, int chunkSize)
            throws IOException {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        if (integrity)
            chunkSize = chunkSize <= 0 ? Integrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;

        int realSize = (int) AesStream.getOutputSize(EncryptionMode.Encrypt, data.length, format, chunkSize);
        byte[] outData = new byte[realSize];

        if (threads == 1) {
            MemoryStream inputStream = new MemoryStream(data);
            encryptData(inputStream, 0, data.length, outData,
                    key, nonce, format, integrity, hashKey, chunkSize);
        } else {
            encryptDataParallel(data, outData,
                    key, hashKey, nonce, format,
                    chunkSize, integrity);
        }
        return outData;
    }

    /**
     * Encrypt stream using parallel threads.
     *
     * @param data      The input data to be encrypted
     * @param outData   The output buffer with the encrypted data.
     * @param key       The AES key.
     * @param hashKey   The hash key.
     * @param nonce     The nonce to be used for encryption.
     * @param format    The format to use, see {@link EncryptionFormat}
     * @param chunkSize The chunk size.
     * @param integrity True to apply integrity.
     */
    private void encryptDataParallel(byte[] data, byte[] outData,
                                     byte[] key, byte[] hashKey, byte[] nonce, EncryptionFormat format,
                                     int chunkSize, boolean integrity) {

        int runningThreads = 1;
        long partSize = data.length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        long minPartSize = AesCTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize > 0)
            minPartSize = chunkSize;
        else if (integrity)
            minPartSize = Integrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = (int) Math.ceil(data.length / (float) threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = (int) (data.length / partSize);
			if (runningThreads > this.threads)
				runningThreads = this.threads;
        }

        submitEncryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, format,
                integrity, chunkSize);
    }

    /**
     * Submit encryption parallel jobs.
     *
     * @param runningThreads The number of threads to submit.
     * @param partSize       The data length of each part that belongs to each thread.
     * @param data           The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *                       thread will read each own part.
     * @param outData        The buffer of data containing the encrypted data.
     * @param key            The AES key.
     * @param hashKey        The hash key for integrity.
     * @param nonce          The nonce for the data.
     * @param format         The format to use, see {@link EncryptionFormat}
     * @param integrity      True to apply the data integrity.
     * @param chunkSize      The chunk size.
     */
    private void submitEncryptJobs(int runningThreads, long partSize, byte[] data, byte[] outData,
                                   byte[] key, byte[] hashKey, byte[] nonce, EncryptionFormat format,
                                   boolean integrity, int chunkSize) {

        final CountDownLatch done = new CountDownLatch(runningThreads);
        AtomicReference<Exception> ex = new AtomicReference<>();
        for (int i = 0; i < runningThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    long start = partSize * index;
                    long length;
                    if (index == runningThreads - 1)
                        length = data.length - start;
                    else
                        length = partSize;
                    MemoryStream ins = new MemoryStream(data);
                    encryptData(ins, start, length, outData, key, nonce, format, integrity, hashKey, chunkSize);
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
     * Encrypt the data stream.
     *
     * @param inputStream The Stream to be encrypted.
     * @param start       The start position of the stream to be encrypted.
     * @param count       The number of bytes to be encrypted.
     * @param outData     The buffer with the encrypted data.
     * @param key         The AES key to be used.
     * @param nonce       The nonce to be used.
     * @param format      The format to use, see {@link EncryptionFormat}
     * @param integrity   True to apply integrity.
     * @param hashKey     The key to be used for integrity application.
     * @param chunkSize   The chunk size.
     * @throws IOException        Thrown if there is an error with the stream.
     * @throws SecurityException  Thrown if there is a security exception with the stream.
     * @throws IntegrityException Thrown if integrity cannot be applied.
     */
    private void encryptData(MemoryStream inputStream, long start, long count, byte[] outData,
                             byte[] key, byte[] nonce, EncryptionFormat format,
                             boolean integrity, byte[] hashKey, int chunkSize)
            throws IOException {
        MemoryStream outputStream = new MemoryStream(outData);
        AesStream stream = null;
        try {
            inputStream.setPosition(start);
            stream = new AesStream(key, nonce, EncryptionMode.Encrypt, outputStream,
                    format, integrity, hashKey, chunkSize);
            stream.setAllowRangeWrite(true);
            stream.setPosition(start);
            long totalChunkBytesRead = 0;
            int buffSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
            buffSize = buffSize / stream.getAlignSize() * stream.getAlignSize();
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = inputStream.read(buff, 0, Math.min(buff.length, (int) (count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count) {
                stream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            stream.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            outputStream.close();
            if (stream != null)
                stream.close();
            if (inputStream != null)
                inputStream.close();
        }
    }

    /**
     * Close the decryptor and release associated resources
     */
    public void close() {
        if (executor != null)
            executor.shutdownNow();
    }
}
