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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalmonJavaPerfTestRunner {
    public static int TEST_PERF_SIZE = 32 * 1024 * 1024;

    static {
        SalmonDefaultOptions.setBufferSize(256 * 1024);
    }

    @Test
    @Order(1)
    public void EncryptAndDecryptStreamPerformanceSysDefault() throws Exception {
        // warm up
        TestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        System.out.println("System Default: ");
        TestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(2)
    public void EncryptAndDecryptStreamPerformanceSalmonDef() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.Default);
        // warm up
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon Def: ");
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(3)
    public void EncryptAndDecryptPerformanceSalmonIntrinsics() throws Exception {
		SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        // warm up
        TestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        System.out.println("Salmon Native: ");
        TestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(4)
    public void EncryptAndDecryptStreamPerformanceSalmonIntrinsics() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        //warm up
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon Intrinsics: ");
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.AesIntrinsics);
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(6)
    public void EncryptAndDecryptStreamPerformanceSalmonTinyAes() throws Exception {
        SalmonStream.setAesProviderType(SalmonStream.ProviderType.TinyAES);
        // warm up
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon TinyAES: ");
        TestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }
}
