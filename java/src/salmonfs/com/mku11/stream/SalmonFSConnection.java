package com.mku11.stream;
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

import com.mku11.file.JavaFile;
import com.mku11.media.SalmonMediaDataSource;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

public class SalmonFSConnection extends HttpURLConnection {
    private static final int MEDIA_BUFFER_SIZE = 0;
    private static final int MEDIA_THREADS = 2;

    private SalmonFile salmonFile;
    private SalmonMediaDataSource salmonMediaDataSource;
    private long pendingSeek = 0;

    protected SalmonFSConnection(URL url) throws IOException {
        super(url);
        setFile(url.toString().replace("http://localhost/", ""));
    }

    @Override
    public void disconnect() {
        try {
            salmonMediaDataSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    private void setFile(String path) {
        path = URLDecoder.decode(path);
        IRealFile rfile = new JavaFile(path);
        salmonFile = new SalmonFile(rfile, SalmonDriveManager.getDrive());
    }

    @Override
    public void connect() throws IOException {

    }

    public void setRequestProperty(String key, String value) {
        if (key.equals("Range")) {
            String val = value.split("=")[1];
            pendingSeek = Long.parseLong(val.split("-")[0]);
        }
    }

    public String getContentType() {

        String type = null;
        try {
            type = URLConnection.guessContentTypeFromName(salmonFile.getBaseName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return type;
    }

    public int getContentLength() {
        try {
            return (int) salmonFile.getSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getResponseCode() {
        if (pendingSeek > 0)
            return 206;
        else
            return 200;
    }

    public long getContentLengthLong() {
        try {
            return salmonFile.getSize();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public InputStream getInputStream() {
        if (salmonMediaDataSource == null) {
            try {
                salmonMediaDataSource = new SalmonMediaDataSource(salmonFile, MEDIA_BUFFER_SIZE, MEDIA_THREADS);
                if (pendingSeek > 0) {
                    salmonMediaDataSource.skip(pendingSeek);
                    pendingSeek = 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return salmonMediaDataSource;
    }
}
