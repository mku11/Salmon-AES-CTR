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
using System.Runtime.CompilerServices;
using System.Security.Cryptography;
using static Salmon.SalmonIntegrity;

namespace Salmon.Streams
{
    /// <summary>
    /// Stream wrapper provides encryption and decryption of data by read and write operations.
    /// Block data integrity is also supported.
    /// </summary>
    public partial class SalmonStream : Stream
    {
        private const int MAX_CHUNK_SIZE = 1 * 1024 * 1024;
        private const int DEFAULT_CHUNK_SIZE = 256 * 1024;

        private Stream baseStream;

        private EncryptionMode encryptionMode;

        private byte[] key;
        private byte[] nonce;
        private byte[] hmacKey;

        private int blockSize;

        private long block = 0;
        private byte[] counter;
        private byte[] encCounter;
        private byte[] blockData;

        private bool integrity;
        private int chunkSize = -1;

        private byte[] headerData;

        private bool allowRangeWrite;
        private bool failSilently;       

        private ICryptoTransform aesTransformer;

        public enum EncryptionMode
        {
            Encrypt, Decrypt
        }

        /// <summary>
        /// /// AES Counter mode Stream
        /// </summary>
        /// <param name="backStream">
        /// There is only Read mode available if you need to directly write you can use it in a buffered loop
        /// or use the helper class SalmonStream.
        /// If you read from the transformer it will encrypt/decrypt the data from the baseStream.
        /// 
        /// The transformation is based on AES CTR Mode: 
        /// https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)
        /// Notes:
        /// a) The initial value of the counter is a result of the concatenation of a 12 byte nonce and an additional 4 bytes.
        /// b) The counter is incremented every block.
        /// b) The counter is then encrypted by the key the user has choosen (usually coverted from his/her text password).
        /// c) Finally the counter is xored with the plain text.
        /// </summary>
        /// <param name="key">The AES key that is used to encrypt decrypt</param>
        /// <param name="nonce">The nonce used for the initial counter</param>
        /// <param name="baseStream">The base Stream that will be used to read the data</param>
        /// <param name="headerData">Header data (unencrypted)</param>
        /// <param name="integrity"></param>
        /// <param name="chunkSize"></param>
        public SalmonStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
            Stream baseStream, byte[] headerData = null,
            bool integrity = false, int? chunkSize = null, byte[] hmacKey = null)
        {
            this.key = key;
            this.nonce = nonce;
            this.encryptionMode = encryptionMode;
            this.baseStream = baseStream;

            this.headerData = headerData;
            this.integrity = integrity;
            this.hmacKey = hmacKey;

            if (this.integrity && chunkSize < 0)
                throw new Exception("Chunk size should be zero for the default or a positive number");
            if (this.integrity && hmacKey == null)
                throw new Exception("You need an HMAC key to use with integrity");

            if (integrity && (chunkSize == null || chunkSize == 0))
                this.chunkSize = DEFAULT_CHUNK_SIZE;
            else if (chunkSize != null)
                this.chunkSize = (int)chunkSize;

            Init();
        }

        /// <summary>
        /// // If the stream has integrity enabled
        /// </summary>
        /// <returns></returns>
        public bool HasIntegrity()
        {
            return GetChunkSize() > 0;
        }

        /// <summary>
        /// Initialize the Transformer and the base stream
        /// </summary>
        private void Init()
        {
            InitTransformer();
            InitStream();
        }

        private void InitStream()
        {
            baseStream.Position = GetHeaderLength();
        }

        private long GetHeaderLength()
        {
            if (headerData == null)
                return 0;
            else
                return headerData.Length;
        }


        /// <summary>
        /// To create the AES CTR mode we use ECB for AES with No Padding. 
        /// Initailize the Counter to the initial vector provided.
        /// For each data block we increase the Counter and apply the EAS encryption on the Counter.
        /// The encrypted Counter then will be xor-ed with the actual data block.
        /// </summary>
        private void InitTransformer()
        {
            SymmetricAlgorithm aes = new AesManaged
            {
                Mode = CipherMode.ECB,
                Padding = PaddingMode.None
            };
            blockSize = aes.BlockSize / 8;
            if (integrity)
            {
                if (chunkSize < 0 || chunkSize < blockSize || chunkSize % blockSize != 0 || chunkSize > MAX_CHUNK_SIZE)
                    throw new SalmonIntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                        + blockSize + " and less than: " + MAX_CHUNK_SIZE + " bytes");
            }
            var zeroIv = new byte[blockSize];
            aesTransformer = aes.CreateEncryptor(key, zeroIv);

            ResetCounter();
            encCounter = new byte[blockSize];
            blockData = new byte[blockSize];
        }


        public override bool CanRead => baseStream.CanRead && encryptionMode == EncryptionMode.Decrypt;
        public override bool CanSeek => baseStream.CanSeek;
        public override bool CanWrite => baseStream.CanWrite && encryptionMode == EncryptionMode.Encrypt;
        public override long Length
        {
            get
            {
                int hmacOffset = chunkSize > 0 ? SalmonGenerator.GetHmacResultLength() : 0;
                long totalHMACBytes = GetTotalHMACBytesFrom(baseStream.Length - 1, hmacOffset);
                return baseStream.Length - GetHeaderLength() - totalHMACBytes;
            }
        }


        /// <summary>
        /// Current Position of the stream excluding the header and all HMAC signatures
        /// </summary>
        public override long Position
        {
            get
            {
                return GetVirtualPosition();
            }
            set
            {
                if (CanWrite && !allowRangeWrite && value != 0)
                    throw new SalmonSecurityException("Range Write is not allowed for security (non-reusable IVs), if you still want to take the risk you can override it by setting SetAllowRangeWrite(true)");
                SetVirtualPosition(value);

            }
        }

        /// <summary>
        /// Seek to a specific position on the stream. This does not include the header and any HMAC Signatures.
        /// </summary>
        /// <param name="offset">The offset that seek will use</param>
        /// <param name="origin">
        /// If it is Begin the offset will be the absolute postion from the start of the stream
        /// If it is Current the offset will be added to the current position of the stream
        /// If it is End the offset will be the absolute position starting from the end of the stream.
        /// </param>
        /// <returns></returns>
        public override long Seek(long offset, SeekOrigin origin)
        {
            if (origin == SeekOrigin.Begin)
                Position = offset;
            else if (origin == SeekOrigin.Current)
                Position += offset;
            else if (origin == SeekOrigin.End)
                Position = Length - offset;
            return Position;
        }

        public override void SetLength(long value)
        {
            throw new NotImplementedException();
        }

        public override void Flush()
        {
            if (this.baseStream != null)
            {
                baseStream.Flush();
            }
        }

        public override void Close()
        {
            CloseStreams();
            base.Close();
        }

        /// <summary>
        /// Returns the Block size of the AES algorithm used
        /// </summary>
        /// <returns></returns>
        public int GetBlockSize()
        {
            return blockSize;
        }

        /// <summary>
        /// Returns the current Counter value
        /// </summary>
        /// <returns></returns>
        public byte [] GetCounter()
        {
            return (byte[]) counter.Clone();
        }

        /// <summary>
        /// Returns the current Block value
        /// </summary>
        /// <returns></returns>
        public long GetBlock()
        {
            return block;
        }

        /// <summary>
        /// Returns the encryption key
        /// </summary>
        /// <returns></returns>
        public byte[] GetKey()
        {
            return key;
        }

        /// <summary>
        /// Returns the HMAC key
        /// </summary>
        /// <returns></returns>
        public byte[] GetHmacKey()
        {
            return hmacKey;
        }

        /// <summary>
        /// Returns the initial vector
        /// </summary>
        /// <returns></returns>
        public byte [] GetNonce()
        {
            return nonce;
        }

        /// <summary>
        /// Returns the Chunk size used to apply HMAC signature
        /// </summary>
        /// <returns></returns>
        public int GetChunkSize()
        {
            return chunkSize;
        }

        /// <summary>
        /// Returns True if the stream has integrity enabled
        /// </summary>
        /// <returns></returns>
        internal bool GetIntegrity()
        {
            return integrity;
        }


        /// <summary>
        /// Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
        /// This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
        /// You should only use this setting for initial encryption with parallel streams and not for overwriting!
        /// </summary>
        /// <param name="value">True to allow byte range encryption write operations</param>
        public void SetAllowRangeWrite(bool value)
        {
            this.allowRangeWrite = value;
        }

        /// <summary>
        /// Set to true if don't want to throw Exceptions if you are using integrity and the are mismatches during read operations.
        /// The stream will anyway return zero for the read.
        /// </summary>
        /// <param name="value"></param>
        public void SetFailSilently(bool value)
        {
            this.failSilently = value;
        }

        /// <summary>
        /// Sets the Virtual Position of the stream excluding the header and HMAC signatures.
        /// </summary>
        /// <param name="value"></param>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void SetVirtualPosition(long value)
        {
            // we skip the header bytes and any hmac values we have if the file has integrity
            baseStream.Position = value;
            baseStream.Position += GetTotalHMACBytesFrom(baseStream.Position, 0);
            baseStream.Position += GetHeaderLength();
            ResetCounter();
            SyncCounter();
        }

        /// <summary>
        /// Returns the Virtual Position of the stream excluding the header and HMAC signatures
        /// </summary>
        /// <returns></returns>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private long GetVirtualPosition()
        {
            int hmacOffset = chunkSize>0?SalmonGenerator.GetHmacResultLength():0;
            long totalHMACBytes = GetTotalHMACBytesFrom(baseStream.Position, hmacOffset);
            return baseStream.Position - GetHeaderLength() - totalHMACBytes;
        }

        /// <summary>
        /// Close base stream
        /// </summary>
        private void CloseStreams()
        {
            if (baseStream != null)
            {
                if (CanWrite)
                    baseStream.Flush();
                baseStream.Close();
            }
        }

        /// <summary>
        /// Resets the Counter and the block count.
        /// </summary>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void ResetCounter()
        {
            counter = new byte[GetBlockSize()];
            if(nonce.Length == 12)
                Array.Copy(nonce, 0, counter, 4, nonce.Length);
            else
                Array.Copy(nonce, 0, counter, 0, nonce.Length);
            block = 0;
        }

        /// <summary>
        /// Syncs the Counter based on what AES block position the stream is at.
        /// The block count is alredy excluding the header and the HMAC signatures.
        /// </summary>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void SyncCounter()
        {
            long currBlock = Position / blockSize;
            IncrementCounter(currBlock - block);
            block = currBlock;

        }

        /// <summary>
        /// Increment the Counter
        /// We use little endianness eventhough it does not matter for AES
        /// </summary>
        /// <param name="value"></param>
        /// TODO: throw Exception when 4 lower bytes overflow
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private void IncrementCounter(long value)
        {
            if (value < 0)
                throw new Exception("Value should be positive");
            int index = 0;
            int carriage = 0;
            while (value != 0 || carriage != 0)
            {
                if (index >= sizeof(int))
                    throw new Exception("Curent CTR max blocks exceeded");

                long val = (value + carriage) % 256;
                carriage = (int)((counter[index] + val) / 256);
                counter[index++] += (byte)val;
                value /= 256;
            }
        }

        /// <summary>
        /// Return the number of bytes that all HMAC signatures occupy for each chunk size
        /// </summary>
        /// <param name="count">Actual length of the real data int the base stream including heder and HMAC signatures.</param>
        /// <param name="hmacOffset">The HMAC key length</param>
        /// <returns>The number of bytes all HMAC signatures occupy</returns>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private long GetTotalHMACBytesFrom(long count, int hmacOffset)
        {
            if (chunkSize <= 0)
                return 0;
            return SalmonIntegrity.GetTotalHMACBytesFrom(count, chunkSize, hmacOffset);
        }

        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        protected internal long GetAlignedVirtualPosition()
        {
            // if we want to apply or verify integrity with HMAC then we align our start position
            if (chunkSize>0)
                return Position / chunkSize * chunkSize;
            // if we don't use integrity we can align to the AES block size
            return Position / blockSize * blockSize;
        }


        /// <summary> 
        /// Decrypts the data from the baseStream and stores them in the buffer provided.
        /// </summary>
        /// <param name="buffer">The buffer that the data will be stored after decryption</param>
        /// <param name="offset">The start position on the buffer that data will be stored</param>
        /// <param name="count">The requested count of the data bytes that should be decrypted</param>
        /// <returns>The number of data bytes that were decrypted</returns>
        public override int Read(byte[] buffer, int offset, int count)
        {


#if ENABLE_TIMING
                long start = SalmonTime.CurrentTimeMillis();
#if ENABLE_TIMING_DETAIL
                // you can always run a profiler to get realistic results
                Console.WriteLine("SalmonStream Read() byte range from: " + Position + ", length: " + count);
                long totalTransformTime = 0;
                long totalVerifyTime = 0;
#endif
#endif

            if (baseStream.Position >= baseStream.Length)
                return 0;
            count = Math.Min(buffer.Length, count);
            int length = blockSize;
            int totalBytesRead = 0;
            long dataStart = Position;
            int blockOffset = (int)(Position % blockSize);
            int bytesRead = 0;
            Position = GetAlignedVirtualPosition();

            // eventhough we can use BufferedStream for the baseStream for performance
            // we bring the whole data set to memory by setting up a temp buffer
            // including all the integrity chunks involved in the byte range
            int bytesAvail = 0;
            byte[] cacheReadBuffer = GetCacheReadBuffer(dataStart, count, ref bytesAvail);
            int chunkToBlockOffset = (int)(dataStart - Position) - blockOffset;
            // check integrity and get the actual available bytes ignoring the hmac signatures
            if (integrity)
            {
#if ENABLE_TIMING_DETAIL
                    long startVerify = SalmonTime.CurrentTimeMillis();
#endif
                bytesAvail = SalmonIntegrity.VerifyHMAC(cacheReadBuffer, bytesAvail, hmacKey, chunkSize,
                    GetVirtualPosition() == 0 ? headerData : null);
#if ENABLE_TIMING_DETAIL
                    totalVerifyTime += (SalmonTime.CurrentTimeMillis() - startVerify);
#endif
                if (bytesAvail < 0)
                {
                    if (failSilently)
                        return 0;
                    throw new SalmonIntegrityException("File is corrupt or tampered!");
                }

            }

            MemoryStream ms = new MemoryStream(cacheReadBuffer);
            Position = dataStart;

            // to get the bytes that are the actual data we remove the offsets
            bytesAvail -= blockOffset;
            bytesAvail -= chunkToBlockOffset;

#if DEBUG
            //Array.Clear(buffer, 0, buffer.Length);
#endif
            for (int i = 0; i < count && i < bytesAvail; i += bytesRead)
            {
                // if we have integrity enabled  we skip the hmac header
                // to arrive at the beginning of our chunk
                if (chunkSize>0 && ms.Position % (chunkSize + SalmonGenerator.GetHmacResultLength()) == 0)
                {
                    ms.Seek(SalmonGenerator.GetHmacResultLength(), SeekOrigin.Current);
                }
                // now we skip the data prior to our block within that chunk
                // this should happen only at the first time so we have to reset
                if (chunkSize > 0)
                {
                    ms.Seek(chunkToBlockOffset, SeekOrigin.Current);
                    chunkToBlockOffset = 0;
                }
                // we also skip the data within the block so we are now at the beginning of the
                // data we want to read
                if (blockOffset > 0)
                    ms.Seek(blockOffset, SeekOrigin.Current);


                // we calculate the length of the data we need to read
                if (bytesAvail - totalBytesRead < blockSize - blockOffset)
                    length = bytesAvail - totalBytesRead;
                else
                    length = blockSize - blockOffset;

                if (length > count - totalBytesRead)
                    length = count - totalBytesRead;

#if DEBUG
                //Array.Clear(blockData, 0, blockData.Length);
#endif
                bytesRead = ms.Read(blockData, 0, length);
                if (bytesRead == 0)
                    break;

#if ENABLE_TIMING_DETAIL
                    long startTransform = SalmonTime.CurrentTimeMillis();
#endif
                aesTransformer.TransformBlock(counter, 0, counter.Length, encCounter, 0);
#if ENABLE_TIMING_DETAIL
                    totalTransformTime += (SalmonTime.CurrentTimeMillis() - startTransform);
#endif
                // xor the plain text with the encrypted counter
                for (int k = 0; k < length && k < bytesRead && i + k < bytesAvail; k++)
                {
                    buffer[i + k + offset] = (byte)(blockData[k] ^ encCounter[k + blockOffset]);
                    totalBytesRead++;
                }

                // XXX: since we have read all the data from the stream already
                // we have to increment the counter
                if (blockOffset + bytesRead >= blockSize)
                    IncrementCounter(1);

                // reset the blockOffset
                blockOffset = 0;
            }
            ms.Close();
            SetVirtualPosition(dataStart + totalBytesRead);

#if ENABLE_TIMING
#if ENABLE_TIMING_DETAIL
                Console.WriteLine("SalmonStream Read() AES 256 Transform: " + totalBytesRead + " bytes in: " + totalTransformTime + " ms");
                Console.WriteLine("SalmonStream Read() HMAC SHA256 Verify: " + totalBytesRead + " bytes in: " + totalVerifyTime + " ms");
#endif
                long total = SalmonTime.CurrentTimeMillis() - start;
                Console.WriteLine("SalmonStream Read() Grand Total: " + totalBytesRead + " bytes in: " + total + " ms"
                + ", avg speed: " + totalBytesRead / (float)total + " bytes/sec");
#endif

            return totalBytesRead;

        }


        /// <summary>
        /// Retrieves the data from base stream that will be used to decrypt.
        /// If you enabled integrity this will also include the HMAC signatures for each chunk size.
        /// </summary>
        /// <param name="start">The virtual position on the stream excluding the length of the header and the hmac signatures</param>
        /// <param name="count">The length of the data that will be retrieved excluding the length of the header and the hmac signatures</param>
        /// <param name="bytesAvail">The length of the data in the stream that are available excluding the length of the header and the hmac signatures</param>
        /// <returns></returns>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private byte[] GetCacheReadBuffer(long start, int count, ref int bytesAvail)
        {
#if ENABLE_TIMING_DETAIL
                long startTime = SalmonTime.CurrentTimeMillis();
#endif
            // by default our count should also include the start bytes of the block
            // if any
            long currPosition = Position;
            int actualCount = (int)(start - Position) + count;
            if (chunkSize>0)
            {
                //if (dataStart % chunkSize != 0)
                //    throw new SalmonIntegrityException("Partial reads with integrity should be aligned to the chunk size: " + chunkSize);

                //if there are any bytes in the tail we read up to the chunk that contain them
                // these extra bytes that are not requested will only be used to match the HMAC
                if (actualCount > chunkSize && actualCount % chunkSize != 0)
                    actualCount = actualCount / chunkSize * chunkSize + chunkSize;
                if (actualCount < chunkSize && actualCount % chunkSize != 0)
                    actualCount = chunkSize;

                // now we need to get all the hmac header.
                // to do this we calculate the total length of the hmac footers
                // and we add it to the virtual count
                int chunks = actualCount / chunkSize;
                if (actualCount != 0 && actualCount % chunkSize != 0)
                    chunks++;
                actualCount += chunks * SalmonGenerator.GetHmacResultLength();
            }

            byte[] cacheBuffer = new byte[actualCount];
            bytesAvail = baseStream.Read(cacheBuffer, 0, actualCount);
            Position = currPosition;
#if ENABLE_TIMING_DETAIL
                long total = SalmonTime.CurrentTimeMillis() - startTime;
                Console.WriteLine("SalmonStream Read Cache: " + bytesAvail + " bytes in: " + total + " ms");
#endif
            return cacheBuffer;
        }


        /// <summary> 
        /// Encrypts/Decrypts the data from the buffer and writes the result to the baseStream.
        /// If you are using integrity you will need to align all writes to the chunk size.
        /// </summary>
        /// <param name="buffer">The buffer that contains the data that will be encrypted</param>
        /// <param name="offset">The offset that encryption will start from</param>
        /// <param name="count">The length of the bytes that will be encrypted</param>
        public override void Write(byte[] buffer, int offset, int count)
        {
#if ENABLE_TIMING
                long start = SalmonTime.CurrentTimeMillis();
#if ENABLE_TIMING_DETAIL
                // you can always run a profiler to get realistic results
                Console.WriteLine("SalmonStream Write() byte range from: " + Position + ", length: " + count);
                long totalTransformTime = 0;
                long totalSignTime = 0;
#endif
#endif
            count = Math.Min(buffer.Length - offset, count);
            byte[] cacheWriteBuffer = GetCacheWriteBuffer(count);
            int length = blockSize;
            int totalBytesWritten = 0;
            int blockOffset = (int)(Position % blockSize);
            long currPosition = GetVirtualPosition();

            // now align to the block for the encryption phase
            SetVirtualPosition(GetVirtualPosition() / blockSize * blockSize);


            int hmacSectionOffset = 0;

            for (int i = 0; i < count; i += length)
            {
                if (count - totalBytesWritten < blockSize - blockOffset)
                    length = count - totalBytesWritten;
                else
                    length = blockSize - blockOffset;

#if ENABLE_TIMING_DETAIL
                    long startTransform = SalmonTime.CurrentTimeMillis();
#endif
                aesTransformer.TransformBlock(counter, 0, counter.Length, encCounter, 0);
#if ENABLE_TIMING_DETAIL
                    totalTransformTime += (SalmonTime.CurrentTimeMillis() - startTransform);
#endif

                // adding a placeholder for hmac
                if (integrity && i % chunkSize == 0)
                    hmacSectionOffset += SalmonGenerator.GetHmacResultLength();

                // xor the plain text with the encrypted counter
                for (int k = 0; k < length; k++)
                    cacheWriteBuffer[i + k + hmacSectionOffset] = (byte)(buffer[i + k + offset] ^ encCounter[k + blockOffset]);

                totalBytesWritten += length;

                // The counter is positioned automatically by the set Position property
                // but since we haven't written the data to the stream yet we have to
                // increment the counter manually
                if (length + blockOffset >= blockSize)
                    IncrementCounter(1);

                blockOffset = 0;
            }

            // reset the position before we write
            SetVirtualPosition(currPosition);

            // write hmac signatures for all the chunk sizes
            if (integrity)
            {
#if ENABLE_TIMING_DETAIL
                    long startSign = SalmonTime.CurrentTimeMillis();
#endif
                SalmonIntegrity.ApplyHMAC(cacheWriteBuffer, cacheWriteBuffer.Length, chunkSize, hmacKey,
                    GetVirtualPosition() == 0 ? headerData : null);
#if ENABLE_TIMING_DETAIL
                    totalSignTime += (SalmonTime.CurrentTimeMillis() - startSign);
#endif
            }

            // finally write to output stream
            baseStream.Write(cacheWriteBuffer, 0, cacheWriteBuffer.Length);


#if ENABLE_TIMING
#if ENABLE_TIMING_DETAIL
                Console.WriteLine("SalmonStream Write() AES Transform: " + totalBytesWritten + " bytes in: " + totalTransformTime + " ms");
                Console.WriteLine("SalmonStream Read() HMAC SHA256 Sign: " + totalBytesWritten + " bytes in: " + totalSignTime + " ms");
#endif
                long total = SalmonTime.CurrentTimeMillis() - start;
                Console.WriteLine("SalmonStream Write() Grand Total: " + totalBytesWritten + " bytes in: " + total + " ms"
                + ", avg speed: " + totalBytesWritten / (float)total + " bytes/sec");
#endif


        }

        /// <summary>
        /// Generates a byte array that will be used to store the encrypted data. 
        /// If you have enabled integrity it will also factor in all the HMAC signatures for each chunk.
        /// </summary>
        /// <param name="count"></param>
        /// <returns></returns>
        [MethodImpl(MethodImplOptions.AggressiveInlining)]
        private byte[] GetCacheWriteBuffer(int count)
        {
#if ENABLE_TIMING_DETAIL
                long start = SalmonTime.CurrentTimeMillis();
#endif
            // by default our count should also include the start bytes of the block
            // if any
            int actualCount = count;
            if (integrity)
            {
                if (Position % chunkSize != 0)
                    throw new SalmonIntegrityException("Partial writes with integrity should use a buffer size multiple of the chunk size: " + chunkSize);

                // we need to get all the data plus the hmac header.
                // to do this we calculate the total length of the hmac headers
                // and we add it to the virtual count
                int chunks = count / chunkSize;
                if (count != 0 && count % chunkSize != 0)
                    chunks++;
                actualCount = count + chunks * SalmonGenerator.GetHmacResultLength();
            }

            byte[] cacheBuffer = new byte[actualCount];
#if ENABLE_TIMING_DETAIL
                long total = SalmonTime.CurrentTimeMillis() - start;
                Console.WriteLine("SalmonStream Write Cache: " + actualCount + " bytes in: " + total + " ms");
#endif
            return cacheBuffer;
        }

    }
}
