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

from abc import ABC
from enum import Enum
from typing import Callable, Any

from typeguard import typechecked

from salmonfs.salmon_file import SalmonFile


@typechecked
class SalmonFileSearcher:
    """
     * Class searches for files in a SalmonDrive by filename.
    """

    def __init__(self):
        self.__running: bool = False
        self.__quit: bool = False

    class SearchEvent(Enum):
        """
         * Event status types.
        """
        SearchingFiles = 0
        SearchingFinished = 1

    def stop(self):
        self.__quit = True

    class OnResultFoundListener(ABC):
        """
         * Functional interface to notify when result is found.
        """

        def on_result_found(self, search_result: SalmonFile):
            """
             * Fired when search result found.
             * @param search_result The search result.
            """
            pass

    def is_running(self) -> bool:
        """
         * True if a search is running.
         * @return
        """
        return self.__running

    def is_stopped(self) -> bool:
        """
         * True if last search was stopped by user.
         * @return
        """
        return self.__quit

    def search(self, v_dir: SalmonFile, terms: str, any_term: bool,
               on_result_found: OnResultFoundListener = None,
               on_search_event: Callable[[SearchEvent], Any] = None) -> [SalmonFile]:
        """
         * Search files in directory and its subdirectories recursively for matching terms.
         * @param dir The directory to start the search.
         * @param terms The terms to search for.
         * @param any True if you want to match any term otherwise match all terms.
         * @param on_result_found Callback interface to receive notifications when results found.
         * @param on_search_event Callback interface to receive status events.
         * @return An array with all the results found.
        """
        self.__running = True
        self.__quit = False
        search_results: {str, SalmonFile} = {}
        if on_search_event is not None:
            on_search_event(SalmonFileSearcher.SearchEvent.SearchingFiles)
        self.__search_dir(v_dir, terms, any_term, on_result_found, search_results)
        if on_search_event is not None:
            on_search_event(SalmonFileSearcher.SearchEvent.SearchingFinished)
        self.__running = False
        return list(search_results.values())

    def __get_search_results(self, filename: str, terms: [str], any_term: bool) -> int:
        """
         * Match the current terms in the filename.
         * @param filename The filename to match.
         * @param terms The terms to match.
         * @param any True if you want to match any term otherwise match all terms.
         * @return A count of all matches.
        """
        count: int = 0
        term: str
        for term in terms:
            try:
                if len(term) > 0 and term.lower() in filename.lower():
                    count += 1
            except Exception as exception:
                print(exception)
        if any or count == len(terms):
            return count
        return 0

    def __search_dir(self, v_dir: SalmonFile, terms: str, anyterm: bool, on_result_found: OnResultFoundListener,
                     search_results: {str, SalmonFile}):
        """
         * Search a directory for all filenames matching the terms supplied.
         * @param dir The directory to start the search.
         * @param terms The terms to search for.
         * @param any True if you want to match any term otherwise match all terms.
         * @param on_result_found Callback interface to receive notifications when results found.
         * @param search_results The array to store the search results.
        """
        if self.__quit:
            return
        files: [SalmonFile] = v_dir.list_files()
        terms_array: [str] = terms.split(" ")
        for file in files:
            if self.__quit:
                break
            if file.is_directory():
                self.__search_dir(file, terms, anyterm, on_result_found, search_results)
            else:
                if file.get_real_path() in search_results:
                    continue
                try:
                    hits: int = self.__get_search_results(file.get_base_name(), terms_array, anyterm)
                    if hits > 0:
                        search_results[file.get_real_path()] = file
                        if on_result_found is not None:
                            on_result_found.on_result_found(file)
                except Exception as ex:
                    print(ex)
