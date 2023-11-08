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

namespace Mku.SalmonFS;

/// <summary>
///  Encryption keys and properties.
/// </summary>
public class SalmonKey
{

    public byte[] MasterKey { get; internal set; }
    public byte[] DriveKey { get; internal set; }
    public byte[] HashKey { get; internal set; }
    public int Iterations { get; internal set; }

    /// <summary>
    ///  Clear the properties from memory.
    /// </summary>
    public void Clear()
    {

        if (DriveKey != null)
            Array.Fill<byte>(DriveKey, 0, DriveKey.Length, (byte)0);
        DriveKey = null;

        if (HashKey != null)
            Array.Fill<byte>(HashKey, 0, HashKey.Length, (byte)0);
        HashKey = null;

        if (MasterKey != null)
            Array.Fill<byte>(MasterKey, 0, MasterKey.Length, (byte)0);
        MasterKey = null;

        Iterations = 0;
    }

    /// <summary>
    ///  Finalize.
	/// </summary>
	///  <exception cref="Exception"></exception>
    ~SalmonKey()
    {
        Clear();
    }
}
