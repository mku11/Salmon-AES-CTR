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
using Mku.Salmon.Transform;
using System;
using System.IO;

namespace Mku.Salmon.Streams;

/// <summary>
///  Stream decorator provides AES256 encryption and decryption of stream.
///  Block data integrity is also supported.
/// </summary>
public class AesStream : Stream
{
    /// <summary>
    ///  Header data embedded in the stream if available.
    /// </summary>
    private readonly Header header;

    /// <summary>
    ///  Mode to be used for this stream. This can only be set once.
    /// </summary>
    public EncryptionMode AesEncryptionMode { get; private set; }

    /// <summary>
    ///  Warning! Allow byte range encryption writes on a current stream. Overwriting is not a good idea because it will re-use the same IV.
    ///  This is not recommended if you use the stream on storing files or generally data if prior version can be inspected by others.
    ///  You should only use this setting for initial encryption with parallel streams and not for overwriting!
	/// </summary>
	///  <param name="value">True to allow byte range encryption write operations</param>
    public bool AllowRangeWrite { get; set; } = false;

    /// <summary>
    ///  Set to True if you want the stream to fail silently when integrity cannot be verified.
    ///  In that case read() operations will return -1 instead of raising an exception.
    ///  This prevents 3rd party code like media players from crashing.
	/// </summary>
	///  <param name="value">True to fail silently.</param>
    public bool FailSilently { get; set; }

    /// <summary>
    ///  The base stream. When EncryptionMode is Encrypt this will be the target stream.
    ///  When EncryptionMode is Decrypt this will be the source stream.
    /// </summary>
    private readonly Stream baseStream;

    /// <summary>
    ///  Current global AES provider type.
    /// </summary>
    public static ProviderType AesProviderType { get; set; } = ProviderType.Default;

    /// <summary>
    ///  The transformer to use for encryption.
    /// </summary>
    private ICTRTransformer transformer;

    /// <summary>
    ///  The integrity to use for hash signature creation and validation.
    /// </summary>
    private Integrity.Integrity salmonIntegrity;

    /// <summary>
    /// Default buffer size for all internal streams including Encryptors and Decryptors
    /// </summary>
    public int BufferSize { get; set; } = Integrity.Integrity.DEFAULT_CHUNK_SIZE;


    /// <summary>
    ///  Get the output size of the data to be transformed(encrypted or decrypted) including
    ///  header and hash without executing any operations. This can be used to prevent over-allocating memory
    ///  where creating your output buffers.
    /// </summary>
    ///  <param name="mode">The <see cref="EncryptionMode"/> Encrypt or Decrypt.</param>
    ///  <param name="format">The format to use, see EncryptionFormat</param>
    ///  <param name="integrity">True if you want to enable integrity.</param>
    ///  <param name="chunkSize">The chunk size for integrity chunks.</param>
    ///  <returns>The size of the output data.</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    ///  <exception cref="Integrity.IntegrityException">Thrown when data are corrupt or tampered with</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public static long GetOutputSize(EncryptionMode mode, long length, 
        EncryptionFormat format = EncryptionFormat.Salmon,
        bool integrity = false, int chunkSize = 0)
    {
        if (format == EncryptionFormat.Generic && integrity)
            throw new SecurityException("Cannot use integrity with generic format");
        if (chunkSize == 0)
            chunkSize = Integrity.Integrity.DEFAULT_CHUNK_SIZE;
        long size = length;
        if (format == EncryptionFormat.Salmon)
        {
            if (mode == EncryptionMode.Encrypt)
            {
                size += Header.HEADER_LENGTH;
                if (integrity)
                {
                    size += Integrity.Integrity.GetTotalHashDataLength(mode, length, chunkSize,
                            0, Generator.HASH_RESULT_LENGTH);
                }
            }
            else
            {
                size -= Header.HEADER_LENGTH;
                if (integrity)
                {
                    size -= Integrity.Integrity.GetTotalHashDataLength(mode, length - Header.HEADER_LENGTH, chunkSize,
                            Generator.HASH_RESULT_LENGTH, Generator.HASH_RESULT_LENGTH);
                }
            }
        }
        return size;
    }

    /// <summary>
    ///  Instantiate a new Salmon stream with a base stream and optional header data and hash integrity.
    ///  <para>
    ///  If you read from the stream it will decrypt the data from the baseStream.
    ///  If you write to the stream it will encrypt the data from the baseStream.
    ///  The transformation is based on AES CTR Mode.
    ///  </para>
    ///  Notes:
    ///  The initial value of the counter is a result of the concatenation of an 12 byte nonce and an additional 4 bytes counter.
    ///  The counter is then: incremented every block, encrypted by the key, and xored with the plain text.
    ///  <para>See: <see href="https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation#Counter_(CTR)">README.md</see></para>
	/// </summary>
    ///  <param name="key">           The AES key that is used to encrypt decrypt</param>
    ///  <param name="nonce">         The nonce used for the initial counter</param>
    ///  <param name="encryptionMode">Encryption mode Encrypt or Decrypt this cannot change later</param>
    ///  <param name="baseStream">    The base Stream that will be used to read the data</param>
    ///  <param name="format">The format to use, see EncryptionFormat</param>
    ///  <param name="integrity">     enable integrity</param>
    ///  <param name="chunkSize">     the chunk size to be used with integrity</param>
    ///  <param name="hashKey">       Hash key to be used with integrity</param>
    public AesStream(byte[] key, byte[] nonce, EncryptionMode encryptionMode,
                        Stream baseStream, EncryptionFormat format = EncryptionFormat.Salmon,
                        bool integrity = false, byte[] hashKey = null, int chunkSize = 0)
    {
        this.AesEncryptionMode = encryptionMode;
        this.baseStream = baseStream;
        this.header = GetOrCreateHeader(format, nonce, integrity, chunkSize);
        if (this.header != null)
        {
            chunkSize = header.ChunkSize;
            nonce = header.Nonce;
        }
        else
        {
            chunkSize = 0;
        }
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        InitIntegrity(integrity, hashKey, chunkSize);
        InitTransformer(key, nonce);
        InitStream();
    }

    private Header GetOrCreateHeader(EncryptionFormat format, byte[] nonce, bool integrity, int chunkSize)
    {
        if (format == EncryptionFormat.Salmon) {
            if (this.AesEncryptionMode== EncryptionMode.Encrypt) {
                if (integrity && chunkSize == 0)
                    chunkSize = Integrity.Integrity.DEFAULT_CHUNK_SIZE;
                return Header.WriteHeader(baseStream, nonce, chunkSize);
            }
            return Header.ReadHeaderData(baseStream);
        }
        return null;
    }

    /// <summary>
    ///  Provides the length of the actual transformed data (minus the header and integrity data).
	/// </summary>
	///  <returns>The length of the stream.</returns>
    override
    public long Length
    {
        get
        {
            long totalHashBytes;
            int hashOffset = salmonIntegrity.ChunkSize > 0 ? Generator.HASH_RESULT_LENGTH : 0;
            totalHashBytes = salmonIntegrity.GetHashDataLength(baseStream.Length - 1, hashOffset);

            return baseStream.Length - GetHeaderLength() - totalHashBytes;
        }
    }

    /// <summary>
    ///  Provides the position of the stream relative to the data to be transformed.
    /// </summary>
    override
    public long Position
    {
        get
        {
            return GetVirtualPosition();
        }

        set
        {
            if (CanWrite && !AllowRangeWrite && value != 0)
            {
                throw new IOException("Could not get stream position",
                        new SecurityException("Range Write is not allowed for security (non-reusable IVs). " +
                                "If you still want to take the risk you need to use SetAllowRangeWrite(true)"));
            }
            try
            {
                SetVirtualPosition(value);
            }
            catch (RangeExceededException e)
            {
                throw new IOException("Could not set position", e);
            }
        }
    }

    /// <summary>
    ///  If the stream is readable (only if EncryptionMode == Decrypted)
	/// </summary>
	///  <returns>True if mode is decryption.</returns>
    override
    public bool CanRead => baseStream.CanRead && AesEncryptionMode == EncryptionMode.Decrypt;

    /// <summary>
    ///  If the stream is seekable (supported only if base stream is seekable).
	/// </summary>
	///  <returns>True if stream is seekable.</returns>
    override
    public bool CanSeek => baseStream.CanSeek;

    /// <summary>
    ///  If the stream is writeable (only if EncryptionMode is Encrypt)
	/// </summary>
	///  <returns>True if mode is decryption.</returns>
    override
    public bool CanWrite => baseStream.CanWrite && AesEncryptionMode == EncryptionMode.Encrypt;

    /// <summary>
    ///  If the stream has integrity enabled
    /// </summary>
    public bool HasIntegrity => ChunkSize > 0;

    /// <summary>
    ///  Initialize the integrity validator. This object is always associated with the
    ///  stream because in the case of a decryption stream that has already embedded integrity
    ///  we still need to calculate/skip the chunks.
	/// </summary>
	///  <param name="integrity">True to enable integrity</param>
    ///  <param name="hashKey">The hash key</param>
    ///  <param name="chunkSize">The chunk size</param>
    private void InitIntegrity(bool integrity, byte[] hashKey, int chunkSize)
    {
        salmonIntegrity = new Integrity.Integrity(integrity, hashKey, chunkSize,
                new HmacSHA256Provider(), Generator.HASH_RESULT_LENGTH);
    }

    /// <summary>
    ///  Init the stream.
	/// </summary>
    private void InitStream()
    {
        Position = 0;
    }

    /// <summary>
    ///  The length of the header data if the stream was initialized with a header.
	/// </summary>
	///  <returns>The header data length.</returns>
    private long GetHeaderLength()
    {
        if (header == null)
            return 0;
        else
            return header.HeaderData.Length;
    }

    /// <summary>
    ///  To create the AES CTR mode we use ECB for AES with No Padding.
    ///  Initailize the Counter to the initial vector provided.
    ///  For each data block we increase the Counter and apply the EAS encryption on the Counter.
    ///  The encrypted Counter then will be xor-ed with the actual data block.
    /// </summary>
    private void InitTransformer(byte[] key, byte[] nonce)
    {
        if (key == null)
            throw new SecurityException("Key is missing");
        if (nonce == null)
            throw new SecurityException("Nonce is missing");

        transformer = TransformerFactory.Create(AesProviderType);
        transformer.Init(key, nonce);
        transformer.ResetCounter();
    }

    /// <summary>
    ///  Seek to a specific position on the stream. This does not include the header and any hash Signatures.
	/// </summary>
	///  <param name="offset">The offset that seek will use</param>
    ///  <param name="origin">If it is Begin the offset will be the absolute position from the start of the stream</param>
    ///                If it is Current the offset will be added to the current position of the stream
    ///                If it is End the offset will be the absolute position starting from the end of the stream.
    override
    public long Seek(long offset, SeekOrigin origin)
    {
        if (origin == SeekOrigin.Begin)
            Position = offset;
        else if (origin == SeekOrigin.Current)
            Position = Position + offset;
        else if (origin == SeekOrigin.End)
            Position = Length - offset;
        return Position;
    }

    /// <summary>
    ///  Set the length of the base stream. Currently unsupported.
	/// </summary>
	///  <param name="value">The new length</param>
    override
    public void SetLength(long value)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    ///  Flushes any buffered data to the base stream.
    /// </summary>
    override
    public void Flush()
    {
        if (this.baseStream != null)
        {
            baseStream.Flush();
        }
    }

    /// <summary>
    ///  Closes the stream and all resources associated with it (including the base stream).
	/// </summary>
    override
    public void Close()
    {
        CloseStreams();
    }

    /// <summary>
    ///  Returns the current Counter value.
	/// </summary>
	///  <returns>The current Counter value.</returns>
    public byte[] Counter => (byte[])transformer.Counter.Clone();

    /// <summary>
    ///  Returns the current Block value
    /// </summary>
    public long Block => transformer.Block;

    /// <summary>
    ///  Returns a copy of the encryption key.
    /// </summary>
    public byte[] Key => (byte[])transformer.Key.Clone();

    /// <summary>
    ///  Returns a copy of the hash key.
    /// </summary>
    public byte[] HashKey => (byte[])salmonIntegrity.Key.Clone();

    /// <summary>
    ///  Returns a copy of the initial vector.
    /// </summary>
    public byte[] Nonce => (byte[])transformer.Nonce.Clone();

    /// <summary>
    ///  Returns the Chunk size used to apply hash signature
    /// </summary>
    public int ChunkSize => salmonIntegrity.ChunkSize;

    /// <summary>
    ///  Set the virtual position of the stream.
	/// </summary>
	///  <param name="value">The new position</param>
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    private void SetVirtualPosition(long value)
    {
        // we skip the header bytes and any hash values we have if the file has integrity set
        long totalHashBytes = salmonIntegrity.GetHashDataLength(value, 0);
        value += totalHashBytes + GetHeaderLength();
        baseStream.Position = value;
		
        transformer.ResetCounter();
        transformer.SyncCounter(Position);
    }

    /// <summary>
    ///  Returns the Virtual Position of the stream excluding the header and hash signatures.
    /// </summary>
    private long GetVirtualPosition()
    {
        long totalHashBytes;
        int hashOffset = salmonIntegrity.ChunkSize > 0 ? Generator.HASH_RESULT_LENGTH : 0;
        totalHashBytes = salmonIntegrity.GetHashDataLength(baseStream.Position, hashOffset);
        return baseStream.Position - GetHeaderLength() - totalHashBytes;
    }

    /// <summary>
    ///  Close base stream
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
    ///  Decrypts the data from the baseStream and stores them in the buffer provided.
	/// </summary>
	///  <param name="buffer">The buffer that the data will be stored after decryption</param>
    ///  <param name="offset">The start position on the buffer that data will be written.</param>
    ///  <param name="count"> The requested count of the data bytes that should be decrypted</param>
    ///  <returns>The number of data bytes that were decrypted.</returns>
    override
    public int Read(byte[] buffer, int offset, int count)
    {
        if (Position == Length)
            return 0;
        int alignedOffset = GetAlignedOffset();
        int bytes = 0;
        long pos = Position;

        // if the base stream is not aligned for read
        if (alignedOffset != 0)
        {
            // read partially once
            Position = Position - alignedOffset;
            int nCount = salmonIntegrity.ChunkSize > 0 ? salmonIntegrity.ChunkSize : Generator.BLOCK_SIZE;
            byte[] buff = new byte[nCount];
            bytes = Read(buff, 0, nCount);
            bytes = Math.Min(bytes - alignedOffset, count);
            // if no more bytes to read from the stream
            if (bytes <= 0)
                return 0;
            Array.Copy(buff, alignedOffset, buffer, offset, bytes);
            Position = pos + bytes;

        }
        // if we have all bytes originally requested
        if (bytes == count)
            return bytes;

        // the base stream position should now be aligned
        // now we can now read the rest of the data.
        pos = Position;
        int nBytes = ReadFromStream(buffer, bytes + offset, count - bytes);
        Position = pos + nBytes;
        return bytes + nBytes;
    }

    /// <summary>
    ///  Decrypts the data from the baseStream and stores them in the buffer provided.
    ///  Use this only after you align the base stream to the chunk if integrity is enabled
    ///  or to the encryption block size.
	/// </summary>
	///  <param name="buffer">The buffer that the data will be stored after decryption</param>
    ///  <param name="offset">The start position on the buffer that data will be written.</param>
    ///  <param name="count"> The requested count of the data bytes that should be decrypted</param>
    ///  <returns>The number of data bytes that were decrypted.</returns>
    ///  <exception cref="IOException">Thrown if stream is not aligned.</exception>
    private int ReadFromStream(byte[] buffer, int offset, int count)
    {
        if (Position == Length)
            return 0;
        if (salmonIntegrity.ChunkSize > 0 && Position % salmonIntegrity.ChunkSize != 0)
            throw new IOException("All reads should be aligned to the chunks size: " + salmonIntegrity.ChunkSize);
        else if (salmonIntegrity.ChunkSize == 0 && Position % AESCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("All reads should be aligned to the block size: " + AESCTRTransformer.BLOCK_SIZE);

        long pos = Position;

        // if there are not enough data in the stream
        count = (int)Math.Min(count, Length - Position);

        // if there are not enough space in the buffer
        count = Math.Min(count, buffer.Length - offset);

        if (count <= 0)
            return 0;

        // make sure our buffer size is also aligned to the block or chunk
        int bufferSize = GetNormalizedBufferSize(true);

        int bytes = 0;
        while (bytes < count)
        {
            // read data and integrity signatures
            byte[] srcBuffer = ReadStreamData(bufferSize);
            try
            {
                byte[][] integrityHashes = null;
                // if there are integrity hashes strip them and get the data chunks only
                if (salmonIntegrity.ChunkSize > 0)
                {
                    // get the integrity signatures
                    integrityHashes = salmonIntegrity.GetHashes(srcBuffer);
                    srcBuffer = StripSignatures(srcBuffer, salmonIntegrity.ChunkSize);
                }
                byte[] destBuffer = new byte[srcBuffer.Length];
                if (salmonIntegrity.UseIntegrity)
                {
                    salmonIntegrity.VerifyHashes(integrityHashes, srcBuffer, pos == 0 && bytes == 0 ? header.HeaderData : null);
                }
                transformer.DecryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.Length);
                int len = Math.Min(count - bytes, destBuffer.Length);
                WriteToBuffer(destBuffer, 0, buffer, bytes + offset, len);
                bytes += len;
                transformer.SyncCounter(Position);
            }
            catch (Exception ex)
            {
                if (ex.GetType() == typeof(Integrity.IntegrityException) && FailSilently)
                    return -1;
                throw new IOException("Could not read from stream: ", ex);
            }
        }
        return bytes;
    }

    /// <summary>
    ///  Encrypts the data from the buffer and writes the result to the baseStream.
    ///  If you are using integrity you will need to align all write operations to the chunk size
    ///  otherwise align to the encryption block size.
    /// </summary>
    ///  <param name="buffer">The buffer that contains the data that will be encrypted</param>
    ///  <param name="offset">The offset in the buffer that the bytes will be encrypted.</param>
    ///  <param name="count"> The length of the bytes that will be encrypted.</param>
    override
    public void Write(byte[] buffer, int offset, int count)
    {
        if (salmonIntegrity.ChunkSize > 0 && Position % salmonIntegrity.ChunkSize != 0)
            throw new IOException("Could not write to stream",
                    new Integrity.IntegrityException("All write operations should be aligned to the chunks size: "
                            + salmonIntegrity.ChunkSize));
        else if (salmonIntegrity.ChunkSize == 0 && Position % AESCTRTransformer.BLOCK_SIZE != 0)
            throw new IOException("Could not write to stream",
                    new Integrity.IntegrityException("All write operations should be aligned to the block size: "
                            + AESCTRTransformer.BLOCK_SIZE));

        // if there are not enough data in the buffer
        count = Math.Min(count, buffer.Length - offset);

        // if there
        int bufferSize = GetNormalizedBufferSize(false);

        int pos = 0;
        while (pos < count)
        {
            int nBufferSize = Math.Min(bufferSize, count - pos);

            byte[] srcBuffer = ReadBufferData(buffer, pos + offset, nBufferSize);
            if (srcBuffer.Length == 0)
                break;
            byte[] destBuffer = new byte[srcBuffer.Length];
            try
            {
                transformer.EncryptData(srcBuffer, 0, destBuffer, 0, srcBuffer.Length);
                byte[][] integrityHashes = null;
                if(salmonIntegrity.UseIntegrity)
                    integrityHashes = salmonIntegrity.GenerateHashes(destBuffer, Position == 0 ? header.HeaderData : null);
                pos += WriteToStream(destBuffer, ChunkSize, integrityHashes);
                transformer.SyncCounter(Position);
            }
            catch (Exception ex)
            {
                throw new IOException("Could not write to stream: ", ex);
            }
        }
    }

    /// <summary>
    ///  Get the aligned offset wrt the Chunk size if integrity is enabled otherwise
    ///  wrt to the encryption block size. Use this method to align a position to the
    ///  start of the block or chunk.
	/// </summary>
	///  <returns>The aligned offset</returns>
    private int GetAlignedOffset()
    {
        int alignOffset;
        if (salmonIntegrity.ChunkSize > 0)
        {
            alignOffset = (int)(Position % salmonIntegrity.ChunkSize);
        }
        else
        {
            alignOffset = (int)(Position % AESCTRTransformer.BLOCK_SIZE);
        }
        return alignOffset;
    }

    /// <summary>
    ///  Get the aligned buffer size wrt the Chunk size if integrity is enabled otherwise
    ///  wrt to the encryption block size. Use this method to ensure that buffer sizes request
    ///  via the API are aligned for read/writes and integrity processing.
	/// </summary>
	///  <returns>The normalized buffer size</returns>
    private int GetNormalizedBufferSize(bool includeHashes)
    {
        int bufferSize = this.BufferSize;
        if (ChunkSize > 0)
        {
            // buffer size should be a multiple of the chunk size if integrity is enabled
            int partSize = ChunkSize;
            // if add the hash signatures

            if (partSize < bufferSize)
            {
                bufferSize = bufferSize / ChunkSize * ChunkSize;
            }
            else
                bufferSize = partSize;

            if (includeHashes)
                bufferSize += bufferSize / ChunkSize * Generator.HASH_RESULT_LENGTH;
        }
        else
        {
            // buffer size should also be a multiple of the AES block size
            bufferSize = bufferSize / AESCTRTransformer.BLOCK_SIZE
                   * AESCTRTransformer.BLOCK_SIZE;
        }

        return bufferSize;
    }

    /// <summary>
    ///  Read the data from the buffer
	/// </summary>
	///  <param name="buffer">The source buffer.</param>
    ///  <param name="offset">The offset to start reading the data.</param>
    ///  <param name="count"> The number of requested bytes to read.</param>
    ///  <returns>The array with the data that were read.</returns>
    private byte[] ReadBufferData(byte[] buffer, int offset, int count)
    {
        byte[] data = new byte[Math.Min(count, buffer.Length - offset)];
        Array.Copy(buffer, offset, data, 0, data.Length);
        return data;
    }

    /// <summary>
    ///  Read the data from the base stream into the buffer.
	/// </summary>
	///  <param name="count">The number of bytes to read.</param>
    ///  <returns>The number of bytes read.</returns>
    private byte[] ReadStreamData(int count)
    {
        byte[] data = new byte[(int)Math.Min(count, baseStream.Length - baseStream.Position)];
        int bytesRead;
        int totalBytesRead = 0;
        while ((bytesRead = baseStream.Read(data, totalBytesRead, data.Length - totalBytesRead)) > 0)
        {
            totalBytesRead += bytesRead;
        }
        return data;
    }

    /// <summary>
    ///  Write the buffer data to the destination buffer.
	/// </summary>
	///  <param name="srcBuffer"> The source byte array.</param>
    ///  <param name="srcOffset"> The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte offset.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">     The number of bytes to write.</param>
    private void WriteToBuffer(byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count)
    {
        Array.Copy(srcBuffer, srcOffset, destBuffer, destOffset, count);
    }

    /// <summary>
    ///  Write data to the base stream.
	/// </summary>
	///  <param name="buffer">   The buffer to read from.</param>
    ///  <param name="chunkSize">The chunk segment size to use when writing the buffer.</param>
    ///  <param name="hashes">   The hash signature to write at the beginning of each chunk.</param>
    ///  <returns>The number of bytes written.</returns>
    private int WriteToStream(byte[] buffer, int chunkSize, byte[][] hashes)
    {
        int pos = 0;
        int chunk = 0;
        if (chunkSize <= 0)
            chunkSize = buffer.Length;
        while (pos < buffer.Length)
        {
            if (hashes != null)
            {
                baseStream.Write(hashes[chunk], 0, hashes[chunk].Length);
            }
            int len = Math.Min(chunkSize, buffer.Length - pos);
            baseStream.Write(buffer, pos, len);
            pos += len;
            chunk++;
        }
        return pos;
    }

    /// <summary>
    ///  Strip hash signatures from the buffer.
	/// </summary>
	///  <param name="buffer">The buffer.</param>
    ///  <param name="chunkSize">The chunk size.</param>
    ///  <returns>The buffer without the hash signatures</returns>
    private byte[] StripSignatures(byte[] buffer, int chunkSize)
    {
        int bytes = buffer.Length / (chunkSize + Generator.HASH_RESULT_LENGTH) * chunkSize;
        if (buffer.Length % (chunkSize + Generator.HASH_RESULT_LENGTH) != 0)
            bytes += buffer.Length % (chunkSize + Generator.HASH_RESULT_LENGTH) - Generator.HASH_RESULT_LENGTH;
        byte[] buff = new byte[bytes];
        int index = 0;
        for (int i = 0; i < buffer.Length; i += chunkSize + Generator.HASH_RESULT_LENGTH)
        {
            int nChunkSize = Math.Min(chunkSize, buff.Length - index);
            Array.Copy(buffer, i + Generator.HASH_RESULT_LENGTH, buff, index, nChunkSize);
            index += nChunkSize;
        }
        return buff;
    }
}

