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
from convert.bit_converter import BitConverter
from iostream.random_access_stream import RandomAccessStream
from salmon.salmon_generator import SalmonGenerator


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

    def getNonce(self) -> bytearray:
        """
         * Get the nonce.
         * @return The nonce
        """
        return self.__nonce

    def getChunkSize(self) -> int:
        """
         * Get the chunk size.
         * @return Chunk size
        """
        return self.__chunkSize

    def getHeaderData(self) -> bytearray:
        """
         * Get the raw header data.
         * @return Header data
        """
        return self.__headerData

    def getVersion(self) -> int:
        """
         * Get the Salmon format  version
         * @return The format version
        """
        return self.__version

    def getMagicBytes(self) -> bytearray:
        """
         * Get the magic bytes
         * @return Magic bytes
        """
        return self.__magicBytes

    @staticmethod
    def parseHeaderData(stream: RandomAccessStream) -> SalmonHeader:
        """
         * Parse the header data from the stream
         * @param stream The stream.
         * @return
         * @throws IOException
        """
        header: SalmonHeader = SalmonHeader()
        header.magicBytes = bytearray(SalmonGenerator.MAGIC_LENGTH)
        stream.read(header.magicBytes, 0, len(header.magicBytes))
        versionBytes: bytearray = bytearray(SalmonGenerator.VERSION_LENGTH)
        stream.read(versionBytes, 0, SalmonGenerator.VERSION_LENGTH);
        header.version = versionBytes[0];
        chunkSizeHeader: bytearray = bytearray(SalmonGenerator.CHUNK_SIZE_LENGTH)
        stream.read(chunkSizeHeader, 0, len(chunkSizeHeader))
        header.chunkSize = BitConverter.to_long(chunkSizeHeader, 0, SalmonGenerator.CHUNK_SIZE_LENGTH)
        header.nonce = bytearray(SalmonGenerator.NONCE_LENGTH)
        stream.read(header.nonce, 0, len(header.nonce))
        stream.position(0)
        header.headerData = bytearray(
            SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH + SalmonGenerator.CHUNK_SIZE_LENGTH + SalmonGenerator.NONCE_LENGTH)
        stream.read(header.headerData, 0, len(header.headerData))
        return header

    """
     * Set the header chunk size
     * @param chunkSize The chunk size
    """

    def setChunkSize(self, chunkSize: int):
        self.__chunkSize = chunkSize

    def setVersion(self, version: int):
        """
         * Set the Salmon format version
         * @param version The format version
        """
        self.__version = version

    def setNonce(self, nonce: bytearray):
        """
         * Set the nonce to be used.
         * @param nonce The nonce
        """
        self.__nonce = nonce
