package com.mku.salmon.integrity;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides HMAC SHA-256 hashing.
 */
public class HMACSHA256Provider implements IHashProvider {

    /**
     * Calculate HMAC SHA256 hash for a byte buffer.
     *
     * @param hashKey The HMAC SHA256 key to use for hashing (32 bytes).
     * @param buffer  The buffer to read the data from.
     * @param offset  The position reading will start from.
     * @param count   The count of bytes to be read.
     * @return The HMAC SHA256 hash.
     * @throws IntegrityException thrown if hash cannot be calculated
     */
    @Override
    public byte[] calc(byte[] hashKey, byte[] buffer, int offset, int count) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(hashKey, "HmacSHA256");
            hmac.init(secret_key);
            hmac.update(buffer, offset, count);
            byte[] hashValue = hmac.doFinal();
            return hashValue;
        } catch (Exception ex) {
            throw new IntegrityException("Could not calculate HMAC", ex);
        }
    }
}
