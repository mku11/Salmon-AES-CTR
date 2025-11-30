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
using Mku.Salmon.Streams;
using Mku.Salmon.Text;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonCoreTests
{
    [ClassInitialize]
    public static void ClassInitialize(TestContext testContext)
    {
        int threads = Environment.GetEnvironmentVariable("ENC_THREADS") != null && !Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
            int.Parse(Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;

        Console.WriteLine("threads: " + threads);
        //SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        //SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonCoreTestHelper.Initialize();

        ProviderType providerType = ProviderType.Default;
        string aesProviderType = Environment.GetEnvironmentVariable("AES_PROVIDER_TYPE");
        if (aesProviderType != null && !aesProviderType.Equals(""))
            providerType = (ProviderType)Enum.Parse(typeof(ProviderType), aesProviderType);
        Console.WriteLine("ProviderType: " + providerType);

        AesStream.AesProviderType = providerType;
    }

    [ClassCleanup]
    public static void ClassCleanup()
    {
        SalmonCoreTestHelper.Close();
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptText()
    {
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;


        string encText = TextEncryptor.EncryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        string decText = TextDecryptor.DecryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        Assert.AreEqual(plainText, decText);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptTextWithHeader()
    {
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;

        string encText = TextEncryptor.EncryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        string decText = TextDecryptor.DecryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, null);
        Assert.AreEqual(plainText, decText);
    }

    [TestMethod]
    public void ShouldEncryptCatchNoKey()
    {
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            TextEncryptor.EncryptString(plainText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        }
        catch (SecurityException ex)
        {
            Console.WriteLine("Caught: " + ex.Message);
            caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptCatchNoNonce()
    {
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            TextEncryptor.EncryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, null);
        }
        catch (SecurityException ex)
        {
            Console.WriteLine("Caught: " + ex.Message);
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
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            string encText = TextEncryptor.EncryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
            TextDecryptor.DecryptString(encText, SalmonCoreTestHelper.TEST_KEY_BYTES, null, EncryptionFormat.Generic);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Caught: " + ex.Message);
            caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldEncryptDecryptCatchNoKey()
    {
        string plainText = SalmonCoreTestHelper.TEST_TINY_TEXT;
        bool caught = false;

        try
        {
            string encText = TextEncryptor.EncryptString(plainText, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
            TextDecryptor.DecryptString(encText, null, SalmonCoreTestHelper.TEST_NONCE_BYTES);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Caught: " + ex.Message);
            caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptTextCompatible()
    {
        string plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        CollectionAssert.AreEqual(bytes, decBytesDef);
        byte[] encBytes = new Encryptor().Encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);

        CollectionAssert.AreEqual(encBytesDef, encBytes);
        byte[] decBytes = new Decryptor().Decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);

        CollectionAssert.AreEqual(bytes, decBytes);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamNoBuffersSpecified()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 0, 0,
                    false, 0, null, false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE,
                    false, 0, null, false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE + 3, SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE + 3,
                    false, 0, null, false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamAlignedBuffer()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2,
                    false, 0, null, false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamDecNoAlignedBuffer()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    16 * 2, 16 * 2 + 3,
                    false, 0, null, false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    false, null);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    0, 0,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, 64);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    0, 0,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, 128);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, 32);
    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    128, 128, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    false, null);

    }

    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative()
    {
        bool caught = false;
        try
        {
            SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
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
            SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);


    }


    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned()
    {
        SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
    }


    [TestMethod]
    public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative()
    {
        bool caught = false;
        try
        {
            SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, false, null);
        }
        catch (Exception ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
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
            SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
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
            SalmonCoreTestHelper.EncryptWriteDecryptRead(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true, null);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldNotReadFromStreamEncryptionMode()
    {
        string testText = SalmonCoreTestHelper.TEST_TEXT;

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
        AesStream encWriter = new AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs,
                EncryptionFormat.Salmon);
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
        string testText = SalmonCoreTestHelper.TEST_TEXT;

        StringBuilder tBuilder = new StringBuilder();
        for (int i = 0; i < 10; i++)
        {
            tBuilder.Append(testText);
        }
        string plainText = tBuilder.ToString();
        bool caught = false;
        byte[] inputBytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytes = SalmonCoreTestHelper.Encrypt(inputBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE, false, 0, null);

        MemoryStream ins = new MemoryStream(encBytes);
        AesStream encWriter = new AesStream(SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            EncryptionMode.Decrypt, ins, EncryptionFormat.Salmon);
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
        SalmonCoreTestHelper.SeekAndRead(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            false, 0, null);
    }


    [TestMethod]
    public void ShouldSeekAndTestBlockAndCounter()
    {
        SalmonCoreTestHelper.SeekTestCounterAndBlock(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
            false, 0, null);
    }


    [TestMethod]
    public void ShouldSeekAndReadWithIntegrity()
    {
        SalmonCoreTestHelper.SeekAndRead(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    }

    [TestMethod]
    public void ShouldSeekAndReadWithIntegrityMultiChunks()
    {
        SalmonCoreTestHelper.SeekAndRead(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
    }

    [TestMethod]
    public void ShouldSeekAndWriteNoIntegrity()
    {
        SalmonCoreTestHelper.SeekAndWrite(SalmonCoreTestHelper.TEST_TEXT,
            SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 16,
            SalmonCoreTestHelper.TEST_TEXT_WRITE.Length, SalmonCoreTestHelper.TEST_TEXT_WRITE,
            false, 0, null, true);
    }

    [TestMethod]
    public void ShouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative()
    {
        bool caught = false;
        try
        {
            SalmonCoreTestHelper.SeekAndWrite(SalmonCoreTestHelper.TEST_TEXT,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, 5,
                SalmonCoreTestHelper.TEST_TEXT_WRITE.Length, SalmonCoreTestHelper.TEST_TEXT_WRITE,
                false, 0, null, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SecurityException))
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
            SalmonCoreTestHelper.SeekAndWrite(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    5, SalmonCoreTestHelper.TEST_TEXT_WRITE.Length, SalmonCoreTestHelper.TEST_TEXT_WRITE, false,
                    32, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
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
            SalmonCoreTestHelper.TestCounterValue(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.MAX_ENC_COUNTER);
        }
        catch (Exception ex)
        {

            if (ex.InnerException.GetType() == typeof(RangeExceededException) || ex.InnerException.GetType() == typeof(ArgumentOutOfRangeException)) {
                caught = true;
				Console.WriteLine("Caught: " + ex.Message);
			} else {
				Console.Error.WriteLine(ex);
			}
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldHoldCTRValue()
    {
        bool caught = false;
        try
        {
            SalmonCoreTestHelper.TestCounterValue(SalmonCoreTestHelper.TEST_TEXT, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonCoreTestHelper.MAX_ENC_COUNTER - 1L);
        }
        catch (Exception ex)
        {
            
            if (ex.GetType() == typeof(RangeExceededException)) {
                caught = true;
				Console.WriteLine("Caught: " + ex.Message);
			} else {
				Console.Error.WriteLine(ex);
			}
        }

        Assert.IsFalse(caught);
    }

    [TestMethod]
    public void ShouldCalcHMac256()
    {
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT);
        byte[] hash = SalmonCoreTestHelper.CalculateHMAC(bytes, 0, bytes.Length,
            SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, null);
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
    public void ShouldEncryptAndDecryptArrayGeneric()
    {
        byte[]
    data = SalmonCoreTestHelper.GetRandArray(1 * 1024 * 1024 + 4);
        long t1 = Time.Time.CurrentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.GetEncryptor().Encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Generic);
        long t2 = Time.Time.CurrentTimeMillis();
        byte[] decData = new Decryptor(2).Decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Generic);
        long t3 = Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayIntegrity()
    {
        byte[] data = SalmonCoreTestHelper.GetRandArray(1 * 1024 * 1024 + 3);
        long t1 = Time.Time.CurrentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.GetEncryptor().Encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        long t2 = Time.Time.CurrentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        long t3 = Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayIntegrityCustomChunkSize()
    {
        byte[] data = SalmonCoreTestHelper.GetRandArray(1 * 1024 * 1024);
        long t1 = Time.Time.CurrentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.GetEncryptor().Encrypt(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 8 * 1024);
        long t2 = Time.Time.CurrentTimeMillis();
        byte[] decData = new Decryptor(2).Decrypt(encData, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 8 * 1024);
        long t3 = Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayIntegrityNoApply()
    {
        byte[] data = UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT);
        byte[] key = Generator.GetSecureRandomBytes(32);
        byte[] nonce = Generator.GetSecureRandomBytes(8);
        byte[] hashKey = Generator.GetSecureRandomBytes(32);

        byte[] encData = SalmonCoreTestHelper.GetEncryptor().Encrypt(data, key, nonce, EncryptionFormat.Salmon, true, hashKey);

        // specify integrity
        byte[] decData2 = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData, key, null, EncryptionFormat.Salmon, true, hashKey);
        CollectionAssert.AreEqual(data, decData2);

        // skip integrity
        byte[] decData3 = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData, key, null, EncryptionFormat.Salmon, false);
        CollectionAssert.AreEqual(data, decData3);

        // tamper
        encData[14] = 0;

        // specify integrity
        bool caught = false;
        try
        {
            byte[] decData4 = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData, key, null, EncryptionFormat.Salmon, true, hashKey);
            CollectionAssert.AreEqual(data, decData4);
        }
        catch (Exception ex)
        {
            caught = true;
        }
        Assert.IsTrue(caught);

        // skip integrity, not failing but results don't match
        bool caught2 = false;
        try
        {
            byte[] decData5 = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData, key, null, EncryptionFormat.Salmon, false);
        }
        catch (Exception ex)
        {
            caught2 = true;
        }
        Assert.IsFalse(caught2);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptArrayIntegrityCustomChunkSizeDecNoChunkSize()
    {
        byte[] data = SalmonCoreTestHelper.GetRandArray(1 * 1024 * 1024);
        long t1 = Time.Time.CurrentTimeMillis();
        byte[] encData = SalmonCoreTestHelper.GetEncryptor().Encrypt(data,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, 8 * 1024);
        long t2 = Time.Time.CurrentTimeMillis();
        byte[] decData = SalmonCoreTestHelper.GetDecryptor().Decrypt(encData,
                SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                EncryptionFormat.Salmon, true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES);
        long t3 = Time.Time.CurrentTimeMillis();

        CollectionAssert.AreEqual(data, decData);
        Console.WriteLine("enc time: " + (t2 - t1));
        Console.WriteLine("dec time: " + (t3 - t2));
    }

    [TestMethod]
    public void ShouldCopyMemory()
    {
        SalmonCoreTestHelper.CopyMemory(4 * 1024 * 1024);
    }

    [TestMethod]
    public void ShouldCopyFromToMemoryStream()
    {
        SalmonCoreTestHelper.CopyFromMemStream(1 * 1024 * 1024, 0);
        SalmonCoreTestHelper.CopyFromMemStream(1 * 1024 * 1024, 32768);
    }

    [TestMethod]
    public void ShouldCopyFromMemoryStreamToSalmonStream()
    {
        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 0, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    32768);

        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, Integrity.Integrity.DEFAULT_CHUNK_SIZE, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, Integrity.Integrity.DEFAULT_CHUNK_SIZE, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    32768);

        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    0);
        SalmonCoreTestHelper.CopyFromMemStreamToSalmonStream(1 * 1024 * 1024,
                    SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 128 * 1024, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    32768);
    }
}
