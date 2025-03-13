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
using Mku.Salmon;
using System;
using System.IO;

using BitConverter = Mku.Convert.BitConverter;

namespace Mku.SalmonFS.Drive;

/// <summary>
///  Represents a configuration file for a drive. The properties are encrypted in the file
///  with a master key which is password derived.
/// </summary>
public class SalmonDriveConfig
{
    //TODO: support versioned formats for the file header
    internal byte[] MagicBytes { get; } = new byte[SalmonGenerator.MAGIC_LENGTH];
    internal byte[] Version { get; } = new byte[SalmonGenerator.VERSION_LENGTH];
    internal byte[] Salt { get; } = new byte[SalmonDriveGenerator.SALT_LENGTH];
    internal byte[] Iterations { get; } = new byte[SalmonDriveGenerator.ITERATIONS_LENGTH];
    internal byte[] Iv { get; } = new byte[SalmonDriveGenerator.IV_LENGTH];
    internal byte[] EncryptedData { get; } = new byte[SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.DRIVE_ID_LENGTH];
    internal byte[] HashSignature { get; } = new byte[SalmonGenerator.HASH_RESULT_LENGTH];

    /// <summary>
    ///  Provide a class that hosts the properties of the drive config file
	/// </summary>
	///  <param name="contents">The byte array that contains the contents of the config file</param>
    public SalmonDriveConfig(byte[] contents)
    {
        MemoryStream ms = new MemoryStream(contents);
        ms.Read(MagicBytes, 0, SalmonGenerator.MAGIC_LENGTH);
        ms.Read(Version, 0, SalmonGenerator.VERSION_LENGTH);
        ms.Read(Salt, 0, SalmonDriveGenerator.SALT_LENGTH);
        ms.Read(Iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH);
        ms.Read(Iv, 0, SalmonDriveGenerator.IV_LENGTH);
        ms.Read(EncryptedData, 0, SalmonDriveGenerator.COMBINED_KEY_LENGTH + SalmonDriveGenerator.AUTH_ID_SIZE);
        ms.Read(HashSignature, 0, SalmonGenerator.HASH_RESULT_LENGTH);
        ms.Close();
    }

    /// <summary>
    ///  Write the properties of a drive to a config file
	/// </summary>
	///  <param name="configFile">                  The configuration file that will be used to write the content into</param>
    ///  <param name="magicBytes">                  The magic bytes for the header</param>
    ///  <param name="version">                     The version of the file format</param>
    ///  <param name="salt">                        The salt that will be used for encryption of the combined key</param>
    ///  <param name="iterations">                  The iteration that will be used to derive the master key from a text password</param>
    ///  <param name="keyIv">                       The initial vector that was used with the master password to encrypt the combined key</param>
    ///  <param name="encryptedData">The encrypted combined key and drive id</param>
    ///  <param name="hashSignature">               The hash signature of the drive id</param>
    public static void WriteDriveConfig(IRealFile configFile, byte[] magicBytes, byte version, byte[] salt,
                                        int iterations, byte[] keyIv,
                                        byte[] encryptedData, byte[] hashSignature)
    {
        // construct the contents of the config file
        MemoryStream ms2 = new MemoryStream();
        ms2.Write(magicBytes, 0, magicBytes.Length);
        ms2.Write(new byte[] { version }, 0, 1);
        ms2.Write(salt, 0, salt.Length);
        ms2.Write(BitConverter.ToBytes(iterations, 4), 0, 4); // sizeof( int)
        ms2.Write(keyIv, 0, keyIv.Length);
        ms2.Write(encryptedData, 0, encryptedData.Length);
        ms2.Write(hashSignature, 0, hashSignature.Length);
        ms2.Flush();
        ms2.Position = 0;

        // we write the contents to the config file
        Stream outputStream = configFile.GetOutputStream();
        ms2.CopyTo(outputStream);
        outputStream.Flush();
        outputStream.Close();
        ms2.Close();
    }

    /// <summary>
    ///  Clear properties.
	/// </summary>
	    public void Clear()
    {
        Array.Fill<byte>(MagicBytes, 0, MagicBytes.Length, (byte)0);
        Array.Fill<byte>(Version, 0, Version.Length, (byte)0);
        Array.Fill<byte>(Salt, 0, Salt.Length, (byte)0);
        Array.Fill<byte>(Iterations, 0, Iterations.Length, (byte)0);
        Array.Fill<byte>(Iv, 0, Iv.Length, (byte)0);
        Array.Fill<byte>(EncryptedData, 0, EncryptedData.Length, (byte)0);
        Array.Fill<byte>(HashSignature, 0, HashSignature.Length, (byte)0);
    }

    /// <summary>
    ///  Get the iterations to be used for the key derivation.
	/// </summary>
	///  <returns>The iterations</returns>
    public int GetIterations()
    {
        if (Iterations == null)
            return 0;
        return (int)BitConverter.ToLong(Iterations, 0, SalmonDriveGenerator.ITERATIONS_LENGTH);
    }
}