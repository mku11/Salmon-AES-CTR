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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Provides a local stream URL handler to read an {@link AesFile} as a source.
 * This works with 3rd party libraries and apps that can read file via HTTP URLs.
 */
public class AesStreamHandler extends URLStreamHandler {
    private static final int BUFFERS = 4;
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int THREADS = 1;
    private static final int BACKOFFSET = 256 * 1024;
    private static AesStreamHandler instance;
    private static URLStreamHandler defaultStreamHandler;
    private static final String protocol = "https";
    private static final String prefix = protocol + "://localhost/salmon?key=";

    private final HashMap<String, AesFile> requests = new HashMap<>();

    private AesStreamHandler() {
        if (defaultStreamHandler == null)
            defaultStreamHandler = getURLStreamHandler();
        URL.setURLStreamHandlerFactory(protocol -> {
            if (protocol.equals(AesStreamHandler.protocol))
                return this;
            return null;
        });
    }

    private static URLStreamHandler getURLStreamHandler() {
        try {
            Method method = URL.class.getDeclaredMethod("getURLStreamHandler", String.class);
            method.setAccessible(true);
            return (URLStreamHandler) method.invoke(null, protocol);
        } catch (Exception e) {
		}
		
		try {
			URL url = new URL(prefix);
			Field handlerField = URL.class.getDeclaredField("handler");
			handlerField.setAccessible(true);
			return (URLStreamHandler)handlerField.get(url);
		} catch (Exception e) {
		}
		
		return null;
    }

    /**
     * Get the instance.
     *
     * @return A URL stream handler for encrypted streams.
     */
    public static AesStreamHandler getInstance() {
        if (instance == null) {
            instance = new AesStreamHandler();
        }
        return instance;
    }

    /**
     * Register a unique key associated to an encrypted AesFile. This will return a URL path that you can use to pass
     * to 3rd party libraries that support URLConnection.
     *
     * @param key  A unique key.
     * @param file The file associated.
     * @return The URL path to use.
     */
    public String register(String key, AesFile file) {
		try {
			String regPath = prefix + URLEncoder.encode(key, StandardCharsets.UTF_8.name());
			requests.put(regPath, file);
			return regPath;
		} catch (Exception ex) {
			throw new RuntimeException("Could not encode key", ex);
		}
    }

    /**
     * Unregister a path.
     *
     * @param path The URL path
     */
    public void unregister(String path) {
        if (path != null)
            requests.remove(path);
        else {
            for (Map.Entry<String, AesFile> entry : requests.entrySet()) {
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
        String path = u.toString();
        if (!requests.containsKey(path)) {
            URLConnection conn = openDefaultConnection(u);
            return conn;
        }
        return new AesURLConnection(u);
    }

    private URLConnection openDefaultConnection(URL u) {
        try {
            Method method = URLStreamHandler.class.getDeclaredMethod("openConnection", URL.class);
            method.setAccessible(true);
            return (URLConnection) method.invoke(defaultStreamHandler, u);
        } catch (Exception e) {
            return null;
        }
    }

    private class AesURLConnection extends HttpURLConnection {
        private long pendingSeek = 0;
        private InputStream stream;

        public AesURLConnection(URL url) {
            super(url);
            this.url = url;
        }

        private AesFile getFile() throws IOException {
            String path = url.toString();
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
                return (int) getFile().getLength();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public long getContentLengthLong() {
            try {
                return getFile().getLength();
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
            AesFile file = getFile();
            stream = new AesFileInputStream(file,
                    BUFFERS, BUFFER_SIZE, THREADS, BACKOFFSET);
            if (pendingSeek > 0) {
                pendingSeek = stream.skip(pendingSeek);
            }
            return stream;
        }
    }
}
