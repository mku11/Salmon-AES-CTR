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

from salmon.iostream.provider_type import ProviderType
from salmon.iostream.salmon_stream import SalmonStream
from salmon.salmon_default_options import SalmonDefaultOptions

from typeguard import typechecked

from salmon.transform.native_proxy import NativeProxy
from test.test_helper import TestHelper


@typechecked
class SalmonPythonPerfTestRunner(TestCase):
    TEST_PERF_SIZE = 64 * 1024 * 1024
    SalmonDefaultOptions.set_buffer_size(256 * 1024)
    NativeProxy.set_library_path("../../projects/salmon-libs-gradle/salmon-native/build/libs/salmon/shared/salmon.dll")

    def test_encrypt_and_decrypt_stream_performance_sys_default(self):
        # warm up
        TestHelper.encrypt_and_decrypt_byte_array_def(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, False)
        print("System Default: ")
        TestHelper.encrypt_and_decrypt_byte_array_def(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_def(self):
        SalmonStream.set_aes_provider_type(ProviderType.Default)
        # warm up
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, False)
        print("SalmonStream Salmon Def: ")
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, True)
        print()

    def test_encrypt_and_decrypt_performance_salmon_intrinsics(self):
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        TestHelper.encrypt_and_decrypt_byte_array_native(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, False)
        print("Salmon Native: ")
        TestHelper.encrypt_and_decrypt_byte_array_native(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_intrinsics(self):
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        # warm up
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, False)
        print("SalmonStream Salmon Intrinsics: ")
        SalmonStream.set_aes_provider_type(ProviderType.AesIntrinsics)
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, True)
        print()

    def test_encrypt_and_decrypt_stream_performance_salmon_tiny_aes(self):
        SalmonStream.set_aes_provider_type(ProviderType.TinyAES)
        # warm up
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, False)
        print("SalmonStream Salmon TinyAES: ")
        TestHelper.encrypt_and_decrypt_byte_array(SalmonPythonPerfTestRunner.TEST_PERF_SIZE, 1, True)
        print()
