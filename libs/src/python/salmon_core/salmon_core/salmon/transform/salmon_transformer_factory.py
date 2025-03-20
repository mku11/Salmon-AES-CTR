#!/usr/bin/env python3
__license__ = """
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
"""

from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.security_exception import SecurityException
from salmon_core.salmon.transform.ictr_transformer import ICTRTransformer
from salmon_core.salmon.transform.aes_default_transformer import AesDefaultTransformer
from typeguard import typechecked

from salmon_core.salmon.transform.aes_native_transformer import AesNativeTransformer


@typechecked
class TransformerFactory:
    """
    Creates an AES transformer object.
    """

    @staticmethod
    def create(v_type: ProviderType) -> ICTRTransformer:
        """!
        Create an encryption transformer implementation.
        @param v_type: The supported provider type.
        @returns The transformer.
        @exception IntegrityException: Thrown when security error
        """
        match v_type:
            case ProviderType.Default:
                return AesDefaultTransformer()
            case ProviderType.AesIntrinsics | ProviderType.Aes | ProviderType.AesGPU:
                return AesNativeTransformer(v_type.value)
        raise SecurityException("Unknown Transformer type")
