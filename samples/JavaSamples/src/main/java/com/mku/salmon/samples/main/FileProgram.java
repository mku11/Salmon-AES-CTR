package com.mku.salmon.samples.main;

import com.mku.file.IRealFile;
import com.mku.file.JavaFile;
import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.samples.samples.FileSample;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

import java.io.IOException;


public class FileProgram {
    public static void main(String[] args) throws IOException {
        String password = "test123";
        String text = "This is a plain text that will be encrypted";
        boolean integrity = true;

        SalmonStream.setAesProviderType(ProviderType.Default);

        // generate an encryption key from the text password
        byte[] key = SamplesCommon.getKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity) {
            // generate an HMAC key
            integrityKey = SalmonGenerator.getSecureRandomBytes(32);
        }

        IRealFile dir = new JavaFile("./output");
        if (!dir.exists())
            dir.mkdir();
        IRealFile file = dir.getChild("data.dat");
        if (file.exists())
            file.delete();

        FileSample.encryptTextToFile(text, key, integrityKey, file);
        String decText = FileSample.decryptTextFromFile(key, integrityKey, file);
    }
}