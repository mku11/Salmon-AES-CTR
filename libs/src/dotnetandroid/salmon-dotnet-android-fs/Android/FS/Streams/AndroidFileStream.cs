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

using Android.OS;
using Java.Nio;
using Java.Nio.Channels;
using Mku.Android.FS.File;
using System;
using System.IO;

namespace Mku.Android.FS.Streams;

/// <summary>
///  Class is a stream implementation wrapper for java streams that are retrieved from AndroidFile
///  which support external SD cards.
/// </summary>
///
public class AndroidFileStream : Stream
{
    private readonly AndroidFile file;
    private readonly ParcelFileDescriptor pfd;
    private bool canWrite;
    private readonly FileChannel fileChannel;

    /// <summary>
    ///  True if the stream is readable.
	/// </summary>
	///  <returns>True if readable</returns>
    override
    public bool CanRead => fileChannel.IsOpen && !canWrite;


    /// <summary>
    ///  True if the stream is writeable.
	/// </summary>
	///  <returns>True if writable</returns>
    override
    public bool CanWrite => fileChannel.IsOpen && canWrite;

    /// <summary>
    ///  True if the stream is seekable (random access).
	/// </summary>
	///  <returns>True if seekable</returns>
    override
    public bool CanSeek => true;

    /// <summary>
    ///  Get the size of the stream.
	/// </summary>
	///  <returns>The length</returns>
    override
    public long Length => file.Length;

    /// <summary>
    ///  Get the current position of the stream.
	/// </summary>
	///  <returns>The current position</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public long Position
    {
        get
        {
            return fileChannel.Position();
        }
        set
        {
            fileChannel.Position(value);
        }
    }

    /// <summary>
    ///  Construct a file stream from an AndroidFile.
    ///  This will create a wrapper stream that will route read() and write() to the Android FileChannel
	/// </summary>
	///  <param name="file">The AndroidFile that will be used to get the read/write stream</param>
    ///  <param name="mode">The mode "r" for read "rw" for write</param>
    public AndroidFileStream(AndroidFile file, string mode)
    {
        this.file = file;
        if (mode.Equals("rw"))
        {
            canWrite = true;
        }
        pfd = file.GetFileDescriptor(mode);
        if (canWrite)
        {
            Java.IO.FileOutputStream outs = new Java.IO.FileOutputStream(pfd.FileDescriptor);
            fileChannel = outs.Channel;
        }
        else
        {
            Java.IO.FileInputStream ins = new Java.IO.FileInputStream(pfd.FileDescriptor);
            fileChannel = ins.Channel;
        }
    }

    /// <summary>
    ///  Set the length of the stream.
	/// </summary>
	///  <param name="value">The length.</param>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public void SetLength(long value)
    {
        fileChannel.Position(value);
    }

    /// <summary>
    ///  Read data from the stream into the buffer.
	/// </summary>
	///  <param name="buffer">The buffer</param>
    ///  <param name="offset">The offset</param>
    ///  <param name="count">The count</param>
    ///  <returns>The bytes read</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public int Read(byte[] buffer, int offset, int count)
    {
        ByteBuffer buf = ByteBuffer.Allocate(count);
        int bytesRead = fileChannel.Read(buf);
        if (bytesRead <= 0)
            return 0;
        buf.Rewind();
        buf.Get(buffer, offset, bytesRead);
        return bytesRead;
    }

    /// <summary>
    ///  Write the data buffer to the stream.
	/// </summary>
	///  <param name="buffer">The buffer to read the contents from.</param>
    ///  <param name="offset">The position the reading will start from.</param>
    ///  <param name="count">The count of bytes to be read from the buffer.</param>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public void Write(byte[] buffer, int offset, int count)
    {
        ByteBuffer buf = ByteBuffer.Allocate(count);
        buf.Put(buffer, offset, count);
        buf.Rewind();
        fileChannel.Write(buf);
    }

    /// <summary>
    ///  Seek to the requested position.
	/// </summary>
	///  <param name="offset">The new position.</param>
    ///  <param name="origin">The origin type.</param>
    ///  <returns>The current position after seeking</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public long Seek(long offset, SeekOrigin origin)
    {
        long pos = fileChannel.Position();

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.Length - offset;

        fileChannel.Position(pos);
        return fileChannel.Position();

    }

    /// <summary>
    ///  Flush the buffers to the stream.
	/// </summary>
	///
    override
    public void Flush()
    {
        try
        {
            if (fileChannel.IsOpen)
            {
                fileChannel.Force(true);
            }
        }
        catch (Exception ex)
        {
            System.Console.Error.WriteLine(ex);
        }
    }

    /// <summary>
    ///  Close the stream.
	/// </summary>
	///  <exception cref="IOException">Thrown if error during IO</exception>
    override
    public void Close()
    {
        fileChannel.Close();
        pfd.Close();
    }
}
