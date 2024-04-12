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
using Mku.Streams;
using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using Mku.Salmon.Password;
using System.Runtime.CompilerServices;
using Mku.Drive;
using Mku.Integrity;
using System;
using System.IO;
using Mku.Sequence;
using Mku.Salmon.Sequence;
using System.Linq;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Class provides an abstract virtual drive that can be extended for use with
///  any filesystem ie disk, net, cloud, etc.
///  Drive implementations needs to be realized together with <see cref="IRealFile"/> }.
/// </summary>
public abstract class SalmonDrive : VirtualDrive
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
    public SalmonDriveKey Key { get; private set; } = null;

    /// <summary>
    /// The current drive ID
    /// </summary>
    public byte[] DriveId { get; private set; }

    /// <summary>
    /// The real root location of the vault
    /// </summary>
    public IRealFile RealRoot { get; private set; } = null;
    private SalmonFile virtualRoot = null;
    private readonly IHashProvider hashProvider = new HmacSHA256Provider();
    
    /// <summary>
    ///  Set the nonce sequencer used for the current drive.
	/// </summary>
    public INonceSequencer Sequencer { get; set; }

    /// <summary>
    ///  Create a virtual drive at the directory path provided
	/// </summary>
	///  <param name="realRoot">The root of the real directory</param>
    ///  <param name="createIfNotExists">Create the drive if it does not exist</param>
    protected void Initialize(IRealFile realRoot, bool createIfNotExists = false)
    {
        Close();
        if (realRoot == null)
            return;
        this.RealRoot = realRoot;
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
        virtualRoot = new SalmonFile(virtualRootRealFile, this);
        RegisterOnProcessClose();
        Key = new SalmonDriveKey();
    }

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
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="SalmonAuthException">Thrown when there is a failure during authorization</exception>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
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
        virtualRoot = new SalmonFile(virtualRootRealFile, this);
    }

    /// <summary>
    ///  Set the drive location to an external directory.
    ///  This requires you previously use SetDriveClass() to provide a class for the drive
    /// </summary>
    ///  <param name="dir">The directory that will be used for storing the contents of the drive</param>
    ///  <param name="driveClassType">The class type of the drive (ie: typeof(DotNetDrive))</param>
    ///  <param name="password">password</param>
    ///  <param name="sequencer">The sequencer</param>
    public static SalmonDrive OpenDrive(IRealFile dir, Type driveClassType,
                                        string password, INonceSequencer sequencer)
    {
        SalmonDrive drive = CreateDriveInstance(dir, false,
            driveClassType, sequencer);
        if (drive == null || !drive.HasConfig())
        {
            throw new IOException("Drive does not exist");
        }
        drive.Unlock(password);
        return drive;
    }

    /// <summary>
    ///  Create a new drive in the provided location.
	/// </summary>
	///  <param name="dir"> Directory to store the drive configuration and virtual filesystem.</param>
    ///  <param name="driveClassType">The drive class type of this drive (ie typeof(DotNetDrive)) </param>
    ///  <param name="password">Master password to encrypt the drive configuration.</param>
    ///  <param name="sequencer">The sequencer</param>
    ///  <returns>The newly created drive.</returns>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public static SalmonDrive CreateDrive(IRealFile dir, Type driveClassType,
        string password, INonceSequencer sequencer)
    {
        SalmonDrive drive = CreateDriveInstance(dir, true,
            driveClassType, sequencer);
        if (drive.HasConfig())
            throw new SalmonSecurityException("Drive already exists");
        drive.SetPassword(password);
        return drive;
    }

    /// <summary>
    ///  Create a drive instance.
	/// </summary>
	///  <param name="dir">The directory where the drive is located.</param>
    ///  <param name="createIfNotExists">Create the drive if it does not exist</param>
    ///  <param name="driveClassType">Create the drive if it does not exist</param>
    ///  <param name="sequencer">Create the drive if it does not exist</param>
    ///  <returns>The drive</returns>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    private static SalmonDrive CreateDriveInstance(IRealFile dir, bool createIfNotExists,
        Type driveClassType, INonceSequencer sequencer)
    {

        SalmonDrive drive;
        try
        {
            drive = Activator.CreateInstance(driveClassType, true) as SalmonDrive;
            drive.Initialize(dir, createIfNotExists);
            drive.Sequencer = sequencer;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            throw new SalmonSecurityException("Could not create drive instance", e);
        }
        return drive;
    }


    /// <summary>
    ///  Get the device authorization byte array for the current drive.
    /// </summary>
    ///  <returns>The authorization id</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public byte[] GetAuthIdBytes()
    {
        string drvStr = BitConverter.ToHex(DriveId);
        NonceSequence sequence = Sequencer.GetSequence(drvStr);
        if (sequence == null)
        {
            byte[] authId = SalmonDriveGenerator.GenerateAuthId();
            CreateSequence(DriveId, authId);
        }
        sequence = Sequencer.GetSequence(drvStr);
        return BitConverter.ToBytes(sequence.AuthId);
    }


    /// <summary>
    ///  Get the default authorization config filename.
	/// </summary>
	///  <returns>The default authorization configuration file name</returns>
    public string GetDefaultAuthConfigFilename()
    {
        return SalmonDrive.AuthConfigFilename;
    }


    /// <summary>
    ///  Get the next nonce for the drive. This operation IS atomic as per transaction.
	/// </summary>
	///  <param name="salmonDrive">The drive</param>
    ///  <returns>The next nonce</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="SalmonRangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    public byte[] GetNextNonce(SalmonDrive salmonDrive)
    {
        return Sequencer.NextNonce(BitConverter.ToHex(salmonDrive.DriveId));
    }

    /// <summary>
    ///  Create a nonce sequence for the drive id and the authorization id provided. Should be called
    ///  once per driveId/authId combination.
	/// </summary>
	///  <param name="driveId">The driveId</param>
    ///  <param name="authId"> The authId</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    internal void CreateSequence(byte[] driveId, byte[] authId)
    {
        string drvStr = BitConverter.ToHex(driveId);
        string authStr = BitConverter.ToHex(authId);
        Sequencer.CreateSequence(drvStr, authStr);
    }

    /// <summary>
    ///  Initialize the nonce sequencer with the current drive nonce range. Should be called
    ///  once per driveId/authId combination.
	/// </summary>
	///  <param name="driveId">Drive ID.</param>
    ///  <param name="authId"> Authorization ID.</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    internal void InitSequence(byte[] driveId, byte[] authId)
    {
        byte[] startingNonce = SalmonDriveGenerator.GetStartingNonce();
        byte[] maxNonce = SalmonDriveGenerator.GetMaxNonce();
        string drvStr = BitConverter.ToHex(driveId);
        string authStr = BitConverter.ToHex(authId);
        Sequencer.InitSequence(drvStr, authStr, startingNonce, maxNonce);
    }

    /// <summary>
    ///  Revoke authorization for this device. This will effectively terminate write operations on the current disk
    ///  by the current device. Warning: If you need to authorize write operations to the device again you will need
    ///  to have another device to export an authorization config file and reimport it.
    /// <para>See: <see href="https://github.com/mku11/Salmon-AES-CTR#readme">README.md</see></para>
	/// </summary>
	///  <exception cref="Exception">Thrown if error during operation</exception>
    public void RevokeAuthorization()
    {
        byte[] driveId = DriveId;
        Sequencer.RevokeSequence(BitConverter.ToHex(driveId));
    }



    /// <summary>
    ///  Get the authorization ID for the current device.
	/// </summary>
	///  <returns>The authorization id</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="SalmonAuthException">Thrown when there is a failure during authorization</exception>
    public string GetAuthId()
    {
        return BitConverter.ToHex(GetAuthIdBytes());
    }

    /// <summary>
    ///  Create a configuration file for the drive.
	/// </summary>
	///  <param name="password">The new password to be saved in the configuration</param>
    ///                  This password will be used to derive the master key that will be used to
    ///                  encrypt the combined key (encryption key + hash key)
    private void CreateConfig(string password)
    {
        byte[] driveKey = Key.DriveKey;
        byte[] hashKey = Key.HashKey;

        IRealFile configFile = RealRoot.GetChild(ConfigFilename);

        if (driveKey == null && configFile != null && configFile.Exists)
            throw new SalmonAuthException("Not authorized");

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
            DriveId = SalmonDriveGenerator.GenerateDriveId();
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
        SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms,
                null, false, null, null);
        stream.Write(driveKey, 0, driveKey.Length);
        stream.Write(hashKey, 0, hashKey.Length);
        stream.Write(DriveId, 0, DriveId.Length);
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
            byte[] authId = SalmonDriveGenerator.GenerateAuthId();
            CreateSequence(DriveId, authId);
            InitSequence(DriveId, authId);
        }
        InitFS();
    }

    /// <summary>
    ///  Return the virtual root directory of the drive.
	/// </summary>
	///  <returns>The root directory</returns>
    ///  <exception cref="SalmonAuthException">Thrown when there is a failure during authorization</exception>
    override
    public SalmonFile Root
    {
        get
        {
            if (RealRoot == null || !RealRoot.Exists)
                return null;
            return virtualRoot;
        }
    }

    /// <summary>
    ///  Verify if the user password is correct otherwise it will throw a SalmonAuthException
	/// </summary>
	///  <param name="password">The password.</param>
    private void Unlock(string password)
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
            stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms,
                    null, false, null, null);

            byte[] driveKey = new byte[SalmonGenerator.KEY_LENGTH];
            stream.Read(driveKey, 0, driveKey.Length);

            byte[] hashKey = new byte[SalmonGenerator.HASH_KEY_LENGTH];
            stream.Read(hashKey, 0, hashKey.Length);

            byte[] driveId = new byte[SalmonDriveGenerator.DRIVE_ID_LENGTH];
            stream.Read(driveId, 0, driveId.Length);

            // to make sure we have the right key we get the hash portion
            // and try to verify the drive nonce
            VerifyHash(salmonConfig, encData, hashKey);

            // set the combined key (drive key + hash key) and the drive nonce
            SetKey(masterKey, driveKey, hashKey, iterations);
            this.DriveId = driveId;
            InitFS();
            OnUnlockSuccess();
        }
        catch (Exception ex)
        {
            OnUnlockError();
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
    ///  <param name="iterations">The iterations</param>
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
	///  <param name="salmonConfig">The drive configuration file</param>
    ///  <param name="data">The data</param>
    ///  <param name="hashKey">The hash key</param>
    private void VerifyHash(SalmonDriveConfig salmonConfig, byte[] data, byte[] hashKey)
    {
        byte[] hashSignature = salmonConfig.HashSignature;
        byte[] hash = SalmonIntegrity.CalculateHash(hashProvider, data, 0, data.Length, hashKey, null);
        for (int i = 0; i < hashKey.Length; i++)
            if (hashSignature[i] != hash[i])
                throw new SalmonAuthException("Wrong password");
    }

    /// <summary>
    ///  Get the next nonce from the sequencer. This advanced the sequencer so unique nonce are used.
	/// </summary>
	///  <returns>The next nonce</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    internal byte[] GetNextNonce()
    {
        return GetNextNonce(this);
    }

    /// <summary>
    ///  Get the byte contents of a file from the real filesystem.
	/// </summary>
	///  <param name="file">The file</param>
    ///  <param name="bufferSize">The buffer to be used when reading</param>
    public byte[] GetBytesFromRealFile(IRealFile file, int bufferSize)
    {
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
            IRealFile exportDir = RealRoot.GetChild(ExportDirectoryName);
            if (exportDir == null || !exportDir.Exists)
                exportDir = RealRoot.CreateDirectory(ExportDirectoryName);
            return exportDir;
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
        byte[] bytes = GetBytesFromRealFile(configFile, 0);
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
    ///  Close the drive and close associated resources.
    /// </summary>
    public void Close()
    {
        RealRoot = null;
        virtualRoot = null;
        DriveId = null;
        if (Key != null)
            Key.Clear();
        Key = null;
    }
}