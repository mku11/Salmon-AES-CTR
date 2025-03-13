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

namespace Mku.Salmon.Bridge;

/// <summary>
///  Interface to native libraries that provide AES-256 encryption in CTR mode.
/// </summary>
public interface INativeProxy
{
    /// <summary>
    ///  Initializes the native library with the specified AES implementation.
    /// </summary>
    ///  <param name="aesImpl">The AES implementation, see {@link Mku.Salmon.Streams.ProviderType} for possible values</param>
    public void SalmonInit(int aesImpl);

    /// <summary>
    ///  Expands the specified AES encryption key.
	/// </summary>
	///  <param name="key">The AES-256 encryption key (32 bytes)</param>
    ///  <param name="expandedKey">The expanded key (240 bytes)</param>
    public void SalmonExpandKey(byte[] key, byte[] expandedKey);

    /// <summary>
    ///  Transforms data using CTR mode which is symmetric so you should use it for both encryption and decryption.
	/// </summary>
	///  <param name="key">The AES-256 encryption key (32 bytes)</param>
    ///  <param name="counter">The counter (16 bytes)</param>
    ///  <param name="srcBuffer">The source buffer</param>
    ///  <param name="srcOffset">The source offset</param>
    ///  <param name="destBuffer">The destination buffer</param>
    ///  <param name="destOffset">The destination offset</param>
    ///  <param name="count">The number of bytes to transform</param>
    ///  <returns>The number of bytes transformed</returns>
    public int SalmonTransform(byte[] key, byte[] counter,
                                     byte[] srcBuffer, int srcOffset,
                                     byte[] destBuffer, int destOffset, int count);
}