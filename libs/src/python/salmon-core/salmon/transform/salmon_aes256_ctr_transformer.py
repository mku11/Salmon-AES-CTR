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

from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon.transform.isalmon_ctr_transformer import ISalmonCTRTransformer


class SalmonAES256CTRTransformer(ISalmonCTRTransformer):
    """
     * Abstract class for AES256 transformer implementations.
     *
    """

    EXPANDED_KEY_SIZE = 240
    """
     * Standard expansion key size for AES256 only.
    """

    @staticmethod
    def getActualSize(data: bytearray, key: bytearray, nonce: bytearray, mode: SalmonStream.EncryptionMode,
                      headerData: bytearray, integrity: bool, chunkSize: int, hashKey: bytearray) -> int:

        """
         * Get the output size of the data to be transformed(encrypted or decrypted) including
         * header and hash without executing any operations. This can be used to prevent over-allocating memory
         * where creating your output buffers.
         *
         * @param data The data to be transformed.
         * @param key The AES key.
         * @param nonce The nonce for the CTR.
         * @param mode The {@link SalmonStream.EncryptionMode} Encrypt or Decrypt.
         * @param headerData The header data to be embedded if you use Encryption.
         * @param integrity True if you want to enable integrity.
         * @param chunkSize The chunk size for integrity chunks.
         * @param hashKey The hash key to be used for integrity checks.
         * @return The size of the output data.
         *
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
         * @throws IOException
        """
        # MemoryStream inputStream = new MemoryStream(data)
        # SalmonStream s = new SalmonStream(key, nonce, mode, inputStream,
        #              headerData, integrity, chunkSize, hashKey)
        # long size = s.actualLength()
        # s.close()
        return size

    """
     * Salmon stream encryption block size, same as AES.
    """
    BLOCK_SIZE = 16

    def __init__(self):
        self.__key: bytearray = bytearray()
        """
         * Key to be used for AES transformation.
        """

        self.__expandedKey: bytearray = bytearray(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE)
        """
         * Expanded key.
        """

        self.__nonce: bytearray = bytearray()
        """
         * Nonce to be used for CTR mode.
        """

        self.__block: int = 0
        """
         * Current operation block.
        """

        self.__counter: bytearray
        """
         * Current operation counter.
        """

    def resetCounter(self):
        """
         * Resets the Counter and the block count.
        """
        __counter: bytearray = bytearray(SalmonAES256CTRTransformer.BLOCK_SIZE)
        __counter[0:len(self.__nonce)] = self.__nonce[0:]
        self.__block = 0

    def syncCounter(self, position: int):
        """
         * Syncs the Counter based on what AES block position the stream is at.
         * The block count is already excluding the header and the hash signatures.
        """
        currBlock: int = position // SalmonAES256CTRTransformer.BLOCK_SIZE
        self.resetCounter()
        self.increaseCounter(currBlock)
        __block = currBlock

    def _increaseCounter(self, value: int):
        """
         * Increase the Counter
         * We use only big endianness for AES regardless of the machine architecture
         *
         * @param value value to increase counter by
        """
        if value < 0:
            raise ValueError("Value should be positive")
        index: int = SalmonAES256CTRTransformer.BLOCK_SIZE - 1
        carriage: int = 0
        while index >= 0 and value + carriage > 0:
            if index <= SalmonAES256CTRTransformer.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH:
                raise SalmonRangeExceededException("Current CTR max blocks exceeded")
            val: int = (value + carriage) % 256
            carriage = int((((self.__counter[index] & 0xFF) + val) / 256))
            self.__counter[index] += val
            index -= 1
            value /= 256

    def init(self, key: bytearray, nonce: bytearray):
        """
         * Initialize the transformer. Most common operations include precalculating expansion keys or
         * any other prior initialization for efficiency.
         * @param key
         * @param nonce
         * @throws SalmonSecurityException
        """
        self.__key = key
        self.__nonce = nonce

    def getCounter(self) -> bytearray:
        """
         * Get the current Counter.
         * @return
        """
        return self.__counter

    def getBlock(self) -> int:
        """
         * Get the current block.
         * @return
        """
        return self.__block

    def getKey(self) -> bytearray:
        """
         * Get the current encryption key.
         * @return
        """
        return self.__key

    def getExpandedKey(self) -> bytearray:
        """
         * Get the expanded key if available.
         * @return
        """
        return self.__expandedKey

    def getNonce(self) -> bytearray:
        """
         * Get the nonce (initial counter)
         * @return
        """
        return self.__nonce

    def setExpandedKey(self, expandedKey: bytearray):
        """
         * Set the expanded key. This should be called once during initialization phase.
         * @param expandedKey
        """
        self.__expandedKey = expandedKey