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

/// <summary>
/// Memory Stream for seeking, reading, and writing to a memory buffer (modeled after C# MemoryStream).
/// </summary>
public class MemoryStream : RandomAccessStream
{
    private static readonly int _INITIAL_CAPACITY = 128 * 1024;
    private byte[] _bytes;
    private long _position;
    private long _capacity;
    private long _length;

    /// <summary>
    /// Create a memory stream.
    /// </summary>
    /// <param name="bytes">existing byte array to use as backing buffer.</param>
    public MemoryStream(byte[] bytes)
    {
        this._length = bytes.Length;
        this._bytes = bytes;
        this._capacity = bytes.Length;
    }

    /// <summary>
    /// 
    /// Create a memory stream.
    /// </summary>
    public MemoryStream()
    {
        _bytes = new byte[_INITIAL_CAPACITY];
        this._capacity = _INITIAL_CAPACITY;
    }

    /// <summary>
    ///Check if the stream can be used for reading 
    /// </summary>
    override
    public bool CanRead => true;

    /// <summary>
    /// Check if the stream can be used for writing.
    /// </summary>
    override
    public bool CanWrite => true;

    /// <summary>
    /// If the stream is seekable.
    /// </summary>
    override
    public bool CanSeek => true;

    /// <summary>
    /// The length of the stream.
    /// </summary>
    override
    public long Length => _length;

    /// <summary>
    /// The position of the stream.
    /// </summary>
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

    /// <summary>
    /// Changes the length of the stream. The capacity of the stream might also change if the value is lesser than the
    /// current capacity.
    /// </summary>
    /// <param name="value">The new length</param>
    override
    public void SetLength(long value)
    {
        checkAndResize(value);
        _capacity = value;
    }

    /// <summary>
    /// Read a sequence of bytes into the provided buffer.
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to read</param>
    /// <returns></returns>
    override
    public int Read(byte[] buffer, int offset, int count)
    {
        int bytesRead = (int)Math.Min(_length - Position, count);
        Array.Copy(_bytes, (int)_position, buffer, offset, bytesRead);
        Position = Position + bytesRead;
        if (bytesRead <= 0)
            return 0;
        return bytesRead;
    }

    /// <summary>
    /// Write to the stream
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to write</param>
    override
    public void Write(byte[] buffer, int offset, int count)
    {
        checkAndResize(_position + count);
        Array.Copy(buffer, offset, _bytes, (int)_position, count);
        Position = Position + count;
    }

    private void checkAndResize(long newLength)
    {
        if (this._capacity < newLength)
        {
            long newCapacity = newLength * 2;
            if (newCapacity > int.MaxValue)
                throw new Exception("Size too large");
            byte[] nBytes = new byte[(int)newCapacity];
            for (int i = 0; i < this._capacity; i++)
                nBytes[i] = this._bytes[i];
            this._capacity = newCapacity;
            this._bytes = nBytes;
        }
        this._length = newLength;
    }

    /// <summary>
    /// Seek to a position.
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The type of seek</param>
    /// <returns></returns>
    override
    public long Seek(long offset, SeekOrigin origin)
    {
        long nPos = 0;
        if (origin == SeekOrigin.Begin)
        {
            nPos = (int)offset;
        }
        else if (origin == SeekOrigin.Current)
        {
            nPos = Position + offset;
        }
        else if (origin == SeekOrigin.End)
        {
            nPos = (int)(_bytes.Length - offset);
        }
        if (nPos > this._length)
            checkAndResize(nPos);
        Position = nPos;
        return Position;
    }

    /// <summary>
    /// Flush the stream.
    /// </summary>
    override
    public void Flush()
    {
        // nop
    }

    /// <summary>
    /// Close any resources the stream is using.
    /// </summary>
    override
    public void Close()
    {
        // nop
    }

    /// <summary>
    /// Convert the stream to an array.
    /// </summary>
    /// <returns>A byte array</returns>
    public byte[] ToArray()
    {
        byte[] nBytes = new byte[(int)_length];
        Array.Copy(this._bytes, 0, nBytes, 0, (int)_length);
        return nBytes;
    }
}
