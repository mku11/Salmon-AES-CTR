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
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var _SalmonHeader_magicBytes, _SalmonHeader_version, _SalmonHeader_chunkSize, _SalmonHeader_nonce, _SalmonHeader_headerData;
import { BitConverter } from "../convert/bit_converter.js";
import { SalmonGenerator } from "./salmon_generator.js";
/**
 * Header embedded in the SalmonStream. Header contains nonce and other information for
 * decrypting the stream.
 */
export class SalmonHeader {
    constructor() {
        /**
         * Magic bytes.
         */
        _SalmonHeader_magicBytes.set(this, new Uint8Array(SalmonGenerator.MAGIC_LENGTH));
        /**
         * Format version from {@link SalmonGenerator#VERSION}.
         */
        _SalmonHeader_version.set(this, 0);
        /**
         * Chunk size used for data integrity.
         */
        _SalmonHeader_chunkSize.set(this, 0);
        /**
         * Starting nonce for the CTR mode. This is the upper part of the Counter.
         *
         */
        _SalmonHeader_nonce.set(this, null);
        /**
         * Binary data.
         */
        _SalmonHeader_headerData.set(this, null);
    }
    /**
     * Get the nonce.
     * @return The nonce
     */
    getNonce() {
        return __classPrivateFieldGet(this, _SalmonHeader_nonce, "f");
    }
    /**
     * Get the chunk size.
     * @return Chunk size
     */
    getChunkSize() {
        return __classPrivateFieldGet(this, _SalmonHeader_chunkSize, "f");
    }
    /**
     * Get the raw header data.
     * @return Header data
     */
    getHeaderData() {
        return __classPrivateFieldGet(this, _SalmonHeader_headerData, "f");
    }
    /**
     * Get the Salmon format  version
     * @return The format version
     */
    getVersion() {
        return __classPrivateFieldGet(this, _SalmonHeader_version, "f");
    }
    /**
     * Get the magic bytes
     * @return Magic bytes
     */
    getMagicBytes() {
        return __classPrivateFieldGet(this, _SalmonHeader_magicBytes, "f");
    }
    /**
     * Parse the header data from the stream
     * @param stream The stream.
     * @return
     * @throws IOException
     */
    static async parseHeaderData(stream) {
        let header = new SalmonHeader();
        __classPrivateFieldSet(header, _SalmonHeader_magicBytes, new Uint8Array(SalmonGenerator.MAGIC_LENGTH), "f");
        await stream.read(__classPrivateFieldGet(header, _SalmonHeader_magicBytes, "f"), 0, __classPrivateFieldGet(header, _SalmonHeader_magicBytes, "f").length);
        let versionBytes = new Uint8Array(SalmonGenerator.VERSION_LENGTH);
        await stream.read(versionBytes, 0, SalmonGenerator.VERSION_LENGTH);
        __classPrivateFieldSet(header, _SalmonHeader_version, versionBytes[0], "f");
        let chunkSizeHeader = new Uint8Array(SalmonGenerator.CHUNK_SIZE_LENGTH);
        await stream.read(chunkSizeHeader, 0, chunkSizeHeader.length);
        __classPrivateFieldSet(header, _SalmonHeader_chunkSize, BitConverter.toLong(chunkSizeHeader, 0, SalmonGenerator.CHUNK_SIZE_LENGTH), "f");
        __classPrivateFieldSet(header, _SalmonHeader_nonce, new Uint8Array(SalmonGenerator.NONCE_LENGTH), "f");
        await stream.read(__classPrivateFieldGet(header, _SalmonHeader_nonce, "f"), 0, __classPrivateFieldGet(header, _SalmonHeader_nonce, "f").length);
        await stream.setPosition(0);
        __classPrivateFieldSet(header, _SalmonHeader_headerData, new Uint8Array(SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH
            + SalmonGenerator.CHUNK_SIZE_LENGTH + SalmonGenerator.NONCE_LENGTH), "f");
        await stream.read(__classPrivateFieldGet(header, _SalmonHeader_headerData, "f"), 0, __classPrivateFieldGet(header, _SalmonHeader_headerData, "f").length);
        return header;
    }
    /**
     * Set the header chunk size
     * @param chunkSize The chunk size
     */
    setChunkSize(chunkSize) {
        __classPrivateFieldSet(this, _SalmonHeader_chunkSize, chunkSize, "f");
    }
    /**
     * Set the Salmon format version
     * @param version The format version
     */
    setVersion(version) {
        __classPrivateFieldSet(this, _SalmonHeader_version, version, "f");
    }
    /**
     * Set the nonce to be used.
     * @param nonce The nonce
     */
    setNonce(nonce) {
        __classPrivateFieldSet(this, _SalmonHeader_nonce, nonce, "f");
    }
}
_SalmonHeader_magicBytes = new WeakMap(), _SalmonHeader_version = new WeakMap(), _SalmonHeader_chunkSize = new WeakMap(), _SalmonHeader_nonce = new WeakMap(), _SalmonHeader_headerData = new WeakMap();
