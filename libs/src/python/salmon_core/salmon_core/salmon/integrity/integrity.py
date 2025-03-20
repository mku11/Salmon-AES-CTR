#!/usr/bin/env python3
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

import math
from builtins import int

from salmon.integrity.ihash_provider import IHashProvider
from salmon_core.salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.generator import Generator
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.security_exception import SecurityException

from typeguard import typechecked


@typechecked
class Integrity:
    """
    Provide operations for calculating, storing, and verifying data integrity.
    This class operates in chunks of data in buffers calculating the hash for each one.
     """

    MAX_CHUNK_SIZE = 8 * 1024 * 1024
    """
    * Maximum chunk size for data integrity.
    """

    DEFAULT_CHUNK_SIZE = 256 * 1024
    """
    Default chunk size for integrity.
    """

    def __init__(self, integrity: bool, key: bytearray | None, chunk_size: int,
                 provider: IHashProvider, hash_size: int):
        """
        Instantiate an object to be used for applying and verifying hash signatures for each of the data chunks.

        :param integrity: True to enable integrity checks.
        :param key:       The key to use for hashing.
        :param chunk_size: The chunk size. Use 0 to enable integrity on the whole file (1 chunk).
                         Use a positive number to specify integrity chunks.
        :param provider:  Hash implementation provider.
        :param hash_size: The hash size.
        :raises IntegrityException: When integrity is comprimised
        :raises SalmonSecurityException: When security has failed
        """

        self._chunkSize: int = -1
        """
        The chunk size to be used for integrity.
        """

        self._key: bytearray
        """
        Key to be used for integrity signing and validation.
        """

        self._hashSize: int
        """
        Hash result size
        """

        self._provider: IHashProvider
        """
        The hash provider.
        """

        _integrity: bool
        """
       True to use integrity, false to skip the chunks
        """

        if (chunk_size < 0 or (0 < chunk_size < Generator.BLOCK_SIZE)
                or (chunk_size > 0 and chunk_size % Generator.BLOCK_SIZE != 0)
                or chunk_size > Integrity.MAX_CHUNK_SIZE):
            raise IntegrityException(
                "Invalid chunk size, specify zero for default value or a positive number multiple of: "
                + str(Generator.BLOCK_SIZE) + " and less than: " + str(
                    Integrity.MAX_CHUNK_SIZE) + " bytes")
        if integrity and key is None:
            raise SecurityException("You need a hash to use with integrity")
        if integrity and chunk_size == 0:
            self._chunkSize = Integrity.DEFAULT_CHUNK_SIZE
        elif integrity or chunk_size > 0:
            self._chunkSize = chunk_size
        if hash_size < 0:
            raise SecurityException("Hash size should be a positive number")
        self._key = key
        self._provider = provider
        self._integrity = integrity
        self._hashSize = hash_size

    @staticmethod
    def calculate_hash(provider: IHashProvider, buffer: bytearray, offset: int, count: int,
                       key: bytearray, include_data: bytearray | None) -> bytearray:
        """
        Calculate hash of the data provided.

        :param provider:    Hash implementation provider.
        :param buffer:      Data to calculate the hash.
        :param offset:      Offset of the buffer that the hashing calculation will start from
        :param count:       Length of the buffer that will be used to calculate the hash.
        :param key:         Key that will be used
        :param include_data: Additional data to be included in the calculation.
        :return: The hash.
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        """

        final_buffer: bytearray = buffer
        final_offset: int = offset
        final_count: int = count
        if include_data is not None:
            final_buffer = bytearray(count + len(include_data))
            final_count = count + len(include_data)
            final_buffer[0:len(include_data)] = include_data[0:len(include_data)]
            final_buffer[len(include_data):len(include_data) + count] = buffer[offset:offset + count]
            final_offset = 0
        hash_value: bytearray = provider.calc(key, final_buffer, final_offset, final_count)
        return hash_value

    @staticmethod
    def get_total_hash_data_length(mode: EncryptionMode, length: int, chunk_size: int,
                                   hash_offset: int, hash_length: int) -> int:
        """
        Get the total number of bytes for all hash signatures for data of a specific length.

        :param mode: The {@link EncryptionMode} Encrypt or Decrypt.
        :param length: 		The length of the data.
        :param chunk_size:      The byte size of the stream chunk that will be used to calculate the hash.
                              The length should be fixed value except for the last chunk which might be lesser since
                              we don't use padding
        :param hash_offset:     The hash key length that will be used as an offset.
        :param hash_length:     The hash length.
        :return: The total hash length
        """
        if mode == EncryptionMode.Decrypt:
            chunks: int = int(math.floor(length / (chunk_size + hash_offset)))
            rem: int = int(length % (chunk_size + hash_offset))
            if rem > hash_offset:
                chunks += 1
            return chunks * hash_length
        else:
            chunks = int(math.floor(length / chunk_size))
            rem: int = int(length % chunk_size)
            if rem > hash_offset:
                chunks += 1
            return chunks * hash_length

    def get_hash_data_length(self, count: int, hash_offset: int) -> int:
        """
        Return the number of bytes that all hash signatures occupy for each chunk size
        
        :param count:      Actual length of the real data int the base stream including header and hash signatures.
        :param hash_offset: The hash key length
        :return: The number of bytes all hash signatures occupy
        """
        if self._chunkSize <= 0:
            return 0
        return Integrity.get_total_hash_data_length(EncryptionMode.Decrypt, count, self._chunkSize, hash_offset,
                                                    self._hashSize)

    def get_chunk_size(self) -> int:
        """
        Get the chunk size.
        :return: The chunk size.
        """
        return self._chunkSize

    def get_key(self) -> bytearray:
        """
        Get the hash key.
        :return: The hash key.
        """
        return self._key

    def use_integrity(self) -> bool:
        """
        Get the integrity enabled option.
        :return: True if integrity is enabled.
        """
        return self._integrity

    def generate_hashes(self, buffer: bytearray, include_header_data: bytearray | None) -> list[bytearray] | None:
        """
        Generate a hash signatures for each data chunk.
        :param buffer: The buffer containing the data chunks.
        :param include_header_data: Include the header data in the first chunk.
        :return: The hash signatures.
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
         """
        if not self._integrity:
            return None
        hashes: list[bytearray] = []
        for i in range(0, len(buffer), self._chunkSize):
            length: int = min(self._chunkSize, len(buffer) - i)
            hashes.append(self.calculate_hash(self._provider, buffer, i, length, self.get_key(),
                                              include_header_data if i == 0 else None))
        return hashes

    def get_hashes(self, buffer: bytearray) -> list[bytearray] | None:
        """
        Get the hashes for each data chunk.
        :param buffer: The buffer that contains the data chunks.
        :return: The hash signatures.
        """
        if not self._integrity:
            return None
        hashes: list[bytearray] = []
        for i in range(0, len(buffer), Generator.HASH_KEY_LENGTH + self._chunkSize):
            v_hash: bytearray = bytearray(Generator.HASH_KEY_LENGTH)
            v_hash[0:Generator.HASH_KEY_LENGTH] = buffer[i:i + Generator.HASH_KEY_LENGTH]
            hashes.append(v_hash)
        return hashes

    def verify_hashes(self, hashes: list | None, buffer: bytearray, include_header_data: bytearray | None):
        """
        Verify the buffer chunks against the hash signatures.
        :param hashes: The hashes to verify.
        :param buffer: The buffer that contains the chunks to verify the hashes.
        :param include_header_data: Header data to include in the hash
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        """
        chunk: int = 0
        for i in range(0, len(buffer), self._chunkSize):
            n_chunk_size: int = min(self._chunkSize, len(buffer) - i)
            v_hash: bytearray = self.calculate_hash(self._provider, buffer, i, n_chunk_size, self.get_key(),
                                                    include_header_data if i == 0 else None)
            for k in range(0, len(v_hash)):
                if v_hash[k] != hashes[chunk][k]:
                    raise IntegrityException("Data corrupt or tampered")
            chunk += 1
