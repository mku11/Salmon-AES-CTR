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
import concurrent
import math
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory

from salmon_core.iostream.memory_stream import MemoryStream
from salmon_core.iostream.random_access_stream import RandomAccessStream
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon_core.salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon_core.salmon.iostream.encryption_mode import EncryptionMode
from salmon_core.salmon.iostream.salmon_stream import SalmonStream
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.salmon_header import SalmonHeader
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException

from typeguard import typechecked


@typechecked
def decrypt_shm(index: int, part_size: int, running_threads: int,
                data: bytearray, shm_out_name: str, shm_length: int, shm_cancel_name: str, key: bytearray,
                nonce: bytearray,
                header_data: bytearray | None,
                integrity: bool, hash_key: bytearray | None, chunk_size: int | None, buffer_size: int):
    """
    Do not use directly use decrypt() instead.
    :param index:
    :param part_size:
    :param running_threads:
    :param data:
    :param shm_out_name:
    :param shm_length:
    :param shm_cancel_name:
    :param key:
    :param nonce:
    :param header_data:
    :param integrity:
    :param hash_key:
    :param chunk_size:
    :param buffer_size:
    :return:
    """
    start: int = part_size * index
    length: int
    if index == running_threads - 1:
        length = len(data) - start
    else:
        length = part_size
    shm_out = SharedMemory(shm_out_name)
    shm_out_data = shm_out.buf
    ins: MemoryStream = MemoryStream(data)
    out_data: bytearray = bytearray(shm_length)
    (byte_start, byte_end) = decrypt_data(ins, start, length, out_data, key, nonce, header_data,
                                          integrity, hash_key, chunk_size, buffer_size, shm_cancel_name)
    shm_out_data[byte_start:byte_end] = out_data[byte_start:byte_end]


@typechecked
def decrypt_data(input_stream: RandomAccessStream, start: int, count: int, out_data: bytearray,
                 key: bytearray, nonce: bytearray,
                 header_data: bytearray | None, integrity: bool, hash_key: bytearray | None,
                 chunk_size: int | None, buffer_size: int, shm_cancel_name: str | None = None) -> (int, int):
    """
     * Decrypt the data stream. Do not use directly use decrypt() instead.
     * @param inputStream The Stream to be decrypted.
     * @param start The start position of the stream to be decrypted.
     * @param count The number of bytes to be decrypted.
     * @param outData The buffer with the decrypted data.
     * @param key The AES key to be used.
     * @param nonce The nonce to be used.
     * @param headerData The header data to be used.
     * @param integrity True to verify integrity.
     * @param hash_key The hash key to be used for integrity verification.
     * @param chunkSize The chunk size.
     * @throws IOError  Thrown if there is an error with the stream.
     * @throws SalmonSecurityException Thrown if there is a security exception with the stream.
     * @throws SalmonIntegrityException Thrown if the stream is corrupt or tampered with.
    """
    shm_cancel_data: memoryview | None = None
    if shm_cancel_name is not None:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    stream: SalmonStream | None = None
    output_stream: MemoryStream | None = None
    start_pos: int
    try:
        output_stream = MemoryStream(out_data)
        output_stream.set_position(start)
        stream = SalmonStream(key, nonce, EncryptionMode.Decrypt, input_stream,
                              header_data, integrity, chunk_size, hash_key)
        stream.set_position(start)
        start_pos = output_stream.get_position()
        total_chunk_bytes_read: int = 0
        # align to the chunk size if available
        buff_size: int = max(buffer_size, stream.get_chunk_size())
        buff: bytearray = bytearray(buff_size)
        bytes_read: int
        while (bytes_read := stream.read(buff, 0, min(len(buff), (
                count - total_chunk_bytes_read)))) > 0 and total_chunk_bytes_read < count:
            if shm_cancel_data is not None and shm_cancel_data[0]:
                break
            output_stream.write(buff, 0, bytes_read)
            total_chunk_bytes_read += bytes_read
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
    end_pos: int = output_stream.get_position()
    return start_pos, end_pos


@typechecked
class SalmonDecryptor:
    """
     * Utility class that decrypts byte arrays.
    """

    def __init__(self, threads: int | None = None, buffer_size: int | None = None, multi_cpu: bool = False):
        """
         * Instantiate an encryptor with parallel tasks and buffer size.
         *
         * @param threads    The number of threads to use.
         * @param buffer_size The buffer size to use. It is recommended for performance  to use
         *                   a multiple of the chunk size if you enabled integrity
         *                   otherwise a multiple of the AES block size (16 bytes).
         * :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
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
            self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(
                self.__threads)

        if buffer_size is None:
            self.__buffer_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE
        else:
            self.__buffer_size = buffer_size

    def decrypt(self, data: bytearray, key: bytearray, nonce: bytearray | None,
                has_header_data: bool,
                integrity: bool = False, hash_key: bytearray | None = None, chunk_size: int | None = None) -> bytearray:
        """
         * Decrypt a byte array using AES256 based on the provided key and nonce.
         * @param data The input data to be decrypted.
         * @param key The AES key to use for decryption.
         * @param nonce The nonce to use for decryption.
         * @param hasHeaderData The header data.
         * @param integrity Verify hash integrity in the data.
         * @param hash_key The hash key to be used for integrity.
         * @param chunkSize The chunk size.
         * @return The byte array with the decrypted data.
         * @throws IOError Thrown if there is a problem with decoding the array.
         * @throws SalmonSecurityException Thrown if the key and nonce are not provided.
         * @throws IOError
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
        header_data: bytearray | None = None
        if has_header_data:
            header = SalmonHeader.parse_header_data(input_stream)
            if header.get_chunk_size() > 0:
                integrity = True
            chunk_size = header.get_chunk_size()
            nonce = header.get_nonce()
            header_data = header.get_header_data()

        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        real_size: int = SalmonStream.get_actual_size(data, key, nonce,
                                                      EncryptionMode.Decrypt,
                                                      header_data, integrity, chunk_size, hash_key)
        out_data: bytearray = bytearray(real_size)

        if self.__threads == 1:
            decrypt_data(input_stream, 0, input_stream.length(), out_data,
                         key, nonce, header_data, integrity, hash_key, chunk_size, self.__buffer_size)
        else:
            self.__decrypt_data_parallel(data, out_data,
                                         key, hash_key, nonce, header_data,
                                         chunk_size, integrity)
        return out_data

    def __decrypt_data_parallel(self, data: bytearray, out_data: bytearray,
                                key: bytearray, hash_key: bytearray | None, nonce: bytearray,
                                header_data: bytearray | None,
                                chunk_size: int | None, integrity: bool):
        """
         * Decrypt stream using parallel threads.
         * @param data The input data to be decrypted
         * @param outData The output buffer with the decrypted data.
         * @param key The AES key.
         * @param hash_key The hash key.
         * @param nonce The nonce to be used for decryption.
         * @param headerData The header data.
         * @param chunkSize The chunk size.
         * @param integrity True to verify integrity.
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
            part_size = math.ceil(len(data) / float(self.__threads))
            if part_size > min_part_size:
				part_size -= part_size % min_part_size
			else
				part_size = min_part_size
            running_threads = int(len(data) // part_size)

        self.__submit_decrypt_jobs(running_threads, part_size,
                                   data, out_data,
                                   key, hash_key, nonce, header_data,
                                   integrity, chunk_size)

    def __submit_decrypt_jobs(self, running_threads: int, part_size: int,
                              data: bytearray, out_data: bytearray,
                              key: bytearray, hash_key: bytearray | None, nonce: bytearray,
                              header_data: bytearray | None,
                              integrity: bool, chunk_size: int | None):

        """
         * Submit decryption parallel jobs.
         * @param runningThreads The number of threads to submit.
         * @param partSize The data length of each part that belongs to each thread.
         * @param data The buffer of data you want to decrypt. This is a shared byte array across all threads where each
         *             thread will read each own part.
         * @param outData The buffer of data containing the decrypted data.
         * @param key The AES key.
         * @param hash_key The hash key for integrity validation.
         * @param nonce The nonce for the data.
         * @param headerData The header data common to all parts.
         * @param integrity True to verify the data integrity.
         * @param chunkSize The chunk size.
        """
        shm_out = shared_memory.SharedMemory(create=True, size=len(out_data))
        shm_out_name = shm_out.name

        shm_cancel = shared_memory.SharedMemory(create=True, size=1)
        shm_cancel_name = shm_cancel.name

        ex: Exception | None = None
        fs = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(decrypt_shm, i, part_size, running_threads,
                                             data, shm_out_name, len(shm_out.buf), shm_cancel_name, key, nonce,
                                             header_data,
                                             integrity, hash_key, chunk_size, self.__buffer_size))

        for f in concurrent.futures.as_completed(fs):
            try:
                # catch any errors within the children processes
                f.result()
            except Exception as ex1:
                print(ex1)
                ex = ex1
                # cancel all tasks
                shm_cancel.buf[0] = 1

        out_data[:] = shm_out.buf[:]

        shm_cancel.close()
        shm_cancel.unlink()

        shm_out.close()
        shm_out.unlink()

        if ex is not None:
            try:
                raise ex
            except Exception as e:
                raise RuntimeError() from e

    def __del__(self):
        pass
        if self.__executor is not None:
            self.__executor.shutdown(False)
