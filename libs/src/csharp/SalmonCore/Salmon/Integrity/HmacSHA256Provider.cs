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
using System;
using System.Security.Cryptography;

namespace Mku.Salmon.Integrity;

/// <summary>
///  Provides C# HMAC256 hashing features.
/// </summary>
public class HmacSHA256Provider : IHashProvider
{
    /// <summary>
    ///  Calculate HMAC SHA256 hash for a byte buffer.
	/// </summary>
	///  <param name="hashKey">The HMAC SHA256 key to use for hashing (32 bytes).</param>
    ///  <param name="buffer">The buffer to read the data from.</param>
    ///  <param name="offset">The position reading will start from.</param>
    ///  <param name="count">The count of bytes to be read.</param>
    ///  <returns>The HMAC SHA256 hash.</returns>
    ///  <exception cref="IntegrityException">thrown if the hash cannot be calculated</exception>
    public byte[] Calc(byte[] hashKey, byte[] buffer, int offset, int count)
    {

        byte[] hashValue;
        try
        {
            HMACSHA256 hmac = new HMACSHA256(hashKey);
            hashValue = hmac.ComputeHash(buffer, offset, count);
        }
        catch (Exception ex)
        {
            throw new Exception("Could not calculate HMAC", ex);
        }
        return hashValue;
    }
}
