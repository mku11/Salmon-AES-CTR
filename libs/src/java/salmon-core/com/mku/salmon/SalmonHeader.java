package com.mku.salmon;
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

import com.mku.convert.BitConverter;
import com.mku.io.RandomAccessStream;

import java.io.IOException;

/**
 * Header embedded in the SalmonStream. Header contains nonce and other information for
 * decrypting the stream.
 */
public class SalmonHeader {

    /**
     * Magic bytes.
     */
    private byte[] magicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];

    /**
     * Format version from {@link SalmonGenerator#VERSION}.
     */
    private byte version;

    /**
     * Chunk size used for data integrity.
     */
    private int chunkSize;

    /**
     * Starting nonce for the CTR mode. This is the upper part of the Counter.
     *
     */
    private byte[] nonce;

    /**
     * Binary data.
     */
    private byte[] headerData;

    /**
     * Get the nonce.
     * @return The nonce
     */
    public byte[] getNonce() {
        return nonce;
    }

    /**
     * Get the chunk size.
     * @return Chunk size
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Get the raw header data.
     * @return Header data
     */
    public byte[] getHeaderData() {
        return headerData;
    }

    /**
     * Get the Salmon format  version
     * @return The format version
     */
    public byte getVersion() {
        return version;
    }

    /**
     * Get the magic bytes
     * @return Magic bytes
     */
    public byte[] getMagicBytes() {
        return magicBytes;
    }

    /**
     * Parse the header data from the stream
     * @param stream The stream.
     * @return
     * @throws IOException
     */
    public static SalmonHeader parseHeaderData(RandomAccessStream stream) throws IOException {
        SalmonHeader header = new SalmonHeader();
        header.magicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];
        stream.read(header.magicBytes, 0, header.magicBytes.length);
        byte[] versionBytes = new byte[SalmonGenerator.VERSION_LENGTH];
        stream.read(versionBytes, 0, SalmonGenerator.VERSION_LENGTH);
        header.version = versionBytes[0];
        byte[] chunkSizeHeader = new byte[SalmonGenerator.CHUNK_SIZE_LENGTH];
        stream.read(chunkSizeHeader, 0, chunkSizeHeader.length);
        header.chunkSize = (int) BitConverter.toLong(chunkSizeHeader, 0, SalmonGenerator.CHUNK_SIZE_LENGTH);
        header.nonce = new byte[SalmonGenerator.NONCE_LENGTH];
        stream.read(header.nonce, 0, header.nonce.length);
        stream.position(0);
        header.headerData = new byte[SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH
                + SalmonGenerator.CHUNK_SIZE_LENGTH + SalmonGenerator.NONCE_LENGTH];
        stream.read(header.headerData, 0, header.headerData.length);

        return header;
    }

    /**
     * Set the header chunk size
     * @param chunkSize The chunk size
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Set the Salmon format version
     * @param version The format version
     */
    public void setVersion(byte version) {
        this.version = version;
    }

    /**
     * Set the nonce to be used.
     * @param nonce The nonce
     */
    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }
}
