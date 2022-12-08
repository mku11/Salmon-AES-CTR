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

public class MemoryStream extends AbsStream {

    private static final int CAPACITY_INCREMENT = 512 * 1024;
    private byte[] bytes;
    private long _position;
    private long _capacity;
    private long _length;

    public MemoryStream(byte[] bytes) {
        this._length = bytes.length;
        this.bytes = bytes;
        this._capacity = bytes.length;
    }

    public MemoryStream() {
        bytes = new byte[CAPACITY_INCREMENT];
        this._capacity = CAPACITY_INCREMENT;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public boolean canSeek() {
        return true;
    }

    @Override
    public long length() {
        return _length;
    }

    @Override
    public long position() throws IOException {
        return _position;
    }

    @Override
    public void position(long value) throws IOException {
        _position = value;
    }

    @Override
    public void setLength(long value) throws IOException {
        checkAndResize(value);
        _capacity = value;
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int bytesRead = (int) Math.min(_length - position(), count);
        System.arraycopy(bytes, (int) _position, buffer, offset, bytesRead);
        position(position() + bytesRead);
        return bytesRead;
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        checkAndResize(_position + count);
        System.arraycopy(buffer, offset, bytes, (int) _position, count);
        position(position() + count);
    }

    private void checkAndResize(long newLength) {
        if(_capacity < newLength) {
            long newCapacity = _capacity + CAPACITY_INCREMENT * ((newLength - _capacity) / CAPACITY_INCREMENT);
            if(newCapacity < newLength)
                newCapacity += CAPACITY_INCREMENT;
            byte [] nBytes = new byte[(int) newCapacity];
            System.arraycopy(bytes, 0, nBytes, 0, (int) _capacity);
            _capacity = newCapacity;
            bytes = nBytes;
        }
        _length = newLength;
    }

    @Override
    public long seek(long offset, SeekOrigin origin) throws IOException {
        long nPos = 0;
        if (origin == SeekOrigin.Begin) {
            nPos = (int) offset;
        } else if (origin == SeekOrigin.Current) {
            nPos = position() + offset;
        } else if (origin == SeekOrigin.End) {
            nPos = (int) (bytes.length - offset);
        }
        checkAndResize(nPos);
        position(nPos);
        return position();
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws IOException {

    }

    public void writeTo(SalmonStream writer) throws Exception {
        int bytesRead;
        long totalBytesRead = 0;
        while(totalBytesRead < _length) {
            bytesRead = (int) Math.min(_length - totalBytesRead, bufferSize);
            writer.write(bytes, (int) totalBytesRead, bytesRead);
            totalBytesRead += bytesRead;
        }
    }

    public byte[] toArray() {
        byte [] nBytes = new byte[(int) _length];
        System.arraycopy(this.bytes, 0, nBytes, 0, (int) _length);
        return nBytes;
    }
}
