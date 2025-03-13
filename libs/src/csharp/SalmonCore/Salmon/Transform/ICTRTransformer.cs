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

namespace Mku.Salmon.Transform;

/// <summary>
///  Contract for the encryption/decryption transformers.
///  Note that Counter mode needs to be supported.
/// </summary>
public interface ICTRTransformer
{

    /// <summary>
    ///  Initialize the transformer.
	/// </summary>
	///  <param name="key">The AES key to use.</param>
    ///  <param name="nonce">The nonce to use.</param>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    void Init(byte[] key, byte[] nonce);

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
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    int EncryptData(byte[] srcBuffer, int srcOffset,
                    byte[] destBuffer, int destOffset, int count);

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
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    int DecryptData(byte[] srcBuffer, int srcOffset,
                    byte[] destBuffer, int destOffset, int count);

    /// <summary>
    ///  Get the current counter.
	/// </summary>
	///  <returns>The counter</returns>
    public byte[] Counter { get; }

    /// <summary>
    ///  Get the current encryption key.
	/// </summary>
	///  <returns>The key</returns>
    public byte[] Key { get; }

    /// <summary>
    ///  Get the current block.
	/// </summary>
	///  <returns>The block</returns>
    public long Block { get; }

    /// <summary>
    ///  Get the nonce (initial counter) to be used for the data.
	/// </summary>
	///  <returns>The nonce</returns>
    public byte[] Nonce { get; }

    /// <summary>
    ///  Reset the counter to the nonce (initial counter).
    /// </summary>
    void ResetCounter();

    /// <summary>
    ///  Calculate the value of the counter based on the current block. After an encryption
    ///  operation (ie sync or read) the block will be incremented. This method calculates
    ///  the Counter.
	/// </summary>
	///  <param name="position">The position to sync to</param>
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    void SyncCounter(long position);
}

