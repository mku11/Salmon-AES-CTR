package com.mku.salmon.transform;
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

import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.streams.ProviderType;

/**
 * Creates an AES transformer object.
 */
public class SalmonTransformerFactory {

    /**
     * Create an encryption transformer implementation.
     * @param type The supported provider type.
     * @return The transformer.
     * @throws SalmonSecurityException Thrown if there is a security exception
     */
    public static ISalmonCTRTransformer create(ProviderType type) {
        switch(type) {
            case Default:
                return new SalmonDefaultTransformer();
            case Aes:
            case AesIntrinsics:
            case AesGPU:
                return new SalmonNativeTransformer(type.ordinal());
        }
        throw new SalmonSecurityException("Unknown Transformer type");
    }
}
