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
class SalmonFileImporter:
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
        """
         * Constructs a file importer that can be used to import files to the drive
         *
         * @param buffer_size Buffer size to be used when encrypting files.
         *                   If using integrity self value has to be a multiple of the Chunk size.
         *                   If not using integrity it should be a multiple of the AES block size for better performance
         * @param threads
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

        self.__buffer_size = buffer_size
        if self.__buffer_size == 0:
            self.__buffer_size = SalmonFileImporter.__DEFAULT_BUFFER_SIZE
        self.__threads = threads
        if self.__threads == 0:
            self.__threads = SalmonFileImporter.__DEFAULT_THREADS

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
        total_bytes_read = [0]
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
            running_threads: int
            part_size: int = file_size

            # make sure we allocate enough space for the file
            target_stream: SalmonStream = salmon_file.get_output_stream()
            target_stream.set_length(file_size)
            target_stream.close()

            if file_size > SalmonFileImporter.__MIN_FILE_SIZE:
                part_size = math.ceil(file_size / float(self.__threads))
                # if we want to check integrity we align to the chunk size instead of the AES Block
                minimum_part_size: int = SalmonFileUtils.get_minimum_part_size(salmon_file)
                rem: int = part_size % minimum_part_size
                if rem != 0:
                    part_size += minimum_part_size - rem

                running_threads = math.ceil(file_size / float(part_size))
            else:
                running_threads = 1

            if running_threads == 0:
                running_threads = 1

            # we use a countdown latch which is better suited with executor than Thread.join.
            done: threading.Barrier = threading.Barrier(running_threads + 1)
            final_part_size: int = part_size
            final_running_threads: int = running_threads
            for i in range(0, running_threads):
                index: int = i

                def __import_file():
                    nonlocal ex, index, total_bytes_read

                    start: int = final_part_size * index
                    length: int
                    if index == final_running_threads - 1:
                        length = file_size - start
                    else:
                        length = final_part_size
                    try:
                        self.__import_file_part(file_to_import, salmon_file, start, length, total_bytes_read,
                                                on_progress)
                    except Exception as e:
                        print(e)

                    done.wait()

                self.__executor.submit(lambda: __import_file())

            done.wait()
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
                    + " File: " + file_to_import.get_base_name()
                    + " imported and signed " + str(total_bytes_read[0]) + " bytes in total time: " + str(total) + " ms"
                    + ", avg speed: " + str(total_bytes_read[0] / float(total)) + " bytes/sec")

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

    def __import_file_part(self, file_to_import: IRealFile, salmon_file: SalmonFile, start: int, count: int,
                           total_bytes_read: list[int], on_progress: Callable[[int, int], Any] | None):
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
        start_time: int = int(time.time() * 1000)
        total_part_bytes_read: int = 0

        target_stream: SalmonStream | None = None
        source_stream: RandomAccessStream | None = None

        try:
            target_stream = salmon_file.get_output_stream()
            target_stream.set_position(start)

            source_stream = file_to_import.get_input_stream()
            source_stream.set_position(start)

            v_bytes: bytearray = bytearray(self.__buffer_size)
            bytes_read: int
            if SalmonFileImporter.__enableLogDetails:
                print(
                    "SalmonFileImporter: FilePart: " + salmon_file.get_real_file().get_base_name()
                    + ": " + salmon_file.get_base_name()
                    + " start = " + str(start) + " count = " + str(count))

            while (bytes_read := source_stream.read(
                    v_bytes, 0,
                    min(len(v_bytes), count - total_part_bytes_read))) > 0 and total_part_bytes_read < count:
                if self.__stopped:
                    break

                target_stream.write(v_bytes, 0, bytes_read)
                total_part_bytes_read += bytes_read

                total_bytes_read[0] += bytes_read
                if on_progress is not None:
                    on_progress(total_bytes_read[0], file_to_import.length())

            if SalmonFileImporter.__enableLogDetails:
                total: int = int(time.time() * 1000) - start_time
                print("SalmonFileImporter: File Part: " + file_to_import.get_base_name()
                      + " imported " + str(total_part_bytes_read) + " bytes in: " + str(total) + " ms"
                      + ", avg speed: " + str(total_part_bytes_read / float(total)) + " bytes/sec")

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
        self.__executor.shutdown()
