
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
using Mku.IO;
using Mku.Salmon;
using Mku.Salmon.Integrity;
using Mku.Salmon.IO;
using Mku.Salmon.Password;
using System.Runtime.CompilerServices;

namespace Mku.SalmonFS;

/// <summary>
///  Class provides an abstract virtual drive that can be extended for use with
///  any filesystem ie disk, net, cloud, etc.
///  Drive implementations needs to be realized together with <see cref="IRealFile"/> }.
/// </summary>
public abstract class SalmonDrive
{
    private static readonly int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;

    /// <summary>
    /// Default config filename
    /// </summary>
    public static string ConfigFilename { get; set; } = "vault.slmn";
    /// <summary>
    /// Default aUthorization filename
    /// </summary>
    public static string AuthConfigFilename { get; set; } = "auth.slma";
    /// <summary>
    /// Virtual drive directory to host the encrypted files
    /// </summary>
    public static string VirtualDriveDirectoryName { get; set; } = "fs";
    /// <summary>
    /// Default shared directory.
    /// </summary>
    public static string ShareDirectoryName { get; set; } = "share";
    /// <summary>
    /// Default export directory filename
    /// </summary>
    public static string ExportDirectoryName { get; set; } = "export";  

    /// <summary>
    /// Default file chunk that will be used to import new files.
    /// </summary>
    public int DefaultFileChunkSize { get; set; } = DEFAULT_FILE_CHUNK_SIZE;

    /// <summary>
    /// The current key
    /// </summary>
    public SalmonKey Key { get; private set; } = null;

    /// <summary>
    /// The current drive ID
    /// </summary>
    public byte[] DriveID { get; private set; }

    /// <summary>
    /// The real root location of the vault
    /// </summary>
    public IRealFile RealRoot { get; private set; } = null;
    private SalmonFile virtualRoot = null;
    private readonly IHashProvider hashProvider = new HmacSHA256Provider();

    /// <summary>
    ///  Create a virtual drive at the directory path provided
	/// </summary>
	///  <param name="realRootPath">The path of the real directory</param>
    ///  <param name="createIfNotExists">Create the drive if it does not exist</param>
    protected SalmonDrive(string realRootPath, bool createIfNotExists = false)
    {
        Close();
        if (realRootPath == null)
            return;
        RealRoot = GetRealFile(realRootPath, true);
        if (!createIfNotExists && !HasConfig() && RealRoot.Parent != null && RealRoot.Parent.Exists)
        {
            // try the parent if this is the filesystem folder 
            IRealFile originalRealRoot = RealRoot;
            RealRoot = RealRoot.Parent;
            if (!HasConfig())
            {
                // revert to original
                RealRoot = originalRealRoot;
            }
        }

        IRealFile virtualRootRealFile = RealRoot.GetChild(VirtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !virtualRootRealFile.Exists))
        {
            virtualRootRealFile = RealRoot.CreateDirectory(VirtualDriveDirectoryName);
        }
        virtualRoot = CreateVirtualRoot(virtualRootRealFile);
        RegisterOnProcessClose();
        Key = new SalmonKey();
    }

    /// <summary>
    ///  Get a file or directory from the current real filesystem. Used internally
    ///  for accessing files from the real filesystem.
    /// </summary>
    ///  <param name="filepath">The real file path</param>
    ///  <param name="isDirectory">True if filepath corresponds to a directory.</param>
    ///  <returns></returns>
    public abstract IRealFile GetRealFile(string filepath, bool isDirectory);

    /// <summary>
    ///  Method is called when the user is authenticated
    /// </summary>
    protected abstract void OnAuthenticationSuccess();

    /// <summary>
    ///  Method is called when the user authentication has failed
    /// </summary>
    protected abstract void OnAuthenticationError();

    /// <summary>
    ///  Clear sensitive information when app is close.
    /// </summary>
    private void RegisterOnProcessClose()
    {
        //TODO
    }

    /// <summary>
    ///  Change the user password.
	/// </summary>
	///  <param name="pass">The new password.</param>
    ///  <exception cref="IOException"></exception>
    ///  <exception cref="SalmonAuthException"></exception>
    ///  <exception cref="SalmonSecurityException"></exception>
    ///  <exception cref="SalmonIntegrityException"></exception>
    ///  <exception cref="Sequence.SalmonSequenceException"></exception>
    [MethodImpl(MethodImplOptions.Synchronized)]
    public void SetPassword(string pass)
    {
        CreateConfig(pass);
    }

    /// <summary>
    ///  Initialize the drive virtual filesystem.
    /// </summary>
    protected void InitFS()
    {
        IRealFile virtualRootRealFile = RealRoot.GetChild(VirtualDriveDirectoryName);
        if (virtualRootRealFile == null || !virtualRootRealFile.Exists)
        {
            try
            {
                virtualRootRealFile = RealRoot.CreateDirectory(VirtualDriveDirectoryName);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
        }
        virtualRoot = CreateVirtualRoot(virtualRootRealFile);
    }

    protected SalmonFile CreateVirtualRoot(IRealFile virtualRootRealFile)
    {
        return new SalmonFile(virtualRootRealFile, this);
    }

    /// <summary>
    ///  Create a configuration file for the drive.
	/// </summary>
	///  <param name="password">The new password to be saved in the configuration</param>
    ///                  This password will be used to derive the master key that will be used to
    ///                  encrypt the combined key (encryption key + hash key)
    //TODO: partial refactor to SalmonDriveConfig
    private void CreateConfig(string password)
    {
        byte[] driveKey = Key.DriveKey;
        byte[] hashKey = Key.HashKey;

        IRealFile configFile = RealRoot.GetChild(ConfigFilename);

        // if it's an existing config that we need to update with
        // the new password then we prefer to be authenticate
        // TODO: we should probably call Authenticate() rather than assume
        //  that the key != null. Though the user can anyway manually delete the config file
        //  so it doesn't matter.
        if (driveKey == null && configFile != null && configFile.Exists)
            throw new SalmonAuthException("Not authenticated");

        // delete the old config file and create a new one
        if (configFile != null && configFile.Exists)
            configFile.Delete();
        configFile = RealRoot.CreateFile(ConfigFilename);

        byte[] magicBytes = SalmonGenerator.GetMagicBytes();

        byte version = SalmonGenerator.VERSION;

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        bool newDrive = false;
        if (driveKey == null)
        {
            newDrive = true;
            driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            byte[] combKey = SalmonDriveGenerator.GenerateCombinedKey();
            Array.Copy(combKey, 0, driveKey, 0, SalmonGenerator.KEY_LENGTH);
            Array.Copy(combKey, SalmonGenerator.KEY_LENGTH, hashKey, 0, SalmonGenerator.HASH_KEY_LENGTH);
            DriveID = SalmonDriveGenerator.GenerateDriveID();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        byte[] salt = SalmonDriveGenerator.GenerateSalt();

        int iterations = SalmonDriveGenerator.GetIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        byte[] masterKeyIv = SalmonDriveGenerator.GenerateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        byte[] masterKey = SalmonPassword.GetMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        MemoryStream ms = new MemoryStream();
        SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.Write(driveKey, 0, driveKey.Length);
        stream.Write(hashKey, 0, hashKey.Length);
        stream.Write(DriveID, 0, DriveID.Length);
        stream.Flush();
        stream.Close();
        byte[] encData = ms.ToArray();

        // generate the hash signature
        byte[] hashSignature = SalmonIntegrity.CalculateHash(hashProvider, encData, 0, encData.Length, hashKey, null);

        SalmonDriveConfig.WriteDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encData, hashSignature);
        SetKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive)
        {
            // create a full sequence for nonces
            byte[] authID = SalmonDriveGenerator.GenerateAuthId();
            SalmonDriveManager.CreateSequence(DriveID, authID);
            SalmonDriveManager.InitSequence(DriveID, authID);
        }
        InitFS();
    }

    /// <summary>
    ///  Return the virtual root directory of the drive.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="SalmonAuthException"></exception>
    public SalmonFile VirtualRoot
    {
        get
        {
            if (RealRoot == null || !RealRoot.Exists)
                return null;
            if (!IsAuthenticated)
                throw new SalmonAuthException("Not authenticated");
            return virtualRoot;
        }
    }

    /// <summary>
    ///  Verify if the user password is correct otherwise it will throw a SalmonAuthException
	/// </summary>
	///  <param name="password">The password.</param>
    public void Authenticate(string password)
    {
        SalmonStream stream = null;
        try
        {
            if (password == null)
            {
                throw new SalmonSecurityException("Password is missing");
            }
            SalmonDriveConfig salmonConfig = GetDriveConfig();
            int iterations = salmonConfig.GetIterations();
            byte[] salt = salmonConfig.Salt;

            // derive the master key from the text password
            byte[] masterKey = SalmonPassword.GetMasterKey(password, salt, iterations, SalmonDriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            byte[] masterKeyIv = salmonConfig.Iv;

            // get the encrypted combined key and drive id
            byte[] encData = salmonConfig.EncryptedData;

            // decrypt the combined key (drive key + hash key) using the master key
            MemoryStream ms = new MemoryStream(encData);
            stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            stream.Read(driveKey, 0, driveKey.Length);

            byte[] hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            stream.Read(hashKey, 0, hashKey.Length);

            byte[] driveID = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
            stream.Read(driveID, 0, driveID.Length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            VerifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            SetKey(masterKey, driveKey, hashKey, iterations);
            this.DriveID = driveID;
            InitFS();
            OnAuthenticationSuccess();
        }
        catch (Exception ex)
        {
            OnAuthenticationError();
            throw ex;
        }
        finally
        {
            if (stream != null)
                stream.Close();
        }
    }

    /// <summary>
    ///  Sets the key properties.
	/// </summary>
	///  <param name="masterKey">The master key.</param>
    ///  <param name="driveKey">The drive key used for enc/dec of files and filenames.</param>
    ///  <param name="hashKey">The hash key used for data integrity.</param>
    ///  <param name="iterations"></param>
    private void SetKey(byte[] masterKey, byte[] driveKey, byte[] hashKey, int iterations)
    {
        Key.MasterKey = masterKey;
        Key.DriveKey = driveKey;
        Key.HashKey = hashKey;
        Key.Iterations = iterations;
    }

    /// <summary>
    ///  Verify that the hash signature is correct
	/// </summary>
	///  <param name="salmonConfig"></param>
    ///  <param name="data"></param>
    ///  <param name="hashKey"></param>
    private void VerifyHash(SalmonDriveConfig salmonConfig, byte[] data, byte[] hashKey)
    {
        byte[] hashSignature = salmonConfig.HashSignature;
        byte[] hash = SalmonIntegrity.CalculateHash(hashProvider, data, 0, data.Length, hashKey, null);
        for (int i = 0; i < hashKey.Length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Could not authenticate");
    }

    /// <summary>
    ///  Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
	/// </summary>
	///  <returns></returns>
    ///  <exception cref="Exception"></exception>
    internal byte[] GetNextNonce()
    {
        if (!IsAuthenticated)
            throw new SalmonAuthException("Not authenticated");
        return SalmonDriveManager.GetNextNonce(this);
    }

    /// <summary>
    ///  Returns true if password authentication has succeeded.
    /// </summary>
    public bool IsAuthenticated
    {
        get
        {
            SalmonKey key = Key;
            if (key == null)
                return false;
            byte[] encKey = key.DriveKey;
            return encKey != null;
        }
    }

    /// <summary>
    ///  Get the byte contents of a file from the real filesystem.
	/// </summary>
	///  <param name="sourcePath">The path of the file</param>
    ///  <param name="bufferSize">The buffer to be used when reading</param>
    public byte[] GetBytesFromRealFile(string sourcePath, int bufferSize)
    {
        IRealFile file = GetRealFile(sourcePath, false);
        Stream stream = file.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms, bufferSize, null);
        ms.Flush();
        ms.Position = 0;
        byte[] byteContents = ms.ToArray();
        ms.Close();
        stream.Close();
        return byteContents;
    }

    /// <summary>
    ///  Return the drive configuration file.
    /// </summary>
    private IRealFile GetDriveConfigFile()
    {
        if (RealRoot == null || !RealRoot.Exists)
            return null;
        IRealFile file = RealRoot.GetChild(ConfigFilename);
        return file;
    }

    /// <summary>
    ///  Return the default external export dir that all file can be exported to.
	/// </summary>
	///  <returns>The file on the real filesystem.</returns>
    public IRealFile ExportDir
    {
        get
        {
            IRealFile virtualThumbnailsRealDir = RealRoot.GetChild(ExportDirectoryName);
            if (virtualThumbnailsRealDir == null || !virtualThumbnailsRealDir.Exists)
                virtualThumbnailsRealDir = RealRoot.CreateDirectory(ExportDirectoryName);
            return virtualThumbnailsRealDir;
        }
    }

    /// <summary>
    ///  Return the configuration properties of this drive.
    /// </summary>
    protected SalmonDriveConfig GetDriveConfig()
    {
        IRealFile configFile = GetDriveConfigFile();
        if (configFile == null || !configFile.Exists)
            return null;
        byte[] bytes = GetBytesFromRealFile(configFile.Path, 0);
        SalmonDriveConfig driveConfig = new SalmonDriveConfig(bytes);
        return driveConfig;
    }

    /// <summary>
    ///  Return true if the drive is already created and has a configuration file.
    /// </summary>
    public bool HasConfig()
    {
        SalmonDriveConfig salmonConfig;
        try
        {
            salmonConfig = GetDriveConfig();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            return false;
        }
        return salmonConfig != null;
    }


    /// <summary>
    ///  Close the drive and associated resources.
    /// </summary>
    public void Close()
    {
        RealRoot = null;
        virtualRoot = null;
        DriveID = null;
        if (Key != null)
            Key.Clear();
        Key = null;
    }
}