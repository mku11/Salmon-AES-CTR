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

using System.Security.Cryptography;

namespace Mku.Salmon;

/// <summary>
///  Utility class generates internal secure properties.
/// </summary>
public class SalmonGenerator
{
    /// <summary>
    ///  Version.
    /// </summary>
    public static readonly byte VERSION = 2;

    /// <summary>
    ///  Lenght for the magic bytes.
    /// </summary>
    public static readonly int MAGIC_LENGTH = 3;

    /// <summary>
    ///  Length for the Version in the data header.
    /// </summary>
    public static readonly int VERSION_LENGTH = 1;

    /// <summary>
    ///  Should be 16 for AES256 the same as the iv.
    /// </summary>
    public static readonly int BLOCK_SIZE = 16;

    /// <summary>
    ///  Encryption key length for AES256.
    /// </summary>
    public static readonly int KEY_LENGTH = 32;

    /// <summary>
    ///  HASH Key length for integrity, currently we use HMAC SHA256.
    /// </summary>
    public static readonly int HASH_KEY_LENGTH = 32;

    /// <summary>
    ///  Hash signature size for integrity, currently we use HMAC SHA256.
    /// </summary>
    public static readonly int HASH_RESULT_LENGTH = 32;

    /// <summary>
    ///  Nonce size.
    /// </summary>
    public static readonly int NONCE_LENGTH = 8;

    /// <summary>
    ///  Chunk size format length.
    /// </summary>
    public static readonly int CHUNK_SIZE_LENGTH = 4;

    /// <summary>
    ///  Magic bytes.
    /// </summary>
    private static readonly string MAGIC_BYTES = "SLM";

    /// <summary>
    /// Gets the fixed magic bytes array
    /// </summary>
    public static byte[] GetMagicBytes()
    {
        return System.Text.UTF8Encoding.Default.GetBytes(MAGIC_BYTES);
    }

    /// <summary>
    ///  Returns a secure random byte array. To be used when generating keys, initial vectors, and nonces.
	/// </summary>
	///  <param name="size">The size of the byte array.</param>
    ///  <returns>The random secure byte array.</returns>
    public static byte[] GetSecureRandomBytes(int size)
    {
        RandomNumberGenerator rand = RandomNumberGenerator.Create();
        byte[] bytes = new byte[size];
        rand.GetNonZeroBytes(bytes);
        return bytes;
    }
}

