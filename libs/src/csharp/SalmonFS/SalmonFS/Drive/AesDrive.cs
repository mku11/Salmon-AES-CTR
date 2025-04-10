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

using Mku.Streams;
using Mku.Salmon.Integrity;
using Mku.Salmon.Streams;
using Mku.Salmon.Password;
using System.Runtime.CompilerServices;
using System;
using System.IO;
using Mku.Salmon.Sequence;
using BitConverter = Mku.Convert.BitConverter;
using Mku.FS.Drive;
using Mku.FS.File;
using Mku.SalmonFS.File;
using Mku.Salmon;
using Mku.SalmonFS.Auth;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.SalmonFS.Drive;

/// <summary>
///  Class provides an abstract virtual drive that can be extended for use with
///  any filesystem ie disk, net, cloud, etc.
///  Drive implementations needs to be realized together with <see cref="IFile"/> }.
/// </summary>
public abstract class AesDrive : VirtualDrive
{

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
    public int DefaultFileChunkSize { get; set; } = Integrity.DEFAULT_CHUNK_SIZE;

    /// <summary>
    /// The current key
    /// </summary>
    public DriveKey Key { get; private set; } = null;

    /// <summary>
    /// The current drive ID
    /// </summary>
    public byte[] DriveId { get; private set; }

    /// <summary>
    /// The real root location of the vault
    /// </summary>
    public IFile RealRoot { get; private set; } = null;
    private AesFile virtualRoot = null;
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
    protected virtual void Initialize(IFile realRoot, bool createIfNotExists = false)
    {
        Close();
        if (realRoot == null)
            return;
        this.RealRoot = realRoot;
        if (!createIfNotExists && !HasConfig() && RealRoot.Parent != null && RealRoot.Parent.Exists)
        {
            // try the parent if this is the filesystem folder 
            IFile originalRealRoot = RealRoot;
            RealRoot = RealRoot.Parent;
            if (!HasConfig())
            {
                // revert to original
                RealRoot = originalRealRoot;
            }
        }

        IFile virtualRootRealFile = RealRoot.GetChild(VirtualDriveDirectoryName);
        if (createIfNotExists && (virtualRootRealFile == null || !virtualRootRealFile.Exists))
        {
            virtualRootRealFile = RealRoot.CreateDirectory(VirtualDriveDirectoryName);
        }
        virtualRoot = GetVirtualFile(virtualRootRealFile);
        RegisterOnProcessClose();
        Key = new DriveKey();
    }

    private AesFile GetVirtualFile(IFile file)
    {
        return new AesFile(file, this);
    }

    /// <summary>
    ///  Clear sensitive information when app is close.
    /// </summary>
    protected void RegisterOnProcessClose()
    {
        //TODO
    }

    /// <summary>
    ///  Change the user password.
	/// </summary>
	///  <param name="pass">The new password.</param>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    ///  <exception cref="AuthException">Thrown when there is a failure during authorization</exception>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
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
        IFile virtualRootRealFile = RealRoot.GetChild(VirtualDriveDirectoryName);
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
        virtualRoot = GetVirtualFile(virtualRootRealFile);
    }

    /// <summary>
    ///  Set the drive location to an external directory.
    ///  This requires you previously use SetDriveClass() to provide a class for the drive
    /// </summary>
    ///  <param name="dir">The directory that will be used for storing the contents of the drive</param>
    ///  <param name="driveClassType">The class type of the drive (ie: typeof(Drive))</param>
    ///  <param name="password">password</param>
    ///  <param name="sequencer">The sequencer</param>
    public static AesDrive OpenDrive(IFile dir, Type driveClassType,
                                        string password, INonceSequencer sequencer = null)
    {
        AesDrive drive = CreateDriveInstance(dir, false,
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
    ///  <param name="driveClassType">The drive class type of this drive (ie typeof(Drive)) </param>
    ///  <param name="password">Master password to encrypt the drive configuration.</param>
    ///  <param name="sequencer">The sequencer</param>
    ///  <returns>The newly created drive.</returns>
    ///  <exception cref="IntegrityException">Thrown when data are corrupt or tampered with.</exception>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    public static AesDrive CreateDrive(IFile dir, Type driveClassType,
        string password, INonceSequencer sequencer)
    {
        AesDrive drive = CreateDriveInstance(dir, true,
            driveClassType, sequencer);
        if (drive.HasConfig())
            throw new SecurityException("Drive already exists");
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
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    private static AesDrive CreateDriveInstance(IFile dir, bool createIfNotExists,
        Type driveClassType, INonceSequencer sequencer)
    {

        AesDrive drive;
        try
        {
            drive = Activator.CreateInstance(driveClassType, true) as AesDrive;
            drive.Initialize(dir, createIfNotExists);
            drive.Sequencer = sequencer;
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            throw new SecurityException("Could not initialize the drive: " + e.Message, e);
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
            byte[] authId = DriveGenerator.GenerateAuthId();
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
        return AesDrive.AuthConfigFilename;
    }


    /// <summary>
    ///  Get the next nonce for the drive. This operation IS atomic as per transaction.
	/// </summary>
	///  <param name="salmonDrive">The drive</param>
    ///  <returns>The next nonce</returns>
    ///  <exception cref="SequenceException">Thrown when there is a failure in the nonce sequencer.</exception>
    ///  <exception cref="RangeExceededException">Thrown when maximum nonce range is exceeded.</exception>
    public byte[] GetNextNonce(AesDrive salmonDrive)
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
        byte[] startingNonce = DriveGenerator.GetStartingNonce();
        byte[] maxNonce = DriveGenerator.GetMaxNonce();
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
    ///  <exception cref="AuthException">Thrown when there is a failure during authorization</exception>
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
        byte[] driveKey = Key.DriveEncKey;
        byte[] hashKey = Key.HashKey;

        IFile configFile = GetConfigFile(RealRoot);

        if (driveKey == null && configFile != null && configFile.Exists)
            throw new AuthException("Not authorized");

        // delete the old config file and create a new one
        if (configFile != null && configFile.Exists)
            configFile.Delete();
        configFile = CreateConfigFile(RealRoot);

        byte[] magicBytes = Generator.GetMagicBytes();

        byte version = Generator.VERSION;

        // if this is a new config file derive a 512-bit key that will be split to:
        // a) drive encryption key (for encrypting filenames and files)
        // b) hash key for file integrity
        bool newDrive = false;
        if (driveKey == null)
        {
            newDrive = true;
            driveKey = new byte[Generator.KEY_LENGTH];
            hashKey = new byte[Generator.HASH_KEY_LENGTH];
            byte[] combKey = DriveGenerator.GenerateCombinedKey();
            Array.Copy(combKey, 0, driveKey, 0, Generator.KEY_LENGTH);
            Array.Copy(combKey, Generator.KEY_LENGTH, hashKey, 0, Generator.HASH_KEY_LENGTH);
            DriveId = DriveGenerator.GenerateDriveId();
        }

        // Get the salt that we will use to encrypt the combined key (drive key + hash key)
        byte[] salt = DriveGenerator.GenerateSalt();

        int iterations = DriveGenerator.GetIterations();

        // generate a 128 bit IV that will be used with the master key to encrypt the combined 64-bit key (drive key + hash key)
        byte[] masterKeyIv = DriveGenerator.GenerateMasterKeyIV();

        // create a key that will encrypt both the (drive key and the hash key)
        byte[] masterKey = Password.GetMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

        // encrypt the combined key (drive key + hash key) using the masterKey and the masterKeyIv
        MemoryStream ms = new MemoryStream();
        AesStream stream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms, EncryptionFormat.Generic);
        stream.Write(driveKey, 0, driveKey.Length);
        stream.Write(hashKey, 0, hashKey.Length);
        stream.Write(DriveId, 0, DriveId.Length);
        stream.Flush();
        stream.Close();
        byte[] encData = ms.ToArray();

        // generate the hash signature
        byte[] hashSignature = Integrity.CalculateHash(hashProvider, encData, 0, encData.Length, hashKey, null);

        DriveConfig.WriteDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encData, hashSignature);
        SetKey(masterKey, driveKey, hashKey, iterations);

        if (newDrive)
        {
            // create a full sequence for nonces
            byte[] authId = DriveGenerator.GenerateAuthId();
            CreateSequence(DriveId, authId);
            InitSequence(DriveId, authId);
        }
        InitFS();
    }

    /// <summary>
    ///  Return the virtual root directory of the drive.
	/// </summary>
	///  <returns>The root directory</returns>
    ///  <exception cref="AuthException">Thrown when there is a failure during authorization</exception>
    override
    public AesFile Root
    {
        get
        {
            if (RealRoot == null || !RealRoot.Exists)
                return null;
            return virtualRoot;
        }
    }

    /// <summary>
    ///  Verify if the user password is correct otherwise it will throw a AuthException
	/// </summary>
	///  <param name="password">The password.</param>
    private void Unlock(string password)
    {
        AesStream stream = null;
        try
        {
            if (password == null)
            {
                throw new SecurityException("Password is missing");
            }
            DriveConfig salmonConfig = GetDriveConfig();
            int iterations = salmonConfig.GetIterations();
            byte[] salt = salmonConfig.Salt;

            // derive the master key from the text password
            byte[] masterKey = Password.GetMasterKey(password, salt, iterations, DriveGenerator.MASTER_KEY_LENGTH);

            // get the master Key Iv
            byte[] masterKeyIv = salmonConfig.Iv;

            // get the encrypted combined key and drive id
            byte[] encData = salmonConfig.EncryptedData;

            // decrypt the combined key (drive key + hash key) using the master key
            MemoryStream ms = new MemoryStream(encData);
            stream = new AesStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms, EncryptionFormat.Generic);

            byte[] driveKey = new byte[Generator.KEY_LENGTH];
            stream.Read(driveKey, 0, driveKey.Length);

            byte[] hashKey = new byte[Generator.HASH_KEY_LENGTH];
            stream.Read(hashKey, 0, hashKey.Length);

            byte[] driveId = new byte[DriveGenerator.DRIVE_ID_LENGTH];
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
            throw;
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
        Key.DriveEncKey = driveKey;
        Key.HashKey = hashKey;
        Key.Iterations = iterations;
    }

    /// <summary>
    ///  Verify that the hash signature is correct
	/// </summary>
	///  <param name="salmonConfig">The drive configuration file</param>
    ///  <param name="data">The data</param>
    ///  <param name="hashKey">The hash key</param>
    private void VerifyHash(DriveConfig salmonConfig, byte[] data, byte[] hashKey)
    {
        byte[] hashSignature = salmonConfig.HashSignature;
        byte[] hash = Integrity.CalculateHash(hashProvider, data, 0, data.Length, hashKey, null);
        for (int i = 0; i < hashKey.Length; i++)
            if (hashSignature[i] != hash[i])
                throw new AuthException("Wrong password");
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
    public byte[] GetBytesFromRealFile(IFile file, int bufferSize)
    {
        RandomAccessStream stream = file.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms, bufferSize);
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
    private IFile GetDriveConfigFile()
    {
        if (RealRoot == null || !RealRoot.Exists)
            return null;
        IFile file = RealRoot.GetChild(ConfigFilename);
        return file;
    }

    /// <summary>
    ///  Return the default external export dir that all file can be exported to.
	/// </summary>
	///  <returns>The file on the real filesystem.</returns>
    public IFile ExportDir
    {
        get
        {
            IFile exportDir = RealRoot.GetChild(ExportDirectoryName);
            if (exportDir == null || !exportDir.Exists)
                exportDir = RealRoot.CreateDirectory(ExportDirectoryName);
            return exportDir;
        }
    }

    /// <summary>
    ///  Return the configuration properties of this drive.
    /// </summary>
    protected DriveConfig GetDriveConfig()
    {
        IFile configFile = GetDriveConfigFile();
        if (configFile == null || !configFile.Exists)
            return null;
        byte[] bytes = GetBytesFromRealFile(configFile, 0);
        DriveConfig driveConfig = new DriveConfig(bytes);
        return driveConfig;
    }

    /// <summary>
    ///  Return true if the drive is already created and has a configuration file.
    /// </summary>
    public bool HasConfig()
    {
        DriveConfig salmonConfig;
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
    /// Close the drive and close associated resources.
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

    /// <summary>
    /// Create the config file for this drive. By default the config file is placed in the real root of the vault.
    /// You can override this with your own location, make sure you also override getConfigFile(). 
    /// </summary>
    /// <param name="realRoot">The real root directory of the vault</param>
    /// <returns>The config file that was created</returns>
	virtual
    public IFile CreateConfigFile(IFile realRoot)
    {
        IFile configFile = realRoot.CreateFile(AesDrive.ConfigFilename);
        return configFile;
    }

    /// <summary>
    /// Get the config file for this drive. By default the config file is placed in the real root of the vault.
    /// You can override this with your own location.
    /// </summary>
    /// <param name="realRoot">The real root directory of the vault</param>
    /// <returns>The config file that will be used for this drive.</returns>
    virtual
    public IFile GetConfigFile(IFile realRoot)
    {
        IFile configFile = realRoot.GetChild(AesDrive.ConfigFilename);
        return configFile;
    }
}