﻿/*
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
        public static readonly string CONFIG_FILE = "vault.slmn";
        public static readonly string AUTH_CONFIG_FILENAME = "auth.slma";
        public static readonly string VIRTUAL_DRIVE_DIR = "fs";
        public static readonly string THUMBNAIL_DIR = ".thumbnail";
        public static readonly string SHARE_DIR = "share";
        public static readonly string EXPORT_DIR = "export";

        private static readonly int DEFAULT_FILE_CHUNK_SIZE = 256 * 1024;
        private static readonly int BUFFER_SIZE = 32768;
        private byte[] driveID;
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

        public abstract IRealFile GetFile(string filepath, bool root);

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
            lock (this)
            {
                SalmonKey key = GetKey();
                CreateConfig(pass, key.GetDriveKey(), key.GetHMACKey());
            }
        }

        private void InitFS()
        {
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
        private void CreateConfig(string password, byte[] driveKey, byte[] hmacKey)
        {
            IRealFile configFile = realRoot.GetChild(CONFIG_FILE);

            // if it's an exsting config that we need to update with
            // the new password then we prefer to be authenticate
            // TODO: we should probably call Authenticate() rather than assume
            //  that the key != null. Though the user can anyway manually delete the config file
            //  so it doesn't matter
            if (driveKey == null && configFile != null && configFile.Exists())
                throw new SalmonAuthException("Not authenticated");

            // delete the old config file and create a new one
            if (configFile != null && configFile.Exists())
                configFile.Delete();
            configFile = realRoot.CreateFile(CONFIG_FILE);

            byte[] magicBytes = SalmonGenerator.GetMagicBytes();

            byte version = SalmonGenerator.GetVersion();

            // if this is a new config file derive a 512 bit key that will be split to:
            // a) drive encryption key (for encrypting filenames and files)
            // b) HMAC key for file integrity
            bool newDrive = false;
            if (driveKey == null)
            {
                newDrive = true;
                driveKey = new byte[SalmonGenerator.KEY_LENGTH];
                hmacKey = new byte[SalmonGenerator.HMAC_KEY_LENGTH];
                byte[] combKey = SalmonGenerator.GenerateCombinedKey();
                Array.Copy(combKey, 0, driveKey, 0, SalmonGenerator.KEY_LENGTH);
                Array.Copy(combKey, SalmonGenerator.KEY_LENGTH, hmacKey, 0, SalmonGenerator.HMAC_KEY_LENGTH);
                driveID = SalmonGenerator.GenerateDriveID();
            }

            // Get the salt that we will use to encrypt the combined key (encryption key + HMAC key)
            byte[] salt = SalmonGenerator.GenerateSalt();

            int iterations = SalmonGenerator.GetIterations();

            // generate a 128 bit IV that will be used with the master key to encrypt the combined 64 bit key (encryption key + HMAC key)
            byte[] masterKeyIv = SalmonGenerator.GenerateMasterKeyIV();

            // create a key that will encrypt both the (encryption key and the HMAC key)
            byte[] masterKey = SalmonGenerator.GetMasterKey(password, salt, iterations);

            // encrypt the combined key (fskey + hmacKey) using the masterKey and the masterKeyIv
            MemoryStream ms = new MemoryStream();
            SalmonStream stream = new SalmonStream(masterKey, masterKeyIv, EncryptionMode.Encrypt, ms);
            stream.Write(driveKey, 0, driveKey.Length);
            stream.Write(hmacKey, 0, hmacKey.Length);
            stream.Write(driveID, 0, driveID.Length);
            stream.Flush();
            stream.Close();
            byte[] encData = ms.ToArray();

            // generate the hmac signature
            byte[] hmacSignature = SalmonIntegrity.CalculateHMAC(encData, 0, encData.Length, hmacKey, null);

            SalmonDriveConfig.WriteDriveConfig(configFile, magicBytes, version, salt, iterations, masterKeyIv,
                    encData, hmacSignature);

            SetKey(masterKey, driveKey, hmacKey, salt, iterations);

            if (newDrive)
            {
                // create a full sequence for nonces
                byte[] authID = SalmonGenerator.GenerateAuthId();
                SalmonDriveManager.CreateSequence(driveID, authID);
                SalmonDriveManager.InitSequence(driveID, authID);
            }
            InitFS();
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
        /// Verify if the user password is correct otherwise it throws a SalmonAuthException
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
                        ClearKey();
                        this.driveID = null;
                    }
                    return;
                }

                SalmonDriveConfig salmonConfig = GetDriveConfig();

                int iterations = salmonConfig.GetIterations();

                byte[] salt = salmonConfig.GetSalt();

                // derive the master key from the text password
                byte[] masterKey = SalmonGenerator.GetMasterKey(password, salt, iterations);

                // get the master Key Iv
                byte[] masterKeyIv = salmonConfig.GetIv();

                // get the encrypted combined key and drive id
                byte[] encData = salmonConfig.GetEncryptedData();
                
                // decrypt the combined key (encryption key + HMAC key) using the master key
                MemoryStream ms = new MemoryStream(encData);
                stream = new SalmonStream(masterKey, masterKeyIv, SalmonStream.EncryptionMode.Decrypt, ms,
                        null, false, null, null);

                byte[] driveKey = new byte[SalmonGenerator.KEY_LENGTH];
                stream.Read(driveKey, 0, driveKey.Length);

                byte[] hmacKey = new byte[SalmonGenerator.HMAC_KEY_LENGTH];
                stream.Read(hmacKey, 0, hmacKey.Length);

                byte[] driveID = new byte[SalmonGenerator.DRIVE_ID_LENGTH];
                stream.Read(driveID, 0, driveID.Length);

                // to make sure we have the right key we get the hmac portion
                // and try to verify the vault drive ID
                VerifyHmac(salmonConfig, encData, hmacKey);

                // set the combined key (encryption key + HMAC key) and the vault nonce
                SetKey(masterKey, driveKey, hmacKey, salt, iterations);
                this.driveID = driveID;
                InitFS();
                OnAuthenticationSuccess();
            }
            catch (Exception ex)
            {
                if (encryptionKey != null)
                {
                    ClearKey();
                }
                this.driveID = null;
                OnAuthenticationError();
                throw new SalmonAuthException("Could not authenticate, try again", ex);
            }
            finally
            {
                if (stream != null)
                    stream.Close();
            }
        }


        private void SetKey(byte[] masterKey, byte[] driveKey, byte[] hmacKey, byte[] salt, int iterations)
        {
            encryptionKey.SetMasterKey(masterKey);
            encryptionKey.SetDriveKey(driveKey);
            encryptionKey.SetHmacKey(hmacKey);
            encryptionKey.SetSalt(salt);
            encryptionKey.SetIterations(iterations);
        }

        private void ClearKey()
        {
            encryptionKey.SetMasterKey(null);
            encryptionKey.SetDriveKey(null);
            encryptionKey.SetHmacKey(null);
            encryptionKey.SetSalt(null);
            encryptionKey.SetIterations(0);
        }


        /// <summary>
        /// Verify that the HMAC is correct
        /// </summary>
        /// <param name="salmonConfig"></param>
        /// <param name="data"></param>
        /// <param name="hmacKey"></param>
        private void VerifyHmac(SalmonDriveConfig salmonConfig, byte[] data, byte[] hmacKey)
        {
            byte[] hmacSignature = salmonConfig.GetHMACsignature();
            byte[] hmac = SalmonIntegrity.CalculateHMAC(data, 0, data.Length, hmacKey);
            for (int i = 0; i < hmacKey.Length; i++)
                if (hmacSignature[i] != hmac[i])
                    throw new Exception("Could not authenticate");
        }

        internal byte[] GetNextNonce()
        {
            if (!IsAuthenticated())
                throw new SalmonAuthException("Not authenticated");
            return SalmonDriveManager.GetNextNonce(this);
        }

        /// <summary>
        /// Method is called when the user is authenticated
        /// </summary>
        protected abstract void OnAuthenticationSuccess();

        /// <summary>
        /// Method is called when the user authentication has failed
        /// </summary>
        protected abstract void OnAuthenticationError();

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
        public byte[] GetBytesFromRealFile(string sourcePath)
        {
            IRealFile file = GetFile(sourcePath, false);
            Stream stream = file.GetInputStream();
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
        /// Return the drive config file
        /// </summary>
        /// <returns></returns>
        private IRealFile GetDriveConfigFile()
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
        private SalmonDriveConfig GetDriveConfig()
        {
            IRealFile configFile = GetDriveConfigFile();
            if (configFile == null || !configFile.Exists())
                return null;
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
        public string GetFileNameWithoutExtension(string fileName)
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
            SalmonDriveConfig salmonConfig = null;
            try
            {
                salmonConfig = GetDriveConfig();
            }
            catch (Exception ex)
            {
                return false;
            }
            if (salmonConfig == null)
                return false;
            return true;
        }

        public void SaveCache()
        {
            //TODO: 
        }

        public byte[] GetDriveID()
        {
            return driveID;
        }
    }
}