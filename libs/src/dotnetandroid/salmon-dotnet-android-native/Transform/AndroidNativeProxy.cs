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

namespace Salmon.Transform;

using Mku.Bridge;
using System.Runtime.InteropServices;

public class AndroidNativeProxy : INativeProxy
{
    /// <summary>
    /// The dll name for the Android library.
    /// </summary>
    private const string DllName = "libsalmon.so";

    /// <summary>
    ///  Init the native code with AES implementation, and hash length options.
    /// </summary>
    ///  <param name="aesImpl">       The AES implementation code.</param>
    [DllImport(DllName)]
    private extern static void salmon_init(int aesImpl);

    /// <summary>
    ///  Native Key schedule algorithm for expanding the 32 byte key to 240 bytes required
    ///  for AES 256.
    /// </summary>
    ///  <param name="key">The AES256 key (32 bytes).</param>
    ///  <param name="expandedKey">The expanded key (240 bytes).</param>
    [DllImport(DllName)]
    private extern static void salmon_expandKey(byte[] key, byte[] expandedKey);

    /// <summary>
    ///  Native transform of the input byte array using AES 256 encryption or decryption mode.
    /// </summary>
    ///  <param name="key">The key</param>
    ///  <param name="counter">The counter</param>
    ///  <param name="srcBuffer">The source buffer</param>
    ///  <param name="srcOffset">The source offset</param>
    ///  <param name="destBuffer">The destination buffer</param>
    ///  <param name="destOffset">The destination offset</param>
    ///  <param name="count">The number of bytes to transform</param>
    ///  <returns>The number of bytes transformed</returns>
    [DllImport(DllName)]
    private extern static int salmon_transform(byte[] key, byte[] counter,
                                     byte[] srcBuffer, int srcOffset,
                                     byte[] destBuffer, int destOffset, int count);


    /// <summary>
    ///  Proxy Init the native code with AES implementation, and hash length options.
    /// </summary>
    ///  <param name="aesImpl">       The AES implementation code.</param>
    public void SalmonInit(int aesImpl)
    {
        salmon_init(aesImpl);
    }

    /// <summary>
    ///  Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
    ///  for AES 256.
    /// </summary>
    ///  <param name="key">The AES256 key (32 bytes).</param>
    ///  <param name="expandedKey">The expanded key (240 bytes).</param>
    public void SalmonExpandKey(byte[] key, byte[] expandedKey)
    {
        salmon_expandKey(key, expandedKey);
    }

    /// <summary>
    ///  Proxy Transform the input byte array using AES 256 using encryption or decryption mode.
    /// </summary>
    ///  <param name="key">The key</param>
    ///  <param name="counter">The counter</param>
    ///  <param name="srcBuffer">The source buffer</param>
    ///  <param name="srcOffset">The source offset</param>
    ///  <param name="destBuffer">The destination buffer</param>
    ///  <param name="destOffset">The destination offset</param>
    ///  <param name="count">The number of bytes to transform</param>
    ///  <returns>The number of bytes transformed</returns>
    public int SalmonTransform(byte[] key, byte[] counter, byte[] srcBuffer, int srcOffset, byte[] destBuffer, int destOffset, int count)
    {
        return salmon_transform(key, counter, srcBuffer, srcOffset, destBuffer, destOffset, count);
    }
}