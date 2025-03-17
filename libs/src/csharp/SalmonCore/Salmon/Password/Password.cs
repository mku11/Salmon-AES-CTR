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

namespace Mku.Salmon.Password;

/// <summary>
///  Generates security keys based on text passwords.
/// </summary>
public class Password
{
    /// <summary>
    ///  Global PBKDF algorithm option that will be used for the master key derivation.
    /// </summary>
    public static PbkdfAlgo PbkdfAlgorithm { get; set; } = PbkdfAlgo.SHA256;

    /// <summary>
    ///  Global PBKDF implementation to be used for text key derivation.
	/// </summary>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public static PbkdfType PbkdfImplType
    {
        set
        {
            provider = PbkdfFactory.Create(value);
        }
    }

    /// <summary>
    ///  Global PBKDF provider to be used for text key derivation.
	/// </summary>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public static IPbkdfProvider PbkdfImplProvider
    {
        set
        {
            provider = value;
        }
    }

    /// <summary>
    ///  Pbkdf provider.
    /// </summary>
    private static IPbkdfProvider provider = new DefaultPbkdfProvider();

    /// <summary>
    ///  Derives the key from a text password
	/// </summary>
	///  <param name="pass">      The text password to be used</param>
    ///  <param name="salt">      The salt to be used for the key derivation</param>
    ///  <param name="iterations">The number of iterations the key derivation algorithm will use</param>
    ///  <param name="length">The length of key to return</param>
    ///  <returns>The derived master key.</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public static byte[] GetMasterKey(string pass, byte[] salt, int iterations, int length)
    {
        byte[] masterKey = GetKeyFromPassword(pass, salt, iterations, length);
        return masterKey;
    }

    /// <summary>
    ///  Function will derive a key from a text password
	/// </summary>
	///  <param name="password">   The password that will be used to derive the key</param>
    ///  <param name="salt">       The salt byte array that will be used together with the password</param>
    ///  <param name="iterations"> The iterations to be used with Pbkdf2</param>
    ///  <param name="outputBytes">The number of bytes for the key</param>
    ///  <returns>The derived key.</returns>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    public static byte[] GetKeyFromPassword(string password, byte[] salt, int iterations, int outputBytes)
    {
        return provider.GetKey(password, salt, iterations, outputBytes, PbkdfAlgorithm);
    }
}

