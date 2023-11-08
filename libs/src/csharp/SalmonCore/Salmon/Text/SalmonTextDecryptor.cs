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
public class SalmonTextDecryptor
{

    private static readonly SalmonDecryptor decryptor = new SalmonDecryptor();

    /// <summary>
    ///  Decrypts a text string using AES256 with the key and nonce provided.
    /// </summary>
    ///  <param name="text"> Text to be decrypted.</param>
    ///  <param name="key">  The encryption key to be used.</param>
    ///  <param name="nonce">The nonce to be used, set only if header=false.</param>
    ///  <param name="header">Set to true if you encrypted the string with encrypt(header=true), set only if nonce=null
    ///                otherwise you will have to provide the original nonce.</param>
    ///  <param name="integrity">      True if you want to calculate and store hash signatures for each chunkSize.</param>
    ///  <param name="hashKey">        Hash key to be used for all chunks.</param>
    ///  <param name="chunkSize">      The chunk size.</param>
    ///  <returns>The decrypted text.</returns>
    ///  <exception cref="IOException"></exception>
    ///  <exception cref="SalmonSecurityException"></exception>
    ///  <exception cref="SalmonIntegrityException"></exception>
    public static string DecryptString(string text, byte[] key, byte[] nonce, bool header,
        bool integrity = false, byte[] hashKey = null, int? chunkSize = null)
    {
        byte[] bytes = SalmonEncoder.Base64.Decode(text);
        byte[] decBytes = decryptor.Decrypt(bytes, key, nonce, header, integrity, hashKey, chunkSize);
        string decString = System.Text.Encoding.Default.GetString(decBytes);
        return decString;
    }
}
