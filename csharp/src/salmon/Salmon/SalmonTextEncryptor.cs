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
using Salmon.Streams;
using System;
using System.IO;
using static Salmon.Streams.SalmonStream;

namespace Salmon
{
    /// <summary>
    /// Utility class that encrypts and decrypts text strings
    /// </summary>
    public class SalmonTextEncryptor
    {
        /// <summary>
        /// Decrypts a text string 
        /// </summary>
        /// <param name="text">Text to be decrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The nonce to be used</param>
        /// <param name="header">If the text has a header with the nonce</param>
        /// <returns></returns>
        // TODO: there is currently no integrity for filenames, is it worthy it?
        public static string DecryptString(string text, byte[] key, byte[] nonce, bool header)
        {
            byte[] bytes = Convert.FromBase64String(text);
            byte[] decBytes = SalmonEncryptor.Decrypt(bytes, key, nonce, header);
            string decString = System.Text.UTF8Encoding.Default.GetString(decBytes);
            return decString;

        }

        /// <summary>
        /// Encrypts a text string
        /// </summary>
        /// <param name="text">Text to be encrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The nonce to be used</param>
        /// <param name="header">Store a header with the nonce</param>
        /// <returns></returns>
        public static string EncryptString(string text, byte[] key, byte[] nonce, bool header)
        {
            byte[] bytes = System.Text.UTF8Encoding.Default.GetBytes(text);
            byte[] encBytes = SalmonEncryptor.Encrypt(bytes, key, nonce, header);
            string encString = Convert.ToBase64String(encBytes);
            return encString;
        }
    }
}
