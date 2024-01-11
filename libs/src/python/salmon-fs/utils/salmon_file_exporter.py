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

import math
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Callable

from typeguard import typechecked

from file.ireal_file import IRealFile
from iostream.random_access_stream import RandomAccessStream
from salmon.iostream.salmon_stream import SalmonStream
from salmonfs.salmon_file import SalmonFile
from utils.salmon_file_utils import SalmonFileUtils


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

    def __init__(self, buffer_size: int, threads: int):

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

        self.__failed: bool = False
        """
         * Failed if last job was failed.
        """

        self.__lastException: Exception | None = None
        """
         * Last exception occurred.
        """

        self.__executor: ThreadPoolExecutor = ThreadPoolExecutor()
        """
         * The executor to be used for running parallel exports.
        """

        if buffer_size == 0:
            buffer_size = SalmonFileExporter.__DEFAULT_BUFFER_SIZE
        if threads == 0:
            threads = SalmonFileExporter.__DEFAULT_THREADS
        self.__buffer_size = buffer_size
        self.__threads = threads

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

        export_file: IRealFile | None = None
        filename = filename if filename is not None else file_to_export.get_base_name()
        try:
            if not self.__enableMultiThread and self.__threads != 1:
                raise NotImplementedError("Multithreading is not supported")

            start_time: int = 0
            self.__stopped = False
            if self.__enableLog:
                start_time = int(time.time() * 1000)

            total_bytes_written = [0]
            self.__failed = False

            if not export_dir.exists():
                export_dir.mkdir()
            export_file = export_dir.create_file(filename)
            # we use the drive hash key for integrity verification
            file_to_export.set_verify_integrity(integrity, None)

            file_size: int = file_to_export.get_size()
            part_size: int = file_size
            running_threads: int

            # make sure we allocate enough space for the file
            target_stream: RandomAccessStream = export_file.get_output_stream()
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

            # we use a countdown latch which is better suited with executor than Thread.join.
            done: threading.Barrier = threading.Barrier(running_threads + 1)
            final_part_size: int = part_size
            final_running_threads: int = running_threads
            for i in range(0, running_threads):

                def __export_file(index: int):
                    nonlocal ex, total_bytes_written

                    start: int = final_part_size * index
                    length: int
                    if index == final_running_threads - 1:
                        length = file_size - start
                    else:
                        length = final_part_size
                    try:
                        self.__export_file_part(file_to_export, export_file, start, length, total_bytes_written,
                                                on_progress)
                    except Exception as e:
                        print(e)

                    done.wait()

                self.__executor.submit(__export_file, i)

            done.wait()
            if self.__stopped:
                export_file.delete()
            elif delete_source:
                file_to_export.get_real_file().delete()
            if self.__lastException is not None:
                raise self.__lastException
            if self.__enableLog:
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
        return export_file

    def __export_file_part(self, file_to_export: SalmonFile, export_file: IRealFile, start: int, count: int,
                           total_bytes_written: list[int], on_progress: Callable[[int, int], Any] | None):
        """
         * Export a file part from the drive.
         *
         * @param file_to_export      The file the part belongs to
         * @param export_file        The file to copy the exported part to
         * @param start             The start position on the file
         * @param count             The length of the bytes to be decrypted
         * @param total_bytes_written The total bytes that were written to the external file
        """
        start_time: int = int(time.time() * 1000)
        total_part_bytes_written: int = 0

        target_stream: RandomAccessStream | None = None
        source_stream: RandomAccessStream | None = None

        try:
            target_stream = export_file.get_output_stream()
            target_stream.set_position(start)

            source_stream = file_to_export.get_input_stream()
            source_stream.set_position(start)

            v_bytes: bytearray = bytearray(self.__buffer_size)
            bytes_read: int
            if self.__enableLogDetails:
                print("SalmonFileExporter: FilePart: " + file_to_export.get_real_file().get_base_name() + ": "
                      + file_to_export.get_base_name() + " start = " + str(start) + " count = " + str(count))

            while (bytes_read := source_stream.read(v_bytes, 0,
                                                    min(len(v_bytes), count - total_part_bytes_written))) > 0 \
                    and total_part_bytes_written < count:
                if self.__stopped:
                    break

                target_stream.write(v_bytes, 0, bytes_read)
                total_part_bytes_written += bytes_read

                total_bytes_written[0] += bytes_read
                if on_progress is not None:
                    on_progress(total_bytes_written[0], file_to_export.get_size())

            if SalmonFileExporter.__enableLogDetails:
                total: int = int(time.time() * 1000) - start_time
                print("SalmonFileExporter: File Part: " + file_to_export.get_base_name() + " exported " + str(
                    total_part_bytes_written)
                      + " bytes in: " + str(total) + " ms"
                      + ", avg speed: " + str(total_bytes_written[0] / float(total)) + " bytes/sec")

        except Exception as ex:
            print(ex)
            self.__failed = True
            self.__lastException = ex
            self.stop()
        finally:
            if target_stream is not None:
                target_stream.flush()
                target_stream.close()

            if source_stream is not None:
                source_stream.close()

    def _finalize(self):
        self.close()

    def close(self):
        self.__executor.shutdown(False)
