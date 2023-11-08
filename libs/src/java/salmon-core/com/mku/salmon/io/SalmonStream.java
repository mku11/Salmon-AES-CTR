package com.mku.salmon.io;
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

import com.mku.io.RandomAccessStream;
import com.mku.salmon.*;
import com.mku.salmon.integrity.HmacSHA256Provider;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmon.transform.ISalmonCTRTransformer;
import com.mku.salmon.transform.SalmonAES256CTRTransformer;
import com.mku.salmon.transform.SalmonTransformerFactory;

import java.io.IOException;

/**
 * Stream decorator provides AES256 encryption and decryption of stream.
 * Block data integrity is also supported.
 */
public class SalmonStream extends RandomAccessStream {

    /**
     * Header data embedded in the stream if available.
     */
    private final byte[] headerData;

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
    private ISalmonCTRTransformer transformer;

    /**
     * The integrity to use for hash signature creation and validation.
     */
    private SalmonIntegrity salmonIntegrity;

    /**
     * AES provider types. List of AES implementations that currently supported.
     *
     * @see #Default
     * @see #AesIntrinsics
     * @see #TinyAES
     */
    public enum ProviderType {
        /**
         * Default Java AES cipher.
         */
        Default,
        /**
         * Salmon builtin AES intrinsics. This needs the SalmonNative library to be loaded. @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
         */
        AesIntrinsics,
        /**
         * Tiny AES implementation. This needs the SalmonNative library to be loaded. @see <a href="https://github.com/mku11/Salmon-AES-CTR#readme">Salmon README.md</a>
         */
        TinyAES
    }

    /**
     * Instantiate a new Salmon stream with a plain base stream.
     *
     * @param key            The AES encryption key.
     * @param nonce          The nonce key.
     * @param encryptionMode The mode to use for encryption or decryption. This can only be set once.
     * @param baseStream     If EncryptionMode is Encrypt this will be the target stream
     *                       that data will be written to. If DecryptionMode is Decrypt this will be the
     *                       source stream to read the data from.
     * @throws SalmonSecurityException  If the stream cannot be decrypted or missing key or nonce.
     * @throws SalmonIntegrityException If the integrity of the stream is compromised.
     * @throws IOException              If the base stream is corrupt.
     */
    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        RandomAccessStream baseStream)
            throws SalmonSecurityException, SalmonIntegrityException, IOException {
        this(key, nonce, encryptionMode, baseStream, null, false, null, null);
    }

    /**
     * Instantiate a new Salmon stream with a base stream and embedded header data.
     *
     * @param key
     * @param nonce
     * @param encryptionMode
     * @param baseStream     If EncryptionMode is Encrypt this will be the target stream
     *                       that data will be written to. If DecryptionMode is Decrypt this will be the
     *                       source stream to read the data from.
     * @param headerData     The header data to embed if you use EncryptionMode = Encrypt.
     * @throws SalmonSecurityException  If the stream cannot be decrypted or missing key or nonce.
     * @throws SalmonIntegrityException If the integrity of the stream is compromised.
     * @throws IOException              If the base stream is corrupt.
     */
    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        RandomAccessStream baseStream, byte[] headerData)
            throws SalmonSecurityException, SalmonIntegrityException, IOException {
        this(key, nonce, encryptionMode, baseStream, headerData, false, null, null);
    }

    /**
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
     * @throws IOException
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     */
    public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        RandomAccessStream baseStream, byte[] headerData,
                        boolean integrity, Integer chunkSize, byte[] hashKey)
            throws IOException, SalmonSecurityException, SalmonIntegrityException {

        this.encryptionMode = encryptionMode;
        this.baseStream = baseStream;
        this.headerData = headerData;

        initIntegrity(integrity, hashKey, chunkSize);
        initTransformer(key, nonce);
        initStream();
    }

    /**
     * Set the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @param providerType The provider Type.
     */
    public static void setAesProviderType(ProviderType providerType) {
        SalmonStream.providerType = providerType;
    }

    /**
     * Get the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @return The provider Type.
     */
    public static ProviderType getAesProviderType() {
        return SalmonStream.providerType;
    }

    /**
     * Provides the length of the actual transformed data (minus the header and integrity data).
     *
     * @return The length of the stream.
     */
	 @Override
    public long length() {
        long totalHashBytes;
        int hashOffset = salmonIntegrity.getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = salmonIntegrity.getHashDataLength(baseStream.length() - 1, hashOffset);

        return baseStream.length() - getHeaderLength() - totalHashBytes;
    }

    /**
     * Provides the total length of the base stream including header and integrity data if available.
     *
     * @return The actual length of the base stream.
     */
    public long actualLength() {
        long totalHashBytes = 0;
        totalHashBytes += salmonIntegrity.getHashDataLength(baseStream.length() - 1, 0);
        if (canRead())
            return length();
        else if (canWrite())
            return baseStream.length() + getHeaderLength() + totalHashBytes;
        return 0;
    }

    /**
     * Provides the position of the stream relative to the data to be transformed.
     *
     * @return The current position of the stream.
     * @throws IOException
     */
	@Override
    public long position() throws IOException {
        return getVirtualPosition();
    }

    /**
     * Sets the current position of the stream relative to the data to be transformed.
     *
     * @param value
     * @throws IOException
     */
	@Override
    public void position(long value) throws IOException {
        if (canWrite() && !allowRangeWrite && value != 0) {
            throw new IOException(
                    new SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                            "If you still want to take the risk you need to use SetAllowRangeWrite(true)"));
        }
        try {
            setVirtualPosition(value);
        } catch (SalmonRangeExceededException e) {
            throw new IOException(e);
        }
    }

    /**
     * If the stream is readable (only if EncryptionMode == Decrypted)
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
     * If the stream is writeable (only if EncryptionMode is Encrypt)
     *
     * @return True if mode is decryption.
     */
    public boolean canWrite() {
        return baseStream.canWrite() && encryptionMode == EncryptionMode.Encrypt;
    }

    /**
     * If the stream has integrity enabled
     */
    public boolean hasIntegrity() {
        return getChunkSize() > 0;
    }

    /**
     * Initialize the integrity validator. This object is always associated with the
     * stream because in the case of a decryption stream that has already embedded integrity
     * we still need to calculate/skip the chunks.
     *
     * @param integrity
     * @param hashKey
     * @param chunkSize
     * @throws SalmonSecurityException
     * @throws SalmonIntegrityException
     */
    private void initIntegrity(boolean integrity, byte[] hashKey, Integer chunkSize)
            throws SalmonSecurityException, SalmonIntegrityException {
        salmonIntegrity = new SalmonIntegrity(integrity, hashKey, chunkSize,
                new HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH);
    }

    /**
     * Init the stream.
     *
     * @throws IOException
     */
    private void initStream() throws IOException {
        baseStream.position(getHeaderLength());
    }

    /**
     * The length of the header data if the stream was initialized with a header.
     *
     * @return The header data length.
     */
    private long getHeaderLength() {
        if (headerData == null)
            return 0;
        else
            return headerData.length;
    }

    /**
     * To create the AES CTR mode we use ECB for AES with No Padding.
     * Initailize the Counter to the initial vector provided.
     * For each data block we increase the Counter and apply the EAS encryption on the Counter.
     * The encrypted Counter then will be xor-ed with the actual data block.
     */
    private void initTransformer(byte[] key, byte[] nonce) throws SalmonSecurityException {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        transformer = SalmonTransformerFactory.create(providerType);
        transformer.init(key, nonce);
        transformer.resetCounter();
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
            position(offset);
        else if (origin == SeekOrigin.Current)
            position(position() + offset);
        else if (origin == SeekOrigin.End)
            position(length() - offset);
        return position();
    }

    /**
     * Set the length of the base stream. Currently unsupported.
     *
     * @param value
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
     * @throws IOException
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
     * Returns the current Block value
     */
    public long getBlock() {
        return transformer.getBlock();
    }

    /**
     * Returns a copy of the encryption key.
     */
    public byte[] getKey() {
        return transformer.getKey().clone();
    }

    /**
     * Returns a copy of the hash key.
     */
    public byte[] getHashKey() {
        return salmonIntegrity.getKey().clone();
    }

    /**
     * Returns a copy of the initial vector.
     */
    public byte[] getNonce() {
        return transformer.getNonce().clone();
    }

    /**
     * Returns the Chunk size used to apply hash signature
     */
    public int getChunkSize() {
        return salmonIntegrity.getChunkSize();
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
     * @param value
     * @throws IOException
     * @throws SalmonRangeExceededException
     */
    private void setVirtualPosition(long value) throws IOException, SalmonRangeExceededException {
        // we skip the header bytes and any hash values we have if the file has integrity set
        baseStream.position(value);
        long totalHashBytes = salmonIntegrity.getHashDataLength(baseStream.position(), 0);
        baseStream.position(baseStream.position() + totalHashBytes);
        baseStream.position(baseStream.position() + getHeaderLength());
        transformer.resetCounter();
        transformer.syncCounter(position());
    }

    /**
     * Returns the Virtual Position of the stream excluding the header and hash signatures.
     */
    private long getVirtualPosition() throws IOException {
        long totalHashBytes;
        int hashOffset = salmonIntegrity.getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = salmonIntegrity.getHashDataLength(baseStream.position(), hashOffset);
        return baseStream.position() - getHeaderLength() - totalHashBytes;
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
        if (position() == length())
            return -1;
        int alignedOffset = getAlignedOffset();
        int bytes = 0;
        long pos = position();

        // if the base stream is not aligned for read
        if (alignedOffset != 0) {
            // read partially once
            position(position() - alignedOffset);
            int nCount = salmonIntegrity.getChunkSize() > 0 ? salmonIntegrity.getChunkSize() : SalmonGenerator.BLOCK_SIZE;
            byte[] buff = new byte[nCount];
            bytes = read(buff, 0, nCount);
            bytes = Math.min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return -1;
            System.arraycopy(buff, alignedOffset, buffer, offset, bytes);
            position(pos + bytes);

        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;

        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = position();
        int nBytes = readFromStream(buffer, bytes + offset, count - bytes);
        position(pos + nBytes);
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
        if (position() == length())
            return 0;
        if (salmonIntegrity.getChunkSize() > 0 && position() % salmonIntegrity.getChunkSize() != 0)
            throw new IOException("All reads should be aligned to the chunks size: " + salmonIntegrity.getChunkSize());
        else if (salmonIntegrity.getChunkSize() == 0 && position() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("All reads should be aligned to the block size: " + SalmonAES256CTRTransformer.BLOCK_SIZE);

        long pos = position();

        // if there are not enough data in the stream
        count = (int) Math.min(count, length() - position());

        // if there are not enough space in the buffer
        count = Math.min(count, buffer.length - offset);

        if (count <= 0)
            return 0;

        // make sure our buffer size is also aligned to the block or chunk
        int bufferSize = getNormalizedBufferSize(true);

        int bytes = 0;
        while (bytes < count) {
            // read data and integrity signatures
            byte[] srcBuffer = readStreamData(bufferSize);
            try {
                byte[][] integrityHashes = null;
                // if there are integrity hashes strip them and get the data chunks only
                if (salmonIntegrity.getChunkSize() > 0) {
                    // get the integrity signatures
                    integrityHashes = salmonIntegrity.getHashes(srcBuffer);
                    srcBuffer = stripSignatures(srcBuffer, salmonIntegrity.getChunkSize());
                }
                byte[] destBuffer = new byte[srcBuffer.length];
                if (salmonIntegrity.useIntegrity()) {
                    salmonIntegrity.verifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? headerData : null);
                }
                transformer.decryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                int len = Math.min(count - bytes, destBuffer.length);
                writeToBuffer(destBuffer, 0, buffer, bytes + offset, len);
                bytes += len;
                transformer.syncCounter(position());
            } catch (SalmonSecurityException | SalmonRangeExceededException | SalmonIntegrityException ex) {
				if(ex instanceof SalmonIntegrityException && failSilently)
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
     *
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        if (salmonIntegrity.getChunkSize() > 0 && position() % salmonIntegrity.getChunkSize() != 0)
            throw new IOException(
                    new SalmonIntegrityException("All write operations should be aligned to the chunks size: "
                            + salmonIntegrity.getChunkSize()));
        else if (salmonIntegrity.getChunkSize() == 0 && position() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
            throw new IOException(
                    new SalmonIntegrityException("All write operations should be aligned to the block size: "
                            + SalmonAES256CTRTransformer.BLOCK_SIZE));

        // if there are not enough data in the buffer
        count = Math.min(count, buffer.length - offset);

        // if there
        int bufferSize = getNormalizedBufferSize(false);

        int pos = 0;
        while (pos < count) {
            int nBufferSize = Math.min(bufferSize, count - pos);

            byte[] srcBuffer = readBufferData(buffer, pos, nBufferSize);
            if (srcBuffer.length == 0)
                break;
            byte[] destBuffer = new byte[srcBuffer.length];
            try {
                transformer.encryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                byte[][] integrityHashes = salmonIntegrity.generateHashes(destBuffer, position() == 0 ? headerData : null);
                pos += writeToStream(destBuffer, getChunkSize(), integrityHashes);
                transformer.syncCounter(position());
            } catch (SalmonSecurityException | SalmonRangeExceededException | SalmonIntegrityException ex) {
                throw new IOException("Could not write to stream: ", ex);
            }
        }
    }


    /**
     * Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to align a position to the
     * start of the block or chunk.
     *
     * @return
     */
    private int getAlignedOffset() throws IOException {
        int alignOffset;
        if (salmonIntegrity.getChunkSize() > 0) {
            alignOffset = (int) (position() % salmonIntegrity.getChunkSize());
        } else {
            alignOffset = (int) (position() % SalmonAES256CTRTransformer.BLOCK_SIZE);
        }
        return alignOffset;
    }

    /**
     * Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to ensure that buffer sizes request
     * via the API are aligned for read/writes and integrity processing.
     *
     * @return
     */
    private int getNormalizedBufferSize(boolean includeHashes) {
        int bufferSize = SalmonDefaultOptions.getBufferSize();
        if (getChunkSize() > 0) {
            // buffer size should be a multiple of the chunk size if integrity is enabled
            int partSize = getChunkSize();
            // if add the hash signatures

            if (partSize < bufferSize) {
                bufferSize = bufferSize / getChunkSize() * getChunkSize();
            } else
                bufferSize = partSize;

            if (includeHashes)
                bufferSize += bufferSize / getChunkSize() * SalmonGenerator.HASH_RESULT_LENGTH;
        } else {
            // buffer size should also be a multiple of the AES block size
            bufferSize = bufferSize / SalmonAES256CTRTransformer.BLOCK_SIZE
                    * SalmonAES256CTRTransformer.BLOCK_SIZE;
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
     * @throws IOException
     */
    private byte[] readStreamData(int count) throws IOException {
        byte[] data = new byte[(int) Math.min(count, baseStream.length() - baseStream.position())];
        baseStream.read(data, 0, data.length);
        return data;
    }

    /**
     * Write the buffer data to the destination buffer.
     *
     * @param srcBuffer  The source byte array.
     * @param srcOffset  The source byte offset.
	 * @param destBuffer  The source byte array.
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
     * @throws IOException
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
     * @return
     */
    private byte[] stripSignatures(byte[] buffer, int chunkSize) {
        int bytes = buffer.length / (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) * chunkSize;
        if (buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) != 0)
            bytes += buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) - SalmonGenerator.HASH_RESULT_LENGTH;
        byte[] buff = new byte[bytes];
        int index = 0;
        for (int i = 0; i < buffer.length; i += chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) {
            int nChunkSize = Math.min(chunkSize, buff.length - index);
            System.arraycopy(buffer, i + SalmonGenerator.HASH_RESULT_LENGTH, buff, index, nChunkSize);
            index += nChunkSize;
        }
        return buff;
    }

    /**
     * Encryption Mode
     *
     * @see #Encrypt
     * @see #Decrypt
     */
    public enum EncryptionMode {
        /**
         * Encryption Mode used with a base stream as a target.
         */
        Encrypt,
        /**
         * Decryption Mode used with a base stream as a source.
         */
        Decrypt
    }

    /**
     * True if the stream has integrity enabled.
     *
     * @return
     */
    public boolean isIntegrityEnabled() {
        return salmonIntegrity.useIntegrity();
    }

    /**
     * Get the encryption mode.
     *
     * @return
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

