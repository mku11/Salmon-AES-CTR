using Mku.File;
using Mku.Salmon;
using Mku.Salmon.Integrity;
using Mku.Salmon.IO;
using System;
using System.IO;

namespace Mku.SalmonFS;
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


/// <summary>
///  Device Authorization Configuration. This represents the authorization that will be provided
///  to the target device to allow writing operations for a virtual drive.
/// </summary>
public class SalmonAuthConfig
{
    public byte[] DriveID { get; private set; } = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
    public byte[] AuthID { get; private set; } = new byte[SalmonDriveGenerator.AUTH_ID_SIZE];
    public byte[] StartNonce { get; private set; } = new byte[SalmonGenerator.NONCE_LENGTH];
    public byte[] MaxNonce { get; private set; } = new byte[SalmonGenerator.NONCE_LENGTH];


    /// <summary>
    ///  Instantiate a class with the properties of the authorization config file.
	/// </summary>
	///  <param name="contents">The byte array that contains the contents of the auth config file.</param>
    public SalmonAuthConfig(byte[] contents)
    {
        MemoryStream ms = new MemoryStream(contents);
        ms.Read(DriveID, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        ms.Read(AuthID, 0, SalmonDriveGenerator.AUTH_ID_SIZE);
        ms.Read(StartNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.Read(MaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
        ms.Close();
    }

    /// <summary>
    ///  Write the properties of the auth configuration to a config file that will be imported by another device.
    ///  The new device will then be authorized editing operations ie: import, rename files, etc.
	/// </summary>
	///  <param name="authConfigFile"></param>
    ///  <param name="drive">The drive you want to create an auth config for.</param>
    ///  <param name="targetAuthID">Authorization ID of the target device.</param>
    ///  <param name="targetStartingNonce">Starting nonce for the target device.</param>
    ///  <param name="targetMaxNonce">Maximum nonce for the target device.</param>
    ///  <exception cref="Exception"></exception>
    public static void WriteAuthFile(IRealFile authConfigFile,
                                     SalmonDrive drive,
                                     byte[] targetAuthID,
                                     byte[] targetStartingNonce, byte[] targetMaxNonce,
                                     byte[] configNonce)
    {
        SalmonFile salmonFile = new SalmonFile(authConfigFile, drive);
        salmonFile.AllowOverwrite = true;
        SalmonStream stream = salmonFile.GetOutputStream(configNonce);
        WriteToStream(stream, drive.DriveID, targetAuthID, targetStartingNonce, targetMaxNonce);
    }

    /// <summary>
    ///  Write authorization configuration to a SalmonStream.
	/// </summary>
	///  <param name="stream">The stream to write to.</param>
    ///  <param name="driveID">The drive id.</param>
    ///  <param name="authID">The auth id of the new device.</param>
    ///  <param name="nextNonce">The next nonce to be used by the new device.</param>
    ///  <param name="maxNonce">The max nonce to be used byte the new device.</param>
    ///  <exception cref="Exception"></exception>
    public static void WriteToStream(SalmonStream stream, byte[] driveID, byte[] authID,
                                     byte[] nextNonce, byte[] maxNonce)
    {
        MemoryStream ms = new MemoryStream();
        try
        {
            ms.Write(driveID, 0, driveID.Length);
            ms.Write(authID, 0, authID.Length);
            ms.Write(nextNonce, 0, nextNonce.Length);
            ms.Write(maxNonce, 0, maxNonce.Length);
            byte[] content = ms.ToArray();
            byte[] buffer = new byte[SalmonIntegrity.DEFAULT_CHUNK_SIZE];
            Array.Copy(content, 0, buffer, 0, content.Length);
            stream.Write(buffer, 0, content.Length);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw new SalmonAuthException("Could not write auth config", ex);
        }
        finally
        {
            ms.Close();
            stream.Flush();
            stream.Close();
        }
    }
}