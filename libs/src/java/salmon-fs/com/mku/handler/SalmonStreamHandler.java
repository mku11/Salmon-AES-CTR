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

import com.mku.salmonfs.SalmonFile;
import com.mku.salmonfs.SalmonFileInputStream;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Provides a local stream handler to read a {@link SalmonFile}.
 * Use this class with {@link SalmonStreamConnection}
 * and {@link SalmonStreamHandlerFactory}
 * as a source to apps that cannot use {@link SalmonFileInputStream} directly.
 */
public class SalmonStreamHandler extends URLStreamHandler {

    private final int buffers;
	private final int bufferSize;
    private final int threads;
	private final int backOffset;

    public SalmonStreamHandler(int buffers, int bufferSize, int threads, int backOffset) {
        this.buffers = buffers;
        this.bufferSize = bufferSize;
        this.threads = threads;
		this.backOffset = backOffset;
    }

    /**
     * Open a local SalmonStreamConnection
     * @param u   the URL that this connects to.
     * @return
     * @throws IOException
     */
    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new SalmonStreamConnection(u, buffers, bufferSize, threads, backOffset);
    }
}
