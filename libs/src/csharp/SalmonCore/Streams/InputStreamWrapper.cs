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

using System.Collections.Generic;
using System;
using System.IO;
using System.Runtime.CompilerServices;
namespace Mku.Streams;

/// <summary>
/// InputStream wrapper for RandomAccessStream.
/// Use this class to wrap any RandomAccessStream to a .NET Stream to use with 3rd party libraries.
/// </summary>
public class InputStreamWrapper : Stream
{
    /// <summary>
    /// Default cache buffer should be high enough for some mpeg videos to work 
    /// the cache buffers should be aligned to the AesFile chunk size for efficiency
    /// </summary>
    public static readonly int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /// <summary>
    /// The default buffer count
    /// </summary>
    public static readonly int DEFAULT_BUFFERS = 1;

    /// <summary>
    /// The default backwards buffer offset
    /// </summary>
    public static readonly int DEFAULT_BACK_OFFSET = 32768;

    /// <summary>
    /// The maximum allowed buffer count
    /// </summary>
    protected static readonly int MAX_BUFFERS = 6;

    /// <summary>
    /// Number of buffers
    /// </summary>
    protected int BuffersCount { get; set; }
    private Buffer[] buffers = null;

    /// <summary>
    /// The stream
    /// </summary>
    private RandomAccessStream stream;

    /// <summary>
    /// The bufferSize
    /// </summary>
    public int BufferSize { get; protected set; }

    /// <summary>
    /// The actual stream position.
    /// </summary>
    protected long StreamPosition { get; set; }

    /// <summary>
    /// The total size of the base stream
    /// </summary>
    protected long TotalSize { get; set; }

    /// <summary>
    /// The align size
    /// </summary>
    public int AlignSize { get; protected set; }

    /// <summary>
    ///  We reuse the least recently used buffer. Since the buffer count is relative
    ///  small (see <see cref="MAX_BUFFERS"/>) there is no need for a fast-access lru queue
    ///  so a simple linked list of keeping the indexes is adequately fast.
    /// </summary>
    private readonly LinkedList<int> lruBuffersIndex = new LinkedList<int>();

    /// <summary>
    ///  Negative offset for the buffers. Some stream consumers might request data right before
    ///  the last request. We provide this offset so we don't make multiple requests for filling
    ///  the buffers ending up with too much overlapping data.
    /// </summary>
    public int BackOffset { get; protected set; }

    /// <summary>
    ///  Instantiate a seekable stream from an encrypted file source
	/// </summary>
	///  <param name="stream">  The stream.</param>
    ///  <param name="buffersCount">Number of buffers to use.</param>
    ///  <param name="bufferSize">  The length of each buffer.</param>
    ///  <param name="backOffset">  The back offset to use</param>
    ///  <param name="alignSize">  The align size</param>
    public InputStreamWrapper(RandomAccessStream stream, int buffersCount = 1, int bufferSize = 0, int backOffset = 32768, int alignSize = 0)
    {
        this.stream = stream;
        if (stream != null)
            this.TotalSize = stream.Length;
        if (buffersCount <= 0)
            buffersCount = DEFAULT_BUFFERS;
        if (buffersCount > MAX_BUFFERS)
            buffersCount = MAX_BUFFERS;
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (backOffset < 0)
            backOffset = DEFAULT_BACK_OFFSET;
		
		if(alignSize <= 0 && stream != null)
            alignSize = stream.AlignSize;
		
        // align the buffers for performance
        if (alignSize > 0)
        {
            if (backOffset > 0)
            {
                int nBackOffset = backOffset / alignSize * alignSize;
                if (nBackOffset < backOffset)
                    nBackOffset += alignSize;
                backOffset = nBackOffset;
            }

            int nBufferSize = bufferSize / alignSize * alignSize;
            if (nBufferSize < alignSize)
            {
                nBufferSize = alignSize;
            }
            if (nBufferSize < bufferSize)
            {
                nBufferSize += alignSize;
            }
            bufferSize = nBufferSize;
        }

        if (backOffset > 0)
        {
            bufferSize += backOffset;
            // we use a minimum 2 buffers since it is very likely
            // that the previous buffer in use will have the backoffset 
            // data of the new one
            if(buffersCount == 1)
                buffersCount = 2;
        }

        this.BuffersCount = buffersCount;
        this.BufferSize = bufferSize;
        this.BackOffset = backOffset;
        this.PositionStart = 0;
        this.PositionEnd = TotalSize - 1;
        this.AlignSize = alignSize;

        CreateBuffers();
    }

    /// <summary>
    ///  Create cache buffers that will be used for sourcing the files.
    ///  These will help reducing multiple small decryption reads from the encrypted source.
    ///  The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
    ///  The rest of the buffers can be placed to whatever position the user slides to
    /// </summary>
    protected virtual void CreateBuffers()
    {
        buffers = new Buffer[BuffersCount];
        for (int i = 0; i < buffers.Length; i++)
            buffers[i] = new Buffer(BufferSize);
    }

    /// <summary>
    ///  Reads and decrypts the contents of an encrypted file
	/// </summary>
	///  <param name="buffer">The buffer that will store the decrypted contents</param>
    ///  <param name="offset">The position on the buffer that the decrypted data will start</param>
    ///  <param name="count"> The length of the data requested</param>
    override
    public int Read(byte[] buffer, int offset, int count)
    {
        if (StreamPosition >= PositionEnd + 1)
            return 0;

        int minCount;
        int bytesRead;

        // truncate the count so getBuffer() reports the correct buffer
        count = (int)Math.Min(count, TotalSize - StreamPosition);

        Buffer cacheBuffer = GetBuffer(StreamPosition, count);
        if (cacheBuffer == null)
        {
            cacheBuffer = GetAvailBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return 0;

            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            long startPosition = StreamPosition;
            if(AlignSize > 0)
            {
                startPosition = startPosition / AlignSize * AlignSize;
            }

            int length = BufferSize;

            // if we have the backoffset data in an existing buffer we don't include the backoffset
            // in the new request because we want to prevent network streams resetting.
            if (startPosition > 0 && !HasBackoffset(startPosition))
            {
				startPosition -= BackOffset;
            } else
            {
                length -= BackOffset;
            }

            bytesRead = FillBuffer(cacheBuffer, startPosition, length);

            if (bytesRead <= 0)
                return bytesRead;
            cacheBuffer.StartPos = startPosition;
            cacheBuffer.Count = bytesRead;
        }
		
		// align the count also
        long end = StreamPosition + count;
        int nCount = (int) (end / AlignSize * AlignSize - StreamPosition);
        if (nCount > 0 && nCount < count) {
            count = nCount;
		}
		
        minCount = Math.Min(count, (int)(cacheBuffer.Count - StreamPosition + cacheBuffer.StartPos));
        if(minCount < count)
        {
            throw new Exception("Buffers are not large enough, if you use a backoffset make sure you double the buffer size");
        }
        Array.Copy(cacheBuffer.Data, (int)(StreamPosition - cacheBuffer.StartPos), buffer, offset, minCount);

        StreamPosition += minCount;
        return minCount;
    }

    private bool HasBackoffset(long startPosition)
    {
        long pos = startPosition - BackOffset;
        for (int i = 0; i < buffers.Length; i++)
        {
            Buffer buffer = buffers[i];
            if (buffer != null && buffer.Count > 0 
                && buffer.StartPos <= pos
                && startPosition <= buffer.StartPos + buffer.Count)
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>
    ///  Fills a cache buffer with the decrypted data from the encrypted source file.
    /// </summary>
    ///  <param name="cacheBuffer">The cache buffer that will store the decrypted contents</param>
    ///  <param name="startPosition"> The start position of the data requested</param>
    ///  <param name="length"> The length of the data requested</param>
    [MethodImpl(MethodImplOptions.Synchronized)]
    protected virtual int FillBuffer(Buffer cacheBuffer, long startPosition, int length)
    {
        int bytesRead = FillBufferPart(cacheBuffer, startPosition, 0, length, stream);
        return bytesRead;
    }

    /// <summary>
    ///  Fills a cache buffer with the decrypted data from a part of an encrypted file.
    /// </summary>
    ///  <param name="cacheBuffer"> The cache buffer that will store the decrypted contents</param>
    ///  <param name="start"> The start position to start reading from</param>
    ///  <param name="offset"> The offset position for the buffer</param>
    ///  <param name="length">  The length of the data requested</param>
    ///  <param name="salmonStream">The stream that will be used to read from</param>
    protected int FillBufferPart(Buffer cacheBuffer, long start, int offset, int length, RandomAccessStream salmonStream)
    {
        salmonStream.Seek(start, SeekOrigin.Begin);
        int bytesRead;
        int totalBytesRead = 0;
        while ((bytesRead = salmonStream.Read(cacheBuffer.Data, offset + totalBytesRead, length - totalBytesRead)) > 0)
        {
            totalBytesRead += bytesRead;
        }
        return totalBytesRead;
    }

    /// <summary>
    ///  Returns an available cache buffer if there is none then reuse the least recently used one.
    /// </summary>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private Buffer GetAvailBuffer()
    {
        if (lruBuffersIndex.Count == BuffersCount)
        {
            // getting least recently used buffer
            int index = lruBuffersIndex.Last.Value;
            // promote to the top
            lruBuffersIndex.Remove(index);
            lruBuffersIndex.AddFirst(index);
            return buffers[lruBuffersIndex.Last.Value];
        }
        for (int i = 0; i < buffers.Length; i++)
        {
            Buffer buffer = buffers[i];
            if (buffer != null && buffer.Count == 0)
            {
                lruBuffersIndex.AddFirst(i);
                return buffer;
            }
        }
        if (buffers[buffers.Length - 1] != null)
            return buffers[buffers.Length - 1];
        else
            return null;
    }

    /// <summary>
    ///  Returns the buffer that contains the data requested.
	/// </summary>
	///  <param name="position">The source file position of the data to be read</param>
    ///  <param name="count">The number of bytes to be read</param>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private Buffer GetBuffer(long position, int count)
    {
        for (int i = 0; i < buffers.Length; i++)
        {
            Buffer buffer = buffers[i];
            if (buffer != null && position >= buffer.StartPos && position + count <= buffer.StartPos + buffer.Count)
            {
                // promote buffer to the front
                lruBuffersIndex.Remove(i);
                lruBuffersIndex.AddFirst(i);
                return buffer;
            }
        }
        return null;
    }

    /// <summary>
    ///  Get the size of the stream.
	/// </summary>
	///  <returns>The length</returns>
    override
    public long Length
    {
        get
        {
            return PositionEnd - PositionStart + 1;
        }
    }

    /// <summary>
    /// True if stream can read
    /// </summary>
    public override bool CanRead => true;

    /// <summary>
    /// True if stream can seek
    /// </summary>
    public override bool CanSeek => true;


    /// <summary>
    /// True if stream can write
    /// </summary>
    public override bool CanWrite => false;

    /// <summary>
    /// Current position of the stream
    /// </summary>
    public override long Position
    {
        get => StreamPosition - PositionStart;
        set
        {
            value += PositionStart;
            if (value > this.TotalSize)
                this.StreamPosition = this.TotalSize;
            else
                this.StreamPosition = value;
        }
    }

    /// <summary>
    /// Position start for libaries that require partial content
    /// </summary>
    public long PositionStart
    {
        [MethodImpl(MethodImplOptions.Synchronized)]
        get;
        [MethodImpl(MethodImplOptions.Synchronized)]
        set;
    }

    /// <summary>
    /// Position end for libaries that require partial content
    /// </summary>
    public long PositionEnd
    {
        [MethodImpl(MethodImplOptions.Synchronized)]
        get;
        [MethodImpl(MethodImplOptions.Synchronized)]
        set;
    }

    /// <summary>
    ///  Close the stream and associated backed streams and clear buffers.
	/// </summary>
	///  <exception cref="IOException">Thrown if error during IO</exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    override
    public void Close()
    {
        CloseStream();
        ClearBuffers();
    }

    /// <summary>
    ///  Clear all buffers.
    /// </summary>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private void ClearBuffers()
    {
        for (int i = 0; i < buffers.Length; i++)
        {
            if (buffers[i] != null)
                buffers[i].Clear();
            buffers[i] = null;
        }
    }

    /// <summary>
    ///  Close all back streams.
    /// </summary>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private void CloseStream()
    {
        if (stream != null)
            stream.Close();
        stream = null;
    }

    /// <summary>
    /// Flush buffer to stream, not used
    /// </summary>
    public override void Flush()
    {

    }

    /// <summary>
    /// Seek to specific position in the stream
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The origin</param>
    /// <returns>The new position after seeking</returns>
    public override long Seek(long offset, SeekOrigin origin)
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
            nPos = (int)(Length - offset);
        }
        Position = nPos;
        return Position;
    }

    /// <summary>
    /// Set the stream length, not used
    /// </summary>
    /// <param name="value">The new length</param>
    /// <exception cref="NotSupportedException">Thrown if operation not supported</exception>
    public override void SetLength(long value)
    {
        throw new NotSupportedException();
    }

    /// <summary>
    /// Write to stream, not used
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The buffer offset</param>
    /// <param name="count">The count of bytes to write</param>
    /// <exception cref="NotSupportedException">Thrown if operation not supported</exception>
    public override void Write(byte[] buffer, int offset, int count)
    {
        throw new NotSupportedException();
    }
}
