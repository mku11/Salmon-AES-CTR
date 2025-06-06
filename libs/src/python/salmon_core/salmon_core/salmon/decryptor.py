#!/usr/bin/env python3
"""!@brief Utility class that decrypts byte arrays. 
"""

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

from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.integrity.integrity import Integrity
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.encryption_format import EncryptionFormat
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.header import Header
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.security_exception import SecurityException

from typeguard import typechecked


@typechecked
def _decrypt_shm(index: int, part_size: int, running_threads: int,
                 data: bytearray, shm_out_name: str, shm_length: int, shm_cancel_name: str, key: bytearray,
                 nonce: bytearray | None,
                 enc_format: EncryptionFormat,
                 integrity: bool, hash_key: bytearray | None, chunk_size: int, buffer_size: int):
    """!
    Do not use directly use Decryptor class instead.
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
    (byte_start, byte_end) = _decrypt_data(ins, start, length, out_data, key, nonce, enc_format,
                                           integrity, hash_key, chunk_size, buffer_size, shm_cancel_name)
    shm_out_data[byte_start:byte_end] = out_data[byte_start:byte_end]


@typechecked
def _decrypt_data(input_stream: RandomAccessStream, start: int, count: int, out_data: bytearray,
                  key: bytearray, nonce: bytearray | None,
                  enc_format: EncryptionFormat, integrity: bool, hash_key: bytearray | None,
                  chunk_size: int, buffer_size: int, shm_cancel_name: str | None = None) -> (int, int):
    """!
    Do not use directly use Decryptor class instead.
    """
    shm_cancel_data: memoryview | None = None
    if shm_cancel_name:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    stream: AesStream | None = None
    output_stream: MemoryStream | None = None
    start_pos: int
    try:
        output_stream = MemoryStream(out_data)
        output_stream.set_position(start)
        stream = AesStream(key, nonce, EncryptionMode.Decrypt, input_stream,
                           enc_format, integrity, hash_key, chunk_size)
        stream.set_position(start)
        start_pos = output_stream.get_position()
        total_chunk_bytes_read: int = 0
        buff_size = RandomAccessStream.DEFAULT_BUFFER_SIZE
        buff_size = buff_size // stream.get_align_size() * stream.get_align_size()
        buff: bytearray = bytearray(buff_size)
        bytes_read: int
        while (bytes_read := stream.read(buff, 0, min(len(buff), (
                count - total_chunk_bytes_read)))) > 0 and total_chunk_bytes_read < count:
            if shm_cancel_data and shm_cancel_data[0]:
                break
            output_stream.write(buff, 0, bytes_read)
            total_chunk_bytes_read += bytes_read
        output_stream.flush()
    except (IOError, SecurityException, IntegrityException) as ex:
        print(ex, file=sys.stderr)
        raise SecurityException("Could not decrypt data") from ex
    finally:
        if input_stream:
            input_stream.close()
        if stream:
            stream.close()
        if output_stream:
            output_stream.close()
    end_pos: int = output_stream.get_position()
    return start_pos, end_pos


@typechecked
class Decryptor:
    """!
    Utility class that decrypts byte arrays.
    """

    def __init__(self, threads: int = 1, buffer_size: int = Integrity.DEFAULT_CHUNK_SIZE, multi_cpu: bool = False):
        """!
        Instantiate an encryptor with parallel tasks and buffer size.
        
        @param threads:    The number of threads to use.
        @param buffer_size: The buffer size to use. It is recommended for performance  to use
                          a multiple of the chunk size if you enabled integrity
                          otherwise a multiple of the AES block size (16 bytes).
        :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
        """

        self.__buffer_size: int = 0
        self.__executor: ThreadPoolExecutor | ProcessPoolExecutor | None = None

        if threads <= 0:
            threads = 1
        self.__threads = threads
        if self.__threads > 1:
            self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(
                self.__threads)

        if buffer_size <= 0:
            self.__buffer_size = Integrity.DEFAULT_CHUNK_SIZE
        else:
            self.__buffer_size = buffer_size

    def decrypt(self, data: bytearray, key: bytearray, nonce: bytearray | None = None,
                enc_format: EncryptionFormat = EncryptionFormat.Salmon,
                integrity: bool = True, hash_key: bytearray | None = None, chunk_size: int = 0) -> bytearray:
        """!
        Decrypt a byte array using AES256 based on the provided key and nonce.
        @param data: The input data to be decrypted.
        @param key: The AES key to use for decryption.
        @param nonce: The nonce to use for decryption.
        @param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        @param integrity: Verify hash integrity in the data.
        @param hash_key: The hash key to be used for integrity.
        @param chunk_size: The chunk size.
        @returns The byte array with the decrypted data.
        @exception IOError: Thrown if there is a problem with decoding the array.
        @exception SalmonSecurityException: Thrown if the key and nonce are not provided.
        @exception IOError: Thrown if there is an IO error.
        @exception IntegrityException: Thrown when data are corrupt or tampered with.
        """
        if key is None:
            raise SecurityException("Key is missing")
        if enc_format == EncryptionFormat.Generic and nonce is None:
            raise SecurityException("Need to specify a nonce if the file doesn't have a header")

        input_stream: MemoryStream = MemoryStream(data)
        if enc_format == EncryptionFormat.Salmon:
            header: Header = Header.read_header_data(input_stream)
            if header:
                chunk_size = header.get_chunk_size()
        elif integrity:
            chunk_size = Integrity.DEFAULT_CHUNK_SIZE if chunk_size <= 0 else chunk_size
        else:
            chunk_size = 0

        real_size: int = AesStream.get_output_size(EncryptionMode.Decrypt, len(data), enc_format, chunk_size)
        out_data: bytearray = bytearray(real_size)

        if self.__threads == 1:
            input_stream: MemoryStream = MemoryStream(data)
            _decrypt_data(input_stream, 0, input_stream.get_length(), out_data,
                          key, nonce, enc_format, integrity, hash_key, chunk_size, self.__buffer_size)
        else:
            self.__decrypt_data_parallel(data, out_data,
                                         key, hash_key, nonce, enc_format,
                                         chunk_size, integrity)
        return out_data

    def __decrypt_data_parallel(self, data: bytearray, out_data: bytearray,
                                key: bytearray, hash_key: bytearray | None, nonce: bytearray | None,
                                enc_format: EncryptionFormat,
                                chunk_size: int, integrity: bool):
        """!
        Decrypt stream using parallel threads.
        @param data: The input data to be decrypted
        @param out_data: The output buffer with the decrypted data.
        @param key: The AES key.
        @param hash_key: The hash key.
        @param nonce: The nonce to be used for decryption.
        @param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        @param chunk_size: The chunk size.
        @param integrity: True to verify integrity.
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
            part_size = math.ceil(len(data) / float(self.__threads))
            if part_size > min_part_size:
                part_size -= part_size % min_part_size
            else:
                part_size = min_part_size
            running_threads = int(len(data) // part_size)
            if running_threads > self.__threads:
                running_threads = self.__threads

        self.__submit_decrypt_jobs(running_threads, part_size,
                                   data, out_data,
                                   key, hash_key, nonce, enc_format,
                                   integrity, chunk_size)

    def __submit_decrypt_jobs(self, running_threads: int, part_size: int,
                              data: bytearray, out_data: bytearray,
                              key: bytearray, hash_key: bytearray | None, nonce: bytearray | None,
                              enc_format: EncryptionFormat,
                              integrity: bool, chunk_size: int):

        """
        Submit decryption parallel jobs.
        @param running_threads: The number of threads to submit.
        @param part_size: The data length of each part that belongs to each thread.
        @param data: The buffer of data you want to decrypt. This is a shared byte array across all threads where each
                    thread will read each own part.
        @param out_data: The buffer of data containing the decrypted data.
        @param key: The AES key.
        @param hash_key: The hash key for integrity validation.
        @param nonce: The nonce for the data.
        @param enc_format: The {@link EncryptionFormat} Generic or Salmon.
        @param integrity: True to verify the data integrity.
        @param chunk_size: The chunk size.
        """
        shm_out = shared_memory.SharedMemory(create=True, size=len(out_data))
        shm_out_name = shm_out.name

        shm_cancel = shared_memory.SharedMemory(create=True, size=1)
        shm_cancel_name = shm_cancel.name

        ex: Exception | None = None
        fs = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(_decrypt_shm, i, part_size, running_threads,
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

        out_data[:] = shm_out.buf[0:len(out_data)]

        shm_cancel.close()
        shm_cancel.unlink()

        shm_out.close()
        shm_out.unlink()

        if ex:
            try:
                raise ex
            except Exception as e:
                raise RuntimeError() from e

    def close(self):
        """!
        Close the decryptor and associated resources
        """
        if self.__executor:
            self.__executor.shutdown(False)
