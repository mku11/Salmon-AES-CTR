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
using System.Security.Cryptography;

namespace Mku.Salmon.Transform;

/// <summary>
///  Salmon AES transformer based on c# System.Security.Cryptography
/// </summary>
public class DefaultTransformer : AESCTRTransformer
{

    /// <summary>
    ///  Default .NET AES transformer
    /// </summary>
    private ICryptoTransform aesTransformer;

    /// <summary>
    ///  Initialize the default c# AES transformer.
	/// </summary>
	///  <param name="key">The AES256 key to use.</param>
    ///  <param name="nonce">The nonce to use.</param>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    override
    public void Init(byte[] key, byte[] nonce)
    {
        base.Init(key, nonce);
        try
        {
            Aes aes = Aes.Create();
            aes.Mode = CipherMode.ECB;
            aes.Padding = PaddingMode.None;
            var zeroIv = new byte[AESCTRTransformer.BLOCK_SIZE];
            // we just initialize with a zero IV since we'll implement a custom CTR
            aesTransformer = aes.CreateEncryptor(key, zeroIv);
        }
        catch (Exception e)
        {
            throw new SecurityException("Could not init AES transformer", e);
        }
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
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    override
    public int EncryptData(byte[] srcBuffer, int srcOffset,
                           byte[] destBuffer, int destOffset, int count)
    {
		if (this.aesTransformer == null)
            throw new SecurityException("No key defined, run init first");
        try
        {
            return Transform(srcBuffer, srcOffset, destBuffer, destOffset, count);
        }
        catch (Exception ex)
        {
            throw new SecurityException("Could not encrypt data: ", ex);
        }
    }


    /// <summary>
    ///  Decrypt the data.
	/// </summary>
	///  <param name="srcBuffer">The source byte array.</param>
    ///  <param name="srcOffset">The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte array.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">The number of bytes to transform.</param>
    ///  <returns>The number of bytes transformed.</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    override
    public int DecryptData(byte[] srcBuffer, int srcOffset,
                            byte[] destBuffer, int destOffset, int count)
    {
		if (this.aesTransformer == null)
            throw new SecurityException("No key defined, run init first");
        try
        {
            return Transform(srcBuffer, srcOffset, destBuffer, destOffset, count);
        }
        catch (Exception ex)
        {
            throw new SecurityException("Could not decrypt data: ", ex);
        }
    }
	
    private int Transform(byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count)
    {
        byte[] encCounter = new byte[Counter.Length];
        int totalBytes = 0;
        for (int i = 0; i < count; i += AESCTRTransformer.BLOCK_SIZE)
        {
            aesTransformer.TransformBlock(Counter, 0, Counter.Length, encCounter, 0);
            // xor the plain text with the encrypted counter
            for (int k = 0; k < AESCTRTransformer.BLOCK_SIZE && i + k < count; k++)
            {
                destBuffer[destOffset + i + k] = (byte)(srcBuffer[srcOffset + i + k] ^ encCounter[k]);
                totalBytes++;
            }
            IncreaseCounter(1);
        }

        return totalBytes;
    }
}
