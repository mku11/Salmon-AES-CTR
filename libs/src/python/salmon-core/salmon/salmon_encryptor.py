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
import concurrent
import math
from concurrent.futures import ProcessPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory

from convert.bit_converter import BitConverter
from iostream.memory_stream import MemoryStream
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.iostream.encryption_mode import EncryptionMode
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_security_exception import SalmonSecurityException


def encrypt_shm(index: int, part_size: int, running_threads: int,
                data: bytearray, shm_out_name: str, key: bytearray, nonce: bytearray, header_data: bytearray,
                integrity: bool, hash_key: bytearray, chunk_size: int, buffer_size: int):
    try:
        start: int = part_size * index
        length: int
        if index == running_threads - 1:
            length = len(data) - start
        else:
            length = part_size

        shm_out = SharedMemory(shm_out_name)
        shm_out_data = shm_out.buf

        ins: MemoryStream = MemoryStream(data)
        out_data: bytearray = bytearray(len(shm_out_data))
        encrypt_data(ins, start, length, out_data, key, nonce, header_data,
                     integrity, hash_key, chunk_size, buffer_size)
        for i in range(0, length):
            shm_out_data[start + i] = out_data[start + i]
    except Exception as ex:
        raise Exception from ex


def encrypt_data(input_stream: MemoryStream, start: int, count: int, out_data: bytearray,
                 key: bytearray, nonce: bytearray, header_data: bytearray,
                 integrity: bool, hash_key: bytearray, chunk_size: int, buffer_size: int):
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
     * @param hash_key     The key to be used for integrity application.
     * @param chunkSize   The chunk size.
     * @throws IOError              Thrown if there is an error with the stream.
     * @throws SalmonSecurityException  Thrown if there is a security exception with the stream.
     * @throws SalmonIntegrityException Thrown if integrity cannot be applied.
    """

    output_stream: MemoryStream = MemoryStream(out_data)
    stream: SalmonStream | None = None
    try:
        input_stream.set_position(start)
        stream = SalmonStream(key, nonce, EncryptionMode.Encrypt, output_stream, header_data,
                              integrity, chunk_size, hash_key)
        stream.set_allow_range_write(True)
        stream.set_position(start)
        total_chunk_bytes_read: int = 0
        # align to the chunk size if available
        buff_size: int = max(buffer_size, stream.get_chunk_size())
        buff: bytearray = bytearray(buff_size)
        bytes_read: int
        while (bytes_read := input_stream.read(buff, 0, min(len(buff), count - total_chunk_bytes_read))) > 0 \
                and total_chunk_bytes_read < count:
            stream.write(buff, 0, bytes_read)
            total_chunk_bytes_read += bytes_read
        stream.flush()
    except IOError as ex:
        print(ex)
        raise ex
    finally:
        output_stream.close()
        if stream is not None:
            stream.close()
        if input_stream is not None:
            input_stream.close()


class SalmonEncryptor:
    """
     * Encrypts byte arrays.
    """

    def __init__(self, threads: int = None, buffer_size: int = None):
        """
         * Instantiate an encryptor with parallel tasks and buffer size.
         *
         * @param threads    The number of threads to use.
         * @param buffer_size The buffer size to use. It is recommended for performance  to use
         *                   a multiple of the chunk size if you enabled integrity
         *                   otherwise a multiple of the AES block size (16 bytes).
        """

        self.__threads: int = 0
        """
         * The number of parallel threads to use.
        """

        self.__executor: ProcessPoolExecutor | None = None
        """
         * Executor for parallel tasks.
        """

        __buffer_size: int = 0
        """
         * The buffer size to use.
        """

        if threads is None or threads <= 0:
            self.__threads = 1
        else:
            self.__threads = threads
            self.__executor = ProcessPoolExecutor(threads)

        if buffer_size is None:
            self.__buffer_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE
        else:
            self.__buffer_size = buffer_size

    def encrypt(self, data: bytearray, key: bytearray, nonce: bytearray,
                store_header_data: bool,
                integrity: bool = False, hash_key: bytearray | None = None, chunk_size: int | None = None) -> bytearray:
        """
         * Encrypts a byte array using the provided key and nonce.
         *
         * @param data            The byte array to be encrypted.
         * @param key             The AES key to be used.
         * @param nonce           The nonce to be used.
         * @param storeHeaderData True if you want to store a header data with the nonce. False if you store
         *                        the nonce external. Note that you will need to provide the nonce when decrypting.
         * @param integrity       True if you want to calculate and store hash signatures for each chunkSize.
         * @param hash_key         Hash key to be used for all chunks.
         * @param chunkSize       The chunk size.
         * @return The byte array with the encrypted data.
         * @throws SalmonSecurityException
         * @throws IOError
         * @throws SalmonIntegrityException
        """

        if key is None:
            raise SalmonSecurityException("Key is missing")
        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        if integrity:
            chunk_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE if chunk_size is None else chunk_size
        else:
            chunk_size = 0

        output_stream: MemoryStream = MemoryStream()
        header_data: bytearray | None = None
        if store_header_data:
            magic_bytes: bytearray = SalmonGenerator.get_magic_bytes()
            output_stream.write(magic_bytes, 0, len(magic_bytes))
            version: int = SalmonGenerator.get_version()
            version_bytes: bytearray = bytearray([version])
            output_stream.write(version_bytes, 0, len(version_bytes))
            chunk_size_bytes: bytearray = BitConverter.to_bytes(chunk_size, SalmonGenerator.CHUNK_SIZE_LENGTH)
            output_stream.write(chunk_size_bytes, 0, len(chunk_size_bytes))
            output_stream.write(nonce, 0, len(nonce))
            output_stream.flush()
            header_data = output_stream.to_array()

        real_size: int = SalmonStream.get_actual_size(data, key, nonce,
                                                      EncryptionMode.Encrypt,
                                                      header_data, integrity, chunk_size, hash_key)
        out_data: bytearray = bytearray(real_size)
        output_stream.set_position(0)
        output_stream.read(out_data, 0, output_stream.length())
        output_stream.close()

        if self.__threads == 1:
            input_stream: MemoryStream = MemoryStream(data)
            encrypt_data(input_stream, 0, len(data), out_data,
                         key, nonce, header_data, integrity, hash_key, chunk_size, self.__buffer_size)
        else:
            self.__encrypt_data_parallel(data, out_data,
                                         key, hash_key, nonce, header_data,
                                         chunk_size, integrity)
        return out_data

    def __encrypt_data_parallel(self, data: bytearray, out_data: bytearray,
                                key: bytearray, hash_key: bytearray, nonce: bytearray, header_data: bytearray,
                                chunk_size: int, integrity: bool):
        """
         * Encrypt stream using parallel threads.
         *
         * @param data       The input data to be encrypted
         * @param outData    The output buffer with the encrypted data.
         * @param key        The AES key.
         * @param hash_key    The hash key.
         * @param nonce      The nonce to be used for encryption.
         * @param headerData The header data.
         * @param chunkSize  The chunk size.
         * @param integrity  True to apply integrity.
        """

        running_threads: int = 1
        part_size: int = len(data)

        # if we want to check integrity we align to the chunk size otherwise to the AES Block
        min_part_size: int = SalmonGenerator.BLOCK_SIZE
        if integrity and chunk_size is not None:
            min_part_size = chunk_size
        elif integrity:
            min_part_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE

        if part_size > min_part_size:
            part_size = int(math.ceil(part_size / float(self.__threads)))
            # if we want to check integrity we align to the chunk size instead of the AES Block
            rem = part_size % min_part_size
            if rem != 0:
                part_size += min_part_size - rem

            running_threads = len(data) // part_size
        else:
            running_threads = 1

        self.__submit_encrypt_jobs(running_threads, part_size,
                                   data, out_data,
                                   key, hash_key, nonce, header_data,
                                   integrity, chunk_size)

    def __submit_encrypt_jobs(self, running_threads: int, part_size: int, data: bytearray, out_data: bytearray,
                              key: bytearray, hash_key: bytearray, nonce: bytearray,
                              header_data: bytearray, integrity: bool, chunk_size: int):
        """
         * Submit encryption parallel jobs.
         *
         * @param runningThreads The number of threads to submit.
         * @param partSize       The data length of each part that belongs to each thread.
         * @param data           The buffer of data you want to decrypt. This is a shared byte array across all threads
                                 where each thread will read each own part.
         * @param outData        The buffer of data containing the encrypted data.
         * @param key            The AES key.
         * @param hash_key        The hash key for integrity.
         * @param nonce          The nonce for the data.
         * @param headerData     The header data common to all parts.
         * @param integrity      True to apply the data integrity.
         * @param chunkSize      The chunk size.
        """

        shm_out = shared_memory.SharedMemory(create=True, size=len(out_data))
        shm_out_name = shm_out.name

        ex: Exception | None = None
        fs = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(encrypt_shm, i, part_size, running_threads,
                                             data, shm_out_name, key, nonce, header_data,
                                             integrity, hash_key, chunk_size, self.__buffer_size))
        concurrent.futures.wait(fs)

        try:
            # catch any errors within the children processes
            for f in fs:
                f.result()
        except Exception as ex1:
            ex = ex1

        out_data[:] = shm_out.buf[:]
        if ex is not None:
            try:
                raise ex
            except Exception as e:
                raise RuntimeError() from e

    def __del__(self):
        pass
        if self.__executor is not None:
            self.__executor.shutdown(False)
