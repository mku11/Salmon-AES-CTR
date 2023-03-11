package com.mku11.salmon;
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

import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmon.streams.MemoryStream;
import com.mku11.salmon.streams.SalmonStream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class that encrypts and decrypts byte arrays
 */
public class SalmonEncryptor {

    private static int bufferSize = 32768;

    public static byte[] decrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean hasHeaderData) throws Exception {
        return decrypt(data, key, nonce, hasHeaderData, 1, false, null, null);
    }

    public static byte[] decrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean hasHeaderData, int threads) throws Exception {
        return decrypt(data, key, nonce, hasHeaderData, threads, false, null, null);
    }

    /// <summary>
    /// Decrypts a byte array,
    /// </summary>
    /// <param name="data">Data to be decrypted</param>
    /// <param name="key">The encryption key to be used</param>
    /// <param name="nonce">The nonce to be used</param>
    /// <param name="header">Optional header data to be added to the header (ie store Nonce). Header data are not encrypted!</param>
    /// <param name="threads">Parallel processing threads default is a single thread.</param>
    /// <returns></returns>
    public static byte[] decrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean hasHeaderData, int threads,
                                 boolean integrity, byte[] hmacKey, Integer chunkSize) throws Exception {
        if (key == null)
            throw new Exception("Need to specify a key");
        if (!hasHeaderData && nonce == null)
            throw new Exception("Need to specify a nonce if the file doesn't have a header");

        if (integrity)
            chunkSize = chunkSize == null ? SalmonStream.DEFAULT_CHUNK_SIZE : (int) chunkSize;

        MemoryStream inputStream = new MemoryStream(data);
        byte[] headerData;
        if (hasHeaderData) {
            byte[] magicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];
            inputStream.read(magicBytes, 0, magicBytes.length);
            byte[] versionBytes = new byte[SalmonGenerator.VERSION_LENGTH];
            inputStream.read(versionBytes, 0, versionBytes.length);
            byte[] chunkSizeHeader = new byte[SalmonGenerator.CHUNKSIZE_LENGTH];
            inputStream.read(chunkSizeHeader, 0, chunkSizeHeader.length);
            chunkSize = (int) BitConverter.toLong(chunkSizeHeader, 0, SalmonGenerator.CHUNKSIZE_LENGTH);
            if (chunkSize > 0)
                integrity = true;
            nonce = new byte[SalmonGenerator.NONCE_LENGTH];
            inputStream.read(nonce, 0, nonce.length);
            inputStream.position(0);
            headerData = new byte[magicBytes.length + versionBytes.length + chunkSizeHeader.length + nonce.length];
            inputStream.read(headerData, 0, headerData.length);
        } else {
            headerData = null;
        }

        int realSize = (int) getOutputSize(data, key, nonce, SalmonStream.EncryptionMode.Decrypt,
                headerData, integrity, chunkSize, hmacKey);
        byte[] outData = new byte[realSize];

        if (threads == 1) {
            decryptData(inputStream, 0, inputStream.length(), outData,
                    key, nonce, headerData, integrity, hmacKey, chunkSize);
            return outData;
        }

        int runningThreads = 1;
        long partSize = data.length;

        // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
        long minPartSize = SalmonStream.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = (long) chunkSize;
        else if (integrity)
            minPartSize = SalmonStream.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = (int) Math.ceil(partSize / (float) threads);
            // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
            long rem = partSize % minPartSize;
            if (rem != 0)
                partSize += minPartSize - rem;

            runningThreads = (int) (data.length / partSize);
            if (partSize % data.length != 0)
                runningThreads++;
        }

        final CountDownLatch done = new CountDownLatch(runningThreads);
        ExecutorService executor = Executors.newFixedThreadPool(runningThreads);
        AtomicReference<Exception> ex = new AtomicReference<>();
        for (int i = 0; i < runningThreads; i++) {
            int index = i;
            long finalPartSize = partSize;
            byte[] finalNonce = nonce;
            boolean finalIntegrity = integrity;
            Integer finalChunkSize = chunkSize;
            executor.submit(() ->
            {
                try {
                    long start = finalPartSize * index;
                    long length = Math.min(finalPartSize, data.length - start);
                    MemoryStream ins = new MemoryStream(data);
                    decryptData(ins, start, length, outData, key, finalNonce, headerData, finalIntegrity, hmacKey, finalChunkSize);
                } catch (Exception ex1) {
                    ex.set(ex1);
                }
                done.countDown();
            });
        }
        done.await();
        inputStream.close();
        if (ex.get() != null)
            throw ex.get();
        return outData;
    }

    public static long getOutputSize(byte[] data, byte[] key, byte[] nonce, SalmonStream.EncryptionMode mode,
                                     byte[] headerData, boolean integrity, Integer chunkSize, byte[] hmacKey) throws Exception {
        MemoryStream inputStream = new MemoryStream(data);
        SalmonStream s = new SalmonStream(key, nonce, mode, inputStream,
                headerData, integrity, chunkSize, hmacKey);
        long size = s.actualLength();
        s.close();
        return size;
    }

    private static void decryptData(AbsStream inputStream, long start, long count, byte[] outData,
                                    byte[] key, byte[] nonce,
                                    byte[] headerData, boolean integrity, byte[] hmacKey, Integer chunkSize) throws Exception {
        SalmonStream stream = null;
        MemoryStream outputStream = null;
        try {
            outputStream = new MemoryStream(outData);
            outputStream.position(start);
            stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, inputStream,
                    headerData, integrity, chunkSize, hmacKey);
            stream.position(start);
            long totalChunkBytesRead = 0;
            // need to be align to the chunksize if there is one
            int buffSize = Math.max(bufferSize, stream.getChunkSize());
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = stream.read(buff, 0, Math.min(buff.length, (int) (count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count) {
                outputStream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            outputStream.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (inputStream != null)
                inputStream.close();
            if (stream != null)
                stream.close();
            if (outputStream != null)
                outputStream.close();
        }
    }

    public static byte[] encrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean storeHeaderData) throws Exception {
        return encrypt(data, key, nonce, storeHeaderData, 1, false, null, null);

    }

    public static byte[] encrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean storeHeaderData, int threads) throws Exception {
        return encrypt(data, key, nonce, storeHeaderData, threads, false, null, null);

    }

    /// <summary>
    /// Encrypts a byte array
    /// </summary>
    /// <param name="data">Text to be encrypted</param>
    /// <param name="key">The encryption key to be used</param>
    /// <param name="nonce">The nonce to be used</param>
    /// <returns></returns>
    public static byte[] encrypt(byte[] data, byte[] key, byte[] nonce,
                                 boolean storeHeaderData, int threads,
                                 boolean integrity, byte[] hmacKey, Integer chunkSize) throws Exception {
        if (integrity)
            chunkSize = chunkSize == null ? SalmonStream.DEFAULT_CHUNK_SIZE : (int) chunkSize;
        else
            chunkSize = 0;

        MemoryStream outputStream = new MemoryStream();
        byte[] headerData;
        if (storeHeaderData) {
            byte[] magicBytes = SalmonGenerator.getMagicBytes();
            outputStream.write(magicBytes, 0, magicBytes.length);
            byte version = SalmonGenerator.getVersion();
            byte[] versionBytes = new byte[]{version};
            outputStream.write(versionBytes, 0, versionBytes.length);
            byte[] chunkSizeBytes = BitConverter.toBytes((int) chunkSize, SalmonGenerator.CHUNKSIZE_LENGTH);
            outputStream.write(chunkSizeBytes, 0, chunkSizeBytes.length);
            outputStream.write(nonce, 0, nonce.length);
            outputStream.flush();
            headerData = outputStream.toArray();
        } else {
            headerData = null;
        }

        int realSize = (int) getOutputSize(data, key, nonce, SalmonStream.EncryptionMode.Encrypt,
                headerData, integrity, chunkSize, hmacKey);
        byte[] outData = new byte[realSize];
        outputStream.position(0);
        outputStream.read(outData, 0, (int) outputStream.length());
        outputStream.close();

        if (threads == 1) {
            MemoryStream inputStream = new MemoryStream(data);
            encryptData(inputStream, 0, data.length, outData,
                    key, nonce, headerData, integrity, hmacKey, chunkSize);
            return outData;
        }

        int runningThreads = 1;
        long partSize = data.length;

        // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
        long minPartSize = SalmonStream.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = (long) chunkSize;
        else if (integrity)
            minPartSize = SalmonStream.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize) {
            partSize = (int) Math.ceil(partSize / (float) threads);
            // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
            long rem = partSize % minPartSize;
            if (rem != 0)
                partSize += minPartSize - rem;

            runningThreads = (int) (data.length / partSize);
            if (partSize % data.length != 0)
                runningThreads++;
        }

        final CountDownLatch done = new CountDownLatch(runningThreads);
        ExecutorService executor = Executors.newFixedThreadPool(runningThreads);
        AtomicReference<Exception> ex = new AtomicReference<>();
        for (int i = 0; i < runningThreads; i++) {
            int index = i;
            long finalPartSize = partSize;
            Integer finalChunkSize = chunkSize;
            executor.submit(() -> {
                try {
                    long start = finalPartSize * index;
                    long length = Math.min(finalPartSize, data.length - start);
                    MemoryStream ins = new MemoryStream(data);
                    encryptData(ins, start, length, outData, key, nonce, headerData, integrity, hmacKey, finalChunkSize);
                } catch (Exception ex1) {
                    ex.set(ex1);
                }
                done.countDown();
            });
        }
        done.await();
        if (ex.get() != null)
            throw ex.get();
        return outData;
    }

    private static void encryptData(MemoryStream inputStream, long start, long count, byte[] outData,
                                    byte[] key, byte[] nonce, byte[] headerData,
                                    boolean integrity, byte[] hmacKey, Integer chunkSize) throws Exception {
        MemoryStream outputStream = new MemoryStream(outData);
        SalmonStream stream = null;
        try {
            inputStream.position(start);
            stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, outputStream, headerData,
                    integrity, chunkSize, hmacKey);
            stream.setAllowRangeWrite(true);
            stream.position(start);
            long totalChunkBytesRead = 0;
            // need to be align to the chunksize if there is one
            int buffSize = Math.max(bufferSize, stream.getChunkSize());
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = inputStream.read(buff, 0, Math.min(buff.length, (int) (count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count) {
                stream.write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            stream.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            if (outputStream != null)
                outputStream.close();
            if (stream != null)
                stream.close();
            if (inputStream != null)
                inputStream.close();
        }
    }

    public static void setBufferSize(int bufferSize) {
        SalmonEncryptor.bufferSize = bufferSize;
    }
}
