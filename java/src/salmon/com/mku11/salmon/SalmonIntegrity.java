package com.mku11.salmon;
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

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SalmonIntegrity {
    /**
     * Verify the HMAC on a data chunk
     *
     * @param buffer    Data     of Chunks that will be used to calculate HMAC. Each chunk has a precalculated HMAC at the end of length same as the HMAC key
     * @param length    Length   of the buffer to use
     * @param hmacKey   HMAC256 key to use to calculate
     * @param chunkSize 0 to use integrity on the whole file (1 chunk)
     *                  >0 to specify integrity chunks
     */
    public static int verifyHMAC(byte[] buffer, int length, byte[] hmacKey, int chunkSize,
                                 byte[] includeHeaderData) throws Exception {
        int chunkOffset = 0;
        int actualBytes = 0;
        int chunkLength;
        while (chunkOffset < length) {
            chunkLength = Math.min(chunkSize, length - chunkOffset - hmacKey.length);
            byte[] includeData = chunkOffset == 0 ? includeHeaderData : null;
            byte[] hmacHash = calculateHMAC(buffer, chunkOffset + hmacKey.length, chunkLength, hmacKey, includeData);
            for (int i = 0; i < hmacKey.length; i++)
                if (hmacHash[i] != buffer[chunkOffset + i])
                    return -1;
            chunkOffset += hmacKey.length + chunkLength;
            actualBytes += chunkLength;
        }
        if (chunkOffset != length)
            throw new Exception("Invalid format");
        return actualBytes;
    }

    public static void applyHMAC(byte[] buffer, int length, int chunkSize, byte[] hmacKey,
                                 byte[] includeHeaderData) throws Exception {
        int chunkOffset = 0;
        int chunkLength;
        while (chunkOffset < length) {
            chunkLength = Math.min(chunkSize, length - chunkOffset - hmacKey.length);
            byte[] includeData = chunkOffset == 0 ? includeHeaderData : null;
            byte[] hmacHash = calculateHMAC(buffer, chunkOffset + hmacKey.length, chunkLength, hmacKey, includeData);
            System.arraycopy(hmacHash, 0, buffer, chunkOffset, hmacHash.length);
            chunkOffset += hmacKey.length + chunkLength;
        }
        if (chunkOffset != length)
            throw new Exception("Invalid format");
    }

    /**
     * Calculate HMAC hash from a data chunk
     *
     * @param buffer  Data        without the HMAC values that will be used to recalculate HMAC
     * @param offset  Offset      of the buffer that the HMAC calculation will start from
     * @param count   Length       of the buffer that will be used to calculate the HMAC
     * @param hmacKey HMACSHA256 key that will be used
     */
    //TODO: we should avoid the header data for performance?
    public static byte[] calculateHMAC(byte[] buffer, int offset, int count, byte[] hmacKey,
                                       byte[] includeData)
            throws NoSuchAlgorithmException, InvalidKeyException, SalmonIntegrityException {
        byte[] hashValue;
        byte[] finalBuffer = buffer;
        int finalOffset = offset;
        int finalCount = count;
        if (includeData != null) {
            finalBuffer = new byte[count + includeData.length];
            finalCount = count + includeData.length;
            System.arraycopy(includeData, 0, finalBuffer, 0, includeData.length);
            System.arraycopy(buffer, offset, finalBuffer, includeData.length, count);
            finalOffset = 0;
        }

        Mac hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(hmacKey, "HmacSHA256");
        hmac.init(secret_key);
        try {
            hmac.update(finalBuffer, finalOffset, finalCount);
            hashValue = hmac.doFinal();
        } catch (Exception ex) {
            throw new SalmonIntegrityException("Could not calculate HMAC", ex);
        }
        return hashValue;
    }

    /**
     * @param actualPosition Actual position of the baseStream
     * @param chunkSize      The byte size of the stream chunk that will be used to calculate the HMAC.
     *                       The length should be fixed value with the exception of the last chunk which might be lesser since we don't use padding
     * @param hmacOffset     The HMAC key length that will be used as an offset
     */
    public static long getTotalHMACBytesFrom(long actualPosition, int chunkSize, int hmacOffset) {
        // if the stream is using multiple chunks for integrity
        int chunks = (int) (actualPosition / (chunkSize + hmacOffset));
        int rem = (int) (actualPosition % (chunkSize + hmacOffset));
        if (rem > hmacOffset)
            chunks++;
        return (long) chunks * SalmonGenerator.HMAC_KEY_LENGTH;
    }

    public static class SalmonIntegrityException extends Exception {
        public SalmonIntegrityException(String message) {
            super(message);
        }

        public SalmonIntegrityException(String msg, Exception ex) {
            super(msg, ex);
        }
    }

}