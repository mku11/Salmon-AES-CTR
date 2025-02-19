package com.mku.file;
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

import com.mku.streams.RandomAccessStream;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;

/**
 * An advanced Salmon File Stream implementation for java files.
 * This class is used internally for random file access of remote physical (real) files.
 */
public class JavaHttpFileStream extends RandomAccessStream {
    public CloseableHttpClient client;

    /**
     * The network input stream associated.
     */
    private InputStream inputStream;

    private boolean closed;

    /**
     * The java file associated with this stream.
     */
    private final JavaHttpFile file;

    private long maxNetBytesSkip = 32768;

    /**
     * Maximum amount of bytes allowed to skip forwards when seeking otherwise will open a new connection
     *
     * @param maxNetBytesSkip The maximum number of bytes to skip
     */
    public void setMaxNetBytesSkip(long maxNetBytesSkip) {
        this.maxNetBytesSkip = maxNetBytesSkip;
    }

    private long position;
    private CloseableHttpResponse httpResponse;

    /**
     * Construct a file stream from a JavaFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JavaFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     * @throws FileNotFoundException Thrown if file not found
     */
    public JavaHttpFileStream(JavaHttpFile file, String mode) {
        this.file = file;
        if (mode.contains("w")) {
            throw new UnsupportedOperationException("Unsupported Operation, readonly filesystem");
        }
    }

    private InputStream getInputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.inputStream == null) {
            createClient();
            long startPosition = this.getPosition();
            URIBuilder uriBuilder;
            httpResponse = null;
            try {
                uriBuilder = new URIBuilder(file.getPath());
                HttpGet httpGet = new HttpGet(uriBuilder.build());
                setDefaultHeaders(httpGet);
				if(this.position > 0)
					httpGet.addHeader("Range", "bytes=" + this.position + "-");
                httpResponse = client.execute(httpGet);
                checkStatus(httpResponse, startPosition > 0 ? HttpStatus.SC_PARTIAL_CONTENT : HttpStatus.SC_OK);
                this.inputStream = new BufferedInputStream(httpResponse.getEntity().getContent());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (this.inputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.inputStream;
    }

    private void createClient() throws IOException {
        if (client != null)
            throw new IOException("A connection is already open");
        client = HttpClients.createDefault();
    }

    /**
     * True if stream can read from file.
     *
     * @return True if readable
     */
    @Override
    public boolean canRead() {
        return true;
    }

    /**
     * True if stream can write to file.
     *
     * @return True if writable
     */
    @Override
    public boolean canWrite() {
        return false;
    }

    /**
     * True if stream can seek.
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
    public long length() {
        return file.length();
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
            pos = file.length() - offset;

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

    public void reset() throws IOException {
        if (this.inputStream != null)
            this.inputStream.close();
        this.inputStream = null;
        if (client != null)
            client.close();
        client = null;
    }

    private void checkStatus(HttpResponse httpResponse, int status) throws IOException {
        if (httpResponse.getStatusLine().getStatusCode() != status)
            throw new IOException(httpResponse.getStatusLine().getStatusCode()
                    + " " + httpResponse.getStatusLine().getReasonPhrase());
    }

    private void setDefaultHeaders(HttpRequest request) {
        request.addHeader("Cache", "no-store");
        request.addHeader("Connection", "keep-alive");
    }
}
