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

using Mku.Salmon.Streams;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonCorePerfTests
{
    public static int TEST_PERF_SIZE = 8 * 1024 * 1024;

	[ClassInitialize]
    public static void ClassInitialize(TestContext testContext)
    {
		int threads = Environment.GetEnvironmentVariable("ENC_THREADS") != null && !Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
			int.Parse(Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;
		
		Console.WriteLine("threads: " + threads);

		//SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 8 * 1024 * 1024;
        //SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 8 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;
    }

    [TestInitialize]
    public void BeforeEach()
    {
        SalmonCoreTestHelper.Initialize();
    }

    [TestCleanup]
    public void AfterEach()
    {
        SalmonCoreTestHelper.Close();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSysDefault()
    {
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        Console.WriteLine("System Default: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSalmonNativeAes()
    {
        AesStream.AesProviderType = ProviderType.Aes;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native Aes: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSalmonNativeAesIntrinsics()
    {
        AesStream.AesProviderType = ProviderType.AesIntrinsics;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native Intr: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSalmonNativeAesGPU()
    {
		if(!SalmonCoreTestHelper.IsGPUEnabled())
			return;
        AesStream.AesProviderType = ProviderType.AesGPU;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native GPU: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonDefault()
    {
        AesStream.AesProviderType = ProviderType.Default;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("AesStream Salmon Default: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAes()
    {
        AesStream.AesProviderType = ProviderType.Aes;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("AesStream Salmon Native Aes: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAesIntrinsics()
    {
        AesStream.AesProviderType = ProviderType.AesIntrinsics;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("AesStream Salmon Native Aes Intrinsics: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }


    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAesGPU()
    {
		if(!SalmonCoreTestHelper.IsGPUEnabled())
			return;
        AesStream.AesProviderType = ProviderType.AesGPU;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("AesStream Salmon GPU: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }
}
