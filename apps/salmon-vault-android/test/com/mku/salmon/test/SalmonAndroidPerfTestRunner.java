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

import com.mku.salmon.SalmonDefaultOptions;
import com.mku.salmon.io.SalmonStream;

public class SalmonAndroidPerfTestRunner {
    public static int TEST_PERF_SIZE = 32 * 1024 * 1024;

    static {
        System.loadLibrary("salmon");
        SalmonDefaultOptions.setBufferSize(256 * 1024);
    }

    public static void startPerfTest() throws Exception {
        EncryptAndDecryptStreamPerformanceSysDefault();
        EncryptAndDecryptStreamPerformanceSalmonDef();
        EncryptAndDecryptPerformanceSalmonIntrinsics();
        EncryptAndDecryptStreamPerformanceSalmonIntrinsics();
        EncryptAndDecryptStreamPerformanceSalmonIntrinsics2Threads();

//        EncryptAndDecryptStreamPerformanceSalmonTinyAes();
    }

    public static void EncryptAndDecryptStreamPerformanceSysDefault() throws Exception {
        // warm up
        AndroidTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        System.out.println("Perf System Default: ");
        AndroidTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        System.out.println();
    }

    public static void EncryptAndDecryptStreamPerformanceSalmonDef() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.Default);
        // warm up
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("Perf SalmonStream Salmon Def: ");
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    public static void EncryptAndDecryptPerformanceSalmonIntrinsics() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        //warm up
        AndroidTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        System.out.println("Perf Salmon Intrinsics: ");
        AndroidTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        System.out.println();
    }

    public static void EncryptAndDecryptStreamPerformanceSalmonIntrinsics() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        //warm up
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("Perf SalmonStream Salmon Intrinsics: ");
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    public static void EncryptAndDecryptStreamPerformanceSalmonIntrinsics2Threads() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        // warm up
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, 2, false);
        System.out.println("Perf SalmonStream Salmon Intrinsics with 2 threads: ");
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, 2, true);
        System.out.println();
    }

    public static void EncryptAndDecryptStreamPerformanceSalmonTinyAes() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.TinyAES);
        // warm up
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println("Perf SalmonStream Salmon TinyAES: ");
        AndroidTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }
}
