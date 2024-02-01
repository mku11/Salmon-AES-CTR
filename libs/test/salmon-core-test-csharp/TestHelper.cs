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

using Mku.Salmon.Integrity;
using Mku.Salmon.IO;
using Mku.Salmon.Transform;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;

using BitConverter = Mku.Convert.BitConverter;
using System.Security.Cryptography;
using System.Reflection;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Security;
using Org.BouncyCastle.Crypto;
using Mku.IO;

namespace Mku.Salmon.Test;

public class TestHelper
{
    public static readonly int TEST_ENC_BUFFER_SIZE = 512 * 1024;
    public static readonly int TEST_DEC_BUFFER_SIZE = 512 * 1024;

    public static readonly string TEST_PASSWORD = "test123";
    public static readonly string TEST_FALSE_PASSWORD = "falsepass";
    public static readonly string TEST_EXPORT_DIR = "export.slma";

    public static readonly long MAX_ENC_COUNTER = (long)Math.Pow(256, 7);
    // a nonce ready to overflow if a new file is imported
    public static readonly byte[] TEXT_VAULT_MAX_FILE_NONCE = {
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
    };
    public static readonly string TEST_SEQUENCER_FILE1 = "seq1.xml";
    public static readonly string TEST_SEQUENCER_FILE2 = "seq2.xml";

    private static readonly int TEXT_ITERATIONS = 1;
    public static string TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    public static byte[] TEST_KEY_BYTES = UTF8Encoding.UTF8.GetBytes(TEST_KEY);
    public static string TEST_NONCE = "12345678"; // 8 bytes
    public static byte[] TEST_NONCE_BYTES = UTF8Encoding.UTF8.GetBytes(TEST_NONCE);
    public static string TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
    public static byte[] TEST_FILENAME_NONCE_BYTES = UTF8Encoding.UTF8.GetBytes(TEST_FILENAME_NONCE);
    public static string TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes
    public static byte[] TEST_HMAC_KEY_BYTES = UTF8Encoding.UTF8.GetBytes(TEST_HMAC_KEY);

    public static string TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
    public static string TEST_TINY_TEXT = "test.txt";
    public static string TEST_TEXT = "This is another test that could be very long if used correctly.";
    public static string TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

    private static readonly Random random = new Random((int)Mku.Time.Time.CurrentTimeMillis());
    private static readonly IHashProvider hashProvider = new HmacSHA256Provider();

    public static string SeekAndGetSubstringByRead(SalmonStream reader, int seek, int readCount, SeekOrigin seekOrigin)
    {
        reader.Seek(seek, seekOrigin);
        MemoryStream encOuts2 = new MemoryStream();

        byte[] bytes = new byte[readCount];
        int bytesRead;
        long totalBytesRead = 0;
        while (totalBytesRead < readCount && (bytesRead = reader.Read(bytes, 0, bytes.Length)) > 0)
        {
            // we skip the alignment offset and start reading the bytes we need
            encOuts2.Write(bytes, 0, bytesRead);
            totalBytesRead += bytesRead;
        }
        string decText1 = UTF8Encoding.UTF8.GetString(encOuts2.ToArray());
        encOuts2.Close();
        return decText1;
    }


    public static void EncryptWriteDecryptRead(string text, byte[] key, byte[] iv,
                                               int encBufferSize, int decBufferSize, bool testIntegrity, int? chunkSize,
                                               byte[] hashKey, bool flipBits, string header, int? maxTextLength)
    {
        string testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++)
        {
            tBuilder.Append(testText);
        }
        string plainText = tBuilder.ToString();
        if (maxTextLength != null && maxTextLength < plainText.Length)
            plainText = plainText.Substring(0, (int)maxTextLength);

        int headerLength = 0;
        if (header != null)
            headerLength = UTF8Encoding.UTF8.GetBytes(header).Length;
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytes = Encrypt(inputBytes, key, iv, encBufferSize,
                testIntegrity, chunkSize, hashKey, header);
        if (flipBits)
            encBytes[encBytes.Length / 2] = 0;

        // Use SalmonStream to read from cipher byte array and MemoryStream to Write to byte array
        byte[] outputByte2 = Decrypt(encBytes, key, iv, decBufferSize,
                testIntegrity, chunkSize, hashKey, header != null ? headerLength :
                        null);
        string decText = UTF8Encoding.UTF8.GetString(outputByte2);

        Console.WriteLine(plainText);
        Console.WriteLine(decText);

        Assert.AreEqual(plainText, decText);
    }


    public static byte[] Encrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 bool integrity, int? chunkSize, byte[] hashKey,
                                 string header)
    {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (header != null)
        {
            headerData = UTF8Encoding.UTF8.GetBytes(header);
            outs.Write(headerData, 0, headerData.Length);
        }
        SalmonStream writer = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffer size of the memorystream to copy
        {
            ins.CopyTo(writer);
        }
        else
        { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0)
            {
                writer.Write(buffer, 0, bytesRead);
            }
        }
        writer.Flush();
        byte[] bytes = outs.ToArray();
        writer.Close();
        ins.Close();
        return bytes;
    }

    public static byte[] Decrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
                                 bool integrity, int? chunkSize, byte[] hashKey,
                                 int? headerLength)
    {
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        byte[] headerData = null;
        if (headerLength != null)
        {
            headerData = new byte[(int)headerLength];
            ins.Read(headerData, 0, headerData.Length);
        }
        SalmonStream reader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, ins,
                headerData, integrity, chunkSize, hashKey);

        if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
        {
            reader.CopyTo(outs);
        }
        else
        { // use our manual buffer to test
            int bytesRead;
            byte[] buffer = new byte[bufferSize];
            while ((bytesRead = reader.Read(buffer, 0, buffer.Length)) > 0)
            {
                outs.Write(buffer, 0, bytesRead);
            }
        }
        outs.Flush();
        byte[] bytes = outs.ToArray();
        reader.Close();
        outs.Close();
        return bytes;
    }


    public static void SeekAndRead(string text, byte[] key, byte[] iv,
                                   bool integrity, int chunkSize, byte[] hashKey)
    {
        string testText = text;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++)
        {
            tBuilder.Append(testText);
        }
        string plainText = tBuilder.ToString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.CopyTo(encWriter);
        ins.Close();
        encWriter.Flush();
        encWriter.Close();
        byte[] encBytes = outs.ToArray();

        // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        string correctText;
        string decText;

        correctText = plainText.Substring(0, 6);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring(0, 6);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring((int)decReader.Position + 4, 4);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 4, 4, SeekOrigin.Current);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring((int)decReader.Position + 6, 4);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 6, 4, SeekOrigin.Current);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring((int)decReader.Position + 10, 6);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 10, 6, SeekOrigin.Current);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring(12, 8);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 12, 8, SeekOrigin.Begin);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);

        correctText = plainText.Substring(plainText.Length - 14, 7);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 14, 7, SeekOrigin.End);

        Assert.AreEqual(correctText, decText);

        correctText = plainText.Substring(plainText.Length - 27, 12);
        decText = TestHelper.SeekAndGetSubstringByRead(decReader, 27, 12, SeekOrigin.End);

        Assert.AreEqual(correctText, decText);
        TestCounter(decReader);
        encIns.Close();
        decReader.Close();
    }

    public static void SeekTestCounterAndBlock(string text, byte[] key, byte[] iv,
                                               bool integrity, int chunkSize, byte[] hashKey)
    {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++)
        {
            tBuilder.Append(text);
        }
        string plainText = tBuilder.ToString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.CopyTo(encWriter);
        ins.Close();
        encWriter.Flush();
        encWriter.Close();
        byte[] encBytes = outs.ToArray();

        // Use SalmonStream to read from cipher text and seek and read to different positions in the stream
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        for (int i = 0; i < 100; i++)
        {
            decReader.Position = decReader.Position + 7;
            TestCounter(decReader);
        }

        encIns.Close();
        decReader.Close();
    }

    private static void TestCounter(SalmonStream decReader)
    {
        long expectedBlock = decReader.Position / SalmonAES256CTRTransformer.BLOCK_SIZE;

        Assert.AreEqual(expectedBlock, decReader.Block);

        long counterBlock = BitConverter.ToLong(decReader.Counter, SalmonGenerator.NONCE_LENGTH,
                SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
        long expectedCounterValue = decReader.Block;

        Assert.AreEqual(expectedCounterValue, counterBlock);

        long nonce = BitConverter.ToLong(decReader.Counter, 0, SalmonGenerator.NONCE_LENGTH);
        long expectedNonce = BitConverter.ToLong(decReader.Nonce, 0, SalmonGenerator.NONCE_LENGTH);

        Assert.AreEqual(expectedNonce, nonce);
    }

    public static void SeekAndWrite(string text, byte[] key, byte[] iv,
                                    long seek, int writeCount, string textToWrite,
                                    bool integrity, int chunkSize, byte[] hashKey,
                                    bool setAllowRangeWrite
    )
    {

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < TEXT_ITERATIONS; i++)
        {
            tBuilder.Append(text);
        }
        string plainText = tBuilder.ToString();

        // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, outs,
                null, integrity, chunkSize, hashKey);
        ins.CopyTo(encWriter);
        ins.Close();
        encWriter.Flush();
        encWriter.Close();
        byte[] encBytes = outs.ToArray();

        // partial write
        byte[] writeBytes = UTF8Encoding.UTF8.GetBytes(textToWrite);
        MemoryStream pOuts = new MemoryStream(encBytes);
        SalmonStream partialWriter = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Encrypt, pOuts,
                null, integrity, chunkSize, hashKey);
        long alignedPosition = seek;
        int count = writeCount;

        // set to allow rewrite
        if (setAllowRangeWrite)
            partialWriter.AllowRangeWrite = setAllowRangeWrite;
        partialWriter.Seek(alignedPosition, SeekOrigin.Begin);
        partialWriter.Write(writeBytes, 0, count);
        partialWriter.Close();
        pOuts.Close();


        // Use SalmonStrem to read from cipher text and test if writing was successful
        MemoryStream encIns = new MemoryStream(encBytes);
        SalmonStream decReader = new SalmonStream(key, iv, SalmonStream.EncryptionMode.Decrypt, encIns,
                null, integrity, chunkSize, hashKey);
        string decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, text.Length, SeekOrigin.Begin);


        Assert.AreEqual(text.Substring(0, (int)seek), decText.Substring(0, (int)seek));

        Assert.AreEqual(textToWrite, decText.Substring((int)seek, writeCount));

        Assert.AreEqual(text.Substring((int)seek + writeCount), decText.Substring((int)seek + writeCount));
        TestCounter(decReader);


        encIns.Close();
        decReader.Close();
    }

    public static void TestCounterValue(string text, byte[] key, byte[] nonce, long counter)
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.Default;
        byte[] testTextBytes = UTF8Encoding.UTF8.GetBytes(text);
        MemoryStream ms = new MemoryStream(testTextBytes);
        SalmonStream stream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.AllowRangeWrite = true;

        // we resort to reflection to test this.
        FieldInfo fieldInfo =
           stream.GetType().GetField("transformer", BindingFlags.Instance | BindingFlags.NonPublic);
        SalmonDefaultTransformer transformer = (SalmonDefaultTransformer)fieldInfo.GetValue(stream);
        MethodInfo methodInfo = transformer.GetType().GetMethod("IncreaseCounter", BindingFlags.Instance | BindingFlags.NonPublic);
        methodInfo.Invoke(transformer, new object[] { counter });

        if (stream != null)
            stream.Close();
    }

    public static byte[] DefaultAESCTRTransform(byte[] plainText, byte[] testKeyBytes, byte[] testNonceBytes, bool encrypt)
    {
        if (testNonceBytes.Length < 16)
        {
            byte[] tmp = new byte[16];
            Array.Copy(testNonceBytes, 0, tmp, 0, testNonceBytes.Length);
            testNonceBytes = tmp;
        }
        IBufferedCipher cipher = CipherUtilities.GetCipher("AES/CTR/NoPadding");
        cipher.Init(true, new ParametersWithIV(ParameterUtilities.CreateKeyParameter("AES", testKeyBytes), testNonceBytes));
        byte[] encryptedBytes = cipher.DoFinal(plainText);
        return encryptedBytes;
    }

    public static byte[] NativeCTRTransform(byte[] input, byte[] testKeyBytes, byte[] testNonceBytes,
                                            bool encrypt, SalmonStream.ProviderType providerType)
    {
        if (testNonceBytes.Length < 16)
        {
            byte[] tmp = new byte[16];
            Array.Copy(testNonceBytes, 0, tmp, 0, testNonceBytes.Length);
            testNonceBytes = tmp;
        }
        ISalmonCTRTransformer transformer = SalmonTransformerFactory.Create(providerType);
        transformer.Init(testKeyBytes, testNonceBytes);
        byte[] output = new byte[input.Length];
        transformer.ResetCounter();
        transformer.SyncCounter(0);
        if (encrypt)
            transformer.EncryptData(input, 0, output, 0, input.Length);
        else
            transformer.DecryptData(input, 0, output, 0, input.Length);
        return output;
    }


    public static byte[] GetRandArray(int size)
    {
        byte[] data = new byte[size];
        random.NextBytes(data);
        return data;
    }

    public static byte[] GetRandArraySame(int size)
    {
        byte[] data = new byte[size];
        Random r = new Random();
        r.NextBytes(data);
        return data;
    }

    public static void EncryptAndDecryptByteArray(int size, bool enableLog)
    {
        EncryptAndDecryptByteArray(size, 1, enableLog);
    }

    public static void EncryptAndDecryptByteArray(int size, int threads, bool enableLog)
    {
        byte[] data = TestHelper.GetRandArray(size);
        EncryptAndDecryptByteArray(data, threads, enableLog);
    }

    public static void EncryptAndDecryptByteArray(byte[] data, int threads, bool enableLog)
    {
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = new SalmonEncryptor(threads).Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = new SalmonDecryptor(threads).Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        if (enableLog)
        {
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
            Console.WriteLine("Total: " + (t3 - t1));
        }
    }

    public static void EncryptAndDecryptByteArrayNative(int size, bool enableLog)
    {
        byte[] data = TestHelper.GetRandArray(size);
        EncryptAndDecryptByteArrayNative(data, enableLog);
    }

    public static void EncryptAndDecryptByteArrayNative(byte[] data, bool enableLog)
    {
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = TestHelper.NativeCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true,
                SalmonStream.AesProviderType);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = TestHelper.NativeCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
                SalmonStream.AesProviderType);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        if (enableLog)
        {
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
            Console.WriteLine("Total: " + (t3 - t1));
        }
    }

    public static void EncryptAndDecryptByteArrayDef(int size, bool enableLog)
    {
        byte[] data = TestHelper.GetRandArray(size);
        EncryptAndDecryptByteArrayDef(data, enableLog);
    }

    public static void EncryptAndDecryptByteArrayDef(byte[] data, bool enableLog)
    {
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = TestHelper.DefaultAESCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = TestHelper.DefaultAESCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        if (enableLog)
        {
            Console.WriteLine("enc: " + (t2 - t1));
            Console.WriteLine("dec: " + (t3 - t2));
            Console.WriteLine("Total: " + (t3 - t1));
        }
    }

    public static void CopyMemory(int size)
    {
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] data = TestHelper.GetRandArray(size);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        long t3 = Mku.Time.Time.CurrentTimeMillis();
        Console.WriteLine("gen time: " + (t2 - t1));
        Console.WriteLine("copy time: " + (t3 - t2));

        byte[] mem = new byte[16];
        MemoryStream ms = new MemoryStream(mem);
        ms.Write(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 3, 2);
        byte[] output = ms.ToArray();
        Console.WriteLine("write: " + string.Join(",", output));
        byte[] buff = new byte[16];
        ms.Position = 0;
        ms.Read(buff, 1, 4);
        Console.WriteLine("read: " + string.Join(",", buff));
    }


    public static void CopyFromMemStream(int size, int bufferSize)
    {
        byte[] testData = GetRandArray(size);
        MD5 md = MD5.Create();
        MemoryStream inStream = new MemoryStream(testData);
        byte[] digest = md.ComputeHash(inStream);
        inStream.Close();

        MemoryStream ms1 = new MemoryStream(testData);
        MemoryStream ms2 = new MemoryStream();
        ms1.CopyTo(ms2, bufferSize, null);
        ms1.Close();
        ms2.Close();
        byte[] data2 = ms2.ToArray();

        Assert.AreEqual(testData.Length, data2.Length);

        md = MD5.Create();
        MemoryStream is2 = new MemoryStream(data2);
        byte[] digest2 = md.ComputeHash(is2);
        ms1.Close();
        ms2.Close();

        CollectionAssert.AreEqual(digest, digest2);

    }

    public static void CopyFromMemStreamToSalmonStream(int size, byte[] key, byte[] nonce,
                                                       bool integrity, int? chunkSize, byte[] hashKey,
                                                       int bufferSize)
    {

        byte[] testData = GetRandArray(size);
        MD5 md = MD5.Create();
        MemoryStream inStream = new MemoryStream(testData);
        byte[] digest2 = md.ComputeHash(inStream);
        inStream.Close();

        // copy to a mem byte stream
        MemoryStream ms1 = new MemoryStream(testData);
        MemoryStream ms2 = new MemoryStream();
        ms1.CopyTo(ms2, bufferSize, null);
        ms1.Close();

        // encrypt to a memory byte stream
        ms2.Position = 0;
        MemoryStream ms3 = new MemoryStream();
        SalmonStream salmonStream = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Encrypt, ms3,
                null, integrity, chunkSize, hashKey);
        // we always align the writes to the chunk size if we enable integrity
        if (integrity)
            bufferSize = salmonStream.ChunkSize;
        ms2.CopyTo(salmonStream, bufferSize, null);
        salmonStream.Close();
        ms2.Close();
        byte[] encData = ms3.ToArray();

        // decrypt
        ms3 = new MemoryStream(encData);
        ms3.Position = 0;
        MemoryStream ms4 = new MemoryStream();
        SalmonStream salmonStream2 = new SalmonStream(key, nonce, SalmonStream.EncryptionMode.Decrypt, ms3,
                null, integrity, chunkSize, hashKey);
        salmonStream2.CopyTo(ms4, bufferSize);
        salmonStream2.Close();
        ms3.Close();
        ms4.Position = 0;
        md = MD5.Create();
        byte[] digest3 = md.ComputeHash(ms4);
        ms4.Close();
    }

    public static string GenerateFolder(string dirPath)
    {
        long time = Mku.Time.Time.CurrentTimeMillis();
        DirectoryInfo dir = Directory.CreateDirectory(dirPath + "_" + time);
        if (dir == null)
            return null;
        return dir.FullName;
    }

    public static byte[] CalculateHMAC(byte[] bytes, int offset, int length,
                                       byte[] hashKey, byte[] includeData)
    {
        SalmonIntegrity salmonIntegrity = new SalmonIntegrity(true, hashKey, null, new HmacSHA256Provider(),
                SalmonGenerator.HASH_RESULT_LENGTH);
        return SalmonIntegrity.CalculateHash(hashProvider, bytes, offset, length, hashKey, includeData);
    }
}