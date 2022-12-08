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

using System.Runtime.Serialization;
namespace Salmon
{
    public class Utils
    {
        public static string getBytes(long bytes, int decimals)
        {
            string format = "#";
            if (decimals > 0)
                format += ".";
            for (int i = 0; i < decimals; i++)
            {
                format += "0";
            }

            if (bytes > 1024 * 1024 * 1024)
            {
                return string.Format("{0:0.00}", ((double)bytes) / (1024 * 1024 * 1024)) + " GB";
            }
            else if (bytes > 1024 * 1024)
            {
                return string.Format("{0:0.00}", ((double)bytes) / (1024 * 1024)) + " MB";
            }
            else if (bytes > 1024)
            {
                return string.Format("{0:0.00}", ((double)bytes) / 1024) + " KB";
            }
            else if (bytes >= 0)
                return ("" + bytes + " B");
            return "";

        }
    }
}