package com.mku.convert;
/*
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
*/

/**
 * Converts from/to: byte arrays, integral values, and hex strings.
 */
public class BitConverter {

    /**
     * Converts a long value to byte array.
     *
     * @param value  The value to be converted.
     * @param length The length of the byte array to be returned.
     * @return A byte array representation of the value.
     */
    public static byte[] toBytes(long value, int length) {
        byte[] buffer = new byte[length];
        for (int i = length - 1; i >= 0; i--) {
            buffer[i] = (byte) (value % 256);
            value /= 256;
        }
        return buffer;
    }

    /**
     * Converts a byte array to a long value. Little endian only.
     *
     * @param bytes  The byte array to be converted.
     * @param index  The starting index of the data in the array that will be converted.
     * @param length The length of the data that will be converted.
     * @return The long value representation of the byte array.
     */
    public static long toLong(byte[] bytes, int index, int length) {
        long num = 0;
        long mul = 1;
        for (int i = index + length - 1; i >= index; i--) {
            num += (bytes[i] & 0xFF) * mul;
            mul *= 256;
        }
        return num;
    }

    /**
     * Convert a byte array to a hex representation.
     *
     * @param data The byte array to be converted.
     * @return The hex string representation.
     */
    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * Convert a hex string to a byte array.
     *
     * @param data The hex string to be converted.
     * @return The byte array converted from the string.
     */
    public static byte[] toBytes(String data) {
        byte[] bytes = new byte[data.length() / 2];
        int k = 0;
        for (int i = 0; i < data.length(); i += 2) {
            bytes[k] = (byte) (16 * Byte.parseByte(data.charAt(i) + "", 16));
            bytes[k++] += Byte.parseByte(data.charAt(i + 1) + "", 16);
        }
        return bytes;
    }
}
