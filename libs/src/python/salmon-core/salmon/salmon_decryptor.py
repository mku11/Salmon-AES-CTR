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

    def __init__(self, threads: int = None, buffer_size: int = None):
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

        if buffer_size is None:
            self.__bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE
        else:
            self.__bufferSize = buffer_size

    def decrypt(self, data: bytearray, key: bytearray, nonce: bytearray,
                has_header_data: bool,
                integrity: bool = False, hash_key: bytearray = None, chunk_size: int = None) -> bytearray:
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
        if not has_header_data and nonce is None:
            raise SalmonSecurityException("Need to specify a nonce if the file doesn't have a header")

        if integrity:
            chunk_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE if chunk_size is None else chunk_size

        input_stream: MemoryStream = MemoryStream(data)
        header: SalmonHeader
        header_data: bytearray = None
        if has_header_data:
            header = SalmonHeader.parse_header_data(input_stream)
            if header.get_chunk_size() > 0:
                integrity = True
            chunk_size = header.get_chunk_size()
            nonce = header.get_nonce()
            header_data = header.get_header_data()

        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        real_size: int = SalmonAES256CTRTransformer.get_actual_size(data, key, nonce,
                                                                    SalmonStream.EncryptionMode.Decrypt,
                                                                    header_data, integrity, chunk_size, hash_key)
        out_data: bytearray = bytearray(real_size)

        if self.__threads == 1:
            self.__decrypt_data(input_stream, 0, input_stream.length(), out_data,
                                key, nonce, header_data, integrity, hash_key, chunk_size)
        else:
            self.__decrypt_data_parallel(data, out_data,
                                         key, hash_key, nonce, header_data,
                                         chunk_size, integrity)
        return out_data

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

    def __decrypt_data_parallel(self, data: bytearray, out_data: bytearray,
                                key: bytearray, hash_key: bytearray, nonce: bytearray, header_data: bytearray,
                                chunk_size: int, integrity: bool):
        running_threads: int = 1
        part_size: int = len(data)

        # if we want to check integrity we align to the chunk size otherwise to the AES Block
        min_part_size: int = SalmonAES256CTRTransformer.BLOCK_SIZE
        if integrity and chunk_size is not None:
            min_part_size = chunk_size
        elif integrity:
            min_part_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE

        if part_size > min_part_size:
            part_size = math.ceil(part_size / float(self.__threads))
            # if we want to check integrity we align to the chunk size instead of the AES Block
            rem: int = part_size % min_part_size
            if rem != 0:
                part_size += min_part_size - rem
            running_threads = len(data) // part_size
        else:
            running_threads = 1

        self.__submit_decrypt_jobs(running_threads, part_size,
                                   data, out_data,
                                   key, hash_key, nonce, header_data,
                                   integrity, chunk_size)

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

    def __submit_decrypt_jobs(self, running_threads: int, part_size: int,
                              data: bytearray, out_data: bytearray,
                              key: bytearray, hash_key: bytearray, nonce: bytearray, header_data: bytearray,
                              integrity: bool, chunk_size: int):
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

    def __decrypt_data(self, input_stream: RandomAccessStream, start: int, count: int, out_data: bytearray,
                       key: bytearray, nonce: bytearray,
                       header_data: bytearray, integrity: bool, hash_key: bytearray, chunk_size: int):
        stream: SalmonStream = None
        output_stream: MemoryStream = None
        try:
            output_stream = MemoryStream(out_data)
            output_stream.set_position(start)
            stream = SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, input_stream,
                                  header_data, integrity, chunk_size, hash_key)
            stream.set_position(start)
            total_chunk_bytes_read: int = 0
            # align to the chunk size if available
            buff_size: int = max(self.__bufferSize, stream.get_chunk_size())
            buff: bytearray = bytearray(buff_size)
            bytesRead: int
            while (bytesRead := stream.read(buff, 0, min(len(buff), (
                    count - total_chunk_bytes_read)))) > 0 and total_chunk_bytes_read < count:
                output_stream.write(buff, 0, bytesRead)
                total_chunk_bytes_read += bytesRead
            output_stream.flush()
        except (IOError, SalmonSecurityException, SalmonIntegrityException) as ex:
            print(ex)
            raise SalmonSecurityException("Could not decrypt data") from ex
        finally:
            if input_stream is not None:
                input_stream.close()
            if stream is not None:
                stream.close()
            if output_stream is not None:
                output_stream.close()

    def __finalize(self):
        self.__executor.close()
