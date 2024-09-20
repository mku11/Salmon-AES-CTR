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

/**
 * Redirection output stream for read/write streams. 
 * Write() will write to temporary buffer and block until the backing stream reads.
 * This works as an adapter.
 */
public class BlockingInputOutputAdapterStream : Stream
{
    internal AnonymousPipeServerStream PipeServer { get; set; }
    internal AnonymousPipeClientStream PipeClient { get; set; }
    public BackingInputStream InputStream { get; internal set; }

    public override bool CanRead => PipeServer.CanRead;

    public override bool CanSeek => PipeServer.CanSeek;

    public override bool CanWrite => PipeServer.CanWrite;

    public override long Length => PipeServer.Length;

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

    public BlockingInputOutputAdapterStream()
    {
        PipeServer = new AnonymousPipeServerStream();
        PipeClient = new AnonymousPipeClientStream(PipeServer.GetClientHandleAsString());
        InputStream = new BackingInputStream(this);
    }

    public override void Write(byte[] buffer, int offset, int count)
    {
        PipeServer.Write(buffer, offset, count);
        position += count;
        PipeServer.WaitForPipeDrain();
        WaitRead();
    }

    public override void Flush()
    {
        PipeServer.Flush();
        PipeServer.WaitForPipeDrain();
        WaitRead();
    }

    public override int Read(byte[] buffer, int offset, int count)
    {
        throw new NotImplementedException();
    }

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

    public override void SetLength(long value)
    {
        PipeServer.SetLength(value);
    }

    public override void Close()
    {
        PipeServer.WaitForPipeDrain();
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

    public void SetReceived(bool value)
    {
        received = value;
        lock (receivedLock)
        {
            Monitor.Pulse(receivedLock);
        }
    }

    public class BackingInputStream : Stream
    {
        BlockingInputOutputAdapterStream parent;
        // PipeStream is throwing: Stream does not support seeking.
        // so we keep track of the position
        private long position = 0;
        public long GetPosition()
        {
            return position;
        }

        internal BackingInputStream(BlockingInputOutputAdapterStream parent)
        {
            this.parent = parent;
        }
        public override bool CanRead => parent.PipeClient.CanRead;

        public override bool CanSeek => parent.PipeServer.CanSeek;

        public override bool CanWrite => parent.PipeClient.CanWrite;

        public override long Length => parent.PipeClient.Length;

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

        public override void Flush()
        {
            throw new NotSupportedException();
        }

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

        public override long Seek(long offset, SeekOrigin origin)
        {
            return parent.PipeClient.Seek(offset, origin);
        }

        public override void SetLength(long value)
        {
            throw new NotSupportedException();
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            throw new NotSupportedException();
        }

        public override void Close()
        {
            parent.PipeClient.Close();
        }
    }
}