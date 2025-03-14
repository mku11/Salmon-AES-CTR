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
using System;
using System.IO;

namespace Mku.Salmon.Transform;

/// <summary>
///  Abstract class for AES256 transformer implementations.
/// </summary>
public abstract class AESCTRTransformer : ICTRTransformer
{

    /// <summary>
    ///  Standard expansion key size for AES256 only.
    /// </summary>
    public static readonly int EXPANDED_KEY_SIZE = 240;

    /// <summary>
    ///  Salmon stream encryption block size, same as AES.
    /// </summary>
    public static readonly int BLOCK_SIZE = 16;

    /// <summary>
    ///  Key to be used for AES transformation.
    /// </summary>
    public byte[] Key { get; private set; }

    /// <summary>
    /// Expanded key.
    /// </summary>
    public byte[] ExpandedKey { get; protected set; } = new byte[EXPANDED_KEY_SIZE];

    /// <summary>
    ///  Nonce to be used for CTR mode.
    /// </summary>
    public byte[] Nonce { get; private set; }

    /// <summary>
    ///  Current operation block.
    /// </summary>
    public long Block { get; private set; } = 0;

    /// <summary>
    ///  Current operation counter.
    /// </summary>
    public byte[] Counter { get; private set; }

    /// <summary>
    ///  Resets the Counter and the block count.
    /// </summary>
    public void ResetCounter()
    {
		if (this.Nonce == null)
            throw new SecurityException("No counter, run init first");
        Counter = new byte[BLOCK_SIZE];
        Array.Copy(Nonce, 0, Counter, 0, Nonce.Length);
        Block = 0;
    }

    /// <summary>
    ///  Syncs the Counter based on what AES block position the stream is at.
    ///  The block count is already excluding the header and the hash signatures.
    /// </summary>
    public void SyncCounter(long position)
    {
        long currBlock = position / BLOCK_SIZE;
        ResetCounter();
        IncreaseCounter(currBlock);
        Block = currBlock;
    }

    /// <summary>
    ///  Increase the Counter
    ///  We use only big endianness for AES regardless of the machine architecture
	/// </summary>
	///  <param name="value">value to increase counter by</param>
    protected void IncreaseCounter(long value)
    {
		if (this.Counter == null || this.Nonce == null)
            throw new SecurityException("No counter, run init first");
        if (value < 0)
            throw new ArgumentOutOfRangeException("Value should be positive");
        int index = BLOCK_SIZE - 1;
        int carriage = 0;
        while (index >= 0 && value + carriage > 0)
        {
            if (index <= BLOCK_SIZE - Generator.NONCE_LENGTH)
                throw new RangeExceededException("Current CTR max blocks exceeded");
            long val = (value + carriage) % 256;
            carriage = (int)(((Counter[index] & 0xFF) + val) / 256);
            Counter[index--] += (byte)val;
            value /= 256;
        }
    }

    /// <summary>
    ///  Initialize the transformer. Most common operations include precalculating expansion keys or
    ///  any other prior initialization for efficiency.
	/// </summary>
	///  <param name="key">The key</param>
    ///  <param name="nonce">The nonce</param>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public virtual void Init(byte[] key, byte[] nonce)
    {
        this.Key = key;
        this.Nonce = nonce;
    }

    /// <summary>
    ///  Encrypt the data.
	/// </summary>
	///  <param name="srcBuffer">The source byte array.</param>
    ///  <param name="srcOffset">The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte array.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">The number of bytes to transform.</param>
    ///  <returns>The number of bytes transformed.</returns>
    public abstract int EncryptData(byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count);

    /// <summary>
    ///  Decrypt the data.
	/// </summary>
	///  <param name="srcBuffer">The source byte array.</param>
    ///  <param name="srcOffset">The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte array.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">The number of bytes to transform.</param>
    ///  <returns>The number of bytes transformed.</returns>
    public abstract int DecryptData(byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count);
}
