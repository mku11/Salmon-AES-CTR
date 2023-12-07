#!/usr/bin/env python3
'''
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
'''
from salmon.salmon_security_exception import SalmonSecurityException
from salmon.transform.isalmon_ctr_transformer import ISalmonCTRTransformer
from salmon.transform.salmon_aes_intr_transformer import SalmonAesIntrTransformer
from salmon.transform.salmon_default_transformer import SalmonDefaultTransformer
from salmon.transform.tiny_aes_transformer import TinyAesTransformer


class SalmonTransformerFactory:
    """
     * Creates an AES transformer object.
    """

    @staticmethod
    def create(type: SalmonStream.ProviderType) -> ISalmonCTRTransformer:
        """
         * Create an encryption transformer implementation.
         * @param type The supported provider type.
         * @return The transformer.
         * @throws SalmonSecurityException
        """
        match type:
            case SalmonStream.ProviderType.Default:
                return SalmonDefaultTransformer()
            case SalmonStream.ProviderType.AesIntrinsics:
                return SalmonAesIntrTransformer()
            case SalmonStream.ProviderType.TinyAES:
                return TinyAesTransformer()
        raise SalmonSecurityException("Unknown Transformer type")
