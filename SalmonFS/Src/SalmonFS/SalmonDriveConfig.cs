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
using Salmon.FS;
using System;
using System.IO;

namespace Salmon
{
    /// <summary>
    /// Virtual Drive Configuration
    /// </summary>
    public class SalmonDriveConfig
    {
        //TODO: support versioned formats for the file header
        internal byte[] magicBytes = new byte[SalmonGenerator.GetMagicBytesLength()];
        internal byte[] version = new byte[SalmonGenerator.GetVersionLength()];
        internal byte[] salt = new byte[SalmonGenerator.GetSaltLength()];
        internal byte[] iterations = new byte[SalmonGenerator.GetIterationsLength()];
        internal byte[] iv = new byte[SalmonGenerator.GetIvLength()];
        internal byte[] encryptedKeysAndNonce = new byte[SalmonGenerator.GetCombinedKeyLength() + SalmonGenerator.GetNonceLength()];
        internal byte[] hmacSignature = new byte[SalmonGenerator.GetHmacResultLength()];

        /// <summary>
        /// Provide a class that hosts the properties of the vault config file
        /// </summary>
        /// <param name="contents">The byte array that contains the contents of the config file</param>
        public SalmonDriveConfig(byte[] contents)
        {
            MemoryStream ms = new MemoryStream(contents);
            ms.Read(magicBytes, 0, SalmonGenerator.GetMagicBytesLength());
            ms.Read(version, 0, SalmonGenerator.GetVersionLength());
            ms.Read(salt, 0, SalmonGenerator.GetSaltLength());
            ms.Read(iterations, 0, SalmonGenerator.GetIterationsLength());
            ms.Read(iv, 0, SalmonGenerator.GetIvLength());
            ms.Read(encryptedKeysAndNonce, 0, SalmonGenerator.GetCombinedKeyLength() + SalmonGenerator.GetNonceLength());
            ms.Read(hmacSignature, 0, SalmonGenerator.GetHmacResultLength());
            ms.Close();
        }

        /// <summary>
        /// Write the properties of a vault configuration to a config file
        /// </summary>
        /// <param name="configFile">The configuration file that will be used to write the content into</param>
        /// <param name="magicBytes">The magic bytes for the header</param>
        /// <param name="version">The version of the file format</param>
        /// <param name="salt">The salt that will be used for encryption of the combined key</param>
        /// <param name="iterations">The iteration that will be used to derive the master key from a text password</param>
        /// <param name="keyIv">The initial vector that was used with the master password to encrypt the combined key</param>
        /// <param name="encryptedCombinedKeyAndNonce">The encrypted combined key and vault nonce</param>
        /// <param name="hmacSignature">The HMAC signature of the nonce</param>
        public static void WriteDriveConfig(IRealFile configFile, byte[] magicBytes, byte version, byte[] salt, int iterations, byte[] keyIv, 
            byte[] encryptedCombinedKeyAndNonce, byte [] hmacSignature)
        {
            // construct the contents of the config file
            MemoryStream ms2 = new MemoryStream();
            ms2.Write(magicBytes, 0, magicBytes.Length);
            ms2.Write(BitConverter.GetBytes(version), 0, sizeof(byte));
            ms2.Write(salt, 0, salt.Length);
            ms2.Write(BitConverter.GetBytes(iterations), 0, sizeof(int));
            ms2.Write(keyIv, 0, keyIv.Length);
            ms2.Write(encryptedCombinedKeyAndNonce, 0, encryptedCombinedKeyAndNonce.Length);
            ms2.Write(hmacSignature, 0, hmacSignature.Length);
            ms2.Flush();
            ms2.Position = 0;

            // we write the contents to the config file
            Stream outputStream = configFile.GetOutputStream();
            ms2.CopyTo(outputStream);
            outputStream.Flush();
            outputStream.Close();
            ms2.Close();
        }

        public void Clear()
        {
            Array.Clear(magicBytes, 0, magicBytes.Length);
            Array.Clear(version, 0, version.Length);
            Array.Clear(salt, 0, salt.Length);
            Array.Clear(iterations, 0, iterations.Length);
            Array.Clear(iv, 0, iv.Length);
            Array.Clear(encryptedKeysAndNonce, 0, encryptedKeysAndNonce.Length);
            Array.Clear(hmacSignature, 0, hmacSignature.Length);
        }
        public byte[] GetMagicBytes()
        {
            return magicBytes;
        }

        public byte[] GetSalt()
        {
            return salt;
        }

        public int GetIterations()
        {
            if (iterations == null)
                return 0;
            return BitConverter.ToInt32(iterations, 0);
        }

        public byte[] GetEncryptedKeysAndNonce()
        {
            return encryptedKeysAndNonce;
        }

        public byte[] GetIv()
        {
            return iv;
        }

        public byte[] GetHMACsignature()
        {
            return hmacSignature;
        }

    }
}