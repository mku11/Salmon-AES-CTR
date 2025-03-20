#!/usr/bin/env python3
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

import concurrent
import math
import time
import sys
from concurrent.futures import ThreadPoolExecutor, Future, ProcessPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory
from typing import Any, Callable
from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.fs.file.ifile import IFile
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_fs.salmonfs.file.aes_file import AesFile


@typechecked
def _export_file(index: int, final_part_size: int, final_running_threads: int, file_size: int,
                 real_file_to_export: IFile,
                 real_file: IFile,
                 shm_total_bytes_read_name: str,
                 shm_cancel_name: str,
                 buffer_size: int, key: bytearray,
                 integrity: bool, hash_key: bytearray | None, chunk_size: int):
    """
    Do not use directly use AesFileExporter class instead.
    """
    file_to_export: AesFile = AesFile(real_file_to_export)
    file_to_export.set_encryption_key(key)
    file_to_export.set_verify_integrity(integrity, hash_key)

    shm_total_bytes_read = SharedMemory(shm_total_bytes_read_name)
    total_bytes_written = memoryview(shm_total_bytes_read.buf[index * 8:(index + 1) * 8])

    start: int = final_part_size * index
    length: int
    if index == final_running_threads - 1:
        length = file_size - start
    else:
        length = final_part_size
    try:
        _export_file_part(file_to_export, real_file, start, length, total_bytes_written,
                          buffer_size, shm_cancel_name)
    except Exception as ex:
        raise ex
    finally:
        if shm_total_bytes_read:
            total_bytes_written.release()
            shm_total_bytes_read.close()


@typechecked
def _export_file_part(file_to_export: AesFile, exported_file: IFile, start: int, count: int,
                      total_bytes_written: memoryview | None, buffer_size: int,
                      shm_cancel_name: str | None = None,
                      stopped: list[bool] | None = None,
                      on_progress_changed: Callable[[int, int], Any] | None = None):
    """
    Do not use directly use AesFileExporter class instead.
    """

    shm_cancel_data: memoryview | None = None
    shm_cancel: SharedMemory | None = None
    if shm_cancel_name:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    start_time: int = int(time.time() * 1000)
    total_part_bytes_written: int = 0

    target_stream: RandomAccessStream | None = None
    source_stream: RandomAccessStream | None = None

    try:
        target_stream = exported_file.get_output_stream()
        target_stream.set_position(start)

        source_stream = file_to_export.get_input_stream()
        source_stream.set_position(start)

        v_bytes: bytearray = bytearray(buffer_size)
        bytes_read: int

        while (bytes_read := source_stream.read(v_bytes, 0,
                                                min(len(v_bytes), count - total_part_bytes_written))) > 0 \
                and total_part_bytes_written < count:
            if (shm_cancel_data and shm_cancel_data[0]) or (stopped and stopped[0]):
                break
            target_stream.write(v_bytes, 0, bytes_read)
            total_part_bytes_written += bytes_read
            if total_bytes_written:
                total_bytes_written[:8] = BitConverter.to_bytes(total_part_bytes_written, 8)[0:8]
            if on_progress_changed:
                on_progress_changed(total_part_bytes_written, count)
    except Exception as ex:
        print(ex, file=sys.stderr)
        raise ex
    finally:
        if target_stream:
            target_stream.flush()
            target_stream.close()

        if source_stream:
            source_stream.close()

        if shm_cancel:
            shm_cancel.close()


@typechecked
class AesFileExporter:
    """
    Exports files from drices
    """

    __DEFAULT_BUFFER_SIZE = 512 * 1024
    """
    The global default buffer size to use when reading/writing on the AesStream.
    """

    __DEFAULT_THREADS = 1
    """
    The global default threads to use.
    """

    def __init__(self, buffer_size: int = 0, threads: int = 1, multi_cpu: bool = False):
        """!
        Constructs a file exporter that can be used to export files from the drive
        
        @param buffer_size: Buffer size to be used when decrypting files.
                          If using integrity self value has to be a multiple of the Chunk size.
                          If not using integrity it should be a multiple of the AES block size for better performance
        @param threads: The threads to use
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
        """!
        Last exception occurred.
        """

        self.__executor: ThreadPoolExecutor | ProcessPoolExecutor | None = None
        """!
        The executor to be used for running parallel exports.
        """

        self.__buffer_size = buffer_size
        if self.__buffer_size <= 0:
            self.__buffer_size = AesFileExporter.__DEFAULT_BUFFER_SIZE
        self.__threads = threads
        if self.__threads <= 0:
            self.__threads = AesFileExporter.__DEFAULT_THREADS
        if self.__threads > 1:
            self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(
                self.__threads)

    def stop(self):
        """!
        Stop all tasks
        """
        self.__stopped[0] = True
        # cancel all tasks
        self.__shm_cancel.buf[0] = 1

    def is_running(self) -> bool:
        """!
        True if exporter is currently running a job.
        
        @returns True if running
        """
        return not self.__stopped[0]

    def export_file(self, file_to_export: AesFile, export_dir: IFile,
                    options: AesFileExporter.FileExportOptions | None = None) -> IFile | None:
        """!
        Export a file from the drive to the external directory path
        
        @param file_to_export: The file that will be exported
        @param export_dir:    The external directory the file will be exported to
        @param options: The options
        """
        if not options:
            options = AesFileExporter.FileExportOptions()

        if self.is_running():
            Exception("Another export is running")
        if file_to_export.is_directory():
            raise Exception("Cannot export directory, use AesFileCommander instead")

        exported_file: IFile | None = None
        filename: str = options.filename if options.filename else file_to_export.get_name()
        try:
            self.__stopped[0] = False
            total_bytes_written = [0]
            self.__failed = False
            self.__lastException = None

            if not export_dir.exists():
                export_dir.mkdir()
            exported_file = export_dir.create_file(filename)
            # we use the drive hash key for integrity verification
            file_to_export.set_verify_integrity(options.integrity)

            file_size: int = file_to_export.get_length()
            running_threads: int = 1
            part_size: int = file_size

            # for python we make sure to allocate enough space for the file
            target_stream: RandomAccessStream = exported_file.get_output_stream()
            target_stream.set_length(file_size)
            target_stream.close()

            # if we want to check integrity we align to the chunk size otherwise to the AES Block
            min_part_size: int = file_to_export.get_minimum_part_size()
            if part_size > min_part_size and self.__threads > 1:
                part_size = int(math.ceil(file_size / float(self.__threads)))
                if part_size > min_part_size:
                    part_size -= part_size % min_part_size
                else:
                    part_size = min_part_size
                running_threads = int(file_size // part_size)

            if running_threads == 1:
                _export_file_part(file_to_export, exported_file, 0, file_size, None, self.__buffer_size,
                                  None, self.__stopped, options.on_progress_changed)
            else:
                self.__submit_export_jobs(running_threads, part_size, file_to_export, exported_file,
                                          options.integrity, options.on_progress_changed)

            if self.__stopped[0]:
                exported_file.delete()
            elif options.delete_source:
                file_to_export.get_real_file().delete()
            if self.__lastException:
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
        return exported_file

    def __submit_export_jobs(self, running_threads: int, part_size: int, file_to_export: AesFile,
                             exported_file: IFile,
                             integrity: bool, on_progress_changed: Callable[[int, int], Any] | None):

        shm_total_bytes_read = shared_memory.SharedMemory(create=True, size=8 * running_threads)
        shm_total_bytes_read_name = shm_total_bytes_read.name
        file_size: int = file_to_export.get_length()
        shm_cancel_name = self.__shm_cancel.name
        self.__shm_cancel.buf[0] = 0

        fs: list[Future] = []
        for i in range(0, running_threads):
            fs.append(self.__executor.submit(_export_file, i, part_size,
                                             running_threads,
                                             file_size,
                                             file_to_export.get_real_file(),
                                             exported_file,
                                             shm_total_bytes_read_name,
                                             shm_cancel_name,
                                             self.__buffer_size,
                                             file_to_export.get_encryption_key(),
                                             integrity,
                                             file_to_export.get_hash_key(),
                                             file_to_export.get_requested_chunk_size()))
        total_bytes_written = 0
        while True:
            n_total_bytes_read = 0
            for i in range(0, len(fs)):
                chunk_bytes_read = bytearray(shm_total_bytes_read.buf[i * 8:(i + 1) * 8])
                n_total_bytes_read += BitConverter.to_long(chunk_bytes_read, 0, 8)

            if total_bytes_written != n_total_bytes_read:
                total_bytes_written = n_total_bytes_read
            if on_progress_changed:
                on_progress_changed(total_bytes_written, file_size)

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

    def close(self):
        """!
        Close this importer and associated resources
        """
        self.__executor.shutdown(False)
        self.__shm_cancel.close()
        self.__shm_cancel.unlink()

    class FileExportOptions:
        """!
         File exporter options
        """

        filename: str | None = None
        """!
         Override the filename
        """

        delete_source: bool = False
        """
         Delete the source file after completion.
        """

        integrity: bool = False
        """
         True to enable integrity.
        """

        on_progress_changed: Callable[[int, int], Any] | None = None
        """!
         Callback when progress changes
        """
