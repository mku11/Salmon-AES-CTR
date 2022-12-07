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
using Salmon.Streams;
using System;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using static Salmon.Streams.SalmonStream;

namespace Salmon.FS
{
    /// <summary>
    /// Class provides an abstract virtual drive that can be extended for other filesystems
    /// that provide an implementation of an IRealFile
    /// </summary>
    public abstract class SalmonDrive
    {
        protected static readonly string CONFIG_FILE = "cf.dat";
        protected static readonly string VALIDATION_FILE = "vl.dat";
        protected static readonly string VIRTUAL_DRIVE_DIR = "fs";
        protected static readonly string THUMBNAIL_DIR = "ic";
        protected static readonly string SHARE_DIR = "share";
        protected static readonly string EXPORT_DIR = "export";

        private static readonly int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;
        private static readonly int BUFFER_SIZE = 32768;

        private SalmonKey encryptionKey = null;
        private bool enableIntegrityCheck = true;
        private static int defaultFileChunkSize = DEFAULT_FILE_CHUNK_SIZE;

        private IRealFile realRoot = null;
        private SalmonFile virtualRoot = null;

        /// <summary>
        /// Return the default file chunk size
        /// </summary>
        /// <returns></returns>
        public int GetDefaultFileChunkSize()
        {
            return defaultFileChunkSize;
        }

        /// <summary>
        /// Set the default file chunk size to be used with HMAC integrity
        /// </summary>
        /// <param name="fileChunkSize"></param>
        public void SetDefaultFileChunkSize(int fileChunkSize)
        {
            defaultFileChunkSize = fileChunkSize;
        }

        protected abstract IRealFile GetFile(string filepath, bool root);

        /// <summary>
        // create a virtual drive at the directory path provided
        /// </summary>
        /// <param name="realRootPath">The path of the real directory</param>
        public SalmonDrive(string realRootPath)
        {
            Clear();
            if (realRootPath == null)
                return;
            realRoot = GetFile(realRootPath, true);
            IRealFile virtualRootRealFile = realRoot.GetChild(VIRTUAL_DRIVE_DIR);
            if (virtualRootRealFile == null || !virtualRootRealFile.Exists())
            {
                try
                {
                    virtualRootRealFile = realRoot.CreateDirectory(VIRTUAL_DRIVE_DIR);
                }
                catch (Exception ex)
                {
                    Console.WriteLine(ex);
                }

            }
            virtualRoot = new SalmonFile(virtualRootRealFile, this);
            encryptionKey = new SalmonKey();
        }

        private void Clear()
        {
            if (encryptionKey != null)
                encryptionKey.Clear();
            encryptionKey = null;
            enableIntegrityCheck = false;
            realRoot = null;
            virtualRoot = null;
        }

        /// <summary>
        /// Returns true if the file is a system file
        /// </summary>
        /// <param name="salmonFile">Virtual File to be checked</param>
        /// <returns></returns>
        internal bool IsSystemFile(SalmonFile salmonFile)
        {
            IRealFile rnFile = realRoot.GetChild(VALIDATION_FILE);
            return salmonFile.GetRealPath().Equals(rnFile.GetPath());
        }

        /// <summary>
        /// Return true if integrity check is enabled
        /// </summary>
        /// <returns></returns>
        public bool GetEnableIntegrityCheck()
        {
            return enableIntegrityCheck;
        }

        /// <summary>
        /// Return the encryption key that is used for encryption / decryption
        /// </summary>
        /// <returns></returns>
        public SalmonKey GetKey()
        {
            return encryptionKey;
        }

        /// <summary>
        /// Set to true to enable the integrity check
        /// </summary>
        /// <param name="value"></param>
        public void SetEnableIntegrityCheck(bool value)
        {
            enableIntegrityCheck = value;
        }

        /// <summary>
        /// Set the user password 
        /// </summary>
        /// <param name="pass"></param>
        public void SetPassword(string pass)
        {
            SalmonKey key = GetKey();
            CreateConfigFile(pass, key.GetDriveKey(), key.GetHMACKey());
        }

        /// <summary>
        /// Create a configuration file for the vault
        /// </summary>
        /// <param name="password">The new password to be save in the configuration
        /// This password will be used to derive the master key that will be used to
        /// encrypt the combined key (encryption key + HMAC key)
        /// </param>
        /// <param name="driveKey">The current Drive key</param>
        /// <param name="hmacKey">The current HMAC key</param>
        //TODO: partial refactor to SalmonDriveConfig
        private void CreateConfigFile(string password, byte[] driveKey, byte [] hmacKey)
        {
            IRealFile configFile = realRoot.GetChild(CONFIG_FILE);

            // if it's an exsting config that we need to update with
            // the new password then we prefer to be authenticate
            // TODO: we should probably call Authenticate() rather
            // than assume the key != null but the user can anyway manually delete the config file
            // so it doesn't matter
            if (driveKey == null && configFile != null && configFile.Exists())
                throw new SalmonAuthException("Not authenticated");

            // delete the old config file and create a new one
            if (configFile != null && configFile.Exists())
                configFile.Delete();
            configFile = realRoot.CreateFile(CONFIG_FILE);

            byte[] magicBytes = SalmonGenerator.GetMagicBytes();

            byte version = SalmonGenerator.GetVersion();

            // if this is a new config file
            // derive a 512 bit key that will be split to:
            // a file encryption key (encryption key)
            // an HMAC key
            if (driveKey == null)
            {
                driveKey = new byte[SalmonGenerator.GetKeyLength()];
                hmacKey = new byte[SalmonGenerator.GetHMACKeyLength()];
                byte [] combinedKey = SalmonGenerator.GenerateCombinedKey();
                Array.Copy(combinedKey, 0, driveKey, 0, SalmonGenerator.GetKeyLength());
                Array.Copy(combinedKey, SalmonGenerator.GetKeyLength(), hmacKey, 0, SalmonGenerator.GetHMACKeyLength());
            }

            // Get the salt that we will use to encrypt the combined key (encryption key + HMAC key)
            byte[] salt = SalmonGenerator.GenerateSalt();

            int iterations = SalmonGenerator.GetIterations();

            // generate a 128 bit IV that will be used with the master key to encrypt the combined 64 bit key (encryption key + HMAC key)
            byte[] masterKeyIv = SalmonGenerator.GenerateMasterKeyIV();

            // create a key that will encrypt both the (encryption key and the HMAC key)
            byte[] masterKey = SalmonGenerator.GetMasterKey(password, salt, iterations);

            // initialize a once that will serve as an incremental sequence for each of the files
            byte[] vaultNonce = new byte[SalmonGenerator.GetNonceLength()];

            // encrypt the combined key (fskey + hmacKey) using the masterKey and the masterKeyIv
            MemoryStream ms = new MemoryStream();
            SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms);
            stream.Write(driveKey, 0, driveKey.Length);
            stream.Write(hmacKey, 0, hmacKey.Length);
            stream.Write(vaultNonce, 0, vaultNonce.Length);
            stream.Flush();
            stream.Close();
            byte[] encryptedCombinedKeyAndNonce = ms.ToArray();

            byte[] encVaultNonce = new byte[SalmonGenerator.GetNonceLength()];
            Array.Copy(encryptedCombinedKeyAndNonce, SalmonGenerator.GetKeyLength() + SalmonGenerator.GetHMACKeyLength(),
                encVaultNonce, 0, SalmonGenerator.GetNonceLength());

            // get the hmac hash only for the vault nonce
            byte[] hmacSignature = SalmonIntegrity.CalculateHMAC(encVaultNonce, 0, encVaultNonce.Length, hmacKey);

            SalmonDriveConfig.WriteDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                encryptedCombinedKeyAndNonce, hmacSignature);

            encryptionKey.SetDriveKey(driveKey);
            encryptionKey.SetHmacKey(hmacKey);
            encryptionKey.SetVaultNonce(vaultNonce);
            encryptionKey.SetMasterKey(masterKey);
            encryptionKey.SetSalt(salt);
            encryptionKey.SetIterations(iterations);
        }


        /// <summary>
        /// Return the root directory of the virtual drive
        /// </summary>
        /// <returns></returns>
        public SalmonFile GetVirtualRoot()
        {
            if (realRoot == null || !realRoot.Exists())
                return null;
            if (!IsAuthenticated())
                throw new SalmonAuthException("Not authenticated");
            return virtualRoot;
        }

        /// <summary>
        /// Function Verifies if the user password is correct otherwise it 
        /// throws a SalmonAuthException
        /// </summary>
        /// <param name="password"></param>
        public void Authenticate(string password)
        {
            SalmonStream stream = null;
            try
            {
                if (password == null)
                {
                    if (encryptionKey != null)
                    {
                        encryptionKey.SetMasterKey(null);
                        encryptionKey.SetDriveKey(null);
                        encryptionKey.SetHmacKey(null);
                        encryptionKey.SetVaultNonce(null);
                        encryptionKey.SetSalt(null);
                        encryptionKey.SetIterations(0);
                    }
                    return;
                }

                IRealFile realConfigFile = GetConfigFile();
                SalmonDriveConfig salmonConfig = GetSalmonConfig(realConfigFile);

                int iterations = salmonConfig.GetIterations();

                byte [] salt = salmonConfig.GetSalt();

                // derive the master key from the text password
                byte[] masterKey = SalmonGenerator.GetMasterKey(password, salt, iterations);
                
                // get the master Key Iv
                byte[] masterKeyIv = salmonConfig.GetIv();

                // get the encrypted combined key and vault nonce
                byte[] encryptedCombinedKeysAndNonce = salmonConfig.GetEncryptedKeysAndNonce();
                byte[] encVaultNonce = new byte[SalmonGenerator.GetNonceLength()];
                Array.Copy(encryptedCombinedKeysAndNonce, SalmonGenerator.GetKeyLength() + SalmonGenerator.GetHMACKeyLength(),
                    encVaultNonce, 0, encVaultNonce.Length);

                // decrypt the combined key (encryption key + HMAC key) using the master key
                MemoryStream ms = new MemoryStream(encryptedCombinedKeysAndNonce);
                stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Decrypt, ms);
                
                byte[] driveKey = new byte[SalmonGenerator.GetKeyLength()];
                int bytesRead = stream.Read(driveKey, 0, driveKey.Length);

                byte[] hmacKey = new byte[SalmonGenerator.GetHMACKeyLength()];
                bytesRead = stream.Read(hmacKey, 0, hmacKey.Length);
                
                byte[] vaultNonce = new byte[SalmonGenerator.GetNonceLength()];
                bytesRead = stream.Read(vaultNonce, 0, vaultNonce.Length);

                // to make sure we have the right key we get the hmac portion 
                // and try to verify the vault nonce
                VerifyHmac(salmonConfig, encVaultNonce, hmacKey);

                // set the combined key (encryption key + HMAC key) and the vault nonce
                encryptionKey.SetMasterKey(masterKey);
                encryptionKey.SetDriveKey(driveKey);
                encryptionKey.SetHmacKey(hmacKey);
                encryptionKey.SetVaultNonce(vaultNonce);
                encryptionKey.SetSalt(salt);
                encryptionKey.SetIterations(iterations);

                OnAuthenticationSuccess();
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                encryptionKey.SetMasterKey(null);
                encryptionKey.SetDriveKey(null);
                encryptionKey.SetHmacKey(null);
                encryptionKey.SetVaultNonce(null);
                encryptionKey.SetSalt(null);
                encryptionKey.SetIterations(0);
                OnAuthenticationError();
                throw new SalmonAuthException("Could not authenticate, try again", ex);
            } finally
            {
                if (stream != null)
                    stream.Close();
            }
        }

        /// <summary>
        /// Verify that the HMAC is correct for the current vaultNonce
        /// </summary>
        /// <param name="salmonConfig"></param>
        /// <param name="encVaultNonce"></param>
        /// <param name="hmacKey"></param>
        private void VerifyHmac(SalmonDriveConfig salmonConfig, byte[] encVaultNonce, byte[] hmacKey)
        {
            byte[] hmacSignature = salmonConfig.GetHMACsignature();
            byte[] hmac = SalmonIntegrity.CalculateHMAC(encVaultNonce, 0, encVaultNonce.Length, hmacKey);
            for (int i = 0; i < hmacKey.Length; i++)
                if (hmacSignature[i] != hmac[i])
                    throw new Exception("Could not authenticate");
        }

        internal byte[] GetNextNonce()
        {
            lock (this)
            {
                if (!IsAuthenticated())
                    throw new SalmonAuthException("Not authenticated");

                int iterations = GetKey().GetIterations();

                // get the salt
                byte[] salt = GetKey().GetSalt();

                // get the current master key
                byte[] masterKey = GetKey().GetMasterKey();

                // generate a new iv so we don't get the same
                // the master iv is 128 bit so the space is quite large so collisions are very non-probable
                byte[] masterKeyIv = SalmonGenerator.GenerateMasterKeyIV();

                byte[] driveKey = GetKey().GetDriveKey();

                byte[] hmacKey = GetKey().GetHMACKey();

                byte[] vaultNonce = GetKey().GetVaultNonce();

                //We get the next nonce by incrementing the lowest 4 bytes
                long newLowNonce = 0;
                //we check not to wrap around so we don't reuse nonces
                long currNonceInt = BitConverter.ToInt64(vaultNonce, 0, SalmonGenerator.GetNonceLength());
                //TODO: use Math.addExact is available for SDK 24+ so for now we provide as much
                // backwards compatibility as possible with a simple check instead
                if (currNonceInt < 0 || currNonceInt >= long.MaxValue)
                    throw new Exception("Cannot import file, vault exceeded maximum nonces");
                newLowNonce = currNonceInt + 1;
                byte[] newLowVaultNonce = BitConverter.GetBytes(newLowNonce, SalmonGenerator.GetNonceLength());
                Array.Copy(newLowVaultNonce, 0, vaultNonce, 0, newLowVaultNonce.Length);
                GetKey().SetVaultNonce(vaultNonce);


                // encrypt the combined key (fskey + hmacKey) using the masterKey and the masterKeyIv
                MemoryStream encMs = new MemoryStream();
                SalmonStream encStream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, encMs);
                encStream.Write(driveKey, 0, driveKey.Length);
                encStream.Write(hmacKey, 0, hmacKey.Length);
                encStream.Write(vaultNonce, 0, vaultNonce.Length);
                encStream.Flush();
                encStream.Close();
                byte[] encryptedCombinedKeyAndNonce = encMs.ToArray();

                byte[] encVaultNonce = new byte[SalmonGenerator.GetNonceLength()];
                Array.Copy(encryptedCombinedKeyAndNonce, SalmonGenerator.GetKeyLength() + SalmonGenerator.GetHMACKeyLength(),
                    encVaultNonce, 0, SalmonGenerator.GetNonceLength());

                // get the hmac hash only for the vault nonce
                byte[] hmacSignature = SalmonIntegrity.CalculateHMAC(encVaultNonce, 0, encVaultNonce.Length, GetKey().GetHMACKey());

                // rewrite the config file
                IRealFile realConfigFile = GetConfigFile();
                SalmonDriveConfig.WriteDriveConfig(realConfigFile, SalmonGenerator.GetMagicBytes(), SalmonGenerator.GetVersion(),
                    salt, iterations, masterKeyIv,
                    encryptedCombinedKeyAndNonce, hmacSignature);

                return vaultNonce;
            }
        }

        /// <summary>
        /// Method is called when the user is authenticated
        /// </summary>
        protected virtual void OnAuthenticationSuccess()
        {
            
        }

        /// <summary>
        /// Method is called when the user authentication has failed
        /// </summary>
        protected virtual void OnAuthenticationError()
        {

        }

        /// <summary>
        /// Returns true if password authentication has succeeded
        /// </summary>
        /// <returns></returns>
        public bool IsAuthenticated()
        {
            SalmonKey key = GetKey();
            if (key == null)
                return false;
            byte[] encKey = key.GetDriveKey();
            if (encKey == null)
                return false;
            return true;
        }

        /// <summary>
        /// Return the byte contents of a real file
        /// </summary>
        /// <param name="sourcePath">The path of the file</param>
        /// <param name="bufferSize">The buffer to be used when reading</param>
        /// <returns></returns>
        public byte[] GetBytesFromRealFile(string sourcePath, int bufferSize = 0)
        {
            IRealFile file = GetFile(sourcePath, false);
            Stream stream = file.GetInputStream(bufferSize);
            BufferedStream bufferedStream = new BufferedStream(stream, BUFFER_SIZE);
            MemoryStream ms = new MemoryStream();
            bufferedStream.CopyTo(ms);
            ms.Flush();
            ms.Position = 0;
            byte[] byteContents = ms.ToArray();
            ms.Close();
            bufferedStream.Close();
            return byteContents;
        }

        /// <summary>
        /// Return the current config file
        /// </summary>
        /// <returns></returns>
        private IRealFile GetConfigFile()
        {
            if (realRoot == null || !realRoot.Exists())
                return null;
            IRealFile file = realRoot.GetChild(CONFIG_FILE);
            return file;
        }

        /// <summary>
        /// Returns the real file from the external thumbnail directory
        /// You can use this directory to store encrypted thumbnails if you want
        /// </summary>
        /// <returns></returns>
        public IRealFile GetThumbnailsDir()
        {
            IRealFile virtualThumbnailsRealDir = realRoot.GetChild(THUMBNAIL_DIR);
            if (virtualThumbnailsRealDir == null)
                virtualThumbnailsRealDir = realRoot.CreateDirectory(THUMBNAIL_DIR);
            return virtualThumbnailsRealDir;
        }

        /// <summary>
        /// Return the external export dir that all exported file will be stored
        /// </summary>
        /// <returns></returns>
        public IRealFile GetExportDir()
        {
            IRealFile virtualThumbnailsRealDir = realRoot.GetChild(EXPORT_DIR);
            if (virtualThumbnailsRealDir == null)
                virtualThumbnailsRealDir = realRoot.CreateDirectory(EXPORT_DIR);
            return virtualThumbnailsRealDir;
        }

        /// <summary>
        /// Return the configuration properties for this drive
        /// </summary>
        /// <param name="configFile">The drive configuration file</param>
        /// <returns></returns>
        private SalmonDriveConfig GetSalmonConfig(IRealFile configFile)
        {
            byte[] bytes = GetBytesFromRealFile(configFile.GetPath());
            SalmonDriveConfig driveConfig = new SalmonDriveConfig(bytes);
            return driveConfig;
        }

        /// <summary>
        /// Return the extension of a filename
        /// </summary>
        /// <param name="fileName"></param>
        /// <returns></returns>
        public string GetExtensionFromFileName(string fileName)
        {
            if (fileName == null)
                return "";

            int index = fileName.LastIndexOf(".");
            if (index >= 0)
            {
                return fileName.Substring(index + 1);
            }
            else
                return "";
        }

        /// <summary>
        /// Return a filename without extension
        /// </summary>
        /// <param name="fileName">Filename</param>
        /// <returns></returns>
        public string GetFileNameWithoutExtension(String fileName)
        {
            if (fileName == null)
                return "";
            int index = fileName.LastIndexOf(".");
            if (index >= 0)
            {
                return fileName.Substring(0, index);
            }
            else
                return "";
        }

        /// <summary>
        /// Return true if the drive already has a configuration file
        /// </summary>
        /// <returns></returns>
        public bool HasConfig()
        {
            IRealFile configFile = GetConfigFile();
            if (configFile == null || !configFile.Exists())
                return false;
            try
            {
                SalmonDriveConfig salmonConfig = GetSalmonConfig(configFile);
            }
            catch (Exception)
            {
                return false;
            }
            return true;
        }

        public void SaveCache()
        {
            //TODO: 
        }
    }
}