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
using System.Runtime.Serialization;
using System.Security.Cryptography;

namespace Salmon
{
    public class SalmonIntegrity
    {

        public class SalmonIntegrityException : Exception
        {
            public SalmonIntegrityException(string message) : base(message) { }

            public SalmonIntegrityException(string msg, Exception ex) : base(msg, ex) { }
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="buffer">Data of Chunks that will be used to calculate HMAC. 
        /// Each chunk has a precalculated HMAC at the end of length same as the HMAC key</param>
        /// <param name="offset">Start of the buffer that will be used to caclulate HMAC</param>
        /// <param name="count">Length of the buffer to use</param>
        /// <param name="hmacKey">HMAC256 key to use to calculate</param>
        /// <param name="chunkSize">
        /// 0 to use integrity on the whole file (1 chunk)
        /// >0 to specify integrity chunks
        /// </param>
        public static int VerifyHMAC(byte[] buffer, int length, byte[] hmacKey, int chunkSize, 
            byte [] includeHeaderData  = null)
        {
            int chunkOffset = 0;
            int actualBytes = 0;
            int chunkLength = chunkSize;
            while (chunkOffset < length)
            {
                chunkLength = Math.Min(chunkSize, length - chunkOffset - hmacKey.Length);
                byte[] includeData = chunkOffset == 0?includeHeaderData:null;
                byte[] hmacHash = CalculateHMAC(buffer, chunkOffset + hmacKey.Length, chunkLength, hmacKey, includeData);
                for (int i = 0; i < hmacKey.Length; i++)
                    if (hmacHash[i] != buffer[chunkOffset + i])
                        return -1;
                chunkOffset += hmacKey.Length + chunkLength;
                actualBytes += chunkLength;
            }
            if (chunkOffset != length)
                throw new Exception("Invalid format");
            return actualBytes;
        }

        public static void ApplyHMAC(byte[] buffer, int length, int chunkSize, byte[] hmacKey, 
            byte[] includeHeaderData = null)
        {
            int chunkOffset = 0;
            int chunkLength = chunkSize;
            while (chunkOffset < length)
            {
                chunkLength = Math.Min(chunkSize, length - chunkOffset - hmacKey.Length);
                byte[] includeData = chunkOffset == 0 ? includeHeaderData : null;
                byte[] hmacHash = CalculateHMAC(buffer, chunkOffset + hmacKey.Length, chunkLength, hmacKey, includeData);
                Array.Copy(hmacHash, 0, buffer, chunkOffset, hmacHash.Length);
                chunkOffset += hmacKey.Length + chunkLength;
            }
            if (chunkOffset != length)
                throw new Exception("Invalid format");
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="buffer">Data that will be used to calculate HMAC</param>
        /// <param name="offset">Offset of the buffer that the HMAC calculation will start from</param>
        /// <param name="count">Length of the buffer that will be used to calculate the HMAC</param>
        /// <param name="hmacKey">HMACSHA256 key that will be used</param>
        /// <returns></returns>
        //TODO: we should avoid prepending the header data for performance?
        public static byte[] CalculateHMAC(byte[] buffer, int offset, int count, byte[] hmacKey, 
            byte[] includeData = null)
        {
            byte[] hashValue = null;
            byte[] finalBuffer = buffer;
            int finalOffset = offset;
            int finalCount = count;
            if (includeData != null)
            {
                finalBuffer = new byte[count + includeData.Length];
                finalCount = count + includeData.Length;
                Array.Copy(includeData, 0, finalBuffer, 0, includeData.Length);
                Array.Copy(buffer, offset, finalBuffer, includeData.Length, count);
                finalOffset = 0;
            }

            HMACSHA256 hmac = new HMACSHA256(hmacKey);
            try
            {
                hashValue = hmac.ComputeHash(finalBuffer, finalOffset, finalCount);
            } catch (Exception ex)
            {
                throw new SalmonIntegrityException("Could not calculate HMAC", ex);
            }
            return hashValue;
        }


        /// <summary>
        /// 
        /// </summary>
        /// <param name="actualPosition">Actual position of the baseStream</param>
        /// <param name="chunkSize">The byte size of the stream chunk that will be used to calculate the HMAC. 
        /// The length should be fixed value with the exception of the last chunk which might be lesser since we don't use padding</param>
        /// <param name="hmacOffset">The HMAC key length that will be used as an offset</param>
        /// <returns></returns>
        public static long GetTotalHMACBytesFrom(long actualPosition, int chunkSize, int hmacOffset)
        {
            // if the stream is using multiple chunks for integrity
            int chunks = (int)(actualPosition / (chunkSize + hmacOffset));
            int rem = (int)(actualPosition % (chunkSize + hmacOffset));
            if (rem > hmacOffset)
                chunks++;
            return chunks * SalmonGenerator.HMAC_KEY_LENGTH;
        }

    }
}