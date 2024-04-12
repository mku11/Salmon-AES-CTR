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

from __future__ import annotations
from unittest import TestCase

from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.salmon_stream import SalmonStream

from typeguard import typechecked

from salmon_core.salmon.bridge.native_proxy import NativeProxy
from test.salmon_core_test_helper import SalmonCoreTestHelper


@typechecked
class SalmonCorePerfTests(TestCase):
    TEST_PERF_SIZE = 16 * 1024 * 1024
    NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")

    # SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024
    # SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024
    SalmonCoreTestHelper.TEST_ENC_THREADS = 1
    SalmonCoreTestHelper.TEST_DEC_THREADS = 1

    def setUp(self) -> None:
        SalmonCoreTestHelper.initialize()

    def tearDown(self) -> None:
        SalmonCoreTestHelper.close()

    def test_encrypt_and_decrypt_stream_performance_sys_default(self):
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_def(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("System Default: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_def(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_def(self):
        SalmonStream.set_aes_provider_type(ProviderType.Default)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("SalmonStream Salmon Def: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_performance_salmon_intrinsics(self):
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("Salmon Native: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_intrinsics(self):
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("SalmonStream Salmon Intrinsics: ")
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_tiny_aes(self):
        SalmonStream.set_aes_provider_type(ProviderType.TinyAES)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("SalmonStream Salmon TinyAES: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()
