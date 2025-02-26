package com.mku.salmon.samples.samples;

import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.password.SalmonPassword;
import com.mku.streams.MemoryStream;

import java.util.Random;

public class SamplesCommon {
    static final Random random = new Random(System.currentTimeMillis());

    // create an encryption key from a text password
    public static byte[] getKeyFromPassword(String password) {
        // generate a salt
        byte[] salt = SalmonGenerator.getSecureRandomBytes(24);
        // make sure the iterations are a large enough number
        int iterations = 60000;

        // generate a 256bit key from the text password
        byte[] key = SalmonPassword.getKeyFromPassword(password, salt, iterations, 32);

        return key;
    }

    public static byte[] generateRandomData(int size) {
        MemoryStream memoryStream = new MemoryStream();
        byte[] buffer = new byte[65536];
        int len = 0;
        while (size > 0) {
            random.nextBytes(buffer);
            len = Math.min(size, buffer.length);
            memoryStream.write(buffer, 0, len);
            size -= len;
        }
        memoryStream.flush();
        memoryStream.close();
        return memoryStream.toArray();
    }
}