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

from typeguard import typechecked

from salmon_core.salmon.password.ipbkdf_provider import ISalmonPbkdfProvider
from salmon_core.salmon.password.pbkdf_algo import PbkdfAlgo
from salmon_core.salmon.password.pbkdf_type import PbkdfType
from salmon_core.salmon.password.default_pbkdf_provider import SalmonDefaultPbkdfProvider
from salmon_core.salmon.password.pbkdf_factory import SalmonPbkdfFactory


@typechecked
class Password:
    """
    Generates security keys based on text passwords.
    """

    __pbkdfAlgo: PbkdfAlgo = PbkdfAlgo.SHA256
    """
    Global PBKDF algorithm option that will be used for the master key derivation.
    """

    __provider: ISalmonPbkdfProvider = SalmonDefaultPbkdfProvider()
    """
    Pbkdf provider.
    """

    @staticmethod
    def get_pbkdf_algo() -> PbkdfAlgo:
        """
        Returns the current global PBKDF algorithm.
        
        :return: The PBKDF algorithm to be used.
        """
        return Password.__pbkdfAlgo

    @staticmethod
    def set_pbkdf_algo(pbkdf_algo: PbkdfAlgo):
        """
        Set the global PDKDF algorithm to be used for key derivation.
        
        :param pbkdf_algo:         """
        Password.__pbkdfAlgo = pbkdf_algo

    @staticmethod
    def set_pbkdf_type(pbkdf_type: PbkdfType):
        """
        Set the global PBKDF implementation to be used for text key derivation.
        
        :param pbkdf_type:         """
        __provider = SalmonPbkdfFactory.create(pbkdf_type)

    @staticmethod
    def set_pbkdf_provider(pbkdf_provider: ISalmonPbkdfProvider):
        """
        Set the global PBKDF provider to be used for text key derivation.
        
        :param pbkdf_provider:         """
        Password.__provider = pbkdf_provider

    @staticmethod
    def get_master_key(password: str, salt: bytearray, iterations: int, length: int) -> bytearray:
        """
        Derives the key from a text password
        
        :param password:       The text password to be used
        :param salt:       The salt to be used for the key derivation
        :param iterations: The number of iterations the key derivation algorithm will use
        :param length:     The length of master key to return
        :return: The derived master key.
        :raises IntegrityException: Thrown when security error
        """
        master_key: bytearray = Password.get_key_from_password(password, salt, iterations, length)
        return master_key

    @staticmethod
    def get_key_from_password(password: str, salt: bytearray, iterations: int, output_bytes: int) -> bytearray:
        """
        Function will derive a key from a text password
        
        :param password:    The password that will be used to derive the key
        :param salt:        The salt byte array that will be used together with the password
        :param iterations:  The iterations to be used with Pbkdf2
        :param output_bytes: The number of bytes for the key
        :return: The derived key.
        :raises IntegrityException: Thrown when security error
        """
        return Password.__provider.get_key(password, salt, iterations, output_bytes, Password.__pbkdfAlgo)
