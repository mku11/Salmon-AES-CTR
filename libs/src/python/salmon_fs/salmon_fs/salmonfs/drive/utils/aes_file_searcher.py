#!/usr/bin/env python3
"""!@brief Searches for files in a AesDrive by filename.
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

from enum import Enum
from typing import Callable, Any
from collections import OrderedDict
import sys
from typeguard import typechecked

from salmon_fs.salmonfs.file.aes_file import AesFile


@typechecked
class AesFileSearcher:
    """!
    Searches for files in a AesDrive by filename.
    """

    def __init__(self):
        """!
        Construct a file searcher
        """
        self.__running: bool = False
        self.__quit: bool = False

    class SearchEvent(Enum):
        """!
        Event status types.
        """
        SearchingFiles = 0
        SearchingFinished = 1

    def stop(self):
        """!
        Stop the search
        """
        self.__quit = True

    def is_running(self) -> bool:
        """!
        True if a search is running.
        @returns True if running
        """
        return self.__running

    def is_stopped(self) -> bool:
        """!
        True if last search was stopped by user.
        @returns True if stopped
        """
        return self.__quit

    def search(self, v_dir: AesFile, terms: str, options: AesFileSearcher.SearchOptions | None = None) -> list[AesFile]:
        """!
        Search files in directory and its subdirectories recursively for matching terms.
        @param v_dir: The directory to start the search.
        @param terms: The terms to search for.
        @param options: The options
        @returns An array with all the results found.
        """

        if not options:
            options = AesFileSearcher.SearchOptions()
        self.__running = True
        self.__quit = False
        search_results: OrderedDict[str, AesFile] = {}
        if options.on_search_event:
            options.on_search_event(AesFileSearcher.SearchEvent.SearchingFiles)
        self.__search_dir(v_dir, terms, options.any_term, options.on_result_found, search_results)
        if options.on_search_event:
            options.on_search_event(AesFileSearcher.SearchEvent.SearchingFinished)
        self.__running = False
        return list(search_results.values())

    def __get_search_results(self, filename: str, terms: list[str], any_term: bool) -> int:
        """!
        Match the current terms in the filename.
        @param filename: The filename to match.
        @param terms: The terms to match.
        @param any_term: True if you want to match any term otherwise match all terms.
        @returns A count of all matches.
        """
        count: int = 0
        term: str
        for term in terms:
            try:
                if len(term) > 0 and term.lower() in filename.lower():
                    count += 1
            except Exception as exception:
                print(exception, file=sys.stderr)
        if any or count == len(terms):
            return count
        return 0

    def __search_dir(self, v_dir: AesFile, terms: str, anyterm: bool,
                     on_result_found: Callable[[AesFile], Any] | None,
                     search_results: OrderedDict[str, AesFile]):
        """!
        Search a directory for all filenames matching the terms supplied.
        @param v_dir: The directory to start the search.
        @param terms: The terms to search for.
        @param anyterm: True if you want to match any term otherwise match all terms.
        @param on_result_found: Callback to receive notifications when results found.
        @param search_results: The array to store the search results.
        """
        if self.__quit:
            return
        files: list[AesFile] = v_dir.list_files()
        terms_array: list[str] = terms.split(" ")
        for file in files:
            if self.__quit:
                break
            if file.is_directory():
                self.__search_dir(file, terms, anyterm, on_result_found, search_results)
            else:
                if file.get_real_path() in search_results:
                    continue
                try:
                    hits: int = self.__get_search_results(file.get_name(), terms_array, anyterm)
                    if hits > 0:
                        search_results[file.get_real_path()] = file
                        if on_result_found:
                            on_result_found(file)
                except Exception as ex:
                    print(ex, file=sys.stderr)

    class SearchOptions:
        """!
         Search options
        """

        any_term: bool = False
        """
         True to search for any term, otherwise match all
        """

        on_result_found: Callable[[AesFile], Any] | None = None
        """!
         Callback when result found
        """

        on_search_event: Callable[[AesFileSearcher.SearchEvent], Any] | None = None
        """!
         Callback when search event happens.
        """
