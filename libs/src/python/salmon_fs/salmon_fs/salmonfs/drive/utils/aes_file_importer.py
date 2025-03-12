#!/usr/bin/env python3
from __future__ import annotations

__license__ = """
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of self software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and self permission notice shall be included in all
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
import time
import sys
from concurrent.futures import ProcessPoolExecutor, Future, ThreadPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory
from typing import Any, Callable
from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.fs.file.ifile import IFile
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.salmon.streams.aes_stream import AesStream
from salmon_fs.salmonfs.file.aes_file import AesFile


@typechecked
def import_file(index: int, final_part_size: int, final_running_threads: int, file_size: int,
                file_to_import: IFile,
                salmon_real_file: IFile,
                shm_total_bytes_read_name: str,
                shm_cancel_name: str,
                buffer_size: int, key: bytearray,
                integrity: bool, hash_key: bytearray | None, chunk_size: int):
    imported_file: AesFile = AesFile(salmon_real_file)
    imported_file.set_allow_overwrite(True)
    imported_file.set_encryption_key(key)
    imported_file.set_apply_integrity(integrity, hash_key, chunk_size)

    shm_total_bytes_read = SharedMemory(shm_total_bytes_read_name)
    total_bytes_read = memoryview(shm_total_bytes_read.buf[index * 8:(index + 1) * 8])

    start: int = final_part_size * index
    length: int
    if index == final_running_threads - 1:
        length = file_size - start
    else:
        length = final_part_size

    try:
        import_file_part(file_to_import, imported_file, start, length, total_bytes_read,
                         buffer_size,
                         shm_cancel_name)
        pass
    except Exception as ex:
        raise ex
    finally:
        if shm_total_bytes_read is not None:
            total_bytes_read.release()
            shm_total_bytes_read.close()


@typechecked
def import_file_part(file_to_import: IFile, salmon_file: AesFile, start: int, count: int,
                     total_bytes_read: memoryview | None, buffer_size: int,
                     shm_cancel_name: str | None = None,
                     stopped: list[bool] | None = None,
                     on_progress: Callable[[int, int], Any] | None = None):
    """
    Import a file part into a file in the drive.
    
    :param file_to_import:   The external file that will be imported
    :param salmon_file:     The file that will be imported to
    :param start:          The start position of the byte data that will be imported
    :param count:          The length of the file content that will be imported
    :param total_bytes_read: The total bytes read from the external file
    :param buffer_size: The buffer size
    :param shm_cancel_name: The shared memory for cancelation
    :param stopped: The stopped flag
    :param on_progress: 	 Progress observer
    """
    shm_cancel_data: memoryview | None = None
    shm_cancel: SharedMemory | None = None
    if shm_cancel_name is not None:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    start_time: int = int(time.time() * 1000)
    total_part_bytes_read: int = 0

    target_stream: AesStream | None = None
    source_stream: RandomAccessStream | None = None

    try:
        target_stream = salmon_file.get_output_stream()
        target_stream.set_position(start)

        source_stream = file_to_import.get_input_stream()
        source_stream.set_position(start)

        v_bytes: bytearray = bytearray(buffer_size)
        bytes_read: int

        while (bytes_read := source_stream.read(
                v_bytes, 0,
                min(len(v_bytes), count - total_part_bytes_read))) > 0 and total_part_bytes_read < count:
            if (shm_cancel_data is not None and shm_cancel_data[0]) or (stopped is not None and stopped[0]):
                break

            target_stream.write(v_bytes, 0, bytes_read)
            total_part_bytes_read += bytes_read
            if total_bytes_read is not None:
                total_bytes_read[:8] = BitConverter.to_bytes(total_part_bytes_read, 8)[0:8]
            if on_progress:
                on_progress(total_part_bytes_read, count)
    except Exception as ex:
        print(ex, file=sys.stderr)
        raise ex
    finally:
        if target_stream is not None:
            target_stream.flush()
            target_stream.close()

        if source_stream is not None:
            source_stream.close()

        if shm_cancel is not None:
            shm_cancel.close()


@typechecked
class AesFileImporter:
    __DEFAULT_BUFFER_SIZE = 512 * 1024
    """
    The global default buffer size to use when reading/writing on the AesStream.
    """

    __DEFAULT_THREADS = 1
    """
    The global default threads to use.
    """

    def __init__(self, buffer_size: int, threads: int, multi_cpu: bool = False):
        """
        Constructs a file importer that can be used to import files to the drive
        
        :param buffer_size: Buffer size to be used when encrypting files.
                          If using integrity self value has to be a multiple of the Chunk size.
                          If not using integrity it should be a multiple of the AES block size for better performance
        :param threads: The threads to use
        :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
        """

        self.__buffer_size: int = 0
        """
        Current buffer size.
        """

        self.__threads: int = 0
        """
        Current threads.
        """

        self.__stopped: list[bool] = [True]
        """
        True if last job was stopped by the user.
        """

        self.__shm_cancel = shared_memory.SharedMemory(create=True, size=1)
        """
        Shared memory to notify for task cancellations 
        """

        self.__failed: bool = False
        """
        Failed if last job was failed.
        """

        self.__lastException: Exception | None = None
        """
        Last exception occurred.
        """

        self.__executor: ThreadPoolExecutor | ProcessPoolExecutor | None = None
        """
        The executor to be used for running parallel exports.
        """

        self.__buffer_size = buffer_size
        if self.__buffer_size == 0:
            self.__buffer_size = AesFileImporter.__DEFAULT_BUFFER_SIZE
        self.__threads = threads
        if self.__threads == 0:
            self.__threads = AesFileImporter.__DEFAULT_THREADS

        self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(self.__threads)

    def stop(self):
        """
        Stops all current importing tasks
        """
        self.__stopped[0] = True
        # cancel all tasks
        self.__shm_cancel.buf[0] = 1

    def is_running(self) -> bool:
        """
        True if importer is currently running a job.
        
        :return: Tru if running
        """
        return not self.__stopped[0]

    def import_file(self, file_to_import: IFile, v_dir: AesFile, filename: str | None, delete_source: bool,
                    integrity: bool,
                    on_progress: Callable[[int, int], Any] | None) -> AesFile | None:
        """
        Imports a real file into the drive.
        
        :param file_to_import: The source file that will be imported in to the drive.
        :param v_dir:          The target directory in the drive that the file will be imported
        :param filename:    The file name
        :param delete_source: If True delete the source file.
        :param integrity:    Apply data integrity
        :param on_progress:   Progress to notify
         """
        if self.is_running():
            raise Exception("Another import is running")
        if file_to_import.is_directory():
            raise Exception("Cannot import directory, use AesFileCommander instead")

        filename = filename if filename is not None else file_to_import.get_name()
        imported_file: AesFile
        try:
            self.__stopped[0] = False
            self.__failed = False
            self.__lastException = None

            imported_file = v_dir.create_file(filename)
            imported_file.set_allow_overwrite(True)
            # we use default chunk file size
            imported_file.set_apply_integrity(integrity)

            file_size: int = file_to_import.get_length()
            running_threads: int = 1
            part_size: int = file_size

            # for python we make sure to allocate enough space for the file 
            # this will also create the header
            target_stream: AesStream = imported_file.get_output_stream()
            target_stream.set_length(file_size)
            target_stream.close()

            # if we want to check integrity we align to the chunk size otherwise to the AES Block
            min_part_size: int = imported_file.get_minimum_part_size()
            if part_size > min_part_size and self.__threads > 1:
                part_size = math.ceil(file_size / float(self.__threads))
                if part_size > min_part_size:
                    part_size -= part_size % min_part_size
                else:
                    part_size = min_part_size
                running_threads = int(file_size // part_size)

            if running_threads == 1:
                import_file_part(file_to_import, imported_file, 0, file_size, None, self.__buffer_size,
                                 None, self.__stopped, on_progress)
            else:
                self.__submit_import_jobs(running_threads, part_size, file_to_import, imported_file,
                                          integrity, on_progress)

            if self.__stopped[0]:
                imported_file.get_real_file().delete()
            elif delete_source:
                file_to_import.delete()
            if self.__lastException is not None:
                raise self.__lastException
        except Exception as ex:
            print(ex, file=sys.stderr)
            self.__failed = True
            self.__stopped[0] = True
            raise ex

        if self.__stopped[0] or self.__failed:
            self.__stopped[0] = True
            return None

        self.__stopped[0] = True
        return imported_file

    def __submit_import_jobs(self, running_threads: int, part_size: int, file_to_import: IFile,
                             imported_file: AesFile,
                             integrity: bool,
                             on_progress: Callable[[int, int], Any] | None):

        shm_total_bytes_read = shared_memory.SharedMemory(create=True, size=8 * running_threads)
        shm_total_bytes_read_name = shm_total_bytes_read.name
        file_size: int = file_to_import.get_length()
        shm_cancel_name = self.__shm_cancel.name
        self.__shm_cancel.buf[0] = 0

        fs: list[Future] = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(import_file, i, part_size, running_threads,
                                             file_size,
                                             file_to_import,
                                             imported_file.get_real_file(),
                                             shm_total_bytes_read_name,
                                             shm_cancel_name,
                                             self.__buffer_size,
                                             imported_file.get_encryption_key(),
                                             integrity,
                                             imported_file.get_hash_key(),
                                             imported_file.get_requested_chunk_size()))
        total_bytes_read = 0
        while True:
            n_total_bytes_read = 0
            for i in range(0, len(fs)):
                chunk_bytes_read = bytearray(shm_total_bytes_read.buf[i * 8:(i + 1) * 8])
                n_total_bytes_read += BitConverter.to_long(chunk_bytes_read, 0, 8)

            if total_bytes_read != n_total_bytes_read:
                total_bytes_read = n_total_bytes_read
                if on_progress:
                    on_progress(total_bytes_read, file_size)

            complete: bool = True
            for f in fs:
                complete &= f.done()
            if complete:
                break
            time.sleep(0.25)

        for f in concurrent.futures.as_completed(fs):
            try:
                # catch any errors within the children processes
                f.result()
            except Exception as ex1:
                print(ex1, file=sys.stderr)
                self.__lastException = ex1
                self.__failed = True
                # cancel all tasks
                self.stop()

        shm_total_bytes_read.close()
        shm_total_bytes_read.unlink()

    def _finalize(self):
        self.close()

    def close(self):
        self.__executor.shutdown()
        self.__shm_cancel.close()
        self.__shm_cancel.unlink()
