package com.mku.salmon.samples.main;

import com.mku.convert.BitConverter;
import com.mku.salmon.Generator;
import com.mku.salmon.samples.samples.DataStreamSample;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;

import java.io.IOException;

public class DataStreamProgram {
    public static void main(String[] args) throws IOException {
        String password = "test123";
        int size = 1 * 1024 * 1024;

        AesStream.setAesProviderType(ProviderType.Default);

        // generate a key
        System.out.println("generating keys and random data...");
        byte[] key = SamplesCommon.getKeyFromPassword(password);

        // Always request a new random secure nonce!
        // if you want to you can embed the nonce in the header data
        // see Encryptor implementation
        byte[] nonce = Generator.getSecureRandomBytes(8); // 64 bit nonce
        System.out.println("Created nonce: " + BitConverter.toHex(nonce));

        // generate random data
        byte[] data = SamplesCommon.generateRandomData(size);

        System.out.println("starting encryption...");
        byte[] encData = DataStreamSample.encryptDataStream(data, key, nonce);
        System.out.println("starting decryption...");
        byte[] decData = DataStreamSample.decryptDataStream(encData, key, nonce);
        System.out.println("done");
    }
}
