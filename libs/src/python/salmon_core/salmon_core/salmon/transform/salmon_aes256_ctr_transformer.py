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
from abc import ABC

from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon_core.salmon.transform.isalmon_ctr_transformer import ISalmonCTRTransformer
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException

from typeguard import typechecked


@typechecked
class SalmonAES256CTRTransformer(ISalmonCTRTransformer, ABC):
    """
    Abstract class for AES256 transformer implementations.
    
    """

    EXPANDED_KEY_SIZE = 240
    """
    Standard expansion key size for AES256 only.
    """

    def __init__(self):
        self.__key: bytearray | None = None
        """
        Key to be used for AES transformation.
        """

        self.__expandedKey: bytearray = bytearray(SalmonAES256CTRTransformer.EXPANDED_KEY_SIZE)
        """
        Expanded key.
        """

        self.__nonce: bytearray | None = None
        """
        Nonce to be used for CTR mode.
        """

        self.__block: int = 0
        """
        Current operation block.
        """

        self.__counter: bytearray | None = None
        """
        Current operation counter.
        """

    def reset_counter(self):
        """
        Resets the Counter and the block count.
        """
        
        if self.__nonce is None:
            raise SalmonSecurityException("No counter, run init first")
        self.__counter: bytearray = bytearray(SalmonGenerator.BLOCK_SIZE)
        self.__counter[0:len(self.__nonce)] = self.__nonce[0:]
        self.__block = 0

    def sync_counter(self, position: int):
        """
        Syncs the Counter based on what AES block position the stream is at.
        The block count is already excluding the header and the hash signatures.
        """
        curr_block: int = position // SalmonGenerator.BLOCK_SIZE
        self.reset_counter()
        self._increase_counter(curr_block)
        self.__block = curr_block

    def _increase_counter(self, value: int):
        """
        Increase the Counter
        We use only big endianness for AES regardless of the machine architecture
        
        :param value: value to increase counter by
        """
        
        if self.__counter is None or self.__nonce is None:
            raise SalmonSecurityException("No counter, run init first")
        if value < 0:
            raise ValueError("Value should be positive")
        index: int = SalmonGenerator.BLOCK_SIZE - 1
        carriage: int = 0
        while index >= 0 and value + carriage > 0:
            if index <= SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH:
                raise SalmonRangeExceededException("Current CTR max blocks exceeded")
            val: int = (value + carriage) % 256
            carriage = int((((self.__counter[index] & 0xFF) + val) // 256))
            self.__counter[index] = (self.__counter[index] + val) % 256
            index -= 1
            value //= 256

    def init(self, key: bytearray, nonce: bytearray):
        """
        Initialize the transformer. Most common operations include precalculating expansion keys or
        any other prior initialization for efficiency.
        :param key: The key
        :param nonce: The nonce
        :raises IntegrityException: Thrown when security error
        """
        self.__key = key
        self.__nonce = nonce

    def get_counter(self) -> bytearray:
        """
        Get the current Counter.
        :return: The counter
        """
        return self.__counter

    def get_block(self) -> int:
        """
        Get the current block.
        :return: The block
        """
        return self.__block

    def get_key(self) -> bytearray:
        """
        Get the current encryption key.
        :return: The key
        """
        return self.__key

    def get_expanded_key(self) -> bytearray:
        """
        Get the expanded key if available.
        :return: The expanded key
        """
        return self.__expandedKey

    def get_nonce(self) -> bytearray:
        """
        Get the nonce (initial counter)
        :return: The nonce
        """
        return self.__nonce

    def set_expanded_key(self, expanded_key: bytearray):
        """
        Set the expanded key. This should be called once during initialization phase.
        :param expanded_key: The expanded key
        """
        self.__expandedKey = expanded_key
