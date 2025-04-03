package com.mku.fs.streams;
/*
MIT License

Copyright (c) 2025 Max Kas

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

import com.mku.fs.file.HttpFile;
import com.mku.streams.RandomAccessStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * File stream implementation for remote HTTP files.
 */
public class HttpFileStream extends RandomAccessStream {

    /**
     * The network input stream associated.
     */
    private InputStream inputStream;

    private boolean closed;

    /**
     * The java file associated with this stream.
     */
    private final HttpFile file;

    private long maxNetBytesSkip = 32768;
    private HttpURLConnection conn;

    /**
     * Maximum amount of bytes allowed to skip forwards when seeking otherwise will open a new connection
     *
     * @param maxNetBytesSkip The maximum number of bytes to skip
     */
    public void setMaxNetBytesSkip(long maxNetBytesSkip) {
        this.maxNetBytesSkip = maxNetBytesSkip;
    }

    private long position;

    /**
     * Construct a file stream from a JavaFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JavaFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     */
    public HttpFileStream(HttpFile file, String mode) {
        this.file = file;
        if (mode.contains("w")) {
            throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
        }
    }

    private InputStream getInputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.inputStream == null) {
            long startPosition = this.getPosition();
            OutputStream outputStream = null;
            try {
                conn = createConnection("GET", file.getPath());
                setDefaultHeaders(conn);
                if (this.position > 0) {
                    conn.addRequestProperty("Range", "bytes=" + this.position + "-");
                }
                conn.connect();
                checkStatus(conn, startPosition > 0 ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK);
                this.inputStream = new BufferedInputStream(conn.getInputStream());
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeStream(outputStream);
            }
        }
        return this.inputStream;
    }

    /**
     * Check if stream can read from file.
     *
     * @return True if readable
     */
    @Override
    public boolean canRead() {
        return true;
    }

    /**
     * Check if stream can write to file.
     *
     * @return True if writable
     */
    @Override
    public boolean canWrite() {
        return false;
    }

    /**
     * Check if stream can seek.
     *
     * @return True if seekable
     */
    @Override
    public boolean canSeek() {
        return true;
    }

    /**
     * Get the length of the stream. This is the same as the backed file.
     *
     * @return The file stream length
     */
    @Override
    public long getLength() {
        return file.getLength();
    }

    /**
     * Get the current position of the stream.
     *
     * @return The current position
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long getPosition() throws IOException {
        return this.position;
    }

    /**
     * Set the current position of the stream.
     *
     * @param value The new position.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setPosition(long value) throws IOException {
        // if the new position is forwards we can skip a small amount rather opening up a new connection
        if (this.position < value && value - position < maxNetBytesSkip && this.inputStream != null) {
            inputStream.skip(value - position);
        } else if (this.position != value) {
            // cannot reuse stream
            this.reset();
        }
        this.position = value;
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     *
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setLength(long value) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Read data from the file stream into the buffer provided.
     *
     * @param buffer The buffer to write the data.
     * @param offset The offset of the buffer to start writing the data.
     * @param count  The maximum number of bytes to read from.
     * @return The bytes read
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int res = getInputStream().read(buffer, offset, count);
        position += res;
        return res;
    }

    /**
     * Write the data from the buffer provided into the stream.
     *
     * @param buffer The buffer to read the data from.
     * @param offset The offset of the buffer to start reading the data.
     * @param count  The maximum number of bytes to read from the buffer.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
    }

    /**
     * Seek to the offset provided.
     *
     * @param offset The position to seek to.
     * @param origin The type of origin {@link SeekOrigin}
     * @return The new position after seeking.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public long seek(long offset, SeekOrigin origin) throws IOException {
        long pos = this.position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.getLength() - offset;

        this.setPosition(pos);
        return this.position;
    }

    /**
     * Flush the buffers to the associated file.
     */
    public void flush() {
        throw new Error("Unsupported Operation, readonly filesystem");
    }

    /**
     * Close this stream and associated resources.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void close() throws IOException {
        reset();
        this.closed = true;
    }

	/**
     * Reset the stream.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    public void reset() throws IOException {
        if (this.inputStream != null)
            this.inputStream.close();
        this.inputStream = null;
        if (conn != null)
            conn.disconnect();
        conn = null;
    }

    private HttpURLConnection createConnection(String method, String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        return conn;
    }

    private void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void checkStatus(HttpURLConnection conn, int status) throws IOException {
        if (conn.getResponseCode() != status)
            throw new IOException(conn.getResponseCode()
                    + " " + conn.getResponseMessage());
    }

    private void setDefaultHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Cache", "no-store");
        conn.setRequestProperty("Connection", "keep-alive");
    }
}
