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

from salmon.transform.inative_proxy import INativeProxy


class NativeProxy(INativeProxy):
    """
     * Proxy class for use with windows native library.
    """

    __loaded: bool
    """
     * If proxy is loaded 
    """

    __libraryName: str = "salmon"
    """
     * The dll name for the salmon library.
    """

    # TODO:
    @staticmethod
    def __init(aesImpl: int):
        """
         * Init the native code with AES implementation, and hash length options.
         *
         * @param aesImpl
        """
        pass

    # TODO:
    @staticmethod
    def expandkey(key: bytearray, expandedKey: bytearray):
        """
         * Native Key schedule algorithm for expanding the 32 byte key to 240 bytes required
         *
         * @param key
         * @param expandedKey
        """
        pass

    # TODO:
    @staticmethod
    def __transform(key: bytearray, counter: bytearray, encryption_mode: int,
                    srcBuffer: bytearray, srcOffset: int,
                    destBuffer: bytearray, destOffset: int, count: int) -> int:
        """
         * Native transform of the input byte array using AES 256 encryption or decryption mode.
         *
         * @param key
         * @param counter
         * @param encryption_mode
         * @param srcBuffer
         * @param srcOffset
         * @param destBuffer
         * @param destOffset
         * @param count
         * @return
        """
        pass

    def salmonInit(self, aesImpl: int):
        """
         * Proxy Init the native code with AES implementation, and hash length options.
         *
         * @param aesImpl
        """
        self._loadLibrary()
        NativeProxy.__init(aesImpl)

    """
     * Load the native library
    """

    def _loadLibrary(self):
        if self.__loaded:
            return
        try:
            # TODO:
            # System.loadLibrary(self.__libraryName)
            pass
        except Exception as ex:
            print(ex)

        __loaded = True

    def salmonExpandKey(self, key: bytearray, expandedKey: bytearray):
        """
         * Proxy Key schedule algorithm for expanding the 32 byte key to 240 bytes required
         *
         * @param key
         * @param expandedKey
        """
        NativeProxy.__expandkey(key, expandedKey)

    def salmonTransform(self, key: bytearray, counter: bytearray, encryption_mode: int,
                        srcBuffer: bytearray, srcOffset: int, destBuffer: bytearray, destOffset: int,
                        count: int) -> int:
        """
         * Proxy Transform the input byte array using AES 256 using encryption or decryption mode.
         *
         * @param key
         * @param counter
         * @param encryption_mode
         * @param srcBuffer
         * @param srcOffset
         * @param destBuffer
         * @param destOffset
         * @param count
         * @return
        """
        return NativeProxy.__transform(key, counter, encryption_mode, srcBuffer, srcOffset, destBuffer, destOffset,
                                       count)
