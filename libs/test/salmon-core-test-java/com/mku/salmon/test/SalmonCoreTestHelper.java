package com.mku.salmon.test;

/*
MIT License

Copyright (c) 2021 Max Kas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import com.mku.convert.BitConverter;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import com.mku.salmon.SalmonDecryptor;
import com.mku.salmon.SalmonEncryptor;
import com.mku.salmon.SalmonGenerator;
import com.mku.salmon.SalmonSecurityException;
import com.mku.integrity.HmacSHA256Provider;
import com.mku.integrity.IHashProvider;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.salmon.integrity.IntegrityException;
import com.mku.salmon.streams.EncryptionMode;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import com.mku.salmon.transform.ISalmonCTRTransformer;
import com.mku.salmon.transform.SalmonAES256CTRTransformer;
import com.mku.salmon.transform.SalmonTransformerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SalmonCoreTestHelper {
    public static int TEST_ENC_BUFFER_SIZE = 512 * 1024;
    public static int TEST_ENC_THREADS = 2;
    public static int TEST_DEC_BUFFER_SIZE = 512 * 1024;
    public static int TEST_DEC_THREADS = 2;

    public static final String TEST_PASSWORD = "test123";
    public static final String TEST_FALSE_PASSWORD = "falsepass";

    public static final long MAX_ENC_COUNTER = (long) Math.pow(256, 7);
    // a nonce ready to overflow if a new file is imported
    public static final byte[] TEXT_VAULT_MAX_FILE_NONCE = {
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };

    private static final int TEXT_ITERATIONS = 1;
    public static String TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    public static byte[] TEST_KEY_BYTES = TEST_KEY.getBytes(Charset.defaultCharset());
    public static String TEST_NONCE = "12345678"; // 8 bytes
    public static byte[] TEST_NONCE_BYTES = TEST_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    public static byte[] TEST_FILENAME_NONCE_BYTES = TEST_FILENAME_NONCE.getBytes(Charset.defaultCharset());
    public static String TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    public static byte[] TEST_HMAC_KEY_BYTES = TEST_HMAC_KEY.getBytes(Charset.defaultCharset());

    public static String TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
    public static String TEST_TINY_TEXT = "test.txt";
    public static String TEST_TEXT = "This is another test that could be very long if used correctly.";
    public static String TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

    private static final Random random = new Random(System.currentTimeMillis());
    private static IHashProvider hashProvider;
    private static SalmonEncryptor encryptor;
    private static SalmonDecryptor decryptor;

    static void initialize() {
        SalmonCoreTestHelper.hashProvider = new HmacSHA256Provider();
        SalmonCoreTestHelper.encryptor = new SalmonEncryptor(SalmonCoreTestHelper.TEST_ENC_THREADS);
        SalmonCoreTestHelper.decryptor = new SalmonDecryptor(SalmonCoreTestHelper.TEST_DEC_THREADS);
    }

    static SalmonEncryptor getEncryptor() {
        return SalmonCoreTestHelper.encryptor;
    }

    static SalmonDecryptor getDecryptor() {
        return SalmonCoreTestHelper.decryptor;
    }

    static void close() {
        SalmonCoreTestHelper.encryptor.close();
        SalmonCoreTestHelper.decryptor.close();
    }


    public static String seekAndGetSubstringByRead(SalmonStream reader, int seek, int readCount, RandomAccessStream.SeekOrigin seekOrigin) throws Exception {
        reader.seek(seek, seekOrigin);
        MemoryStream encOuts2 = new MemoryStream();

        byte[] bytes = new byte[readCount];
        int bytesRead;
        long totalBytesRead = 0;
        while (totalBytesRead < readCount && (bytesRead = reader.read(bytes, 0, bytes.length)) > 0) {
            // we skip the alignment offset and start reading the bytes we need
            encOuts2.write(bytes, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        String decText1 = new String(encOuts2.toArray(), Charset.defaultCharset());
        encOuts2.close();
        return decText1;
    }

    public static void encryptWriteDecryptRead(String text, byte[] key, byte[] iv,
                                               int encBufferSize, int decBufferSize, boolean testIntegrity, Integer chunkSize,
                                               byte[] hashKey, boolean flipBits, String header, Integer maxTextLength) throws Exception {
        String testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();
        if (maxTextLength != null && maxTextLength < plainText.length())
            plainText = plainText.substring(0, maxTextLength);

        int headerLength = 0;
        if (header != null)
            headerLength = header.getBytes(Charset.defaultCharset()).length;
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytes = encrypt(inputBytes, key, iv, encBufferSize,
                testIntegrity, chunkSize, hashKey, header);
        if (flipBits)
            encBytes[encBytes.length / 2] = 0;

        // Use SalmonStream to read from cipher byte array and MemoryStream to Write to byte array
        byte[] outputByte2 = decrypt(encBytes, key, iv, decBufferSize,
                testIntegrity, chunkSize, hashKey, header != null ? headerLength :
                        null);
        String decText = new String(outputByte2, Charset.defaultCharset());

        System.out.println(plainText);
        System.out.println(decText);

        assertEquals(plainText, decText);
    }

    public static byte[] encrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 boolean integrity, Integer chunkSize, byte[] hashKey,
                                 String header) throws Exception {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (header != null) {
            headerData = header.getBytes(Charset.defaultCharset());
            outs.write(headerData, 0, headerData.length);
        }
        SalmonStream writer = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffer size of the memorystream to copy
        {
            ins.copyTo(writer);
        } else { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = ins.read(buffer, 0, buffer.length)) > 0) {
                writer.write(buffer, 0, bytesRead);
            }
        }
        writer.flush();
        byte[] bytes = outs.toArray();
        writer.close();
        ins.close();
        return bytes;
    }

    public static byte[] decrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 Boolean integrity, Integer chunkSize, byte[] hashKey,
                                 Integer headerLength) throws Exception {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (headerLength != null) {
            headerData = new byte[(int) headerLength];
            ins.read(headerData, 0, headerData.length);
        }
        SalmonStream reader = new SalmonStream(key, iv, EncryptionMode.Decrypt, ins,
                headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
        {
            reader.copyTo(outs);
        } else { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = reader.read(buffer, 0, buffer.length)) > 0) {
                outs.write(buffer, 0, bytesRead);
            }
        }
        outs.flush();
        byte[] bytes = outs.toArray();
        reader.close();
        outs.close();
        return bytes;
    }

    public static void seekAndRead(String text, byte[] key, byte[] iv,
                                   boolean integrity, int chunkSize, byte[] hashKey) throws Exception {
        String testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(testText);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        String correctText;
        String decText;

        correctText = plainText.substring(0, 6);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, 6, RandomAccessStream.SeekOrigin.Begin);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(0, 6);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, 6, RandomAccessStream.SeekOrigin.Begin);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.getPosition() + 4, (int) decReader.getPosition() + 4 + 4);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 4, 4, RandomAccessStream.SeekOrigin.Current);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.getPosition() + 6, (int) decReader.getPosition() + 6 + 4);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 6, 4, RandomAccessStream.SeekOrigin.Current);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring((int) decReader.getPosition() + 10, (int) decReader.getPosition() + 10 + 6);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 10, 6, RandomAccessStream.SeekOrigin.Current);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(12, 12 + 8);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 12, 8, RandomAccessStream.SeekOrigin.Begin);

        assertEquals(correctText, decText);
        testCounter(decReader);

        correctText = plainText.substring(plainText.length() - 14, plainText.length() - 14 + 7);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 14, 7, RandomAccessStream.SeekOrigin.End);

        assertEquals(correctText, decText);

        correctText = plainText.substring(plainText.length() - 27, plainText.length() - 27 + 12);
        decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 27, 12, RandomAccessStream.SeekOrigin.End);

        assertEquals(correctText, decText);
        testCounter(decReader);
        encIns.close();
        decReader.close();
    }

    public static void seekTestCounterAndBlock(String text, byte[] key, byte[] iv,
                                               boolean integrity, int chunkSize, byte[] hashKey) throws Exception {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(text);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // Use SalmonStream to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        for (int i = 0; i < 100; i++) {
            decReader.setPosition(decReader.getPosition() + 7);
            testCounter(decReader);
        }

        encIns.close();
        decReader.close();
    }

    private static void testCounter(SalmonStream decReader) throws IOException {
        long expectedBlock = decReader.getPosition() / SalmonAES256CTRTransformer.BLOCK_SIZE;

        assertEquals(expectedBlock, decReader.getBlock());

        long counterBlock = BitConverter.toLong(decReader.getCounter(), SalmonGenerator.NONCE_LENGTH,
                SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
        long expectedCounterValue = decReader.getBlock();

        assertEquals(expectedCounterValue, counterBlock);

        long nonce = BitConverter.toLong(decReader.getCounter(), 0, SalmonGenerator.NONCE_LENGTH);
        long expectedNonce = BitConverter.toLong(decReader.getNonce(), 0, SalmonGenerator.NONCE_LENGTH);

        assertEquals(expectedNonce, nonce);
    }

    public static void seekAndWrite(String text, byte[] key, byte[] iv,
                                    long seek, int writeCount, String textToWrite,
                                    boolean integrity, int chunkSize, byte[] hashKey,
                                    boolean setAllowRangeWrite
    ) throws Exception {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++) {
            tBuilder.append(text);
        }
        String plainText = tBuilder.toString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = plainText.getBytes(Charset.defaultCharset());
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.copyTo(encWriter);
        ins.close();
        encWriter.flush();
        encWriter.close();
        byte[] encBytes = outs.toArray();

        // partial write
        byte[] writeBytes = textToWrite.getBytes(Charset.defaultCharset());
        MemoryStream pOuts = new MemoryStream(encBytes);
        SalmonStream partialWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, pOuts,
                null, integrity, chunkSize, hashKey);
        long alignedPosition = seek;
        int alignOffset = 0;
        int count = writeCount;

        // set to allow rewrite
        if (setAllowRangeWrite)
            partialWriter.setAllowRangeWrite(setAllowRangeWrite);
        partialWriter.seek(alignedPosition, RandomAccessStream.SeekOrigin.Begin);
        partialWriter.write(writeBytes, 0, count);
        partialWriter.close();
        pOuts.close();

        // Use SalmonStrem to read from cipher text and test if writing was successful
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        String decText = SalmonCoreTestHelper.seekAndGetSubstringByRead(decReader, 0, text.length(), RandomAccessStream.SeekOrigin.Begin);

        assertEquals(text.substring(0, (int) seek), decText.substring(0, (int) seek));

        assertEquals(textToWrite, decText.substring((int) seek, (int) seek + writeCount));

        assertEquals(text.substring((int) seek + writeCount), decText.substring((int) seek + writeCount));
        testCounter(decReader);

        encIns.close();
        decReader.close();
    }

    public static void testCounterValue(String text, byte[] key, byte[] nonce, long counter) throws Throwable {
        SalmonStream.setAesProviderType(ProviderType.Default);
        byte[] testTextBytes = text.getBytes(Charset.defaultCharset());
        MemoryStream ms = new MemoryStream(testTextBytes);
        SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.setAllowRangeWrite(true);

        // creating enormous files to test is overkill and since the law was made for man
        // we use reflection to test this.
        Field transformerField = SalmonStream.class.getDeclaredField("transformer");
        transformerField.setAccessible(true);
        SalmonAES256CTRTransformer transformer = (SalmonAES256CTRTransformer) transformerField.get(stream);

        Method incrementCounter = SalmonAES256CTRTransformer.class.getDeclaredMethod("increaseCounter", long.class);
        incrementCounter.setAccessible(true);
        try {
            incrementCounter.invoke(transformer, counter);
        } catch (Exception ex) {
            if (ex.getCause() != null)
                throw ex.getCause();
        } finally {
            stream.close();
        }
    }

    public static byte[] defaultAESCTRTransform(byte[] plainText, byte[] testKeyBytes, byte[] testNonceBytes, boolean encrypt)
            throws Exception {

        if (testNonceBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(testNonceBytes, 0, tmp, 0, testNonceBytes.length);
            testNonceBytes = tmp;
        }
        SecretKeySpec key = new SecretKeySpec(testKeyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        IvParameterSpec ivSpec = new IvParameterSpec(testNonceBytes);
        // mode doesn't make a difference since the encryption is symmetrical
        if (encrypt)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        else
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText);
        return encrypted;
    }

    public static byte[] nativeCTRTransform(byte[] input, byte[] testKeyBytes, byte[] testNonceBytes,
                                            boolean encrypt, ProviderType providerType)
            throws Exception {
        if (testNonceBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(testNonceBytes, 0, tmp, 0, testNonceBytes.length);
            testNonceBytes = tmp;
        }
        ISalmonCTRTransformer transformer = SalmonTransformerFactory.create(providerType);
        transformer.init(testKeyBytes, testNonceBytes);
        byte[] output = new byte[input.length];
        transformer.resetCounter();
        transformer.syncCounter(0);
        if (encrypt)
            transformer.encryptData(input, 0, output, 0, input.length);
        else
            transformer.decryptData(input, 0, output, 0, input.length);
        return output;
    }

    public static byte[] getRandArray(int size) {
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    public static byte[] getRandArraySame(int size) {
        byte[] data = new byte[size];
        Random r = new Random();
        r.nextBytes(data);
        return data;
    }

    public static void encryptAndDecryptByteArray(int size, boolean enableLog) throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(size);
        encryptAndDecryptByteArray(data, enableLog);
    }

    public static void encryptAndDecryptByteArray(byte[] data, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = encryptor.encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        long t2 = System.currentTimeMillis();
        byte[] decData = decryptor.decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        if (enableLog) {
            System.out.println("enc time: " + (t2 - t1));
            System.out.println("dec time: " + (t3 - t2));
            System.out.println("Total: " + (t3 - t1));
        }
    }
	
    public static void encryptAndDecryptByteArrayNative(int size, boolean enableLog) throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(size);
        encryptAndDecryptByteArrayNative(data, enableLog);
    }

    public static void encryptAndDecryptByteArrayNative(byte[] data, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.nativeCTRTransform(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true,
                SalmonStream.getAesProviderType());
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.nativeCTRTransform(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                SalmonStream.getAesProviderType());
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        if (enableLog) {
            System.out.println("enc time: " + (t2 - t1));
            System.out.println("dec time: " + (t3 - t2));
            System.out.println("Total: " + (t3 - t1));
        }
    }

    public static void encryptAndDecryptByteArrayDef(int size, boolean enableLog) throws Exception {
        byte[] data = SalmonCoreTestHelper.getRandArray(size);
        encryptAndDecryptByteArrayDef(data, enableLog);
    }

    public static void encryptAndDecryptByteArrayDef(byte[] data, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.defaultAESCTRTransform(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        long t2 = System.currentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.defaultAESCTRTransform(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        if (enableLog) {
            System.out.println("enc: " + (t2 - t1));
            System.out.println("dec: " + (t3 - t2));
            System.out.println("Total: " + (t3 - t1));
        }
    }

    public static void CopyMemory(int size) throws IOException {
        long t1 = System.currentTimeMillis();
        byte[] data = SalmonCoreTestHelper.getRandArray(size);
        long t2 = System.currentTimeMillis();
        long t3 = System.currentTimeMillis();
        System.out.println("gen time: " + (t2 - t1));
        System.out.println("copy time: " + (t3 - t2));

        byte[] mem = new byte[16];
        MemoryStream ms = new MemoryStream(mem);
        ms.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 3, 2);
        byte[] output = ms.toArray();
        System.out.println("write: " + Arrays.toString(output));
        byte[] buff = new byte[16];
        ms.setPosition(0);
        ms.read(buff, 1, 4);
        System.out.println("read: " + Arrays.toString(buff));
    }

    public static void copyFromMemStream(int size, int bufferSize) throws Exception {
        byte[] testData = getRandArray(size);
        MessageDigest md = MessageDigest.getInstance("SHA-256"); // TODO: replace MD5 with sha256
        ByteArrayInputStream is = new ByteArrayInputStream(testData);
        DigestInputStream dis = new DigestInputStream(is, md);
        byte[] digest = md.digest();
        is.close();
        dis.close();

        MemoryStream ms1 = new MemoryStream(testData);
        MemoryStream ms2 = new MemoryStream();
        ms1.copyTo(ms2, bufferSize, null);
        ms1.close();
        ms2.close();
        byte[] data2 = ms2.toArray();

        assertEquals(testData.length, data2.length);

        MessageDigest md2 = MessageDigest.getInstance("SHA-256");
        ByteArrayInputStream is2 = new ByteArrayInputStream(data2);
        DigestInputStream dis2 = new DigestInputStream(is2, md2);
        byte[] digest2 = md2.digest();
        ms1.close();
        ms2.close();
        dis2.close();

        assertArrayEquals(digest, digest2);

    }

    public static void copyFromMemStreamToSalmonStream(int size, byte[] key, byte[] nonce,
                                                       boolean integrity, Integer chunkSize, byte[] hashKey,
                                                       int bufferSize) throws Exception {

        byte[] testData = getRandArray(size);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        ByteArrayInputStream is = new ByteArrayInputStream(testData);
        DigestInputStream dis = new DigestInputStream(is, md);
        byte[] digest = md.digest();
        is.close();
        dis.close();

        // copy to a mem byte stream
        MemoryStream ms1 = new MemoryStream(testData);
        MemoryStream ms2 = new MemoryStream();
        ms1.copyTo(ms2, bufferSize, null);
        ms1.close();

        // encrypt to a memory byte stream
        ms2.setPosition(0);
        MemoryStream ms3 = new MemoryStream();
        SalmonStream salmonStream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms3,
                null, integrity, chunkSize, hashKey);
        // we always align the writes to the chunk size if we enable integrity
        if (integrity)
            bufferSize = salmonStream.getChunkSize();
        ms2.copyTo(salmonStream, bufferSize, null);
        salmonStream.close();
        ms2.close();
        byte[] encData = ms3.toArray();

        // decrypt
		ms3 = new MemoryStream(encData);
        ms3.setPosition(0);
        MemoryStream ms4 = new MemoryStream();
        SalmonStream salmonStream2 = new SalmonStream(key, nonce, EncryptionMode.Decrypt, ms3,
                null, integrity, chunkSize, hashKey);
        salmonStream2.copyTo(ms4, bufferSize, null);
        salmonStream2.close();
        ms3.close();
        ms4.setPosition(0);
        MessageDigest md2 = MessageDigest.getInstance("SHA-256");
        DigestInputStream dis2 = new DigestInputStream(new InputStreamWrapper(ms4), md2);
        byte[] digest2 = md2.digest();
        ms4.close();
        dis2.close();
    }

    public static byte[] calculateHMAC(byte[] bytes, int offset, int length,
                                       byte[] hashKey, byte[] includeData) throws IntegrityException, SalmonSecurityException {
        SalmonIntegrity salmonIntegrity = new SalmonIntegrity(true, hashKey, null, new HmacSHA256Provider(),
                SalmonGenerator.HASH_RESULT_LENGTH);
        return SalmonIntegrity.calculateHash(hashProvider, bytes, offset, length, hashKey, includeData);
    }
}