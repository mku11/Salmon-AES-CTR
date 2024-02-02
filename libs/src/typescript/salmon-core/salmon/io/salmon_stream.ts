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

/**
     * Encryption Mode
     *
     * @see #Encrypt
     * @see #Decrypt
     */
enum EncryptionMode {
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
 * AES provider types. List of AES implementations that currently supported.
 *
 * @see #Default
 * @see #AesIntrinsics
 * @see #TinyAES
 */
enum ProviderType {
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
 * Stream decorator provides AES256 encryption and decryption of stream.
 * Block data integrity is also supported.
 */
class SalmonStream extends RandomAccessStream {

    /**
     * Header data embedded in the stream if available.
     */
    private readonly headerData: Uint8Array | null = null;

    /**
     * Mode to be used for this stream. This can only be set once.
     */
    private readonly encryptionMode: EncryptionMode;

    /**
     * Allow seek and write.
     */
    private allowRangeWrite: boolean = false;
	
	/**
     * Fail silently if integrity cannot be verified.
     */
    private failSilently: boolean = false;
	
    /**
     * The base stream. When EncryptionMode is Encrypt this will be the target stream.
     * When EncryptionMode is Decrypt this will be the source stream.
     */
    private readonly baseStream: RandomAccessStream;

    /**
     * Current global AES provider type.
     */
    private static providerType: ProviderType = ProviderType.Default;

    /**
     * The transformer to use for encryption.
     */
    private transformer: ISalmonCTRTransformer | null = null;

    /**
     * The integrity to use for hash signature creation and validation.
     */
    private salmonIntegrity: SalmonIntegrity | null = null;

    
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
    public constructor(key: Uint8Array, nonce: Uint8Array, encryptionMode: EncryptionMode,
        baseStream: RandomAccessStream, headerData: Uint8Array | null = null,
        integrity: boolean = false, chunkSize: number | null = null, hashKey: Uint8Array | null = null) {
        super();
        this.encryptionMode = encryptionMode;
        this.baseStream = baseStream;
        this.headerData = headerData;

        this.initIntegrity(integrity, hashKey, chunkSize);
        this.initTransformer(key, nonce);
        this.initStream();
    }

    /**
     * Set the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @param providerType The provider Type.
     */
    public static setAesProviderType(providerType: ProviderType): void {
        SalmonStream.providerType = providerType;
    }

    /**
     * Get the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @return The provider Type.
     */
    public static getAesProviderType(): ProviderType {
        return SalmonStream.providerType;
    }

    /**
     * Provides the length of the actual transformed data (minus the header and integrity data).
     *
     * @return The length of the stream.
     */
    public length(): number {
        let totalHashBytes: number;
        let hashOffset: number = this.salmonIntegrity.getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = this.salmonIntegrity.getHashDataLength(this.baseStream.length() - 1, hashOffset);

        return this.baseStream.length() - this.getHeaderLength() - totalHashBytes;
    }

    /**
     * Provides the total length of the base stream including header and integrity data if available.
     *
     * @return The actual length of the base stream.
     */
    public actualLength(): number {
        let totalHashBytes: number = 0;
        totalHashBytes += this.salmonIntegrity.getHashDataLength(this.baseStream.length() - 1, 0);
        if (this.canRead())
            return this.length();
        else if (this.canWrite())
            return this.baseStream.length() + this.getHeaderLength() + totalHashBytes;
        return 0;
    }

    /**
     * Provides the position of the stream relative to the data to be transformed.
     *
     * @return The current position of the stream.
     * @throws IOException
     */
    public getPosition(): number{
        return this.getVirtualPosition();
    }

    /**
     * Sets the current position of the stream relative to the data to be transformed.
     *
     * @param value
     * @throws IOException
     */
    public setPosition(value: number) : void {
        if (this.canWrite() && !this.allowRangeWrite && value != 0) {
            //throw new Error(
                    throw new SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                        "If you still want to take the risk you need to use SetAllowRangeWrite(true)")
            //);
        }
        try {
            this.setVirtualPosition(value);
        } catch (e) {
            throw e;
        }
    }

    /**
     * If the stream is readable (only if EncryptionMode == Decrypted)
     *
     * @return True if mode is decryption.
     */
    public canRead(): boolean {
        return this.baseStream.canRead() && this.encryptionMode == EncryptionMode.Decrypt;
    }

    /**
     * If the stream is seekable (supported only if base stream is seekable).
     *
     * @return True if stream is seekable.
     */
    public canSeek(): boolean {
        return this.baseStream.canSeek();
    }

    /**
     * If the stream is writeable (only if EncryptionMode is Encrypt)
     *
     * @return True if mode is decryption.
     */
    public canWrite(): boolean {
        return this.baseStream.canWrite() && this.encryptionMode == EncryptionMode.Encrypt;
    }

    /**
     * If the stream has integrity enabled
     */
    public hasIntegrity(): boolean {
        return this.getChunkSize() > 0;
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
    private initIntegrity(integrity: boolean, hashKey: Uint8Array, chunkSize: number | null): void{
        this.salmonIntegrity = new SalmonIntegrity(integrity, hashKey, chunkSize,
                new HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH);
    }

    /**
     * Init the stream.
     *
     * @throws IOException
     */
    private initStream() : void {
        this.baseStream.position(this.getHeaderLength());
    }

    /**
     * The length of the header data if the stream was initialized with a header.
     *
     * @return The header data length.
     */
    private getHeaderLength(): number {
        if (this.headerData == null)
            return 0;
        else
            return this.headerData.length;
    }

    /**
     * To create the AES CTR mode we use ECB for AES with No Padding.
     * Initailize the Counter to the initial vector provided.
     * For each data block we increase the Counter and apply the EAS encryption on the Counter.
     * The encrypted Counter then will be xor-ed with the actual data block.
     */
    private initTransformer(key: Uint8Array, nonce: Uint8Array) : void {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        this.transformer = SalmonTransformerFactory.create(SalmonStream.providerType);
        this.transformer.init(key, nonce);
        this.transformer.resetCounter();
    }

    /**
     * Seek to a specific position on the stream. This does not include the header and any hash Signatures.
     *
     * @param offset The offset that seek will use
     * @param origin If it is Begin the offset will be the absolute position from the start of the stream
     *               If it is Current the offset will be added to the current position of the stream
     *               If it is End the offset will be the absolute position starting from the end of the stream.
     */
    public seek(offset: number, origin: SeekOrigin): number {
        if (origin == SeekOrigin.Begin)
            this.setPosition(offset);
        else if (origin == SeekOrigin.Current)
            this.setPosition(this.getPosition() + offset);
        else if (origin == SeekOrigin.End)
            this.setPosition(this.length() - offset);
        return this.getPosition();
    }

    /**
     * Set the length of the base stream. Currently unsupported.
     *
     * @param value
     */
    public setLength(value: number): void {
        throw new Error();
    }

    /**
     * Flushes any buffered data to the base stream.
     */
    public flush() : void {
        if (this.baseStream != null) {
            this.baseStream.flush();
        }
    }

    /**
     * Closes the stream and all resources associated with it (including the base stream).
     *
     * @throws IOException
     */
    public close() : void {
        this.closeStreams();
    }

    /**
     * Returns the current Counter value.
     *
     * @return The current Counter value.
     */
    public getCounter(): Uint8Array{
        return this.transformer.getCounter().clone();
    }

    /**
     * Returns the current Block value
     */
    public getBlock(): number {
        return this.transformer.getBlock();
    }

    /**
     * Returns a copy of the encryption key.
     */
    public getKey(): Uint8Array {
        return this.transformer.getKey().clone();
    }

    /**
     * Returns a copy of the hash key.
     */
    public getHashKey() : Uint8Array{
        return this.salmonIntegrity.getKey().clone();
    }

    /**
     * Returns a copy of the initial vector.
     */
    public getNonce() : Uint8Array{
        return this.transformer.getNonce().clone();
    }

    /**
     * Returns the Chunk size used to apply hash signature
     */
    public getChunkSize(): number {
        return this.salmonIntegrity.getChunkSize();
    }

    /**
     * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param value True to allow byte range encryption write operations
     */
    public setAllowRangeWrite(value: boolean) : void {
        this.allowRangeWrite = value;
    }
	
	/**
     * Set to True if you want the stream to fail silently when integrity cannot be verified.
     * In that case read() operations will return -1 instead of raising an exception.
	 * This prevents 3rd party code like media players from crashing.
     *
     * @param value True to fail silently.
     */
    public setFailSilently(value: boolean) : void {
        this.failSilently = value;
    }

    /**
     * Set the virtual position of the stream.
     *
     * @param value
     * @throws IOException
     * @throws SalmonRangeExceededException
     */
    private setVirtualPosition(value: number) : void {
        // we skip the header bytes and any hash values we have if the file has integrity set
        this.baseStream.position(value);
        let totalHashBytes: number = this.salmonIntegrity.getHashDataLength(this.baseStream.position(), 0);
        this.baseStream.setPosition(this.baseStream.position() + totalHashBytes);
        this.baseStream.setPosition(this.baseStream.position() + this.getHeaderLength());
        this.transformer.resetCounter();
        this.transformer.syncCounter(getPosition());
    }

    /**
     * Returns the Virtual Position of the stream excluding the header and hash signatures.
     */
    private getVirtualPosition(): number{
        let totalHashBytes: number;
        let hashOffset: number = this.salmonIntegrity.getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = this.salmonIntegrity.getHashDataLength(this.baseStream.position(), hashOffset);
        return this.baseStream.position() - this.getHeaderLength() - totalHashBytes;
    }

    /**
     * Close base stream
     */
    private closeStreams() : void {
        if (this.baseStream != null) {
            if (this.canWrite())
                this.baseStream.flush();
            this.baseStream.close();
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
    public read(buffer: Uint8Array, offset: number, count: number): number {
        if (this.getPosition() == this.length())
            return -1;
        let alignedOffset: number = this.getAlignedOffset();
        let bytes: number = 0;
        let pos: number = this.getPosition();

        // if the base stream is not aligned for read
        if (alignedOffset != 0) {
            // read partially once
            this.setPosition(this.getPosition() - alignedOffset);
            let nCount: number = this.salmonIntegrity.getChunkSize() > 0 ? this.salmonIntegrity.getChunkSize() : SalmonGenerator.BLOCK_SIZE;
            let buff: Uint8Array = new Uint8Array(nCount);
            bytes = this.read(buff, 0, nCount);
            bytes = Math.min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return -1;
            for (let i: number = 0; i < bytes; i++)
                buffer[offset + i] = buff[alignedOffset + i];
            this.setPosition(pos + bytes);

        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;

        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = this.getPosition();
        let nBytes: number = this.readFromStream(buffer, bytes + offset, count - bytes);
        this.setPosition(pos + nBytes);
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
    private readFromStream(buffer: Uint8Array, offset: number, count: number): number {
        if (this.getPosition() == this.length())
            return 0;
        if (this.salmonIntegrity.getChunkSize() > 0 && this.getPosition() % this.salmonIntegrity.getChunkSize() != 0)
            throw new Error("All reads should be aligned to the chunks size: " + this.salmonIntegrity.getChunkSize());
        else if (this.salmonIntegrity.getChunkSize() == 0 && this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
            throw new Error("All reads should be aligned to the block size: " + SalmonAES256CTRTransformer.BLOCK_SIZE);

        let pos: number = this.getPosition();

        // if there are not enough data in the stream
        count = Math.min(count, this.length() - this.getPosition());

        // if there are not enough space in the buffer
        count = Math.min(count, buffer.length - offset);

        if (count <= 0)
            return 0;

        // make sure our buffer size is also aligned to the block or chunk
        let bufferSize: number = this.getNormalizedBufferSize(true);

        let bytes: number = 0;
        while (bytes < count) {
            // read data and integrity signatures
            let srcBuffer: Uint8Array = this.readStreamData(bufferSize);
            try {
                let integrityHashes: Array<Uint8Array> | null = null;
                // if there are integrity hashes strip them and get the data chunks only
                if (this.salmonIntegrity.getChunkSize() > 0) {
                    // get the integrity signatures
                    integrityHashes = this.salmonIntegrity.getHashes(srcBuffer);
                    srcBuffer = this.stripSignatures(srcBuffer, this.salmonIntegrity.getChunkSize());
                }
                let destBuffer: Uint8Array = new Uint8Array(srcBuffer.length);
                if (this.salmonIntegrity.useIntegrity()) {
                    this.salmonIntegrity.verifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? headerData : null);
                }
                this.transformer.decryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                let len: number = Math.min(count - bytes, destBuffer.length);
                this.writeToBuffer(destBuffer, 0, buffer, bytes + offset, len);
                bytes += len;
                this.transformer.syncCounter(this.getPosition());
            } catch (ex) {
                if (ex instanceof SalmonIntegrityException && this.failSilently)
					return -1;
                throw new Error("Could not read from stream: ", ex);
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
    public write(buffer: Uint8Array, offset: number, count: number) : void {
        if (this.salmonIntegrity.getChunkSize() > 0 && this.getPosition() % this.salmonIntegrity.getChunkSize() != 0)
            throw new Error(
                    new SalmonIntegrityException("All write operations should be aligned to the chunks size: "
                        + this.salmonIntegrity.getChunkSize()));
        else if (this.salmonIntegrity.getChunkSize() == 0 && this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
            throw new Error(
                    new SalmonIntegrityException("All write operations should be aligned to the block size: "
                            + SalmonAES256CTRTransformer.BLOCK_SIZE));

        // if there are not enough data in the buffer
        count = Math.min(count, buffer.length - offset);

        // if there
        let bufferSize: number = this.getNormalizedBufferSize(false);

        let pos: number = 0;
        while (pos < count) {
            let nBufferSize: number = Math.min(bufferSize, count - pos);

            let srcBuffer: Uint8Array = this.readBufferData(buffer, pos, nBufferSize);
            if (srcBuffer.length == 0)
                break;
            let destBuffer: Uint8Array = new Uint8Array(srcBuffer.length);
            try {
                this.transformer.encryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                let integrityHashes: Array<Uint8Array> = this.salmonIntegrity.generateHashes(destBuffer, this.getPosition() == 0 ? this.headerData : null);
                pos += this.writeToStream(destBuffer, this.getChunkSize(), integrityHashes);
                this.transformer.syncCounter(this.getPosition());
            } catch (ex) {
                throw new Error("Could not write to stream: " + ex);
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
    private getAlignedOffset(): number {
        let alignOffset: number;
        if (this.salmonIntegrity.getChunkSize() > 0) {
            alignOffset = (this.getPosition() % this.salmonIntegrity.getChunkSize());
        } else {
            alignOffset = (this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE);
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
    private getNormalizedBufferSize(includeHashes: boolean): number {
        let bufferSize: number = SalmonDefaultOptions.getBufferSize();
        if (this.getChunkSize() > 0) {
            // buffer size should be a multiple of the chunk size if integrity is enabled
            let partSize: number = this.getChunkSize();
            // if add the hash signatures

            if (partSize < bufferSize) {
                bufferSize = bufferSize / this.getChunkSize() * this.getChunkSize();
            } else
                bufferSize = partSize;

            if (includeHashes)
                bufferSize += bufferSize / this.getChunkSize() * SalmonGenerator.HASH_RESULT_LENGTH;
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
    private readBufferData(buffer: Uint8Array, offset: number, count: number): Uint8Array {
        let data: Uint8Array = new Uint8Array(Math.min(count, buffer.length - offset));
        for (let i = 0; i < data.length; i++)
            data[i] = buffer[offset + i];
        return data;
    }

    /**
     * Read the data from the base stream into the buffer.
     *
     * @param count The number of bytes to read.
     * @return The number of bytes read.
     * @throws IOException
     */
    private readStreamData(count: number): Uint8Array  {
        let data: Uint8Array = new Uint8Array(Math.min(count, this.baseStream.length() - this.baseStream.position()));
        this.baseStream.read(data, 0, data.length);
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
    private writeToBuffer(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): void {
        for (let i = 0; i < count; i++)
            destBuffer[destOffset + i] = srcBuffer[srcOffset + i];
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
    private writeToStream(buffer: Uint8Array, chunkSize: number, hashes: Array<Uint8Array>): number{
        let pos: number = 0;
        let chunk: number = 0;
        if (chunkSize <= 0)
            chunkSize = buffer.length;
        while (pos < buffer.length) {
            if (hashes != null) {
                this.baseStream.write(hashes[chunk], 0, hashes[chunk].length);
            }
            let len: number = Math.min(chunkSize, buffer.length - pos);
            this.baseStream.write(buffer, pos, len);
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
    private stripSignatures(buffer: Uint8Array, chunkSize: number): Uint8Array {
        let bytes: number = buffer.length / (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) * chunkSize;
        if (buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) != 0)
            bytes += buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) - SalmonGenerator.HASH_RESULT_LENGTH;
        let buff: Uint8Array = new Uint8Array(bytes);
        let index: number = 0;
        for (let i = 0; i < buffer.length; i += chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) {
            let nChunkSize: number = Math.min(chunkSize, buff.length - index);
            for (let j = 0; j < nChunkSize; j++)
                buff[index + j] = buffer[i + SalmonGenerator.HASH_RESULT_LENGTH + j];
            index += nChunkSize;
        }
        return buff;
    }

    /**
     * True if the stream has integrity enabled.
     *
     * @return
     */
    public isIntegrityEnabled(): boolean {
        return this.salmonIntegrity.useIntegrity();
    }

    /**
     * Get the encryption mode.
     *
     * @return
     */
    public getEncryptionMode(): EncryptionMode{
        return this.encryptionMode;
    }

    /**
     * Get the allowed range write option. This can check if you can use random access write.
     * This is generally not a good option since it prevents reusing the same nonce/counter.
     *
     * @return True if the stream allowed to seek and write.
     */
    public isAllowRangeWrite(): boolean {
        return this.allowRangeWrite;
    }
}

