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

import { Platform, PlatformType } from '../../lib/salmon-core/platform/platform.js';
import { WebGPU } from '../../lib/salmon-core/salmon/bridge/webgpu.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { ProviderType } from '../../lib/salmon-core/salmon/streams/provider_type.js';
import { SalmonCoreTestHelper } from './salmon_core_test_helper.js';

describe('salmon-perf', () => {
    var TEST_PERF_SIZE = 8 * 1024 * 1024;
	
    beforeAll(() => {
        SalmonCoreTestHelper.initialize();
        if(SalmonCoreTestHelper.isGPUEnabled() && 
            Platform.getPlatform() == PlatformType.Browser)
            WebGPU.enable(true);
        //SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
		//SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
    });
    
    afterAll(() => {
        SalmonCoreTestHelper.close();
    });

    it('EncryptAndDecryptPerfSysDefault', async () => {
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, false);
        console.log("System Default: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayDef(TEST_PERF_SIZE, true);
        console.log();
    });

    it('encryptAndDecryptPerfSalmonNativeAes', async () => {
        AesStream.setAesProviderType(ProviderType.Aes);
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        console.log("Salmon Native Aes: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        console.log();
    });

    it('encryptAndDecryptPerfSalmonNativeAesIntrinsics', async () => {
        if(Platform.getPlatform() == PlatformType.Browser)
			return;
        AesStream.setAesProviderType(ProviderType.AesIntrinsics);
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        console.log("Salmon Native Intr: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        console.log();
    });

    it('encryptAndDecryptPerfSalmonNativeAesGPU', async () => {
	    if(!SalmonCoreTestHelper.isGPUEnabled())
			return;
        AesStream.setAesProviderType(ProviderType.AesGPU);
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, false);
        console.log("Salmon Native GPU: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArrayNative(TEST_PERF_SIZE, true);
        console.log();
    });


    it('encryptAndDecryptStreamPerfSalmonDefault', async () => {
        AesStream.setAesProviderType(ProviderType.Default);
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        console.log("SalmonStream Salmon Default: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        console.log();
    });


    it('encryptAndDecryptStreamPerfSalmonNativeAes', async () => {
        AesStream.setAesProviderType(ProviderType.Aes);
        // warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        console.log("SalmonStream Salmon Native Aes: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        console.log();
    });

    it('encryptAndDecryptStreamPerfSalmonNativeAesIntrinsics', async () => {
        if(Platform.getPlatform() == PlatformType.Browser)
			return;
        AesStream.setAesProviderType(ProviderType.AesIntrinsics);
        //warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        console.log("SalmonStream Salmon Native Aes Intrinsics: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        console.log();
    });

    it('encryptAndDecryptStreamPerfSalmonNativeAesGPU', async () => {
	    if(!SalmonCoreTestHelper.isGPUEnabled())
			return;
        AesStream.setAesProviderType(ProviderType.AesGPU);
        //warm up
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, false);
        console.log("SalmonStream Salmon GPU: ");
        await SalmonCoreTestHelper.encryptAndDecryptByteArray(TEST_PERF_SIZE, true);
        console.log();
    });
});
