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
using Mku.Salmon.Text;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonCSharpTestRunner
{
    // NOTE: set to false out if you're building with gradle or running test cases for android
    public static readonly bool enableNativeLib = false;
    protected static readonly string TEST_OUTPUT_DIR = "d:\\tmp\\output";
    protected static readonly string TEST_VAULT_DIR = "d:\\tmp\\output\\enc";
    protected static readonly string TEST_VAULT2_DIR = "d:\\tmp\\output\\enc2";
    protected static readonly string TEST_EXPORT_AUTH_DIR = "d:\\tmp\\output\\export";
    protected static readonly string TEST_IMPORT_TINY_FILE = "d:\\tmp\\testdata\\tiny_test.txt";
    protected static readonly string TEST_IMPORT_SMALL_FILE = "d:\\tmp\\testdata\\small_test.zip";
    protected static readonly string TEST_IMPORT_MEDIUM_FILE = "d:\\tmp\\testdata\\medium_test.zip";
    protected static readonly string TEST_IMPORT_LARGE_FILE = "d:\\tmp\\testdata\\large_test.mp4";
    protected static readonly string TEST_IMPORT_HUGE_FILE = "d:\\tmp\\testdata\\huge.zip";
    protected static readonly string TEST_IMPORT_FILE = TEST_IMPORT_MEDIUM_FILE;


    [TestInitialize]
    public void Init()
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.Default;
        SalmonDefaultOptions.BufferSize = SalmonIntegrity.DEFAULT_CHUNK_SIZE;
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptText()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;


        string encText = SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        string decText = SalmonTextDecryptor.DecryptString(encText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.AreEqual(plainText, decText);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptTextWithHeader()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;


        string encText = SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        string decText = SalmonTextDecryptor.DecryptString(encText, TestHelper.TEST_KEY_BYTES, null, true);
        Assert.AreEqual(plainText, decText);
    }

    [TestMethod]
    public void ShouldEncryptCatchNoKey()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            SalmonTextEncryptor.EncryptString(plainText, null, TestHelper.TEST_NONCE_BYTES, true);
        }
        catch (SalmonSecurityException ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptCatchNoNonce()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, null, true);
        }
        catch (SalmonSecurityException ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptDecryptNoHeaderCatchNoNonce()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            string encText = SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
            SalmonTextDecryptor.DecryptString(encText, TestHelper.TEST_KEY_BYTES, null, false);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldEncryptDecryptCatchNoKey()
    {
        string plainText = TestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            string encText = SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
            SalmonTextDecryptor.DecryptString(encText, null, TestHelper.TEST_NONCE_BYTES, true);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptTextCompatible()
    {
        string plainText = TestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = TestHelper.DefaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = TestHelper.DefaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        CollectionAssert.AreEqual(bytes, decBytesDef);
        byte[] encBytes = new SalmonEncryptor().Encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        CollectionAssert.AreEqual(encBytesDef, encBytes);
        byte[] decBytes = new SalmonDecryptor().Decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        CollectionAssert.AreEqual(bytes, decBytes);
    }


    [TestMethod]
    public void ShouldEncryptAndDecryptTextCompatibleWithIntegrity()
    {
        string plainText = TestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = TestHelper.DefaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = TestHelper.DefaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);

        int threads = 1;
        int chunkSize = 256 * 1024;
        CollectionAssert.AreEqual(bytes, decBytesDef);
        byte[]
    encBytes = new SalmonEncryptor(threads).Encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
                true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        AssertAreEqualWithIntegrity(encBytesDef, encBytes, chunkSize);
        byte[] decBytes = new SalmonDecryptor(threads).Decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false,
                true, TestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        CollectionAssert.AreEqual(bytes, decBytes);
    }

    private void AssertAreEqualWithIntegrity(byte[] buffer, byte[] bufferWithIntegrity, int chunkSize)
    {
        int index = 0;
        for (int i = 0; i < buffer.Length; i += chunkSize)
        {
            int nChunkSize = Math.Min(chunkSize, buffer.Length - i);
            byte[] buff1 = new byte[chunkSize];
            Array.Copy(buffer, i, buff1, 0, nChunkSize);

            byte[] buff2 = new byte[chunkSize];
            Array.Copy(bufferWithIntegrity, index + SalmonGenerator.HASH_RESULT_LENGTH, buff2, 0, nChunkSize);

            CollectionAssert.AreEqual(buff1, buff2);
            index += nChunkSize + SalmonGenerator.HASH_RESULT_LENGTH;
        }
        Assert.AreEqual(bufferWithIntegrity.Length, index);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamNoBuffersSpecified()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 0, 0,
                    false, null, null, false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_DEC_BUFFER_SIZE,
                    false, null, null, false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE + 3, TestHelper.TEST_DEC_BUFFER_SIZE + 3,
                    false, null, null, false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamAlignedBuffer()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
                    false, null, null, false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamDecNoAlignedBuffer()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    16 * 2, 16 * 2 + 3,
                    false, null, null, false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                    false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                    false, null, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    0, 0,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
                    64
            );
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    0, 0,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, TestHelper.TEST_HEADER,
                    128
            );
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 0, TestHelper.TEST_HMAC_KEY_BYTES, false, null, 32);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    128, 128, true, 64, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);

    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);


    }


    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned()
    {
        TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
    }


    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, false, null, null
            );
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);


    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES, true, null, null
            );
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldNotReadFromStreamEncryptionMode()
    {
        string testText = TestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++)
        {
            tBuilder.Append(testText);
        }
        string plainText = tBuilder.ToString();
        bool caught = false;
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        MemoryStream ins = new MemoryStream(inputBytes);
        MemoryStream outs = new MemoryStream();
        SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Encrypt, outs,
                null, false, null, null);
        try
        {
            encWriter.CopyTo(outs);
        }
        catch (Exception)
        {
            caught = true;
        }
        ins.Close();
        encWriter.Flush();
        encWriter.Close();

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldNotWriteToStreamDecryptionMode()
    {
        string testText = TestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++)
        {
            tBuilder.Append(testText);
        }
        string plainText = tBuilder.ToString();
        bool caught = false;
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytes = TestHelper.Encrypt(inputBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, false, 0, null, null);

        MemoryStream ins = new MemoryStream(encBytes);
        SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Decrypt, ins,
                null, false, null, null);
        try
        {
            ins.CopyTo(encWriter);
        }
        catch (Exception)
        {
            caught = true;
        }
        ins.Close();
        encWriter.Flush();
        encWriter.Close();

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldSeekAndReadNoIntegrity()
    {
        TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    }


    [TestMethod]
    public void ShouldSeekAndTestBlockAndCounter()
    {
        TestHelper.SeekTestCounterAndBlock(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, 0, null);
    }


    [TestMethod]
    public void ShouldSeekAndReadWithIntegrity()
    {
        TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 0, TestHelper.TEST_HMAC_KEY_BYTES);
    }

    [TestMethod]
    public void ShouldSeekAndReadWithIntegrityMultiChunks()
    {
        TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 32, TestHelper.TEST_HMAC_KEY_BYTES);
    }

    [TestMethod]
    public void ShouldSeekAndWriteNoIntegrity()
    {
        TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16,
                    TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, false, 0, null, true);
    }

    [TestMethod]
    public void ShouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5,
                    TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, false, 0, null, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonSecurityException))
                caught = true;
        }

        Assert.IsTrue(caught);

    }

    [TestMethod]
    public void ShouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative()
    {
        bool caught = false;
        try
        {
            TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, false,
                    32, TestHelper.TEST_HMAC_KEY_BYTES, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldCatchCTROverflow()
    {
        bool caught = false;
        try
        {
            TestHelper.TestCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            if (ex.InnerException.GetType() == typeof(SalmonRangeExceededException) || ex.InnerException.GetType() == typeof(ArgumentOutOfRangeException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldHoldCTRValue()
    {
        bool caught = false;
        try
        {
            TestHelper.TestCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER - 1L);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            if (ex.GetType() == typeof(SalmonRangeExceededException))
                caught = true;
        }

        Assert.IsFalse(caught);
    }

    [TestMethod]
    public void ShouldCalcHMac256()
    {
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(TestHelper.TEST_TEXT);
        byte[] hash = TestHelper.CalculateHMAC(bytes, 0, bytes.Length, TestHelper.TEST_HMAC_KEY_BYTES, null);
        foreach (byte b in hash) Console.Write(b.ToString("x2") + " ");
        Console.WriteLine();
    }

    [TestMethod]
    public void ShouldConvert()
    {
        int num1 = 12564;
        byte[] bytes = BitConverter.ToBytes(num1, 4);
        int num2 = (int)BitConverter.ToLong(bytes, 0, 4);

        Assert.AreEqual(num1, num2);


        long lnum1 = 56445783493L;
        bytes = BitConverter.ToBytes(lnum1, 8);
        long lnum2 = BitConverter.ToLong(bytes, 0, 8);

        Assert.AreEqual(lnum1, lnum2);

    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayMultipleThreads()
    {
        byte[]
    data = TestHelper.GetRandArray(1 * 1024 * 1024 + 4);
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = new SalmonEncryptor(2).Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = new SalmonDecryptor(2).Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrity()
    {
        SalmonDefaultOptions.BufferSize = 2 * 1024 * 1024;
        byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024 + 3);
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = new SalmonEncryptor(2).Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = new SalmonDecryptor(2).Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSize()
    {
        byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024);
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = new SalmonEncryptor(2).Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = new SalmonDecryptor(2).Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader()
    {
        byte[] data = TestHelper.GetRandArraySame(129 * 1024);
        long t1 = Mku.Time.Time.CurrentTimeMillis();
        byte[] encData = new SalmonEncryptor().Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                true, true, TestHelper.TEST_HMAC_KEY_BYTES, 32);
        long t2 = Mku.Time.Time.CurrentTimeMillis();
        byte[] decData = new SalmonDecryptor().Decrypt(encData, TestHelper.TEST_KEY_BYTES,
                null, true, true, TestHelper.TEST_HMAC_KEY_BYTES, null);
        long t3 = Mku.Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }


    [TestMethod]
    public void ShouldCopyMemory()
    {
        TestHelper.CopyMemory(4 * 1024 * 1024);
    }


    [TestMethod]
    public void ShouldCopyFromToMemoryStream()
    {
        TestHelper.CopyFromMemStream(1 * 1024 * 1024, 0);
        TestHelper.CopyFromMemStream(1 * 1024 * 1024, 32768);
    }

    [TestMethod]
    public void ShouldCopyFromMemoryStreamToSalmonStream()
    {
        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, null, TestHelper.TEST_HMAC_KEY_BYTES,
                    32768);

        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 256 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                    32768);

        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        TestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                    true, 128 * 1024, TestHelper.TEST_HMAC_KEY_BYTES,
                    32768);
    }
}
