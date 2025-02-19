package com.mku.salmon.transform;
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

import com.mku.salmon.SalmonSecurityException;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Salmon AES transformer based on the javax.crypto routines.
 */
public class SalmonDefaultTransformer extends SalmonAES256CTRTransformer {

    /**
     * Default Java AES cipher.
     */
    private Cipher cipher;

    /**
     * Key spec for the initial nonce (counter).
     */
    private SecretKeySpec encSecretKey;

    /**
     * Initialize the default Java AES cipher transformer.
     * @param key The AES256 key to use.
     * @param nonce The nonce to use.
     * @throws SalmonSecurityException Thrown if there is a security exception
     */
    public void init(byte[] key, byte[] nonce) {
        super.init(key, nonce);
        try {
            encSecretKey = new SecretKeySpec(key, "AES");
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new SalmonSecurityException("Could not init AES transformer", e);
        }
    }

    /**
     * Encrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     * @throws SalmonSecurityException Thrown if there is a security exception
     */
    public int encryptData(byte[] srcBuffer, int srcOffset,
                           byte[] destBuffer, int destOffset, int count) {
        if (this.encSecretKey == null)
            throw new SalmonSecurityException("No key defined, run init first");
        try {
            byte[] counter = getCounter();
            IvParameterSpec ivSpec = new IvParameterSpec(counter);
            cipher.init(Cipher.ENCRYPT_MODE, encSecretKey, ivSpec);
            return cipher.doFinal(srcBuffer, srcOffset, count, destBuffer, destOffset);
        } catch (IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException ex) {
            throw new SalmonSecurityException("Could not encrypt data: ", ex);
        }
    }

    /**
     * Decrypt the data.
     * @param srcBuffer The source byte array.
     * @param srcOffset The source byte offset.
     * @param destBuffer The destination byte array.
     * @param destOffset The destination byte offset.
     * @param count The number of bytes to transform.
     * @return The number of bytes transformed.
     * @throws SalmonSecurityException Thrown if there is a security exception
     */
    public int decryptData(byte[] srcBuffer, int srcOffset,
                            byte[] destBuffer, int destOffset, int count) {
        if (this.encSecretKey == null)
            throw new SalmonSecurityException("No key defined, run init first");
        try {
            byte[] counter = getCounter();
            IvParameterSpec ivSpec = new IvParameterSpec(counter);
            cipher.init(Cipher.DECRYPT_MODE, encSecretKey, ivSpec);
            return cipher.doFinal(srcBuffer, srcOffset, count, destBuffer, destOffset);
        } catch (IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException ex) {
            throw new SalmonSecurityException("Could not decrypt data: ", ex);
        }
    }
}
