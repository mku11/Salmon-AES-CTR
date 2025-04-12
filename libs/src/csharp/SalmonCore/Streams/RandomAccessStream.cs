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
/// Random access stream for seeking, reading, and writing to a memory buffer
/// </summary>
public abstract class RandomAccessStream
{
    /// <summary>
    /// Default buffer size
    /// </summary>
    public static readonly int DEFAULT_BUFFER_SIZE = 256 * 1024;

    /// <summary>
    /// Check if the stream is readable.
    /// </summary>
    public abstract bool CanRead { get; }

    /// <summary>
    /// Check if the stream is writable
    /// </summary>
    public abstract bool CanWrite { get; }

    /// <summary>
    /// Check if the stream is seekable
    /// </summary>
    public abstract bool CanSeek { get; }

    /// <summary>
    /// The length of the stream
    /// </summary>
    public abstract long Length { get; }

    /// <summary>
    /// The position of the stream
    /// </summary>
    public abstract long Position { get; set; }

    /// <summary>
    /// The preferred align size
    /// </summary>
    public virtual int AlignSize => 32768;

    /// <summary>
    /// Close the stream
    /// </summary>
    public abstract void Close();

    /// <summary>
    /// Flush the stream
    /// </summary>
    public abstract void Flush();

    /// <summary>
    /// Seek to a position
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The type of seek</param>
    /// <returns></returns>
    public abstract long Seek(long offset, SeekOrigin origin);

    /// <summary>
    /// Set the length of the stream
    /// </summary>
    /// <param name="value"></param>
    public abstract void SetLength(long value);

    /// <summary>
    /// Read from the stream
    /// </summary>
    /// <param name="buffer">The buffer to read into</param>
    /// <param name="offset">THe offset</param>
    /// <param name="count"></param>
    /// <returns></returns>
    public abstract int Read(byte[] buffer, int offset, int count);

    /// <summary>
    /// Write to the stream
    /// </summary>
    /// <param name="buffer">The buffer to write</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes</param>
    public abstract void Write(byte[] buffer, int offset, int count);


    /// <summary>
    ///  Write stream contents to another stream.
	/// </summary>
	///  <param name="destStream">The target stream.</param>
    public void CopyTo(RandomAccessStream destStream)
    {
        CopyTo(destStream, 0, null);
    }

    /// <summary>
    ///  Write stream contents to another stream.
	/// </summary>
	///  <param name="destStream">The target stream.</param>
    ///  <param name="progressListener">The listener to notify when progress changes.</param>
    public void CopyTo(RandomAccessStream destStream, Action<long, long> progressListener = null)
    {
        CopyTo(destStream, 0, progressListener);
    }

    /// <summary>
    ///  Write stream contents to another stream.
    /// </summary>
    ///  <param name="destStream">The target stream.</param>
    ///  <param name="bufferSize">The buffer size to be used when copying.</param>
    ///  <param name="progressListener">The listener to notify when progress changes.</param>
    public void CopyTo(RandomAccessStream destStream, int bufferSize = 0, Action<long, long> progressListener = null)
    {
        if (!this.CanRead)
            throw new IOException("Target stream not readable");
        if (!destStream.CanWrite)
            throw new IOException("Target stream not writable");
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        bufferSize = bufferSize / AlignSize * AlignSize;
        int bytesRead;
        long pos = this.Position;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = this.Read(buffer, 0, bufferSize)) > 0)
        {
            destStream.Write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener(this.Position, this.Length);
        }
        destStream.Flush();
    }

    /// <summary>
    /// Get a native stream for reading
    /// </summary>
    /// <returns></returns>
    public virtual Stream AsReadStream()
    {
        return new InputStreamWrapper(this);
    }
}