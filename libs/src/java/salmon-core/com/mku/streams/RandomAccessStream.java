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

import com.mku.func.BiConsumer;

import java.io.IOException;
import java.io.InputStream;

/**
 * Base class for read-write seekable streams.
 */
public abstract class RandomAccessStream {
    /**
     * Default buffer size
     */
    public static final int DEFAULT_BUFFER_SIZE = 256 * 1024;

    /**
     * Check if the stream is readable.
     *
     * @return True if readable
     */
    public abstract boolean canRead();

    /**
     * Check if the stream is writable.
     *
     * @return True if writable
     */
    public abstract boolean canWrite();

    /**
     * Check if the stream is seekable.
     *
     * @return True if seekable
     */
    public abstract boolean canSeek();

    /**
     * Get the length of the stream.
     *
     * @return The length
     */
    public abstract long getLength();

    /**
     * Get the current position of the stream.
     *
     * @return The current position.
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract long getPosition() throws IOException;

    /**
     * Change the current position of the stream.
     *
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract void setPosition(long value) throws IOException;

    /**
     * The preferred align size
     * @return The align size
     */
    public int getAlignSize() {
        return 32768;
    }

    /**
     * Set the length of this stream.
     *
     * @param value The length.
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract void setLength(long value) throws IOException;

    /**
     * Read the contents from the stream into the buffer.
     *
     * @param buffer The buffer to read into
     * @param offset The offset to start reading into
     * @param count  The number of bytes that were read. If the stream reached the end return -1.
     * @return The bytes read
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract int read(byte[] buffer, int offset, int count) throws IOException;

    /**
     * Write the contents of the buffer to this stream.
     *
     * @param buffer The buffer to read the contents from.
     * @param offset The position the reading will start from.
     * @param count  The count of bytes to be read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract void write(byte[] buffer, int offset, int count) throws IOException;

    /**
     * Seek to a specific position in the stream.
     *
     * @param position The new position.
     * @param origin   The origin type.
     * @return The position after the seeking was complete.
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract long seek(long position, SeekOrigin origin) throws IOException;

    /**
     * Flush buffers.
     */
    public abstract void flush();

    /**
     * Close the stream and associated resources.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    public abstract void close() throws IOException;

    /**
     * Write stream contents to another stream.
     *
     * @param stream The target stream.
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyTo(RandomAccessStream stream) throws IOException {
        copyTo(stream, 0, null);
    }


    /**
     * Write stream contents to another stream.
     *
     * @param stream           The target stream.
     * @param bufferSize       The buffer size to be used when copying.
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyTo(RandomAccessStream stream, int bufferSize)
            throws IOException {
        copyTo(stream, bufferSize, null);
    }

    /**
     * Write stream contents to another stream.
     *
     * @param stream           The target stream.
     * @param progressListener The listener to notify when progress changes.
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyTo(RandomAccessStream stream, BiConsumer<Long, Long> progressListener)
            throws IOException {
        copyTo(stream, 0, progressListener);
    }

    /**
     * Write stream contents to another stream.
     *
     * @param stream           The target stream.
     * @param bufferSize       The buffer size to be used when copying.
     * @param progressListener The listener to notify when progress changes.
     * @throws IOException Thrown if there is an IO error.
     */
    public void copyTo(RandomAccessStream stream, int bufferSize, BiConsumer<Long, Long> progressListener)
            throws IOException {
        if (!canRead())
            throw new IOException("Target stream not readable");
        if (!stream.canWrite())
            throw new IOException("Target stream not writable");
        if (bufferSize <= 0)
            bufferSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
        bufferSize = bufferSize / getAlignSize() * getAlignSize();
        int bytesRead;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = read(buffer, 0, bufferSize)) > 0) {
            stream.write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener.accept(getPosition(), getLength());
        }
        stream.flush();
    }

	/**
     * Wrap to a Java InputStream to use with 3rd party libraries.
	 * @return The InputStream
     */
    public InputStream asReadStream()
    {
        return new InputStreamWrapper(this);
    }

    /**
     * Used to identify the start offset for seeking to a stream.
     */
    public enum SeekOrigin {
        /**
         * Start from the beginning of the stream.
         */
        Begin,
        /**
         * Start from the current position of the stream.
         */
        Current,
        /**
         * Start from the end of the stream.
         */
        End
    }
}
