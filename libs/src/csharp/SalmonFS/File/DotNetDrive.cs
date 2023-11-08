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

using Mku.SalmonFS;
using System;

namespace Mku.File;

/// <summary>
///  SalmonDrive implementation for standard C# file API. This provides a virtual drive implementation
///  that you can use to store and access encrypted files.
/// </summary>
public class DotNetDrive : SalmonDrive
{

    /// <summary>
    ///  Instantiate a virtual drive with the provided real filepath.
    ///  Encrypted files will be located under the <see cref="SalmonDrive.VIRTUAL_DRIVE_DIR"/>.
	/// </summary>
	///  <param name="realRoot">The filepath to the location of the virtual drive.</param>
	///  <param name="createIfNotExists">Create the drive if it doesn't exist.</param>
    public DotNetDrive(string realRoot, bool createIfNotExists) : base(realRoot, createIfNotExists)
    {

    }

    /// <summary>
    ///  Get a private dir for sharing files with external applications.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="Exception"></exception>
    public static string PrivateDir
    {
        get
        {
            throw new NotSupportedException();
        }
    }

    /// <summary>
    ///  Get a file from the real filesystem.
	/// </summary>
	///  <param name="filepath">The file path.</param>
    ///  <param name="isDirectory">True if filepath corresponds to a directory.</param>
    ///  <returns></returns>
	override
    public IRealFile GetRealFile(string filepath, bool isDirectory)
    {
        DotNetFile DotNetFile = new DotNetFile(filepath);
        return DotNetFile;
    }

    /// <summary>
    ///  When authentication succeed.
	/// </summary>
	    override
    protected void OnAuthenticationSuccess()
    {

    }

    /// <summary>
    ///  When authentication succeeds.
	/// </summary>
	    override
    protected void OnAuthenticationError()
    {

    }
}
