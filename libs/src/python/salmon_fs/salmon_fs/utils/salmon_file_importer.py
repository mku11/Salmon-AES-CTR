#!/usr/bin/env python3
'''
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
'''
from __future__ import annotations

import concurrent
import math
import time
from concurrent.futures import ProcessPoolExecutor, Future, ThreadPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory
from typing import Any, Callable

from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_fs.file.ireal_file import IRealFile
from salmon_core.iostream.random_access_stream import RandomAccessStream
from salmon_core.salmon.iostream.salmon_stream import SalmonStream
from salmon_fs.salmonfs.salmon_file import SalmonFile
from salmon_fs.utils.salmon_file_utils import SalmonFileUtils


def import_file(index: int, final_part_size: int, final_running_threads: int, file_size: int,
                file_to_import: IRealFile,
                salmon_real_file: IRealFile,
                shm_total_bytes_read_name: str,
                shm_cancel_name: str,
                buffer_size: int, key: bytearray, integrity: bool, hash_key: bytearray, chunk_size: int,
                enable_log_details: bool = False):
    """
    Do not use directly use encrypt() instead.
    :param index:
    :param final_part_size:
    :param final_running_threads:
    :param file_size:
    :param file_to_import:
    :param salmon_real_file:
    :param shm_total_bytes_read_name:
    :param shm_cancel_name:
    :param buffer_size:
    :param key:
    :param integrity:
    :param hash_key:
    :param chunk_size:
    :param enable_log_details:
    :return:
    """

    salmon_file: SalmonFile = SalmonFile(salmon_real_file)
    salmon_file.set_allow_overwrite(True)
    salmon_file.set_encryption_key(key)
    salmon_file.set_apply_integrity(integrity, hash_key, chunk_size)

    shm_total_bytes_read = SharedMemory(shm_total_bytes_read_name)
    total_bytes_read = memoryview(shm_total_bytes_read.buf[index * 8:(index + 1) * 8])

    start: int = final_part_size * index
    length: int
    if index == final_running_threads - 1:
        length = file_size - start
    else:
        length = final_part_size

    try:
        import_file_part(file_to_import, salmon_file, start, length, total_bytes_read,
                         buffer_size,
                         shm_cancel_name, enable_log_details)
        pass
    except Exception as ex:
        raise ex
    finally:
        if shm_total_bytes_read is not None:
            total_bytes_read.release()
            shm_total_bytes_read.close()
            shm_total_bytes_read.unlink()


def import_file_part(file_to_import: IRealFile, salmon_file: SalmonFile, start: int, count: int,
                     total_bytes_read: memoryview, buffer_size: int,
                     shm_cancel_name: str | None = None, enable_log_details: bool = False):
    """
     * Import a file part into a file in the drive.
     *
     * @param file_to_import   The external file that will be imported
     * @param salmon_file     The file that will be imported to
     * @param start          The start position of the byte data that will be imported
     * @param count          The length of the file content that will be imported
     * @param total_bytes_read The total bytes read from the external file
     * @param on_progress 	 Progress observer
    """
    shm_cancel_data: memoryview | None = None
    shm_cancel: SharedMemory | None = None
    if shm_cancel_name is not None:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    start_time: int = int(time.time() * 1000)
    total_part_bytes_read: int = 0

    target_stream: SalmonStream | None = None
    source_stream: RandomAccessStream | None = None

    try:
        target_stream = salmon_file.get_output_stream()
        target_stream.set_position(start)

        source_stream = file_to_import.get_input_stream()
        source_stream.set_position(start)

        v_bytes: bytearray = bytearray(buffer_size)
        bytes_read: int
        if enable_log_details:
            print(
                "SalmonFileImporter: File Part: " + salmon_file.get_real_file().get_base_name()
                + ": " + salmon_file.get_base_name()
                + " start = " + str(start) + " count = " + str(count))

        while (bytes_read := source_stream.read(
                v_bytes, 0,
                min(len(v_bytes), count - total_part_bytes_read))) > 0 and total_part_bytes_read < count:
            if shm_cancel_data is not None and shm_cancel_data[0]:
                break

            target_stream.write(v_bytes, 0, bytes_read)
            total_part_bytes_read += bytes_read
            total_bytes_read[:8] = BitConverter.to_bytes(total_part_bytes_read, 8)[0:8]

        if enable_log_details:
            total: int = int(time.time() * 1000) - start_time
            print("SalmonFileImporter: File Part: " + file_to_import.get_base_name()
                  + " imported " + str(total_part_bytes_read) + " bytes in: " + str(total) + " ms"
                  + ", avg speed: " + str(total_part_bytes_read / float(total)) + " Kbytes/sec")

    except Exception as ex:
        print(ex)
        raise ex
    finally:
        if target_stream is not None:
            target_stream.flush()
            target_stream.close()

        if source_stream is not None:
            source_stream.close()

        if shm_cancel is not None:
            shm_cancel.close()
            shm_cancel.unlink()


@typechecked
class SalmonFileImporter:
    __DEFAULT_BUFFER_SIZE = 512 * 1024
    """
     * The global default buffer size to use when reading/writing on the SalmonStream.
    """

    __DEFAULT_THREADS = 1
    """
     * The global default threads to use.
    """

    __enableMultiThread: bool = True
    """
     * True if multithreading is enabled.
    """

    __enableLog: bool = False
    __enableLogDetails: bool = False

    def __init__(self, buffer_size: int, threads: int, multi_cpu: bool = False):
        """
         * Constructs a file importer that can be used to import files to the drive
         *
         * @param buffer_size Buffer size to be used when encrypting files.
         *                   If using integrity self value has to be a multiple of the Chunk size.
         *                   If not using integrity it should be a multiple of the AES block size for better performance
         * @param threads
         * :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
        """

        self.__buffer_size: int = 0
        """
         * Current buffer size.
        """

        self.__threads: int = 0
        """
         * Current threads.
        """

        self.__stopped: bool = True
        """
         * True if last job was stopped by the user.
        """

        self.__shm_cancel = shared_memory.SharedMemory(create=True, size=1)
        """
        Shared memory to notify for task cancellations 
        """

        self.__failed: bool = False
        """
         * Failed if last job was failed.
        """

        self.__lastException: Exception | None = None
        """
         * Last exception occurred.
        """

        self.__executor: ThreadPoolExecutor | ProcessPoolExecutor | None = None
        """
         * The executor to be used for running parallel exports.
        """

        self.__buffer_size = buffer_size
        if self.__buffer_size == 0:
            self.__buffer_size = SalmonFileImporter.__DEFAULT_BUFFER_SIZE
        self.__threads = threads
        if self.__threads == 0:
            self.__threads = SalmonFileImporter.__DEFAULT_THREADS

        self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(self.__threads)

    @staticmethod
    def set_enable_log(value: bool):
        """
         * Enable logging when importing.
         *
         * @param value True to enable logging.
        """
        SalmonFileImporter.__enableLog = value

    @staticmethod
    def set_enable_log_details(value: bool):
        """
         * Enable logging details when importing.
         *
         * @param value True to enable logging details.
        """
        SalmonFileImporter.__enableLogDetails = value

    def stop(self):
        """
         * Stops all current importing tasks
        """
        self.__stopped = True
        # cancel all tasks
        self.__shm_cancel.buf[0] = 1

    def is_running(self) -> bool:
        """
         * True if importer is currently running a job.
         *
         * @return
        """
        return not self.__stopped

    def import_file(self, file_to_import: IRealFile, v_dir: SalmonFile, filename: str | None, delete_source: bool,
                    integrity: bool,
                    on_progress: Callable[[int, int], Any] | None) -> SalmonFile | None:
        """
         * Imports a real file into the drive.
         *
         * @param file_to_import The source file that will be imported in to the drive.
         * @param dir          The target directory in the drive that the file will be imported
         * @param delete_source If True delete the source file.
         * @param integrity    Apply data integrity
         * @param on_progress   Progress to notify
         """
        if self.is_running():
            raise Exception("Another import is running")
        if file_to_import.is_directory():
            raise Exception("Cannot import directory, use SalmonFileCommander instead")

        filename = filename if filename is not None else file_to_import.get_base_name()
        start_time: int = 0
        total_bytes_read: int = 0
        salmon_file: SalmonFile
        try:
            if not SalmonFileImporter.__enableMultiThread and self.__threads != 1:
                raise NotImplementedError("Multithreading is not supported")
            self.__stopped = False
            if SalmonFileImporter.__enableLog:
                start_time = int(time.time() * 1000)

            self.__failed = False
            salmon_file = v_dir.create_file(filename)
            salmon_file.set_allow_overwrite(True)
            # we use default chunk file size
            salmon_file.set_apply_integrity(integrity, None, None)

            file_size: int = file_to_import.length()
            running_threads: int = 1
            part_size: int = file_size

            # for python we make sure to allocate enough space for the file
            target_stream: SalmonStream = salmon_file.get_output_stream()
            target_stream.set_length(file_size)
            target_stream.close()

            # if we want to check integrity we align to the chunk size otherwise to the AES Block
            min_part_size: int = SalmonFileUtils.get_minimum_part_size(salmon_file)
            if part_size > min_part_size and self.__threads > 1:
                part_size = math.ceil(file_size / float(self.__threads))
                part_size -= part_size % min_part_size
                running_threads = int(file_size // part_size)

            final_part_size: int = part_size
            final_running_threads: int = running_threads

            shm_total_bytes_read = shared_memory.SharedMemory(create=True, size=8 * final_running_threads)
            shm_total_bytes_read_name = shm_total_bytes_read.name

            shm_cancel_name = self.__shm_cancel.name
            self.__shm_cancel.buf[0] = 0

            fs: list[Future] = []
            for i in range(0, running_threads):
                fs.append(self.__executor.submit(import_file, i, final_part_size, final_running_threads,
                                                 file_size,
                                                 file_to_import,
                                                 salmon_file.get_real_file(),
                                                 shm_total_bytes_read_name,
                                                 shm_cancel_name,
                                                 # on_progress,
                                                 self.__buffer_size,
                                                 salmon_file.get_encryption_key(),
                                                 integrity,
                                                 salmon_file.get_hash_key(),
                                                 salmon_file.get_requested_chunk_size(),
                                                 SalmonFileImporter.__enableLogDetails))
            total_bytes_read = 0
            while True:
                n_total_bytes_read = 0
                for i in range(0, len(fs)):
                    chunk_bytes_read = bytearray(shm_total_bytes_read.buf[i * 8:(i + 1) * 8])
                    n_total_bytes_read += BitConverter.to_long(chunk_bytes_read, 0, 8)

                if total_bytes_read != n_total_bytes_read:
                    total_bytes_read = n_total_bytes_read
                    if on_progress:
                        on_progress(total_bytes_read, salmon_file.get_size())

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
                    print(ex1)
                    self.__lastException = ex1
                    self.__failed = True
                    # cancel all tasks
                    self.stop()

            shm_total_bytes_read.close()
            shm_total_bytes_read.unlink()

            if self.__stopped:
                salmon_file.get_real_file().delete()
            elif delete_source:
                file_to_import.delete()
            if self.__lastException is not None:
                raise self.__lastException
            if SalmonFileImporter.__enableLog:
                total: int = int(time.time() * 1000) - start_time
                print(
                    "SalmonFileImporter AesType: " + SalmonStream.get_aes_provider_type().name
                    + ", File: " + file_to_import.get_base_name()
                    + " imported and signed " + str(total_bytes_read) + " bytes in total time: " + str(total) + " ms"
                    + ", avg speed: " + str(total_bytes_read / float(total)) + " Kbytes/sec")

        except Exception as ex:
            print(ex)
            self.__failed = True
            self.__stopped = True
            raise ex

        if self.__stopped or self.__failed:
            self.__stopped = True
            return None

        self.__stopped = True
        return salmon_file

    def _finalize(self):
        self.close()

    def close(self):
        self.__executor.shutdown()
        self.__shm_cancel.close()
        self.__shm_cancel.unlink()
