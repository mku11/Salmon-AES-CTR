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
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Text;
using Mku.Salmon.Password;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonNativeTests
{
    static int ENC_THREADS = 1;
    static int DEC_THREADS = 1;

    static SalmonNativeTests()
    {
        try
        {
            SalmonPassword.PbkdfImplType = PbkdfType.Default;
        }
        catch (SalmonSecurityException e)
        {
            throw new Exception("Could not run native test", e);
        }
    }

    [TestInitialize]
    public void Init()
    {
		ProviderType providerType = ProviderType.Aes;
		String aesProviderType = Environment.GetEnvironmentVariable("AES_PROVIDER_TYPE");
		if(aesProviderType != null && !aesProviderType.Equals(""))
			providerType = (ProviderType) Enum.Parse(typeof(ProviderType),aesProviderType);
		int threads = Environment.GetEnvironmentVariable("ENC_THREADS") != null && !Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
			int.Parse(Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;

		Console.WriteLine("ProviderType: " + providerType);
		Console.WriteLine("threads: " + threads);

		SalmonStream.AesProviderType = providerType;
		ENC_THREADS = threads;
		DEC_THREADS = threads;
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptNativeTextCompatible()
    {
        string plainText = SalmonCoreTestHelper.TEST_TEXT;//.substring(1, 1 + 2*16+2);
        for (int i = 0; i < 6; i++)
            plainText += plainText;
        plainText = plainText.Substring(0, 16);

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);

        byte[] decBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        CollectionAssert.AreEqual(bytes, decBytesDef);

        byte[] encBytes = SalmonCoreTestHelper.NativeCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true,
                SalmonStream.AesProviderType);
        CollectionAssert.AreEqual(encBytesDef, encBytes);
        byte[] decBytes = SalmonCoreTestHelper.NativeCTRTransform(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                SalmonStream.AesProviderType);
        CollectionAssert.AreEqual(bytes, decBytes);
    }


    [TestMethod]
    public void ShouldEncryptAndDecryptNativeStreamTextCompatible()
    {
        string plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 2; i++)
            plainText += plainText;
        plainText = plainText.Substring(0, 16);

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        byte[] decBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        CollectionAssert.AreEqual(bytes, decBytesDef);

        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).Encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                                false, null, null);
        CollectionAssert.AreEqual(encBytesDef, encBytes);

        byte[] decBytes = new SalmonDecryptor(DEC_THREADS).Decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        CollectionAssert.AreEqual(bytes, decBytes);
    }


    [TestMethod]
    public void ShouldEncryptAndDecryptNativeStreamReadBuffersNotAlignedTextCompatible()
    {
        string plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 3; i++)
            plainText += plainText;

        plainText = plainText.Substring(0, 64 + 6);

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        byte[] decBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        CollectionAssert.AreEqual(bytes, decBytesDef);

        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).Encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        CollectionAssert.AreEqual(encBytesDef, encBytes);
        SalmonDecryptor decryptor = new SalmonDecryptor(DEC_THREADS, 32 + 2);
        byte[] decBytes = decryptor.Decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                false, null, null);
        CollectionAssert.AreEqual(bytes, decBytes);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptNativeStreamCompatibleWithIntegrity()
    {
        string plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 13; i++)
            plainText += plainText;

        byte[] bytes = UTF8Encoding.UTF8.GetBytes(plainText);
        byte[] encBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = SalmonCoreTestHelper.DefaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);

        int chunkSize = 256 * 1024;
        CollectionAssert.AreEqual(bytes, decBytesDef);
        byte[] encBytes = new SalmonEncryptor(ENC_THREADS).Encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        AssertArrayEqualsWithIntegrity(encBytesDef, encBytes, chunkSize);
        byte[] decBytes = new SalmonDecryptor(DEC_THREADS).Decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                true, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, chunkSize);

        CollectionAssert.AreEqual(bytes, decBytes);
    }


    private void AssertArrayEqualsWithIntegrity(byte[] buffer, byte[] bufferWithIntegrity, int chunkSize)
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
}
