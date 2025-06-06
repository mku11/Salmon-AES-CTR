
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

using Mku.Salmon.Streams;
using Mku.Salmon.Transform;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Mku.Salmon.Integrity;

/// <summary>
///  Provide operations for calculating, storing, and verifying data integrity.
///  This class operates in chunks of data in buffers calculating the hash for each one.
/// </summary>
public class Integrity
{
    /// <summary>
    ///  Default chunk size for integrity.
    /// </summary>
    public static readonly int DEFAULT_CHUNK_SIZE = 256 * 1024;

    /// <summary>
    ///  The chunk size to be used for integrity.
    /// </summary>
    public int ChunkSize { get; private set; } = -1;

    /// <summary>
    ///  Key to be used for integrity signing and validation.
    /// </summary>
    public byte[] Key { get; private set; }

    /// <summary>
    ///  Hash result size;
    /// </summary>
    private readonly int hashSize;

    /// <summary>
    ///  The hash provider.
    /// </summary>
    private readonly IHashProvider provider;

    private readonly bool integrity;

    /// <summary>
    ///  Instantiate an object to be used for applying and verifying hash signatures for each of the data chunks.
	/// </summary>
	///  <param name="integrity">True to enable integrity checks.</param>
    ///  <param name="key">      The key to use for hashing.</param>
    ///  <param name="chunkSize">The chunk size. Use 0 to enable integrity on the whole file (1 chunk).</param>
    ///                   Use a positive number to specify integrity chunks.
    ///  <param name="provider"> Hash implementation provider.</param>
    ///  <param name="hashSize">The hash size.</param>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public Integrity(bool integrity, byte[] key, int chunkSize,
                           IHashProvider provider, int hashSize)
    {
        if (chunkSize < 0 || (chunkSize > 0 && chunkSize < AESCTRTransformer.BLOCK_SIZE)
                || (chunkSize > 0 && chunkSize % AESCTRTransformer.BLOCK_SIZE != 0))
        {
            throw new IntegrityException("Invalid chunk size, specify zero for default value or a positive number multiple of: "
                    + AESCTRTransformer.BLOCK_SIZE);
        }
        if (integrity && key == null)
            throw new SecurityException("You need a hash to use with integrity");
        if (integrity && chunkSize == 0)
            this.ChunkSize = DEFAULT_CHUNK_SIZE;
        else if (integrity || chunkSize > 0)
            this.ChunkSize = (int)chunkSize;
        if (hashSize < 0)
            throw new SecurityException("Hash size should be a positive number");
        this.Key = key;
        this.provider = provider;
        this.integrity = integrity;
        this.hashSize = hashSize;
    }

    /// <summary>
    ///  Calculate hash of the data provided.
	/// </summary>
	///  <param name="provider">   Hash implementation provider.</param>
    ///  <param name="buffer">     Data to calculate the hash.</param>
    ///  <param name="offset">     Offset of the buffer that the hashing calculation will start from</param>
    ///  <param name="count">      Length of the buffer that will be used to calculate the hash.</param>
    ///  <param name="key">        Key that will be used</param>
    ///  <param name="includeData">Additional data to be included in the calculation.</param>
    ///  <returns>The hash.</returns>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public static byte[] CalculateHash(IHashProvider provider, byte[] buffer, int offset, int count,
                                       byte[] key, byte[] includeData)
    {

        byte[] finalBuffer = buffer;
        int finalOffset = offset;
        int finalCount = count;
        if (includeData != null)
        {
            finalBuffer = new byte[count + includeData.Length];
            finalCount = count + includeData.Length;
            Array.Copy(includeData, 0, finalBuffer, 0, includeData.Length);
            Array.Copy(buffer, offset, finalBuffer, includeData.Length, count);
            finalOffset = 0;
        }
        byte[] hashValue = provider.Calc(key, finalBuffer, finalOffset, finalCount);
        return hashValue;
    }

    /// <summary>
    /// Get the total number of bytes for all hash signatures for data of a specific length.
    /// </summary>
    /// <param name="mode">The encryption mode</param>
    /// <param name="length">The length of the data</param>
    ///  <param name="chunkSize">     The byte size of the stream chunk that will be used to calculate the hash.</param>
    ///                        The length should be fixed value except for the last chunk which might be lesser since we don't use padding
    ///  <param name="hashOffset">    The hash key length that will be used as an offset</param>
    ///  <param name="hashLength">    The hash length.</param>
    ///  <returns>The total hash length</returns>
    public static long GetTotalHashDataLength(EncryptionMode mode, long length, int chunkSize,
                                              int hashOffset, int hashLength)
    {
        if (mode == EncryptionMode.Decrypt)
        {
            int chunks = (int)Math.Floor(length / (double)(chunkSize + hashOffset));
            int rem = (int)(length % (chunkSize + hashOffset));
            if (rem > hashOffset)
                chunks++;
            return (long)chunks * hashLength;
        }
        else
        {
            int chunks = (int)Math.Floor(length / (double)chunkSize);
            int rem = (int)(length % chunkSize);
            if (rem > hashOffset)
                chunks++;
            return (long)chunks * hashLength;
        }
    }

    /// <summary>
    ///  Return the number of bytes that all hash signatures occupy for each chunk size
	/// </summary>
	///  <param name="count">     Actual length of the real data int the base stream including header and hash signatures.</param>
    ///  <param name="hashOffset">The hash key length</param>
    ///  <returns>The number of bytes all hash signatures occupy</returns>
    public long GetHashDataLength(long count, int hashOffset)
    {
        if (ChunkSize <= 0)
            return 0;
        return Integrity.GetTotalHashDataLength(EncryptionMode.Decrypt, count, ChunkSize, hashOffset, hashSize);
    }

    /// <summary>
    ///  Get the integrity enabled option.
    /// </summary>
    ///  <returns>True if integrity is enabled.</returns>
    public bool UseIntegrity => integrity;

    /// <summary>
    ///  Generate a hash signatures for each data chunk.
	/// </summary>
	///  <param name="buffer">The buffer containing the data chunks.</param>
    ///  <param name="includeHeaderData">Include the header data in the first chunk.</param>
    ///  <returns>The hash signatures.</returns>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public byte[][] GenerateHashes(byte[] buffer, byte[] includeHeaderData)
    {
        if (!integrity)
            return null;
        IList<byte[]> hashes = new List<byte[]>();
        for (int i = 0; i < buffer.Length; i += ChunkSize)
        {
            int len = Math.Min(ChunkSize, buffer.Length - i);
            hashes.Add(CalculateHash(provider, buffer, i, len, Key, i == 0 ? includeHeaderData : null));
        }
        return hashes.ToArray();
    }

    /// <summary>
    ///  Get the hashes for each data chunk.
	/// </summary>
	///  <param name="buffer">The buffer that contains the data chunks.</param>
    ///  <returns>The hash signatures.</returns>
    public byte[][] GetHashes(byte[] buffer)
    {
        if (!integrity)
            return null;
        IList<byte[]> hashes = new List<byte[]>();
        for (int i = 0; i < buffer.Length; i += Generator.HASH_KEY_LENGTH + ChunkSize)
        {
            byte[] hash = new byte[Generator.HASH_KEY_LENGTH];
            Array.Copy(buffer, i, hash, 0, Generator.HASH_KEY_LENGTH);
            hashes.Add(hash);
        }
        return hashes.ToArray();
    }

    /// <summary>
    ///  Verify the buffer chunks against the hash signatures.
	/// </summary>
	///  <param name="hashes">The hashes to verify.</param>
    ///  <param name="buffer">The buffer that contains the chunks to verify the hashes.</param>
    ///  <param name="includeHeaderData">The header data to include</param>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    public void VerifyHashes(byte[][] hashes, byte[] buffer, byte[] includeHeaderData)
    {
        int chunk = 0;
        for (int i = 0; i < buffer.Length; i += ChunkSize)
        {
            int nChunkSize = Math.Min(ChunkSize, buffer.Length - i);
            byte[] hash = CalculateHash(provider, buffer, i, nChunkSize, Key, i == 0 ? includeHeaderData : null);
            for (int k = 0; k < hash.Length; k++)
            {
                if (hash[k] != hashes[chunk][k])
                {
                    throw new IntegrityException("Data corrupt or tampered");
                }
            }
            chunk++;
        }
    }
}