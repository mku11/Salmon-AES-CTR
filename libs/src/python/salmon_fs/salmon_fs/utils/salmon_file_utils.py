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

from typeguard import typechecked

from salmon_fs.salmonfs.salmon_file import SalmonFile


@typechecked
class SalmonFileUtils:
    @staticmethod
    def is_text(filename: str) -> bool:
        """
         * Detect if filename is a text file.
         * @param filename The filename.
         * @return True if text file.
        """
        ext: str = SalmonFileUtils.get_extension_from_file_name(filename).lower()
        return ext == "txt"

    @staticmethod
    def is_image(filename: str) -> bool:
        """
         * Detect if filename is an image file.
         * @param filename The filename.
         * @return True if image file.
        """
        ext: str = SalmonFileUtils.get_extension_from_file_name(filename).lower()
        return ext == "png" or ext == "jpg" or ext == "jpeg" or ext == "bmp" or ext == "webp" or ext == "gif" \
            or ext == "tif" or ext == "tiff"

    @staticmethod
    def is_audio(filename: str) -> bool:
        """
         * Detect if filename is an audio file.
         * @param filename The filename.
         * @return True if audio file.
        """
        ext: str = SalmonFileUtils.get_extension_from_file_name(filename).lower()
        return ext == "wav" or ext == "mp3"

    @staticmethod
    def is_video(filename: str) -> bool:
        """
         * Detect if filename is a video file.
         * @param filename The filename.
         * @return True if video file.
        """
        ext: str = SalmonFileUtils.get_extension_from_file_name(filename).lower()
        return ext == "mp4"

    @staticmethod
    def is_pdf(filename: str) -> bool:
        """
         * Detect if filename is a pdf file.
         * @param filename
         * @return
        """
        ext: str = SalmonFileUtils.get_extension_from_file_name(filename).lower()
        return ext == "pdf"

    @staticmethod
    def get_extension_from_file_name(file_name: str) -> str:
        """
         * Return the extension of a filename.
         *
         * @param file_name
        """
        if file_name is None:
            return ""
        index: int = file_name.rindex(".")
        if index >= 0:
            return file_name[index + 1]
        else:
            return ""

    @staticmethod
    def get_file_name_without_extension(file_name: str) -> str:
        """
         * Return a filename without extension
         *
         * @param file_name
        """
        if file_name is None:
            return ""
        index: int = file_name.rindex(".")
        if index >= 0:
            return file_name[0: index]
        else:
            return ""

    @staticmethod
    def get_minimum_part_size(file: SalmonFile) -> int:
        curr_chunk_size = file.get_file_chunk_size()
        if curr_chunk_size is not None and curr_chunk_size != 0:
            return curr_chunk_size
        if file.get_requested_chunk_size() is not None and file.get_requested_chunk_size() != 0:
            return file.get_requested_chunk_size()
        return file.get_block_size()
