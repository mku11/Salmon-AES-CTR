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

using Mku.Salmon.Streams;
using Mku.SalmonFS.File;
using System;
using System.Collections.Generic;
using System.IO;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;

namespace Mku.SalmonFS.Streams;

/// <summary>
///  Implementation of a C# InputStream for seeking and reading a SalmonFile.
///  This class provides a seekable source with parallel substreams and cached buffers
///  for performance.
/// </summary>
public class SalmonFileInputStream : Stream
{
    // Default cache buffer should be high enough for some mpeg videos to work
    // the cache buffers should be aligned to the SalmonFile chunk size for efficiency
    private static readonly int DEFAULT_BUFFER_SIZE = 512 * 1024;

    // default threads is one but you can increase it
    private static readonly int DEFAULT_THREADS = 1;

    private static readonly int DEFAULT_BUFFERS = 3;

    private static readonly int MAX_BUFFERS = 6;

    private readonly int buffersCount;
    private CacheBuffer[] buffers = null;
    private SalmonStream[] streams;
    private readonly SalmonFile salmonFile;
    private readonly int cacheBufferSize;
    private readonly int threads;
    private long position;
    private long size;

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
    private readonly int backOffset;

    /// <summary>
    ///  Instantiate a seekable stream from an encrypted file source
	/// </summary>
	///  <param name="salmonFile">  The source file.</param>
    ///  <param name="buffersCount">Number of buffers to use.</param>
    ///  <param name="bufferSize">  The length of each buffer.</param>
    ///  <param name="threads">     The number of threads/streams to source the file in parallel.</param>
    ///  <param name="backOffset">  The back offset to use</param>
    public SalmonFileInputStream(SalmonFile salmonFile,
                                 int buffersCount, int bufferSize, int threads, int backOffset)
    {

        this.salmonFile = salmonFile;
        this.size = salmonFile.Size;
        if (buffersCount == 0)
            buffersCount = DEFAULT_BUFFERS;
        if (buffersCount > MAX_BUFFERS)
            buffersCount = MAX_BUFFERS;
        if (bufferSize == 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (backOffset > 0)
            bufferSize += backOffset;
        if (threads == 0)
            threads = DEFAULT_THREADS;

        this.buffersCount = buffersCount;
        this.cacheBufferSize = bufferSize;
        this.threads = threads;
        this.backOffset = backOffset;
        this.PositionStart = 0;
        this.PositionEnd = size - 1;

        CreateBuffers();
        CreateStreams();
    }

    /// <summary>
    ///  Method creates the parallel streams for reading from the file
    /// </summary>
    private void CreateStreams()
    {
        streams = new SalmonStream[threads];
        try
        {
            for (int i = 0; i < threads; i++)
            {
                streams[i] = salmonFile.GetInputStream();
            }
        }
        catch (Exception ex)
        {
            throw new IOException("Could not create streams", ex);
        }
    }

    /// <summary>
    ///  Create cache buffers that will be used for sourcing the files.
    ///  These will help reducing multiple small decryption reads from the encrypted source.
    ///  The first buffer will be sourcing at the start of the encrypted file where the header and indexing are
    ///  The rest of the buffers can be placed to whatever position the user slides to
    /// </summary>
    private void CreateBuffers()
    {
        buffers = new CacheBuffer[buffersCount];
        for (int i = 0; i < buffers.Length; i++)
            buffers[i] = new CacheBuffer(cacheBufferSize);
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
        if (position >= PositionEnd + 1)
            return 0;

        int minCount;
        int bytesRead;

        // truncate the count so getCacheBuffer() reports the correct buffer
        count = (int)Math.Min(count, size - position);

        CacheBuffer cacheBuffer = GetCacheBuffer(position, count);
        if (cacheBuffer == null)
        {
            cacheBuffer = GetAvailCacheBuffer();
            // the stream is closed
            if (cacheBuffer == null)
                return 0;
            // for some applications like media players they make a second immediate request
            // in a position a few bytes before the first request. To make
            // sure we don't make 2 overlapping requests we start the buffer
            // a position ahead of the first request.
            long startPosition = position - backOffset;
            if (startPosition < 0)
                startPosition = 0;

            bytesRead = FillBuffer(cacheBuffer, startPosition, cacheBufferSize);

            if (bytesRead <= 0)
                return bytesRead;
            cacheBuffer.startPos = startPosition;
            cacheBuffer.count = bytesRead;
        }
        minCount = Math.Min(count, (int)(cacheBuffer.count - position + cacheBuffer.startPos));
        Array.Copy(cacheBuffer.buffer, (int)(position - cacheBuffer.startPos), buffer, offset, minCount);

        position += minCount;
        return minCount;
    }

    /// <summary>
    ///  Fills a cache buffer with the decrypted data from the encrypted source file.
	/// </summary>
	///  <param name="cacheBuffer">The cache buffer that will store the decrypted contents</param>
    ///  <param name="startPosition"> The start position of the data requested</param>
    ///  <param name="bufferSize"> The length of the data requested</param>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private int FillBuffer(CacheBuffer cacheBuffer, long startPosition, int bufferSize)
    {
        int bytesRead;
        if (threads == 1)
        {
            bytesRead = FillBufferPart(cacheBuffer, startPosition, 0, bufferSize, streams[0]);
        }
        else
        {
            bytesRead = FillBufferMulti(cacheBuffer, startPosition, bufferSize);
        }
        return bytesRead;
    }

    /// <summary>
    ///  Fills a cache buffer with the decrypted data from a part of an encrypted file served as a salmon stream
    /// </summary>
    ///  <param name="cacheBuffer"> The cache buffer that will store the decrypted contents</param>
    ///  <param name="start"> The start position to start reading from</param>
    ///  <param name="offset"> The offset position for the buffer</param>
    ///  <param name="bufferSize">  The length of the data requested</param>
    ///  <param name="salmonStream">The stream that will be used to read from</param>
    private int FillBufferPart(CacheBuffer cacheBuffer, long start, int offset, int bufferSize,
                               SalmonStream salmonStream)
    {
        salmonStream.Seek(start, SeekOrigin.Begin);
        int totalBytesRead = salmonStream.Read(cacheBuffer.buffer, offset, bufferSize);
        return totalBytesRead;
    }

    /// <summary>
    ///  Fill the buffer using parallel streams for performance
	/// </summary>
	///  <param name="cacheBuffer">  The cache buffer that will store the decrypted data</param>
    ///  <param name="startPosition">The source file position the read will start from</param>
    ///  <param name="bufferSize">   The buffer size that will be used to read from the file</param>
    private int FillBufferMulti(CacheBuffer cacheBuffer, long startPosition, int bufferSize)
    {
        int bytesRead = 0;
        IOException ex = null;
        Task[] tasks = new Task[threads];
        // Multithreaded decryption jobs
        int partSize = (int)Math.Ceiling(bufferSize / (float)threads);
        for (int i = 0; i < threads; i++)
        {
            int index = i;
            tasks[i] = Task.Run(() =>
            {
                int start = partSize * index;
                int length;
                if (index == threads - 1)
                    length = bufferSize - start;
                else
                    length = partSize;
                try
                {
                    int chunkBytesRead = FillBufferPart(cacheBuffer, startPosition + start, start, length,
                            streams[index]);
                    if (chunkBytesRead >= 0)
                        bytesRead += chunkBytesRead;
                }
                catch (IOException ex1)
                {
                    ex = ex1;
                }
                catch (Exception ex2)
                {
                    Console.Error.WriteLine(ex2);
                }
            });
        }
        Task.WaitAll(tasks);

        if (ex != null)
        {
            throw ex;
        }
        return bytesRead;
    }

    /// <summary>
    ///  Returns an available cache buffer if there is none then reuse the least recently used one.
    /// </summary>
    [MethodImpl(MethodImplOptions.Synchronized)]
    private CacheBuffer GetAvailCacheBuffer()
    {
        if (lruBuffersIndex.Count == buffersCount)
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
            CacheBuffer buffer = buffers[i];
            if (buffer != null && buffer.count == 0)
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
    private CacheBuffer GetCacheBuffer(long position, int count)
    {
        for (int i = 0; i < buffers.Length; i++)
        {
            CacheBuffer buffer = buffers[i];
            if (buffer != null && position >= buffer.startPos && position + count <= buffer.startPos + buffer.count)
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
        get => position - PositionStart;
        set
        {
            value += PositionStart;
            if (value > this.size)
                this.position = this.size;
            else
                this.position = value;
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
        CloseStreams();
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
    private void CloseStreams()
    {
        for (int i = 0; i < threads; i++)
        {
            if (streams[i] != null)
                streams[i].Close();
            streams[i] = null;
        }
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

    /// <summary>
    ///  Class will be used to cache decrypted data that can later be read via the ReadAt() method
    ///  without requesting frequent decryption reads.
	/// </summary>
	//TODO: replace the CacheBuffer with a MemoryStream to simplify the code
    public class CacheBuffer
    {
        /// <summary>
        /// The buffer
        /// </summary>
        public byte[] buffer;

        /// <summary>
        /// The starting position
        /// </summary>
        public long startPos = 0;

        /// <summary>
        ///  The count of bytes used
        /// </summary>
        public long count = 0;

        /// <summary>
        ///  Instantiate a cache buffer.
        /// </summary>
        ///  <param name="bufferSize">The buffer size</param>
        public CacheBuffer(int bufferSize)
        {
            buffer = new byte[bufferSize];
        }

        /// <summary>
        ///  Clear the buffer.
        /// </summary>
        public void Clear()
        {
            if (buffer != null)
                Array.Fill<byte>(buffer, 0, buffer.Length, (byte)0);
        }
    }
}

