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

using Mku.Convert;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Utility provides nonce operations.
/// </summary>
public class SalmonNonce
{
    /// <summary>
    ///  Increase the sequential NONCE by a value of 1.
    ///  This implementation assumes that the NONCE length is 8 bytes or fewer so it can fit in a long.
	/// </summary>
	///  <param name="startNonce"></param>
    ///  <param name="endNonce"></param>
    ///  <returns></returns>
    ///  <exception cref="SalmonRangeExceededException"></exception>
    public static byte[] IncreaseNonce(byte[] startNonce, byte[] endNonce)
    {
        long nonce = BitConverter.ToLong(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        long maxNonce = BitConverter.ToLong(endNonce, 0, SalmonGenerator.NONCE_LENGTH);
        nonce++;
        if (nonce <= 0 || nonce > maxNonce)
            throw new SalmonRangeExceededException("Cannot increase nonce, maximum nonce exceeded");
        return BitConverter.ToBytes(nonce, 8);
    }

    /// <summary>
    ///  Returns the middle nonce in the provided range.
    ///  Note: This assumes the nonce is 8 bytes, if you need to increase the nonce length
    ///  then the long transient variables will not hold. In that case you will need to
    ///  override with your own implementation.
	/// </summary>
	///  <param name="startNonce">The starting nonce.</param>
    ///  <param name="endNonce">The ending nonce in the sequence.</param>
    ///  <returns>The byte array with the middle nonce.</returns>
    ///  <exception cref="SalmonSecurityException"></exception>
    public static byte[] SplitNonceRange(byte[] startNonce, byte[] endNonce)
    {
        long start = BitConverter.ToLong(startNonce, 0, SalmonGenerator.NONCE_LENGTH);
        long end = BitConverter.ToLong(endNonce, 0, SalmonGenerator.NONCE_LENGTH);
        // we reserve some nonces
        if (end - start < 256)
            throw new SalmonSecurityException("Not enough nonces left");
        return BitConverter.ToBytes(start + (end - start) / 2, SalmonGenerator.NONCE_LENGTH);
    }
}
