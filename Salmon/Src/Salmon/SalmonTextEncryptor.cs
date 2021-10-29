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
        private static readonly int ENC_BUFFER_SIZE = 32768;

        /// <summary>
        /// Decrypts a text string 
        /// </summary>
        /// <param name="text">Text to be decrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The 12 byte nonce to be used</param>
        /// <returns></returns>
        // TODO: there is currently no integrity for filenames, is it worthy it?
        public static string DecryptString(string text, byte[] key, byte[] nonce = null)
        {
            byte[] bytes = Convert.FromBase64String(text);

            MemoryStream inputStream = new MemoryStream(bytes);
            byte[] magicBytes = new byte[SalmonGenerator.GetMagicBytesLength()];
            inputStream.Read(magicBytes, 0, magicBytes.Length);

            byte[] versionBytes = new byte[SalmonGenerator.GetVersionLength()];
            inputStream.Read(versionBytes, 0, versionBytes.Length);

            if (nonce == null)
            {
                nonce = new byte[SalmonGenerator.GetNonceLength()];
                inputStream.Read(nonce, 0, nonce.Length);
            } else
            {
                // TODO: add flags byte that informs if iv is present so we don't waste extra memory
                inputStream.Seek(SalmonGenerator.GetNonceLength(), SeekOrigin.Current);
            }

            inputStream.Position = 0;
            byte[] headerData = new byte[magicBytes.Length + versionBytes.Length + nonce.Length];
            inputStream.Read(headerData, 0, headerData.Length);

            SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Decrypt, inputStream,
                headerData: headerData);
            byte[] buffer = new byte[ENC_BUFFER_SIZE];
            int bytesRead = stream.Read(buffer, 0, buffer.Length);

            string decString = System.Text.UTF8Encoding.Default.GetString(buffer, 0, bytesRead);
            inputStream.Close();
            stream.Close();
            return decString;

        }

        /// <summary>
        /// Encrypts a text string
        /// </summary>
        /// <param name="text">Text to be encrypted</param>
        /// <param name="key">The encryption key to be used</param>
        /// <param name="nonce">The 12 byte nonce to be used</param>
        /// <returns></returns>
        public static string EncryptString(string text, byte[] key, byte[] nonce)
        {
            byte[] bytes = System.Text.UTF8Encoding.Default.GetBytes(text);
            
            MemoryStream outputStream = new MemoryStream();
            byte[] magicBytes = SalmonGenerator.GetMagicBytes();
            outputStream.Write(magicBytes, 0, magicBytes.Length);

            byte version = SalmonGenerator.GetVersion();
            byte[] versionBytes = new byte[]{ version };
            outputStream.Write(versionBytes, 0, versionBytes.Length);

            outputStream.Write(nonce, 0, nonce.Length);

            outputStream.Flush();

            byte[] headerData = outputStream.ToArray();

            SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, outputStream, headerData: headerData);
            stream.Write(bytes, 0, bytes.Length);
            stream.Flush();
            
            byte [] encBytes = outputStream.ToArray();
            string encString = Convert.ToBase64String(encBytes);
            outputStream.Close();
            stream.Close();
            return encString;
        }
    }
}
