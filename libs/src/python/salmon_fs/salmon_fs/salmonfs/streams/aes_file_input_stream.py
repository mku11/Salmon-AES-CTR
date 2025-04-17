#!/usr/bin/env python3
"""!@brief Implementation of a Python BufferedIOBase for seeking and reading an AesFile.
"""

from __future__ import annotations

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

import math
import threading
import sys
from concurrent.futures import ThreadPoolExecutor
from typeguard import typechecked
from wrapt import synchronized

from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.streams.buffer import Buffer
from salmon_core.salmon.generator import Generator
from salmon_core.streams.buffered_io_wrapper import BufferedIOWrapper
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_core.salmon.security_exception import SecurityException
from salmon_fs.salmonfs.file.aes_file import AesFile


@typechecked
class AesFileInputStream(BufferedIOWrapper):
    """!
    Implementation of a Python InputStream for seeking and reading a AesFile.
    This class provides a seekable source with parallel substreams and cached buffers
    for performance.
    """

    __DEFAULT_THREADS = 1
    """
    default threads is one but you can increase it
    """

    def __init__(self, aes_file: AesFile, buffers_count: int, buffer_size: int, threads: int, back_offset: int):
        """
        Instantiate a seekable stream from an encrypted file source
        
        @param aes_file:   The source file.
        @param buffers_count: Number of buffers to use.
        @param buffer_size:   The length of each buffer.
        @param threads:      The number of threads/streams to source the file in parallel.
        @param back_offset:   The back offset.
        """

        super().__init__(None, buffers_count, buffer_size, back_offset,
                         aes_file.get_file_chunk_size() if aes_file.get_file_chunk_size() > 0 else Generator.BLOCK_SIZE)
        self.__streams: list[AesStream | None] | None = None
        self.__aes_file: AesFile | None = None
        self.__threads: int = 0
        self.__executor: ThreadPoolExecutor | None = None

        self.__aes_file = aes_file
        self.set_total_size(aes_file.get_length())
        if threads == 0:
            threads = AesFileInputStream.DEFAULT_THREADS
        if (threads & (threads - 1)) != 0:
            raise Exception("Threads needs to be a power of 2 (ie 1,2,4,8)")
        self.__threads = threads
        self.set_position_end(self.get_total_size() - 1)
        self.__create_streams()

    def __create_streams(self):
        """!
        Method creates the parallel streams for reading from the file
        """
        self.__executor = ThreadPoolExecutor(self.__threads)
        self.__streams: list[RandomAccessStream | None] = [None] * self.__threads
        try:
            for i in range(0, self.__threads):
                self.__streams[i] = self.__aes_file.get_input_stream()

        except (SecurityException, IntegrityException) as ex:
            raise IOError("Could not create streams") from ex

    @synchronized
    def _fill_buffer(self, cache_buffer: Buffer, start_position: int,
                     length: int) -> int:
        """!
        Fills a cache buffer with the decrypted data from the encrypted source file.
        
        @param cache_buffer: The cache buffer that will store the decrypted contents
        @param length:  The length of the data requested
        """

        bytes_read: int
        if self.__threads == 1:
            bytes_read = self._fill_buffer_part(cache_buffer, start_position, 0, length, self.__streams[0])
        else:
            bytes_read = self.__fill_buffer_multi(cache_buffer, start_position, length)

        return bytes_read

    def __fill_buffer_multi(self, cache_buffer: Buffer, start_position: int,
                            total_buffer_length: int) -> int:
        """!
        Fill the buffer using parallel streams for performance
        
        @param cache_buffer:   The cache buffer that will store the decrypted data
        @param start_position: The source file position the read will start from
        @param total_buffer_length:    The buffer size that will be used to read from the file
        """
        bytes_read = [0]
        ex: Exception | None = None
        # Multithreaded decryption jobs
        done: threading.Barrier = threading.Barrier(self.__threads + 1)
        needs_back_offset: bool = total_buffer_length == self.get_buffer_size()
        part_size = 0
        if needs_back_offset:
            part_size = int(math.ceil((total_buffer_length - self.get_backoffset()) / float(self.__threads)))
        else:
            part_size = int(math.ceil(total_buffer_length / float(self.__threads)))

        for i in range(0, self.__threads):

            def __fill(index: int):
                nonlocal ex, bytes_read

                start: int = part_size * index
                if index > 0 and needs_back_offset:
                    start += self.get_backoffset()
                length: int
                if index == 0 and needs_back_offset:
                    length = part_size + self.get_backoffset()
                elif index == self.__threads - 1:
                    length = self.get_buffer_size() - start
                else:
                    length = part_size
                try:
                    chunk_bytes_read: int = self._fill_buffer_part(cache_buffer, start_position + start, start, length,
                                                                   self.__streams[index])
                    if chunk_bytes_read >= 0:
                        bytes_read[0] += chunk_bytes_read
                except Exception as ex1:
                    ex = ex1

                done.wait()

            self.__executor.submit(__fill, i)

        try:
            done.wait()
        except InterruptedError as ex:
            print(ex, file=sys.stderr)

        if ex:
            try:
                raise ex
            except Exception as e:
                raise RuntimeError() from e

        return bytes_read[0]

    def close(self):
        """!
        Close the stream and associated backed streams and clear buffers.
        
        @exception IOError: Thrown if there is an IO error.
        """
        self.__close_streams()
        if self.__executor:
            self.__executor.shutdown(False)
        super().close()

    @synchronized
    def __close_streams(self):
        """!
        Close all back streams.
        
        @exception IOError: Thrown if there is an IO error.
        """
        for i in range(0, self.__threads):
            if self.__streams[i]:
                self.__streams[i].close()
            self.__streams[i] = None
