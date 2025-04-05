package com.mku.salmon.samples.samples;

import com.mku.convert.BitConverter;
import com.mku.func.Consumer;
import com.mku.func.Function;
import com.mku.salmon.Decryptor;
import com.mku.salmon.Encryptor;
import com.mku.salmon.Generator;
import com.mku.salmon.streams.EncryptionFormat;

import java.io.IOException;
import java.util.Arrays;

public class DataSample {

    public static byte[] encryptData(byte[] data, byte[] key, byte[] integrityKey, int threads,  Consumer<String> log) throws IOException {
        log.accept("Encrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        // Always request a new random secure nonce.
        byte[] nonce = Generator.getSecureRandomBytes(8);

        Encryptor encryptor = new Encryptor(threads);
        byte[] encData = encryptor.encrypt(data, key, nonce, EncryptionFormat.Salmon,
                integrityKey != null, integrityKey);
        encryptor.close();

        log.accept("Bytes encrypted: " + BitConverter.toHex(Arrays.copyOf(encData, 24)) + "...");
        return encData;
    }

    public static byte[] decryptData(byte[] data, byte[] key, byte[] integrityKey, int threads, Consumer<String> log) throws IOException {
        log.accept("Decrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        Decryptor decryptor = new Decryptor(threads);
        byte[] decBytes = decryptor.decrypt(data, key, null, EncryptionFormat.Salmon,
                integrityKey != null, integrityKey);
        decryptor.close();

        log.accept("Bytes decrypted: " + BitConverter.toHex(Arrays.copyOf(decBytes, 24)) + "...");
        return decBytes;
    }
}