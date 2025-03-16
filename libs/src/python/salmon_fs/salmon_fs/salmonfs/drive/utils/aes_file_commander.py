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

from concurrent.futures import CancelledError
from typing import Callable, Any
from typeguard import typechecked

from salmon_fs.fs.file.ifile import IFile
from salmon_fs.salmonfs.file.aes_file import AesFile
from salmon.sequence.sequence_exception import SequenceException
from salmon_fs.salmonfs.drive.utils.aes_file_exporter import AesFileExporter
from salmon_fs.salmonfs.drive.utils.aes_file_importer import AesFileImporter
from salmon_fs.fs.drive.utils.file_searcher import FileSearcher


@typechecked
class AesFileCommander:
    """
    Facade class for file operations.
    """

    def __init__(self, import_buffer_size: int = 0, export_buffer_size: int = 0, threads: int = 1, multi_cpu: False = False):
        """
        Instantiate a new file commander object.
        
        :param import_buffer_size: The buffer size to use for importing files.
        :param export_buffer_size: The buffer size to use for exporting files.
        :multi_cpu:  Utilize multiple cpus. Windows does not have a fast fork() so it has a very slow startup
        """
        self.__fileImporter: AesFileImporter
        self.__fileExporter: AesFileExporter
        self.__fileSearcher: FileSearcher
        self.__stopJobs: bool = False

        self.__fileImporter = AesFileImporter(import_buffer_size, threads, multi_cpu)
        self.__fileExporter = AesFileExporter(export_buffer_size, threads, multi_cpu)
        self.__fileSearcher = FileSearcher()

    def get_file_importer(self):
        return self.__fileImporter

    def get_file_exporter(self):
        return self.__fileExporter

    def import_files(self, files_to_import: list[IFile], import_dir: AesFile,
                     delete_source: bool, integrity: bool,
                     auto_rename: Callable[[IFile], str] | None = None,
                     on_failed: Callable[[IFile, Exception], Any] | None = None,
                     on_progress_changed: Callable[[AesFileCommander.RealFileTaskProgress], Any] | None = None) -> list[
        AesFile]:
        """
        Import files to the drive.
        
        :param files_to_import:     The files to import.
        :param import_dir:         The target directory.
        :param delete_source:      True if you want to delete the source files when import complete.
        :param integrity:         True to apply integrity to imported files.
        :param on_progress_changed: Observer to notify when progress changes.
        :param auto_rename:        Function to rename file if another file with the same filename exists
        :param on_failed:          Observer to notify when a file fails importing
        :return: The imported files if completes successfully.
        :raises Exception:         """
        self.__stopJobs = False
        imported_files: list[AesFile] = []

        total: list[int] = [0]
        for i in range(0, len(files_to_import)):
            if self.__stopJobs:
                break
            total[0] += self.__get_real_files_count_recursively(files_to_import[i])
        count: list[int] = [1]
        existing_files: dict[str, AesFile] = self.__get_existing_salmon_files(import_dir)
        for i in range(0, len(files_to_import)):
            if self.__stopJobs:
                break
            self.__import_recursively(files_to_import[i], import_dir,
                                      delete_source, integrity,
                                      on_progress_changed, auto_rename, on_failed,
                                      imported_files, count, total,
                                      existing_files)

        return imported_files

    def __get_existing_salmon_files(self, import_dir: AesFile) -> dict[str, AesFile]:
        files: dict[str, AesFile] = {}
        for file in import_dir.list_files():
            if self.__stopJobs:
                break
            try:
                files[file.get_name()] = file
            except Exception as ignored:
                pass

        return files

    def __import_recursively(self, file_to_import: IFile, import_dir: AesFile,
                             delete_source: bool, integrity: bool,
                             on_progress_changed: Callable[[AesFileCommander.RealFileTaskProgress], Any] | None,
                             auto_rename: Callable[[IFile], str] | None,
                             on_failed: Callable[[IFile, Exception], Any] | None,
                             imported_files: list[AesFile], count: list[int], total: list[int],
                             existing_files: dict[str, AesFile]):
        sfile: AesFile | None = existing_files.get(
            file_to_import.get_name()) if file_to_import.get_name() in existing_files else None
        if file_to_import.is_directory():
            if on_progress_changed is not None:
                on_progress_changed(AesFileCommander.RealFileTaskProgress(file_to_import, 0, 1, count[0], total[0]))
            if sfile is None or not sfile.exists():
                sfile = import_dir.create_directory(file_to_import.get_name())
            elif sfile is not None and sfile.exists() and sfile.is_file() and auto_rename is not None:
                sfile = import_dir.create_directory(auto_rename(file_to_import))
            if on_progress_changed is not None:
                on_progress_changed(AesFileCommander.RealFileTaskProgress(file_to_import, 1, 1, count[0], total[0]))
            count[0] += 1
            n_existing_files: dict[str, AesFile] = self.__get_existing_salmon_files(sfile)
            for child in file_to_import.list_files():
                if self.__stopJobs:
                    break
                self.__import_recursively(child, sfile, delete_source, integrity, on_progress_changed,
                                          auto_rename, on_failed, imported_files, count, total,
                                          n_existing_files)
            if delete_source and not self.__stopJobs:
                file_to_import.delete()
        else:
            try:
                filename: str = file_to_import.get_name()
                if sfile is not None and (sfile.exists() or sfile.is_directory()) and auto_rename is not None:
                    filename = auto_rename(file_to_import)
                sfile = self.__fileImporter.import_file(file_to_import, import_dir, filename, delete_source, integrity,
                                                        lambda v_bytes, total_bytes2:
                                                        self.__notify_real_file_progress(file_to_import,
                                                                                         v_bytes,
                                                                                         total_bytes2,
                                                                                         count, total[0],
                                                                                         on_progress_changed))
                existing_files[sfile.get_name()] = sfile
                imported_files.append(sfile)
                count[0] += 1
            except SequenceException as ex:
                raise ex
            except Exception as ex:
                if on_failed is not None:
                    on_failed(file_to_import, ex)

    def export_files(self, files_to_export: list[AesFile], export_dir: IFile,
                     delete_source: bool, integrity: bool,
                     auto_rename: Callable[[IFile], str] | None = None,
                     on_failed: Callable[[AesFile, Exception], Any] | None = None,
                     on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any] | None = None) \
            -> list[IFile]:
        """
        Export a file from a drive.
        
        :param files_to_export:     The files to export.
        :param export_dir:         The export target directory
        :param delete_source:      True if you want to delete the source files
        :param integrity:         True to use integrity verification before exporting files
        :param on_progress_changed: Observer to notify when progress changes.
        :param auto_rename:        Function to rename file if another file with the same filename exists
        :param on_failed:          Observer to notify when a file fails exporting
        :return: The exported files
        :raises Exception:         """
        stop_jobs = False
        exported_files: list[IFile] = []

        total: int = 0
        for i in range(0, len(files_to_export)):
            if self.__stopJobs:
                break
            total += self.__get_salmon_files_count_recursively(files_to_export[i])

        existing_files: dict[str, IFile] = self.__get_existing_real_files(export_dir)

        count: list[int] = [1]
        for i in range(0, len(files_to_export)):
            if stop_jobs:
                break
            self.__export_recursively(files_to_export[i], export_dir,
                                      delete_source, integrity,
                                      on_progress_changed, auto_rename, on_failed,
                                      exported_files, count, total,
                                      existing_files)

        return exported_files

    def __get_existing_real_files(self, export_dir: IFile) -> dict[str, IFile]:
        files: dict[str, IFile] = {}
        for file in export_dir.list_files():
            files[file.get_name()] = file

        return files

    def __export_recursively(self, file_to_export: AesFile, export_dir: IFile,
                             delete_source: bool, integrity: bool,
                             on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any] | None,
                             auto_rename: Callable[[IFile], str] | None,
                             on_failed: Callable[[AesFile, Exception], Any] | None,
                             exported_files: list[IFile], count: list[int], total: int,
                             existing_files: dict[str, IFile]):
        rfile: IFile | None = existing_files.get(
            file_to_export.get_name()) if file_to_export.get_name() in existing_files else None

        if file_to_export.is_directory():
            if rfile is None or not rfile.exists():
                rfile = export_dir.create_directory(file_to_export.get_name())
            elif rfile is not None and rfile.is_file() and auto_rename is not None:
                rfile = export_dir.create_directory(auto_rename(rfile))
            if on_progress_changed is not None:
                on_progress_changed(AesFileCommander.AesFileTaskProgress(file_to_export, 1, 1, count[0], total))
            count[0] += 1
            n_existing_files: dict[str, IFile] = self.__get_existing_real_files(rfile)
            for child in file_to_export.list_files():
                if self.__stopJobs:
                    break
                self.__export_recursively(child, rfile, delete_source, integrity, on_progress_changed,
                                          auto_rename, on_failed, exported_files, count, total,
                                          n_existing_files)
            if delete_source and not self.__stopJobs:
                file_to_export.delete()
        else:
            try:
                filename: str = file_to_export.get_name()
                if rfile is not None and rfile.exists() and auto_rename is not None:
                    filename = auto_rename(rfile)
                rfile = self.__fileExporter.export_file(file_to_export, export_dir, filename, delete_source, integrity,
                                                        lambda v_bytes, total_bytes: self.__notify_salmon_file_progress(
                                                            file_to_export, v_bytes, total_bytes, count, total,
                                                            on_progress_changed))
                existing_files[rfile.get_name()] = rfile
                exported_files.append(rfile)
                count[0] += 1
            except SequenceException as ex:
                raise ex
            except Exception as ex:
                if on_failed is not None:
                    on_failed(file_to_export, ex)

    def __get_salmon_files_count_recursively(self, file: AesFile) -> int:
        count: int = 1
        if file.is_directory():
            for child in file.list_files():
                if self.__stopJobs:
                    break
                count += self.__get_salmon_files_count_recursively(child)
        return count

    def __get_real_files_count_recursively(self, file: IFile) -> int:
        count: int = 1
        if file.is_directory():
            for child in file.list_files():
                if self.__stopJobs:
                    break
                count += self.__get_real_files_count_recursively(child)

        return count

    def delete_files(self, files_to_delete: list[AesFile],
                     on_failed: Callable[[AesFile, Exception], Any] | None = None,
                     on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any] | None = None):
        """
        Delete files.
        
        :param files_to_delete:         The files to delete.
        :param on_progress_changed: The observer to notify when each file is deleted.
        :param on_failed: The observer to notify when a file has failed.
        """
        self.__stopJobs = False
        count: list[int] = [1]
        total: int = 0
        for i in range(0, len(files_to_delete)):
            if self.__stopJobs:
                break
            total += self.__get_salmon_files_count_recursively(files_to_delete[i])
        for aes_file in files_to_delete:
            if self.__stopJobs:
                break
            final_total: int = total
            aes_file.delete_recursively(
                lambda file, position, length: self.__notify_delete_progress(file, position, length, count, final_total,
                                                                             on_progress_changed), on_failed)

    def __notify_delete_progress(self, file: AesFile, position: int, length: int, count: list[int], final_total: int,
                                 on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any] | None):
        if self.__stopJobs:
            raise CancelledError()
        if on_progress_changed is not None:
            try:
                on_progress_changed(
                    AesFileCommander.AesFileTaskProgress(file, position, length, count[0], final_total))

            except Exception as ex:
                if position == length:
                    count[0] += 1

    def copy_files(self, files_to_copy: list[AesFile], v_dir: AesFile, move: bool,
                   auto_rename: Callable[[AesFile, str], Any] | None = None,
                   auto_rename_folders: bool = False,
                   on_failed: Callable[[AesFile, Exception], Any] | None = None,
                   on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any] | None = None):
        """
        Copy files to another directory.
        
        :param files_to_copy:       The array of files to copy.
        :param v_dir:               The target directory.
        :param move:              True if moving files instead of copying.
        :param on_progress_changed: The progress change observer to notify.
        :param auto_rename:        The auto rename function to use when files with same filename are found
        :param auto_rename_folders: True to auto rename folders
        :param on_failed:          The observer to notify when failures occur
        :raises Exception:         """
        self.__stopJobs = False
        count: list[int] = [1]
        total: int = 0
        for i in range(0, len(files_to_copy)):
            if self.__stopJobs:
                break
            total += self.__get_salmon_files_count_recursively(files_to_copy[i])
        final_total: int = total
        for aes_file in files_to_copy:
            if self.__stopJobs:
                break
            if v_dir.get_real_file().get_path().startswith(aes_file.get_real_file().get_path()):
                continue

            if self.__stopJobs:
                break

            if move:
                aes_file.move_recursively(v_dir,
                                          lambda file, position, length:
                                          self.__notify_move_progress(file, position,
                                                                      length, count,
                                                                      final_total,
                                                                      on_progress_changed),
                                          auto_rename, auto_rename_folders, on_failed)
            else:
                AesFile.copy_recursively(v_dir,
                                         lambda file, position, length:
                                         self.__notify_copy_progress(file, position,
                                                                     length, count,
                                                                     final_total,
                                                                     on_progress_changed),
                                         auto_rename, auto_rename_folders, on_failed)

    def cancel(self):
        """
        Cancel all jobs.
        """
        self.__stopJobs = True
        self.__fileImporter.stop()
        self.__fileExporter.stop()
        self.__fileSearcher.stop()

    def is_file_searcher_running(self) -> bool:
        """
        True if the file search is currently running.
        
        :return: True if search running
        """
        return self.__fileSearcher.is_running()

    def is_running(self) -> bool:
        """
        True if jobs are currently running.
        
        :return: True if running
        """
        return self.__fileSearcher.is_running() or self.__fileImporter.is_running() or self.__fileExporter.is_running()

    def is_file_searcher_stopped(self) -> bool:
        """
        True if file search stopped.
        
        :return: True if search stopped
        """
        return self.__fileSearcher.is_stopped()

    def stop_file_search(self):
        """
        Stop file search.
        """
        self.__fileSearcher.stop()

    def search(self, v_dir: AesFile, terms: str, any_term: bool,
               on_result_found: FileSearcher.OnResultFoundListener,
               on_search_event: Callable[[FileSearcher.SearchEvent], Any]) -> [AesFile]:
        """
        Search
        
        :param v_dir:           The directory to start the search.
        :param terms:         The terms to search for.
        :param any_term:           True if you want to match any term otherwise match all terms.
        :param on_result_found: Callback interface to receive notifications when results found.
        :param on_search_event: Callback interface to receive status events.
        :return: An array with all the results found.
        """

        return self.__fileSearcher.search(v_dir, terms, any_term, on_result_found, on_search_event)

    def are_jobs_stopped(self) -> bool:
        """
        True if all jobs are stopped.
        
        :return: True if stopped
        """
        return self.__stopJobs

    def __get_files(self, files: list[AesFile]) -> int:
        """
        Get number of files recursively for the files provided.
        
        :param files: Total number of files and files under subdirectories.
        :return: The files
        """
        total: int = 0
        for file in files:
            if self.__stopJobs:
                break
            total += 1
            if file.is_directory():
                total += self.__get_files(file.list_files())

        return total

    def close(self):
        self.__fileImporter.close()
        self.__fileExporter.close()

    def rename_file(self, ifile: AesFile, new_filename: str):
        """
        Rename an encrypted file
        
        """
        ifile.rename(new_filename)

    class FileTaskProgress:
        """
        File task progress class.
        """

        def get_total_bytes(self) -> int:
            return self.__totalBytes

        def get_processed_bytes(self) -> int:
            return self.__processedBytes

        def get_processed_files(self) -> int:
            return self.__processedFiles

        def get_total_files(self) -> int:
            return self.__totalFiles

        def __init__(self, processed_bytes: int, total_bytes: int, processed_files: int, total_files: int):
            self.__processedBytes: int = processed_bytes
            self.__totalBytes = total_bytes
            self.__processedFiles = processed_files
            self.__totalFiles = total_files

    class AesFileTaskProgress(FileTaskProgress):
        def get_file(self) -> AesFile:
            return self.__file

        def __init__(self, file: AesFile, processed_bytes: int, total_bytes: int,
                     processed_files: int, total_files: int):
            super().__init__(processed_bytes, total_bytes, processed_files, total_files)
            self.__file: AesFile | None = None
            self.__file = file

    class RealFileTaskProgress(FileTaskProgress):
        def get_file(self) -> IFile:
            return self.__file

        def __init__(self, file: IFile, processed_bytes: int, total_bytes: int,
                     processed_files: int, total_files: int):
            super().__init__(processed_bytes, total_bytes, processed_files, total_files)
            self.__file: IFile | None = None
            self.__file = file

    def __notify_real_file_progress(self, file_to_import: IFile, v_bytes: int, total_bytes: int, count: list[int],
                                    total: int,
                                    on_progress_changed: Callable[[AesFileCommander.RealFileTaskProgress], Any] | None):
        if on_progress_changed is not None:
            on_progress_changed(
                AesFileCommander.RealFileTaskProgress(file_to_import, v_bytes, total_bytes, count[0], total))

    def __notify_salmon_file_progress(self, file_to_export: AesFile, v_bytes: int, total_bytes: int,
                                      count: list[int],
                                      total: int,
                                      on_progress_changed: Callable[
                                                               [AesFileCommander.AesFileTaskProgress], Any] | None):
        if on_progress_changed is not None:
            on_progress_changed(
                AesFileCommander.AesFileTaskProgress(file_to_export, v_bytes, total_bytes, count[0], total))

    def __notify_copy_progress(self, file: AesFile, position: int, length: int, count: list[int], final_total: int,
                               on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any]):

        if self.__stopJobs:
            raise CancelledError()
        if on_progress_changed is not None:
            try:
                on_progress_changed(
                    AesFileCommander.AesFileTaskProgress(file, position, length, count[0], final_total))
            except Exception as ignored:
                pass
        if position == length:
            count[0] += 1

    def __notify_move_progress(self, file: AesFile, position: int, length: int, count: list[int], final_total: int,
                               on_progress_changed: Callable[[AesFileCommander.AesFileTaskProgress], Any]):
        if self.__stopJobs:
            raise CancelledError()
        if on_progress_changed is not None:
            try:
                on_progress_changed(
                    AesFileCommander.AesFileTaskProgress(file, position, length, count[0], final_total))
            except Exception as ex:
                pass
        if position == length:
            count[0] += 1
