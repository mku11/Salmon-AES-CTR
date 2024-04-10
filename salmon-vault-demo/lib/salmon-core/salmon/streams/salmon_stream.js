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
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _SalmonStream_instances, _a, _SalmonStream_headerData, _SalmonStream_encryptionMode, _SalmonStream_allowRangeWrite, _SalmonStream_failSilently, _SalmonStream_baseStream, _SalmonStream_providerType, _SalmonStream_transformer, _SalmonStream_salmonIntegrity, _SalmonStream_bufferSize, _SalmonStream_key, _SalmonStream_nonce, _SalmonStream_init, _SalmonStream_initTransformer, _SalmonStream_initStream, _SalmonStream_initIntegrity, _SalmonStream_getHeaderLength, _SalmonStream_setVirtualPosition, _SalmonStream_closeStreams, _SalmonStream_readFromStream, _SalmonStream_getAlignedOffset, _SalmonStream_getNormalizedBufferSize, _SalmonStream_readBufferData, _SalmonStream_readStreamData, _SalmonStream_writeToBuffer, _SalmonStream_writeToStream, _SalmonStream_stripSignatures;
import { IOException } from "../../streams/io_exception.js";
import { MemoryStream } from "../../streams/memory_stream.js";
import { RandomAccessStream, SeekOrigin } from "../../streams/random_access_stream.js";
import { HmacSHA256Provider } from "../../integrity/hmac_sha256_provider.js";
import { SalmonIntegrity } from "../integrity/salmon_integrity.js";
import { IntegrityException } from "../../integrity/integrity_exception.js";
import { SalmonGenerator } from "../salmon_generator.js";
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { SalmonAES256CTRTransformer } from "../transform/salmon_aes256_ctr_transformer.js";
import { SalmonTransformerFactory } from "../transform/salmon_transformer_factory.js";
import { EncryptionMode } from "./encryption_mode.js";
import { ProviderType } from "./provider_type.js";
/**
 * Stream decorator provides AES256 encryption and decryption of stream.
 * Block data integrity is also supported.
 */
export class SalmonStream extends RandomAccessStream {
    /**
     * Get the output size of the data to be transformed(encrypted or decrypted) including
     * header and hash without executing any operations. This can be used to prevent over-allocating memory
     * where creating your output buffers.
     *
     * @param {Uint8Array} data The data to be transformed.
     * @param {Uint8Array} key The AES key.
     * @param {Uint8Array} nonce The nonce for the CTR.
     * @param {EncryptionMode} mode The EncryptionMode Encrypt or Decrypt.
     * @param {Uint8Array|null} headerData The header data to be embedded if you use Encryption.
     * @param {boolean} integrity True if you want to enable integrity.
     * @param {number | null} The chunk size for integrity chunks.
     * @param {Uint8Array | null} hashKey The hash key to be used for integrity checks.
     * @return {Promise<number>} The size of the output data.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     * @throws IOException Thrown if there is an IO error.
     */
    static async getActualSize(data, key, nonce, mode, headerData, integrity = false, chunkSize, hashKey = null) {
        let inputStream = new MemoryStream(data);
        let s = new _a(key, nonce, mode, inputStream, headerData, integrity, chunkSize, hashKey);
        let size = await s.actualLength();
        await s.close();
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
     * @param {Uint8Array} nonce          The nonce used for the initial counter
     * @param {EncryptionMode} encryptionMode Encryption mode Encrypt or Decrypt this cannot change later
     * @param {RandomAccessStream} baseStream     The base Stream that will be used to read the data
     * @param {Uint8Array | null} headerData     The data to store in the header when encrypting.
     * @param {boolean} integrity      enable integrity
     * @param {number | null} chunkSize      the chunk size to be used with integrity
     * @param {Uint8Array | null} hashKey        Hash key to be used with integrity
     * @throws IOException Thrown if there is an IO error.
     * @throws SalmonSecurityException Thrown when error with security
     * @throws IntegrityException Thrown if the data are corrupt or tampered with.
     */
    constructor(key, nonce, encryptionMode, baseStream, headerData = null, integrity = false, chunkSize = null, hashKey = null) {
        super();
        _SalmonStream_instances.add(this);
        /**
         * Header data embedded in the stream if available.
         */
        _SalmonStream_headerData.set(this, null);
        /**
         * Mode to be used for this stream. This can only be set once.
         */
        _SalmonStream_encryptionMode.set(this, void 0);
        /**
         * Allow seek and write.
         */
        _SalmonStream_allowRangeWrite.set(this, false);
        /**
         * Fail silently if integrity cannot be verified.
         */
        _SalmonStream_failSilently.set(this, false);
        /**
         * The base stream. When EncryptionMode is Encrypt this will be the target stream.
         * When EncryptionMode is Decrypt this will be the source stream.
         */
        _SalmonStream_baseStream.set(this, void 0);
        /**
         * The transformer to use for encryption.
         */
        _SalmonStream_transformer.set(this, SalmonTransformerFactory.create(__classPrivateFieldGet(_a, _a, "f", _SalmonStream_providerType)));
        /**
         * The integrity to use for hash signature creation and validation.
         */
        _SalmonStream_salmonIntegrity.set(this, new SalmonIntegrity(false, null, null, new HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH));
        /**
         * Internal buffer size by default should be aligned to the integrity for better performance
         */
        _SalmonStream_bufferSize.set(this, SalmonIntegrity.DEFAULT_CHUNK_SIZE);
        _SalmonStream_key.set(this, void 0);
        _SalmonStream_nonce.set(this, void 0);
        __classPrivateFieldSet(this, _SalmonStream_encryptionMode, encryptionMode, "f");
        __classPrivateFieldSet(this, _SalmonStream_baseStream, baseStream, "f");
        __classPrivateFieldSet(this, _SalmonStream_headerData, headerData, "f");
        __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_initIntegrity).call(this, integrity, hashKey, chunkSize);
        __classPrivateFieldSet(this, _SalmonStream_key, key, "f");
        __classPrivateFieldSet(this, _SalmonStream_nonce, nonce, "f");
    }
    /**
     * Set the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @param {ProviderType} providerType The provider Type.
     */
    static setAesProviderType(providerType) {
        __classPrivateFieldSet(_a, _a, providerType, "f", _SalmonStream_providerType);
    }
    /**
     * Get the global AES provider type. Supported types: {@link ProviderType}.
     *
     * @return {ProviderType} The provider Type.
     */
    static getAesProviderType() {
        return __classPrivateFieldGet(_a, _a, "f", _SalmonStream_providerType);
    }
    /**
     * Provides the length of the actual transformed data (minus the header and integrity data).
     *
     * @return {Promise<number>} The length of the stream.
     */
    async length() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        let totalHashBytes;
        let hashOffset = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getHashDataLength(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").length() - 1, hashOffset);
        return await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").length() - __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getHeaderLength).call(this) - totalHashBytes;
    }
    /**
     * Provides the total length of the base stream including header and integrity data if available.
     *
     * @return {Promise<number>} The actual length of the base stream.
     */
    async actualLength() {
        let totalHashBytes = 0;
        totalHashBytes += __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getHashDataLength(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").length() - 1, 0);
        if (await this.canRead())
            return this.length();
        else if (await this.canWrite())
            return await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").length() + __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getHeaderLength).call(this) + totalHashBytes;
        return 0;
    }
    /**
     * Provides the position of the stream relative to the data to be transformed.
     *
     * @return {Promise<number>} The current position of the stream.
     * @throws IOException Thrown if there is an IO error.
     */
    async getPosition() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        let totalHashBytes;
        let hashOffset = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0 ? SalmonGenerator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getHashDataLength(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition(), hashOffset);
        return await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition() - __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getHeaderLength).call(this) - totalHashBytes;
    }
    /**
     * Sets the current position of the stream relative to the data to be transformed.
     *
     * @param {number} value The new position
     * @return {Promise<void>}
     * @throws IOException Thrown if there is an IO error.
     */
    async setPosition(value) {
        if (await this.canWrite() && !__classPrivateFieldGet(this, _SalmonStream_allowRangeWrite, "f") && value != 0) {
            throw new IOException(null, new SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                "If you still want to take the risk you need to use SetAllowRangeWrite(true)"));
        }
        try {
            await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_setVirtualPosition).call(this, value);
        }
        catch (e) {
            throw new IOException(null, e);
        }
    }
    /**
     * If the stream is readable (only if EncryptionMode == Decrypted)
     *
     * @return {Promise<boolean>} True if mode is decryption.
     */
    async canRead() {
        return await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").canRead() && __classPrivateFieldGet(this, _SalmonStream_encryptionMode, "f") == EncryptionMode.Decrypt;
    }
    /**
     * If the stream is seekable (supported only if base stream is seekable).
     *
     * @return {Promise<boolean>} True if stream is seekable.
     */
    async canSeek() {
        return __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").canSeek();
    }
    /**
     * If the stream is writeable (only if EncryptionMode is Encrypt)
     *
     * @return {Promise<boolean>} True if mode is decryption.
     */
    async canWrite() {
        return await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").canWrite() && __classPrivateFieldGet(this, _SalmonStream_encryptionMode, "f") == EncryptionMode.Encrypt;
    }
    /**
     * If the stream has integrity enabled
     * @returns {boolean}
     */
    hasIntegrity() {
        return this.getChunkSize() > 0;
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
    async seek(offset, origin) {
        if (origin == SeekOrigin.Begin)
            await this.setPosition(offset);
        else if (origin == SeekOrigin.Current)
            await this.setPosition(await this.getPosition() + offset);
        else if (origin == SeekOrigin.End)
            await this.setPosition(await this.length() - offset);
        return await this.getPosition();
    }
    /**
     * Set the length of the base stream. Currently unsupported.
     *
     * @param {number} value The new length.
     */
    async setLength(value) {
        let pos = await this.getPosition();
        await this.setPosition(value);
        await this.setPosition(pos);
    }
    /**
     * Flushes any buffered data to the base stream.
     */
    async flush() {
        if (__classPrivateFieldGet(this, _SalmonStream_baseStream, "f") != null) {
            await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").flush();
        }
    }
    /**
     * Closes the stream and all resources associated with it (including the base stream).
     *
     * @throws IOException Thrown if there is an IO error.
     */
    async close() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_closeStreams).call(this);
    }
    /**
     * Returns the current Counter value.
     *
     * @return {Promise<Uint8Array>} The current Counter value.
     */
    async getCounter() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        let ctr = __classPrivateFieldGet(this, _SalmonStream_transformer, "f").getCounter();
        if (ctr == null)
            throw new SalmonSecurityException("No counter, init transformer first");
        return ctr.slice(0);
    }
    /**
     * Returns the current Block value
     * @returns {Promise<number>} The current block value.
     */
    async getBlock() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        return __classPrivateFieldGet(this, _SalmonStream_transformer, "f").getBlock();
    }
    /**
     * Returns a copy of the encryption key.
     * @returns {Promise<Uint8Array>} A copy of the key.
     */
    async getKey() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        let key = __classPrivateFieldGet(this, _SalmonStream_transformer, "f").getKey();
        if (key == null)
            throw new SalmonSecurityException("No key, init transformer first");
        return key.slice(0);
    }
    /**
     * Returns a copy of the hash key.
     * @returns {Uint8Array} A copy of the hash key
     */
    getHashKey() {
        return __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getKey().slice(0);
    }
    /**
     * Returns a copy of the initial vector.
     * @returns {Promise<Uint8Array>} A copy of the initial vector
     */
    async getNonce() {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        let nonce = __classPrivateFieldGet(this, _SalmonStream_transformer, "f").getNonce();
        if (nonce == null)
            throw new SalmonSecurityException("No nonce, init transformer first");
        return nonce.slice(0);
    }
    /**
     * Returns the chunk size used to apply hash signature
     * @returns {number} The chunk size
     */
    getChunkSize() {
        return __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize();
    }
    /**
     * Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
     * This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
     * You should only use this setting for initial encryption with parallel streams and not for overwriting!
     *
     * @param {boolean} value True to allow byte range encryption write operations
     */
    setAllowRangeWrite(value) {
        __classPrivateFieldSet(this, _SalmonStream_allowRangeWrite, value, "f");
    }
    /**
     * Set to True if you want the stream to fail silently when integrity cannot be verified.
     * In that case read() operations will return -1 instead of raising an exception.
     * This prevents 3rd party code like media players from crashing.
     *
     * @param {boolean} value True to fail silently.
     */
    setFailSilently(value) {
        __classPrivateFieldSet(this, _SalmonStream_failSilently, value, "f");
    }
    /**
     * Decrypts the data from the baseStream and stores them in the buffer provided.
     *
     * @param {Uint8Array} buffer The buffer that the data will be stored after decryption
     * @param {number} offset The start position on the buffer that data will be written.
     * @param {number} count  The requested count of the data bytes that should be decrypted
     * @return {Promise<number>} The number of data bytes that were decrypted.
     */
    async read(buffer, offset, count) {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        if (await this.getPosition() == await this.length())
            return -1;
        let alignedOffset = await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getAlignedOffset).call(this);
        let bytes = 0;
        let pos = await this.getPosition();
        // if the base stream is not aligned for read
        if (alignedOffset != 0) {
            // read partially once
            await this.setPosition(await this.getPosition() - alignedOffset);
            let nCount = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0 ? __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() : SalmonGenerator.BLOCK_SIZE;
            let buff = new Uint8Array(nCount);
            bytes = await this.read(buff, 0, nCount);
            bytes = Math.min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return -1;
            for (let i = 0; i < bytes; i++)
                buffer[offset + i] = buff[alignedOffset + i];
            await this.setPosition(pos + bytes);
        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;
        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = await this.getPosition();
        let nBytes = await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_readFromStream).call(this, buffer, bytes + offset, count - bytes);
        await this.setPosition(pos + nBytes);
        return bytes + nBytes;
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
    async write(buffer, offset, count) {
        await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
        if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0 && await this.getPosition() % __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() != 0)
            throw new IOException(null, new IntegrityException("All write operations should be aligned to the chunks size: "
                + __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize()));
        else if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() == 0 && await this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
            throw new IOException(null, new IntegrityException("All write operations should be aligned to the block size: "
                + SalmonAES256CTRTransformer.BLOCK_SIZE));
        // if there are not enough data in the buffer
        count = Math.min(count, buffer.length - offset);
        // if there
        let bufferSize = __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getNormalizedBufferSize).call(this, false);
        let pos = 0;
        while (pos < count) {
            let nBufferSize = Math.min(bufferSize, count - pos);
            let srcBuffer = __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_readBufferData).call(this, buffer, pos, nBufferSize);
            if (srcBuffer.length == 0)
                break;
            let destBuffer = new Uint8Array(srcBuffer.length);
            try {
                await __classPrivateFieldGet(this, _SalmonStream_transformer, "f").encryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
                let integrityHashes = await __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").generateHashes(destBuffer, await this.getPosition() == 0 ? __classPrivateFieldGet(this, _SalmonStream_headerData, "f") : null);
                pos += await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_writeToStream).call(this, destBuffer, this.getChunkSize(), integrityHashes);
                __classPrivateFieldGet(this, _SalmonStream_transformer, "f").syncCounter(await this.getPosition());
            }
            catch (ex) {
                throw new IOException("Could not write to stream: ", ex);
            }
        }
    }
    /**
     * True if the stream has integrity enabled.
     *
     * @return {boolean} If integrity is enabled for this stream.
     */
    isIntegrityEnabled() {
        return __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").useIntegrity();
    }
    /**
     * Get the encryption mode.
     *
     * @return {EncryptionMode} The encryption mode.
     */
    getEncryptionMode() {
        return __classPrivateFieldGet(this, _SalmonStream_encryptionMode, "f");
    }
    /**
     * Get the allowed range write option. This can check if you can use random access write.
     * This is generally not a good option since it prevents reusing the same nonce/counter.
     *
     * @return {boolean} True if the stream allowed to seek and write.
     */
    isAllowRangeWrite() {
        return __classPrivateFieldGet(this, _SalmonStream_allowRangeWrite, "f");
    }
    /**
     * Get the current transformer for this stream.
     * @returns {ISalmonCTRTransformer}
     */
    getTransformer() {
        return __classPrivateFieldGet(this, _SalmonStream_transformer, "f");
    }
    /**
     * Get the internal buffer size.
     * @return {number} The buffer size.
     */
    getBufferSize() {
        return __classPrivateFieldGet(this, _SalmonStream_bufferSize, "f");
    }
    /**
     * Set the internal buffer size.
     *
     * @param {number} bufferSize The new buffer size.
     */
    setBufferSize(bufferSize) {
        __classPrivateFieldSet(this, _SalmonStream_bufferSize, bufferSize, "f");
    }
}
_a = SalmonStream, _SalmonStream_headerData = new WeakMap(), _SalmonStream_encryptionMode = new WeakMap(), _SalmonStream_allowRangeWrite = new WeakMap(), _SalmonStream_failSilently = new WeakMap(), _SalmonStream_baseStream = new WeakMap(), _SalmonStream_transformer = new WeakMap(), _SalmonStream_salmonIntegrity = new WeakMap(), _SalmonStream_bufferSize = new WeakMap(), _SalmonStream_key = new WeakMap(), _SalmonStream_nonce = new WeakMap(), _SalmonStream_instances = new WeakSet(), _SalmonStream_init = 
/**
 * Initialize the salmon stream.
 * @param {Uint8Array} key The encryption key
 * @param {Uint8Array} nonce The nonce
 */
async function _SalmonStream_init(key, nonce) {
    // init only once
    if (__classPrivateFieldGet(this, _SalmonStream_transformer, "f").getKey() != null)
        return;
    await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_initTransformer).call(this, key, nonce);
    await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_initStream).call(this);
}, _SalmonStream_initTransformer = 
/**
 * To create the AES CTR mode we use ECB for AES with No Padding.
 * Initailize the Counter to the initial vector provided.
 * For each data block we increase the Counter and apply the EAS encryption on the Counter.
 * The encrypted Counter then will be xor-ed with the actual data block.
 * Note: for typescript since its async and we cannot run it in the constructor we delay
 * until we run an opearation using the transformer.
 */
async function _SalmonStream_initTransformer(key, nonce) {
    if (key == null)
        throw new SalmonSecurityException("Key is missing");
    if (nonce == null)
        throw new SalmonSecurityException("Nonce is missing");
    __classPrivateFieldSet(this, _SalmonStream_transformer, SalmonTransformerFactory.create(__classPrivateFieldGet(_a, _a, "f", _SalmonStream_providerType)), "f");
    await __classPrivateFieldGet(this, _SalmonStream_transformer, "f").init(key, nonce);
    __classPrivateFieldGet(this, _SalmonStream_transformer, "f").resetCounter();
}, _SalmonStream_initStream = 
/**
 * Init the stream.
 *
 * @throws IOException Thrown if there is an IO error.
 */
async function _SalmonStream_initStream() {
    await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").setPosition(__classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getHeaderLength).call(this));
}, _SalmonStream_initIntegrity = function _SalmonStream_initIntegrity(integrity, hashKey, chunkSize) {
    __classPrivateFieldSet(this, _SalmonStream_salmonIntegrity, new SalmonIntegrity(integrity, hashKey, chunkSize, new HmacSHA256Provider(), SalmonGenerator.HASH_RESULT_LENGTH), "f");
}, _SalmonStream_getHeaderLength = function _SalmonStream_getHeaderLength() {
    if (__classPrivateFieldGet(this, _SalmonStream_headerData, "f") == null)
        return 0;
    else
        return __classPrivateFieldGet(this, _SalmonStream_headerData, "f").length;
}, _SalmonStream_setVirtualPosition = 
/**
 * Set the virtual position of the stream.
 *
 * @param {number} value The new position
 * @throws IOException Thrown if there is an IO error.
 * @throws SalmonRangeExceededException Thrown if nonce has exceeded range
 */
async function _SalmonStream_setVirtualPosition(value) {
    await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_init).call(this, __classPrivateFieldGet(this, _SalmonStream_key, "f"), __classPrivateFieldGet(this, _SalmonStream_nonce, "f"));
    // we skip the header bytes and any hash values we have if the file has integrity set
    await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").setPosition(value);
    let totalHashBytes = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getHashDataLength(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition(), 0);
    await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").setPosition(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition() + totalHashBytes);
    await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").setPosition(await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition() + __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getHeaderLength).call(this));
    __classPrivateFieldGet(this, _SalmonStream_transformer, "f").resetCounter();
    __classPrivateFieldGet(this, _SalmonStream_transformer, "f").syncCounter(await this.getPosition());
}, _SalmonStream_closeStreams = 
/**
 * Close base stream
 */
async function _SalmonStream_closeStreams() {
    if (__classPrivateFieldGet(this, _SalmonStream_baseStream, "f") != null) {
        if (await this.canWrite())
            await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").flush();
        await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").close();
    }
}, _SalmonStream_readFromStream = 
/**
 * Decrypts the data from the baseStream and stores them in the buffer provided.
 * Use this only after you align the base stream to the chunk if integrity is enabled
 * or to the encryption block size.
 *
 * @param {Uint8Array} buffer The buffer that the data will be stored after decryption
 * @param {number} offset The start position on the buffer that data will be written.
 * @param {number} count  The requested count of the data bytes that should be decrypted
 * @return {Promise<number>} The number of data bytes that were decrypted.
 * @throws IOException Thrown if stream is not aligned.
 */
async function _SalmonStream_readFromStream(buffer, offset, count) {
    if (await this.getPosition() == await this.length())
        return 0;
    if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0 && await this.getPosition() % __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() != 0)
        throw new IOException("All reads should be aligned to the chunks size: " + __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize());
    else if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() == 0 && await this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE != 0)
        throw new IOException("All reads should be aligned to the block size: " + SalmonAES256CTRTransformer.BLOCK_SIZE);
    let pos = await this.getPosition();
    // if there are not enough data in the stream
    count = Math.min(count, await this.length() - await this.getPosition());
    // if there are not enough space in the buffer
    count = Math.min(count, buffer.length - offset);
    if (count <= 0)
        return 0;
    // make sure our buffer size is also aligned to the block or chunk
    let bufferSize = __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_getNormalizedBufferSize).call(this, true);
    let bytes = 0;
    while (bytes < count) {
        // read data and integrity signatures
        let srcBuffer = await __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_readStreamData).call(this, bufferSize);
        try {
            let integrityHashes = null;
            // if there are integrity hashes strip them and get the data chunks only
            if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0) {
                // get the integrity signatures
                integrityHashes = __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getHashes(srcBuffer);
                srcBuffer = __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_stripSignatures).call(this, srcBuffer, __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize());
            }
            let destBuffer = new Uint8Array(srcBuffer.length);
            if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").useIntegrity() && integrityHashes != null) {
                await __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").verifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? __classPrivateFieldGet(this, _SalmonStream_headerData, "f") : null);
            }
            await __classPrivateFieldGet(this, _SalmonStream_transformer, "f").decryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.length);
            let len = Math.min(count - bytes, destBuffer.length);
            __classPrivateFieldGet(this, _SalmonStream_instances, "m", _SalmonStream_writeToBuffer).call(this, destBuffer, 0, buffer, bytes + offset, len);
            bytes += len;
            __classPrivateFieldGet(this, _SalmonStream_transformer, "f").syncCounter(await this.getPosition());
        }
        catch (ex) {
            if (ex instanceof IntegrityException && __classPrivateFieldGet(this, _SalmonStream_failSilently, "f"))
                return -1;
            throw new IOException("Could not read from stream: ", ex);
        }
    }
    return bytes;
}, _SalmonStream_getAlignedOffset = 
/**
 * Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
 * wrt to the encryption block size. Use this method to align a position to the
 * start of the block or chunk.
 *
 * @return
 */
async function _SalmonStream_getAlignedOffset() {
    let alignOffset;
    if (__classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize() > 0) {
        alignOffset = (await this.getPosition() % __classPrivateFieldGet(this, _SalmonStream_salmonIntegrity, "f").getChunkSize());
    }
    else {
        alignOffset = (await this.getPosition() % SalmonAES256CTRTransformer.BLOCK_SIZE);
    }
    return alignOffset;
}, _SalmonStream_getNormalizedBufferSize = function _SalmonStream_getNormalizedBufferSize(includeHashes) {
    let bufferSize = __classPrivateFieldGet(this, _SalmonStream_bufferSize, "f");
    if (this.getChunkSize() > 0) {
        // buffer size should be a multiple of the chunk size if integrity is enabled
        let partSize = this.getChunkSize();
        // if add the hash signatures
        if (partSize < bufferSize) {
            bufferSize = Math.floor(bufferSize / this.getChunkSize()) * this.getChunkSize();
        }
        else
            bufferSize = partSize;
        if (includeHashes)
            bufferSize += Math.floor(bufferSize / this.getChunkSize()) * SalmonGenerator.HASH_RESULT_LENGTH;
    }
    else {
        // buffer size should also be a multiple of the AES block size
        bufferSize = Math.floor(bufferSize / SalmonAES256CTRTransformer.BLOCK_SIZE)
            * SalmonAES256CTRTransformer.BLOCK_SIZE;
    }
    return bufferSize;
}, _SalmonStream_readBufferData = function _SalmonStream_readBufferData(buffer, offset, count) {
    let data = new Uint8Array(Math.min(count, buffer.length - offset));
    for (let i = 0; i < data.length; i++)
        data[i] = buffer[offset + i];
    return data;
}, _SalmonStream_readStreamData = 
/**
 * Read the data from the base stream into the buffer.
 *
 * @param count The number of bytes to read.
 * @return The number of bytes read.
 * @throws IOException Thrown if there is an IO error.
 */
async function _SalmonStream_readStreamData(count) {
    let data = new Uint8Array(Math.min(count, await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").length() - await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").getPosition()));
    await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").read(data, 0, data.length);
    return data;
}, _SalmonStream_writeToBuffer = function _SalmonStream_writeToBuffer(srcBuffer, srcOffset, destBuffer, destOffset, count) {
    for (let i = 0; i < count; i++)
        destBuffer[destOffset + i] = srcBuffer[srcOffset + i];
}, _SalmonStream_writeToStream = 
/**
 * Write data to the base stream.
 *
 * @param buffer    The buffer to read from.
 * @param chunkSize The chunk segment size to use when writing the buffer.
 * @param hashes    The hash signature to write at the beginning of each chunk.
 * @return The number of bytes written.
 * @throws IOException Thrown if there is an IO error.
 */
async function _SalmonStream_writeToStream(buffer, chunkSize, hashes) {
    let pos = 0;
    let chunk = 0;
    if (chunkSize <= 0)
        chunkSize = buffer.length;
    while (pos < buffer.length) {
        if (hashes != null) {
            await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").write(hashes[chunk], 0, hashes[chunk].length);
        }
        let len = Math.min(chunkSize, buffer.length - pos);
        await __classPrivateFieldGet(this, _SalmonStream_baseStream, "f").write(buffer, pos, len);
        pos += len;
        chunk++;
    }
    return pos;
}, _SalmonStream_stripSignatures = function _SalmonStream_stripSignatures(buffer, chunkSize) {
    let bytes = Math.floor(buffer.length / (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH)) * chunkSize;
    if (buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) != 0)
        bytes += buffer.length % (chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) - SalmonGenerator.HASH_RESULT_LENGTH;
    let buff = new Uint8Array(bytes);
    let index = 0;
    for (let i = 0; i < buffer.length; i += chunkSize + SalmonGenerator.HASH_RESULT_LENGTH) {
        let nChunkSize = Math.min(chunkSize, buff.length - index);
        for (let j = 0; j < nChunkSize; j++)
            buff[index + j] = buffer[i + SalmonGenerator.HASH_RESULT_LENGTH + j];
        index += nChunkSize;
    }
    return buff;
};
/**
 * Current global AES provider type.
 */
_SalmonStream_providerType = { value: ProviderType.Default };
