package com.mku.salmon.samples.samples;

import com.mku.fs.file.IFile;
import com.mku.salmon.Generator;
import com.mku.salmon.integrity.Integrity;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.file.AesFile;
import com.mku.streams.RandomAccessStream;

import java.io.IOException;

public class FileSample {
    static int BUFFER_SIZE = 256 * 1024; // recommended buffer size aligned to internal buffers

    public static void encryptTextToFile(String text, byte[] key, byte[] integrityKey, IFile file) throws IOException {
        // encrypt to a file, the AesFile has a virtual file system API
        System.out.println("Encrypting text to file: " + file.getName());

        byte[] data = text.getBytes();

        // Always request a new random secure nonce
        byte[] nonce = Generator.getSecureRandomBytes(8); // 64 bit nonce

        AesFile encFile = new AesFile(file);
        encFile.setEncryptionKey(key);
        encFile.setRequestedNonce(nonce);

        if (integrityKey != null)
            encFile.setApplyIntegrity(true, integrityKey, Integrity.DEFAULT_CHUNK_SIZE);
        else
            encFile.setApplyIntegrity(false);

        RandomAccessStream encStream = encFile.getOutputStream();

        // now write the data you want to decrypt
        // it is recommended to use a large enough buffer while writing the data
        // for better performance
        int totalBytesWritten = 0;
        while (totalBytesWritten < data.length) {
            int length = Math.min(data.length - totalBytesWritten, FileSample.BUFFER_SIZE);
            encStream.write(data, totalBytesWritten, length);
            totalBytesWritten += length;
        }
        encStream.flush();
        encStream.close();
    }

    public static String decryptTextFromFile(byte[] key, byte[] integrityKey, IFile file) throws IOException {
        System.out.println("Decrypting text from file: " + file.getName());

        // Wrap the file with a AesFile
        // the nonce is already embedded in the header
        AesFile encFile = new AesFile(file);

        // set the key
        encFile.setEncryptionKey(key);

        if (integrityKey != null)
            encFile.setVerifyIntegrity(true, integrityKey);
        else
            encFile.setVerifyIntegrity(false, null);

        // open a read stream
        AesStream decStream = encFile.getInputStream();

        // decrypt the data
        byte[] decData = new byte[(int) decStream.getLength()];
        int totalBytesRead = 0;
        int bytesRead = 0;
        while ((bytesRead = decStream.read(decData, totalBytesRead, FileSample.BUFFER_SIZE)) > 0) {
            totalBytesRead += bytesRead;
        }

        String decText = new String(decData, 0, totalBytesRead);
        decStream.close();

        return decText;
    }
}