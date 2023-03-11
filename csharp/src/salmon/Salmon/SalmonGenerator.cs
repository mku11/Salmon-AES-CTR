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
        public static readonly string MAGIC_BYTES = "SLM";
        public static readonly byte VERSION = 2;

        public static readonly int MAGIC_LENGTH = 3;
        public static readonly int VERSION_LENGTH = 1;

        public static readonly int ITERATIONS_LENGTH = 4;

        // should be 16 for AES256 the same as the iv
        public static readonly int BLOCK_SIZE = 16;

        // length for IV that will be used for encryption and master encryption of the combined key
        public static readonly int IV_LENGTH = 16;

        // encryption key length for AES256
        public static readonly int KEY_LENGTH = 32;

        // encryption key length for HMAC256
        public static readonly int HMAC_KEY_LENGTH = 32;

        // result of the SHA256 should always be 256 bits
        public static readonly int HMAC_RESULT_LENGTH = 32;


        // combined key is encryption key + HMAC key
        public static readonly int COMBINED_KEY_LENGTH = KEY_LENGTH + HMAC_KEY_LENGTH;

        // master key to encrypt the combined key we also use AES256
        public static readonly int MASTER_KEY_LENGTH = 32;

        // salt size
        public static readonly int SALT_LENGTH = 24;

        // vault nonce size
        public static readonly int NONCE_LENGTH = 8;

        // drive ID size
        public static readonly int DRIVE_ID_LENGTH = 16;

        // auth ID size
        public static readonly int AUTH_ID_SIZE = 16;

        public static readonly int CHUNKSIZE_LENGTH = 4;

        // iterations for the text derived master key
        public static int iterations = 65536;

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
        /// Returns the iterations used for deriving the combined key from 
        /// the text password
        /// </summary>
        /// <returns></returns>
        public static int GetIterations()
        {
            return iterations;
        }

        public static void SetIterations(int iterations)
        {
            SalmonGenerator.iterations = iterations;
        }

        public static byte[] GenerateDriveID()
        {
            return GetSecureRandomBytes(DRIVE_ID_LENGTH);
        }

        public static byte[] getDefaultVaultNonce()
        {
            byte[] bytes = new byte[NONCE_LENGTH];
            return bytes;
        }

        public static byte[] GetDefaultMaxVaultNonce()
        {
            return BitConverter.ToBytes(long.MaxValue, 8);
        }

        public static byte[] GenerateAuthId()
        {
            return GetSecureRandomBytes(AUTH_ID_SIZE);
        }

        /// <summary>
        /// Returns a secure random byte array
        /// </summary>
        /// <returns></returns>
        public static byte[] GetSecureRandomBytes(int size)
        {
            RNGCryptoServiceProvider rand = new RNGCryptoServiceProvider();
            byte[] bytes = new byte[size];
            rand.GetNonZeroBytes(bytes);
            return bytes;
        }

        /// <summary>
        /// Generates a secure random combined key (encryption key + HMAC key)
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateCombinedKey()
        {
            return GetSecureRandomBytes(COMBINED_KEY_LENGTH);
        }

        /// <summary>
        /// Derives the key from a text password
        /// </summary>
        /// <param name="pass">The text password to be used</param>
        /// <param name="salt">The salt to be used for the key derivation</param>
        /// <param name="iterations">The number of iterations the key derivation alogrithm will use</param>
        /// <returns></returns>
        public static byte[] GetMasterKey(string pass, byte[] salt, int iterations)
        {
            byte[] masterKey = SalmonGenerator.GetKeyFromPassword(pass, salt, iterations, MASTER_KEY_LENGTH);
            return masterKey;
        }

        /// <summary>
        /// Generates the salt
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateSalt()
        {
            return GetSecureRandomBytes(SALT_LENGTH);
        }

        /// <summary>
        /// Generates the initial vector used that will be used with the master key to encrypt the combined key
        /// </summary>
        /// <returns></returns>
        public static byte[] GenerateMasterKeyIV()
        {
            return GetSecureRandomBytes(IV_LENGTH);
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

    }
}
