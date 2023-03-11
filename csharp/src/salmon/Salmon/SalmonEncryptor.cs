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
using Salmon.Streams;
using System;
using System.IO;
using System.Threading;
using static Salmon.Streams.SalmonStream;

namespace Salmon
{
    /// <summary>
    /// Utility class that encrypts and decrypts byte arrays
    /// </summary>
    public class SalmonEncryptor
    {
        private static int bufferSize = 32768;

        /// <summary>
        /// Decrypts a byte array,
        /// </summary>
        /// <param name="data">Data to be decrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The nonce to be used</param>
        /// <param name="header">Optional header data to be added to the header (ie store Nonce). Header data are not encrypted!</param>
        /// <param name="threads">Parallel processing threads default is a single thread.</param>
        /// <returns></returns>
        public static byte[] Decrypt(byte[] data, byte[] key, byte[] nonce = null, 
            bool hasHeaderData = false, int threads = 1,
            bool integrity = false, byte[] hmacKey = null, int? chunkSize = null)
        {
            if (key == null)
                throw new Exception("Need to specify a key");
            if (!hasHeaderData && nonce == null)
                throw new Exception("Need to specify a nonce if the file doesn't have a header");
            
            if (integrity)
                chunkSize = chunkSize == null ? SalmonStream.DEFAULT_CHUNK_SIZE : (int)chunkSize;

            MemoryStream inputStream = new MemoryStream(data);
            byte[] headerData = null;
            if (hasHeaderData)
            {
                byte[] magicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];
                inputStream.Read(magicBytes, 0, magicBytes.Length);
                byte[] versionBytes = new byte[SalmonGenerator.VERSION_LENGTH];
                inputStream.Read(versionBytes, 0, versionBytes.Length);
                byte[] chunkSizeHeader = new byte[SalmonGenerator.CHUNKSIZE_LENGTH];
                inputStream.Read(chunkSizeHeader, 0, chunkSizeHeader.Length);
                chunkSize = (int) BitConverter.ToLong(chunkSizeHeader, 0, SalmonGenerator.CHUNKSIZE_LENGTH);
                if (chunkSize > 0)
                    integrity = true;
                nonce = new byte[SalmonGenerator.NONCE_LENGTH];
                inputStream.Read(nonce, 0, nonce.Length);
                inputStream.Position = 0;
                headerData = new byte[magicBytes.Length + versionBytes.Length + chunkSizeHeader.Length + nonce.Length];
                inputStream.Read(headerData, 0, headerData.Length);
            }

            long realSize = GetOutputSize(data, key, nonce, EncryptionMode.Decrypt,
                headerData: headerData, integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            byte[] outData = new byte[realSize];

            if (threads == 1)
            {
                DecryptData(inputStream, 0, inputStream.Length, outData,
                    key, nonce, headerData, integrity, hmacKey, chunkSize);
                return outData;
            }

            AutoResetEvent done = new AutoResetEvent(false);
            int runningThreads = 1;
            long partSize = data.Length;

            // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
            long minPartSize = SalmonStream.AES_BLOCK_SIZE;
            if (integrity && chunkSize != null)
                minPartSize = (long)chunkSize;
            else if (integrity)
                minPartSize = SalmonStream.DEFAULT_CHUNK_SIZE;

            if (partSize > minPartSize)
            {
                partSize = (int)Math.Ceiling(partSize / (float)threads);
                // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
                long rem = partSize % minPartSize;
                if (rem != 0)
                    partSize += minPartSize - rem;

                runningThreads = (int)(data.Length / partSize);
                if (partSize % data.Length != 0)
                    runningThreads++;
            }

            Exception ex = null;
            for (int i = 0; i < runningThreads; i++)
            {
                int index = i;
                ThreadPool.QueueUserWorkItem(state =>
                {
                    try
                    {
                        long start = partSize * index;
                        long length = Math.Min(partSize, data.Length - start);
                        MemoryStream ins = new MemoryStream(data);
                        DecryptData(ins, start, length, outData, key, nonce, headerData, integrity, hmacKey, chunkSize);
                    }
                    catch (Exception ex1)
                    {
                        ex = ex1;
                    }
                    runningThreads--;
                    if (runningThreads == 0)
                        done.Set();
                });
            }
            done.WaitOne();
            inputStream.Close();
            if (ex != null)
                throw ex;
            return outData;
        }

        public static long GetOutputSize(byte[] data, byte[] key, byte[] nonce, EncryptionMode mode, 
            byte[] headerData, bool integrity, int? chunkSize, byte[] hmacKey)
        {
            MemoryStream inputStream = new MemoryStream(data);
            SalmonStream s = new SalmonStream(key, nonce, mode, inputStream,
                headerData: headerData, integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            long size = s.ActualLength;
            s.Close();
            return size;
        }

        private static void DecryptData(Stream inputStream, long start, long count, byte[] outData,
            byte[] key, byte[] nonce,
            byte[] headerData = null, bool integrity = false, byte[] hmacKey = null, int? chunkSize = null)
        {
            SalmonStream stream = null;
            MemoryStream outputStream = null;
            try
            {
                Console.WriteLine("Decrypting range: " + start + " to: " + count);
                outputStream = new MemoryStream(outData);
                outputStream.Position = start;
                stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                headerData: headerData, integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
                stream.Position = start;
                long totalChunkBytesRead = 0;
                // need to be align to the chunksize if there is one
                int buffSize = Math.Max(bufferSize, stream.GetChunkSize());
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
                throw ex;
            }
            finally
            {
                if(inputStream!=null)
                    inputStream.Close();
                if (stream != null)
                    stream.Close();
                if (outputStream != null)
                    outputStream.Close();
            }
        }

        /// <summary>
        /// Encrypts a byte array
        /// </summary>
        /// <param name="data">Text to be encrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The nonce to be used</param>
        /// <returns></returns>
        public static byte[] Encrypt(byte[] data, byte[] key, byte[] nonce = null, 
            bool storeHeaderData = false, int threads = 1,
            bool integrity = false, byte[] hmacKey = null, int? chunkSize = null)
        {
            if (integrity)
                chunkSize = chunkSize == null ? SalmonStream.DEFAULT_CHUNK_SIZE : (int)chunkSize;
            else
                chunkSize = 0;

            MemoryStream outputStream = new MemoryStream();
            byte[] headerData = null;
            if (storeHeaderData)
            {
                byte[] magicBytes = SalmonGenerator.GetMagicBytes();
                outputStream.Write(magicBytes, 0, magicBytes.Length);
                byte version = SalmonGenerator.GetVersion();
                byte[] versionBytes = new byte[] { version };
                outputStream.Write(versionBytes, 0, versionBytes.Length);
                byte[] chunkSizeBytes = BitConverter.ToBytes((int) chunkSize, SalmonGenerator.CHUNKSIZE_LENGTH);
                outputStream.Write(chunkSizeBytes, 0, chunkSizeBytes.Length);
                outputStream.Write(nonce, 0, nonce.Length);
                outputStream.Flush();
                headerData = outputStream.ToArray();
            }

            long realSize = GetOutputSize(data, key, nonce, EncryptionMode.Encrypt,
                headerData: headerData, integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            byte[] outData = new byte[realSize];
            outputStream.Position = 0;
            outputStream.Read(outData, 0, (int)outputStream.Length);
            outputStream.Close();

            if (threads == 1)
            {
                MemoryStream inputStream = new MemoryStream(data);
                EncryptData(inputStream, 0, data.Length, outData,
                    key, nonce, headerData, integrity, hmacKey, chunkSize);
                return outData;
            }

            AutoResetEvent done = new AutoResetEvent(false);
            int runningThreads = 1;
            long partSize = data.Length;

            // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
            long minPartSize = SalmonStream.AES_BLOCK_SIZE;
            if (integrity && chunkSize != null)
                minPartSize = (long)chunkSize;
            else if (integrity)
                minPartSize = SalmonStream.DEFAULT_CHUNK_SIZE;

            if (partSize > minPartSize)
            {
                partSize = (int)Math.Ceiling(partSize / (float)threads);
                // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
                long rem = partSize % minPartSize;
                if (rem != 0)
                    partSize += minPartSize - rem;

                runningThreads = (int)(data.Length / partSize);
                if (partSize % data.Length != 0)
                    runningThreads++;
            }

            Exception ex = null;
            for (int i = 0; i < runningThreads; i++)
            {
                int index = i;
                ThreadPool.QueueUserWorkItem(state =>
                {
                    try
                    {
                        long start = partSize * index;
                        long length = Math.Min(partSize, data.Length - start);
                        MemoryStream ins = new MemoryStream(data);
                        EncryptData(ins, start, length, outData, key, nonce, headerData, integrity, hmacKey, chunkSize);
                    }
                    catch (Exception ex1)
                    {
                        ex = ex1;
                    }
                    runningThreads--;
                    if (runningThreads == 0)
                        done.Set();
                });
            }
            done.WaitOne();
            if (ex != null)
                throw ex;
            return outData;
        }

        private static void EncryptData(MemoryStream inputStream, long start, long count, byte[] outData,
            byte[] key, byte[] nonce,byte[] headerData = null, 
            bool integrity = false, byte[] hmacKey = null, int? chunkSize = null)
        {
            MemoryStream outputStream = new MemoryStream(outData);
            SalmonStream stream = null;
            try
            {
                Console.WriteLine("Encrypting range: " + start + " to: " + count);
                inputStream.Position = start;
                stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, outputStream, headerData: headerData, 
                    integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
                stream.SetAllowRangeWrite(true);
                stream.Position = start;
                long totalChunkBytesRead = 0;
                // need to be align to the chunksize if there is one
                int buffSize = Math.Max(bufferSize, stream.GetChunkSize());
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
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                throw ex;
            }
            finally
            {
                if (outputStream != null)
                    outputStream.Close();
                if (stream != null)
                    stream.Close();
                if (inputStream != null)
                    inputStream.Close();
            }
        }

        public static void SetBufferSize(int bufferSize)
        {
            SalmonEncryptor.bufferSize = bufferSize;
        }
    }
}
