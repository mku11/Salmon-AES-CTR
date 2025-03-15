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
using System;
using System.IO;
using System.Threading.Tasks;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.Salmon;

/// <summary>
///  Utility class that decrypts byte arrays.
/// </summary>
public class Decryptor
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
    public Decryptor()
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
    public Decryptor(int threads)
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
    public Decryptor(int threads, int bufferSize)
    {
        this.threads = threads;
        this.bufferSize = bufferSize;
    }

    /// <summary>
    ///  Decrypt a byte array using AES256 based on the provided key and nonce.
	/// </summary>
	///  <param name="data">The input data to be decrypted.</param>
    ///  <param name="key">The AES key to use for decryption.</param>
    ///  <param name="nonce">The nonce to use for decryption.</param>
    ///  <param name="format">The EncryptionFormat.</param>
    ///  <param name="integrity">Verify hash integrity in the data.</param>
    ///  <param name="hashKey">The hash key to be used for integrity.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <returns>The byte array with the decrypted data.</returns>
    ///  <exception cref="IOException">Thrown if there is a problem with decoding the array.</exception>
    ///  <exception cref="SecurityException">Thrown if the key and nonce are not provided.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public byte[] Decrypt(byte[] data, byte[] key, byte[] nonce = null,
                                 EncryptionFormat format = EncryptionFormat.Salmon,
                                 bool integrity = true, byte[] hashKey = null, int chunkSize = 0)
    {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (format == EncryptionFormat.Generic && nonce == null)
            throw new SecurityException("Need to specify a nonce if the file doesn't have a header");


        MemoryStream inputStream = new MemoryStream(data);
        if (format == EncryptionFormat.Salmon)
        {
            Header header = Header.ReadHeaderData(inputStream);
            if (header != null)
                chunkSize = header.ChunkSize;
        }
        else if (integrity)
        {
            chunkSize = chunkSize <= 0 ? Integrity.Integrity.DEFAULT_CHUNK_SIZE : chunkSize;
        }
        else
        {
            chunkSize = 0;
        }

        int realSize = (int)AesStream.GetOutputSize(EncryptionMode.Decrypt, data.Length, format, chunkSize);
        byte[] outData = new byte[realSize];

        if (threads == 1)
        {
            DecryptData(inputStream, 0, realSize, outData,
                    key, nonce, format, integrity, hashKey, chunkSize);
        }
        else
        {
            DecryptDataParallel(data, outData,
                    key, hashKey, nonce, format,
                    chunkSize, integrity);
        }

        return outData;
    }

    /// <summary>
    ///  Decrypt stream using parallel threads.
	/// </summary>
	///  <param name="data">The input data to be decrypted</param>
    ///  <param name="outData">The output buffer with the decrypted data.</param>
    ///  <param name="key">The AES key.</param>
    ///  <param name="hashKey">The hash key.</param>
    ///  <param name="nonce">The nonce to be used for decryption.</param>
    ///  <param name="format">The EncryptionFormat.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <param name="integrity">True to verify integrity.</param>
    private void DecryptDataParallel(byte[] data, byte[] outData,
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
            if(partSize > minPartSize)
				partSize -= partSize % minPartSize;
			else
				partSize = minPartSize;
            runningThreads = (int)(data.Length / partSize);
			if (runningThreads > this.threads)
				runningThreads = this.threads;
        }

        SubmitDecryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, format,
                integrity, chunkSize);
    }

    /// <summary>
    ///  Submit decryption parallel jobs.
	/// </summary>
	///  <param name="runningThreads">The number of threads to submit.</param>
    ///  <param name="partSize">The data length of each part that belongs to each thread.</param>
    ///  <param name="data">The buffer of data you want to decrypt. This is a shared byte array across all threads where each
    ///              thread will read each own part.</param>
    ///  <param name="outData">The buffer of data containing the decrypted data.</param>
    ///  <param name="key">The AES key.</param>
    ///  <param name="hashKey">The hash key for integrity validation.</param>
    ///  <param name="nonce">The nonce for the data.</param>
    ///  <param name="format">The EncryptionFormat.</param>
    ///  <param name="integrity">True to verify the data integrity.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    private void SubmitDecryptJobs(int runningThreads, long partSize,
                                          byte[] data, byte[] outData,
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
                    DecryptData(ins, start, length, outData, key, nonce, format,
                            integrity, hashKey, chunkSize);
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
    ///  Decrypt the data stream.
	/// </summary>
	///  <param name="inputStream">The Stream to be decrypted.</param>
    ///  <param name="start">The start position of the stream to be decrypted.</param>
    ///  <param name="count">The number of bytes to be decrypted.</param>
    ///  <param name="outData">The buffer with the decrypted data.</param>
    ///  <param name="key">The AES key to be used.</param>
    ///  <param name="nonce">The nonce to be used.</param>
    ///  <param name="format">The EncryptionFormat.</param>
    ///  <param name="integrity">True to verify integrity.</param>
    ///  <param name="hashKey">The hash key to be used for integrity verification.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <exception cref="IOException"> Thrown if there is an error with the stream.</exception>
    ///  <exception cref="SecurityException">Thrown if there is a security exception with the stream.</exception>
    ///  <exception cref="IntegrityException">Thrown if the stream is corrupt or tampered with.</exception>
    private void DecryptData(Stream inputStream, long start, long count, byte[] outData,
                                    byte[] key, byte[] nonce,
                                    EncryptionFormat format, bool integrity, byte[] hashKey, int chunkSize)
    {
        AesStream stream = null;
        MemoryStream outputStream = null;
        try
        {
            outputStream = new MemoryStream(outData);
            outputStream.Position = start;
            stream = new AesStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                    format, integrity, hashKey, chunkSize);
            stream.Position = start;
            long totalChunkBytesRead = 0;
            // align to the chunksize if available
            int buffSize = Math.Max(bufferSize, stream.ChunkSize);
            // set the same buffer size for the internal stream
            stream.BufferSize = buffSize;
            byte[] buff = new byte[buffSize];
            int bytesRead;
            while ((bytesRead = stream.Read(buff, 0, Math.Min(buff.Length, (int)(count - totalChunkBytesRead)))) > 0
                    && totalChunkBytesRead < count)
            {
                outputStream.Write(buff, 0, bytesRead);
                totalChunkBytesRead += bytesRead;
            }
            outputStream.Flush();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SecurityException("Could not decrypt data", ex);
        }
        finally
        {
            if (inputStream != null)
                inputStream.Close();
            if (stream != null)
                stream.Close();
            if (outputStream != null)
                outputStream.Close();
        }
    }

    /// <summary>
    /// Close all associated resources
    /// </summary>
    public void Close()
    {

    }
}
