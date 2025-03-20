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

from typeguard import typechecked

from salmon_core.convert.bit_converter import BitConverter
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.salmon.generator import Generator


@typechecked
class Header:
    """
    Header embedded in the AesStream. Header contains nonce and other information for
    decrypting the stream.
    """

    HEADER_LENGTH: int = 16
    """
    Header length
    """

    def __init__(self, header_data: bytearray):
        self.__magic_bytes: bytearray = bytearray(Generator.MAGIC_LENGTH)
        """
        Magic bytes.
        """

        self.__version: int = 0
        """
        Format version from {@link DriveGenerator#VERSION}.
        """

        self.__chunk_size: int = 0
        """
        Chunk size used for data integrity.
        """

        self.__nonce: bytearray = bytearray()
        """
        Starting nonce for the CTR mode. This is the upper part of the Counter.
        
        """

        self.__header_data: bytearray = header_data
        """
        Binary data.
        """

    def get_nonce(self) -> bytearray:
        """!
        Get the nonce.
        @returns The nonce
        """
        return self.__nonce

    def get_chunk_size(self) -> int:
        """!
        Get the chunk size.
        @returns Chunk size
        """
        return self.__chunk_size

    def get_header_data(self) -> bytearray:
        """!
        Get the raw header data.
        @returns Header data
        """
        return self.__header_data

    def get_version(self) -> int:
        """!
        Get the Salmon format  version
        @returns The format version
        """
        return self.__version

    def get_magic_bytes(self) -> bytearray:
        """!
        Get the magic bytes
        @returns Magic bytes
        """
        return self.__magic_bytes

    @staticmethod
    def read_header_data(stream: RandomAccessStream) -> Header | None:
        """!
        Parse the header data from the stream
        @param stream: The stream.
        @returns The header
        @exception IOError: Thrown if there is an IO error.
        """
        if stream.get_length() == 0:
            return None
        pos: int = stream.get_position()

        stream.set_position(0)

        header_data: bytearray = bytearray(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                                           + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH)
        stream.read(header_data, 0, len(header_data))

        header: Header = Header(header_data)
        ms: MemoryStream = MemoryStream(header_data)
        header.__magic_bytes = bytearray(Generator.MAGIC_LENGTH)
        ms.read(header.__magic_bytes, 0, len(header.__magic_bytes))
        version_bytes: bytearray = bytearray(Generator.VERSION_LENGTH)
        ms.read(version_bytes, 0, Generator.VERSION_LENGTH)
        header.__version = version_bytes[0]
        chunk_size_header: bytearray = bytearray(Generator.CHUNK_SIZE_LENGTH)
        ms.read(chunk_size_header, 0, len(chunk_size_header))
        header.__chunk_size = BitConverter.to_long(chunk_size_header, 0, Generator.CHUNK_SIZE_LENGTH)
        header.__nonce = bytearray(Generator.NONCE_LENGTH)
        ms.read(header.__nonce, 0, len(header.__nonce))

        stream.set_position(0)
        return header

    @staticmethod
    def write_header(stream: RandomAccessStream, nonce: bytearray, chunk_size: int) -> Header:
        """!
        Write the header to the stream
        @param stream: The stream
        @param nonce: The nonce
        @param chunk_size: The chunk size
        @returns The header
        """
        magic_bytes: bytearray = Generator.get_magic_bytes()
        version: int = Generator.get_version()
        version_bytes: bytearray = bytearray([version])
        chunk_size_bytes: bytearray = BitConverter.to_bytes(chunk_size, Generator.CHUNK_SIZE_LENGTH)

        header_data: bytearray = bytearray(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                                           + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH)
        ms: MemoryStream = MemoryStream(header_data)
        ms.write(magic_bytes, 0, len(magic_bytes))
        ms.write(version_bytes, 0, len(version_bytes))
        ms.write(chunk_size_bytes, 0, len(chunk_size_bytes))
        ms.write(nonce, 0, len(nonce))
        ms.set_position(0)
        header: Header = Header.read_header_data(ms)

        pos: int = stream.get_position()
        stream.set_position(0)
        ms.set_position(0)
        ms.copy_to(stream)
        ms.close()
        stream.flush()
        stream.set_position(pos)

        return header

    def set_chunk_size(self, chunk_size: int):
        """!
        Set the header chunk size
        @param chunk_size: The chunk size
        """
        self.__chunk_size = chunk_size

    def set_version(self, version: int):
        """!
        Set the Salmon format version
        @param version: The format version
        """
        self.__version = version

    def set_nonce(self, nonce: bytearray):
        """!
        Set the nonce to be used.
        @param nonce: The nonce
        """
        self.__nonce = nonce
