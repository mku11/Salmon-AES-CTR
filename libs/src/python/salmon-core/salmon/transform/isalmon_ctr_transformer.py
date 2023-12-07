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

from interface import Interface


class ISalmonCTRTransformer(Interface):
    """
     * Contract for the encryption/decryption transformers.
     * Note that Counter mode needs to be supported.
    """

    def init(self, key: bytearray, nonce: bytearray):
        """
         * Initialize the transformer.
         * @param key The AES key to use.
         * @param nonce The nonce to use.
         * @throws SalmonSecurityException
        """
        pass

    def encryptData(self, srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        """
         * Encrypt the data.
         * @param srcBuffer The source byte array.
         * @param srcOffset The source byte offset.
         * @param destBuffer The destination byte array.
         * @param destOffset The destination byte offset.
         * @param count The number of bytes to transform.
         * @return The number of bytes transformed.
         * @throws SalmonSecurityException
         * @throws SalmonRangeExceededException
        """
        pass

    def decryptData(self, srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        """
         * Decrypt the data.
         * @param srcBuffer The source byte array.
         * @param srcOffset The source byte offset.
         * @param destBuffer The destination byte array.
         * @param destOffset The destination byte offset.
         * @param count The number of bytes to transform.
         * @return The number of bytes transformed.
         * @throws SalmonSecurityException
         * @throws SalmonRangeExceededException
        """
        pass

    def getCounter(self) -> bytearray:
        """
         * Get the current counter.
         * @return
        """
        pass

    def getKey(self) -> bytearray:
        """
         * Get the current encryption key.
         * @return
        """
        pass

    def getBlock(self) -> int:
        """
         * Get the current block.
         * @return
        """
        pass

    def getNonce(self) -> bytearray:
        """
         * Get the nonce (initial counter) to be used for the data.
         * @return
        """
        pass

    def resetCounter(self):
        """
         * Reset the counter to the nonce (initial counter).
        """
        pass

    def syncCounter(self, position: int):
        """
         * Calculate the value of the counter based on the current block. After an encryption
         * operation (ie sync or read) the block will be incremented. This method calculates
         * the Counter.
         * @param position
         * @throws SalmonRangeExceededException
        """
        pass
