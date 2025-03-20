# not /usr/bin/env python3
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

from typeguard import typechecked
import sys

from salmon_fs.salmonfs.file.aes_file import AesFile
from salmon_fs.fs.drive.utils.file_utils import FileUtils


@typechecked
class AesFileComparators:
    """
    Useful comparators for AesFile.
    """

    @staticmethod
    def default_comparator(c1: AesFile, c2: AesFile):
        """!
        Defoult comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return 0

    @staticmethod
    def filename_asc_comparator(c1: AesFile, c2: AesFile):
        """!
        File name comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return AesFileComparators.__compare(AesFileComparators.__try_get_basename(c1),
                                                AesFileComparators.__try_get_basename(c2))

    @staticmethod
    def filename_desc_comparator(c1: AesFile, c2: AesFile):
        """!
        File name descending comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return AesFileComparators.__compare(AesFileComparators.__try_get_basename(c2),
                                                AesFileComparators.__try_get_basename(c1))

    @staticmethod
    def size_asc_comparator(c1: AesFile, c2: AesFile):
        """!
        Size comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return AesFileComparators.__try_get_size(c1) - AesFileComparators.__try_get_size(c2)

    @staticmethod
    def size_desc_comparator(c1: AesFile, c2: AesFile):
        """!
        Size descending comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return AesFileComparators.__try_get_size(c2) - AesFileComparators.__try_get_size(c1)

    @staticmethod
    def type_asc_comparator(c1: AesFile, c2: AesFile):
        """!
        Type comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return AesFileComparators.__compare(AesFileComparators.__try_get_type(c1),
                                                AesFileComparators.__try_get_type(c2))

    @staticmethod
    def type_desc_comparator(c1: AesFile, c2: AesFile):
        """!
        Type descending comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return AesFileComparators.__compare(AesFileComparators.__try_get_type(c2),
                                                AesFileComparators.__try_get_type(c1))

    @staticmethod
    def date_asc_comparator(c1: AesFile, c2: AesFile):
        """!
        Date comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return -1
        elif not c1.is_directory() and c2.is_directory():
            return 1
        else:
            return AesFileComparators.__try_get_date(c1) - AesFileComparators.__try_get_date(c2)

    @staticmethod
    def date_desc_comparator(c1: AesFile, c2: AesFile):
        """!
        Date descending comparator
        """
        if c1.is_directory() and not c2.is_directory():
            return 1
        elif not c1.is_directory() and c2.is_directory():
            return -1
        else:
            return AesFileComparators.__try_get_date(c2) - AesFileComparators.__try_get_date(c1)

    @staticmethod
    def relevance_comparator(c1: AesFile, c2: AesFile):
        """!
        Relevance comparator for searching
        """
        return int(str(c2.get_tag())) - int(str(c1.get_tag()))

    @staticmethod
    def __try_get_basename(salmon_file: AesFile) -> str:
        """!
        Get the AesFile basename if available.
        @param salmon_file:
        @returns The base name
        """
        try:
            return salmon_file.get_name()
        except Exception as ex:
            print(ex, file=sys.stderr)

        return ""

    @staticmethod
    def __try_get_type(salmon_file: AesFile) -> str:
        """!
        Get the AesFile file type extension if available.
        @param salmon_file:
        @returns The file type
        """
        try:
            if salmon_file.is_directory():
                return salmon_file.get_name()
            return FileUtils.get_extension_from_file_name(salmon_file.get_name()).lower()
        except Exception as ex:
            print(ex, file=sys.stderr)

        return ""

    @staticmethod
    def __try_get_size(salmon_file: AesFile) -> int:
        """!
        Get the AesFile size if available.
        @param salmon_file:
        @returns The size
        """
        try:
            if salmon_file.is_directory():
                return salmon_file.get_children_count()
            # the original file length requires reading the chunks size
            # from the file so it can get a bit expensive
            # so instead we sort on the real file size
            return salmon_file.get_real_file().get_length()
        except Exception as ex:
            print(ex, file=sys.stderr)

        return 0

    @staticmethod
    def __try_get_date(salmon_file: AesFile) -> int:
        """!
        Get the AesFile date if available.
        @param salmon_file:
        @returns The date
        """
        try:
            return salmon_file.get_last_date_modified()
        except Exception as ex:
            print(ex, file=sys.stderr)

        return 0

    @staticmethod
    def __compare(s1: str, s2: str) -> int:
        if s1 == s2:
            return 0
        elif s1 < s2:
            return -1
        else:
            return 1
