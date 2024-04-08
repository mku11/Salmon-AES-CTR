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

namespace Mku.Salmon;

/// <summary>
///  Utility class that decrypts byte arrays.
/// </summary>
public class SalmonDecryptor
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
    public SalmonDecryptor()
    {
        this.threads = 1;
        // we use the chunks size as default this keeps buffers aligned in case
        // integrity is enabled.
        this.bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
    }

    /// <summary>
    ///  Instantiate an encryptor with parallel tasks and buffer size.
	/// </summary>
	///  <param name="threads">   The number of threads to use.</param>
    public SalmonDecryptor(int threads)
    {
        this.threads = threads;
        this.bufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
    }

    /// <summary>
    ///  Instantiate an encryptor with parallel tasks and buffer size.
	/// </summary>
	///  <param name="threads">   The number of threads to use.</param>
    ///  <param name="bufferSize">The buffer size to use. It is recommended for performance  to use
    ///                    a multiple of the chunk size if you enabled integrity
    ///                    otherwise a multiple of the AES block size (16 bytes).</param>
    public SalmonDecryptor(int threads, int bufferSize)
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
    ///  <param name="hasHeaderData">The header data.</param>
    ///  <param name="integrity">Verify hash integrity in the data.</param>
    ///  <param name="hashKey">The hash key to be used for integrity.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <returns>The byte array with the decrypted data.</returns>
    ///  <exception cref="IOException">Thrown if there is a problem with decoding the array.</exception>
    ///  <exception cref="SalmonSecurityException">Thrown if the key and nonce are not provided.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public byte[] Decrypt(byte[] data, byte[] key, byte[] nonce,
                                 bool hasHeaderData = false,
                                 bool integrity = false, byte[] hashKey = null, int? chunkSize = null)
    {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (!hasHeaderData && nonce == null)
            throw new SalmonSecurityException("Need to specify a nonce if the file doesn't have a header");

        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;

        MemoryStream inputStream = new MemoryStream(data);
        SalmonHeader header;
        byte[] headerData = null;
        if (hasHeaderData)
        {
            header = SalmonHeader.ParseHeaderData(inputStream);
            if (header.ChunkSize > 0)
                integrity = true;
            chunkSize = header.ChunkSize;
            nonce = header.Nonce;
            headerData = header.HeaderData;
        }
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        int realSize = (int)SalmonAES256CTRTransformer.GetActualSize(data, key, nonce, EncryptionMode.Decrypt,
                headerData, integrity, chunkSize, hashKey);
        byte[] outData = new byte[realSize];

        if (threads == 1)
        {
            DecryptData(inputStream, 0, inputStream.Length, outData,
                    key, nonce, headerData, integrity, hashKey, chunkSize);
        }
        else
        {
            DecryptDataParallel(data, outData,
                    key, hashKey, nonce, headerData,
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
    ///  <param name="headerData">The header data.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <param name="integrity">True to verify integrity.</param>
    private void DecryptDataParallel(byte[] data, byte[] outData,
                                            byte[] key, byte[] hashKey, byte[] nonce, byte[] headerData,
                                            int? chunkSize, bool integrity)
    {
        int runningThreads = 1;
        long partSize = data.Length;

        // if we want to check integrity we align to the chunk size otherwise to the AES Block
        long minPartSize = SalmonAES256CTRTransformer.BLOCK_SIZE;
        if (integrity && chunkSize != null)
            minPartSize = (long)chunkSize;
        else if (integrity)
            minPartSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;

        if (partSize > minPartSize)
        {
            partSize = (int)Math.Ceiling(data.Length / (float)threads);
            if(partSize > minPartSize)
				partSize -= partSize % minPartSize;
			else
				partSize = minPartSize;
            runningThreads = (int)(data.Length / partSize);
        }

        SubmitDecryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, headerData,
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
    ///  <param name="headerData">The header data common to all parts.</param>
    ///  <param name="integrity">True to verify the data integrity.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    private void SubmitDecryptJobs(int runningThreads, long partSize,
                                          byte[] data, byte[] outData,
                                          byte[] key, byte[] hashKey, byte[] nonce, byte[] headerData,
                                          bool integrity, int? chunkSize)
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
                    DecryptData(ins, start, length, outData, key, nonce, headerData,
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
    ///  <param name="headerData">The header data to be used.</param>
    ///  <param name="integrity">True to verify integrity.</param>
    ///  <param name="hashKey">The hash key to be used for integrity verification.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <exception cref="IOException"> Thrown if there is an error with the stream.</exception>
    ///  <exception cref="SalmonSecurityException">Thrown if there is a security exception with the stream.</exception>
    ///  <exception cref="IntegrityException">Thrown if the stream is corrupt or tampered with.</exception>
    private void DecryptData(Stream inputStream, long start, long count, byte[] outData,
                                    byte[] key, byte[] nonce,
                                    byte[] headerData, bool integrity, byte[] hashKey, int? chunkSize)
    {
        SalmonStream stream = null;
        MemoryStream outputStream = null;
        try
        {
            outputStream = new MemoryStream(outData);
            outputStream.Position = start;
            stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                    headerData, integrity, chunkSize, hashKey);
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
            throw new SalmonSecurityException("Could not decrypt data", ex);
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
