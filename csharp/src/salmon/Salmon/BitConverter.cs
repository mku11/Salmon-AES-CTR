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

using System;
using System.Text;

namespace Salmon
{
    public class BitConverter
    {

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

        public static long ToLong(byte[] bytes, int index, int length)
        {
            long num = 0;
            long mul = 1;
            for(int i=index+length-1; i>=index; i--)
            {
                num += bytes[i]*mul;
                mul *= 256;
            }
            return num;
        }

        public static string ToHex(byte[] driveId)
        {
            StringBuilder sb = new StringBuilder();
            foreach (byte b in driveId)
                sb.Append(b.ToString("X2"));
            return sb.ToString();
        }

        public static byte[] ToBytes(string data)
        {
            byte[] bytes = new byte[data.Length/2];
            int k = 0;
            for (int i = 0; i < data.Length; i += 2)
            {
                bytes[k] = (byte)(16 * byte.Parse(data[i] + "", System.Globalization.NumberStyles.HexNumber));
                bytes[k++] += byte.Parse(data[i + 1] + "", System.Globalization.NumberStyles.HexNumber);
            }
            return bytes;
        }

        public static byte[] Increase(byte[] num, byte[] max)
        {
            long n = BitConverter.ToLong(num, 0, num.Length);
            long m = BitConverter.ToLong(max, 0, max.Length);
            n++;
            if (n <= 0 || n > m)
                throw new Exception("Range exceeded");
            return BitConverter.ToBytes(n, 8);
        }

        public static byte[] Split(byte[] start, byte[] end)
        {
            long s = BitConverter.ToLong(start, 0, start.Length);
            long e = BitConverter.ToLong(end, 0, end.Length);
            // we reserve some nonces
            if (e - s < 256)
                throw new Exception("Insufficient Range Size");
            return BitConverter.ToBytes(s + (e - s) / 2, start.Length);
        }
    }
}