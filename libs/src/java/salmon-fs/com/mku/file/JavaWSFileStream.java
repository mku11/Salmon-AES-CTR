package com.mku.file;
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

import com.mku.convert.Base64;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.URISyntaxException;

/**
 * An advanced Salmon File Stream implementation for java files.
 * This class is used internally for random file access of remote physical (real) files.
 */
public class JavaWSFileStream extends RandomAccessStream {
    private static final String PATH = "path";
    public static CloseableHttpClient client = HttpClients.createDefault();
    /**
     * The network input stream associated.
     */
    private InputStream inputStream;

    /**
     * The network output stream associated.
     */
    private OutputStream outputStream;

    private byte[] buffer = null;
    private int bufferPosition = 0;
    private boolean closed;

    /**
     * The java file associated with this stream.
     */
    private final JavaWSFile file;

    private boolean canWrite;
    private long position;
    private CloseableHttpResponse httpResponse;
    private CloseableHttpResponse outHttpResponse;

    /**
     * Construct a file stream from a JavaFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JavaFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     * @throws FileNotFoundException Thrown if file not found
     */
    public JavaWSFileStream(JavaWSFile file, String mode) throws FileNotFoundException {
        this.file = file;
        if (mode.equals("rw")) {
            canWrite = true;
        }
    }

    private InputStream getInputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.inputStream == null) {
            URIBuilder uriBuilder;
            httpResponse = null;
            try {
                uriBuilder = new URIBuilder(file.getServicePath() + "/api/get");
                uriBuilder.addParameter(PATH, this.file.getPath());
                HttpGet httpGet = new HttpGet(uriBuilder.build());
                httpGet.addHeader("Cache", "no-store");
                httpGet.addHeader("KeepAlive", "true");
                httpGet.addHeader("Byte-Range", "bytes=" + this.position + "-");
                setServiceAuth(httpGet);
                httpResponse = client.execute(httpGet);
                if (this.position > 0)
                    checkStatus(httpResponse, HttpStatus.SC_PARTIAL_CONTENT);
                else
                    checkStatus(httpResponse, HttpStatus.SC_OK);
                this.inputStream = httpResponse.getEntity().getContent();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        if (this.inputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.inputStream;
    }

    private OutputStream getOutputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.outputStream == null) {
            URIBuilder uriBuilder;
            HttpPost httpPost = null;
            OutputAdapterStream stream = null;
            try {
                uriBuilder = new URIBuilder(file.getServicePath() + "/api/upload");
                uriBuilder.addParameter(PATH, this.file.getPath());
                httpPost = new HttpPost(uriBuilder.build());
                httpPost.addHeader("Cache", "no-store");
                httpPost.addHeader("KeepAlive", "true");
                setServiceAuth(httpPost);

                stream = new OutputAdapterStream();
                this.outputStream = stream;
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            HttpPost finalHttpPost = httpPost;
            OutputAdapterStream finalStream = stream;
            new Thread(() -> {
                try {
                    HttpEntity entity = MultipartEntityBuilder.create()
                            .addPart("file", new InputStreamBody(finalStream.getBackStream(), file.getBaseName()))
                            .build();
                    finalHttpPost.setEntity(entity);
                    outHttpResponse = client.execute(finalHttpPost);
                    checkStatus(outHttpResponse, HttpStatus.SC_OK);
                    finalStream.received();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (outHttpResponse != null) {
                        try {
                            outHttpResponse.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }).start();
        }
        if (this.outputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.outputStream;
    }

    /**
     * True if stream can read from file.
     *
     * @return True if readable
     */
    @Override
    public boolean canRead() {
        return !canWrite;
    }

    /**
     * True if stream can write to file.
     *
     * @return True if writable
     */
    @Override
    public boolean canWrite() {
        return canWrite;
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
        this.position = value;
        this.reset();
    }

    /**
     * Set the length of the stream. This is applicable for write streams only.
     *
     * @param value The new length.
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void setLength(long value) throws IOException {
        HttpPut httpPut = new HttpPut(file.getServicePath() + "/api/setLength?path=" + file.getPath() + "&length=" + value);
        setServiceAuth(httpPut);
        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = client.execute(httpPut);
            checkStatus(httpResponse, HttpStatus.SC_OK);
        } finally {
            if (httpResponse != null)
                httpResponse.close();
        }
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
        return getInputStream().read(buffer, offset, count);
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
        getOutputStream().write(buffer, offset, count);
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
        if (inputStream != null)
            this.getInputStream();
        else if (outputStream != null)
            this.getOutputStream();
        return this.position;

    }

    /**
     * Flush the buffers to the associated file.
     */
    public void flush() {
        try {
            if (outputStream != null)
                outputStream.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Close this stream and associated resources.
     *
     * @throws IOException Thrown if there is an IO error.
     */
    @Override
    public void close() throws IOException {
        if (inputStream != null)
            inputStream.close();
        if (outputStream != null)
            outputStream.close();
        if (httpResponse != null)
            httpResponse.close();
        if (outHttpResponse != null)
            outHttpResponse.close();
        this.closed = true;
    }

    public void reset() throws IOException {
        if (this.inputStream != null)
            this.inputStream.close();
        this.inputStream = null;

        if (this.outputStream != null)
            this.outputStream.close();
        this.outputStream = null;

        this.buffer = null;
        this.bufferPosition = 0;
    }

    private void setServiceAuth(HttpRequest httpRequest) {
        String encoding = new Base64().encode((file.getCredentials().getServiceUser() + ":"
                + file.getCredentials().getServicePassword()).getBytes());
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
    }

    private void checkStatus(HttpResponse httpResponse, int status) throws IOException {
        if (httpResponse.getStatusLine().getStatusCode() != status)
            throw new IOException(httpResponse.getStatusLine().getStatusCode()
                    + " " + httpResponse.getStatusLine().getReasonPhrase());
    }

    /**
     * Redirection adapter for uploading a multipart file without loading all content at once
     */
    private class OutputAdapterStream extends OutputStream {
        MemoryStream memoryStream = new MemoryStream();
        private boolean pendingRead = false;
        private final Object readWriteLock = new Object();
        private final Object doneLock = new Object();
        private final Object receivedLock = new Object();
        private boolean closed;
        private boolean received;

        InputStream stream = new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] bytes = new byte[1];
                return read(bytes, 0, bytes.length);
            }

            @Override
            public int available() {
                synchronized (readWriteLock) {
                    if (closed)
                        return 0;
                    return (int) (memoryStream.length() - memoryStream.getPosition());
                }
            }

            @Override
            public int read(byte[] buffer, int offset, int count) throws IOException {
                int bytesRead;
                synchronized (readWriteLock) {
                    while (!pendingRead && !closed) {
                        try {
                            readWriteLock.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    bytesRead = memoryStream.read(buffer, 0, count);
                    // we read all pending data
                    if (memoryStream.getPosition() == memoryStream.length()) {
                        pendingRead = false;
                        readWriteLock.notifyAll();
                    }
                }
                return bytesRead;
            }
        };


        @Override
        public void write(int b) throws IOException {
            this.write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            synchronized (readWriteLock) {
                while (pendingRead && !closed) {
                    try {
                        readWriteLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
                memoryStream.setPosition(0);
                memoryStream.write(buffer, offset, count);
                memoryStream.setLength(count);
                pendingRead = true;
                memoryStream.setPosition(0);
                readWriteLock.notifyAll();
            }
        }

        public InputStream getBackStream() {
            return stream;
        }

        @Override
        public void close() throws IOException {
            synchronized (readWriteLock) {
                while (pendingRead) {
                    try {
                        readWriteLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            synchronized (readWriteLock) {
                closed = true;
                readWriteLock.notifyAll();
            }
            synchronized (receivedLock) {
                while(!received) {
                    try {
                        receivedLock.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                super.close();
            }
        }

        public void received() {
            synchronized (receivedLock) {
                received = true;
                receivedLock.notify();
            }
        }
    }
}
