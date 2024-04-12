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
from typeguard import typechecked


@typechecked
class BitConverter:
    """
    Converts from/to byte arrays, integral values, and hex strings.
    """

    @staticmethod
    def to_bytes(value: int, length: int) -> bytearray:
        """
        Converts a long value to byte array.
        :param value: The value to be converted.
        :param length: The length of the byte array to be returned.
        :return: A byte array representation of the value.
        """
        buffer: bytearray = bytearray(length)
        for i in range(length - 1, -1, -1):
            buffer[i] = value % 256
            value //= 256
        return buffer

    @staticmethod
    def to_long(v_bytes: bytearray, index: int, length: int) -> int:
        """
        Converts a byte array to a long value. Little endian only.
        :param v_bytes: The byte array to be converted.
        :param index: The starting index of the data in the array that will be converted.
        :param length: The length of the data that will be converted.
        :return: The long value representation of the byte array.
        """
        num: int = 0
        mul: int = 1
        for i in range(index + length - 1, index - 1, -1):
            num += (v_bytes[i] & 0xFF) * mul
            mul *= 256
        return num

    """
    Convert a byte array to a hex representation.
    :param data: The byte array to be converted.
    :return: The hex string representation.
    """

    @staticmethod
    def to_hex(data: bytearray) -> str:
        sb = ""
        for b in data:
            sb += format(b, '02x')
        return sb

    @staticmethod
    def hex_to_bytes(data: str) -> bytearray:
        """
       Convert a hex string to a byte array.
       :param data: The hex string to be converted.
       :return: The byte array converted from the string.
        """
        v_bytes: bytearray = bytearray(bytes.fromhex(data))
        return v_bytes
