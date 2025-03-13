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
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Encrypts byte arrays.
/// </summary>
public class SalmonEncryptor
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
    public SalmonEncryptor()
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
    public SalmonEncryptor(int threads)
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
    public SalmonEncryptor(int threads, int bufferSize)
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
    ///  <param name="storeHeaderData">True if you want to store a header data with the nonce. False if you store
    ///                         the nonce external. Note that you will need to provide the nonce when decrypting.</param>
    ///  <param name="integrity">      True if you want to calculate and store hash signatures for each chunkSize.</param>
    ///  <param name="hashKey">        Hash key to be used for all chunks.</param>
    ///  <param name="chunkSize">      The chunk size.</param>
    ///  <returns>The byte array with the encrypted data.</returns>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public byte[] Encrypt(byte[] data, byte[] key, byte[] nonce,
                          bool storeHeaderData = false,
                          bool integrity = false, byte[] hashKey = null, int? chunkSize = null)
    {
        if (key == null)
            throw new SalmonSecurityException("Key is missing");
        if (nonce == null)
            throw new SalmonSecurityException("Nonce is missing");

        if (integrity)
            chunkSize = chunkSize == null ? SalmonIntegrity.DEFAULT_CHUNK_SIZE : chunkSize;
        else
            chunkSize = 0;

        MemoryStream outputStream = new MemoryStream();
        byte[] headerData = null;
        if (storeHeaderData)
        {
            byte[] magicBytes = SalmonGenerator.GetMagicBytes();
            outputStream.Write(magicBytes, 0, magicBytes.Length);
            byte version = SalmonGenerator.VERSION;
            byte[] versionBytes = new byte[] { version };
            outputStream.Write(versionBytes, 0, versionBytes.Length);
            byte[] chunkSizeBytes = BitConverter.ToBytes((long)chunkSize, SalmonGenerator.CHUNK_SIZE_LENGTH);
            outputStream.Write(chunkSizeBytes, 0, chunkSizeBytes.Length);
            outputStream.Write(nonce, 0, nonce.Length);
            outputStream.Flush();
            headerData = outputStream.ToArray();
        }

        int realSize = (int)SalmonAES256CTRTransformer.GetActualSize(data, key, nonce, EncryptionMode.Encrypt,
                headerData, integrity, chunkSize, hashKey);
        byte[] outData = new byte[realSize];
        outputStream.Position = 0;
        outputStream.Read(outData, 0, (int)outputStream.Length);
        outputStream.Close();

        if (threads == 1)
        {
            MemoryStream inputStream = new MemoryStream(data);
            EncryptData(inputStream, 0, data.Length, outData,
                    key, nonce, headerData, integrity, hashKey, chunkSize);
        }
        else
        {
            EncryptDataParallel(data, outData,
                    key, hashKey, nonce, headerData,
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
    ///  <param name="headerData">The header data.</param>
    ///  <param name="chunkSize"> The chunk size.</param>
    ///  <param name="integrity"> True to apply integrity.</param>
    private void EncryptDataParallel(byte[] data, byte[] outData,
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
			if (runningThreads > this.threads)
				runningThreads = this.threads;
        }

        SubmitEncryptJobs(runningThreads, partSize,
                data, outData,
                key, hashKey, nonce, headerData,
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
    ///  <param name="headerData">    The header data common to all parts.</param>
    ///  <param name="integrity">     True to apply the data integrity.</param>
    ///  <param name="chunkSize">     The chunk size.</param>
    private void SubmitEncryptJobs(int runningThreads, long partSize, byte[] data, byte[] outData,
                                   byte[] key, byte[] hashKey, byte[] nonce,
                                   byte[] headerData, bool integrity, int? chunkSize)
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
                    EncryptData(ins, start, length, outData, key, nonce, headerData, integrity, hashKey, chunkSize);
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
    ///  <param name="headerData"> The header data to be used.</param>
    ///  <param name="integrity">  True to apply integrity.</param>
    ///  <param name="hashKey">    The key to be used for integrity application.</param>
    ///  <param name="chunkSize">  The chunk size.</param>
    ///  <exception cref="IOException">             Thrown if there is an error with the stream.</exception>
    ///  <exception cref="SalmonSecurityException"> Thrown if there is a security exception with the stream.</exception>
    ///  <exception cref="IntegrityException">Thrown if integrity cannot be applied.</exception>
    private void EncryptData(MemoryStream inputStream, long start, long count, byte[] outData,
                             byte[] key, byte[] nonce, byte[] headerData,
                             bool integrity, byte[] hashKey, int? chunkSize)
    {
        MemoryStream outputStream = new MemoryStream(outData);
        SalmonStream stream = null;
        try
        {
            inputStream.Position = start;
            stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, outputStream, headerData,
                    integrity, chunkSize, hashKey);
            stream.AllowRangeWrite = true;
            stream.Position = start;
            long totalChunkBytesRead = 0;
            // align to the chunk size if available
            int buffSize = Math.Max(bufferSize, stream.ChunkSize);
            // set the same buffer size for the internal stream
            stream.BufferSize = buffSize;
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
            throw ex;
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
