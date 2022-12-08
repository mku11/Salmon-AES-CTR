package com.mku11.salmon.streams;
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

import java.io.IOException;

public abstract class AbsStream {

    private static final int DEFAULT_BUFFER_SIZE = 128 * 1024;
    protected int bufferSize = DEFAULT_BUFFER_SIZE;

    public abstract boolean canRead();

    public abstract boolean canWrite();

    public abstract boolean canSeek();

    public abstract long length();

    public abstract long position() throws IOException;

    public abstract void position(long value) throws Exception;

    public abstract void setLength(long value) throws IOException;

    public abstract int read(byte[] buffer, int offset, int count) throws Exception;

    public abstract void write(byte[] buffer, int offset, int count) throws Exception;

    public abstract long seek(long offset, SeekOrigin origin) throws Exception;

    public abstract void flush();

    public abstract void close() throws IOException;

    public interface OnProgressListener {
        void onProgressChanged(long position, long length);
    }

    public void copyTo(AbsStream stream) throws Exception {
        copyTo(stream, bufferSize, null);
    }

    public void copyTo(AbsStream stream, OnProgressListener progressListener) throws Exception {
        copyTo(stream, bufferSize, progressListener);
    }

    public void copyTo(AbsStream stream, int bufferSize, OnProgressListener progressListener) throws Exception {
        if (!canRead())
            throw new Exception("Target stream not readable");
        if (!stream.canWrite())
            throw new Exception("Target stream not writable");
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        int bytesRead;
        long pos = position();
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = read(buffer, 0, bufferSize)) > 0) {
            stream.write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener.onProgressChanged(position(), length());
        }
        stream.flush();
        position(pos);
    }

    public enum SeekOrigin {
        Begin, Current, End
    }
}
