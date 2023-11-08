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

using Mku.Salmon.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonCSharpPerfTestRunner
{
    public static int TEST_PERF_SIZE = 32 * 1024 * 1024;

    static SalmonCSharpPerfTestRunner()
    {
        SalmonDefaultOptions.BufferSize = 256 * 1024;
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerformanceSysDefault()
    {
        // warm up
        TestHelper.EncryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        Console.WriteLine("System Default: ");
        TestHelper.EncryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerformanceSalmonDef()
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.Default;
        // warm up
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon Def: ");
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptPerformanceSalmonIntrinsics()
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.AesIntrinsics;
        // warm up
        TestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        Console.WriteLine("Salmon Native: ");
        TestHelper.EncryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    [TestMethod]
    public void EncryptAndDecryptStreamPerformanceSalmonIntrinsics()
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.AesIntrinsics;
        //warm up
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon Intrinsics: ");
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

    // [TestMethod]
    public void EncryptAndDecryptStreamPerformanceSalmonTinyAes()
    {
        SalmonStream.AesProviderType = SalmonStream.ProviderType.TinyAES;
        // warm up
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        Console.WriteLine("SalmonStream Salmon TinyAES: ");
        TestHelper.EncryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        Console.WriteLine();
    }

}
