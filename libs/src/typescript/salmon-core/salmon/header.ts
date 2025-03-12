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

import { BitConverter } from "../convert/bit_converter.js";
import { RandomAccessStream } from "../streams/random_access_stream.js";
import { MemoryStream } from "../streams/memory_stream.js";
import { Generator } from "./generator.js";

/**
 * Header embedded in the SalmonStream. Header contains nonce and other information for
 * decrypting the stream.
 */
export class Header {
    public static readonly HEADER_LENGTH: number = 16;

    /**
     * Magic bytes.
     */
    #magicBytes: Uint8Array = new Uint8Array(Generator.MAGIC_LENGTH);

    /**
     * Format version from {@link Generator#VERSION}.
     */
    #version: number = 0;

    /**
     * Chunk size used for data integrity.
     */
    #chunkSize: number = 0;

    /**
     * Starting nonce for the CTR mode. This is the upper part of the Counter.
     *
     */
    #nonce: Uint8Array | null = null;

    /**
     * Binary data.
     */
    #headerData: Uint8Array;

    /**
     * Get the nonce.
     * @return {Uint8Array | null} The nonce saved in the header.
     */
    public getNonce(): Uint8Array | null {
        return this.#nonce;
    }

    /**
     * Get the chunk size.
     * @return {number} Chunk size
     */
    public getChunkSize(): number {
        return this.#chunkSize;
    }

    /**
     * Get the raw header data.
     * @return {Uint8Array | null} Header data
     */
    public getHeaderData(): Uint8Array {
        return this.#headerData;
    }

    /**
     * Get the Salmon format  version
     * @return {number} The format version
     */
    public getVersion(): number {
        return this.#version;
    }

    /**
     * Get the magic bytes
     * @return {Uint8Array} Magic bytes
     */
    public getMagicBytes(): Uint8Array {
        return this.#magicBytes;
    }

    public constructor(headerData: Uint8Array) {
        this.#headerData = headerData;
    }

    /**
     * Parse the header data from the stream
     * @param {RandomAccessStream} stream The stream.
     * @return {Header} The header data.
     * @throws IOException Thrown if there is an IO error.
     */
    public static async readHeaderData(stream: RandomAccessStream): Promise<Header|null> {
        if(await stream.getLength() == 0)
            return null;
        let pos: number = await stream.getPosition();

        await stream.setPosition(0);
        
        let headerData = new Uint8Array(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH);
        await stream.read(headerData, 0, headerData.length);
        let header:Header = new Header(headerData);
        let ms: MemoryStream = new MemoryStream(header.#headerData);
        header.#magicBytes = new Uint8Array(Generator.MAGIC_LENGTH);
        await ms.read(header.#magicBytes, 0, header.#magicBytes.length);
        let versionBytes: Uint8Array = new Uint8Array(Generator.VERSION_LENGTH);
        await ms.read(versionBytes, 0, Generator.VERSION_LENGTH);
        header.#version = versionBytes[0];
        let chunkSizeHeader: Uint8Array = new Uint8Array(Generator.CHUNK_SIZE_LENGTH);
        await ms.read(chunkSizeHeader, 0, chunkSizeHeader.length);
        header.#chunkSize = BitConverter.toLong(chunkSizeHeader, 0, Generator.CHUNK_SIZE_LENGTH);
        header.#nonce = new Uint8Array(Generator.NONCE_LENGTH);
        await ms.read(header.#nonce, 0, header.#nonce.length);

        await stream.setPosition(pos);
        return header;
    }
    
    public static async writeHeader(stream: RandomAccessStream, nonce: Uint8Array, chunkSize: number): Promise<Header|null> {
        let magicBytes: Uint8Array = Generator.getMagicBytes();
        let version: number = Generator.getVersion();
        let versionBytes: Uint8Array = new Uint8Array([version]);
        let chunkSizeBytes: Uint8Array = BitConverter.toBytes(chunkSize, Generator.CHUNK_SIZE_LENGTH);

        let headerData: Uint8Array =  new Uint8Array(Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH);
        let ms: MemoryStream = new MemoryStream(headerData);
        await ms.write(magicBytes, 0, magicBytes.length);
        await ms.write(versionBytes, 0, versionBytes.length);
        await ms.write(chunkSizeBytes, 0, chunkSizeBytes.length);
        await ms.write(nonce, 0, nonce.length);
        await ms.setPosition(0);
        let header: Header | null = await Header.readHeaderData(ms);

        let pos: number = await stream.getPosition();
        await stream.setPosition(0);
        await ms.setPosition(0);
        await ms.copyTo(stream);
        await ms.close();
        await stream.flush();
        await stream.setPosition(pos);

        return header;
    }

    /**
     * Set the header chunk size
     * @param {number} chunkSize The chunk size
     */
    public setChunkSize(chunkSize: number): void {
        this.#chunkSize = chunkSize;
    }

    /**
     * Set the Salmon format version
     * @param {number} version The format version
     */
    public setVersion(version: number): void {
        this.#version = version;
    }

    /**
     * Set the nonce to be used.
     * @param {Uint8Array} nonce The nonce
     */
    public setNonce(nonce: Uint8Array): void {
        this.#nonce = nonce;
    }
}