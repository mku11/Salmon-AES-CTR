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
using Mku.Streams;
namespace Mku.FS.Streams;

/// <summary>
/// A File Stream implementation.
/// This class is used internally for random file access of remote physical (real) files.
/// </summary>
public class FileStream : RandomAccessStream
{
    private readonly System.IO.Stream stream;
    private File.File file;

    /// <summary>
    /// Construct a file stream
    /// </summary>
    /// <param name="file">The file</param>
    /// <param name="access">The access</param>
    public FileStream(File.File file, FileAccess access)
    {
        this.file = file;
        if (access == FileAccess.Read)
        {
            stream = System.IO.File.Open(file.Path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
        }
        else if (access == FileAccess.Write)
        {
            stream = System.IO.File.Open(file.Path, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite);
        }
    }

    /// <summary>
    /// Check if stream can read
    /// </summary>
    public override bool CanRead => stream.CanRead;

    /// <summary>
    /// Check if stream can seek
    /// </summary>
    public override bool CanSeek => stream.CanSeek;

    /// <summary>
    /// Check if stream can write
    /// </summary>
    public override bool CanWrite => stream.CanWrite;

    /// <summary>
    /// The length of the stream
    /// </summary>
    public override long Length => stream.Length;

    /// <summary>
    /// The position of the stream
    /// </summary>
    public override long Position
    {
        get => stream.Position;
        set
        {
            stream.Position = value;
        }
    }

    /// <summary>
    /// Close the stream
    /// </summary>
    public override void Close()
    {
        stream.Close();
    }

    /// <summary>
    /// Flush the stream
    /// </summary>
    public override void Flush()
    {
        stream.Flush();
    }

    /// <summary>
    /// Read from the stream
    /// </summary>
    /// <param name="buffer">The buffer to read into</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">THe number of bytes to read</param>
    /// <returns></returns>
    public override int Read(byte[] buffer, int offset, int count)
    {
        return stream.Read(buffer, offset, count);
    }

    /// <summary>
    /// Seek to a position
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The type of seek</param>
    /// <returns></returns>
    public override long Seek(long offset, SeekOrigin origin)
    {
        long pos = this.Position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.Length - offset;

        this.Position = pos;
        return this.Position;
    }

    /// <summary>
    /// Set the length of the stream
    /// </summary>
    /// <param name="value"></param>
    public override void SetLength(long value)
    {
        stream.SetLength(value);
    }

    /// <summary>
    /// Write to the stream
    /// </summary>
    /// <param name="buffer">The buffer to write</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to write</param>
    public override void Write(byte[] buffer, int offset, int count)
    {
        stream.Write(buffer, offset, count);
    }
}
