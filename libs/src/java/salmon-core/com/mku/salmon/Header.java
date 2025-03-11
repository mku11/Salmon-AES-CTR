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
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;

/**
 * Header embedded within the AesStream. Header contains nonce and other information for
 * decrypting the stream.
 */
public class Header {

    /**
     * Header length
     */
    public static final long HEADER_LENGTH = 16;

    /**
     * Magic bytes.
     */
    private byte[] magicBytes = new byte[Generator.MAGIC_LENGTH];

    /**
     * Format version from {@link Generator#VERSION}.
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
     * @return The header data.
     * @throws IOException Thrown if there is an IO error.
     */
    public static Header readHeaderData(RandomAccessStream stream) throws IOException {
        if(stream.length() == 0)
            return null;
        long pos = stream.getPosition();

        stream.setPosition(0);
        Header header = new Header();
        header.headerData = new byte[Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH];
        stream.read(header.headerData, 0, header.headerData.length);

        MemoryStream ms = new MemoryStream(header.headerData);
        header.magicBytes = new byte[Generator.MAGIC_LENGTH];
        ms.read(header.magicBytes, 0, header.magicBytes.length);
        byte[] versionBytes = new byte[Generator.VERSION_LENGTH];
        ms.read(versionBytes, 0, Generator.VERSION_LENGTH);
        header.version = versionBytes[0];
        byte[] chunkSizeHeader = new byte[Generator.CHUNK_SIZE_LENGTH];
        ms.read(chunkSizeHeader, 0, chunkSizeHeader.length);
        header.chunkSize = (int) BitConverter.toLong(chunkSizeHeader, 0, Generator.CHUNK_SIZE_LENGTH);
        header.nonce = new byte[Generator.NONCE_LENGTH];
        ms.read(header.nonce, 0, header.nonce.length);

        stream.setPosition(pos);
        return header;
    }

    public static Header writeHeader(RandomAccessStream stream, byte[] nonce, int chunkSize) throws IOException {
        byte[] magicBytes = Generator.getMagicBytes();
        byte version = Generator.getVersion();
        byte[] versionBytes = new byte[]{version};
        byte[] chunkSizeBytes = BitConverter.toBytes(chunkSize, Generator.CHUNK_SIZE_LENGTH);

        byte[] headerData =  new byte[Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH];
        MemoryStream ms = new MemoryStream(headerData);
        ms.write(magicBytes, 0, magicBytes.length);
        ms.write(versionBytes, 0, versionBytes.length);
        ms.write(chunkSizeBytes, 0, chunkSizeBytes.length);
        ms.write(nonce, 0, nonce.length);
        ms.setPosition(0);
        Header header = readHeaderData(ms);

        long pos = stream.getPosition();
        stream.setPosition(0);
        ms.setPosition(0);
        ms.copyTo(stream);
        ms.close();
        stream.flush();
        stream.setPosition(pos);

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
