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

import { ProviderType } from "../streams/provider_type.js";
import { SalmonSecurityException } from "../salmon_security_exception.js";
import { ISalmonCTRTransformer } from "./isalmon_ctr_transformer.js";
import { SalmonDefaultTransformer } from "./salmon_default_transformer.js";
import { SalmonNativeTransformer } from "./salmon_native_transformer.js";

/**
 * Creates an AES transformer object.
 */
export class SalmonTransformerFactory {

    /**
     * Create an encryption transformer implementation.
     * @param {ProviderType} type The supported provider type.
     * @return {ISalmonCTRTransformer} The transformer.
     * @throws SalmonSecurityException Thrown when error with security
     */
    public static create(type: ProviderType): ISalmonCTRTransformer {
        switch(type) {
            case ProviderType.Default:
                return new SalmonDefaultTransformer();
            case ProviderType.AesIntrinsics:
            case ProviderType.Aes:
            case ProviderType.AesGPU:
                return new SalmonNativeTransformer(type);
            default:
                throw new SalmonSecurityException("Unknown Transformer type");
        }
    }
}