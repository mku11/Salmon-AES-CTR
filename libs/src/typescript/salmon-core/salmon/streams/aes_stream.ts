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

import { IOException } from "../../streams/io_exception.js";
import { RandomAccessStream, SeekOrigin } from "../../streams/random_access_stream.js";
import { ReadableStreamWrapper } from "../../streams/readable_stream_wrapper.js";
import { HmacSHA256Provider } from "../integrity/hmac_sha256_provider.js";
import { Integrity } from "../integrity/integrity.js";
import { IntegrityException } from "../integrity/integrity_exception.js";
import { Generator } from "../generator.js";
import { Header } from "../header.js";
import { SecurityException } from "../security_exception.js";
import { EncryptionFormat } from "./encryption_format.js";
import { ISalmonCTRTransformer } from "../transform/ictr_transformer.js";
import { AESCTRTransformer } from "../transform/aes_ctr_transformer.js";
import { TransformerFactory } from "../transform/transformer_factory.js";
import { EncryptionMode } from "./encryption_mode.js";
import { ProviderType } from "./provider_type.js";

/**
 * Stream wrapper provides AES256 encryption and decryption of a base stream.
 */
export class AesStream extends RandomAccessStream {

    /**
     * Header data embedded in the stream if available.
     */
    #header: Header | null = null;

    /**
     * Mode to be used for this stream. This can only be set once.
     */
    readonly #encryptionMode: EncryptionMode;

    /**
     * Format to be used for this stream. This can only be set once.
     */
    readonly #format: EncryptionFormat;

    /**
     * Allow seek and write.
     */
    #allowRangeWrite: boolean = false;

    /**
     * Fail silently if integrity cannot be verified.
     */
    #failSilently: boolean = false;

    /**
     * The base stream. When EncryptionMode is Encrypt this will be the target stream.
     * When EncryptionMode is Decrypt this will be the source stream.
     */
    readonly #baseStream: RandomAccessStream;

    static #providerType: ProviderType = ProviderType.Default;

    /**
     * The transformer to use for encryption.
     */
    #transformer: ISalmonCTRTransformer = TransformerFactory.create(AesStream.#providerType);

    /**
     * The integrity to use for hash signature creation and validation.
     */
    #integrity: Integrity = new Integrity(false, null, 0, new HmacSHA256Provider(), Generator.HASH_RESULT_LENGTH);

    #key: Uint8Array;
    #nonce: Uint8Array | null;
    #hashKey: Uint8Array | null;
    #chunkSize: number;
    #enableIntegrity: boolean;

    /**
     * Align size for performance calculating the integrity when available.
     * @returns The align size
     */
    public getAlignSize() {
        return this.#integrity.getChunkSize() > 0 ? this.#integrity.getChunkSize() : Generator.BLOCK_SIZE;
    }

    /**
     * Get the output size of the data to be transformed(encrypted or decrypted) including
     * header and hash without executing any operations. This can be used to prevent over-allocating memory
     * where creating your output buffers.
     *
     * @param {EncryptionMode} mode The EncryptionMode Encrypt or Decrypt.
     * @param {number} length The data length
     * @param {EncryptionFormat} format The format to use, see {@link EncryptionFormat}
     * @param {number} chunkSize The chunk size for integrity chunks.
     * @returns {Promise<number>} The size of the output data.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException Thrown if there is an IO error.
     */
    public static async getOutputSize(mode: EncryptionMode, length: number,
        format: EncryptionFormat, chunkSize: number = Integrity.DEFAULT_CHUNK_SIZE): Promise<number> {
            let size: number = length;
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
     * Instantiate a new Salmon stream with a base stream and optional header data and hash integrity.
     * If you read from the stream it will decrypt the data from the baseStream.
     * If you write to the stream it will encrypt the data from the baseStream.
     * The transformation is based on AES CTR Mode.
     * Notes:
     * The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional 4 bytes counter.
     * The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
     * @see {@link https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)|Salmon README.md}
     *
     * @param {Uint8Array} key            The AES key that is used to encrypt decrypt
     * @param {Uint8Array | null} nonce          The nonce used for the initial counter. If mode is Decrypt and you use Salmon format set to null.
     * @param {EncryptionMode} encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param {RandomAccessStream} baseStream     The base Stream that will be used to read the data
     * @param {EncryptionFormat} format         The format to use, see {@link EncryptionFormat}
     * @param {boolean} integrity      enable integrity
     * @param {Uint8Array | null} hashKey        Hash key to be used with integrity. Used with integrity=true.
     * @param {number} chunkSize      the chunk size to be used with integrity.
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    public constructor(key: Uint8Array, nonce: Uint8Array | null, encryptionMode: EncryptionMode,
        baseStream: RandomAccessStream, format: EncryptionFormat = EncryptionFormat.Salmon,
        integrity: boolean = false, hashKey: Uint8Array | null = null, chunkSize: number = 0) {
        super();
        if (format == EncryptionFormat.Generic) {
            integrity = false;
            hashKey = null;
            chunkSize = 0;
        }
        this.#encryptionMode = encryptionMode;
        this.#baseStream = baseStream;
        this.#key = key;
        this.#nonce = nonce;
        this.#format = format;
        this.#chunkSize = chunkSize;
        this.#hashKey = hashKey;
        this.#enableIntegrity = integrity;
    }

    /**
     * Initialize the salmon stream.
     */
    async #init(): Promise<void> {
        // init only once
        if (this.#transformer.getKey())
            return;
        this.#header = await this.#getOrCreateHeader(this.#format, this.#nonce, 
            this.#enableIntegrity, this.#chunkSize);
        if (this.#header) {
            this.#chunkSize = this.#header.getChunkSize();
            this.#nonce = this.#header.getNonce();
        } else {
			this.#chunkSize = 0;
		}
			
        if (this.#nonce == null)
            throw new SecurityException("Nonce is missing");

        this.#initIntegrity();
        await this.#initTransformer();
        await this.#initStream();
    }

    async #getOrCreateHeader(format: EncryptionFormat, nonce: Uint8Array | null, 
        integrity: boolean, chunkSize: number): Promise<Header|null> {
        if (format == EncryptionFormat.Salmon) {
            if (this.#encryptionMode == EncryptionMode.Encrypt) {
                if (this.#nonce == null)
                    throw new SecurityException("Nonce is missing");

                if (integrity && chunkSize <= 0)
                    chunkSize = Integrity.DEFAULT_CHUNK_SIZE;
                return await Header.writeHeader(this.#baseStream, this.#nonce, 
                    chunkSize);
            }
            return await Header.readHeaderData(this.#baseStream);
        }
        return null;
    }

    /**
     * To create the AES CTR mode we use ECB for AES with No Padding.
     * Initailize the Counter to the initial vector provided.
     * For each data block we increase the Counter and apply the EAS encryption on the Counter.
     * The encrypted Counter then will be xor-ed with the actual data block.
     * Note: for typescript since its async and we cannot run it in the constructor we delay 
     * until we run an opearation using the transformer.
     */
    async #initTransformer(): Promise<void> {
        if (this.#key == null)
            throw new SecurityException("Key is missing");
        if (this.#nonce == null)
            throw new SecurityException("Nonce is missing");

        this.#transformer = TransformerFactory.create(AesStream.#providerType);
        await this.#transformer.init(this.#key, this.#nonce);
        this.#transformer.resetCounter();        
    }

    /**
     * Init the stream.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    async #initStream() {
        await this.setPosition(0);
    }

    /**
     * Set the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @param {ProviderType} providerType The provider Type.
     */
    public static setAesProviderType(providerType: ProviderType): void {
        AesStream.#providerType = providerType;
    }

    /**
     * Get the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @returns {ProviderType} The provider Type.
     */
    public static getAesProviderType(): ProviderType {
        return AesStream.#providerType;
    }

    /**
     * Provides the length of the actual transformed data (minus the header and integrity data).
     *
     * @returns {Promise<number>} The length of the stream.
     */
    public async getLength(): Promise<number> {
        await this.#init();
        let totalHashBytes: number;
        let hashOffset: number = this.#integrity.getChunkSize() > 0 ? Generator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = this.#integrity.getHashDataLength(await this.#baseStream.getLength() - 1, hashOffset);

        return await this.#baseStream.getLength() - this.#getHeaderLength() - totalHashBytes;
    }

    /**
     * Provides the position of the stream relative to the data to be transformed.
     *
     * @returns {Promise<number>} The current position of the stream.
     * @throws IOException Thrown if there is an IO error.
     */
    public async getPosition(): Promise<number> {
        await this.#init();
        let totalHashBytes: number;
        let hashOffset: number = this.#integrity.getChunkSize() > 0 ? Generator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = this.#integrity.getHashDataLength(await this.#baseStream.getPosition(), hashOffset);
        return await this.#baseStream.getPosition() - this.#getHeaderLength() - totalHashBytes;
    }

    /**
     * Sets the current position of the stream relative to the data to be transformed.
     *
     * @param {number} value The new position
     * @returns {Promise<void>}
     * @throws IOException Thrown if there is an IO error.
     */
    public async setPosition(value: number): Promise<void> {
        if (await this.canWrite() && !this.#allowRangeWrite && value != 0) {
            throw new IOException("Could not set position", new SecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                "If you still want to take the risk you need to use SetAllowRangeWrite(true)"));
        }
        try {
            await this.#setVirtualPosition(value);
        } catch (e) {
			console.error(e);
            throw new IOException("Could not set position", e);
        }
    }

    /**
     * If the stream is readable (only if EncryptionMode == Decrypted)
     *
     * @returns {Promise<boolean>} True if mode is decryption.
     */
    public async canRead(): Promise<boolean> {
        return await this.#baseStream.canRead() && this.#encryptionMode == EncryptionMode.Decrypt;
    }

    /**
     * If the stream is seekable (supported only if base stream is seekable).
     *
     * @returns {Promise<boolean>} True if stream is seekable.
     */
    public async canSeek(): Promise<boolean> {
        return this.#baseStream.canSeek();
    }

    /**
     * If the stream is writeable (only if EncryptionMode is Encrypt)
     *
     * @returns {Promise<boolean>} True if mode is decryption.
     */
    public async canWrite(): Promise<boolean> {
        return await this.#baseStream.canWrite() && this.#encryptionMode == EncryptionMode.Encrypt;
    }

    /**
     * If the stream has integrity enabled
     * @returns {boolean}
     */
    public hasIntegrity(): boolean {
        return this.getChunkSize() > 0;
    }

    /**
     * Initialize the integrity validator. This object is always associated with the
     * stream because in the case of a decryption stream that has already embedded integrity
     * we still need to calculate/skip the chunks.
     *
     * @param {boolean} integrity True to enable integrity
     * @param {Uint8Array | null} hashKey The hash key for integrity
     * @param {number} chunkSize The chunk size
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    #initIntegrity(): void {
        this.#integrity = new Integrity(this.#enableIntegrity, this.#hashKey, this.#chunkSize,
            new HmacSHA256Provider(), Generator.HASH_RESULT_LENGTH);
    }

    /**
     * The length of the header data if the stream was initialized with a header.
     *
     * @returns {number} The header data length.
     */
    #getHeaderLength(): number {
        if (this.#header == null)
            return 0;
        else
            return this.#header.getHeaderData().length;
    }

    /**
     * Seek to a specific position on the stream. This does not include the header and any hash Signatures.
     *
     * @param {number} offset The offset that seek will use
     * @param {SeekOrigin.Begin} origin If it is Begin the offset will be the absolute position from the start of the stream
     *               If it is Current the offset will be added to the current position of the stream
     *               If it is End the offset will be the absolute position starting from the end of the stream.
     * @returns {Promise<number>} The new position after seeking.
     */
    public async seek(offset: number, origin: SeekOrigin): Promise<number> {
        if (origin == SeekOrigin.Begin)
            await this.setPosition(offset);
        else if (origin == SeekOrigin.Current)
            await this.setPosition(await this.getPosition() + offset);
        else if (origin == SeekOrigin.End)
            await this.setPosition(await this.getLength() - offset);
        return await this.getPosition();
    }

    /**
     * Set the length of the base stream. Currently unsupported.
     *
     * @param {number} value The new length.
     */
    public async setLength(value: number): Promise<void> {
        let pos: number = await this.getPosition();
        await this.setPosition(value);
        await this.setPosition(pos);
    }

    /**
     * Flushes any buffered data to the base stream.
     */
    public async flush(): Promise<void> {
        if (this.#baseStream) {
            await this.#baseStream.flush();
        }
    }

    /**
     * Closes the stream and all resources associated with it (including the base stream).
     *
     * @throws IOException Thrown if there is an IO error.
     */
    public async close(): Promise<void> {
        await this.#closeStreams();
    }

    /**
     * Returns the current Counter value.
     *
     * @returns {Promise<Uint8Array>} The current Counter value.
     */
    public async getCounter(): Promise<Uint8Array> {
        await this.#init();

        let ctr: Uint8Array | null = this.#transformer.getCounter();
        if (ctr == null)
            throw new SecurityException("No counter, init transformer first");
        return ctr.slice(0);
    }

    /**
     * Returns the current Block value
     * @returns {Promise<number>} The current block value.
     */
    public async getBlock(): Promise<number> {
        await this.#init();
        return this.#transformer.getBlock();
    }

    /**
     * Returns a copy of the encryption key.
     * @returns {Promise<Uint8Array>} A copy of the key.
     */
    public async getKey(): Promise<Uint8Array> {
        await this.#init();

        let key: Uint8Array | null = this.#transformer.getKey();
        if (key == null)
            throw new SecurityException("No key, init transformer first");
        return key.slice(0);
    }

    /**
     * Returns a copy of the hash key.
     * @returns {Uint8Array} A copy of the hash key
     */
    public getHashKey(): Uint8Array {
        return this.#integrity.getKey().slice(0);
    }

    /**
     * Returns a copy of the initial vector.
     * @returns {Promise<Uint8Array>} A copy of the initial vector
     */
    public async getNonce(): Promise<Uint8Array> {
        await this.#init();

        let nonce: Uint8Array | null = this.#transformer.getNonce();
        if (nonce == null)
            throw new SecurityException("No nonce, init transformer first");
        return nonce.slice(0);
    }

    /**
     * Returns the chunk size used to apply hash signature
     * @returns {number} The chunk size
     */
    public getChunkSize(): number {
        return this.#integrity.getChunkSize();
    }

    /**
     * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param {boolean} value True to allow byte range encryption write operations
     */
    public setAllowRangeWrite(value: boolean): void {
        this.#allowRangeWrite = value;
    }

    /**
     * Set to True if you want the stream to fail silently when integrity cannot be verified.
     * In that case read() operations will return -1 instead of raising an exception.
     * This prevents 3rd party code like media players from crashing.
     *
     * @param {boolean} value True to fail silently.
     */
    public setFailSilently(value: boolean): void {
        this.#failSilently = value;
    }

    /**
     * Set the virtual position of the stream.
     *
     * @param {number} value The new position
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
     */
    async #setVirtualPosition(value: number): Promise<void> {
        await this.#init();

        // we skip the header bytes and any hash values we have if the file has integrity set
        let totalHashBytes: number = this.#integrity.getHashDataLength(value, 0);
		value += totalHashBytes + this.#getHeaderLength();
        await this.#baseStream.setPosition(value);
		
        this.#transformer.resetCounter();
        this.#transformer.syncCounter(await this.getPosition());
    }

    /**
     * Close base stream
     */
    async #closeStreams(): Promise<void> {
        if (this.#baseStream) {
            if (await this.canWrite())
                await this.#baseStream.flush();
            await this.#baseStream.close();
        }
    }

    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     *
     * @param {Uint8Array} buffer The buffer that the data will be stored after decryption
     * @param {number} offset The start position on the buffer that data will be written.
     * @param {number} count  The requested count of the data bytes that should be decrypted
     * @returns {Promise<number>} The number of data bytes that were decrypted.
     */
    public async read(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        await this.#init();

        if (await this.getPosition() == await this.getLength())
            return -1;
        let alignedOffset: number = await this.#getAlignedOffset();
        let bytes: number = 0;
        let pos: number = await this.getPosition();

        // if the base stream is not aligned for read
        if (alignedOffset != 0) {
            // read partially once
            await this.setPosition(await this.getPosition() - alignedOffset);
            let nCount: number = this.#integrity.getChunkSize() > 0 ? this.#integrity.getChunkSize() : Generator.BLOCK_SIZE;
            let buff: Uint8Array = new Uint8Array(nCount);
            bytes = await this.read(buff, 0, nCount);
            bytes = Math.min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return -1;
            for (let i: number = 0; i < bytes; i++)
                buffer[offset + i] = buff[alignedOffset + i];
            await this.setPosition(pos + bytes);

        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;

        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = await this.getPosition();
        let nBytes: number = await this.#readFromStream(buffer, bytes + offset, count - bytes);
        await this.setPosition(pos + nBytes);
        return bytes + nBytes;
    }

    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     * Use this only after you align the base stream to the chunk if integrity is enabled
     * or to the encryption block size.
     *
     * @param {Uint8Array} buffer The buffer that the data will be stored after decryption
     * @param {number} offset The start position on the buffer that data will be written.
     * @param {number} count  The requested count of the data bytes that should be decrypted
     * @returns {Promise<number>} The number of data bytes that were decrypted.
     * @throws IOException Thrown if stream is not aligned.
     */
    async #readFromStream(buffer: Uint8Array, offset: number, count: number): Promise<number> {
        if (await this.getPosition() == await this.getLength())
            return 0;
        if (this.#integrity.getChunkSize() > 0 && await this.getPosition() % this.#integrity.getChunkSize() != 0)
            throw new IOException("All reads should be aligned to the chunks size: " + this.#integrity.getChunkSize());
        else if (this.#integrity.getChunkSize() == 0 && await this.getPosition() % AESCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("All reads should be aligned to the block size: " + AESCTRTransformer.BLOCK_SIZE);

        let pos: number = await this.getPosition();

        // if there are not enough data in the stream
        count = Math.min(count, await this.getLength() - await this.getPosition());

        // if there are not enough space in the buffer
        count = Math.min(count, buffer.length - offset);

        if (count <= 0)
            return 0;

        // make sure our buffer size is also aligned to the block or chunk
        let bufferSize: number = this.#getNormalizedBufferSize(true);

        let bytes: number = 0;
        while (bytes < count) {
            // if there is no integrity make sure we don't overread for performance.
            let nBufferSize = this.getChunkSize() > 0 ? bufferSize : Math.min(bufferSize, count - bytes);
       
            // read data and integrity signatures
            let srcBuffer: Uint8Array = await this.#readStreamData(nBufferSize);
            try {
                let integrityHashes: Uint8Array[] | null = null;
                // if there are integrity hashes strip them and get the data chunks only
                if (this.#integrity.getChunkSize() > 0) {
                    // get the integrity signatures
                    integrityHashes = this.#integrity.getHashes(srcBuffer);
                    srcBuffer = this.#stripSignatures(srcBuffer, this.#integrity.getChunkSize());
                }
                let destBuffer: Uint8Array = new Uint8Array(srcBuffer.length);
                if (this.#integrity.useIntegrity() && integrityHashes  && this.#header) {
                    await this.#integrity.verifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? this.#header?.getHeaderData() : null);
                }
                await this.#transformer.decryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                let len: number = Math.min(count - bytes, destBuffer.length);
                this.#writeToBuffer(destBuffer, 0, buffer, bytes + offset, len);
                bytes += len;
                this.#transformer.syncCounter(await this.getPosition());
            } catch (ex) {
                if (ex instanceof IntegrityException && this.#failSilently)
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
     * @param {Uint8Array} buffer The buffer that contains the data that will be encrypted
     * @param {number} offset The offset in the buffer that the bytes will be encrypted.
     * @param {number} count  The length of the bytes that will be encrypted.
     *
     */
    public async write(buffer: Uint8Array, offset: number, count: number): Promise<void> {
        await this.#init();

        if (this.#integrity.getChunkSize() > 0 && await this.getPosition() % this.#integrity.getChunkSize() != 0)
            throw new IOException("Error during write", new IntegrityException("All write operations should be aligned to the chunks size: "
                + this.#integrity.getChunkSize()));
        else if (this.#integrity.getChunkSize() == 0 && await this.getPosition() % AESCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("Error during write", new IntegrityException("All write operations should be aligned to the block size: "
                + AESCTRTransformer.BLOCK_SIZE));

        // if there are not enough data in the buffer
        count = Math.min(count, buffer.length - offset);

        // if there
        let bufferSize: number = this.#getNormalizedBufferSize(false);

        let pos: number = 0;
        while (pos < count) {
            let nBufferSize: number = Math.min(bufferSize, count - pos);

            let srcBuffer: Uint8Array = this.#readBufferData(buffer, pos + offset, nBufferSize);
            if (srcBuffer.length == 0)
                break;
            let destBuffer: Uint8Array = new Uint8Array(srcBuffer.length);
            try {
                await this.#transformer.encryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                let integrityHashes: Uint8Array[] | null = null;
                if(this.#integrity.useIntegrity() && this.#header)
                    integrityHashes = await this.#integrity.generateHashes(destBuffer, await this.getPosition() == 0 ? this.#header.getHeaderData() : null);
                pos += await this.#writeToStream(destBuffer, this.getChunkSize(), integrityHashes);
                this.#transformer.syncCounter(await this.getPosition());
            } catch (ex) {
                throw new IOException("Could not write to stream: ", ex);
            }
        }
    }

    /**
     * Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to align a position to the
     * start of the block or chunk.
     *
     * @returns {Promise<number>} The offset
     */
    async #getAlignedOffset(): Promise<number> {
        let alignOffset: number;
        if (this.#integrity.getChunkSize() > 0) {
            alignOffset = (await this.getPosition() % this.#integrity.getChunkSize());
        } else {
            alignOffset = (await this.getPosition() % AESCTRTransformer.BLOCK_SIZE);
        }
        return alignOffset;
    }

    /**
     * Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
     * wrt to the encryption block size. Use this method to ensure that buffer sizes request
     * via the API are aligned for read/writes and integrity processing.
     *
     * @returns {number} The buffer size
     */
    #getNormalizedBufferSize(includeHashes: boolean): number {
        let bufferSize: number = Integrity.DEFAULT_CHUNK_SIZE;
        if (this.getChunkSize() > 0) {
            // buffer size should be a multiple of the chunk size if integrity is enabled
            let partSize: number = this.getChunkSize();
            // if add the hash signatures

            if (partSize < bufferSize) {
                bufferSize = Math.floor(bufferSize / this.getChunkSize()) * this.getChunkSize();
            } else
                bufferSize = partSize;

            if (includeHashes)
                bufferSize += Math.floor(bufferSize / this.getChunkSize()) * Generator.HASH_RESULT_LENGTH;
        } else {
            // buffer size should also be a multiple of the AES block size
            bufferSize = Math.floor(bufferSize / AESCTRTransformer.BLOCK_SIZE)
                * AESCTRTransformer.BLOCK_SIZE;
        }

        return bufferSize;
    }

    /**
     * Read the data from the buffer
     *
     * @param {Uint8Array} buffer The source buffer.
     * @param {number} offset The offset to start reading the data.
     * @param {number} count  The number of requested bytes to read.
     * @returns {Uint8Array} The array with the data that were read.
     */
    #readBufferData(buffer: Uint8Array, offset: number, count: number): Uint8Array {
        let data: Uint8Array = new Uint8Array(Math.min(count, buffer.length - offset));
        for (let i = 0; i < data.length; i++)
            data[i] = buffer[offset + i];
        return data;
    }

    /**
     * Read the data from the base stream into the buffer.
     *
     * @param {number} count The number of bytes to read.
     * @returns {number} The number of bytes read.
     * @throws IOException Thrown if there is an IO error.
     */
    async #readStreamData(count: number): Promise<Uint8Array> {
        let data: Uint8Array = new Uint8Array(Math.min(count, await this.#baseStream.getLength() - await this.#baseStream.getPosition()));
        let totalBytesRead = 0;
        while(totalBytesRead < data.length) {
            let bytesRead = await this.#baseStream.read(data, totalBytesRead, data.length - totalBytesRead);
            if(bytesRead <= 0)
                break;
            totalBytesRead += bytesRead;
        }
        return data;
    }

    /**
     * Write the buffer data to the destination buffer.
     *
     * @param {Uint8Array} srcBuffer  The source byte array.
     * @param {number} srcOffset  The source byte offset.
     * @param {Uint8Array} destBuffer  The source byte array.
     * @param {number} destOffset The destination byte offset.
     * @param {number} count      The number of bytes to write.
     */
    #writeToBuffer(srcBuffer: Uint8Array, srcOffset: number, destBuffer: Uint8Array, destOffset: number, count: number): void {
        for (let i = 0; i < count; i++)
            destBuffer[destOffset + i] = srcBuffer[srcOffset + i];
    }

    /**
     * Write data to the base stream.
     *
     * @param {Uint8Array} buffer    The buffer to read from.
     * @param {number} chunkSize The chunk segment size to use when writing the buffer.
     * @param {Uint8Array[]} hashes    The hash signature to write at the beginning of each chunk.
     * @returns {number} The number of bytes written.
     * @throws IOException Thrown if there is an IO error.
     */
    async #writeToStream(buffer: Uint8Array, chunkSize: number, hashes: Uint8Array[] | null): Promise<number> {
        let pos: number = 0;
        let chunk: number = 0;
        if (chunkSize <= 0)
            chunkSize = buffer.length;
        while (pos < buffer.length) {
            if (hashes) {
                await this.#baseStream.write(hashes[chunk], 0, hashes[chunk].length);
            }
            let len: number = Math.min(chunkSize, buffer.length - pos);
            await this.#baseStream.write(buffer, pos, len);
            pos += len;
            chunk++;
        }
        return pos;
    }

    /**
     * Strip hash signatures from the buffer.
     *
     * @param {Uint8Array} buffer    The buffer.
     * @param {number} chunkSize The chunk size.
     * @returns {Uint8Array} The data without the hash signatures
     */
    #stripSignatures(buffer: Uint8Array, chunkSize: number): Uint8Array {
        let bytes: number = Math.floor(buffer.length / (chunkSize + Generator.HASH_RESULT_LENGTH)) * chunkSize;
        if (buffer.length % (chunkSize + Generator.HASH_RESULT_LENGTH) != 0)
            bytes += buffer.length % (chunkSize + Generator.HASH_RESULT_LENGTH) - Generator.HASH_RESULT_LENGTH;
        let buff: Uint8Array = new Uint8Array(bytes);
        let index: number = 0;
        for (let i = 0; i < buffer.length; i += chunkSize + Generator.HASH_RESULT_LENGTH) {
            let nChunkSize: number = Math.min(chunkSize, buff.length - index);
            for (let j = 0; j < nChunkSize; j++)
                buff[index + j] = buffer[i + Generator.HASH_RESULT_LENGTH + j];
            index += nChunkSize;
        }
        return buff;
    }

    /**
     * Get a native buffered stream to use with 3rd party libraries.
     * @returns The native read stream
     */
    public async asReadStream() : Promise<ReadableStream>
    {
        if (await this.canWrite())
            throw new Error("Stream is in write mode");

        // adjust for data integrity
        let backOffset = 32768;
        let bufferSize = 4 * 1024 * 1024;
        return ReadableStreamWrapper.createReadableStream(this, 1, bufferSize, backOffset, this.getAlignSize());
    }

    /**
     * True if the stream has integrity enabled.
     *
     * @returns {boolean} If integrity is enabled for this stream.
     */
    public isIntegrityEnabled(): boolean {
        return this.#integrity.useIntegrity();
    }

    /**
     * Get the encryption mode.
     *
     * @returns {EncryptionMode} The encryption mode.
     */
    public getEncryptionMode(): EncryptionMode {
        return this.#encryptionMode;
    }

    /**
     * Get the allowed range write option. This can check if you can use random access write.
     * This is generally not a good option since it prevents reusing the same nonce/counter.
     *
     * @returns {boolean} True if the stream allowed to seek and write.
     */
    public isAllowRangeWrite(): boolean {
        return this.#allowRangeWrite;
    }

    /**
     * Get the current transformer for this stream.
     * @returns {ISalmonCTRTransformer}
     */
    public getTransformer(): ISalmonCTRTransformer {
        return this.#transformer;
    }
}