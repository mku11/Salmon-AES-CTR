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

from salmon_core.streams.memory_stream import MemoryStream
from salmon_core.streams.random_access_stream import RandomAccessStream
from salmon.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon_core.salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.integrity.integrity_exception import IntegrityException
from salmon_core.salmon.streams.encryption_mode import EncryptionMode
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.salmon_generator import SalmonGenerator
from salmon_core.salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon_core.salmon.salmon_security_exception import SalmonSecurityException
from salmon_core.salmon.transform.isalmon_ctr_transformer import ISalmonCTRTransformer
from salmon_core.salmon.transform.salmon_transformer_factory import SalmonTransformerFactory


@typechecked
class SalmonStream(RandomAccessStream):
    """
    Stream decorator provides AES256 encryption and decryption of stream.
    Block data integrity is also supported.
    """

    __provider_type: ProviderType = ProviderType.Default
    """
    Current global AES provider type.
    """

    @staticmethod
    def get_actual_size(data: bytearray, key: bytearray, nonce: bytearray, mode: EncryptionMode,
                        header_data: bytearray | None, integrity: bool, chunk_size: int | None,
                        hash_key: bytearray | None) -> int:

        """
        Get the output size of the data to be transformed(encrypted or decrypted) including
        header and hash without executing any operations. This can be used to prevent over-allocating memory
        where creating your output buffers.
        
        :param data: The data to be transformed.
        :param key: The AES key.
        :param nonce: The nonce for the CTR.
        :param mode: The {@link EncryptionMode} Encrypt or Decrypt.
        :param header_data: The header data to be embedded if you use Encryption.
        :param integrity: True if you want to enable integrity.
        :param chunk_size: The chunk size for integrity chunks.
        :param hash_key: The hash key to be used for integrity checks.
        :return: The size of the output data.
        
        :raises IntegrityException: Thrown when security error
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        :raises IOError: Thrown if there is an IO error.
        """
        input_stream: MemoryStream = MemoryStream(data)
        s: SalmonStream = SalmonStream(key, nonce, mode, input_stream, header_data, integrity, chunk_size, hash_key)
        size: int = s.actual_length()
        s.close()
        return size

    def __init__(self, key: bytearray, nonce: bytearray, encryption_mode: EncryptionMode,
                 base_stream: RandomAccessStream, header_data: bytearray | None = None,
                 integrity: bool = False, chunk_size: int | None = None, hash_key: bytearray | None = None):
        """
        Instantiate a new Salmon stream with a base stream and optional header data and hash integrity.
        <p>
        If you read from the stream it will decrypt the data from the baseStream.
        If you write to the stream it will encrypt the data from the baseStream.
        The transformation is based on AES CTR Mode.
        </p>
        Notes:
        The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional
        4 bytes counter.
        The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
        see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
        
        :param key:            The AES key that is used to encrypt decrypt
        :param nonce:          The nonce used for the initial counter
        :param encryption_mode: Encryption mode Encrypt or Decrypt this cannot change later
        :param base_stream:     The base Stream that will be used to read the data
        :param header_data:     The data to store in the header when encrypting.
        :param integrity:      enable integrity
        :param chunk_size:      the chunk size to be used with integrity
        :param hash_key:        Hash key to be used with integrity
        :raises IOError: Thrown if there is an IO error.
        :raises IntegrityException: Thrown when security error
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        """
        self.__headerData: bytearray
        """
        Header data embedded in the stream if available.
        """

        self.__encryptionMode: EncryptionMode
        """
        Mode to be used for this stream. This can only be set once.
        """

        self.__allowRangeWrite: bool = False
        """
        Allow seek and write.
        """

        self.__failSilently: bool = False
        """
        Fail silently if integrity cannot be verified.
        """

        self.__baseStream: RandomAccessStream
        """
        The base stream. When EncryptionMode is Encrypt this will be the target stream.
        When EncryptionMode is Decrypt this will be the source stream.
        """

        self.__transformer: ISalmonCTRTransformer | None = None
        """
        The transformer to use for encryption.
        """

        self.__salmonIntegrity: SalmonIntegrity | None = None
        """
        The integrity to use for hash signature creation and validation.
        """

        self.__buffer_size = SalmonIntegrity.DEFAULT_CHUNK_SIZE
        """
        Default buffer size for all internal streams including Encryptors and Decryptors
        """

        self.__encryptionMode = encryption_mode
        self.__baseStream = base_stream
        self.__headerData = header_data

        self.__init_integrity(integrity, hash_key, chunk_size)
        self.__init_transformer(key, nonce)
        self.__init_stream()

    @staticmethod
    def set_aes_provider_type(provider_type: ProviderType):
        """
        Set the global AES provider type. Supported types: {@link ProviderType}.
        
        :param provider_type: The provider Type.
        """
        SalmonStream.__provider_type = provider_type

    @staticmethod
    def get_aes_provider_type() -> ProviderType:
        """
        Get the global AES provider type. Supported types: {@link ProviderType}.
        
        :return: The provider Type.
        """
        return SalmonStream.__provider_type

    def length(self) -> int:
        """
        Provides the length of the actual transformed data (minus the header and integrity data).
        
        :return: The length of the stream.
        """
        total_hash_bytes: int
        hash_offset: int = SalmonGenerator.HASH_RESULT_LENGTH if self.__salmonIntegrity.get_chunk_size() > 0 else 0
        total_hash_bytes = self.__salmonIntegrity.get_hash_data_length(self.__baseStream.length() - 1, hash_offset)
        return self.__baseStream.length() - self.get_header_length() - total_hash_bytes

    def actual_length(self) -> int:
        """
        Provides the total length of the base stream including header and integrity data if available.
        
        :return: The actual length of the base stream.
        """
        total_hash_bytes: int = 0
        total_hash_bytes += self.__salmonIntegrity.get_hash_data_length(self.__baseStream.length() - 1, 0)
        if self.can_read():
            return self.length()
        elif self.can_write():
            return self.__baseStream.length() + self.get_header_length() + total_hash_bytes
        return 0

    def get_position(self) -> int:
        """
        Provides the position of the stream relative to the data to be transformed.
        
        :return: The current position of the stream.
        :raises IOError: Thrown if there is an IO error.
        """
        return self.__get_virtual_position()

    def set_position(self, value: int):
        """
        Sets the current position of the stream relative to the data to be transformed.
        
        :param value:         :raises IOError: Thrown if there is an IO error.
        """
        if self.can_write() and not self.__allowRangeWrite and value != 0:
            raise IOError() from \
                SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                                        "If you want to take the risk you need to use set_allow_range_write(true)")
        try:
            self.__set_virtual_position(value)
        except SalmonRangeExceededException as e:
            raise IOError() from e

    def can_read(self) -> bool:
        """
        If the stream is readable (only if EncryptionMode == Decrypted)
        
        :return: True if mode is decryption.
        """
        return self.__baseStream.can_read() and self.__encryptionMode == EncryptionMode.Decrypt

    def can_seek(self) -> bool:
        """
        If the stream is seekable (supported only if base stream is seekable).
        
        :return: True if stream is seekable.
        """
        return self.__baseStream.can_seek()

    def can_write(self) -> bool:
        """
        If the stream is writeable (only if EncryptionMode is Encrypt)
        
        :return: True if mode is decryption.
        """
        return self.__baseStream.can_write() and self.__encryptionMode == EncryptionMode.Encrypt

    def has_integrity(self) -> bool:
        """
        If the stream has integrity enabled
        """
        return self.get_chunk_size() > 0

    def __init_integrity(self, integrity: bool, hash_key: bytearray | None, chunk_size: int | None):
        """
        Initialize the integrity validator. This object is always associated with the
        stream because in the case of a decryption stream that has already embedded integrity
        we still need to calculate/skip the chunks.
        
        :param integrity: True to enable integrity
        :param hash_key:  The hash key to use
        :param chunk_size: The chunk size
        :raises IntegrityException: Thrown when data are corrupt or tampered with.
        """
        self.__salmonIntegrity = SalmonIntegrity(integrity, hash_key, chunk_size,
                                                 HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH)

    def __init_stream(self):
        """
        Init the stream.
        
        :raises IOError: Thrown if there is an IO error.
        """
        self.__baseStream.set_position(self.get_header_length())

    def get_header_length(self) -> int:
        """
        The length of the header data if the stream was initialized with a header.
        
        :return: The header data length.
        """
        if self.__headerData is None:
            return 0
        else:
            return len(self.__headerData)

    def __init_transformer(self, key: bytearray, nonce: bytearray):
        """
        To create the AES CTR mode we use ECB for AES with No Padding.
        Initailize the Counter to the initial vector provided.
        For each data block we increase the Counter and apply the EAS encryption on the Counter.
        The encrypted Counter then will be xor-ed with the actual data block.
        """
        if key is None:
            raise SalmonSecurityException("Key is missing")
        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        self.__transformer = SalmonTransformerFactory.create(SalmonStream.__provider_type)
        self.__transformer.init(key, nonce)
        self.__transformer.reset_counter()

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """
        Seek to a specific position on the stream. This does not include the header and any hash Signatures.
        
        :param offset: The offset that seek will use
        :param origin: If it is Begin the offset will be the absolute position from the start of the stream
                      If it is Current the offset will be added to the current position of the stream
                      If it is End the offset will be the absolute position starting from the end of the stream.
        """
        if origin == RandomAccessStream.SeekOrigin.Begin:
            self.set_position(offset)
        elif origin == RandomAccessStream.SeekOrigin.Current:
            self.set_position(self.get_position() + offset)
        elif origin == RandomAccessStream.SeekOrigin.End:
            self.set_position(self.length() - offset)
        return self.get_position()

    def set_length(self, value: int):
        """
        Set the length of the base stream. Currently unsupported.
        
        :param value:         """
        pos: int = self.get_position()
        self.set_position(value)
        self.set_position(pos)

    def flush(self):
        """
        Flushes any buffered data to the base stream.
        """
        if self.__baseStream is not None:
            self.__baseStream.flush()

    def close(self):
        """
        Closes the stream and all resources associated with it (including the base stream).
        
        :raises IOError: Thrown if there is an IO error.
        """
        self.__close_streams()

    def get_counter(self) -> bytearray:
        """
        Returns the current Counter value.
        
        :return: The current Counter value.
        """
        return self.__transformer.get_counter().copy()

    def get_block(self) -> int:
        """
        Returns the current Block value
        """
        return self.__transformer.get_block()

    def get_key(self) -> bytearray:
        """
        Returns a copy of the encryption key.
        """
        return self.__transformer.get_key().copy()

    def get_hash_key(self) -> bytearray:
        """
        Returns a copy of the hash key.
        """
        return self.__salmonIntegrity.get_key().copy()

    def get_nonce(self) -> bytearray:
        """
        Returns a copy of the initial vector.
        """
        return self.__transformer.get_nonce().copy()

    def get_chunk_size(self) -> int:
        """
        Returns the Chunk size used to apply hash signature
        """
        return self.__salmonIntegrity.get_chunk_size()

    def set_allow_range_write(self, value: bool):
        """
        Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it
        will re-use the same IV.
        This is not recommended if you use the stream on storing files or generally data if prior version can
        be inspected by others.
        You should only use this setting for initial encryption with parallel streams and not for overwriting!
        
        :param value: True to allow byte range encryption write operations
        """
        self.__allowRangeWrite = value

    def set_fail_silently(self, value: bool):
        """
        Set to True if you want the stream to fail silently when integrity cannot be verified.
        In that case read() operations will return -1 instead of raising an exception.
        This prevents 3rd party code like media players from crashing.
        
        :param value: True to fail silently.
        """
        self.__failSilently = value

    def __set_virtual_position(self, value: int):
        """
        Set the virtual position of the stream.
        
        :param value:         :raises IOError: Thrown if there is an IO error.
        :raises SalmonRangeExceededException: Thrown when maximum nonce range is exceeded.
        """
        # we skip the header bytes and any hash values we have if the file has integrity set
        total_hash_bytes: int = self.__salmonIntegrity.get_hash_data_length(value, 0)
        value = value + total_hash_bytes + self.get_header_length()
        self.__baseStream.set_position(value)

        self.__transformer.reset_counter()
        self.__transformer.sync_counter(self.get_position())

    def __get_virtual_position(self) -> int:
        """
        Returns the Virtual Position of the stream excluding the header and hash signatures.
        """
        total_hash_bytes: int
        hash_offset: int = SalmonGenerator.HASH_RESULT_LENGTH if self.__salmonIntegrity.get_chunk_size() > 0 else 0
        total_hash_bytes = self.__salmonIntegrity.get_hash_data_length(self.__baseStream.get_position(), hash_offset)
        return self.__baseStream.get_position() - self.get_header_length() - total_hash_bytes

    def __close_streams(self):
        """
        Close base stream
        """
        if self.__baseStream is not None:
            if self.can_write():
                self.__baseStream.flush()
            self.__baseStream.close()

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
        Decrypts the data from the baseStream and stores them in the buffer provided.
        
        :param buffer: The buffer that the data will be stored after decryption
        :param offset: The start position on the buffer that data will be written.
        :param count:  The requested count of the data bytes that should be decrypted
        :return: The number of data bytes that were decrypted.
        """
        if self.get_position() == self.length():
            return -1
        aligned_offset: int = self.__get_aligned_offset()
        v_bytes: int = 0
        pos: int = self.get_position()

        # if the base stream is not aligned for read
        if aligned_offset != 0:
            # read partially once
            self.set_position(self.get_position() - aligned_offset)
            n_count: int = self.__salmonIntegrity.get_chunk_size() if self.__salmonIntegrity.get_chunk_size() > 0 \
                else SalmonGenerator.BLOCK_SIZE
            buff: bytearray = bytearray(n_count)
            v_bytes = self.read(buff, 0, n_count)
            v_bytes = min(v_bytes - aligned_offset, count)
            # if no more bytes to read from the stream
            if v_bytes <= 0:
                return -1
            buffer[offset:offset + v_bytes] = buff[aligned_offset:aligned_offset + v_bytes]
            self.set_position(pos + v_bytes)

        # if we have all bytes originally requested
        if v_bytes == count:
            return v_bytes

        # the base stream position should now be aligned
        # now we can now read the rest of the data.
        pos = self.get_position()
        n_bytes: int = self.__read_from_stream(buffer, v_bytes + offset, count - v_bytes)
        self.set_position(pos + n_bytes)
        return v_bytes + n_bytes

    def __read_from_stream(self, buffer: bytearray, offset: int, count: int):
        """
        Decrypts the data from the baseStream and stores them in the buffer provided.
        Use this only after you align the base stream to the chunk if integrity is enabled
        or to the encryption block size.
        
        :param buffer: The buffer that the data will be stored after decryption
        :param offset: The start position on the buffer that data will be written.
        :param count:  The requested count of the data bytes that should be decrypted
        :return: The number of data bytes that were decrypted.
        :raises IOError: Thrown if stream is not aligned.
        """
        if self.get_position() == self.length():
            return 0
        if self.__salmonIntegrity.get_chunk_size() > 0 \
                and self.get_position() % self.__salmonIntegrity.get_chunk_size() != 0:
            raise IOError("All reads should be aligned to the chunks size: " + self.__salmonIntegrity.get_chunk_size())
        elif self.__salmonIntegrity.get_chunk_size() == 0 and self.get_position() % SalmonGenerator.BLOCK_SIZE != 0:
            raise IOError("All reads should be aligned to the block size: " + SalmonGenerator.BLOCK_SIZE)

        pos: int = self.get_position()

        # if there are not enough data in the stream
        count = min(count, self.length() - self.get_position())

        # if there are not enough space in the buffer
        count = min(count, len(buffer) - offset)

        if count <= 0:
            return 0

        # make sure our buffer size is also aligned to the block or chunk
        buffer_size: int = self.__get_normalized_buffer_size(True)

        v_bytes: int = 0
        while v_bytes < count:
            # read data and integrity signatures
            src_buffer: bytearray = self.__read_stream_data(buffer_size)
            try:
                integrity_hashes: list | None = None
                # if there are integrity hashes strip them and get the data chunks only
                if self.__salmonIntegrity.get_chunk_size() > 0:
                    # get the integrity signatures
                    integrity_hashes = self.__salmonIntegrity.get_hashes(src_buffer)
                    src_buffer = self.__strip_signatures(src_buffer, self.__salmonIntegrity.get_chunk_size())
                dest_buffer: bytearray = bytearray(len(src_buffer))
                if self.__salmonIntegrity.use_integrity():
                    self.__salmonIntegrity.verify_hashes(integrity_hashes, src_buffer,
                                                         self.__headerData if pos == 0 and v_bytes == 0 else None)
                self.__transformer.decrypt_data(src_buffer, 0, dest_buffer, 0, len(src_buffer))
                length: int = min(count - v_bytes, len(dest_buffer))
                self.__write_to_buffer(dest_buffer, 0, buffer, v_bytes + offset, length)
                v_bytes += length
                self.__transformer.sync_counter(self.get_position())
            except (SalmonSecurityException, SalmonRangeExceededException, IntegrityException) as ex:
                if isinstance(ex, IntegrityException) and self.__failSilently:
                    return -1
                raise IOError("Could not read from stream: ") from ex
        return v_bytes

    def write(self, buffer: bytearray, offset: int, count: int):
        """
        Encrypts the data from the buffer and writes the result to the baseStream.
        If you are using integrity you will need to align all write operations to the chunk size
        otherwise align to the encryption block size.
        
        :param buffer: The buffer that contains the data that will be encrypted
        :param offset: The offset in the buffer that the bytes will be encrypted.
        :param count:  The length of the bytes that will be encrypted.
        
        """
        if self.__salmonIntegrity.get_chunk_size() > 0 \
                and self.get_position() % self.__salmonIntegrity.get_chunk_size() != 0:
            raise IOError() from \
                IntegrityException("All write operations should be aligned to the chunks size: "
                                   + str(self.__salmonIntegrity.get_chunk_size()))
        elif self.__salmonIntegrity.get_chunk_size() == 0 \
                and self.get_position() % SalmonGenerator.BLOCK_SIZE != 0:
            raise IOError() from \
                IntegrityException("All write operations should be aligned to the block size: "
                                   + SalmonGenerator.BLOCK_SIZE)

        # if there are not enough data in the buffer
        count = min(count, len(buffer) - offset)

        # if there
        buffer_size: int = self.__get_normalized_buffer_size(False)

        pos: int = 0
        while pos < count:
            n_buffer_size: int = min(buffer_size, count - pos)

            src_buffer: bytearray = self.__read_buffer_data(buffer, pos + offset, n_buffer_size)
            if len(src_buffer) == 0:
                break
            dest_buffer: bytearray = bytearray(len(src_buffer))

            try:
                self.__transformer.encrypt_data(src_buffer, 0, dest_buffer, 0, len(src_buffer))
                integrity_hashes: list | None = \
                    self.__salmonIntegrity.generate_hashes(
                        dest_buffer, self.__headerData if self.get_position() == 0 else None)
                pos += self.__write_to_stream(dest_buffer, self.get_chunk_size(), integrity_hashes)
                self.__transformer.sync_counter(self.get_position())
            except (SalmonSecurityException, SalmonRangeExceededException, IntegrityException) as ex:
                raise IOError("Could not write to stream: ") from ex

    def __get_aligned_offset(self) -> int:
        """
        Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
        wrt to the encryption block size. Use this method to align a position to the
        start of the block or chunk.
        
        :return: The aligned offset
        """
        align_offset: int
        if self.__salmonIntegrity.get_chunk_size() > 0:
            align_offset = self.get_position() % self.__salmonIntegrity.get_chunk_size()
        else:
            align_offset = self.get_position() % SalmonGenerator.BLOCK_SIZE
        return align_offset

    def __get_normalized_buffer_size(self, include_hashes: bool) -> int:
        """
        Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
        wrt to the encryption block size. Use this method to ensure that buffer sizes request
        via the API are aligned for read/writes and integrity processing.
        
        :return: The normalized buffer size
        """
        buffer_size: int = self.__buffer_size
        if self.get_chunk_size() > 0:
            # buffer size should be a multiple of the chunk size if integrity is enabled
            part_size: int = self.get_chunk_size()
            # if add the hash signatures

            if part_size < buffer_size:
                buffer_size = buffer_size // self.get_chunk_size() * self.get_chunk_size()
            else:
                buffer_size = part_size

            if include_hashes:
                buffer_size += buffer_size // self.get_chunk_size() * SalmonGenerator.HASH_RESULT_LENGTH
        else:
            # buffer size should also be a multiple of the AES block size
            buffer_size = buffer_size // SalmonGenerator.BLOCK_SIZE * SalmonGenerator.BLOCK_SIZE

        return buffer_size

    def __read_buffer_data(self, buffer: bytearray, offset: int, count: int) -> bytearray:
        """
        Read the data from the buffer
        
        :param buffer: The source buffer.
        :param offset: The offset to start reading the data.
        :param count:  The number of requested bytes to read.
        :return: The array with the data that were read.
        """
        data: bytearray = bytearray(min(count, len(buffer) - offset))
        data[0:len(data)] = buffer[offset:offset + len(data)]
        return data

    def __read_stream_data(self, count: int) -> bytearray:
        """
        Read the data from the base stream into the buffer.
        
        :param count: The number of bytes to read.
        :return: The number of bytes read.
        :raises IOError: Thrown if there is an IO error.
        """
        data: bytearray = bytearray(min(count, self.__baseStream.length() - self.__baseStream.get_position()))
        total_bytes_read = 0
        while total_bytes_read < len(data):
            bytes_read = self.__baseStream.read(data, total_bytes_read, len(data) - total_bytes_read)
            if bytes_read <= 0:
                break
            total_bytes_read += bytes_read
        return data

    def __write_to_buffer(self, src_buffer: bytearray, src_offset: int, dest_buffer: bytearray, dest_offset: int,
                          count: int):
        """
        Write the buffer data to the destination buffer.
        
        :param src_buffer:  The source byte array.
        :param src_offset:  The source byte offset.
        :param dest_buffer:  The source byte array.
        :param dest_offset: The destination byte offset.
        :param count:      The number of bytes to write.
        """
        dest_buffer[dest_offset:dest_offset + count] = src_buffer[src_offset:src_offset + count]

    def __write_to_stream(self, buffer: bytearray, chunk_size: int, hashes: list | None) -> int:
        """
        Write data to the base stream.
        
        :param buffer:    The buffer to read from.
        :param chunk_size: The chunk segment size to use when writing the buffer.
        :param hashes:    The hash signature to write at the beginning of each chunk.
        :return: The number of bytes written.
        :raises IOError: Thrown if there is an IO error.
        """
        pos: int = 0
        chunk: int = 0
        if chunk_size <= 0:
            chunk_size = len(buffer)
        while pos < len(buffer):
            if hashes is not None:
                self.__baseStream.write(hashes[chunk], 0, len(hashes[chunk]))
            length: int = min(chunk_size, len(buffer) - pos)
            self.__baseStream.write(buffer, pos, length)
            pos += length
            chunk += 1
        return pos

    def __strip_signatures(self, buffer: bytearray, chunk_size: int) -> bytearray:
        """
        Strip hash signatures from the buffer.
        
        :param buffer:    The buffer.
        :param chunk_size: The chunk size.
        :return: The data witout the signatures
        """
        v_bytes: int = len(buffer) // (chunk_size + SalmonGenerator.HASH_RESULT_LENGTH) * chunk_size
        if len(buffer) % (chunk_size + SalmonGenerator.HASH_RESULT_LENGTH) != 0:
            v_bytes += len(buffer) % (chunk_size + SalmonGenerator.HASH_RESULT_LENGTH) \
                       - SalmonGenerator.HASH_RESULT_LENGTH
        buff: bytearray = bytearray(v_bytes)
        index: int = 0
        for i in range(0, len(buffer), chunk_size + SalmonGenerator.HASH_RESULT_LENGTH):
            n_chunk_size: int = min(chunk_size, len(buff) - index)
            end: int = i + SalmonGenerator.HASH_RESULT_LENGTH + n_chunk_size
            buff[index:index + n_chunk_size] = buffer[i + SalmonGenerator.HASH_RESULT_LENGTH:end]
            index += n_chunk_size
        return buff

    def is_integrity_enabled(self) -> bool:
        """
        True if the stream has integrity enabled.
        
        :return: True if integrity enabled
        """
        return self.__salmonIntegrity.use_integrity()

    def get_encryption_mode(self) -> EncryptionMode:
        """
        Get the encryption mode.
        
        :return: The encryption mode
        """
        return self.__encryptionMode

    def is_allow_range_write(self) -> bool:
        """
        Get the allowed range write option. This can check if you can use random access write.
        This is generally not a good option since it prevents reusing the same nonce/counter.
        
        :return: True if the stream allowed to seek and write.
        """
        return self.__allowRangeWrite

    def get_buffer_size(self) -> int:
        """
        Get the default buffer size for all internal streams including Encryptors and Decryptors.
        :return: The buffer size
        """
        return self.__buffer_size

    def set_buffer_size(self, buffer_size: int):
        """
        Set the default buffer size for all internal streams including Encryptors and Decryptors.
        
        :param buffer_size:         """
        self.__buffer_size = buffer_size
