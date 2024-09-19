package com.mku.streams;
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

import java.io.*;

/**
 * Piped input and outputstream with blocking flush and close.
 */
public class BlockingInputOutputAdapterStream extends PipedOutputStream {
    private SyncedPipedInputStream inputStream;
    private long position;
    private boolean received;
    private final Object readLock = new Object();
    private final Object receivedLock = new Object();

    class SyncedPipedInputStream extends PipedInputStream {
        private long position = 0;

        public long getPosition() {
            return position;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            int res = super.read(buffer, offset, count);
            position += res;
            synchronized (readLock) {
                readLock.notify();
            }
            return res;
        }
    }

    public BlockingInputOutputAdapterStream() throws IOException {
        super();
        inputStream = new SyncedPipedInputStream();
        connect(inputStream);
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        super.write(buffer, offset, count);
        position += count;
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        waitRead();
    }

    @Override
    public void close() throws IOException {
        super.close();
        waitReceived();
    }

    private void waitRead() {
        synchronized (readLock) {
            while (inputStream.getPosition() != position) {
                try {
                    readLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void waitReceived() {
        synchronized (receivedLock) {
            while (!received) {
                try {
                    receivedLock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void setReceived(boolean value) {
        received = value;
        synchronized (receivedLock) {
            receivedLock.notify();
        }
    }
}