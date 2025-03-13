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

using Mku.Salmon;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.SalmonFS.Drive;

/// <summary>
///  Utility class generates internal secure properties for the drive.
/// </summary>
public class DriveGenerator
{
    /// <summary>
    ///  Initial vector length that will be used for encryption and master encryption of the combined key
    /// </summary>
    public static readonly int IV_LENGTH = 16;
    /// <summary>
    ///  combined key is drive key + hash key.
    /// </summary>
    public static readonly int COMBINED_KEY_LENGTH = Generator.KEY_LENGTH + Generator.HASH_KEY_LENGTH;
    /// <summary>
    ///  Salt length.
    /// </summary>
    public static readonly int SALT_LENGTH = 24;
    /// <summary>
    ///  Drive ID size.
    /// </summary>
    public static readonly int DRIVE_ID_LENGTH = 16;
    /// <summary>
    ///  Auth ID size
    /// </summary>
    public static readonly int AUTH_ID_SIZE = 16;
    /// <summary>
    ///  Length for the iterations that will be stored in the encrypted data header.
    /// </summary>
    public static readonly int ITERATIONS_LENGTH = 4;
    /// <summary>
    ///  Master key to encrypt the combined key we also use AES256.
    /// </summary>
    public static readonly int MASTER_KEY_LENGTH = 32;

    /// <summary>
    ///  Global default iterations that will be used for the master key derivation.
    /// </summary>
    private static int iterations = 65536;

    /// <summary>
    ///  Generate a Drive ID.
	/// </summary>
	///  <returns>The Drive ID.</returns>
    public static byte[] GenerateDriveId()
    {
        return Generator.GetSecureRandomBytes(DRIVE_ID_LENGTH);
    }

    /// <summary>
    ///  Generate a secure random authorization ID.
	/// </summary>
	///  <returns>The authorization Id (16 bytes).</returns>
    public static byte[] GenerateAuthId()
    {
        return Generator.GetSecureRandomBytes(AUTH_ID_SIZE);
    }

    /// <summary>
    ///  Generates a secure random combined key (drive key + hash key)
	/// </summary>
	///  <returns>The length of the combined key.</returns>
    public static byte[] GenerateCombinedKey()
    {
        return Generator.GetSecureRandomBytes(COMBINED_KEY_LENGTH);
    }

    /// <summary>
    ///  Generates the initial vector that will be used with the master key to encrypt the combined key (drive key + hash key)
    /// </summary>
    public static byte[] GenerateMasterKeyIV()
    {
        return Generator.GetSecureRandomBytes(IV_LENGTH);
    }

    /// <summary>
    ///  Generates a salt.
	/// </summary>
	///  <returns>The salt byte array.</returns>
    public static byte[] GenerateSalt()
    {
        return Generator.GetSecureRandomBytes(SALT_LENGTH);
    }

    /// <summary>
    ///  Get the starting nonce that will be used for encrypt drive files and filenames.
	/// </summary>
	///  <returns>A secure random byte array (8 bytes).</returns>
    public static byte[] GetStartingNonce()
    {
        byte[] bytes = new byte[Generator.NONCE_LENGTH];
        return bytes;
    }

    /// <summary>
    ///  Get the default max nonce to be used for drives.
	/// </summary>
	///  <returns>A secure random byte array (8 bytes).</returns>
    public static byte[] GetMaxNonce()
    {
        return BitConverter.ToBytes(long.MaxValue, 8);
    }

    /// <summary>
    ///  Returns the iterations used for deriving the combined key from
    ///  the text password
	/// </summary>
	///  <returns>The current iterations for the key derivation.</returns>
    public static int GetIterations()
    {
        return iterations;
    }

    /// <summary>
    ///  Set the default iterations.
	/// </summary>
	///  <param name="iterations">The iterations</param>
    public static void SetIterations(int iterations)
    {
        DriveGenerator.iterations = iterations;
    }

}
