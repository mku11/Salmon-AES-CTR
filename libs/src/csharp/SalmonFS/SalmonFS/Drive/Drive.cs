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

using Mku.FS.File;
using Mku.Salmon.Sequence;
using Mku.SalmonFS.Auth;
using Mku.SalmonFS.File;
using System;

namespace Mku.SalmonFS.Drive;

/// <summary>
///  AesDrive implementation for standard C# file API. This provides a virtual drive implementation
///  that you can use to store and access encrypted files.
/// </summary>
public class Drive : AesDrive
{
    /// <summary>
    /// Private constructor, use open() or create() instead.
    /// </summary>
    private Drive()
    {

    }

    /// <summary>
    /// Helper method that opens and initializes a JsDrive
    /// </summary>
    /// <param name="dir">The directory that will host the drive.</param>
    /// <param name="password">The password</param>
    /// <param name="sequencer">The nonce sequencer that will be used for encryption.</param>
    /// <returns>The drive</returns>
    public static AesDrive Open(IFile dir, string password, INonceSequencer sequencer)
    {
        return AesDrive.OpenDrive(dir, typeof(Drive), password, sequencer);
    }

    /// <summary>
    /// Helper method that creates and initializes a JsDrive
    /// </summary>
    /// <param name="dir">The directory that will host the drive.</param>
    /// <param name="password">The password</param>
    /// <param name="sequencer">The nonce sequencer that will be used for encryption.</param>
    /// <returns>The drive</returns>
    public static AesDrive Create(IFile dir, string password, INonceSequencer sequencer)
    {
        return AesDrive.CreateDrive(dir, typeof(Drive), password, sequencer);
    }

    /// <summary>
    ///  Get a private dir for sharing files with external applications.
    /// </summary>
    ///  <returns>The private directory</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    override
    public IFile PrivateDir
    {
        get { throw new NotSupportedException(); }
    }

    /// <summary>
    ///  When authorization succeed.
    /// </summary>
    override
    public void OnUnlockSuccess()
    {

    }

    /// <summary>
    ///  When authorization succeeds.
    /// </summary>
    override
    public void OnUnlockError()
    {

    }

    /// <summary>
    ///  Return the virtual root directory of the drive.
    /// </summary>
    ///  <returns>The root directory</returns>
    ///  <exception cref="AuthException">Thrown when there is a failure during authorization</exception>
    override
    public AesFile Root => (AesFile)base.Root;
}
