package com.mku.salmon.samples.main;

import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.samples.samples.TextSample;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

import java.io.IOException;

public class TextProgram {
    public static void main(String[] args) throws IOException {
        String password = "test123";
        String text = "This is a plain text that will be encrypted";

        SalmonStream.setAesProviderType(ProviderType.Default);

        // generate an encryption key from the text password
        byte[] key = SamplesCommon.getKeyFromPassword(password);
        System.out.println("Plain Text: " + "\n" + text + "\n");

        String encText = TextSample.encryptText(text, key);
        System.out.println("Encrypted Text: " + "\n" + encText + "\n");

        String decText = TextSample.decryptText(encText, key);
        System.out.println("Decrypted Text: " + "\n" + decText + "\n");
    }
}