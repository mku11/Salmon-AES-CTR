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
using Mku.Salmon;
using Mku.Salmon.Integrity;
using Mku.Salmon.IO;
using Mku.Sequence;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.SalmonFS;

/// <summary>
///  Manages the drive and nonce sequencer to be used.
///  Currently only one drive and one nonce sequencer are supported.
/// </summary>
public class SalmonDriveManager
{

    /// <summary>
    ///  Set the global drive class. Currently only one drive is supported.
	/// </summary>
    public static Type VirtualDriveClass { get; set; }

    /// <summary>
    ///  Current drive.
	/// </summary>
    public static SalmonDrive Drive { get; private set; }

    /// <summary>
    ///  Set the nonce sequencer used for the current drive.
	/// </summary>
    public static ISalmonSequencer Sequencer { get; set; }


    /// <summary>
    ///  Set the drive location to an external directory.
    ///  This requires you previously use SetDriveClass() to provide a class for the drive
	/// </summary>
	///  <param name="dirPath">The directory path that will be used for storing the contents of the drive</param>
    public static SalmonDrive OpenDrive(string dirPath)
    {
        LockDrive();
        SalmonDrive drive = CreateDriveInstance(dirPath);
        if (drive == null || !drive.HasConfig())
        {
            throw new Exception("Drive does not exist");
        }
        SalmonDriveManager.Drive = drive;
        return Drive;
    }

    /// <summary>
    ///  Create a new drive in the provided location.
	/// </summary>
	///  <param name="dirPath"> Directory to store the drive configuration and virtual filesystem.</param>
    ///  <param name="password">Master password to encrypt the drive configuration.</param>
    ///  <returns>The newly created drive.</returns>
    ///  <exception cref="SalmonIntegrityException"></exception>
    ///  <exception cref="SalmonSequenceException"></exception>
    public static SalmonDrive CreateDrive(string dirPath, string password)
    {
        LockDrive();
        SalmonDrive drive = CreateDriveInstance(dirPath, true);
        if (drive.HasConfig())
            throw new SalmonSecurityException("Drive already exists");
        SalmonDriveManager.Drive = drive;
        drive.SetPassword(password);
        return drive;
    }

    /// <summary>
    ///  Create a drive instance.
	/// </summary>
	///  <param name="dirPath">The target directory where the drive is located.</param>
    ///  <param name="createIfNotExists">Create the drive if it does not exist</param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSecurityException"></exception>
    private static SalmonDrive CreateDriveInstance(string dirPath, bool createIfNotExists = false)
    {

        SalmonDrive drive;
        try
        {
            drive = Activator.CreateInstance(VirtualDriveClass, new object[] { dirPath, createIfNotExists }) as SalmonDrive;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
        return drive;
    }

    /// <summary>
    ///  Lock the current drive.
    /// </summary>
    public static void LockDrive()
    {
        if (Drive != null)
        {
            Drive.Lock();
            Drive = null;
        }
    }

    /// <summary>
    ///  Get the device authorization byte array for the current drive.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="Exception"></exception>
    static byte[] GetAuthIDBytes()
    {
        string drvStr = BitConverter.ToHex(Drive.DriveID);
        SalmonSequence sequence = Sequencer.GetSequence(drvStr);
        if (sequence == null)
        {
            byte[] authID = SalmonDriveGenerator.GenerateAuthId();
            CreateSequence(Drive.DriveID, authID);
        }
        sequence = Sequencer.GetSequence(drvStr);
        return BitConverter.ToBytes(sequence.AuthID);
    }

    /// <summary>
    ///  Import the device authorization file.
	/// </summary>
	///  <param name="filePath">The filepath to the authorization file.</param>
    ///  <exception cref="Exception"></exception>
    public static void ImportAuthFile(string filePath)
    {
        SalmonSequence sequence = Sequencer.GetSequence(BitConverter.ToHex(Drive.DriveID));
        if (sequence != null && sequence.SequenceStatus == SalmonSequence.Status.Active)
            throw new Exception("Device is already authorized");

        IRealFile authConfigFile = Drive.GetRealFile(filePath, false);
        if (authConfigFile == null || !authConfigFile.Exists)
            throw new Exception("Could not import auth file");

        SalmonAuthConfig authConfig = GetAuthConfig(authConfigFile);

        if (!Enumerable.SequenceEqual(authConfig.AuthID, SalmonDriveManager.GetAuthIDBytes())
                || !Enumerable.SequenceEqual(authConfig.DriveID, Drive.DriveID)
        )
            throw new Exception("Auth file doesn't match driveID or authID");

        SalmonDriveManager.ImportSequence(authConfig);
    }

    /// <summary>
    ///  Get the default auth config filename.
	/// </summary>
	///  <returns></returns>
    public static string GetDefaultAuthConfigFilename()
    {
        return SalmonDrive.AuthConfigFilename;
    }

    /// <summary>
    ///  <param name="targetAuthID">The authorization id of the target device.</param>
	/// </summary>
	///  <param name="targetDir">   The target dir the file will be written to.</param>
    ///  <param name="filename">    The filename of the auth config file.</param>
    ///  <exception cref="Exception"></exception>
    public static void ExportAuthFile(string targetAuthID, string targetDir, string filename)
    {
        byte[] cfgNonce = Sequencer.NextNonce(BitConverter.ToHex(Drive.DriveID));

        SalmonSequence sequence = Sequencer.GetSequence(BitConverter.ToHex(Drive.DriveID));
        if (sequence == null)
            throw new Exception("Device is not authorized to export");
        IRealFile dir = Drive.GetRealFile(targetDir, true);
        IRealFile targetAppDriveConfigFile = dir.GetChild(filename);
        if (targetAppDriveConfigFile == null || !targetAppDriveConfigFile.Exists)
            targetAppDriveConfigFile = dir.CreateFile(filename);
        else if (targetAppDriveConfigFile != null && targetAppDriveConfigFile.Exists)
            throw new SalmonAuthException(filename + " already exists, delete this file or choose another directory");

        byte[] pivotNonce = SalmonNonce.SplitNonceRange(sequence.NextNonce, sequence.MaxNonce);
        Sequencer.SetMaxNonce(sequence.DriveID, sequence.AuthID, pivotNonce);
        SalmonAuthConfig.WriteAuthFile(targetAppDriveConfigFile, Drive,
                BitConverter.ToBytes(targetAuthID),
                pivotNonce, sequence.MaxNonce,
                cfgNonce);
    }

    /// <summary>
    ///  Get the next nonce for the drive. This operation IS atomic as per transaction.
	/// </summary>
	///  <param name="salmonDrive"></param>
    ///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="SalmonRangeExceededException"></exception>
    public static byte[] GetNextNonce(SalmonDrive salmonDrive)
    {
        return Sequencer.NextNonce(BitConverter.ToHex(salmonDrive.DriveID));
    }

    /// <summary>
    ///  Create a nonce sequence for the drive id and the authorization id provided. Should be called
    ///  once per driveID/authID combination.
	/// </summary>
	///  <param name="driveID">The driveID</param>
    ///  <param name="authID"> The authID</param>
    ///  <exception cref="Exception"></exception>
    internal static void CreateSequence(byte[] driveID, byte[] authID)
    {
        string drvStr = BitConverter.ToHex(driveID);
        string authStr = BitConverter.ToHex(authID);
        Sequencer.CreateSequence(drvStr, authStr);
    }

    /// <summary>
    ///  Initialize the nonce sequencer with the current drive nonce range. Should be called
    ///  once per driveID/authID combination.
	/// </summary>
	///  <param name="driveID">Drive ID.</param>
    ///  <param name="authID"> Authorization ID.</param>
    ///  <exception cref="Exception"></exception>
    internal static void InitSequence(byte[] driveID, byte[] authID)
    {
        byte[] startingNonce = SalmonDriveGenerator.GetStartingNonce();
        byte[] maxNonce = SalmonDriveGenerator.GetMaxNonce();
        string drvStr = BitConverter.ToHex(driveID);
        string authStr = BitConverter.ToHex(authID);
        Sequencer.InitSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /// <summary>
    ///  Revoke authorization for this device. This will effectively terminate write operations on the current disk
    ///  by the current device. Warning: If you need to authorize write operations to the device again you will need
    ///  to have another device to export an authorization config file and reimport it.
    /// <para>See: <see href="https://github.com/mku11/Salmon-AES-CTR#readme">README.md</see></para>
	/// </summary>
	///  <exception cref="Exception"></exception>
    public static void RevokeAuthorization()
    {
        byte[] driveID = Drive.DriveID;
        Sequencer.RevokeSequence(BitConverter.ToHex(driveID));
    }

    /// <summary>
    ///  Verify the authorization id with the current drive auth id.
	/// </summary>
	///  <param name="authID">The authorization id to verify.</param>
    ///  <returns></returns>
    ///  <exception cref="Exception"></exception>
    private static bool VerifyAuthID(byte[] authID)
    {
        return Enumerable.SequenceEqual(authID, SalmonDriveManager.GetAuthIDBytes());
    }

    /// <summary>
    ///  Import sequence into the current drive.
	/// </summary>
	///  <param name="authConfig"></param>
    ///  <exception cref="Exception"></exception>
    private static void ImportSequence(SalmonAuthConfig authConfig)
    {
        string drvStr = BitConverter.ToHex(authConfig.DriveID);
        string authStr = BitConverter.ToHex(authConfig.AuthID);
        Sequencer.InitSequence(drvStr, authStr, authConfig.StartNonce, authConfig.MaxNonce);
    }

    /// <summary>
    ///  Get the app drive pair configuration properties for this drive
	/// </summary>
	///  <param name="authFile">The encrypted authorization file.</param>
    ///  <returns>The decrypted authorization file.</returns>
    ///  <exception cref="Exception"></exception>
    public static SalmonAuthConfig GetAuthConfig(IRealFile authFile)
    {
        SalmonFile salmonFile = new SalmonFile(authFile, Drive);
        SalmonStream stream = salmonFile.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms);
        ms.Close();
        stream.Close();
        SalmonAuthConfig driveConfig = new SalmonAuthConfig(ms.ToArray());
        if (!VerifyAuthID(driveConfig.AuthID))
            throw new SalmonSecurityException("Could not authorize this device, the authorization id does not match");
        return driveConfig;
    }

    /// <summary>
    ///  Get the authorization ID for the current device.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="SalmonSequenceException"></exception>
    ///  <exception cref="SalmonAuthException"></exception>
    public static string GetAuthID()
    {
        return BitConverter.ToHex(GetAuthIDBytes());
    }
}
