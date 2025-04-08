package com.mku.fs.streams;
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
import com.mku.fs.file.WSFile;
import com.mku.streams.RandomAccessStream;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * File stream implementation for web service files.
 */
public class WSFileStream extends RandomAccessStream {
    private static final String PATH = "path";
    private static final String POSITION = "position";
    private static final String LENGTH = "length";

    private static String boundary = "*******";

    private HttpURLConnection conn;

    /**
     * The network input stream associated.
     */
    private InputStream inputStream;

    /**
     * The network output stream associated.
     */
    private OutputStream outputStream;

    private boolean closed;

    /**
     * The java file associated with this stream.
     */
    private final WSFile file;

    private long maxNetBytesSkip = 32768;

    /**
     * Maximum amount of bytes allowed to skip forwards when seeking otherwise will open a new connection
     *
     * @param maxNetBytesSkip The maximum number of bytes to skip
     */
    public void setMaxNetBytesSkip(long maxNetBytesSkip) {
        this.maxNetBytesSkip = maxNetBytesSkip;
    }

    private boolean canWrite;
    private long position;

    /**
     * Position to start writing from
     */
    private long startWritePosition;

    /**
     * Construct a file stream from a JavaFile.
     * This will create a wrapper stream that will route read() and write() to the FileChannel
     *
     * @param file The JavaFile that will be used to get the read/write stream
     * @param mode The mode "r" for read "rw" for write
     * @throws FileNotFoundException Thrown if file not found
     */
    public WSFileStream(WSFile file, String mode) throws FileNotFoundException {
        this.file = file;
        if (mode.equals("rw")) {
            canWrite = true;
        }
    }

    private InputStream getInputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.inputStream == null) {
            long startPosition = this.getPosition();
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.file.getPath());
                params.put(POSITION, String.valueOf(startPosition));
                conn = createConnection("GET", file.getServicePath() + "/api/get", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.connect();
                checkStatus(conn, startPosition > 0 ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK);
                this.inputStream = new BufferedInputStream(conn.getInputStream());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return this.inputStream;
    }

    private OutputStream getOutputStream() throws IOException {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.outputStream == null) {
            startWritePosition = this.getPosition();
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put(PATH, this.file.getPath());
                params.put(POSITION, String.valueOf(startWritePosition));
                conn = createConnection("POST", file.getServicePath() + "/api/upload", params);
                setDefaultHeaders(conn);
                setServiceAuth(conn);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.connect();
                outputStream = conn.getOutputStream();
                byte[] headerData = getHeader();
                outputStream.write(headerData, 0, headerData.length);
                outputStream.flush();
            } catch (Exception e) {
                close();
                throw new RuntimeException(e);
            }
        }
        if (this.outputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.outputStream;
    }

    /**
     * Check if stream can read from file.
     *
     * @return True if readable
     */
    @Override
    public boolean canRead() {
        return !canWrite;
    }

    /**
     * Check if stream can write to file.
     *
     * @return True if writable
     */
    @Override
    public boolean canWrite() {
        return canWrite;
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
        if (this.closed)
            throw new IOException("Stream is closed");
        HttpURLConnection conn = null;
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put(PATH, file.getPath());
            params.put(LENGTH, Long.toString(value));
            conn = createConnection("PUT", file.getServicePath() + "/api/setLength", params);
            setDefaultHeaders(conn);
            setServiceAuth(conn);
            conn.connect();
            checkStatus(conn, HttpURLConnection.HTTP_OK);
        } finally {
            closeConnection(conn);
        }
        reset();
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
        getOutputStream().write(buffer, offset, count);
        position += Math.min(buffer.length, count);
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
        reset();
        this.closed = true;
    }

    private void sendFooter() throws IOException {
        if (this.outputStream != null) {
            byte[] footerData = getFooter();
            outputStream.write(footerData, 0, footerData.length);
            outputStream.flush();
            checkStatus(conn, startWritePosition > 0 ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK);
        }
    }

    public void reset() throws IOException {
        if (this.outputStream != null)
            sendFooter();
        if (this.inputStream != null)
            this.inputStream.close();
        this.inputStream = null;
        if (this.outputStream != null)
            this.outputStream.close();
        this.outputStream = null;
        if (conn != null)
            conn.disconnect();
        conn = null;
        file.reset();
    }

    private HttpURLConnection createConnection(String method, String url) throws IOException {
        return createConnection(method, url, null);
    }

    private HttpURLConnection createConnection(String method, String url, HashMap<String, String> params) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (stringBuilder.length() == 0)
                    stringBuilder.append("?");
                else
                    stringBuilder.append("&");
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            }
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url + stringBuilder).openConnection();
        conn.setUseCaches(false);
        conn.setDefaultUseCaches(false);
        conn.setRequestMethod(method);
        conn.setDoInput(true);
        if(!method.equals("GET") && !method.equals("HEAD"))
			conn.setDoOutput(true);
        return conn;
    }

    private byte[] getHeader() {
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; " +
                "name=\"file\"; " +
                "filename=\"" + this.file.getName() + "\"" +
                "\r\n\r\n";
        return header.getBytes();
    }

    private byte[] getFooter() {
        return ("\r\n--" + boundary + "--").getBytes();
    }

    private void closeConnection(HttpURLConnection conn) {
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
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

    private void setServiceAuth(HttpURLConnection conn) {
        String encoding = new Base64().encode((file.getCredentials().getServiceUser()
                + ":" + file.getCredentials().getServicePassword()).getBytes());
        conn.setRequestProperty("Authorization", "Basic " + encoding);
    }

    private void addParameters(OutputStream os, HashMap<String, String> params) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (stringBuilder.length() > 0)
                stringBuilder.append("&");
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        byte[] paramData = stringBuilder.toString().getBytes();
        os.write(paramData, 0, paramData.length);
        os.flush();
    }

    private void checkStatus(HttpURLConnection conn, int status) throws IOException {
        if (conn.getResponseCode() != status)
            throw new IOException(conn.getResponseCode()
                    + " " + conn.getResponseMessage());
    }

    private void setDefaultHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Cache", "no-store");
    }
}
