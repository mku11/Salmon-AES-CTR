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
from convert.bit_converter import BitConverter
from iostream.random_access_stream import RandomAccessStream
from salmon.salmon_generator import SalmonGenerator

from typeguard import typechecked


@typechecked
class SalmonHeader:
    """
     * Header embedded in the SalmonStream. Header contains nonce and other information for
     * decrypting the stream.
    """

    def __init__(self):
        self.__magicBytes: bytearray = bytearray(SalmonGenerator.MAGIC_LENGTH)
        """
         * Magic bytes.
        """

        self.__version: int = 0
        """
         * Format version from {@link SalmonGenerator#VERSION}.
        """

        self.__chunkSize: int = 0
        """
         * Chunk size used for data integrity.
        """

        self.__nonce: bytearray = bytearray()
        """
         * Starting nonce for the CTR mode. This is the upper part of the Counter.
         *
        """

        self.__headerData: bytearray = bytearray()
        """
         * Binary data.
        """

    def get_nonce(self) -> bytearray:
        """
         * Get the nonce.
         * @return The nonce
        """
        return self.__nonce

    def get_chunk_size(self) -> int:
        """
         * Get the chunk size.
         * @return Chunk size
        """
        return self.__chunkSize

    def get_header_data(self) -> bytearray:
        """
         * Get the raw header data.
         * @return Header data
        """
        return self.__headerData

    def get_version(self) -> int:
        """
         * Get the Salmon format  version
         * @return The format version
        """
        return self.__version

    def get_magic_bytes(self) -> bytearray:
        """
         * Get the magic bytes
         * @return Magic bytes
        """
        return self.__magicBytes

    @staticmethod
    def parse_header_data(stream: RandomAccessStream) -> SalmonHeader:
        """
         * Parse the header data from the stream
         * @param stream The stream.
         * @return
         * @throws IOException
        """
        header: SalmonHeader = SalmonHeader()
        header.__magicBytes = bytearray(SalmonGenerator.MAGIC_LENGTH)
        stream.read(header.__magicBytes, 0, len(header.__magicBytes))
        version_bytes: bytearray = bytearray(SalmonGenerator.VERSION_LENGTH)
        stream.read(version_bytes, 0, SalmonGenerator.VERSION_LENGTH)
        header.__version = version_bytes[0]
        chunk_size_header: bytearray = bytearray(SalmonGenerator.CHUNK_SIZE_LENGTH)
        stream.read(chunk_size_header, 0, len(chunk_size_header))
        header.__chunkSize = BitConverter.to_long(chunk_size_header, 0, SalmonGenerator.CHUNK_SIZE_LENGTH)
        header.__nonce = bytearray(SalmonGenerator.NONCE_LENGTH)
        stream.read(header.__nonce, 0, len(header.__nonce))
        stream.set_position(0)
        header.__headerData = bytearray(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH
                                      + SalmonGenerator.CHUNK_SIZE_LENGTH + SalmonGenerator.NONCE_LENGTH)
        stream.read(header.__headerData, 0, len(header.__headerData))
        return header

    """
     * Set the header chunk size
     * @param chunkSize The chunk size
    """

    def set_chunk_size(self, chunk_size: int):
        self.__chunkSize = chunk_size

    def set_version(self, version: int):
        """
         * Set the Salmon format version
         * @param version The format version
        """
        self.__version = version

    def set_nonce(self, nonce: bytearray):
        """
         * Set the nonce to be used.
         * @param nonce The nonce
        """
        self.__nonce = nonce
