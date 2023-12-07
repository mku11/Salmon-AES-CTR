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
from enum import Enum

from salmon.password.isalmon_pbkdf_provider import ISalmonPbkdfProvider
from salmon.password.salmon_default_pbkdf_provider import SalmonDefaultPbkdfProvider


class SalmonPassword:
    """
     * Generates security keys based on text passwords.
    """

    ENABLE_SHA1: bool = False
    """
     * WARNING! SHA1 is not secure anymore enable only if you know what you're doing!
    """

    __pbkdfAlgo: PbkdfAlgo = None
    """
     * Global PBKDF algorithm option that will be used for the master key derivation.
    """

    __provider: ISalmonPbkdfProvider = SalmonDefaultPbkdfProvider()
    """
     * Pbkdf provider.
    """

    @staticmethod
    def getPbkdfAlgo() -> PbkdfAlgo:
        """
         * Returns the current global PBKDF algorithm.
         *
         * @return The PBKDF algorithm to be used.
        """
        return SalmonPassword.__pbkdfAlgo

    @staticmethod
    def setPbkdfAlgo(pbkdfAlgo: PbkdfAlgo):
        """
         * Set the global PDKDF algorithm to be used for key derivation.
         *
         * @param pbkdfAlgo
        """
        SalmonPassword.__pbkdfAlgo = pbkdfAlgo

    @staticmethod
    def setPbkdfType(pbkdfType: PbkdfType):
        """
         * Set the global PBKDF implementation to be used for text key derivation.
         *
         * @param pbkdfType
        """
        provider = SalmonPbkdfFactory.create(pbkdfType)

    @staticmethod
    def setPbkdfProvider(pbkdfProvider: ISalmonPbkdfProvider):
        """
         * Set the global PBKDF provider to be used for text key derivation.
         *
         * @param pbkdfProvider
        """
        SalmonPassword.__provider = pbkdfProvider

    @staticmethod
    def getMasterKey(password: str, salt: bytearray, iterations: int, length: int) -> bytearray:
        """
         * Derives the key from a text password
         *
         * @param pass       The text password to be used
         * @param salt       The salt to be used for the key derivation
         * @param iterations The number of iterations the key derivation algorithm will use
         * @param length     The length of master key to return
         * @return The derived master key.
         * @throws SalmonSecurityException
        """
        masterKey: bytearray = SalmonPassword.getKeyFromPassword(password, salt, iterations, length)
        return masterKey

    @staticmethod
    def getKeyFromPassword(password: str, salt: bytearray, iterations: int, outputBytes: int) -> bytearray:
        """
         * Function will derive a key from a text password
         *
         * @param password    The password that will be used to derive the key
         * @param salt        The salt byte array that will be used together with the password
         * @param iterations  The iterations to be used with Pbkdf2
         * @param outputBytes The number of bytes for the key
         * @return The derived key.
         * @throws SalmonSecurityException
        """
        if SalmonPassword.__pbkdfAlgo == SalmonPassword.PbkdfAlgo.SHA1 and not SalmonPassword.ENABLE_SHA1:
            raise RuntimeError("Cannot use SHA1, SHA1 is not secure anymore use SHA256!")
        return SalmonPassword.__provider.getKey(password, salt, iterations, outputBytes, SalmonPassword.__pbkdfAlgo);

    class PbkdfType(Enum):
        """
          * Pbkdf implementation type.
         """

        Default = 1
        """
          * Default Java pbkdf implementation.
         """

    class PbkdfAlgo(Enum):
        """
         * Pbkdf algorithm implementation type.
        """

        SHA1 = 1
        """
         * SHA1 hashing. DO NOT USE.
        """

        SHA256 = 2
        """
         * SHA256 hashing.
        """

    # WORKAROUND: __future__ forw references does not seem to work with enum at least easily,
    # so we delay default assignment till the enum is declared in this module
    setPbkdfAlgo(PbkdfAlgo.SHA256)
