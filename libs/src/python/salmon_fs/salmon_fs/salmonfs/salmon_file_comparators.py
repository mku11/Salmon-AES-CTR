# not /usr/bin/env python3
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

from typeguard import typechecked

from salmon_fs.salmonfs.salmon_file import SalmonFile
from salmon_fs.utils.salmon_file_utils import SalmonFileUtils


@typechecked
class SalmonFileComparators:
    """
     * Useful comparators for SalmonFile.
    """

    @staticmethod
    def default_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return 0

    @staticmethod
    def filename_asc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return SalmonFileComparators.__compare(SalmonFileComparators.__try_get_basename(c1),
                                                   SalmonFileComparators.__try_get_basename(c2))

    @staticmethod
    def filename_desc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return SalmonFileComparators.__compare(SalmonFileComparators.__try_get_basename(c2),
                                                   SalmonFileComparators.__try_get_basename(c1))

    @staticmethod
    def size_asc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return SalmonFileComparators.__try_get_size(c1) - SalmonFileComparators.__try_get_size(c2)

    @staticmethod
    def size_desc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return SalmonFileComparators.__try_get_size(c2) - SalmonFileComparators.__try_get_size(c1)

    @staticmethod
    def type_asc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return SalmonFileComparators.__compare(SalmonFileComparators.__try_get_type(c1),
                                                   SalmonFileComparators.__try_get_type(c2))

    @staticmethod
    def type_desc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return SalmonFileComparators.__compare(SalmonFileComparators.__try_get_type(c2),
                                                   SalmonFileComparators.__try_get_type(c1))

    @staticmethod
    def date_asc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return SalmonFileComparators.__try_get_date(c1) - SalmonFileComparators.__try_get_date(c2)

    @staticmethod
    def date_desc_comparator(c1: SalmonFile, c2: SalmonFile):
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return SalmonFileComparators.__try_get_date(c2) - SalmonFileComparators.__try_get_date(c1)

    @staticmethod
    def relevance_comparator(c1: SalmonFile, c2: SalmonFile):
        return int(str(c2.get_tag())) - int(str(c1.get_tag()))

    @staticmethod
    def __try_get_basename(salmon_file: SalmonFile) -> str:
        """
         * Get the SalmonFile basename if available.
         * @param salmon_file
         * @return
        """
        try:
            return salmon_file.get_base_name()
        except Exception as ex:
            print(ex)

        return ""

    @staticmethod
    def __try_get_type(salmon_file: SalmonFile) -> str:
        """
         * Get the SalmonFile file type extension if available.
         * @param salmon_file
         * @return
        """
        try:
            if salmon_file.is_directory():
                return salmon_file.get_base_name()
            return SalmonFileUtils.get_extension_from_file_name(salmon_file.get_base_name()).lower()
        except Exception as ex:
            print(ex)

        return ""

    @staticmethod
    def __try_get_size(salmon_file: SalmonFile) -> int:
        """
         * Get the SalmonFile size if available.
         * @param salmon_file
         * @return
        """
        try:
            if salmon_file.is_directory():
                return salmon_file.get_children_count()
            # the original file length requires reading the chunks size
            # from the file so it can get a bit expensive
            # so instead we sort on the real file size
            return salmon_file.get_real_file().length()
        except Exception as ex:
            print(ex)

        return 0

    @staticmethod
    def __try_get_date(salmon_file: SalmonFile) -> int:
        """
         * Get the SalmonFile date if available.
         * @param salmon_file
         * @return
        """
        try:
            return salmon_file.get_last_date_time_modified()
        except Exception as ex:
            print(ex)

        return 0

    @staticmethod
    def __compare(s1: str, s2: str) -> int:
        if s1 == s2:
            return 0
        elif s1 < s2:
            return -1
        else:
            return 1
