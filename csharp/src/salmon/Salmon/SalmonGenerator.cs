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
using Microsoft.AspNetCore.Cryptography.KeyDerivation;
using System;
using System.Security.Cryptography;

namespace Salmon
{
    /// <summary>
    /// Utility class to be used with generating secure keys and initial vectors
    /// </summary>
    public class SalmonGenerator
    {
        //TODO: support versioned formats for the stream header
        //TODO: perhaps store this property in an XML config file?
        private static string MAGIC_BYTES = "SAL";
        private static readonly byte VERSION = 1; 

        private static readonly int MAGIC_LENGTH = 3;
        private static readonly int VERSION_LENGTH = sizeof(byte);

        private static readonly int ITERATIONS_LENGTH = sizeof(int);

        // iterations for the text derived master key
        private static readonly int ITERATIONS = 65536;

        // should be 16 for AES256 the same as the iv
        private static readonly int BLOCK_SIZE = 16;

        // length for IV that will be used for encryption and master encryption of the combined key
        private static readonly int IV_LENGTH = 16;

        // encryption key length for AES256
        private static readonly int KEY_LENGTH = 32;

        // encryption key length for HMAC256
        private static readonly int HMAC_KEY_LENGTH = 32;

        // result of the SHA256 should always be 256 bits
        private static readonly int HMAC_RESULT_LENGTH = 32;
        

        // combined key is encryption key + HMAC key
        private static readonly int COMBINED_KEY_LENGTH = KEY_LENGTH + HMAC_KEY_LENGTH;

        // master key to encrypt the combined key we also use AES256
        private static readonly int MASTER_KEY_LENGTH = 32;

        // salt size
        private static readonly int SALT_LENGTH = 24;

        // vault nonce size
        private static readonly int NONCE_LENGTH = 8;

        /// <summary>
        /// Returns the byte length for the salt that will be used for encrypting the combined key (encryption key + HMAC key)
        /// </summary>
        /// <returns></returns>
        public static int GetSaltLength()
        {
            return SALT_LENGTH;
        }

        /// <summary>
        /// Returns the byte length for the combined key (encryption key + HMAC key)
        /// </summary>
        /// <returns></returns>
        public static int GetCombinedKeyLength()
        {
            return COMBINED_KEY_LENGTH;
        }

        /// <summary>
        /// Gets the fixed magic bytes array
        /// </summary>
        /// <returns></returns>
        public static byte[] GetMagicBytes()
        {
            return System.Text.UTF8Encoding.Default.GetBytes(MAGIC_BYTES);
        }

        /// <summary>
        ///  Returns the Salmon format version
        /// </summary>
        /// <returns></returns>
        public static byte GetVersion()
        {
            return VERSION;
        }

        /// <summary>
        /// Returns the byte length that will store the version number 
        /// </summary>
        /// <returns></returns>
        public static int GetVersionLength()
        {
            return VERSION_LENGTH;
        }

        /// <summary>
        /// Returns the byte length of the magic bytes
        /// </summary>
        /// <returns></returns>
        public static int GetMagicBytesLength()
        {
            return MAGIC_LENGTH;
        }

        /// <summary>
        /// Returns the byte length of the initial vector
        /// </summary>
        /// <returns></returns>
        public static int GetIvLength()
        {
            return IV_LENGTH;
        }

        /// <summary>
        /// Returns the iterations used for deriving the combined key from 
        /// the text password
        /// </summary>
        /// <returns></returns>
        public static int GetIterations()
        {
            return ITERATIONS;
        }

        /// <summary>
        /// Returns the byte length of the iterations that will be stored in the config file
        /// </summary>
        /// <returns></returns>
        public static int GetIterationsLength()
        {
            return ITERATIONS_LENGTH;
        }

        /// <summary>
        /// Returns the byte length of the HMAC key that will be stored in the file
        /// </summary>
        /// <returns></returns>
        public static int GetHMACKeyLength()
        {
            return HMAC_KEY_LENGTH;
        }

        /// <summary>
        /// Returns the byte length of the encryption key
        /// </summary>
        /// <returns></returns>
        public static int GetKeyLength()
        {
            return KEY_LENGTH;
        }

        /// <summary>
        /// Returns a secure random byte array
        /// </summary>
        /// <returns></returns>
        public static byte[] GetSecureRandomBytes(int size)
        {
            RNGCryptoServiceProvider rand = new RNGCryptoServiceProvider();
            byte [] bytes = new byte[size];
            rand.GetNonZeroBytes(bytes);
            return bytes;
        }

        /// <summary>
        /// Generates a secure random combined key (encryption key + HMAC key)
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateCombinedKey()
        {
            return GetSecureRandomBytes(GetCombinedKeyLength());
        }

        /// <summary>
        /// Derives the key from a text password
        /// </summary>
        /// <param name="pass">The text password to be used</param>
        /// <param name="salt">The salt to be used for the key derivation</param>
        /// <param name="iterations">The number of iterations the key derivation alogrithm will use</param>
        /// <returns></returns>
        public static byte[] GetMasterKey(string pass, byte [] salt, int iterations)
        {
            byte[] masterKey = SalmonGenerator.GetKeyFromPassword(pass, salt, iterations, GetMasterKeyLength());
            return masterKey;
        }

        /// <summary>
        /// Return the length of the master key in bytes
        /// </summary>
        /// <returns></returns>
        public static int GetMasterKeyLength()
        {
            return MASTER_KEY_LENGTH;
        }

        /// <summary>
        /// Generates the salt
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateSalt()
        {
            return GetSecureRandomBytes(GetSaltLength());
        }

        /// <summary>
        /// Generates the initial vector used that will be used with the master key to encrypt the combined key
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateMasterKeyIV()
        {
            return GetSecureRandomBytes(GetIvLength());
        }

        /// <summary>
        /// Function will derive a key from a text password using Pbkdf2 with SHA256
        /// </summary>
        /// <param name="password">The password that will be used to derive the key</param>
        /// <param name="salt">The salt byte array that will be used together with the password</param>
        /// <param name="iterations">The iterations to be used with Pbkdf2</param>
        /// <param name="outputBytes">The number of bytes for the key</param>
        /// <returns></returns>
        public static byte[] GetKeyFromPassword(string password, byte[] salt, int iterations, int outputBytes)
        {
            // we should be using SHA256 so we choose the ASP .net KeyDerivation implementation
            return KeyDerivation.Pbkdf2(password, salt, KeyDerivationPrf.HMACSHA256, iterations, outputBytes);
        }

        /// <summary>
        /// Returns the HMAC signature length
        /// </summary>
        /// <returns></returns>
        public static int GetHmacResultLength()
        {
            return HMAC_RESULT_LENGTH;
        }

        /// <summary>
        /// Returns the HMAC signature length
        /// </summary>
        /// <returns></returns>
        public static int GetBlockSize()
        {
            return BLOCK_SIZE;
        }

        /// <summary>
        /// Returns the Vault Nonce Length
        /// </summary>
        /// <returns></returns>
        public static int GetNonceLength()
        {
            return NONCE_LENGTH;
        }
        
    }
}
