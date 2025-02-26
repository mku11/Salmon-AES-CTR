package com.mku.salmon.samples.main;
import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.samples.samples.*;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;

import java.io.IOException;

public class DataProgram
{
    public static void main(String[] args) throws IOException {
        String password = "test123";
        int size = 8 * 1024 * 1024;
        int threads = 1;
        boolean integrity = true;

        SalmonStream.setAesProviderType(ProviderType.Default);

        // generate a key
        System.out.println("generating keys and random data...");
        byte[] key = SamplesCommon.getKeyFromPassword(password);

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity)
        {
            // generate an HMAC key
            integrityKey = SalmonGenerator.getSecureRandomBytes(32);
        }

        // generate random data
        byte[] data = SamplesCommon.generateRandomData(size);

        System.out.println("starting encryption...");
        byte[] encData = DataSample.encryptData(data, key, integrityKey, threads);
        System.out.println("starting decryption...");
        byte[] decData = DataSample.decryptData(encData, key, integrityKey, threads);
        System.out.println("done");
    }
}