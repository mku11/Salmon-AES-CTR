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
from convert.bit_converter import BitConverter
from salmon.salmon_generator import SalmonGenerator
from salmon.salmon_range_exceeded_exception import SalmonRangeExceededException
from salmon.salmon_security_exception import SalmonSecurityException

from typeguard import typechecked


@typechecked
class SalmonNonce:
    """
     * Utility provides nonce operations.
    """

    @staticmethod
    def increase_nonce(start_nonce: bytearray, end_nonce: bytearray) -> bytearray:
        """
         * Increase the sequential NONCE by a value of 1.
         * This implementation assumes that the NONCE length is 8 bytes or fewer so it can fit in a long.
         *
         * @param startNonce
         * @param endNonce
         * @return
         * @throws SalmonRangeExceededException
        """
        nonce: int = BitConverter.to_long(start_nonce, 0, SalmonGenerator.NONCE_LENGTH)
        max_nonce: int = BitConverter.to_long(end_nonce, 0, SalmonGenerator.NONCE_LENGTH)
        nonce += 1
        if nonce <= 0 or nonce > max_nonce:
            raise SalmonRangeExceededException("Cannot increase nonce, maximum nonce exceeded")
        return BitConverter.to_bytes(nonce, 8)

    """
     * Returns the middle nonce in the provided range.
     * Note: This assumes the nonce is 8 bytes, if you need to increase the nonce length
     * then the long transient variables will not hold. In that case you will need to
     * override with your own implementation.
     *
     * @param startNonce The starting nonce.
     * @param endNonce The ending nonce in the sequence.
     * @return The byte array with the middle nonce.
     * @throws SalmonSecurityException
    """

    @staticmethod
    def split_nonce_range(start_nonce: bytearray, end_nonce: bytearray) -> bytearray:
        start: int = BitConverter.to_long(start_nonce, 0, SalmonGenerator.NONCE_LENGTH)
        end: int = BitConverter.to_long(end_nonce, 0, SalmonGenerator.NONCE_LENGTH)
        # we reserve some nonces
        if end - start < 256:
            raise SalmonSecurityException("Not enough nonces left")
        return BitConverter.to_bytes(start + (end - start) // 2, SalmonGenerator.NONCE_LENGTH)
