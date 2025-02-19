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

import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalmonCorePerfTests {
    public static int TEST_PERF_SIZE = 8 * 1024 * 1024;
	public static boolean enableGPU = false;
	
    @BeforeAll
    static void beforeAll() {
		String enableGPUStr = System.getProperty("ENABLE_GPU");
		if(enableGPUStr!=null) {
			enableGPU = enableGPUStr.equals("1") || enableGPUStr.equals("true");
			System.out.println("ENABLE_GPU: " + enableGPU);
		}
		 
		//SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
		//SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
		SalmonCoreTestHelper.TEST_ENC_THREADS = 1;
		SalmonCoreTestHelper.TEST_DEC_THREADS = 1;
    }

    @BeforeEach
    void beforeEach(){
        SalmonCoreTestHelper.initialize();
    };

    @AfterEach
    void afterEach(){
        SalmonCoreTestHelper.close();
    };

    @Test
    @Order(1)
    public void EncryptAndDecryptPerfSysDefault() throws Exception {
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        System.out.println("System Default: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        System.out.println();
    }


    @Test
    @Order(2)
    public void EncryptAndDecryptPerfSalmonNativeAes() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.Aes);
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        System.out.println("Salmon Native Aes: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        System.out.println();
    }


    @Test
    @Order(3)
    public void EncryptAndDecryptPerfSalmonNativeAesIntrinsics() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        System.out.println("Salmon Native Intr: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(4)
	@EnabledIfSystemProperty(named = "ENABLE_GPU", matches = "true")
    public void EncryptAndDecryptPerfSalmonNativeAesGPU() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.AesGPU);
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        System.out.println("Salmon Native GPU: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        System.out.println();
    }


    @Test
    @Order(5)
    public void EncryptAndDecryptStreamPerfSalmonDefault() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.Default);
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon Default: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }


    @Test
    @Order(6)
    public void EncryptAndDecryptStreamPerfSalmonNativeAes() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.Aes);
        // warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon Native Aes: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(7)
    public void EncryptAndDecryptStreamPerfSalmonNativeAesIntrinsics() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);
        //warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon Native Aes Intrinsics: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }

    @Test
    @Order(8)
	@EnabledIfSystemProperty(named = "ENABLE_GPU", matches = "true")
    public void EncryptAndDecryptStreamPerfSalmonNativeAesGPU() throws Exception {
        SalmonStream.setAesProviderType(ProviderType.AesGPU);
        //warm up
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        System.out.println("SalmonStream Salmon GPU: ");
        SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        System.out.println();
    }
}
