package com.mku.salmon.jmh;

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

import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.test.SalmonCoreTestHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.security.Provider;
import java.security.Security;

@State(Scope.Benchmark)
public class SalmonBenchmark {
    public static int TEST_PERF_SIZE = 4 * 1024 * 1024;

    private static final Provider bouncycastleProvider;

    static {
        bouncycastleProvider = new BouncyCastleProvider();
    }

    // system
    @Benchmark
    public void EncryptAndDecryptSysDefault() throws Exception {
        SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
    }

    // system w/ BC
    @Benchmark
    public void EncryptAndDecryptSysBouncyCastle() throws Exception {
        if (!Security.getProviders()[0].getName().equals("BC"))
            Security.insertProviderAt(bouncycastleProvider, 1);
        SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        if (Security.getProviders()[0].getName().equals("BC")) {
            Security.removeProvider(Security.getProviders()[0].getName());
        }
    }

    // Native transformer
    @Benchmark
    public void EncryptAndDecryptSalmonNativeAes() throws Exception {
        AesStream.setAesProviderType(ProviderType.Aes);
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
    }

    @Benchmark
    public void EncryptAndDecryptSalmonNativeAesIntr() throws Exception {
        AesStream.setAesProviderType(ProviderType.AesIntrinsics);
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
    }

    @Benchmark
    public void EncryptAndDecryptSalmonNativeAesGPU() throws Exception {
        AesStream.setAesProviderType(ProviderType.AesGPU);
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
    }

    // streams
    @Benchmark
    public void EncryptAndDecryptStreamPerfSalmonDefault() throws Exception {
        AesStream.setAesProviderType(ProviderType.Default);
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
    }

    @Benchmark
    public void EncryptAndDecryptStreamPerfSalmonNativeAes() throws Exception {
        AesStream.setAesProviderType(ProviderType.Aes);
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
    }

    @Benchmark
    public void EncryptAndDecryptStreamPerfSalmonNativeIntrinsics() throws Exception {
        AesStream.setAesProviderType(ProviderType.AesIntrinsics);
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
    }

    @Benchmark
    public void EncryptAndDecryptStreamPerfSalmonNativeGPU() throws Exception {
        AesStream.setAesProviderType(ProviderType.AesGPU);
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
    }
}
