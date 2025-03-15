package com.mku.salmon.samples.samples;

import com.mku.salmon.Generator;
import com.mku.salmon.text.TextDecryptor;
import com.mku.salmon.text.TextEncryptor;

import java.io.IOException;


public class TextSample {
    public static String encryptText(String text, byte[] key) throws IOException {
        // Always request a new random secure nonce.
        byte[] nonce = Generator.getSecureRandomBytes(8);

        // encrypt String and embed the nonce in the header
        String encText = TextEncryptor.encryptString(text, key, nonce);
        return encText;
    }

    public static String decryptText(String encText, byte[] key) throws IOException {
        // decrypt String, the nonce is already embedded
        String decText = TextDecryptor.decryptString(encText, key);
        return decText;
    }
}