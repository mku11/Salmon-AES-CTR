package com.mku.salmon.samples.samples;

import com.mku.convert.BitConverter;
import com.mku.func.Consumer;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.AesStream;
import com.mku.streams.MemoryStream;

import java.io.IOException;
import java.util.Arrays;

public class DataStreamSample {
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static byte[] encryptDataStream(byte[] data, byte[] key, byte[] nonce,
                                           Consumer<String> log) throws IOException {
        log.accept("Encrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        // we use a memory stream to host the encrypted data
        MemoryStream memoryStream = new MemoryStream();

        // and wrap it with a AesStream that will do the encryption
        AesStream encStream = new AesStream(key, nonce, EncryptionMode.Encrypt, memoryStream);

        // now write the data you want to decrypt
        // it is recommended to use a large enough buffer while writing the data
        // for better performance
        int totalBytesWritten = 0;
        while (totalBytesWritten < data.length) {
            int length = Math.min(data.length - totalBytesWritten, DataStreamSample.BUFFER_SIZE);
            encStream.write(data, totalBytesWritten, length);
            totalBytesWritten += length;
        }
        encStream.flush();

        // the encrypted data are now written to the memoryStream/encData.
        encStream.close();
        byte[] encData = memoryStream.toArray();
        memoryStream.close();

        log.accept("Bytes encrypted: " + BitConverter.toHex(Arrays.copyOf(encData, 24)) + "...");
        return encData;
    }

    public static byte[] decryptDataStream(byte[] data, byte[] key, byte[] nonce,
                                           Consumer<String> log) throws IOException {
        log.accept("Decrypting bytes: " + BitConverter.toHex(Arrays.copyOf(data, 24)) + "...");

        // we use a stream that contains the encrypted data
        MemoryStream memoryStream = new MemoryStream(data);

        // and wrap it with a salmon stream to do the decryption
        AesStream decStream = new AesStream(key, nonce, EncryptionMode.Decrypt, memoryStream);

        // decrypt the data
        byte[] decData = new byte[(int) decStream.getLength()];
        int totalBytesRead = 0;
        int bytesRead = 0;
        while ((bytesRead = decStream.read(decData, totalBytesRead, DataStreamSample.BUFFER_SIZE)) > 0) {
            totalBytesRead += bytesRead;
        }

        decStream.close();
        memoryStream.close();

        log.accept("Bytes decrypted: " + BitConverter.toHex(Arrays.copyOf(decData, 24)) + "...");
        return decData;
    }
}