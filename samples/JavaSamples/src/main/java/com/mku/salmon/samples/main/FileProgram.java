package com.mku.salmon.samples.main;

import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmon.Generator;
import com.mku.salmon.samples.samples.FileSample;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;

import java.io.IOException;


public class FileProgram {
    public static void main(String[] args) throws IOException {
        String password = "test123";
        String text = "This is a plain text that will be encrypted";
        boolean integrity = true;

        AesStream.setAesProviderType(ProviderType.Default);

        // generate an encryption key from the text password
        byte[] key = SamplesCommon.getKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity) {
            // generate an HMAC key
            integrityKey = Generator.getSecureRandomBytes(32);
        }

        IFile dir = new File("./output");
        if (!dir.exists())
            dir.mkdir();
        IFile file = dir.getChild("data.dat");
        if (file.exists())
            file.delete();

        FileSample.encryptTextToFile(text, key, integrityKey, file);
        String decText = FileSample.decryptTextFromFile(key, integrityKey, file);
    }
}