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

using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using Mku.Salmon.Transform;
using Mku.Streams;
using System;
using System.IO;
using System.Threading.Tasks;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.Salmon;

/// <summary>
///  Encrypts byte arrays.
/// </summary>
public class Encryptor
{

    /// <summary>
    ///  The number of parallel threads to use.
    /// </summary>
    private readonly int threads;

    /// <summary>
    ///  The buffer size to use.
    /// </summary>
    private readonly int bufferSize;

    /// <summary>
    ///  Instantiate an encryptor.
    /// </summary>
    public Encryptor()
    {
        this.threads = 1;
        // we use the chunks size as default this keeps buffers aligned in case
        // integrity is enabled.
        this.bufferSize = Integrity.Integrity.DEFAULT_CHUNK_SIZE;
    }

    /// <summary>
    ///  Instantiate an encryptor with parallel tasks and buffer size.
	/// </summary>
	///  <param name="threads">   The number of threads to use.</param>
    public Encryptor(int threads)
    {
        this.threads = threads;
        this.bufferSize = Integrity.Integrity.DEFAULT_CHUNK_SIZE;
    }

    /// <summary>
    ///  Instantiate an encryptor with parallel tasks and buffer size.
	/// </summary>
	///  <param name="threads">   The number of threads to use.</param>
    ///  <param name="bufferSize">The buffer size to use. It is recommended for performance  to use
    ///                    a multiple of the chunk size if you enabled integrity
    ///                    otherwise a multiple of the AES block size (16 bytes).</param>
    public Encryptor(int threads, int bufferSize)
    {
        this.threads = threads;
        this.bufferSize = bufferSize;
    }

    /// <summary>
    ///  Encrypts a byte array using the provided key and nonce.
	/// </summary>
	///  <param name="data">           The byte array to be encrypted.</param>
    ///  <param name="key">            The AES key to be used.</param>
    ///  <param name="nonce">          The nonce to be used.</param>
    ///  <param name="format">         The format to use, see EncryptionFormat</param>
    ///  <param name="integrity">      True if you want to calculate and store hash signatures for each chunkSize.</param>
    ///  <param name="hashKey">        Hash key to be used for all chunks.</param>
    ///  <param name="chunkSize">      The chunk size.</param>
    ///  <returns>The byte array with the encrypted data.</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public byte[] Encrypt(byte[] data, byte[] key, byte[] nonce,
                          EncryptionFormat format,
                          bool integrity = false, byte[] hashKey = null, int chunkSize = 0)
    {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        if (integrity)
            chunkSize = chunkSize <= 0 ? Integrity.Integrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;

        int realSize = (int)AesStream.GetOutputSize(EncryptionMode.Encrypt, data.Length, format, chunkSize);
        byte[] outData = new byte[realSize];

        if (threads == 1)
        {
            MemoryStream inputStream = new MemoryStream(data);
            EncryptData(inputStream, 0, data.Length, outData,
                    key, nonce, format, integrity, hashKey, chunkSize);
        }
        else
        {
            EncryptDataParallel(data, outData,
                    key, hashKey, nonce, format,
                    chunkSize, integrity);
        }
        return outData;
    }

    /// <summary>
    ///  Encrypt stream using parallel threads.
	/// </summary>
	///  <param name="data">      The input data to be encrypted</param>
    ///  <param name="outData">   The output buffer with the encrypted data.</param>
    ///  <param name="key">       The AES key.</param>
    ///  <param name="hashKey">   The hash key.</param>
    ///  <param name="nonce">     The nonce to be used for encryption.</param>
    ///  <param name="format">         The format to use, see EncryptionFormat</param>
    ///  <param name="chunkSize"> The chunk size.</param>
    ///  <param name="integrity"> True to apply integrity.</param>
    private void EncryptDataParallel(byte[] data, byte[] outData,
                                     byte[] key, byte[] hashKey, byte[] nonce,
                                     EncryptionFormat format,
                                     int chunkSize, bool integrity)
    {

        int runningThreads = 1;
        long partSize = data.Length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        long minPartSize = AESCTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize > 0)
            minPartSize = (long)chunkSize;
        else if (integrity)
            minPartSize = Integrity.Integrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize)
        {
            partSize = (int)Math.Ceiling(data.Length / (float)threads);
            if (partSize > minPartSize)
                partSize -= partSize % minPartSize;
            else
                partSize = minPartSize;
            runningThreads = (int)(data.Length / partSize);
            if (runningThreads > this.threads)
                runningThreads = this.threads;
        }

        SubmitEncryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, format,
                integrity, chunkSize);
    }

    /// <summary>
    ///  Submit encryption parallel jobs.
	/// </summary>
	///  <param name="runningThreads">The number of threads to submit.</param>
    ///  <param name="partSize">      The data length of each part that belongs to each thread.</param>
    ///  <param name="data">          The buffer of data you want to decrypt. This is a shared byte array across all threads where each</param>
    ///                        thread will read each own part.
    ///  <param name="outData">       The buffer of data containing the encrypted data.</param>
    ///  <param name="key">           The AES key.</param>
    ///  <param name="hashKey">       The hash key for integrity.</param>
    ///  <param name="nonce">         The nonce for the data.</param>
    ///  <param name="format">         The format to use, see EncryptionFormat</param>
    ///  <param name="integrity">     True to apply the data integrity.</param>
    ///  <param name="chunkSize">     The chunk size.</param>
    private void SubmitEncryptJobs(int runningThreads, long partSize, byte[] data, byte[] outData,
                                   byte[] key, byte[] hashKey, byte[] nonce,
                                   EncryptionFormat format,
                                   bool integrity, int chunkSize)
    {
        Task[] tasks = new Task[runningThreads];
        Exception ex = null;
        for (int i = 0; i < runningThreads; i++)
        {
            int index = i;
            tasks[i] = Task.Run(() =>
            {
                try
                {
                    long start = partSize * index;
                    long length;
                    if (index == runningThreads - 1)
                        length = data.Length - start;
                    else
                        length = partSize;
                    MemoryStream ins = new MemoryStream(data);
                    EncryptData(ins, start, length, outData, key, nonce, format, integrity, hashKey, chunkSize);
                }
                catch (Exception ex1)
                {
                    ex = ex1;
                }
            });
        }
        Task.WaitAll(tasks);
        if (ex != null)
        {
            throw ex;
        }
    }

    /// <summary>
    ///  Encrypt the data stream.
	/// </summary>
	///  <param name="inputStream">The Stream to be encrypted.</param>
    ///  <param name="start">      The start position of the stream to be encrypted.</param>
    ///  <param name="count">      The number of bytes to be encrypted.</param>
    ///  <param name="outData">    The buffer with the encrypted data.</param>
    ///  <param name="key">        The AES key to be used.</param>
    ///  <param name="nonce">      The nonce to be used.</param>
    ///  <param name="format">         The format to use, see EncryptionFormat</param>
    ///  <param name="integrity">  True to apply integrity.</param>
    ///  <param name="hashKey">    The key to be used for integrity application.</param>
    ///  <param name="chunkSize">  The chunk size.</param>
    ///  <exception cref="IOException">             Thrown if there is an error with the stream.</exception>
    ///  <exception cref="SecurityException"> Thrown if there is a security exception with the stream.</exception>
    ///  <exception cref="IntegrityException">Thrown if integrity cannot be applied.</exception>
    private void EncryptData(MemoryStream inputStream, long start, long count, byte[] outData,
                             byte[] key, byte[] nonce,
                             EncryptionFormat format,
                             bool integrity, byte[] hashKey, int chunkSize)
    {
        MemoryStream outputStream = new MemoryStream(outData);
        AesStream stream = null;
        try
        {
            inputStream.Position = start;
            stream = new AesStream(key, nonce, EncryptionMode.Encrypt, outputStream, format,
                    integrity, hashKey, chunkSize);
            stream.AllowRangeWrite = true;
            stream.Position = start;
            long totalChunkBytesRead = 0;
            int buffSize = RandomAccessStream.DEFAULT_BUFFER_SIZE;
            buffSize = buffSize / stream.AlignSize * stream.AlignSize;
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = inputStream.Read(buff, 0, Math.Min(buff.Length, (int)(count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count)
            {
                stream.Write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            stream.Flush();
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            throw;
        }
        finally
        {
            outputStream.Close();
            if (stream != null)
                stream.Close();
            if (inputStream != null)
                inputStream.Close();
        }
    }

    /// <summary>
    /// Close all associated resources
    /// </summary>
    public void Close()
    {

    }
}
