#!/usr/bin/env python3
__license__ = """
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
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory
import sys
from typeguard import typechecked

from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.salmon.integrity.integrity import Integrity
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.security_exception import SecurityException


@typechecked
def encrypt_shm(index: int, part_size: int, running_threads: int,
                data: bytearray, shm_out_name: str, shm_length: int, shm_cancel_name: str, key: bytearray,
                nonce: bytearray,
                enc_format: EncryptionFormat,
                integrity: bool, hash_key: bytearray | None, chunk_size: int, buffer_size: int):
    """
    Do not use directly use encrypt() instead.
    :param index: The worker index
    :param part_size: The part size
    :param running_threads: The threads
    :param data: The data to encrypt
    :param shm_out_name: The shared memory name
    :param shm_length: The shared memory length
    :param shm_cancel_name: The shared memory for cancelation
    :param key: The encryption key
    :param nonce: The nonce
    :param enc_format: The {@link EncryptionFormat} Generic or Salmon.
    :param integrity: True to apply integrity when encrypting
    :param hash_key: The hash key for integrity
    :param chunk_size: The chunk size for integrity
    :param buffer_size: The buffer size
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
    (byte_start, byte_end) = encrypt_data(ins, start, length, out_data, key, nonce, enc_format,
                                          integrity, hash_key, chunk_size, buffer_size, shm_cancel_name)
    if index == 0:
        byte_start = 0
    shm_out_data[byte_start:byte_end] = out_data[byte_start:byte_end]


@typechecked
def encrypt_data(input_stream: MemoryStream, start: int, count: int, out_data: bytearray,
                 key: bytearray, nonce: bytearray, enc_format: EncryptionFormat,
                 integrity: bool, hash_key: bytearray | None, chunk_size: int, buffer_size: int,
                 shm_cancel_name: str | None = None) -> (
        int, int):
    """
    Encrypt the data stream. Do not use directly use encrypt() instead.

    :param input_stream: The Stream to be encrypted.
    :param start:       The start position of the stream to be encrypted.
    :param count:       The number of bytes to be encrypted.
    :param out_data:     The buffer with the encrypted data.
    :param key:         The AES key to be used.
    :param nonce:       The nonce to be used.
    :param enc_format: The {@link EncryptionFormat} Generic or Salmon.
    :param integrity:   True to apply integrity.
    :param hash_key:     The key to be used for integrity application.
    :param chunk_size:   The chunk size.
    :param buffer_size:   The chunk size.
    :param shm_cancel_name: The shared memory cancelation name.
    :raises IOError:             Thrown if there is an error with the stream.
    :raises SalmonSecurityException: Thrown if there is a security exception with the stream.
    :raises IntegrityException: Thrown if integrity cannot be applied.
    """
    shm_cancel_data: memoryview | None = None
    if shm_cancel_name is not None:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    output_stream: MemoryStream = MemoryStream(out_data)
    stream: AesStream | None = None
    start_pos: int
    try:
        input_stream.set_position(start)
        stream = AesStream(key, nonce, EncryptionMode.Encrypt, output_stream, enc_format,
                           integrity, hash_key, chunk_size)
        stream.set_allow_range_write(True)
        stream.set_position(start)
        start_pos = output_stream.get_position()
        total_chunk_bytes_read: int = 0
        # align to the chunk size if available
        buff_size: int = max(buffer_size, stream.get_chunk_size())
        # set the same buffer size for the internal stream
        stream.set_buffer_size(buff_size)
        buff: bytearray = bytearray(buff_size)
        bytes_read: int
        while (bytes_read := input_stream.read(buff, 0, min(len(buff), count - total_chunk_bytes_read))) > 0 \
                and total_chunk_bytes_read < count:
            if shm_cancel_data is not None and shm_cancel_data[0]:
                break
            stream.write(buff, 0, bytes_read)
            total_chunk_bytes_read += bytes_read
        stream.flush()
    except (IOError, SecurityException, IntegrityException) as ex:
        print(ex, file=sys.stderr)
        raise SecurityException("Could not encrypt data") from ex
    finally:
        output_stream.close()
        if stream is not None:
            stream.close()
        if input_stream is not None:
            input_stream.close()
    end_pos: int = output_stream.get_position()
    return start_pos, end_pos


@typechecked
class Encryptor:
    """
    Encrypts byte arrays.
    """

    def __init__(self, threads: int = 1, buffer_size: int = Integrity.DEFAULT_CHUNK_SIZE, multi_cpu: bool = False):
        """
        Instantiate an encryptor with parallel tasks and buffer size.
        
        :param threads:    The number of threads to use.
        :param buffer_size: The buffer size to use. It is recommended for performance  to use
                          a multiple of the chunk size if you enabled integrity
                          otherwise a multiple of the AES block size (16 bytes).
        :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
        """

        self.__threads: int = 0
        """
        The number of parallel threads to use.
        """

        self.__executor: ProcessPoolExecutor | None = None
        """
        Executor for parallel tasks.
        """

        __buffer_size: int = 0
        """
        The buffer size to use.
        """

        if threads <= 0:
            self.__threads = 1
        self.__threads = threads
        if threads > 1:
            self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(
                self.__threads)

        if buffer_size <= 0:
            self.__buffer_size = Integrity.DEFAULT_CHUNK_SIZE
        else:
            self.__buffer_size = buffer_size

    def encrypt(self, data: bytearray, key: bytearray, nonce: bytearray,
                enc_format: EncryptionFormat = EncryptionFormat.Salmon,
                integrity: bool = False, hash_key: bytearray | None = None,
                chunk_size: int = 0) -> bytearray:
        """
        Encrypts a byte array using the provided key and nonce.
        
        :param data:            The byte array to be encrypted.
        :param key:             The AES key to be used.
        :param nonce:           The nonce to be used.
        :param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        :param integrity:       True if you want to calculate and store hash signatures for each chunk size.
        :param hash_key:         Hash key to be used for all chunks.
        :param chunk_size:       The chunk size.
        :return: The byte array with the encrypted data.
        :raises IntegrityException: Thrown when security error
        :raises IOError: Thrown if there is an IO error.
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        """

        if key is None:
            raise SecurityException("Key is missing")
        if nonce is None:
            raise SecurityException("Nonce is missing")

        if integrity:
            chunk_size = Integrity.DEFAULT_CHUNK_SIZE if chunk_size <= 0 else chunk_size
        else:
            chunk_size = 0

        real_size: int = AesStream.get_output_size(EncryptionMode.Encrypt, len(data), enc_format, chunk_size)
        out_data: bytearray = bytearray(real_size)

        if self.__threads == 1:
            input_stream: MemoryStream = MemoryStream(data)
            encrypt_data(input_stream, 0, len(data), out_data,
                         key, nonce, enc_format, integrity, hash_key, chunk_size, self.__buffer_size)
        else:
            self.__encrypt_data_parallel(data, out_data,
                                         key, hash_key, nonce, enc_format,
                                         chunk_size, integrity)
        return out_data

    def __encrypt_data_parallel(self, data: bytearray, out_data: bytearray,
                                key: bytearray, hash_key: bytearray | None, nonce: bytearray,
                                enc_format: EncryptionFormat,
                                chunk_size: int, integrity: bool):
        """
        Encrypt stream using parallel threads.
        
        :param data:       The input data to be encrypted
        :param out_data:    The output buffer with the encrypted data.
        :param key:        The AES key.
        :param hash_key:    The hash key.
        :param nonce:      The nonce to be used for encryption.
        :param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        :param chunk_size:  The chunk size.
        :param integrity:  True to apply integrity.
        """

        running_threads: int = 1
        part_size: int = len(data)

        # if we want to check integrity we align to the chunk size otherwise to the AES Block
        min_part_size: int = Generator.BLOCK_SIZE
        if integrity and chunk_size > 0:
            min_part_size = chunk_size
        elif integrity:
            min_part_size = Integrity.DEFAULT_CHUNK_SIZE

        if part_size > min_part_size:
            part_size = int(math.ceil(len(data) / float(self.__threads)))
            if part_size > min_part_size:
                part_size -= part_size % min_part_size
            else:
                part_size = min_part_size
            running_threads = len(data) // part_size
            if running_threads > self.__threads:
                running_threads = self.__threads

        self.__submit_encrypt_jobs(running_threads, part_size,
                                   data, out_data,
                                   key, hash_key, nonce, enc_format,
                                   integrity, chunk_size)

    def __submit_encrypt_jobs(self, running_threads: int, part_size: int, data: bytearray, out_data: bytearray,
                              key: bytearray, hash_key: bytearray | None, nonce: bytearray,
                              enc_format: EncryptionFormat, integrity: bool, chunk_size: int):
        """
        Submit encryption parallel jobs.
        
        :param running_threads: The number of threads to submit.
        :param part_size:       The data length of each part that belongs to each thread.
        :param data:           The buffer of data you want to decrypt. This is a shared byte array across all threads
                                 where each thread will read each own part.
        :param out_data:        The buffer of data containing the encrypted data.
        :param key:            The AES key.
        :param hash_key:        The hash key for integrity.
        :param nonce:          The nonce for the data.
        :param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        :param integrity:      True to apply the data integrity.
        :param chunk_size:      The chunk size.
        """

        shm_out = shared_memory.SharedMemory(create=True, size=len(out_data))
        shm_out_name = shm_out.name

        shm_cancel = shared_memory.SharedMemory(create=True, size=1)
        shm_cancel_name = shm_cancel.name

        ex: Exception | None = None
        fs = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(encrypt_shm, i, part_size, running_threads,
                                             data, shm_out_name, len(shm_out.buf), shm_cancel_name, key, nonce,
                                             enc_format,
                                             integrity, hash_key, chunk_size, self.__buffer_size))
        for f in concurrent.futures.as_completed(fs):
            try:
                # catch any errors within the children processes
                f.result()
            except Exception as ex1:
                print(ex1, file=sys.stderr)
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

    def close(self):
        """
        Close the encryptor and associated resources
        """
        if self.__executor is not None:
            self.__executor.shutdown(False)
