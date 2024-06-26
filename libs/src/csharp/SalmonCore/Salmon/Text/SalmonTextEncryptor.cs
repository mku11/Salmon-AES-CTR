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

using Mku.Salmon.Encode;
using Mku.Salmon.Integrity;
using System.IO;

namespace Mku.Salmon.Text;

/// <summary>
///  Utility class that encrypts and decrypts text strings.
/// </summary>
public class SalmonTextEncryptor
{
    private static readonly SalmonEncryptor encryptor = new SalmonEncryptor();

    /// <summary>
    ///  Encrypts a text string using AES256 with the key and nonce provided.
    /// </summary>
    ///  <param name="text"> Text to be encrypted.</param>
    ///  <param name="key">  The encryption key to be used.</param>
    ///  <param name="nonce">The nonce to be used.</param>
    ///  <param name="header">Set to true to store a header with information like nonce and/or chunk size
    ///                otherwise you will have to store that information externally.</param>
    ///  <param name="integrity">      True if you want to calculate and store hash signatures for each chunkSize.</param>
    ///  <param name="hashKey">        Hash key to be used for all chunks.</param>
    ///  <param name="chunkSize">      The chunk size.</param>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public static string EncryptString(string text, byte[] key, byte[] nonce, bool header, 
        bool integrity = false, byte[] hashKey = null, int? chunkSize = null)
    {
        byte[] bytes = System.Text.Encoding.Default.GetBytes(text);
        byte[] encBytes = encryptor.Encrypt(bytes, key, nonce, header, integrity, hashKey, chunkSize);
        string encString = SalmonEncoder.Base64.Encode(encBytes).Replace("\n", "");
        return encString;
    }
}
