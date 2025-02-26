package com.mku.salmon.samples.samples;

import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.text.SalmonTextDecryptor;
import com.mku.salmon.text.SalmonTextEncryptor;

import java.io.IOException;


public class TextSample {
    public static String encryptText(String text, byte[] key) throws IOException {
        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        // encrypt String and embed the nonce in the header
        String encText = SalmonTextEncryptor.encryptString(text, key, nonce, true);
        return encText;
    }

    public static String decryptText(String encText, byte[] key) throws IOException {
        // decrypt String, the nonce is already embedded
        String decText = SalmonTextDecryptor.decryptString(encText, key, null, true);
        return decText;
    }
}