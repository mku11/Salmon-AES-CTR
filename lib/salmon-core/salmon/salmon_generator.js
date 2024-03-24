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
var _a, _SalmonGenerator_MAGIC_BYTES;
/**
 * Utility class generates internal secure properties.
 */
export class SalmonGenerator {
    /**
     * Gets the fixed magic bytes array
     */
    static getMagicBytes() {
        return new TextEncoder().encode(__classPrivateFieldGet(_a, _a, "f", _SalmonGenerator_MAGIC_BYTES));
    }
    /**
     * Returns the current Salmon format version.
     */
    static getVersion() {
        return _a.VERSION;
    }
    /**
     * Returns a secure random byte array. To be used when generating keys, initial vectors, and nonces.
     * @param size The size of the byte array.
     * @return The random secure byte array.
     */
    static getSecureRandomBytes(size) {
        let bytes = new Uint8Array(size);
        crypto.getRandomValues(bytes);
        return bytes;
    }
}
_a = SalmonGenerator;
/**
 * Version.
 */
SalmonGenerator.VERSION = 2;
/**
 * Lenght for the magic bytes.
 */
SalmonGenerator.MAGIC_LENGTH = 3;
/**
 * Length for the Version in the data header.
 */
SalmonGenerator.VERSION_LENGTH = 1;
/**
 * Should be 16 for AES256 the same as the iv.
 */
SalmonGenerator.BLOCK_SIZE = 16;
/**
 * Encryption key length for AES256.
 */
SalmonGenerator.KEY_LENGTH = 32;
/**
 * HASH Key length for integrity, currently we use HMAC SHA256.
 */
SalmonGenerator.HASH_KEY_LENGTH = 32;
/**
 * Hash signature size for integrity, currently we use HMAC SHA256.
 */
SalmonGenerator.HASH_RESULT_LENGTH = 32;
/**
 * Nonce size.
 */
SalmonGenerator.NONCE_LENGTH = 8;
/**
 * Chunk size format length.
 */
SalmonGenerator.CHUNK_SIZE_LENGTH = 4;
/**
 * Magic bytes.
 */
_SalmonGenerator_MAGIC_BYTES = { value: "SLM" };
