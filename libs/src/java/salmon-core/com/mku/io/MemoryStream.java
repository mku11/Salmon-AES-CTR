package com.mku.io;
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

/**
 * Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
 * If the memory buffer is not specified then an internal resizable buffer will be created.
 */
public class MemoryStream extends RandomAccessStream {

    /**
     * Increment to resize to when capacity is exhausted.
     */
    private static final int CAPACITY_INCREMENT = 128 * 1024;

    /**
     * Buffer to store the data. This can be provided via the constructor.
     */
    private byte[] bytes;

    /**
     * Current position of the stream.
     */
    private long _position;

    /**
     * Current capacity.
     */
    private long _capacity;

    /**
     * Current length of the stream.
     */
    private long _length;

    /**
     * Create a memory stream backed by an existing byte-array.
     * @param bytes
     */
    public MemoryStream(byte[] bytes) {
        this._length = bytes.length;
        this.bytes = bytes;
        this._capacity = bytes.length;
    }

    /**
     * Create a memory stream.
     */
    public MemoryStream() {
        bytes = new byte[CAPACITY_INCREMENT];
        this._capacity = CAPACITY_INCREMENT;
    }

    /**
     * @return Always True.
     */
    @Override
    public boolean canRead() {
        return true;
    }

    /**
     * @return Always True.
     */
    @Override
    public boolean canWrite() {
        return true;
    }

    /**
     * @return Always True.
     */
    @Override
    public boolean canSeek() {
        return true;
    }

    /**
     *
     * @return The length of the stream.
     */
    @Override
    public long length() {
        return _length;
    }

    /**
     *
     * @return The position of the stream.
     * @throws IOException
     */
    @Override
    public long position() throws IOException {
        return _position;
    }

    /**
     * Changes the current position of the stream. For more options use seek() method.
     * @param value The new position of the stream.
     * @throws IOException
     */
    @Override
    public void position(long value) throws IOException {
        _position = value;
    }

    /**
     * Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
     * current capacity.
     * @param value
     * @throws IOException
     */
    @Override
    public void setLength(long value) throws IOException {
        checkAndResize(value);
        _capacity = value;
    }

    /**
     * Read a sequence of bytes into the provided buffer.
     * @param buffer The buffer to write the bytes that are read from the stream.
     * @param offset The offset of the buffer that will be used to write the bytes.
     * @param count The length of the bytes that can be read from the stream and written to the buffer.
     * @return
     * @throws IOException
     */
    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int bytesRead = (int) Math.min(_length - position(), count);
        System.arraycopy(bytes, (int) _position, buffer, offset, bytesRead);
        position(position() + bytesRead);
        if (bytesRead <= 0)
            return -1;
        return bytesRead;
    }

    /**
     * Write a sequence of bytes into the stream.
     * @param buffer The buffer that the bytes will be read from.
     * @param offset The position offset that will be used to read from the buffer.
     * @param count The number of bytes that will be written to the stream.
     * @throws IOException
     */
    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        checkAndResize(_position + count);
        System.arraycopy(buffer, offset, bytes, (int) _position, count);
        position(position() + count);
    }

    /**
     * Check if there is no more space in the byte array and increase the capacity.
     * @param newLength The new length of the stream.
     */
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

    /**
     * Seek to a position in the stream.
     * @param offset
     * @param origin Possible Values: Begin, Current, End
     * @return
     * @throws IOException
     */
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

    /**
     * Flush the stream. Not-Applicable for memory stream.
     */
    @Override
    public void flush() {

    }

    /**
     * Close any resources the stream is using. Not-Applicable for memory stream.
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * Convert the stream to an array:
     * @return A byte array containing the data from the stream.
     */
    public byte[] toArray() {
        byte [] nBytes = new byte[(int) _length];
        System.arraycopy(this.bytes, 0, nBytes, 0, (int) _length);
        return nBytes;
    }
}
