package com.mku.io;
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

import com.mku.salmon.SalmonDefaultOptions;

import java.io.IOException;

/**
 * Abstract read-write seekable stream used by internal streams
 * (modeled after c# Stream class).
 */
public abstract class RandomAccessStream {

    /**
     * True if the stream is readable.
     * @return
     */
    public abstract boolean canRead();

    /**
     * True if the stream is writeable.
     * @return
     */
    public abstract boolean canWrite();

    /**
     * True if the stream is seekable.
     * @return
     */
    public abstract boolean canSeek();

    /**
     * Get the length of the stream.
     * @return
     */
    public abstract long length();

    /**
     * Get the current position of the stream.
     * @return The current position.
     * @throws IOException
     */
    public abstract long position() throws IOException;

    /**
     * Change the current position of the stream.
     * @param value The new position.
     * @throws IOException
     */
    public abstract void position(long value) throws IOException;

    /**
     * Set the length of this stream.
     * @param value The length.
     * @throws IOException
     */
    public abstract void setLength(long value) throws IOException;

    /**
     *
     * @param buffer
     * @param offset
     * @param count The number of bytes that were read. If the stream reached the end return -1.
     * @return
     * @throws IOException
     */
    public abstract int read(byte[] buffer, int offset, int count) throws IOException;

    /**
     * Write the contents of the buffer to this stream.
     * @param buffer The buffer to read the contents from.
     * @param offset The position the reading will start from.
     * @param count The count of bytes to be read from the buffer.
     * @throws IOException
     */
    public abstract void write(byte[] buffer, int offset, int count) throws IOException;

    /**
     * Seek to a specific position in the stream.
     * @param position The new position.
     * @param origin The origin type.
     * @return The position after the seeking was complete.
     * @throws IOException
     */
    public abstract long seek(long position, SeekOrigin origin) throws IOException;

    /**
     * Flush buffers.
     */
    public abstract void flush();

    /**
     * Close the stream and associated resources.
     * @throws IOException
     */
    public abstract void close() throws IOException;

	/**
     * Progress listener for stream operations.
     * 
	 */
    public interface OnProgressListener {
        void onProgressChanged(long position, long length);
    }

    /**
     * Write stream contents to another stream.
     * @param stream The target stream.
     * @throws IOException
     */
    public void copyTo(RandomAccessStream stream) throws IOException {
        copyTo(stream, 0, null);
    }

    /**
     * Write stream contents to another stream.
     * @param stream The target stream.
     * @param progressListener The listener to notify when progress changes.
     * @throws IOException
     */
    public void copyTo(RandomAccessStream stream, OnProgressListener progressListener)
            throws IOException {
        copyTo(stream, 0, progressListener);
    }

    /**
     * Write stream contents to another stream.
     * @param stream The target stream.
     * @param bufferSize The buffer size to be used when copying.
     * @param progressListener The listener to notify when progress changes.
     * @throws IOException
     */
    public void copyTo(RandomAccessStream stream, int bufferSize, OnProgressListener progressListener)
            throws IOException {
        if (!canRead())
            throw new IOException("Target stream not readable");
        if (!stream.canWrite())
            throw new IOException("Target stream not writable");
        if (bufferSize <= 0)
            bufferSize = SalmonDefaultOptions.getBufferSize();
        int bytesRead;
        long pos = position();
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = read(buffer, 0, bufferSize)) > 0) {
            stream.write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener.onProgressChanged(position(), length());
        }
        stream.flush();
        position(pos);
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
