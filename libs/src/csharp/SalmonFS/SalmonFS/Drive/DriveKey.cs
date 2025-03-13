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

namespace Mku.SalmonFS.Drive;

/// <summary>
///  Encryption keys and properties.
/// </summary>
public class DriveKey
{
    /// <summary>
    /// The master key
    /// </summary>
    public byte[] MasterKey { get; internal set; }

    /// <summary>
    /// The drive key
    /// </summary>
    public byte[] DriveEncKey { get; internal set; }

    /// <summary>
    /// The hash key
    /// </summary>
    public byte[] HashKey { get; internal set; }

    /// <summary>
    /// The iterations
    /// </summary>
    public int Iterations { get; internal set; }

    /// <summary>
    ///  Clear the properties from memory.
    /// </summary>
    public void Clear()
    {

        if (DriveEncKey != null)
            Array.Fill<byte>(DriveEncKey, 0, DriveEncKey.Length, (byte)0);
        DriveEncKey = null;

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
	///  <exception cref="Exception">Thrown if error during operation</exception>
    ~DriveKey()
    {
        Clear();
    }
}
