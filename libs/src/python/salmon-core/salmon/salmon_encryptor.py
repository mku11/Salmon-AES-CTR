#!/usr/bin/env python3
"""
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
"""
import math
from multiprocessing.pool import ThreadPool

from convert.bit_converter import BitConverter
from iostream.memory_stream import MemoryStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer


class SalmonEncryptor:
    """
     * Encrypts byte arrays.
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

        __bufferSize: int = 0
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

    def encrypt(self, data: bytearray, key: bytearray, nonce: bytearray,
                storeHeaderData: bool,
                integrity: bool, hashKey: bytearray, chunkSize: int) -> bytearray:
        """
         * Encrypts a byte array using the provided key and nonce.
         *
         * @param data            The byte array to be encrypted.
         * @param key             The AES key to be used.
         * @param nonce           The nonce to be used.
         * @param storeHeaderData True if you want to store a header data with the nonce. False if you store
         *                        the nonce external. Note that you will need to provide the nonce when decrypting.
         * @param integrity       True if you want to calculate and store hash signatures for each chunkSize.
         * @param hashKey         Hash key to be used for all chunks.
         * @param chunkSize       The chunk size.
         * @return The byte array with the encrypted data.
         * @throws SalmonSecurityException
         * @throws IOException
         * @throws SalmonIntegrityException
        """

        if key is not None:
            raise SalmonSecurityException("Key is missing")
        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        if integrity:
            chunkSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE if chunkSize is None else chunkSize
        else:
            chunkSize = 0

        outputStream: MemoryStream = MemoryStream()
        headerData: bytearray = None
        if storeHeaderData:
            magicBytes: bytearray = SalmonGenerator.getMagicBytes()
            outputStream.write(magicBytes, 0, len(magicBytes))
            version: int = SalmonGenerator.getVersion()
            versionBytes: bytearray = bytearray([version])
            outputStream.write(versionBytes, 0, len(versionBytes))
            chunkSizeBytes: bytearray = BitConverter.to_bytes(chunkSize, SalmonGenerator.CHUNK_SIZE_LENGTH)
            outputStream.write(chunkSizeBytes, 0, len(chunkSizeBytes))
            outputStream.write(nonce, 0, len(nonce))
            outputStream.flush()
            headerData = outputStream.toArray()

        realSize: int = SalmonAES256CTRTransformer.getActualSize(data, key, nonce, SalmonStream.EncryptionMode.Encrypt,
                                                                 headerData, integrity, chunkSize, hashKey)
        outData: bytearray = bytearray(realSize)
        outputStream.set_position(0)
        outputStream.read(outData, 0, outputStream.length())
        outputStream.close()

        if self.__threads == 1:
            inputStream: MemoryStream = MemoryStream(data)
            self.encryptData(inputStream, 0, len(data), outData,
                             key, nonce, headerData, integrity, hashKey, chunkSize)
        else:
            self.encryptDataParallel(data, outData,
                                     key, hashKey, nonce, headerData,
                                     chunkSize, integrity)
        return outData

    def __encryptDataParallel(self, data: bytearray, outData: bytearray,
                              key: bytearray, hashKey: bytearray, nonce: bytearray, headerData: bytearray,
                              chunkSize: int, integrity: bool):
        """
         * Encrypt stream using parallel threads.
         *
         * @param data       The input data to be encrypted
         * @param outData    The output buffer with the encrypted data.
         * @param key        The AES key.
         * @param hashKey    The hash key.
         * @param nonce      The nonce to be used for encryption.
         * @param headerData The header data.
         * @param chunkSize  The chunk size.
         * @param integrity  True to apply integrity.
        """

        runningThreads: int = 1
        partSize: int = len(data)

        # if we want to check integrity we align to the chunk size otherwise to the AES Block
        minPartSize: int = SalmonAES256CTRTransformer.BLOCK_SIZE
        if integrity and chunkSize is not None:
            minPartSize = chunkSize
        elif integrity:
            minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE

        if partSize > minPartSize:
            partSize = int(math.ceil(partSize / float(self.__threads)))
            # if we want to check integrity we align to the chunk size instead of the AES Block
            rem = partSize % minPartSize
            if rem != 0:
                partSize += minPartSize - rem

            runningThreads = len(data) // partSize
        else:
            runningThreads = 1

        self.__submitEncryptJobs(runningThreads, partSize,
                                 data, outData,
                                 key, hashKey, nonce, headerData,
                                 integrity, chunkSize)

    def __submitEncryptJobs(self, runningThreads: int, partSize: int, data: bytearray, outData: bytearray,
                            key: bytearray, hashKey: bytearray, nonce: bytearray,
                            headerData: bytearray, integrity: bool, chunkSize: int):
        """
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
         * @param headerData     The header data common to all parts.
         * @param integrity      True to apply the data integrity.
         * @param chunkSize      The chunk size.
        """
        # TODO:

    #     final CountDownLatch done = new CountDownLatch(runningThreads)
    #
    #     AtomicReference<Exception> ex = new AtomicReference<>()
    #     for (int i = 0 i < runningThreads i++) {
    #         final int index = i
    #         executor.submit(() -> {
    #             try {
    #                 long start = partSize * index
    #                 long length
    #                 if (index == runningThreads - 1)
    #                     length = data.length - start
    #                 else
    #                     length = partSize
    #                 MemoryStream ins = new MemoryStream(data)
    #                 encryptData(ins, start, length, outData, key, nonce, headerData, integrity, hashKey, chunkSize)
    #             } catch (Exception ex1) {
    #                 ex.set(ex1)
    #             }
    #             done.countDown()
    #         })
    #     }
    #     try {
    #         done.await()
    #     } catch (InterruptedException ignored) {
    #     }
    #
    #     if (ex.get() != null) {
    #         try {
    #             throw ex.get()
    #         } catch (Exception e) {
    #             throw new RuntimeException(e)
    #         }
    #     }
    # }

    def __encryptData(self, inputStream: MemoryStream, start: int, count: int, outData: bytearray,
                      key: bytearray, nonce: bytearray, headerData: bytearray,
                      integrity: bool, hashKey: bytearray, chunkSize: int):
        """
         * Encrypt the data stream.
         *
         * @param inputStream The Stream to be encrypted.
         * @param start       The start position of the stream to be encrypted.
         * @param count       The number of bytes to be encrypted.
         * @param outData     The buffer with the encrypted data.
         * @param key         The AES key to be used.
         * @param nonce       The nonce to be used.
         * @param headerData  The header data to be used.
         * @param integrity   True to apply integrity.
         * @param hashKey     The key to be used for integrity application.
         * @param chunkSize   The chunk size.
         * @throws IOException              Thrown if there is an error with the stream.
         * @throws SalmonSecurityException  Thrown if there is a security exception with the stream.
         * @throws SalmonIntegrityException Thrown if integrity cannot be applied.
        """

        outputStream: MemoryStream = MemoryStream(outData)
        stream: SalmonStream = None
        try:
            inputStream.set_position(start)
            stream = SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, outputStream, headerData,
                                  integrity, chunkSize, hashKey)
            stream.setAllowRangeWrite(True)
            stream.set_position(start)
            totalChunkBytesRead: int = 0
            # align to the chunk size if available
            buffSize: int = max(self.__bufferSize, stream.getChunkSize())
            buff: bytearray = bytearray(buffSize)
            bytesRead: int
            while (bytesRead := inputStream.read(buff, 0, min(len(buff),
                                                              count - totalChunkBytesRead))) > 0 and totalChunkBytesRead < count:
                stream.write(buff, 0, bytesRead)
                totalChunkBytesRead += bytesRead
            stream.flush()
        except IOError as ex:
            print(ex)
            raise ex
        finally:
            outputStream.close()
            if stream is not None:
                stream.close()
            if inputStream is not None:
                inputStream.close()

    def __finalize(self):
        self.__executor.close()
