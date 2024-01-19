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
from __future__ import annotations

import concurrent
import math
import time
from concurrent.futures import ThreadPoolExecutor, Future, ProcessPoolExecutor
from multiprocessing import shared_memory
from multiprocessing.shared_memory import SharedMemory
from typing import Any, Callable

from typeguard import typechecked

from convert.bit_converter import BitConverter
from file.ireal_file import IRealFile
from iostream.random_access_stream import RandomAccessStream
from salmon.iostream.salmon_stream import SalmonStream
from salmonfs.salmon_file import SalmonFile
from utils.salmon_file_utils import SalmonFileUtils


@typechecked
def export_file(index: int, final_part_size: int, final_running_threads: int, file_size: int,
                real_file_to_export: IRealFile,
                real_file: IRealFile,
                shm_total_bytes_read_name: str,
                shm_cancel_name: str,
                buffer_size: int, key: bytearray, integrity: bool, hash_key: bytearray | None, chunk_size: int,
                enable_log_details: bool = False):
    file_to_export: SalmonFile = SalmonFile(real_file_to_export)
    file_to_export.set_allow_overwrite(True)
    file_to_export.set_encryption_key(key)
    file_to_export.set_apply_integrity(integrity, hash_key, chunk_size)

    shm_total_bytes_read = SharedMemory(shm_total_bytes_read_name)
    total_bytes_written = memoryview(shm_total_bytes_read.buf[index * 8:(index + 1) * 8])

    start: int = final_part_size * index
    length: int
    if index == final_running_threads - 1:
        length = file_size - start
    else:
        length = final_part_size
    try:
        export_file_part(file_to_export, real_file, start, length, total_bytes_written,
                         buffer_size,
                         shm_cancel_name, enable_log_details)
    except Exception as ex:
        raise ex
    finally:
        if shm_total_bytes_read is not None:
            total_bytes_written.release()
            shm_total_bytes_read.close()
            shm_total_bytes_read.unlink()


@typechecked
def export_file_part(file_to_export: SalmonFile, real_file: IRealFile, start: int, count: int,
                     total_bytes_written: memoryview, buffer_size: int,
                     shm_cancel_name: str | None = None, enable_log_details: bool = False):
    """
     * Export a file part from the drive.
     *
     * @param file_to_export      The file the part belongs to
     * @param export_file        The file to copy the exported part to
     * @param start             The start position on the file
     * @param count             The length of the bytes to be decrypted
     * @param total_bytes_written The total bytes that were written to the external file
    """

    shm_cancel_data: memoryview | None = None
    shm_cancel: SharedMemory | None = None
    if shm_cancel_name is not None:
        shm_cancel = SharedMemory(shm_cancel_name, size=1)
        shm_cancel_data = shm_cancel.buf

    start_time: int = int(time.time() * 1000)
    total_part_bytes_written: int = 0

    target_stream: RandomAccessStream | None = None
    source_stream: RandomAccessStream | None = None

    try:
        target_stream = real_file.get_output_stream()
        target_stream.set_position(start)

        source_stream = file_to_export.get_input_stream()
        source_stream.set_position(start)

        v_bytes: bytearray = bytearray(buffer_size)
        bytes_read: int
        if enable_log_details:
            print("SalmonFileExporter: FilePart: " + file_to_export.get_real_file().get_base_name() + ": "
                  + file_to_export.get_base_name() + " start = " + str(start) + " count = " + str(count))

        while (bytes_read := source_stream.read(v_bytes, 0,
                                                min(len(v_bytes), count - total_part_bytes_written))) > 0 \
                and total_part_bytes_written < count:
            if shm_cancel_data is not None and shm_cancel_data[0]:
                break
            target_stream.write(v_bytes, 0, bytes_read)
            total_part_bytes_written += bytes_read
            total_bytes_written[:8] = BitConverter.to_bytes(total_part_bytes_written, 8)[0:8]

        if enable_log_details:
            total: int = int(time.time() * 1000) - start_time
            print("SalmonFileExporter: File Part: " + file_to_export.get_base_name() + " exported " + str(
                total_part_bytes_written)
                  + " bytes in: " + str(total) + " ms"
                  + ", avg speed: " + str(total_bytes_written[0] / float(total)) + " bytes/sec")

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
class SalmonFileExporter:
    __DEFAULT_BUFFER_SIZE = 512 * 1024
    """
     * The global default buffer size to use when reading/writing on the SalmonStream.
    """

    __DEFAULT_THREADS = 1
    """
     * The global default threads to use.
    """

    __MIN_FILE_SIZE = 2 * 1024 * 1024
    """
     * Minimum file size to use parallelism. Anything less will use single thread.
    """

    __enableMultiThread: bool = True
    """
     * True if multithreading is enabled.
    """

    __enableLog: bool = False
    __enableLogDetails: bool = False

    def __init__(self, buffer_size: int, threads: int, multi_cpu: bool = False):
        """
         * Constructs a file exporter that can be used to export files from the drive
         *
         * @param buffer_size Buffer size to be used when decrypting files.
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

        if buffer_size == 0:
            buffer_size = SalmonFileExporter.__DEFAULT_BUFFER_SIZE
        if threads == 0:
            threads = SalmonFileExporter.__DEFAULT_THREADS
        self.__buffer_size = buffer_size
        self.__threads = threads

        self.__executor = ThreadPoolExecutor(self.__threads) if not multi_cpu else ProcessPoolExecutor(self.__threads)

    @staticmethod
    def set_enable_log(value: bool):
        SalmonFileExporter.__enableLog = value

    @staticmethod
    def set_enable_log_details(value: bool):
        SalmonFileExporter.__enableLogDetails = value

    def is_running(self) -> bool:
        return not self.__stopped

    def stop(self):
        self.__stopped = True

    def export_file(self, file_to_export: SalmonFile, export_dir: IRealFile, filename: str | None,
                    delete_source: bool,
                    integrity: bool, on_progress: Callable[[int, int], Any] | None) -> IRealFile | None:
        """
         * Export a file from the drive to the external directory path
         *
         * @param file_to_export The file that will be exported
         * @param export_dir    The external directory the file will be exported to
         * @param filename     The filename to use
         * @param delete_source Delete the source file when the export finishes successfully
         * @param integrity    True to verify integrity
        """

        if self.is_running():
            Exception("Another export is running")
        if file_to_export.is_directory():
            raise Exception("Cannot export directory, use SalmonFileCommander instead")

        exported_file: IRealFile | None = None
        filename = filename if filename is not None else file_to_export.get_base_name()
        try:
            if not self.__enableMultiThread and self.__threads != 1:
                raise NotImplementedError("Multithreading is not supported")

            start_time: int = 0
            self.__stopped = False
            if SalmonFileExporter.__enableLog:
                start_time = int(time.time() * 1000)

            total_bytes_written = [0]
            self.__failed = False

            if not export_dir.exists():
                export_dir.mkdir()
            exported_file = export_dir.create_file(filename)
            # we use the drive hash key for integrity verification
            file_to_export.set_verify_integrity(integrity, None)

            file_size: int = file_to_export.get_size()
            part_size: int = file_size
            running_threads: int

            # make sure we allocate enough space for the file
            target_stream: RandomAccessStream = exported_file.get_output_stream()
            target_stream.set_length(file_size)
            target_stream.close()

            if file_size > SalmonFileExporter.__MIN_FILE_SIZE:
                part_size = int(math.ceil(file_size / float(self.__threads)))

                # if we want to check integrity we align to the chunk size otherwise to the AES Block
                min_part_size: int = SalmonFileUtils.get_minimum_part_size(file_to_export)

                # calculate the last part size
                rem: int = part_size % min_part_size
                if rem != 0:
                    part_size += min_part_size - rem

                running_threads = int(math.ceil(file_size / float(part_size)))
            else:
                running_threads = 1

            if running_threads == 0:
                running_threads = 1

            final_part_size: int = part_size
            final_running_threads: int = running_threads

            shm_total_bytes_read = shared_memory.SharedMemory(create=True, size=8 * final_running_threads)
            shm_total_bytes_read_name = shm_total_bytes_read.name

            shm_cancel_name = self.__shm_cancel.name
            self.__shm_cancel.buf[0] = 0

            fs: list[Future] = []
            for i in range(0, running_threads):
                fs.append(self.__executor.submit(export_file, i, final_part_size,
                                                 final_running_threads,
                                                 file_size,
                                                 file_to_export.get_real_file(),
                                                 exported_file,
                                                 shm_total_bytes_read_name,
                                                 shm_cancel_name,
                                                 # on_progress,
                                                 self.__buffer_size,
                                                 file_to_export.get_encryption_key(),
                                                 integrity,
                                                 file_to_export.get_hash_key(),
                                                 file_to_export.get_requested_chunk_size(),
                                                 SalmonFileExporter.__enableLogDetails))
            total_bytes_read = 0
            while True:
                n_total_bytes_read = 0
                for i in range(0, len(fs)):
                    chunk_bytes_read = bytearray(shm_total_bytes_read.buf[i * 8:(i + 1) * 8])
                    n_total_bytes_read += BitConverter.to_long(chunk_bytes_read, 0, 8)

                if total_bytes_read != n_total_bytes_read:
                    total_bytes_read = n_total_bytes_read
                    on_progress(total_bytes_read, file_to_export.get_size())

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
                exported_file.delete()
            elif delete_source:
                file_to_export.get_real_file().delete()
            if self.__lastException is not None:
                raise self.__lastException
            if SalmonFileExporter.__enableLog:
                total: int = int(time.time() * 1000) - start_time
                print("SalmonFileExporter AesType: " + SalmonStream.get_aes_provider_type().name
                      + " File: " + file_to_export.get_base_name() + " verified and exported "
                      + str(total_bytes_written[0]) + " bytes in: " + str(total) + " ms"
                      + ", avg speed: " + str(total_bytes_written[0] / float(total)) + " bytes/sec")

        except Exception as ex:
            print(ex)
            self.__failed = True
            self.__stopped = True
            raise ex

        if self.__stopped or self.__failed:
            self.__stopped = True
            return None

        self.__stopped = True
        return exported_file

    def _finalize(self):
        self.close()

    def close(self):
        self.__executor.shutdown(False)
        self.__shm_cancel.close()
        self.__shm_cancel.unlink()
