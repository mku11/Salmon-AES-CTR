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
 * Utility class generates internal secure properties.
 */
export class SalmonGenerator {
    /**
     * Version.
     */
    public static readonly VERSION: number = 2;

    /**
     * Lenght for the magic bytes.
     */
    public static readonly MAGIC_LENGTH: number = 3;

    /**
     * Length for the Version in the data header.
     */
    public static readonly VERSION_LENGTH: number = 1;

    /**
     * Should be 16 for AES256 the same as the iv.
     */
    public static readonly BLOCK_SIZE: number = 16;

    /**
     * Encryption key length for AES256.
     */
    public static readonly KEY_LENGTH: number = 32;

    /**
     * HASH Key length for integrity, currently we use HMAC SHA256.
     */
    public static readonly HASH_KEY_LENGTH: number = 32;

    /**
     * Hash signature size for integrity, currently we use HMAC SHA256.
     */
    public static readonly HASH_RESULT_LENGTH: number = 32;

    /**
     * Nonce size.
     */
    public static readonly NONCE_LENGTH: number = 8;

    /**
     * Chunk size format length.
     */
    public static readonly CHUNK_SIZE_LENGTH: number = 4;

    /**
     * Magic bytes.
     */
    static readonly #MAGIC_BYTES: string = "SLM";

    /**
     * Gets the fixed magic bytes array
     */
    public static getMagicBytes(): Uint8Array {
        return new TextEncoder().encode(SalmonGenerator.#MAGIC_BYTES);
    }

    /**
     * Returns the current Salmon format version.
     */
    public static getVersion(): number {
        return SalmonGenerator.VERSION;
    }

    /**
     * Returns a secure random byte array. To be used when generating keys, initial vectors, and nonces.
     * @param size The size of the byte array.
     * @return The random secure byte array.
     */
    public static getSecureRandomBytes(size: number): Uint8Array {
        let bytes: Uint8Array = new Uint8Array(size);
        crypto.getRandomValues(bytes);
        return bytes;
    }
}