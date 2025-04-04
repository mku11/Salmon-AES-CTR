package com.mku.streams;
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

import java.io.IOException;
import java.io.InputStream;

/***
 * InputStream wrapper for RandomAccessStream.
 * Use this class to wrap any RandomAccessStream to a Java InputStream to use with 3rd party libraries.
 */
public class InputStreamWrapper extends InputStream {
    private final RandomAccessStream stream;

    /**
     * Instantiates an InputStreamWrapper from a RandomAccessStream.
     *
     * @param stream The stream that you want to wrap.
     */
    public InputStreamWrapper(RandomAccessStream stream) {
        this.stream = stream;
    }

    /**
     * Read a byte from the stream. Blocking is dependent on the base stream.
     *
     * @return -1 if there are no more bytes from the stream otherwise the next byte value (0-255).
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int bytesRead = read(buffer, 0, 1);
        if (bytesRead <= 0)
            return -1;
        return buffer[0];
    }

    /**
     * Read a sequance of bytes from the base stream into the buffer provided.
     *
     * @param buffer the buffer into which the data is read.
     * @param offset the start offset in array <code>b</code>
     *               at which the data is written.
     * @param count  the maximum number of bytes to read.
     * @return The number of bytes read.
     * @throws IOException with an optional inner Exception if the base stream is a AesStream
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int bytesRead;
        try {
            bytesRead = stream.read(buffer, offset, count);
        } catch (Exception exception) {
            throw new IOException(exception);
        }
        return bytesRead;
    }

    /**
     * Closes the base stream.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void close() throws IOException {
        stream.close();
    }

    /**
     * Reset the stream.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void reset() throws IOException {
        stream.setPosition(0);
    }

    /**
     * Skip number of bytes on the stream.
     *
     * @param pos the number of bytes to be skipped.
     * @return The number of bytes that were skipped.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long skip(long pos) throws IOException {
        if (pos > stream.getLength())
            stream.setPosition(stream.getLength());
        else
            stream.setPosition(stream.getPosition() + pos);
        return stream.getPosition();
    }
}
