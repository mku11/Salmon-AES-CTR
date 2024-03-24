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
 * Converts from/to byte arrays, integral values, and hex strings.
 */
export class BitConverter {
    /**
     * Converts a long value to byte array.
     * @param value The value to be converted.
     * @param length The length of the byte array to be returned.
     * @return A byte array representation of the value.
     */
    static toBytes(value, length) {
        const buffer = new Uint8Array(length);
        for (let i = length - 1; i >= 0; i--) {
            buffer[i] = value % 256;
            value /= 256;
        }
        return buffer;
    }
    /**
     * Converts a byte array to a long value. Little endian only.
     * @param bytes The byte array to be converted.
     * @param index The starting index of the data in the array that will be converted.
     * @param length The length of the data that will be converted.
     * @return The long value representation of the byte array.
     */
    static toLong(bytes, index, length) {
        let num = 0;
        let mul = 1;
        for (let i = index + length - 1; i >= index; i--) {
            num += bytes[i] * mul;
            mul *= 256;
        }
        return num;
    }
    /**
     * Convert a byte array to a hex representation.
     * @param data The byte array to be converted.
     * @return The hex string representation.
     */
    static toHex(data) {
        let hexString = "";
        for (let i = 0; i < data.length; i++) {
            hexString += data[i].toString(16).padStart(2, "0");
        }
        return hexString;
    }
    /**
     * Convert a hex string to a byte array.
     * @param data The hex string to be converted.
     * @return The byte array converted from the string.
     */
    static hexToBytes(data) {
        const bytes = new Uint8Array(Math.floor(data.length / 2));
        let k = 0;
        for (let i = 0; i < data.length; i += 2) {
            bytes[k] = (16 * parseInt(data.charAt(i) + "", 16));
            bytes[k++] += parseInt(data.charAt(i + 1) + "", 16);
        }
        return bytes;
    }
}
