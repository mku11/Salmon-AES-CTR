package com.mku.salmon.samples.samples;

import com.mku.convert.BitConverter;
import com.mku.salmon.SalmonDecryptor;
import com.mku.salmon.SalmonEncryptor;
import com.mku.salmon.SalmonGenerator;

import java.io.IOException;
import java.util.Arrays;

public class DataSample {

    public static byte[] encryptData(byte[] data, byte[] key, byte[] integrityKey, int threads) throws IOException {
        System.out.println("Encrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = SalmonGenerator.getSecureRandomBytes(8);

        SalmonEncryptor encryptor = new SalmonEncryptor(threads);
        byte[] encData = encryptor.encrypt(data, key, nonce, true,
                integrityKey != null, integrityKey, null);
        encryptor.close();

        System.out.println("Bytes encrypted: " + BitConverter.toHex(Arrays.copyOf(encData, 24)) + "...");
        return encData;
    }

    public static byte[] decryptData(byte[] data, byte[] key, byte[] integrityKey, int threads) throws IOException {
        System.out.println("Decrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        SalmonDecryptor decryptor = new SalmonDecryptor(threads);
        byte[] decBytes = decryptor.decrypt(data, key, null, true,
                integrityKey != null, integrityKey, null);
        decryptor.close();

        System.out.println("Bytes decrypted: " + BitConverter.toHex(Arrays.copyOf(decBytes, 24)) + "...");
        return decBytes;
    }
}