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

using Salmon;
using Salmon.FS;
using Salmon.Streams;
using System;
using System.IO;
using System.Threading;

namespace SalmonFS.Media
{
    /// <summary>
    /// This class provides a parallel processing seekable source for encrypted media content
    /// </summary>
    public class SalmonMediaDataSource : System.IO.Stream, IDisposable
    {
        private static bool enableLog = false;

        // Default cache buffer should be high enough for some mpeg videos to work
        // the cache buffers should be aligned to the SalmonFile chunk size for efficiency
        private static readonly int DEFAULT_MEDIA_CACHE_BUFFER_SIZE = 512 * 1024;
        // this offset is also needed to be aligned to the chunk size
        private static readonly int STREAM_OFFSET = 256 * 1024;
        // default threads is one but you can increase it
        private static readonly int DEFAULT_MEDIA_THREADS = 1;

        private CacheBuffer[] buffers = null;
        private SalmonStream[] streams;
        private readonly int buffersCount = 2;
        private readonly SalmonFile salmonFile;
        private readonly long streamSize;
        private readonly int cacheBufferSize;
        private readonly int threads = DEFAULT_MEDIA_THREADS;

        private bool integrityFailed;
        private long position;

        public override bool CanRead => streams[0].CanRead;

        public override bool CanSeek => streams[0].CanSeek;

        public override bool CanWrite => streams[0].CanWrite;

        public override long Length => streams[0].Length;

        public override long Position
        {
            get => position; 
            set {
                position = value;
            }
        }

        /// <summary>
        /// Construct a seekable source for the media player from an encrypted file source
        /// </summary>
        /// <param name="context">Context that this data source will be used with. This is usually the activity the MediaPlayer is attached to</param>
        /// <param name="salmonFile"></param>
        /// <param name="bufferSize"></param>
        /// <param name="threads"></param>
        public SalmonMediaDataSource(SalmonFile salmonFile, int bufferSize, int threads)
        {
            this.salmonFile = salmonFile;
            this.streamSize = salmonFile.GetSize();
            if (bufferSize == 0)
                bufferSize = DEFAULT_MEDIA_CACHE_BUFFER_SIZE;
            if (threads == 0)
                threads = DEFAULT_MEDIA_THREADS;
            this.cacheBufferSize = bufferSize;
            this.threads = threads;
            CreateBuffers();
            CreateStreams();
        }

        public static void SetEnableLog(bool value)
        {
            enableLog = value;
        }

        /// <summary>
        /// Method creates the parallel streams for reading from the file
        /// </summary>
        private void CreateStreams()
        {
            try
            {
                streams = new SalmonStream[threads];
                for (int i = 0; i < threads; i++)
                {
                    streams[i] = salmonFile.GetInputStream();
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
        }

        /// <summary>
        /// Create cache buffers that will be used for sourcing the files.
        /// These will help reducing multiple small decryption reads from the encrypted source.
        /// The first buffer will be sourcing at the start of the media file where the header and indexing are
        /// The rest of the buffers can be placed to whatever position the user slides to
        /// </summary>
        private void CreateBuffers()
        {
            buffers = new CacheBuffer[buffersCount];
            for (int i = 0; i < buffers.Length; i++)
                buffers[i] = new CacheBuffer(cacheBufferSize);
        }

        public override long Seek(long offset, SeekOrigin origin)
        {
            long currPos = this.position;
            if (offset >= this.streamSize)
                return 0;
            if(origin == SeekOrigin.Begin)
                this.position = offset;
            else if (origin == SeekOrigin.Current)
                this.position += offset;
            else if (origin == SeekOrigin.End)
                this.position = Length - offset;
            return this.position - currPos;
        }

        /// <summary>
        /// Decrypts and reads the contents of an encrypted file
        /// </summary>
        /// <param name="position">The source file position the read will start from</param>
        /// <param name="buffer">The buffer that will store the decrypted contents</param>
        /// <param name="offset">The position on the buffer that the decrypted data will start</param>
        /// <param name="size">The length of the data requested</param>
        /// <returns></returns>
        public override int Read(byte[] buffer, int offset, int size)
        {
            //we return 0 for c#
            try
            {
                if (position >= salmonFile.GetSize())
                    return 0;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }

            int minSize = 0;
            int bytesRead = 0;
            try
            {
                CacheBuffer cacheBuffer = GetCacheBuffer(position);
                if (cacheBuffer == null)
                {
                    cacheBuffer = GetAvailCacheBuffer();
                    // for some media the player makes a second immediate request
                    // in a position a few bytes before the first request. To make
                    // sure we don't make 2 overlapping requests we start the buffer
                    // a little before to the first request.
                    long startPosition = position - STREAM_OFFSET;
                    if (startPosition < 0)
                        startPosition = 0;

                    bytesRead = FillBuffer(cacheBuffer, startPosition, offset, cacheBufferSize);

                    if (bytesRead <= 0)
                        return bytesRead;
                    cacheBuffer.startPos = startPosition;
                    cacheBuffer.count = bytesRead;
                }
                minSize = Math.Min(size, (int)(cacheBuffer.count - position + cacheBuffer.startPos));
                Array.Copy(cacheBuffer.buffer, (int)(position - cacheBuffer.startPos), buffer, 0, minSize);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            position += minSize;
            return minSize;
        }

        /// <summary>
        /// Fills a cache buffer with the decrypted data from the encrypted source file.
        /// </summary>
        /// <param name="cacheBuffer">The cache buffer that will store the decrypted contents</param>
        /// <param name="offset">The position on the buffer that the decrypted data will start</param>
        /// <param name="bufferSize">The length of the data requested</param>
        /// <returns></returns>
        private int FillBuffer(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize)
        {
            long start = 0;
            if (enableLog)
            {
                start = SalmonTime.CurrentTimeMillis();
            }
            int bytesRead;
            if (threads == 1)
            {
                bytesRead = FillBufferPart(cacheBuffer, startPosition, offset, bufferSize, streams[0]);
            }
            else
            {
                bytesRead = FillBufferMulti(cacheBuffer, startPosition, offset, bufferSize);
            }
            if (enableLog)
            {
                Console.WriteLine("Total requested: " + bufferSize + ", Total Read: " + bytesRead + " bytes in: " + (SalmonTime.CurrentTimeMillis() - start) + " ms");
            }
            return bytesRead;
        }

        /// <summary>
        /// Fills a cache buffer with the decrypted data from a part of an encrypted file served as a salmon stream
        /// </summary>
        /// <param name="cacheBuffer">The cache buffer that will store the decrypted contents</param>
        /// <param name="offset">The position on the buffer that the decrypted data will start</param>
        /// <param name="bufferSize">The length of the data requested</param>
        /// <param name="salmonStream">The stream that will be used to read from</param>
        /// <returns></returns>
        private int FillBufferPart(CacheBuffer cacheBuffer, long start, int offset, int bufferSize,
                                   SalmonStream salmonStream)
        {
            // there is no need to pre test the SalmonFile for integrity we can start reading it
            // while we fill up our buffers. if we reach a chunk with a mismatch on the HMAC
            // there will be an integrity exception thrown.
            try
            {
                salmonStream.Seek(start, SeekOrigin.Begin);
                int totalBytesRead = salmonStream.Read(cacheBuffer.buffer, offset, bufferSize);
                return totalBytesRead;
            }
            catch (SalmonIntegrity.SalmonIntegrityException ex)
            {
                Console.Error.WriteLine(ex);
                DisplayIntegrityErrorOnce();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
            return 0;
        }

        /// <summary>
        /// Display a message when integrity test has failed
        /// </summary>
        private void DisplayIntegrityErrorOnce()
        {
            if (!integrityFailed)
            {
                integrityFailed = true;
                //TODO: display to the user
                //            new Alert(javafx.scene.control.Alert.AlertType.ERROR, "File corrupt or tampered").show();
            }
        }

        /// <summary>
        /// Fill the buffer using parallel streams for performance
        /// </summary>
        /// <param name="cacheBuffer">The cache buffer that will store the decrypted data</param>
        /// <param name="startPosition">The source file position the read will start from</param>
        /// <param name="offset">The start position on the cache buffer that the decrypted data will be stored</param>
        /// <param name="bufferSize">The buffer size that will be used to read from the file</param>
        /// <returns></returns>
        private int FillBufferMulti(CacheBuffer cacheBuffer, long startPosition, int offset, int bufferSize)
        {
            int[] bytesRead = { 0 };
            // Multi threaded decryption jobs
            CountdownEvent countDownLatch = new CountdownEvent(threads);
            int partSize = (int)Math.Ceiling(bufferSize / (float)threads);
            for (int i = 0; i < threads; i++)
            {
                int index = i;
                new Thread(() =>
                {
                    int start = partSize * index;
                    int length = Math.Min(partSize, bufferSize - start);
                    int chunkBytesRead = FillBufferPart(cacheBuffer, startPosition + start, offset + start, length,
                            streams[index]);
                    if (chunkBytesRead >= 0)
                        bytesRead[0] += chunkBytesRead;
                    countDownLatch.Signal();

                }).Start();
            }
            countDownLatch.Wait();
            return bytesRead[0];
        }

        /// <summary>
        /// Returns an available cache buffer if there is none then it reuses the last one
        /// </summary>
        /// <returns></returns>
        private CacheBuffer GetAvailCacheBuffer()
        {
            for (int i = 0; i < buffers.Length; i++)
            {
                CacheBuffer buffer = buffers[i];
                if (buffer.count == 0)
                    return buffer;
            }
            return buffers[buffers.Length - 1];
        }

        /// <summary>
        /// Returns the buffer that contains the data requested.
        /// </summary>
        /// <param name="position">The source file position of the data to be read</param>
        /// <returns></returns>
        private CacheBuffer GetCacheBuffer(long position)
        {
            for (int i = 0; i < buffers.Length; i++)
            {
                CacheBuffer buffer = buffers[i];
                if (position >= buffer.startPos && position < buffer.startPos + buffer.count)
                    return buffer;
            }
            return null;
        }


        public override void Close()
        {
            CloseStreams();
            CloseBuffers();
        }

        private void CloseBuffers()
        {
            for (int i = 0; i < buffers.Length; i++)
            {
                if (buffers[i] != null)
                    buffers[i].Clear();
                buffers[i] = null;
            }
        }

        private void CloseStreams()
        {
            for (int i = 0; i < threads; i++)
            {
                if (streams[i] != null)
                    streams[i].Close();
                streams[i] = null;
            }
        }

        public override void Flush()
        {
            
        }


        public override void SetLength(long value)
        {
            
        }

        public override void Write(byte[] buffer, int offset, int count)
        {
            throw new NotSupportedException();
        }

        /// <summary>
        /// Class will be used to cache decrypted data that can later be read via the ReadAt() method
        /// without requesting frequent decryption reads.
        /// </summary>
        //TODO: replace the CacheBuffer with a MemoryStream to simplify the code
        public class CacheBuffer
        {
            public byte[] buffer = null;
            public long startPos = 0;
            public long count = 0;

            public CacheBuffer(int bufferSize)
            {
                buffer = new byte[bufferSize];
            }

            public void Clear()
            {
                if (buffer != null)
                    Array.Fill<byte>(buffer, 0, buffer.Length, (byte)0);
            }
        }
    }
}