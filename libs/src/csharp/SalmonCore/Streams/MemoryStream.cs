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

using System;
using System.IO;

namespace Mku.Streams;

/**
 * Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
 * If the memory buffer is not specified then an internal resizable buffer will be created.
 */
public class MemoryStream : Stream {

    /**
     * Increment to resize to when capacity is exhausted.
     */
    private static readonly int _INITIAL_CAPACITY = 128 * 1024;

    /**
     * Buffer to store the data. This can be provided via the constructor.
     */
    private byte[] _bytes;

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
     * Create a memory stream.
     *
     * @param bytes Optional existing byte array to use as backing buffer.
     * If omitted a new backing array will be created automatically.
     */
    public MemoryStream(byte[] bytes) {
        this._length = bytes.Length;
        this._bytes = bytes;
        this._capacity = bytes.Length;
    }

    /**
     * Create a memory stream.
     */
    public MemoryStream() {
        _bytes = new byte[_INITIAL_CAPACITY];
        this._capacity = _INITIAL_CAPACITY;
    }

    /**
	 * Check if the stream can be used for reading
     * @return True if the stream can be used for reading.
     */
    override
    public bool CanRead => true;

    /**
     * @return If the stream can be used for writing.
     */
    override
    public bool CanWrite => true;

    /**
     * @return If the stream is seekable.
     */
    override
    public bool CanSeek => true;

    /**
     * @return The length of the stream.
     */
    override
    public long Length => _length;

    /**
     * @return The position of the stream.
     */
    override
    public long Position
    {
        get
        {
            return _position;
        }
        set
        {
            _position = value;
        }
    }

    /**
     * Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
     * current capacity.
     *
     * @param value The new file length.
     */
    override
    public void SetLength(long value) {
        checkAndResize(value);
        _capacity = value;
    }

    /**
     * Read a sequence of bytes into the provided buffer.
     *
     * @param buffer The buffer to write the bytes that are read from the stream.
     * @param offset The offset of the buffer that will be used to write the bytes.
     * @param count  The length of the bytes that can be read from the stream and written to the buffer.
     * @return The number of bytes read.
     * @Thrown if there is an IO error.
     */
    override
    public int Read(byte[] buffer, int offset, int count) {
        int bytesRead = (int) Math.Min(_length - Position, count);
        Array.Copy(_bytes, (int) _position, buffer, offset, bytesRead);
        Position = Position + bytesRead;
        if (bytesRead <= 0)
            return 0;
        return bytesRead;
    }

    /**
     * Write a sequence of bytes into the stream.
     *
     * @param buffer The buffer that the bytes will be read from.
     * @param offset The position offset that will be used to read from the buffer.
     * @param count  The number of bytes that will be written to the stream.
     */
    override
    public void Write(byte[] buffer, int offset, int count) {
        checkAndResize(_position + count);
        Array.Copy(buffer, offset, _bytes, (int) _position, count);
        Position = Position + count;
    }

    /**
     * Check if there is no more space in the byte array and increase the capacity.
     *
     * @param newLength The new length of the stream.
     */
    private void checkAndResize(long newLength) {
        if (this._capacity < newLength) {
            long newCapacity = newLength * 2;
            if (newCapacity > int.MaxValue)
                throw new Exception("Size too large");
            byte[] nBytes = new byte[(int) newCapacity];
            for (int i = 0; i < this._capacity; i++)
                nBytes[i] = this._bytes[i];
            this._capacity = newCapacity;
            this._bytes = nBytes;
        }
        this._length = newLength;
    }

    /**
     * Seek to a position in the stream.
     *
     * @param offset The offset to use.
     * @param origin Possible Values: Begin, Current, End
     * @return The new position after seeking.
     */
    override
    public long Seek(long offset, SeekOrigin origin) {
        long nPos = 0;
        if (origin == SeekOrigin.Begin) {
            nPos = (int) offset;
        } else if (origin == SeekOrigin.Current) {
            nPos = Position + offset;
        } else if (origin == SeekOrigin.End) {
            nPos = (int) (_bytes.Length - offset);
        }
        checkAndResize(nPos);
        Position = nPos;
        return Position;
    }

    /**
     * Flush the stream. Not-Applicable for memory stream.
     */
    override
    public void Flush() {
        // nop
    }

    /**
     * Close any resources the stream is using. Not-Applicable for memory stream.
     */
    override
    public void Close() {
        // nop
    }

    /**
     * Convert the stream to an array:
     *
     * @return A byte array containing the data from the stream.
     */
    public byte[] ToArray() {
        byte[] nBytes = new byte[(int) _length];
        Array.Copy(this._bytes, 0, nBytes, 0, (int) _length);
        return nBytes;
    }
}
