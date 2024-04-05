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

using Mku.File;
using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using Mku.Sequence;
using System;
using System.IO;
using System.Linq;
using static System.Runtime.InteropServices.JavaScript.JSType;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Device Authorization Configuration. This represents the authorization that will be provided
///  to the target device to allow writing operations for a virtual drive.
/// </summary>
public class SalmonAuthConfig
{
    public byte[] DriveId { get; private set; } = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
    public byte[] AuthId { get; private set; } = new byte[SalmonDriveGenerator.AUTH_ID_SIZE];
    public byte[] StartNonce { get; private set; } = new byte[SalmonGenerator.NONCE_LENGTH];
    public byte[] MaxNonce { get; private set; } = new byte[SalmonGenerator.NONCE_LENGTH];


    /// <summary>
    ///  Instantiate a class with the properties of the authorization config file.
	/// </summary>
	///  <param name="contents">The byte array that contains the contents of the auth config file.</param>
    public SalmonAuthConfig(byte[] contents)
    {
        MemoryStream ms = new MemoryStream(contents);
        ms.Read(DriveId, 0, SalmonDriveGenerator.DRIVE_ID_LENGTH);
        ms.Read(AuthId, 0, SalmonDriveGenerator.AUTH_ID_SIZE);
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
    ///  <param name="targetAuthId">Authorization ID of the target device.</param>
    ///  <param name="targetStartingNonce">Starting nonce for the target device.</param>
    ///  <param name="targetMaxNonce">Maximum nonce for the target device.</param>
    ///  <exception cref="Exception"></exception>
    public static void WriteAuthFile(IRealFile authConfigFile,
                                     SalmonDrive drive,
                                     byte[] targetAuthId,
                                     byte[] targetStartingNonce, byte[] targetMaxNonce,
                                     byte[] configNonce)
    {
        byte[] driveId = drive.DriveId;
        if (driveId == null)
            throw new Exception("Could not write auth file, no drive id found");
        SalmonFile salmonFile = new SalmonFile(authConfigFile, drive);
        salmonFile.AllowOverwrite = true;
        SalmonStream stream = salmonFile.GetOutputStream(configNonce);
        WriteToStream(stream, driveId, targetAuthId, targetStartingNonce, targetMaxNonce);
    }

    /// <summary>
    ///  Write authorization configuration to a SalmonStream.
	/// </summary>
	///  <param name="stream">The stream to write to.</param>
    ///  <param name="driveId">The drive id.</param>
    ///  <param name="authId">The auth id of the new device.</param>
    ///  <param name="nextNonce">The next nonce to be used by the new device.</param>
    ///  <param name="maxNonce">The max nonce to be used byte the new device.</param>
    ///  <exception cref="Exception"></exception>
    public static void WriteToStream(SalmonStream stream, byte[] driveId, byte[] authId,
                                     byte[] nextNonce, byte[] maxNonce)
    {
        MemoryStream ms = new MemoryStream();
        try
        {
            ms.Write(driveId, 0, driveId.Length);
            ms.Write(authId, 0, authId.Length);
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


    /// <summary>
    ///  Get the app drive pair configuration properties for this drive
    /// </summary>
    ///  <param name="authFile">The encrypted authorization file.</param>
    ///  <returns>The decrypted authorization file.</returns>
    ///  <exception cref="Exception"></exception>
    public static SalmonAuthConfig GetAuthConfig(SalmonDrive drive, IRealFile authFile)
    {
        SalmonFile salmonFile = new SalmonFile(authFile, drive);
        SalmonStream stream = salmonFile.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms);
        ms.Close();
        stream.Close();
        SalmonAuthConfig driveConfig = new SalmonAuthConfig(ms.ToArray());
        if (!VerifyAuthId(drive, driveConfig.AuthId))
            throw new SalmonSecurityException("Could not authorize this device, the authorization id does not match");
        return driveConfig;
    }


    /// <summary>
    ///  Verify the authorization id with the current drive auth id.
    /// </summary>
    ///  <param name="authId">The authorization id to verify.</param>
    ///  <returns></returns>
    ///  <exception cref="Exception"></exception>
    private static bool VerifyAuthId(SalmonDrive drive, byte[] authId)
    {
        return Enumerable.SequenceEqual(authId, drive.GetAuthIdBytes());
    }


    /// <summary>
    ///  Import sequence into the current drive.
    /// </summary>
    ///  <param name="authConfig"></param>
    ///  <exception cref="Exception"></exception>
    private static void ImportSequence(SalmonDrive drive, SalmonAuthConfig authConfig)
    {
        string drvStr = BitConverter.ToHex(authConfig.DriveId);
        string authStr = BitConverter.ToHex(authConfig.AuthId);
        drive.Sequencer.InitSequence(drvStr, authStr, authConfig.StartNonce, authConfig.MaxNonce);
    }


    /// <summary>
    ///  Import the device authorization file.
    /// </summary>
    ///  <param name="filePath">The filepath to the authorization file.</param>
    ///  <exception cref="Exception"></exception>
    public static void ImportAuthFile(SalmonDrive drive, IRealFile authConfigFile)
    {
        if (drive.DriveId == null)
            throw new Exception("Could not get drive id, make sure you init the drive first");

        NonceSequence sequence = drive.Sequencer.GetSequence(BitConverter.ToHex(drive.DriveId));
        if (sequence != null && sequence.SequenceStatus == NonceSequence.Status.Active)
            throw new Exception("Device is already authorized");

        if (authConfigFile == null || !authConfigFile.Exists)
            throw new Exception("Could not import file");

        SalmonAuthConfig authConfig = GetAuthConfig(drive, authConfigFile);

        if (!Enumerable.SequenceEqual(authConfig.AuthId, drive.GetAuthIdBytes())
                || !Enumerable.SequenceEqual(authConfig.DriveId, drive.DriveId)
        )
            throw new Exception("Auth file doesn't match driveId or authId");

        ImportSequence(drive,authConfig);
    }


    /// <summary>
    ///  <param name="targetAuthId">The authorization id of the target device.</param>
    /// </summary>
    ///  <param name="targetDir">   The target dir the file will be written to.</param>
    ///  <param name="filename">    The filename of the auth config file.</param>
    ///  <exception cref="Exception"></exception>
    public static void ExportAuthFile(SalmonDrive drive, string targetAuthId, IRealFile file)
    {
        if (drive.DriveId == null)
            throw new Exception("Could not get drive id, make sure you init the drive first");

        byte[] cfgNonce = drive.Sequencer.NextNonce(BitConverter.ToHex(drive.DriveId));

        NonceSequence sequence = drive.Sequencer.GetSequence(BitConverter.ToHex(drive.DriveId));
        if (sequence == null)
            throw new Exception("Device is not authorized to export");

        if (file.Exists && file.Length > 0)
        {
            Stream outStream = null;
            try
            {
                outStream = file.GetOutputStream();
                outStream.SetLength(0);
            }
            catch (Exception ex)
            {
            }
            finally
            {
                if (outStream != null)
                    outStream.Close();
            }
        }
        byte[] maxNonce = sequence.MaxNonce;
        if (maxNonce == null)
            throw new SequenceException("Could not get current max nonce");
        byte[] nextNonce = sequence.NextNonce;
        if (nextNonce == null)
            throw new SequenceException("Could not get next nonce");

        byte[] pivotNonce = SalmonNonce.SplitNonceRange(sequence.NextNonce, sequence.MaxNonce);
        string authId = sequence.AuthId;
        if (authId == null)
            throw new SequenceException("Could not get auth id");

        drive.Sequencer.SetMaxNonce(sequence.DriveId, sequence.AuthId, pivotNonce);
        SalmonAuthConfig.WriteAuthFile(file, drive,
                BitConverter.ToBytes(targetAuthId),
                pivotNonce, sequence.MaxNonce,
                cfgNonce);
    }
}