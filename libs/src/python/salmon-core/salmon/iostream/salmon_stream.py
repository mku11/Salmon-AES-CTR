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
from enum import Enum

from iostream.random_access_stream import RandomAccessStream
from salmon.integrity.hmac_sha256_provider import HmacSHA256Provider
from salmon.integrity.salmon_integrity import SalmonIntegrity
from salmon.integrity.salmon_integrity_exception import SalmonIntegrityException
from salmon.salmon_default_options import SalmonDefaultOptions
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.transform.isalmon_ctr_transformer import ISalmonCTRTransformer
from salmon.transform.salmon_aes256_ctr_transformer import SalmonAES256CTRTransformer
from salmon.transform.salmon_transformer_factory import SalmonTransformerFactory


class SalmonStream(RandomAccessStream):
    """
     * Stream decorator provides AES256 encryption and decryption of stream.
     * Block data integrity is also supported.
    """

    class ProviderType(Enum):
        """
         * AES provider types. List of AES implementations that currently supported.
         *
         * @see #Default
         * @see #AesIntrinsics
         * @see #TinyAES
        """

        Default = 0
        """
         * Default AES cipher.
        """

        AesIntrinsics = 1
        """
         * Salmon builtin AES intrinsics. This needs the SalmonNative library to be loaded. @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
        """

        TinyAES = 2
        """
         * Tiny AES implementation. This needs the SalmonNative library to be loaded. @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
        """

    __providerType: SalmonStream.ProviderType = ProviderType.Default
    """
     * Current global AES provider type.
    """

    def __init__(self, key: bytearray, nonce: bytearray, encryptionMode: EncryptionMode,
                 baseStream: RandomAccessStream, headerData: bytearray = None,
                 integrity: bool = False, chunkSize: int = None, hashKey: bytearray = None):
        """
         * Instantiate a new Salmon stream with a base stream and optional header data and hash integrity.
         * <p>
         * If you read from the stream it will decrypt the data from the baseStream.
         * If you write to the stream it will encrypt the data from the baseStream.
         * The transformation is based on AES CTR Mode.
         * </p>
         * Notes:
         * The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional 4 bytes counter.
         * The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
         * @see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
         *
         * @param key            The AES key that is used to encrypt decrypt
         * @param nonce          The nonce used for the initial counter
         * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
         * @param baseStream     The base Stream that will be used to read the data
         * @param headerData     The data to store in the header when encrypting.
         * @param integrity      enable integrity
         * @param chunkSize      the chunk size to be used with integrity
         * @param hashKey        Hash key to be used with integrity
         * @throws IOError
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
        """
        self.__headerData: bytearray
        """
         * Header data embedded in the stream if available.
        """

        self.__encryptionMode: SalmonStream.EncryptionMode
        """
         * Mode to be used for this stream. This can only be set once.
        """

        self.__allowRangeWrite: bool = False
        """
         * Allow seek and write.
        """

        self.__failSilently: bool = False
        """
         * Fail silently if integrity cannot be verified.
        """

        self.__baseStream: RandomAccessStream
        """
         * The base stream. When EncryptionMode is Encrypt this will be the target stream.
         * When EncryptionMode is Decrypt this will be the source stream.
        """

        self.__transformer: ISalmonCTRTransformer = None
        """
         * The transformer to use for encryption.
        """

        self.__salmonIntegrity: SalmonIntegrity = None
        """
         * The integrity to use for hash signature creation and validation.
        """

        self.__encryptionMode = encryptionMode
        self.__baseStream = baseStream
        self.__headerData = headerData

        self.__initIntegrity(integrity, hashKey, chunkSize)
        self.__initTransformer(key, nonce)
        self.__initStream()

    @staticmethod
    def setAesProviderType(providerType: ProviderType):
        """
         * Set the global AES provider type. Supported types: {@link ProviderType}.
         *
         * @param providerType The provider Type.
        """
        SalmonStream.providerType = providerType

    @staticmethod
    def getAesProviderType() -> ProviderType:
        """
         * Get the global AES provider type. Supported types: {@link ProviderType}.
         *
         * @return The provider Type.
        """
        return SalmonStream.__providerType

    def length(self) -> int:
        """
         * Provides the length of the actual transformed data (minus the header and integrity data).
         *
         * @return The length of the stream.
        """
        totalHashBytes: int
        hashOffset: int = SalmonGenerator.HASH_RESULT_LENGTH if self.__salmonIntegrity.getChunkSize() > 0 else 0
        totalHashBytes = self.__salmonIntegrity.get_hash_data_length(self.__baseStream.length() - 1, hashOffset)
        return self.__baseStream.length() - self.getHeaderLength() - totalHashBytes

    def actualLength(self) -> int:
        """
         * Provides the total length of the base stream including header and integrity data if available.
         *
         * @return The actual length of the base stream.
        """
        totalHashBytes: int = 0
        totalHashBytes += self.__salmonIntegrity.get_hash_data_length(self.__baseStream.length() - 1, 0)
        if self.canRead():
            return self.length()
        elif self.canWrite():
            return self.__baseStream.length() + self.getHeaderLength() + totalHashBytes
        return 0

    def position(self) -> int:
        """
         * Provides the position of the stream relative to the data to be transformed.
         *
         * @return The current position of the stream.
         * @throws IOError
        """
        return self.__getVirtualPosition()

    def set_position(self, value: int):
        """
         * Sets the current position of the stream relative to the data to be transformed.
         *
         * @param value
         * @throws IOError
        """
        if self.canWrite() and not self.__allowRangeWrite and value != 0:
            raise IOError() from \
                SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                                        "If you still want to take the risk you need to use SetAllowRangeWrite(true)")
        try:
            self.setVirtualPosition(value)
        except SalmonRangeExceededException as e:
            raise IOError() from e

    def canRead(self) -> bool:
        """
         * If the stream is readable (only if EncryptionMode == Decrypted)
         *
         * @return True if mode is decryption.
        """
        return self.__baseStream.canRead() and self.__encryptionMode == SalmonStream.EncryptionMode.Decrypt

    def canSeek(self) -> bool:
        """
         * If the stream is seekable (supported only if base stream is seekable).
         *
         * @return True if stream is seekable.
        """
        return self.__baseStream.canSeek()

    def canWrite(self) -> bool:
        """
         * If the stream is writeable (only if EncryptionMode is Encrypt)
         *
         * @return True if mode is decryption.
        """
        return self.__baseStream.canWrite() and self.__encryptionMode == SalmonStream.EncryptionMode.Encrypt

    def hasIntegrity(self) -> bool:
        """
         * If the stream has integrity enabled
        """
        return self.getChunkSize() > 0

    def __initIntegrity(self, integrity: bool, hashKey: bytearray, chunkSize: int):
        """
         * Initialize the integrity validator. This object is always associated with the
         * stream because in the case of a decryption stream that has already embedded integrity
         * we still need to calculate/skip the chunks.
         *
         * @param integrity
         * @param hashKey
         * @param chunkSize
         * @throws SalmonSecurityException
         * @throws SalmonIntegrityException
        """
        self.__salmonIntegrity = SalmonIntegrity(integrity, hashKey, chunkSize,
                                                 HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH)

    def __initStream(self):
        """
         * Init the stream.
         *
         * @throws IOError
        """
        self.__baseStream.set_position(self.getHeaderLength())

    def getHeaderLength(self) -> int:
        """
         * The length of the header data if the stream was initialized with a header.
         *
         * @return The header data length.
        """
        if self.__headerData is None:
            return 0
        else:
            return len(self.__headerData)

    def __initTransformer(self, key: bytearray, nonce: bytearray):
        """
         * To create the AES CTR mode we use ECB for AES with No Padding.
         * Initailize the Counter to the initial vector provided.
         * For each data block we increase the Counter and apply the EAS encryption on the Counter.
         * The encrypted Counter then will be xor-ed with the actual data block.
        """
        if key is None:
            raise SalmonSecurityException("Key is missing")
        if nonce is None:
            raise SalmonSecurityException("Nonce is missing")

        transformer = SalmonTransformerFactory.create(self.__providerType)
        transformer.init(key, nonce)
        transformer.resetCounter()

    def seek(self, offset: int, origin: RandomAccessStream.SeekOrigin) -> int:
        """
         * Seek to a specific position on the stream. This does not include the header and any hash Signatures.
         *
         * @param offset The offset that seek will use
         * @param origin If it is Begin the offset will be the absolute position from the start of the stream
         *               If it is Current the offset will be added to the current position of the stream
         *               If it is End the offset will be the absolute position starting from the end of the stream.
        """
        if origin == RandomAccessStream.SeekOrigin.Begin:
            self.set_position(offset)
        elif origin == RandomAccessStream.SeekOrigin.Current:
            self.set_position(self.get_position() + offset)
        elif origin == RandomAccessStream.SeekOrigin.End:
            self.set_position(self.length() - offset)
        return self.get_position()

    def setLength(self, value: int):
        """
         * Set the length of the base stream. Currently unsupported.
         *
         * @param value
        """
        raise NotImplementedError()

    def flush(self):
        """
         * Flushes any buffered data to the base stream.
        """
        if self.__baseStream is not None:
            self.__baseStream.flush()

    def close(self):
        """
         * Closes the stream and all resources associated with it (including the base stream).
         *
         * @throws IOError
        """
        self.closeStreams()

    def getCounter(self) -> bytearray:
        """
         * Returns the current Counter value.
         *
         * @return The current Counter value.
        """
        return self.__transformer.getCounter().copy()

    def getBlock(self) -> int:
        """
         * Returns the current Block value
        """
        return self.__transformer.getBlock()

    def getKey(self) -> bytearray:
        """
         * Returns a copy of the encryption key.
        """
        return self.__transformer.getKey().copy()

    def getHashKey(self) -> bytearray:
        """
         * Returns a copy of the hash key.
        """
        return self.__salmonIntegrity.get_key().copy()

    def getNonce(self) -> bytearray:
        """
         * Returns a copy of the initial vector.
        """
        return self.__transformer.getNonce().copy()

    def getChunkSize(self) -> int:
        """
         * Returns the Chunk size used to apply hash signature
        """
        return self.__salmonIntegrity.getChunkSize()

    def setAllowRangeWrite(self, value: bool):
        """
         * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
         * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
         * You should only use this setting for initial encryption with parallel streams and not for overwriting!
         *
         * @param value True to allow byte range encryption write operations
        """
        self.__allowRangeWrite = value

    def setFailSilently(self, value: bool):
        """
         * Set to True if you want the stream to fail silently when integrity cannot be verified.
         * In that case read() operations will return -1 instead of raising an exception.
         * This prevents 3rd party code like media players from crashing.
         *
         * @param value True to fail silently.
        """
        self.__failSilently = value

    def __setVirtualPosition(self, value: int):
        """
         * Set the virtual position of the stream.
         *
         * @param value
         * @throws IOError
         * @throws SalmonRangeExceededException
        """
        # we skip the header bytes and any hash values we have if the file has integrity set
        self.__baseStream.set_position(value)
        totalHashBytes: int = self.__salmonIntegrity.get_hash_data_length(self.__baseStream.self.get_position(), 0)
        self.__baseStream.set_position(self.__baseStream.self.get_position() + totalHashBytes)
        self.__baseStream.set_position(self.__baseStream.self.get_position() + self.getHeaderLength())
        self.__transformer.resetCounter()
        self.__transformer.syncCounter(self.get_position())

    def __getVirtualPosition(self) -> int:
        """
         * Returns the Virtual Position of the stream excluding the header and hash signatures.
        """
        totalHashBytes: int
        hashOffset: int = SalmonGenerator.HASH_RESULT_LENGTH if self.__salmonIntegrity.getChunkSize() > 0 else 0
        totalHashBytes = self.__salmonIntegrity.get_hash_data_length(self.__baseStream.self.get_position(), hashOffset)
        return self.__baseStream.self.get_position() - self.getHeaderLength() - totalHashBytes

    def __closeStreams(self):
        """
         * Close base stream
        """
        if self.__baseStream is not None:
            if self.canWrite():
                self.__baseStream.flush()
            self.__baseStream.close()

    def read(self, buffer: bytearray, offset: int, count: int) -> int:
        """
         * Decrypts the data from the baseStream and stores them in the buffer provided.
         *
         * @param buffer The buffer that the data will be stored after decryption
         * @param offset The start position on the buffer that data will be written.
         * @param count  The requested count of the data bytes that should be decrypted
         * @return The number of data bytes that were decrypted.
        """
        if self.get_position() == self.length():
            return -1
        alignedOffset: int = self.getAlignedOffset()
        bytes: int = 0
        pos: int = self.get_position()

        # if the base stream is not aligned for read
        if alignedOffset != 0:
            # read partially once
            self.set_position(self.get_position() - alignedOffset)
            nCount: int = self.__salmonIntegrity.getChunkSize() if self.__salmonIntegrity.getChunkSize() > 0 else SalmonGenerator.BLOCK_SIZE
            buff: bytearray = bytearray(nCount)
            bytes = self.read(buff, 0, nCount)
            bytes = min(bytes - alignedOffset, count)
            # if no more bytes to read from the stream
            if bytes <= 0:
                return -1
            buffer[offset:offset + bytes] = buff[alignedOffset:alignedOffset + bytes]
            self.set_position(pos + bytes)

        # if we have all bytes originally requested
        if bytes == count:
            return bytes

        # the base stream position should now be aligned
        # now we can now read the rest of the data.
        pos = self.get_position()
        nBytes: int = self.readFromStream(buffer, bytes + offset, count - bytes)
        self.set_position(pos + nBytes)
        return bytes + nBytes

    def __readFromStream(self, buffer: bytearray, offset: int, count: int):
        """
         * Decrypts the data from the baseStream and stores them in the buffer provided.
         * Use this only after you align the base stream to the chunk if integrity is enabled
         * or to the encryption block size.
         *
         * @param buffer The buffer that the data will be stored after decryption
         * @param offset The start position on the buffer that data will be written.
         * @param count  The requested count of the data bytes that should be decrypted
         * @return The number of data bytes that were decrypted.
         * @throws IOError Thrown if stream is not aligned.
        """
        if self.get_position() == self.length():
            return 0
        if self.__salmonIntegrity.getChunkSize() > 0 and self.get_position() % self.__salmonIntegrity.getChunkSize() != 0:
            raise IOError("All reads should be aligned to the chunks size: " + self.__salmonIntegrity.getChunkSize())
        elif self.__salmonIntegrity.getChunkSize() == 0 and self.get_position() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0:
            raise IOError("All reads should be aligned to the block size: " + SalmonAES256CTRTransformer.BLOCK_SIZE)

        pos: int = self.get_position()

        # if there are not enough data in the stream
        count = min(count, self.length() - self.get_position())

        # if there are not enough space in the buffer
        count = min(count, len(buffer) - offset)

        if count <= 0:
            return 0

        # make sure our buffer size is also aligned to the block or chunk
        bufferSize: int = self.getNormalizedBufferSize(True)

        bytes: int = 0
        while bytes < count:
            # read data and integrity signatures
            srcBuffer: bytearray = self.__readStreamData(bufferSize)
            try:
                integrityHashes: [] = None
                # if there are integrity hashes strip them and get the data chunks only
                if self.__salmonIntegrity.getChunkSize() > 0:
                    # get the integrity signatures
                    integrityHashes = self.__salmonIntegrity.getHashes(srcBuffer)
                    srcBuffer = self.__stripSignatures(srcBuffer, self.__salmonIntegrity.getChunkSize())
                destBuffer: bytearray = bytearray(len(srcBuffer))
                if self.__salmonIntegrity.use_integrity():
                    self.__salmonIntegrity.verifyHashes(integrityHashes, srcBuffer,
                                                        self.__headerData if pos == 0 and bytes == 0 else None)
                self.__transformer.decryptData(srcBuffer, 0, destBuffer, 0, len(srcBuffer))
                length: int = min(count - bytes, len(destBuffer))
                self.__writeToBuffer(destBuffer, 0, buffer, bytes + offset, length)
                bytes += length
                self.__transformer.syncCounter(self.get_position())
            except (SalmonSecurityException, SalmonRangeExceededException, SalmonIntegrityException) as ex:
                if isinstance(ex, SalmonIntegrityException) and self.__failSilently:
                    return -1
                raise IOError("Could not read from stream: ") from ex
        return bytes

    def write(self, buffer: bytearray, offset: int, count: int):
        """
         * Encrypts the data from the buffer and writes the result to the baseStream.
         * If you are using integrity you will need to align all write operations to the chunk size
         * otherwise align to the encryption block size.
         *
         * @param buffer The buffer that contains the data that will be encrypted
         * @param offset The offset in the buffer that the bytes will be encrypted.
         * @param count  The length of the bytes that will be encrypted.
         *
        """
        if self.__salmonIntegrity.getChunkSize() > 0 and self.get_position() % self.__salmonIntegrity.getChunkSize() != 0:
            raise IOError() from \
                SalmonIntegrityException("All write operations should be aligned to the chunks size: "
                                         + str(self.__salmonIntegrity.getChunkSize()))
        elif self.__salmonIntegrity.getChunkSize() == 0 and self.get_position() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0:
            raise IOError() from \
                SalmonIntegrityException("All write operations should be aligned to the block size: "
                                         + SalmonAES256CTRTransformer.BLOCK_SIZE)

        # if there are not enough data in the buffer
        count = min(count, len(buffer) - offset)

        # if there
        bufferSize: int = self.__getNormalizedBufferSize(False)

        pos: int = 0
        while pos < count:
            nBufferSize: int = min(bufferSize, count - pos)

            srcBuffer: bytearray = self.__readBufferData(buffer, pos, nBufferSize)
            if len(srcBuffer) == 0:
                break
            destBuffer: bytearray = bytearray(len(srcBuffer))

            try:
                self.__transformer.encryptData(srcBuffer, 0, destBuffer, 0, len(srcBuffer))
                integrityHashes: [] = self.__salmonIntegrity.generateHashes(destBuffer,
                                                                            self.__headerData if self.get_position() == 0 else None)
                pos += self.__writeToStream(destBuffer, self.__getChunkSize(), integrityHashes)
                self.__transformer.syncCounter(self.get_position())
            except SalmonSecurityException | SalmonRangeExceededException | SalmonIntegrityException as ex:
                raise IOError("Could not write to stream: ") from ex

    def __getAlignedOffset(self) -> int:
        """
         * Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
         * wrt to the encryption block size. Use this method to align a position to the
         * start of the block or chunk.
         *
         * @return
        """
        alignOffset: int
        if self.__salmonIntegrity.getChunkSize() > 0:
            alignOffset = self.get_position() % self.__salmonIntegrity.getChunkSize()
        else:
            alignOffset = self.get_position() % SalmonAES256CTRTransformer.BLOCK_SIZE
        return alignOffset

    def __getNormalizedBufferSize(self, includeHashes: bool) -> int:
        """
         * Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
         * wrt to the encryption block size. Use this method to ensure that buffer sizes request
         * via the API are aligned for read/writes and integrity processing.
         *
         * @return
        """
        bufferSize: int = SalmonDefaultOptions.getBufferSize()
        if self.getChunkSize() > 0:
            # buffer size should be a multiple of the chunk size if integrity is enabled
            partSize: int = self.getChunkSize()
            # if add the hash signatures

            if partSize < bufferSize:
                bufferSize = bufferSize // self.getChunkSize() * self.getChunkSize()
            else:
                bufferSize = partSize

            if includeHashes:
                bufferSize += bufferSize // self.getChunkSize() * SalmonGenerator.HASH_RESULT_LENGTH
        else:
            # buffer size should also be a multiple of the AES block size
            bufferSize = bufferSize // SalmonAES256CTRTransformer.BLOCK_SIZE * SalmonAES256CTRTransformer.BLOCK_SIZE

        return bufferSize

    def __readBufferData(self, buffer: bytearray, offset: int, count: int) -> bytearray:
        """
         * Read the data from the buffer
         *
         * @param buffer The source buffer.
         * @param offset The offset to start reading the data.
         * @param count  The number of requested bytes to read.
         * @return The array with the data that were read.
        """
        data: bytearray = bytearray(min(count, len(buffer) - offset))
        data[0:len(data)] = buffer[offset:len(data)]
        return data

    def __readStreamData(self, count: int) -> bytearray:
        """
         * Read the data from the base stream into the buffer.
         *
         * @param count The number of bytes to read.
         * @return The number of bytes read.
         * @throws IOError
        """
        data: bytearray = bytearray(min(count, self.__baseStream.length() - self.__baseStream.get_position()))
        self.__baseStream.read(data, 0, len(data))
        return data

    def __writeToBuffer(self, srcBuffer: bytearray, srcOffset: int, destBuffer: bytearray, destOffset: int, count: int):
        """
         * Write the buffer data to the destination buffer.
         *
         * @param srcBuffer  The source byte array.
         * @param srcOffset  The source byte offset.
         * @param destBuffer  The source byte array.
         * @param destOffset The destination byte offset.
         * @param count      The number of bytes to write.
        """
        destBuffer[destOffset:destOffset + count] = srcBuffer[srcOffset:srcOffset + count]

    def __writeToStream(self, buffer: bytearray, chunkSize: int, hashes: []) -> int:
        """
         * Write data to the base stream.
         *
         * @param buffer    The buffer to read from.
         * @param chunkSize The chunk segment size to use when writing the buffer.
         * @param hashes    The hash signature to write at the beginning of each chunk.
         * @return The number of bytes written.
         * @throws IOError
        """
        pos: int = 0
        chunk: int = 0
        if chunkSize <= 0:
            chunkSize = len(buffer)
        while pos < len(buffer):
            if hashes is not None:
                self.__baseStream.write(hashes[chunk], 0, hashes[chunk].length)
            length: int = min(chunkSize, len(buffer) - pos)
            self.__baseStream.write(buffer, pos, length)
            pos += length
            chunk += 1
        return pos

    def __stripSignatures(self, buffer: bytearray, chunkSize: int) -> bytearray:
        """
         * Strip hash signatures from the buffer.
         *
         * @param buffer    The buffer.
         * @param chunkSize The chunk size.
         * @return
        """
        bytes: int = len(buffer) // (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) * chunkSize
        if len(buffer) % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) != 0:
            bytes += len(buffer) % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) - SalmonGenerator.HASH_RESULT_LENGTH
        buff: bytearray = bytearray(bytes)
        index: int = 0
        for i in range(0, len(buffer), chunkSize + SalmonGenerator.HASH_RESULT_LENGTH):
            nChunkSize: int = min(chunkSize, len(buff) - index)
            end: int = i + SalmonGenerator.HASH_RESULT_LENGTH + nChunkSize
            buff[index:index + nChunkSize] = buffer[i + SalmonGenerator.HASH_RESULT_LENGTH:end]
            index += nChunkSize
        return buff

    class EncryptionMode:
        """
         * Encryption Mode
         *
         * @see #Encrypt
         * @see #Decrypt
        """

        Encrypt = 0,
        """
         * Encryption Mode used with a base stream as a target.
        """

        Decrypt = 1
        """
         * Decryption Mode used with a base stream as a source.
        """

    def isIntegrityEnabled(self) -> bool:
        """
         * True if the stream has integrity enabled.
         *
         * @return
        """
        return self.__salmonIntegrity.use_integrity()

    def getEncryptionMode(self) -> EncryptionMode:
        """
         * Get the encryption mode.
         *
         * @return
        """
        return self.__encryptionMode

    def isAllowRangeWrite(self) -> bool:
        """
         * Get the allowed range write option. This can check if you can use random access write.
         * This is generally not a good option since it prevents reusing the same nonce/counter.
         *
         * @return True if the stream allowed to seek and write.
        """
        return self.__allowRangeWrite
