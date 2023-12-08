#!/usr/bin/env python3
'''
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
'''
import math
from multiprocessing.pool import ThreadPool

from iostream.memory_stream import MemoryStream
from iostream.random_access_stream import RandomAccessStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_header import SalmonHeader
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer


class SalmonDecryptor:
    """
     * Utility class that decrypts byte arrays.
    """

    def __init__(self, threads: int = None, bufferSize: int = None):
        """
         * Instantiate an encryptor with parallel tasks and buffer size.
         *
         * @param threads    The number of threads to use.
         * @param bufferSize The buffer size to use. It is recommended for performance  to use
         *                   a multiple of the chunk size if you enabled integrity
         *                   otherwise a multiple of the AES block size (16 bytes).
        """

        self.__threads: int = 0
        """
         * The number of parallel threads to use.
        """

        self.__executor: ThreadPool = None
        """
         * Executor for parallel tasks.
        """

        __bufferSize:int = 0
        """
         * The buffer size to use.
        """

        if threads is None:
            self.__threads = 1
        else:
            self.__threads = threads
            executor = ThreadPool(threads)

        if bufferSize is None:
            self.__bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE
        else:
            self.__bufferSize = bufferSize

    def decrypt(self, data: bytearray, key: bytearray, nonce: bytearray,
                                 hasHeaderData: bool,
                                 integrity: bool = False, hashKey: bytearray = None, chunkSize: int = None) -> bytearray:
        """
         * Decrypt a byte array using AES256 based on the provided key and nonce.
         * @param data The input data to be decrypted.
         * @param key The AES key to use for decryption.
         * @param nonce The nonce to use for decryption.
         * @param hasHeaderData The header data.
         * @param integrity Verify hash integrity in the data.
         * @param hashKey The hash key to be used for integrity.
         * @param chunkSize The chunk size.
         * @return The byte array with the decrypted data.
         * @throws IOException Thrown if there is a problem with decoding the array.
         * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
         * @throws IOException
         * @throws SalmonIntegrityException
        """
        if key is None:
            raise SalmonSecurityException("Key is missing")
        if not hasHeaderData and nonce is None:
            raise SalmonSecurityException("Need to specify a nonce if the file doesn't have a header")

        if integrity:
            chunkSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE  if chunkSize is None else chunkSize

        inputStream: MemoryStream = MemoryStream(data)
        header: SalmonHeader
        headerData: bytearray = None
        if hasHeaderData:
            header = SalmonHeader.parseHeaderData(inputStream)
            if header.getChunkSize() > 0:
                integrity = True
            chunkSize = header.getChunkSize()
            nonce = header.getNonce()
            headerData = header.getHeaderData()

        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        realSize: int = SalmonAES256CTRTransformer.getActualSize(data, key, nonce, SalmonStream.EncryptionMode.Decrypt,
                headerData, integrity, chunkSize, hashKey)
        outData: bytearray = bytearray(realSize)

        if self.__threads == 1:
            self.decryptData(inputStream, 0, inputStream.length(), outData,
                    key, nonce, headerData, integrity, hashKey, chunkSize)
        else:
            self.decryptDataParallel(data, outData,
                    key, hashKey, nonce, headerData,
                    chunkSize, integrity)
        return outData

    """
     * Decrypt stream using parallel threads.
     * @param data The input data to be decrypted
     * @param outData The output buffer with the decrypted data.
     * @param key The AES key.
     * @param hashKey The hash key.
     * @param nonce The nonce to be used for decryption.
     * @param headerData The header data.
     * @param chunkSize The chunk size.
     * @param integrity True to verify integrity.
    """
    def __decryptDataParallel(self, data: bytearray, outData: bytearray,
                                            key: bytearray, hashKey: bytearray, nonce: bytearray, headerData: bytearray,
                                            chunkSize: int, integrity: bool):
        runningThreads: int = 1
        partSize: int = len(data)

        # if we want to check integrity we align to the chunk size otherwise to the AES Block
        minPartSize: int = SalmonAES256CTRTransformer.BLOCK_SIZE
        if integrity and chunkSize is not None:
            minPartSize = chunkSize
        elif integrity:
            minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE

        if partSize > minPartSize:
            partSize = math.ceil(partSize / float(self.__threads))
            # if we want to check integrity we align to the chunk size instead of the AES Block
            rem: int = partSize % minPartSize
            if rem != 0:
                partSize += minPartSize - rem
            runningThreads = len(data) // partSize
        else:
            runningThreads = 1

        self.__submitDecryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, headerData,
                integrity, chunkSize)

    """
     * Submit decryption parallel jobs.
     * @param runningThreads The number of threads to submit.
     * @param partSize The data length of each part that belongs to each thread.
     * @param data The buffer of data you want to decrypt. This is a shared byte array across all threads where each
     *             thread will read each own part.
     * @param outData The buffer of data containing the decrypted data.
     * @param key The AES key.
     * @param hashKey The hash key for integrity validation.
     * @param nonce The nonce for the data.
     * @param headerData The header data common to all parts.
     * @param integrity True to verify the data integrity.
     * @param chunkSize The chunk size.
    """
    def __submitDecryptJobs(self, runningThreads: int, partSize: int,
                                          data: bytearray, outData: bytearray,
                                          key: bytearray, hashKey: bytearray, nonce: bytearray, headerData: bytearray,
                                          integrity: bool, chunkSize: int):
        pass
        # TODO:
        # done: CountDownLatch = CountDownLatch(runningThreads)
    #     AtomicReference<Exception> ex = new AtomicReference<>()
    #     for i in (0, runningThreads):
    #         int index = i
    #         executor.submit(() ->
    #         {
    #             try {
    #                 long start = partSize * index
    #                 long length
	# 				if(index == runningThreads - 1)
	# 					length = data.length-start
	# 				else
	# 					length = partSize
    #                 MemoryStream ins = new MemoryStream(data)
    #                 decryptData(ins, start, length, outData, key, nonce, headerData,
    #                         integrity, hashKey, chunkSize)
    #             } catch (Exception ex1) {
    #                 ex.set(ex1)
    #             }
    #             done.countDown()
    #         })
    #     }
    #
    #     try {
    #         done.await()
    #     } catch (InterruptedException ignored) {}
    #
    #     if (ex.get() != null) {
    #         try {
    #             throw ex.get()
    #         } catch (Exception e) {
    #             throw new RuntimeException(e)
    #         }
    #     }
    # }

    """
     * Decrypt the data stream.
     * @param inputStream The Stream to be decrypted.
     * @param start The start position of the stream to be decrypted.
     * @param count The number of bytes to be decrypted.
     * @param outData The buffer with the decrypted data.
     * @param key The AES key to be used.
     * @param nonce The nonce to be used.
     * @param headerData The header data to be used.
     * @param integrity True to verify integrity.
     * @param hashKey The hash key to be used for integrity verification.
     * @param chunkSize The chunk size.
     * @throws IOException  Thrown if there is an error with the stream.
     * @throws SalmonSecurityException Thrown if there is a security exception with the stream.
     * @throws SalmonIntegrityException Thrown if the stream is corrupt or tampered with.
    """
    def __decryptData(self, inputStream: RandomAccessStream, start: int, count: int, outData: bytearray,
                                    key: bytearray, nonce: bytearray,
                                    headerData: bytearray, integrity: bool, hashKey: bytearray, chunkSize: int):
        stream: SalmonStream = None
        outputStream: MemoryStream = None
        try:
            outputStream = MemoryStream(outData)
            outputStream.set_position(start)
            stream = SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, inputStream,
                    headerData, integrity, chunkSize, hashKey)
            stream.set_position(start)
            totalChunkBytesRead: int = 0
            # align to the chunksize if available
            buffSize: int = max(self.__bufferSize, stream.getChunkSize())
            buff: bytearray = bytearray(buffSize)
            bytesRead: int
            while (bytesRead := stream.read(buff, 0, min(len(buff), (count - totalChunkBytesRead)))) > 0 and totalChunkBytesRead < count:
                outputStream.write(buff, 0, bytesRead)
                totalChunkBytesRead += bytesRead
            outputStream.flush()
        except (IOError, SalmonSecurityException, SalmonIntegrityException) as ex:
            print(ex)
            raise SalmonSecurityException("Could not decrypt data") from ex
        finally:
            if inputStream is not None:
                inputStream.close()
            if stream is not None:
                stream.close()
            if outputStream is not None:
                outputStream.close()

    def __finalize(self):
        self.__executor.close()
