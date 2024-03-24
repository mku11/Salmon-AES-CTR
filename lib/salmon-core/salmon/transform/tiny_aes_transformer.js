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
import { SalmonAesIntrTransformer } from "./salmon_aes_intr_transformer.js";
import { SalmonNativeTransformer } from "./salmon_native_transformer.js";
/**
 *  Salmon AES transformer implemented with TinyAES backend.
 */
export class TinyAesTransformer extends SalmonNativeTransformer {
    /**
     * Initialiaze the native transformer to use the Tiny AES implementation.
     *
     * @param key   The AES key to use.
     * @param nonce The nonce to use.
     * @throws SalmonSecurityException
     */
    async init(key, nonce) {
        SalmonAesIntrTransformer.getNativeProxy().salmonInit(TinyAesTransformer.AES_IMPL_TINY_AES);
        await super.init(key, nonce);
    }
}
/**
 * The constant to pass to the native code while initializing.
 */
TinyAesTransformer.AES_IMPL_TINY_AES = 2;
