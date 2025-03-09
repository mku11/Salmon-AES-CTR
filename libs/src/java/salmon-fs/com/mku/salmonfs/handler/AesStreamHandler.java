package com.mku.salmonfs.handler;
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

import com.mku.fs.file.IVirtualFile;
import com.mku.salmonfs.file.AesFile;
import com.mku.salmonfs.streams.AesFileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a local stream handler to read a {@link AesFile}
 * as a source to apps that cannot use {@link AesFileInputStream} directly.
 */
public class AesStreamHandler extends URLStreamHandler {
    private static final int BUFFERS = 4;
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int THREADS = 1;
    private static final int BACKOFFSET = 256 * 1024;
    private static AesStreamHandler instance;

    private final HashMap<String, IVirtualFile> requests = new HashMap<>();

    private AesStreamHandler() {
        URL.setURLStreamHandlerFactory(protocol -> {
            if (protocol.equals("http"))
                return this;
            return null;
        });
    }

    /**
     * Get the instance.
     *
     * @return
     */
    public static AesStreamHandler getInstance() {
        if (instance == null) {
            instance = new AesStreamHandler();
        }
        return instance;
    }

    /**
     * Register a path to be handled by this handler.
     * @param path The URL path
     * @param file The file associated with this path.
     */
    public void register(String path, IVirtualFile file) {
        requests.put(path, file);
    }

    /**
     * Unregister a path.
     * @param path The URL path
     */
    public void unregister(String path) {
        if (path != null)
            requests.remove(path);
        else {
            for (Map.Entry<String, IVirtualFile> entry : requests.entrySet()) {
                requests.remove(entry.getKey());
            }
        }
    }

    /**
     * Open a local connection to an encrypted stream.
     *
     * @param u the URL that this connects to.
     * @return URLConnection to inject the decoded stream
     */
    @Override
    protected URLConnection openConnection(URL u) {
        return new HttpURLConnection(u) {
            private long pendingSeek = 0;
            private InputStream stream;

            private IVirtualFile getFile() throws IOException {
                String path = u.toString();
                if (!requests.containsKey(path))
                    throw new IOException("Could not find path in registered requests");
                return requests.get(path);
            }

            @Override
            public void disconnect() {
                try {
                    if (stream != null)
                        stream.close();
                    stream = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
                // nop
            }

            public void setRequestProperty(String key, String value) {
                if (key.equals("Range")) {
                    String val = value.split("=")[1];
                    pendingSeek = Long.parseLong(val.split("-")[0]);
                }
            }

            public String getContentType() {
                try {
                    return URLConnection.guessContentTypeFromName(getFile().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            public int getContentLength() {
                try {
                    return (int) getFile().getSize();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public long getContentLengthLong() {
                try {
                    return getFile().getSize();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public int getResponseCode() {
                if (pendingSeek > 0)
                    return 206;
                else
                    return 200;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                AesFile file = (AesFile) getFile();
                stream = new AesFileInputStream(file,
                        BUFFERS, BUFFER_SIZE, THREADS, BACKOFFSET);
                if (pendingSeek > 0) {
                    pendingSeek = stream.skip(pendingSeek);
                }
                return stream;
            }
        };
    }
}
