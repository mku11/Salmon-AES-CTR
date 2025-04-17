package com.mku.salmon.streams;
/*
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
*/

import com.mku.salmon.Generator;
import com.mku.salmon.Header;
import com.mku.salmon.RangeExceededException;
import com.mku.salmon.SecurityException;
import com.mku.salmon.integrity.HMACSHA256Provider;
import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.transform.AesCTRTransformer;
import com.mku.salmon.transform.ICTRTransformer;
import com.mku.salmon.transform.TransformerFactory;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stream wrapper provides AES-256 encryption, decryption, and integrity verification of a data stream.
 */
public class AesStream extends RandomAccessStream {

    /**
     * Header data embedded in the stream if available.
     */
    private final Header header;

    /**
     * Mode to be used for this stream. This can only be set once.
     */
    private final EncryptionMode encryptionMode;

    /**
     * Allow seek and write.
     */
    private boolean allowRangeWrite;

    /**
     * Fail silently if integrity cannot be verified.
     */
    private boolean failSilently;

    /**
     * The base stream. When EncryptionMode is Encrypt this will be the target stream.
     * When EncryptionMode is Decrypt this will be the source stream.
     */
    private final RandomAccessStream baseStream;

    /**
     * Current global AES provider type.
     */
    private static ProviderType providerType = ProviderType.Default;

    /**
     * The transformer to use for encryption.
     */
    private ICTRTransformer transformer;

    /**
     * The integrity to use for hash signature creation and validation.
     */
    private Integrity integrity;

    /**
     * Align size for performance calculating the integrity when available.
     * @return The align size
     */
    @Override
    public int getAlignSize() {
        return integrity.getChunkSize() > 0 ? integrity.getChunkSize() : Generator.BLOCK_SIZE;
    }

    /**
     * Get the output size of the data to be transformed (encrypted or decrypted) including
     * header and hashes.
     * This can be used for efficient memory pre-allocation.
     *
     * @param mode   The {@link EncryptionMode} Encrypt or Decrypt.
     * @param length The length of the data to transform.
     * @param format The format to use, see {@link EncryptionFormat}
     * @return The size of the output data.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public static long getOutputSize(EncryptionMode mode, long length,
                                     EncryptionFormat format) {
        return getOutputSize(mode, length, format, Integrity.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Get the output size of the data to be transformed (encrypted or decrypted) including
     * header and hashes for the specified chunk size.
     * This can be used for efficient memory pre-allocation.
     *
     * @param mode      The {@link EncryptionMode} Encrypt or Decrypt.
     * @param length    The length of the data to transform.
     * @param format    The format to use, see {@link EncryptionFormat}
     * @param chunkSize the chunk size to be used with integrity
     * @return The size of the output data.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public static long getOutputSize(EncryptionMode mode, long length,
                                     EncryptionFormat format, int chunkSize) {
        long size = length;
        if (format == EncryptionFormat.Salmon) {
            if (mode == EncryptionMode.Encrypt) {
                size += Header.HEADER_LENGTH;
                if (chunkSize > 0) {
                    size += Integrity.getTotalHashDataLength(mode, length, chunkSize,
                            0, Generator.HASH_RESULT_LENGTH);
                }
            } else {
                size -= Header.HEADER_LENGTH;
                if (chunkSize > 0) {
                    size -= Integrity.getTotalHashDataLength(mode, length - Header.HEADER_LENGTH, chunkSize,
                            Generator.HASH_RESULT_LENGTH, Generator.HASH_RESULT_LENGTH);
                }
            }
        }
        return size;
    }

    /**
     * Instantiate a new encrypted stream with a key, a nonce, and a base stream.
     * <p>
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data to the baseStream.
     * The transformation is based on AES CTR Mode.
     * </p>
     *
     * @param key            The AES key that is used to encrypt decrypt
     * @param nonce          The nonce used for the initial counter
     * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param baseStream     The base Stream that will be used to read the data
     * @throws IOException        Thrown if there is an IO error.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
     */
    public AesStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                     RandomAccessStream baseStream)
            throws IOException {
        this(key, nonce, encryptionMode, baseStream, EncryptionFormat.Salmon, false, null, 0);
    }

    /**
     * Instantiate a new encrypted stream with a key, a nonce, a base stream, and optionally store
     * the nonce information in the header, see EncryptionFormat.
     * <p>
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data to the baseStream.
     * The transformation is based on AES CTR Mode.
     * </p>
     *
     * @param key            The AES key that is used to encrypt decrypt
     * @param nonce          The nonce used for the initial counter
     * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param baseStream     The base Stream that will be used to read the data
     * @param format         The format to use, see {@link EncryptionFormat}
     * @throws IOException        Thrown if there is an IO error.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
     */
    public AesStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                     RandomAccessStream baseStream, EncryptionFormat format)
            throws IOException {
        this(key, nonce, encryptionMode, baseStream, format, false, null, 0);
    }

    /**
     * Instantiate a new encrypted stream with a key, a nonce, a base stream, and optionally enable
     * integrity with a hash key and store the nonce and the integrity information in the header,
     * see EncryptionFormat.
     * <p>
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data to the baseStream.
     * The transformation is based on AES CTR Mode.
     * </p>
     *
     * @param key            The AES key that is used to encrypt decrypt
     * @param nonce          The nonce used for the initial counter. If mode is Decrypt and format is Salmon then use null.
     * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param baseStream     The base Stream that will be used to read the data
     * @param format         The format to use, see {@link EncryptionFormat}
     * @param integrity      True to enable integrity verification
     * @param hashKey        Hash key to be used with integrity
     * @throws IOException        Thrown if there is an IO error.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
     */
    public AesStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                     RandomAccessStream baseStream, EncryptionFormat format, boolean integrity, byte[] hashKey)
            throws IOException {
        this(key, nonce, encryptionMode, baseStream, format, integrity, hashKey, 0);
    }

    /**
     * Instantiate a new encrypted stream with a key, a nonce, a base stream, and optionally enable
     * integrity with a hash key and specified chunk size as well as store the nonce and the integrity
     * information in the header, see EncryptionFormat.
     * <p>
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data to the baseStream.
     * The transformation is based on AES CTR Mode.
     * </p>
     * <p>
     * Notes:
     * The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional 4 bytes counter.
     * The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
     *
     * @param key            The AES key that is used to encrypt decrypt
     * @param nonce          The nonce used for the initial counter
     * @param encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param baseStream     The base Stream that will be used to read the data
     * @param format         The format to use, see {@link EncryptionFormat}
     * @param integrity      True to enable integrity verification
     * @param hashKey        Hash key to be used with integrity
     * @param chunkSize      the chunk size to be used with integrity
     * @throws IOException        Thrown if there is an IO error.
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @see <a href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">Salmon README.md</a>
     */
    public AesStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                     RandomAccessStream baseStream, EncryptionFormat format, boolean integrity, byte[] hashKey, int chunkSize)
            throws IOException {
        if (format == EncryptionFormat.Generic) {
            integrity = false;
            hashKey = null;
            chunkSize = 0;
        }
        this.encryptionMode = encryptionMode;
        this.baseStream = baseStream;
        this.header = getOrCreateHeader(format, nonce, integrity, chunkSize);
        if (this.header != null) {
            chunkSize = this.header.getChunkSize();
            nonce = this.header.getNonce();
        } else {
            chunkSize = 0;
        }
        if (nonce == null)
            throw new SecurityException("Nonce is missing");
        initIntegrity(integrity, hashKey, chunkSize);
        initTransformer(key, nonce);
        initStream();
    }

    private Header getOrCreateHeader(EncryptionFormat format, byte[] nonce, boolean integrity, int chunkSize) throws IOException {
        if (format == EncryptionFormat.Salmon) {
            if (encryptionMode == EncryptionMode.Encrypt) {
                if (nonce == null)
                    throw new SecurityException("Nonce is missing");

                if (integrity && chunkSize <= 0)
                    chunkSize = Integrity.DEFAULT_CHUNK_SIZE;
                return Header.writeHeader(baseStream, nonce, chunkSize);
            }
            return Header.readHeaderData(baseStream);
        }
        return null;
    }

    /**
     * Initialize the integrity validator. This object is always associated with the
     * stream because in the case of a decryption stream that has already embedded integrity
     * we still need to calculate/skip the chunks.
     *
     * @param integrity True to use integrity checking
     * @param hashKey   The hash key to verify the data
     * @param chunkSize The chunk size
     * @throws SecurityException  Thrown if there is a security exception
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    private void initIntegrity(boolean integrity, byte[] hashKey, int chunkSize) {
        this.integrity = new Integrity(integrity, hashKey, chunkSize,
                new HMACSHA256Provider(), Generator.HASH_RESULT_LENGTH);
    }


    /**
     * To create the AES CTR mode we use ECB for AES with No Padding.
     * Initailize the Counter to the initial vector provided.
     * For each data block we increase the Counter and apply the EAS encryption on the Counter.
     * The encrypted Counter then will be xor-ed with the actual data block.
     */
    private void initTransformer(byte[] key, byte[] nonce) {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        transformer = TransformerFactory.create(providerType);
        transformer.init(key, nonce);
        transformer.resetCounter();
    }

    /**
     * Init the stream.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    private void initStream() throws IOException {
        setPosition(0);
    }

    /**
     * Set the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @param aesProviderType The provider Type.
     */
    public static void setAesProviderType(ProviderType aesProviderType) {
        AesStream.providerType = aesProviderType;
    }

    /**
     * Get the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @return The provider Type.
     */
    public static ProviderType getAesProviderType() {
        return AesStream.providerType;
    }

    /**
     * Provides the length of the actual transformed data (minus the header and integrity data).
     *
     * @return The length of the stream.
     */
    @Override
    public long getLength() {
        long totalHashBytes;
        int hashOffset = integrity.getChunkSize() > 0 ? Generator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = integrity.getHashDataLength(baseStream.getLength() - 1, hashOffset);
        return baseStream.getLength() - getHeaderLength() - totalHashBytes;
    }

    /**
     * Provides the position of the stream relative to the data to be transformed.
     *
     * @return The current position of the stream.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long getPosition() throws IOException {
        long totalHashBytes;
        int hashOffset = integrity.getChunkSize() > 0 ? Generator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = integrity.getHashDataLength(baseStream.getPosition(), hashOffset);
        return baseStream.getPosition() - getHeaderLength() - totalHashBytes;
    }

    /**
     * Sets the current position of the stream relative to the data to be transformed.
     *
     * @param value The new position
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setPosition(long value) throws IOException {
        if (canWrite() && !allowRangeWrite && value != 0) {
            throw new IOException(
                    new SecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                            "If you still want to take the risk you need to use SetAllowRangeWrite(true)"));
        }
        try {
            setVirtualPosition(value);
        } catch (RangeExceededException e) {
            throw new IOException(e);
        }
    }

    /**
     * If the stream is readable (only if EncryptionMode is EncryptionMode.Decrypt)
     *
     * @return True if mode is decryption.
     */
    public boolean canRead() {
        return baseStream.canRead() && encryptionMode == EncryptionMode.Decrypt;
    }

    /**
     * If the stream is seekable (supported only if base stream is seekable).
     *
     * @return True if stream is seekable.
     */
    public boolean canSeek() {
        return baseStream.canSeek();
    }

    /**
     * If the stream is writable (only if EncryptionMode is EncryptionMode.Encrypt)
     *
     * @return True if mode is decryption.
     */
    public boolean canWrite() {
        return baseStream.canWrite() && encryptionMode == EncryptionMode.Encrypt;
    }

    /**
     * If the stream has integrity embedded.
     *
     * @return True if the stream has integrity embedded.
     */
    public boolean hasIntegrity() {
        return getChunkSize() > 0;
    }

    /**
     * The length of the header data if the stream was initialized with a header.
     *
     * @return The header data length.
     */
    private long getHeaderLength() {
        if (header == null)
            return 0;
        else
            return header.getHeaderData().length;
    }

    /**
     * Seek to a specific position on the stream. This does not include the header and any hash Signatures.
     *
     * @param offset The offset that seek will use
     * @param origin If it is Begin the offset will be the absolute position from the start of the stream
     *               If it is Current the offset will be added to the current position of the stream
     *               If it is End the offset will be the absolute position starting from the end of the stream.
     */
    @Override
    public long seek(long offset, SeekOrigin origin) throws IOException {
        if (origin == SeekOrigin.Begin)
            setPosition(offset);
        else if (origin == SeekOrigin.Current)
            setPosition(getPosition() + offset);
        else if (origin == SeekOrigin.End)
            setPosition(getLength() - offset);
        return getPosition();
    }

    /**
     * Set the length of the base stream. Currently unsupported.
     *
     * @param value The new length
     */
    @Override
    public void setLength(long value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Flushes any buffered data to the base stream.
     */
    @Override
    public void flush() {
        if (this.baseStream != null) {
            baseStream.flush();
        }
    }

    /**
     * Closes the stream and all resources associated with it (including the base stream).
     *
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void close() throws IOException {
        closeStreams();
    }

    /**
     * Returns the current Counter value.
     *
     * @return The current Counter value.
     */
    public byte[] getCounter() {
        return transformer.getCounter().clone();
    }

    /**
     * Returns the current block value
     *
     * @return The current block
     */
    public long getBlock() {
        return transformer.getBlock();
    }

    /**
     * Returns a copy of the encryption key.
     *
     * @return The current key
     */
    public byte[] getKey() {
        return transformer.getKey().clone();
    }

    /**
     * Returns a copy of the hash key.
     *
     * @return The current hash key
     */
    public byte[] getHashKey() {
        return integrity.getKey().clone();
    }

    /**
     * Returns a copy of the nonce.
     *
     * @return The nonce.
     */
    public byte[] getNonce() {
        return transformer.getNonce().clone();
    }

    /**
     * Returns the chunk size used to apply hash signature
     *
     * @return The chunk size
     */
    public int getChunkSize() {
        return integrity.getChunkSize();
    }

    /**
     * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow byte range encryption write operations
     */
    public void setAllowRangeWrite(boolean value) {
        this.allowRangeWrite = value;
    }

    /**
     * Set to True if you want the stream to fail silently when integrity cannot be verified.
     * In that case read() operations will return -1 instead of raising an exception.
     * This prevents 3rd party code like media players from crashing.
     *
     * @param value True to fail silently.
     */
    public void setFailSilently(boolean value) {
        this.failSilently = value;
    }

    /**
     * Set the virtual position of the stream.
     *
     * @param value The new position
     * @throws IOException            Thrown if there is an IO error.
     * @throws RangeExceededException Thrown if the nonce exceeds its range
     */
    private void setVirtualPosition(long value) throws IOException {
        // we skip the header bytes and any hash values we have if the file has integrity set
        long totalHashBytes = integrity.getHashDataLength(value, 0);
        value += totalHashBytes + getHeaderLength();
        baseStream.setPosition(value);

        transformer.resetCounter();
        transformer.syncCounter(getPosition());
    }

    /**
     * Close base stream
     */
    private void closeStreams() throws IOException {
        if (baseStream != null) {
            if (canWrite())
                baseStream.flush();
            baseStream.close();
        }
    }

    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     *
     * @param buffer The buffer that the data will be stored after decryption
     * @param offset The start position on the buffer that data will be written.
     * @param count  The requested count of the data bytes that should be decrypted
     * @return The number of data bytes that were decrypted.
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        if (getPosition() == getLength())
            return -1;
        int alignedOffset = getAlignedOffset();
        int bytes = 0;
        long pos = getPosition();

        // if the base stream is not aligned for read
        if (alignedOffset != 0) {
            // read partially once
            setPosition(getPosition() - alignedOffset);
            int nCount = integrity.getChunkSize() > 0 ? integrity.getChunkSize() : Generator.BLOCK_SIZE;
            byte[] buff = new byte[nCount];
            bytes = read(buff, 0, nCount);
            bytes = Math.min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return -1;
            System.arraycopy(buff, alignedOffset, buffer, offset, bytes);
            setPosition(pos + bytes);

        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;

        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = getPosition();
        int nBytes = readFromStream(buffer, bytes + offset, count - bytes);
        setPosition(pos + nBytes);
        return bytes + nBytes;
    }

    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     * Use this only after you align the base stream to the chunk if integrity is enabled
     * or to the encryption block size.
     *
     * @param buffer The buffer that the data will be stored after decryption
     * @param offset The start position on the buffer that data will be written.
     * @param count  The requested count of the data bytes that should be decrypted
     * @return The number of data bytes that were decrypted.
     * @throws IOException Thrown if stream is not aligned.
     */
    private int readFromStream(byte[] buffer, int offset, int count) throws IOException {
        if (getPosition() == getLength())
            return 0;
        if (integrity.getChunkSize() > 0 && getPosition() % integrity.getChunkSize() != 0)
            throw new IOException("All reads should be aligned to the chunks size: " + integrity.getChunkSize());
        else if (integrity.getChunkSize() == 0 && getPosition() % AesCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("All reads should be aligned to the block size: " + AesCTRTransformer.BLOCK_SIZE);

        long pos = getPosition();

        // if there are not enough data in the stream
        count = (int) Math.min(count, getLength() - getPosition());

        // if there are not enough space in the buffer
        count = Math.min(count, buffer.length - offset);

        if (count <= 0)
            return 0;

        // make sure our buffer size is also aligned to the block or chunk
        int bufferSize = getNormalizedBufferSize(true);

        int bytes = 0;
        while (bytes < count) {
            // if there is no integrity make sure we don't overread for performance.
            int nBufferSize = getChunkSize() > 0 ? bufferSize : Math.min(bufferSize, count - bytes);

            // read data and integrity signatures
            byte[] srcBuffer = readStreamData(nBufferSize);
            try {
                byte[][] integrityHashes = null;
                // if there are integrity hashes strip them and get the data chunks only
                if (integrity.getChunkSize() > 0) {
                    // get the integrity signatures
                    integrityHashes = integrity.getHashes(srcBuffer);
                    srcBuffer = stripSignatures(srcBuffer, integrity.getChunkSize());
                }
                byte[] destBuffer = new byte[srcBuffer.length];
                if (integrity.useIntegrity()) {
                    integrity.verifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? header.getHeaderData() : null);
                }
                transformer.decryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                int len = Math.min(count - bytes, destBuffer.length);
                writeToBuffer(destBuffer, 0, buffer, bytes + offset, len);
                bytes += len;
                transformer.syncCounter(getPosition());
            } catch (SecurityException | RangeExceededException | IntegrityException ex) {
                if (ex instanceof IntegrityException && failSilently)
                    return -1;
                throw new IOException("Could not read from stream: ", ex);
            }
        }
        return bytes;
    }

    /**
     * Encrypts the data from the buffer and writes the result to the baseStream.
     * If you are using integrity you will need to align all write operations to the chunk size
     * otherwise align to the encryption block size.
     *
     * @param buffer The buffer that contains the data that will be encrypted
     * @param offset The offset in the buffer that the bytes will be encrypted.
     * @param count  The length of the bytes that will be encrypted.
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        if (integrity.getChunkSize() > 0 && getPosition() % integrity.getChunkSize() != 0)
            throw new IOException(
                    new IntegrityException("All write operations should be aligned to the chunks size: "
                            + integrity.getChunkSize()));
        else if (integrity.getChunkSize() == 0 && getPosition() % AesCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException(
                    new IntegrityException("All write operations should be aligned to the block size: "
                            + AesCTRTransformer.BLOCK_SIZE));

        // if there are not enough data in the buffer
        count = Math.min(count, buffer.length - offset);

        // if there
        int bufferSize = getNormalizedBufferSize(false);

        int pos = 0;
        while (pos < count) {
            int nBufferSize = Math.min(bufferSize, count - pos);

            byte[] srcBuffer = readBufferData(buffer, pos + offset, nBufferSize);
            if (srcBuffer.length == 0)
                break;
            byte[] destBuffer = new byte[srcBuffer.length];
            try {
                transformer.encryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                byte[][] integrityHashes = null;
                if (integrity.useIntegrity())
                    integrityHashes = integrity.generateHashes(destBuffer,
                            getPosition() == 0 ? header.getHeaderData() : null);
                pos += writeToStream(destBuffer, getChunkSize(), integrityHashes);
                transformer.syncCounter(getPosition());
            } catch (SecurityException | RangeExceededException | IntegrityException ex) {
                throw new IOException("Could not write to stream: ", ex);
            }
        }
    }

    /**
     * Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to align a position to the
     * start of the block or chunk.
     *
     * @return The aligned offset
     */
    private int getAlignedOffset() throws IOException {
        int alignOffset;
        if (integrity.getChunkSize() > 0) {
            alignOffset = (int) (getPosition() % integrity.getChunkSize());
        } else {
            alignOffset = (int) (getPosition() % AesCTRTransformer.BLOCK_SIZE);
        }
        return alignOffset;
    }

    /**
     * Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to ensure that buffer sizes request
     * via the API are aligned for read/writes and integrity processing.
     *
     * @return The buffer size
     */
    private int getNormalizedBufferSize(boolean includeHashes) {
        int bufferSize = Integrity.DEFAULT_CHUNK_SIZE;
        if (getChunkSize() > 0) {
            // buffer size should be a multiple of the chunk size if integrity is enabled
            int partSize = getChunkSize();
            // if add the hash signatures

            if (partSize < bufferSize) {
                bufferSize = bufferSize / getChunkSize() * getChunkSize();
            } else
                bufferSize = partSize;

            if (includeHashes)
                bufferSize += bufferSize / getChunkSize() * Generator.HASH_RESULT_LENGTH;
        } else {
            // buffer size should also be a multiple of the AES block size
            bufferSize = bufferSize / AesCTRTransformer.BLOCK_SIZE
                    * AesCTRTransformer.BLOCK_SIZE;
        }

        return bufferSize;
    }

    /**
     * Read the data from the buffer
     *
     * @param buffer The source buffer.
     * @param offset The offset to start reading the data.
     * @param count  The number of requested bytes to read.
     * @return The array with the data that were read.
     */
    private byte[] readBufferData(byte[] buffer, int offset, int count) {
        byte[] data = new byte[Math.min(count, buffer.length - offset)];
        System.arraycopy(buffer, offset, data, 0, data.length);
        return data;
    }

    /**
     * Read the data from the base stream into the buffer.
     *
     * @param count The number of bytes to read.
     * @return The number of bytes read.
     * @throws IOException Thrown if there is an IO error.
     */
    private byte[] readStreamData(int count) throws IOException {
        byte[] data = new byte[(int) Math.min(count, baseStream.getLength() - baseStream.getPosition())];
        int bytesRead;
        int totalBytesRead = 0;
        while ((bytesRead = baseStream.read(data, totalBytesRead, data.length - totalBytesRead)) > 0) {
            totalBytesRead += bytesRead;
        }
        return data;
    }

    /**
     * Write the buffer data to the destination buffer.
     *
     * @param srcBuffer  The source byte array.
     * @param srcOffset  The source byte offset.
     * @param destBuffer The source byte array.
     * @param destOffset The destination byte offset.
     * @param count      The number of bytes to write.
     */
    private void writeToBuffer(byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count) {
        System.arraycopy(srcBuffer, srcOffset, destBuffer, destOffset, count);
    }

    /**
     * Write data to the base stream.
     *
     * @param buffer    The buffer to read from.
     * @param chunkSize The chunk segment size to use when writing the buffer.
     * @param hashes    The hash signature to write at the beginning of each chunk.
     * @return The number of bytes written.
     * @throws IOException Thrown if there is an IO error.
     */
    private int writeToStream(byte[] buffer, int chunkSize, byte[][] hashes) throws IOException {
        int pos = 0;
        int chunk = 0;
        if (chunkSize <= 0)
            chunkSize = buffer.length;
        while (pos < buffer.length) {
            if (hashes != null) {
                baseStream.write(hashes[chunk], 0, hashes[chunk].length);
            }
            int len = Math.min(chunkSize, buffer.length - pos);
            baseStream.write(buffer, pos, len);
            pos += len;
            chunk++;
        }
        return pos;
    }

    /**
     * Strip hash signatures from the buffer.
     *
     * @param buffer    The buffer.
     * @param chunkSize The chunk size.
     * @return The data without the hash signatures
     */
    private byte[] stripSignatures(byte[] buffer, int chunkSize) {
        int bytes = buffer.length / (chunkSize + Generator.HASH_RESULT_LENGTH) * chunkSize;
        if (buffer.length % (chunkSize + Generator.HASH_RESULT_LENGTH) != 0)
            bytes += buffer.length % (chunkSize + Generator.HASH_RESULT_LENGTH) - Generator.HASH_RESULT_LENGTH;
        byte[] buff = new byte[bytes];
        int index = 0;
        for (int i = 0; i < buffer.length; i += chunkSize + Generator.HASH_RESULT_LENGTH) {
            int nChunkSize = Math.min(chunkSize, buff.length - index);
            System.arraycopy(buffer, i + Generator.HASH_RESULT_LENGTH, buff, index, nChunkSize);
            index += nChunkSize;
        }
        return buff;
    }

    /**
     * Get a native buffered stream to use with 3rd party libraries.
     * @return The native read stream
     */
    @Override
    public InputStream asReadStream()
    {
        if (canWrite())
            throw new RuntimeException("Stream is in write mode");

        // adjust for data integrity
        int backOffset = 32768;
        int bufferSize = 4 * 1024 * 1024;
        return new InputStreamWrapper(this, 1, bufferSize, backOffset, getAlignSize());
    }

    /**
     * Check if the stream has integrity enabled.
     *
     * @return True if integrity is enabled
     */
    public boolean isIntegrityEnabled() {
        return integrity.useIntegrity();
    }

    /**
     * Get the encryption mode see {@link EncryptionMode}.
     *
     * @return The encryption mode
     */
    public EncryptionMode getEncryptionMode() {
        return encryptionMode;
    }

    /**
     * Get the allowed range write option. This can check if you can use random access write.
     * This is generally not a good option since it prevents reusing the same nonce/counter.
     *
     * @return True if the stream allowed to seek and write.
     */
    public boolean isAllowRangeWrite() {
        return allowRangeWrite;
    }
}

