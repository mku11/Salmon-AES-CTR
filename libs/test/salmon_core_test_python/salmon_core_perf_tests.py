#!/usr/bin/env python3
from __future__ import annotations

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

import os
import sys
from unittest import TestCase
from typeguard import typechecked

sys.path.append(os.path.dirname(__file__) + '/../../src/python/salmon_core')
from salmon_core.salmon.streams.provider_type import ProviderType
from salmon_core.salmon.streams.aes_stream import AesStream

from salmon_core_test_helper import SalmonCoreTestHelper


@typechecked
class SalmonCorePerfTests(TestCase):
    TEST_PERF_SIZE = 32 * 1024 * 1024

    # SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024
    # SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024
    SalmonCoreTestHelper.TEST_ENC_THREADS = 1
    SalmonCoreTestHelper.TEST_DEC_THREADS = 1

    @classmethod
    def setUpClass(cls) -> None:
        SalmonCoreTestHelper.initialize()

    @classmethod
    def tearDownClass(cls) -> None:
        SalmonCoreTestHelper.close()

    def test_encrypt_and_decrypt_perf_sys_default(self):
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_def(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("System Default: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_def(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_perf_salmon_aes(self):
        AesStream.set_aes_provider_type(ProviderType.Aes)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("Salmon Native Aes: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_perf_salmon_intrinsics(self):
        AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("Salmon Native Aes Intrinsics: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_perf_salmon_gpu(self):
        AesStream.set_aes_provider_type(ProviderType.AesGPU)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("Salmon Native GPU: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array_native(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    # streams
    def test_encrypt_and_decrypt_stream_performance_salmon_def(self):
        AesStream.set_aes_provider_type(ProviderType.Default)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("AesStream Salmon Default: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_perf_salmon_aes(self):
        AesStream.set_aes_provider_type(ProviderType.Aes)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("AesStream Salmon Native Aes: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_perf_salmon_intrinsics(self):
        AesStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("AesStream Salmon Native Intrinsics: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_perf_salmon_aes_gpu(self):
        AesStream.set_aes_provider_type(ProviderType.AesGPU)
        # warm up
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, False)
        print("AesStream Salmon Native GPU: ")
        SalmonCoreTestHelper.encrypt_and_decrypt_byte_array(SalmonCorePerfTests.TEST_PERF_SIZE, True)
        print()
