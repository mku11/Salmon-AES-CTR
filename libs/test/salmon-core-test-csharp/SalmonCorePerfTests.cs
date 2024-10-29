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
    public static int TEST_PERF_SIZE = 32 * 1024 * 1024;

    static SalmonCorePerfTests() {
        //SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 8 * 1024 * 1024;
        //SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 8 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = 1;
        SalmonCoreTestHelper.TEST_DEC_THREADS = 1;
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
        SalmonStream.AesProviderType = ProviderType.Aes;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native Aes: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSalmonNativeAesIntrinsics()
    {
        SalmonStream.AesProviderType = ProviderType.AesIntrinsics;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native Intr: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerfSalmonNativeAesGPU()
    {
        SalmonStream.AesProviderType = ProviderType.AesGPU;
        // warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native GPU: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonDefault()
    {
        SalmonStream.AesProviderType = ProviderType.Default;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon Default: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAes()
    {
        SalmonStream.AesProviderType = ProviderType.Aes;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon Native Aes: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAesIntrinsics()
    {
        SalmonStream.AesProviderType = ProviderType.AesIntrinsics;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon Native Aes Intrinsics: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }


    [TestMethod]
    public void EncryptAndDecryptStreamPerfSalmonNativeAesGPU()
    {
        SalmonStream.AesProviderType = ProviderType.AesGPU;
        //warm up
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon GPU: ");
        SalmonCoreTestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }
}
