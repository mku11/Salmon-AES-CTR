package com.mku.handler;
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

import com.mku.file.JavaFile;
import com.mku.salmonfs.SalmonFileInputStream;
import com.mku.file.IRealFile;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.Charset;

/**
 * Provides a local connection to read a {@link SalmonFile}.
 * Use this class with {@link SalmonStreamHandler}
 * and {@link SalmonStreamHandlerFactory}
 * as a source to apps that cannot use {@link SalmonFileInputStream} directly.
 */
public class SalmonStreamConnection extends HttpURLConnection {
    private final int buffers;
	private final int bufferSize;
    private final int threads;
	private final int backOffset;

    /**
     * The file to be read.
     */
    private SalmonFile salmonFile;

    /**
     * The input stream to read from.
     */
    private SalmonFileInputStream salmonDataSource;
    private long pendingSeek = 0;

    /**
     * Instantiates a connection that can provide encrypted contents.
     *
     * @param url Dummy url.
     */
    protected SalmonStreamConnection(URL url, int buffers, int bufferSize, int threads, int backOffset) {
        super(url);
		this.buffers = buffers;
        this.bufferSize = bufferSize;
        this.threads = threads;
		this.backOffset = backOffset;
		
        setFile(url.toString().replace("http://localhost/", ""));
    }

    /**
     * Disconnect.
     */
    @Override
    public void disconnect() {
        try {
            salmonDataSource.close();
			salmonDataSource = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Using a Proxy - N/A.
     *
     * @return
     */
    @Override
    public boolean usingProxy() {
        return false;
    }

    /**
     * Set the file to read the contents from.
     *
     * @param path
     */
    private void setFile(String path) {
        path = URLDecoder.decode(path, Charset.defaultCharset());
        IRealFile rfile = new JavaFile(path);
        salmonFile = new SalmonFile(rfile, SalmonDriveManager.getDrive());
    }

    /**
     * Connect to the {@link SalmonFileInputStream}.
     */
    @Override
    public void connect() {
        // no op
    }

    /**
     * Set the request property.
     *
     * @param key   the keyword by which the request is known
     *              (e.g., "{@code Accept}").
     * @param value the value associated with it.
     */
    public void setRequestProperty(String key, String value) {
        if (key.equals("Range")) {
            String val = value.split("=")[1];
            pendingSeek = Long.parseLong(val.split("-")[0]);
        }
    }

    /**
     * Get content type.
     *
     * @return
     */
    public String getContentType() {

        String type = null;
        try {
            type = URLConnection.guessContentTypeFromName(salmonFile.getBaseName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return type;
    }

    /**
     * Get the content length.
     *
     * @return
     */
    public int getContentLength() {
        try {
            return (int) salmonFile.getSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get the response code.
     *
     * @return
     */
    public int getResponseCode() {
        if (pendingSeek > 0)
            return 206;
        else
            return 200;
    }

    /**
     * Get the content length.
     *
     * @return
     */
    public long getContentLengthLong() {
        try {
            return salmonFile.getSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get a {@link SalmonFileInputStream}.
     *
     * @return
     */
    @Override
    public InputStream getInputStream() {
        if (salmonDataSource == null) {
            try {
                salmonDataSource = new SalmonFileInputStream(salmonFile, buffers, bufferSize, threads, backOffset);
                if (pendingSeek > 0) {
                    pendingSeek = salmonDataSource.skip(pendingSeek);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return salmonDataSource;
    }
}
