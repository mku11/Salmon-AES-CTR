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
using Salmon.FS;
using Salmon.Streams;
using Salmon;
using System;
using System.IO;
using System.Security.Claims;
using System.Linq;

namespace Salmon.FS
{
    /// <summary>
    ///  Initializer class for setting the SalmonDrive implementation
    ///  Currently only 1 salmon drive instance is supported
    /// </summary>
    public class SalmonDriveManager
    {
        private static Type driveClassType;
        private static SalmonDrive drive;
        private static ISalmonSequencer sequencer;

        public static void SetVirtualDriveClass(Type driveClassType)
        {
            SalmonDriveManager.driveClassType = driveClassType;
        }

        public static void SetSequencer(ISalmonSequencer sequencer)
        {
            SalmonDriveManager.sequencer = sequencer;
        }

        public static ISalmonSequencer GetSequencer()
        {
            return sequencer;
        }

        /// <summary>
        /// Get the current virtual drive.
        /// </summary>
        /// <returns></returns>
        public static SalmonDrive GetDrive()
        {
            return drive;
        }

        /// <summary>
        /// Set the vault location to a directory.
        /// This requires you previously use SetDriveClass() to provide a class for the drive
        /// </summary>
        /// <param name="dirPath">The directory path that will used for storing the contents of the vault</param>
        public static SalmonDrive OpenDrive(string dirPath)
        {
			CloseDrive();
            SalmonDrive drive = Activator.CreateInstance(driveClassType, new object[] { dirPath }) as SalmonDrive;
            if (drive == null || !drive.HasConfig())
            {
                throw new Exception("Drive does not exist");
            }
            SalmonDriveManager.drive = drive;
            return drive;
        }


        public static SalmonDrive CreateDrive(string dirPath, string password)
        {
            CloseDrive();
            SalmonDrive drive = Activator.CreateInstance(driveClassType, new object[] { dirPath }) as SalmonDrive;
            if (drive.HasConfig())
            {
                throw new Exception("Drive already exists");
            }
            SalmonDriveManager.drive = drive;
            drive.SetPassword(password);
            return drive;
        }

        public static void CloseDrive()
        {
            if (drive != null)
            {
                drive.Authenticate(null);
                drive = null;
            }
        }

        static byte[] GetAuthIDBytes()
        {
            string drvStr = BitConverter.ToHex(GetDrive().GetDriveID());
            SalmonSequenceConfig.Sequence sequence = sequencer.GetSequence(drvStr);
            if (sequence == null)
            {
                byte[] authID = SalmonGenerator.GenerateAuthId();
                CreateSequence(GetDrive().GetDriveID(), authID);
            }
            sequence = sequencer.GetSequence(drvStr);
            return BitConverter.ToBytes(sequence.authID);
        }

        public static void ImportAuthFile(string filePath)
        {
            SalmonSequenceConfig.Sequence sequence = sequencer.GetSequence(BitConverter.ToHex(GetDrive().GetDriveID()));
            if (sequence != null && sequence.status == SalmonSequenceConfig.Status.Active)
                throw new Exception("Device is already authorized");

            IRealFile authConfigFile = GetDrive().GetFile(filePath, false);
            if (authConfigFile == null || !authConfigFile.Exists())
                throw new Exception("Could not import file");

            SalmonAuthConfig authConfig = GetAuthConfig(authConfigFile);

            if (!Enumerable.SequenceEqual(authConfig.authID, SalmonDriveManager.GetAuthIDBytes())
                    || !Enumerable.SequenceEqual(authConfig.driveID, GetDrive().GetDriveID())
            )
                throw new Exception("Auth file doesn't match driveID or authID");

            SalmonDriveManager.ImportSequence(authConfig.driveID, authConfig);
        }

        public static string GetAppDriveConfigFilename()
        {
            return SalmonDrive.AUTH_CONFIG_FILENAME;
        }

        public static void ExportAuthFile(string targetDeviceID, String targetDir, String filename)
        {
            byte[] cfgNonce = sequencer.NextNonce(BitConverter.ToHex(GetDrive().GetDriveID()));

            SalmonSequenceConfig.Sequence sequence = sequencer.GetSequence(BitConverter.ToHex(GetDrive().GetDriveID()));
            if (sequence == null)
                throw new Exception("Device is not authorized to export");
            IRealFile dir = GetDrive().GetFile(targetDir, true);
            IRealFile targetAppDriveConfigFile = dir.CreateFile(filename);

            byte[] pivotNonce = BitConverter.Split(sequence.nonce, sequence.maxNonce);
            sequencer.SetMaxNonce(sequence.driveID, sequence.authID, pivotNonce);
            SalmonAuthConfig.writeAuthFile(targetAppDriveConfigFile, GetDrive(),
                    BitConverter.ToBytes(sequence.driveID), BitConverter.ToBytes(targetDeviceID),
                    pivotNonce, sequence.maxNonce,
                    cfgNonce);
        }

        public static byte[] GetNextNonce(SalmonDrive salmonDrive)
        {
            return sequencer.NextNonce(BitConverter.ToHex(salmonDrive.GetDriveID()));
        }

        public static void CreateSequence(byte[] driveID, byte[] authID)
        {
            string drvStr = BitConverter.ToHex(driveID);
            string authStr = BitConverter.ToHex(authID);
            sequencer.CreateSequence(drvStr, authStr);
        }

        public static void InitSequence(byte[] driveID, byte[] authID)
        {
            byte[]
        newVaultNonce = SalmonGenerator.getDefaultVaultNonce();
            byte[]
        vaultMaxNonce = SalmonGenerator.GetDefaultMaxVaultNonce();
            string drvStr = BitConverter.ToHex(driveID);
            string authStr = BitConverter.ToHex(authID);
            sequencer.InitSequence(drvStr, authStr, newVaultNonce, vaultMaxNonce);
        }

        public static void RevokeSequences()
        {
            byte[] driveID = drive.GetDriveID();
            sequencer.RevokeSequence(BitConverter.ToHex(driveID));
        }

        private static bool VerifyAppDriveId(byte[] authID)
        {
            byte[] driveAuthID = SalmonDriveManager.GetAuthIDBytes();
            for (int i = 0; i < driveAuthID.Length; i++)
            {
                if (driveAuthID[i] != authID[i])
                {
                    return false;
                }
            }
            return true;
        }

        private static void ImportSequence(byte[] driveID, SalmonAuthConfig authConfig)
        {
            string drvStr = BitConverter.ToHex(driveID);
            string authStr = BitConverter.ToHex(authConfig.authID);
            sequencer.InitSequence(BitConverter.ToHex(driveID), authStr, authConfig.startNonce, authConfig.maxNonce);
        }

        /**
         * Return the app drive pair configuration properties for this drive
         */
        public static SalmonAuthConfig GetAuthConfig(IRealFile authFile)
        {
            SalmonFile salmonFile = new SalmonFile(authFile, GetDrive());
            SalmonStream stream = salmonFile.GetInputStream();
            MemoryStream ms = new MemoryStream();
            stream.CopyTo(ms);
            ms.Close();
            stream.Close();
            SalmonAuthConfig driveConfig = new SalmonAuthConfig(ms.ToArray());
            if (!VerifyAppDriveId(driveConfig.authID))
                throw new Exception("Could not authorize this device");
            return driveConfig;
        }

        public static string GetAuthID()
        {
            return BitConverter.ToHex(GetAuthIDBytes());
        }
    }
}
