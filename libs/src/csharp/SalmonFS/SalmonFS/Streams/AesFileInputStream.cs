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

using Mku.Streams;
using Mku.Salmon.Streams;
using Mku.SalmonFS.File;
using System;
using System.IO;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;
using Buffer = Mku.Streams.Buffer;
using Mku.Salmon;

namespace Mku.SalmonFS.Streams;

/// <summary>
///  Implementation of a C# InputStream for seeking and reading a AesFile.
///  This class provides a seekable source with parallel substreams and cached buffers
///  for performance.
/// </summary>
public class AesFileInputStream : InputStreamWrapper
{
    // default threads is one but you can increase it
    private static readonly int DEFAULT_THREADS = 1;

    private AesStream[] streams;
    private readonly AesFile salmonFile;
    private readonly int threads;

    /// <summary>
    ///  Instantiate a seekable stream from an encrypted file source
	/// </summary>
	///  <param name="salmonFile">  The source file.</param>
    ///  <param name="buffersCount">Number of buffers to use.</param>
    ///  <param name="bufferSize">  The length of each buffer.</param>
    ///  <param name="threads">     The number of threads/streams to source the file in parallel.</param>
    ///  <param name="backOffset">  The back offset to use</param>
    public AesFileInputStream(AesFile salmonFile, int buffersCount = 1, int bufferSize = 0, int threads = 1, int backOffset = 32768)
        : base(null, buffersCount, bufferSize, backOffset, salmonFile.FileChunkSize > 0 ? salmonFile.FileChunkSize : Generator.BLOCK_SIZE)
    {
        this.salmonFile = salmonFile;
        this.TotalSize = salmonFile.Length;
        if (threads == 0)
            threads = DEFAULT_THREADS;
        this.threads = threads;
        this.PositionEnd = TotalSize - 1;
        CreateStreams();
    }

    /// <summary>
    ///  Method creates the parallel streams for reading from the file
    /// </summary>
    private void CreateStreams()
    {
        streams = new AesStream[threads];
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
    ///  Fills a cache buffer with the decrypted data from the encrypted source file.
	/// </summary>
	///  <param name="cacheBuffer">The cache buffer that will store the decrypted contents</param>
    ///  <param name="startPosition"> The start position of the data requested</param>
    ///  <param name="length"> The length of the data requested</param>
    [MethodImpl(MethodImplOptions.Synchronized)]
    protected override int FillBuffer(Buffer cacheBuffer, long startPosition, int length)
    {
        int bytesRead;
        if (threads == 1)
        {
            bytesRead = FillBufferPart(cacheBuffer, startPosition, 0, length, streams[0]);
        }
        else
        {
            bytesRead = FillBufferMulti(cacheBuffer, startPosition, length);
        }
        return bytesRead;
    }

    /// <summary>
    ///  Fill the buffer using parallel streams for performance
	/// </summary>
	///  <param name="cacheBuffer">  The cache buffer that will store the decrypted data</param>
    ///  <param name="startPosition">The source file position the read will start from</param>
    ///  <param name="totalBufferLength">   The total buffer size that will be used to read from the file</param>
    private int FillBufferMulti(Buffer cacheBuffer, long startPosition, long totalBufferLength)
    {
        int bytesRead = 0;
        IOException ex = null;
        Task[] tasks = new Task[threads];
        // Multithreaded decryption jobs
        int partSize = (int)Math.Ceiling(totalBufferLength / (float)threads);
        for (int i = 0; i < threads; i++)
        {
            int index = i;
            tasks[i] = Task.Run(() =>
            {
                int start = partSize * index;
                int length;
                if (index == threads - 1)
                    length = BufferSize - start;
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
    ///  Close the stream and associated backed streams and clear buffers.
	/// </summary>
	///  <exception cref="IOException">Thrown if error during IO</exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    override
    public void Close()
    {
        CloseStreams();
        base.Close();
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
}

