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
using System;

namespace Salmon.FS
{
    public class SalmonKey
    {
        //TODO: could these be hosted inside secure storage like SecureString?
        private byte[] masterKey;
        private byte[] driveKey;
        private byte[] hmacKey;
        private byte[] salt;
        private int iterations;

        public void Clear()
        {
            
            if (driveKey != null)
                Array.Clear(driveKey, 0, driveKey.Length);
            driveKey = null;
            
            if (hmacKey != null)
                Array.Clear(hmacKey, 0, hmacKey.Length);
            hmacKey = null;
        }

        /// <summary>
        /// Function returns the encryption key that will be used to encrypt/decrypt the files
        /// </summary>
        /// <returns></returns>
        public byte[] GetDriveKey()
        {
            return driveKey;
        }

        /// <summary>
        /// Function returns the HMAC key that will be used to sign the file chunks
        /// </summary>
        /// <returns></returns>
        public byte[] GetHMACKey()
        {
            return hmacKey;
        }

        public void SetDriveKey(byte[] driveKey)
        {
            this.driveKey = driveKey;
        }

        public void SetHmacKey(byte[] hmacKey)
        {
            this.hmacKey = hmacKey;
        }

        public byte[] GetMasterKey()
        {
            return masterKey;
        }

        public void SetMasterKey(byte [] masterKey)
        {
            this.masterKey = masterKey;
        }

        public int GetIterations()
        {
            return iterations;
        }

        public void SetIterations(int iterations)
        {
            this.iterations = iterations;
        }

        public void SetSalt(byte [] salt)
        {
            this.salt = salt;
        }

        public byte [] GetSalt()
        {
            return salt;
        }
    }
}
