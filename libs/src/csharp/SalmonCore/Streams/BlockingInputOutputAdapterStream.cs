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
using System.IO.Pipes;
using System.Threading;

namespace Mku.Streams;

/// <summary>
/// Adapter stream pipes a write stream to a read stream. 
/// The InputStream.Read() method will block until 
/// there are data written with BlockingInputOutputAdapterStream.Write().
/// </summary>
public class BlockingInputOutputAdapterStream : Stream
{
    internal AnonymousPipeServerStream PipeServer { get; set; }
    internal AnonymousPipeClientStream PipeClient { get; set; }

    /// <summary>
    /// Stream to read. Use with 3rd party libraries.
    /// </summary>
    public BackingInputStream InputStream { get; internal set; }

    /// <summary>
    /// Check if stream can read.
    /// </summary>

    public override bool CanRead => PipeServer.CanRead;


    /// <summary>
    /// Check if stream can seek.
    /// </summary>

    public override bool CanSeek => PipeServer.CanSeek;

    /// <summary>
    /// Check if stream can write
    /// </summary>

    public override bool CanWrite => PipeServer.CanWrite;

    /// <summary>
    /// Length of the stream.
    /// </summary>

    public override long Length => PipeServer.Length;

    /// <summary>
    /// Position of the stream.
    /// </summary>

    public override long Position
    {
        get
        {
            return position;
        }
        set
        {
            position = value;
        }
    }

    private long position;
    private bool received;
    private readonly object readLock = new object();
    private readonly object receivedLock = new object();

    /// <summary>
    /// Construct an adapter stream.
    /// </summary>
    public BlockingInputOutputAdapterStream()
    {
        PipeServer = new AnonymousPipeServerStream();
        PipeClient = new AnonymousPipeClientStream(PipeServer.GetClientHandleAsString());
        InputStream = new BackingInputStream(this);
    }

    /// <summary>
    /// Write to stream. This will block until the InputStream (back stream) will read the data.
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to write</param>
    public override void Write(byte[] buffer, int offset, int count)
    {
        PipeServer.Write(buffer, offset, count);
        position += count;
        WaitRead();
    }

    /// <summary>
    /// Flush the stream. Not supported
    /// </summary>
    public override void Flush()
    {
        PipeServer.Flush();
        WaitRead();
    }

    /// <summary>
    /// Read from the stream. Not supported.
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to read</param>
    /// <returns>The number of bytes Read</returns>
    /// <exception cref="NotImplementedException"></exception>
    public override int Read(byte[] buffer, int offset, int count)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Seek to position.
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The type of seek</param>
    /// <returns></returns>
    public override long Seek(long offset, SeekOrigin origin)
    {
        long newPosition = 0;
        if (origin == SeekOrigin.Begin)
            newPosition = offset;
        else if (origin == SeekOrigin.Current)
            newPosition = Position + offset;
        else if (origin == SeekOrigin.End)
            newPosition = Length - offset;

        Position = newPosition;
        return Position;
    }

    /// <summary>
    /// Set the length of the stream.
    /// </summary>
    /// <param name="value">The new length</param>
    public override void SetLength(long value)
    {
        PipeServer.SetLength(value);
    }

    /// <summary>
    /// Close the stream.
    /// </summary>
    public override void Close()
    {
        PipeServer.Close();
        WaitReceived();
    }


    private void WaitRead()
    {
        lock (readLock)
        {
            while (InputStream.GetPosition() != position)
            {
                Monitor.Wait(readLock);
            }
        }
    }

    private void WaitReceived()
    {
        lock (receivedLock)
        {
            while (!received)
            {
                Monitor.Wait(receivedLock);
            }
        }
    }

    /// <summary>
    /// Set to notify the end of receive.
    /// </summary>
    /// <param name="value">True to set the end</param>
    public void SetReceived(bool value)
    {
        received = value;
        lock (receivedLock)
        {
            Monitor.Pulse(receivedLock);
        }
    }

    /// <summary>
    /// Input stream to be used by 3rd party libraries to read the data.
    /// See: BlockingInputOutputAdapterStream.
    /// </summary>
    public class BackingInputStream : Stream
    {
        BlockingInputOutputAdapterStream parent;
        // PipeStream is throwing: Stream does not support seeking.
        // so we keep track of the position internally
        private long position = 0;
        internal long GetPosition()
        {
            return position;
        }

        internal BackingInputStream(BlockingInputOutputAdapterStream parent)
        {
            this.parent = parent;
        }

        /// <summary>
        /// Check if stream can read
        /// </summary>
        public override bool CanRead => parent.PipeClient.CanRead;

        /// <summary>
        /// Check if stream can seek
        /// </summary>
        public override bool CanSeek => parent.PipeServer.CanSeek;

        /// <summary>
        /// Check if stream can write
        /// </summary>

        public override bool CanWrite => parent.PipeClient.CanWrite;

        /// <summary>
        /// Get the length.
        /// </summary>
        public override long Length => parent.PipeClient.Length;

        /// <summary>
        /// Get current position.
        /// </summary>
        public override long Position
        {
            get
            {
                return parent.PipeClient.Position;
            }
            set
            {
                parent.PipeClient.Position = value;
            }
        }

        /// <summary>
        /// Flush the stream.
        /// </summary>
        /// <exception cref="NotSupportedException"></exception>
        public override void Flush()
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Read from stream
        /// </summary>
        /// <param name="buffer">The data buffer to read into</param>
        /// <param name="offset">The data offset to use</param>
        /// <param name="count">The nubmer of bytes to read</param>
        /// <returns></returns>
        public override int Read(byte[] buffer, int offset, int count)
        {
            int res = parent.PipeClient.Read(buffer, offset, count);
            position += res;
            lock (parent.readLock)
            {
                Monitor.Pulse(parent.readLock);
            }
            return res;
        }

        /// <summary>
        /// Seek to position
        /// </summary>
        /// <param name="offset">The offset</param>
        /// <param name="origin">The type of seek</param>
        /// <returns></returns>
        public override long Seek(long offset, SeekOrigin origin)
        {
            return parent.PipeClient.Seek(offset, origin);
        }

        /// <summary>
        /// Set the length.
        /// </summary>
        /// <param name="value">The new length</param>
        /// <exception cref="NotSupportedException"></exception>
        public override void SetLength(long value)
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Write to the stream
        /// </summary>
        /// <param name="buffer">The buffer</param>
        /// <param name="offset">The offset</param>
        /// <param name="count">The number of bytes to write</param>
        /// <exception cref="NotSupportedException"></exception>
        public override void Write(byte[] buffer, int offset, int count)
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Close the stream.
        /// </summary>
        public override void Close()
        {
            parent.PipeClient.Close();
        }
    }
}