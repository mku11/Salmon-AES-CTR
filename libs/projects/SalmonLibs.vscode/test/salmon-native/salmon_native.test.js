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

import { Decryptor } from '../../lib/salmon-core/salmon/decryptor.js';
import { Encryptor } from '../../lib/salmon-core/salmon/encryptor.js';
import { Password } from '../../lib/salmon-core/salmon/password/password.js';
import { PbkdfType } from '../../lib/salmon-core/salmon/password/pbkdf_type.js';
import { AesStream } from '../../lib/salmon-core/salmon/streams/aes_stream.js';
import { EncryptionFormat } from '../../lib/salmon-core/salmon/streams/encryption_format.js';
import { ProviderType } from '../../lib/salmon-core/salmon/streams/provider_type.js';
import { SalmonCoreTestHelper } from '../salmon-core/salmon_core_test_helper.js';

describe('salmon-native', () => {
    var ENC_THREADS = 1;
    var DEC_THREADS = 1;

    beforeAll(() => {
        SalmonCoreTestHelper.initialize();
    });

    afterAll(() => {
        SalmonCoreTestHelper.close();
    });
    
    beforeEach(() => {
        if(AesStream.getAesProviderType() == ProviderType.Default)
            AesStream.setAesProviderType(ProviderType.Aes);
        ENC_THREADS = SalmonCoreTestHelper.TEST_ENC_THREADS;
        DEC_THREADS = SalmonCoreTestHelper.TEST_DEC_THREADS;
    });

    it('shouldEncryptAndDecryptNativeTextCompatible', async() => {
        let plainText = SalmonCoreTestHelper.TEST_TEXT;//.substring(1, 1 + 2*16+2);
        for (let i = 0; i < 13; i++)
            plainText += plainText;

        let bytes = new TextEncoder().encode(plainText);
        let encBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true);

        let decBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false);
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytesDef);

        let encBytes = await SalmonCoreTestHelper.nativeCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, true,
                AesStream.getAesProviderType());
        await SalmonCoreTestHelper.assertLargeArrayEquals(encBytesDef, encBytes);
        let decBytes = await SalmonCoreTestHelper.nativeCTRTransform(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, false,
                AesStream.getAesProviderType());
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytes);
    });

    it('shouldEncryptAndDecryptNativeStreamTextCompatible', async() => {
        let plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (let i = 0; i < 2; i++)
            plainText += plainText;
        plainText = plainText.substring(0, 16);

        let bytes = new TextEncoder().encode(plainText);
        let encBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        let decBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytesDef);

        let encryptor = new Encryptor(ENC_THREADS);
        let encBytes = await encryptor.encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        encryptor.close();
        await SalmonCoreTestHelper.assertLargeArrayEquals(encBytesDef, encBytes);

        let decryptor = new Decryptor(DEC_THREADS);
        let decBytes = await decryptor.decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES,
                SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        decryptor.close();
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytes);
    });

    it('shouldEncryptAndDecryptNativeStreamReadBuffersNotAlignedTextCompatible', async() => {
        let plainText = SalmonCoreTestHelper.TEST_TEXT;
        for (let i = 0; i < 3; i++)
            plainText += plainText;

        plainText = plainText.substring(0, 64 + 6);

        let bytes = new TextEncoder().encode(plainText);
        let encBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                true);
        let decBytesDef = await SalmonCoreTestHelper.defaultAESCTRTransform(encBytesDef, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false);
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytesDef);

        let encryptor = new Encryptor(ENC_THREADS);
        let encBytes = await encryptor.encrypt(bytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        encryptor.close();
        await SalmonCoreTestHelper.assertLargeArrayEquals(encBytesDef, encBytes);
        let decryptor = new Decryptor(DEC_THREADS, 32 + 2);
        let decBytes = await decryptor.decrypt(encBytes, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, EncryptionFormat.Generic);
        decryptor.close();
        await SalmonCoreTestHelper.assertLargeArrayEquals(bytes, decBytes);
    });
});