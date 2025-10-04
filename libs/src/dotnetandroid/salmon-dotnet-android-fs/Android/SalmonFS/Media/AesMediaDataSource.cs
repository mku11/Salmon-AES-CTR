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

using Android.Media;
using Android.Util;
using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using Android.App;
using System.IO;
using System;
using Android.Widget;
using Mku.SalmonFS.File;
using Mku.SalmonFS.Streams;
using Mku.Android.SalmonFS.Drive;
using Mku.Android.FS.File;
using Mku.Streams;

namespace Mku.Android.SalmonFS.Media;


/// <summary>
/// This class provides a parallel processing seekable source for encrypted media content
/// </summary>
public class AesMediaDataSource : MediaDataSource
{
    private static readonly string TAG = nameof(AesMediaDataSource);

    private readonly System.IO.Stream stream;
    private readonly AesFile salmonFile;
	public Action<string> OnError;

    private bool integrityFailed;
    private long size;

    /// <summary>
    /// Construct a seekable source for the media player from an encrypted file.
    /// </summary>
    /// <param name="salmonFile">Salmon file to use as source</param>
    public AesMediaDataSource(AesFile salmonFile)
    {
        this.salmonFile = salmonFile;
        this.size = salmonFile.Length;
        this.stream = salmonFile.GetInputStream().AsReadStream();
    }
	
    /// <summary>
    /// Construct a seekable source for the media player from an encrypted file using cached buffers and parallel processing.
    /// </summary>
    /// <param name="salmonFile">Salmon file to use as source</param>
    /// <param name="buffers">Then number of buffers</param>
    /// <param name="bufferSize">Buffer size</param>
    /// <param name="threads">Threads for parallel processing</param>
    /// <param name="backOffset">Backwards offset</param>
    public AesMediaDataSource(AesFile salmonFile,
                                 int buffers, int bufferSize, int threads, int backOffset)
    {
        this.salmonFile = salmonFile;
        this.size = salmonFile.Length;
		this.stream = new AesFileInputStream(salmonFile, buffers, bufferSize, threads, backOffset);
    }


    /// <summary>
    /// Construct a seekable source for the media player from an encrypted stream.
    /// </summary>
    /// <param name="salmonStream">AesStream that will be used as a source</param>
    public AesMediaDataSource(AesStream salmonStream)
    {
        this.size = salmonFile.Length;
        this.stream = new InputStreamWrapper(salmonStream);
    }
	
    /// <summary>
    /// Decrypts and reads the contents of an encrypted file
    /// </summary>
    /// <param name="position">The source file position the read will start from</param>
    /// <param name="buffer">The buffer that will store the decrypted contents</param>
    /// <param name="offset">The position on the buffer that the decrypted data will start</param>
    /// <param name="size">The length of the data requested</param>
    /// <returns>The bytes read</returns>
    override
    public int ReadAt(long position, byte[] buffer, int offset, int size)
    {
        //        Log.d(TAG, "readAt: position=" + position + ",offset=" + offset + ",size=" + size);
        stream.Position = position;
        try
        {
            int bytesRead = stream.Read(buffer, offset, size);
            //            Log.d(TAG, "bytesRead: " + bytesRead);
            if (bytesRead != size)
            {
                Log.Debug(TAG, "Read underbuffered: " + bytesRead + " != " + size + ", position: " + position);
            }
            return bytesRead;
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            if (ex.InnerException != null && ex.InnerException.GetType() == typeof(IntegrityException) && !integrityFailed)
            {
                // showing integrity error only once
                integrityFailed = true;
                if (OnError != null)
                    OnError("File is corrupt or tampered");
            }
            throw;
        }
    }

    /// <summary>
    /// Get the content size
    /// </summary>
    override
    public long Size => size;

    /// <summary>
    /// Close the source and all associated resources.
    /// </summary>
    override
    public void Close()
    {
        stream.Close();
    }
}

