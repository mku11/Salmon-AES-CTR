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

using System.Globalization;
using System.Text;

namespace Mku.Convert;

/// <summary>
///  Converts from/to byte arrays, integral values, and hex strings.
/// </summary>
public class BitConverter
{
    /// <summary>
    ///  Converts a long value to byte array.
	/// </summary>
	///  <param name="value">The value to be converted.</param>
    ///  <param name="length">The length of the byte array to be returned.</param>
    ///  <returns>A byte array representation of the value.</returns>
    public static byte[] ToBytes(long value, int length)
    {
        byte[] buffer = new byte[length];
        for (int i = length - 1; i >= 0; i--)
        {
            buffer[i] = (byte)(value % 256);
            value /= 256;
        }
        return buffer;
    }

    /// <summary>
    ///  Converts a byte array to a long value. Little endian only.
	/// </summary>
	///  <param name="bytes">The byte array to be converted.</param>
    ///  <param name="index">The starting index of the data in the array that will be converted.</param>
    ///  <param name="length">The length of the data that will be converted.</param>
    ///  <returns>The long value representation of the byte array.</returns>
    public static long ToLong(byte[] bytes, int index, int length)
    {
        long num = 0;
        long mul = 1;
        for (int i = index + length - 1; i >= index; i--)
        {
            num += (bytes[i] & 0xFF) * mul;
            mul *= 256;
        }
        return num;
    }

    /// <summary>
    ///  Convert a byte array to a hex representation.
	/// </summary>
	///  <param name="data">The byte array to be converted.</param>
    ///  <returns>The hex string representation.</returns>
    public static string ToHex(byte[] data)
    {
        StringBuilder sb = new StringBuilder();
        foreach (byte b in data)
            sb.Append(b.ToString("X2"));
        return sb.ToString();
    }

    /// <summary>
    ///  Convert a hex string to a byte array.
	/// </summary>
	///  <param name="data">The hex string to be converted.</param>
    ///  <returns>The byte array converted from the string.</returns>
    public static byte[] ToBytes(string data)
    {
        byte[] bytes = new byte[data.Length / 2];
        int k = 0;
        for (int i = 0; i < data.Length; i += 2)
        {
            bytes[k] = (byte)(16 * byte.Parse(data[i] + "", NumberStyles.HexNumber));
            bytes[k++] += byte.Parse(data[i + 1] + "", NumberStyles.HexNumber);
        }
        return bytes;
    }
}
