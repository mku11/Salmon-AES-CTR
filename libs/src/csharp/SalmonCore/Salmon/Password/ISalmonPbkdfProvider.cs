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

using System;

namespace Mku.Salmon.Password;

/// <summary>
///  Provides key derivation based on text passwords.
/// </summary>
public interface ISalmonPbkdfProvider
{
    /// <summary>
    ///  C# Cipher key for SHA256;
    /// </summary>
    static readonly string PBKDF_SHA256 = "PBKDF2WithHmacSHA256";
    /// <summary>
    ///  C# Cipher key for SHA1.
    ///  WARNING! SHA1 is considered insecure! Use PBKDF_SHA256 instead.
    /// </summary>
    [Obsolete("SHA1 is not secure use SHA256 instead")]
    static readonly string PBKDF_SHA1 = "PBKDF2WithHmacSHA1";

    /// <summary>
    ///  Get the PBKDF C# cipher algorigthm string.
	/// </summary>
	///  <param name="pbkdfAlgo">The PBKDF algorithm to be used</param>
    ///  <returns>The C# cipher algorithm string.</returns>
    [Obsolete("SHA1 is not secure use SHA256 instead")]
    static string GetPbkdfAlgoString(SalmonPassword.PbkdfAlgo pbkdfAlgo)
    {
        switch (pbkdfAlgo)
        {
            case SalmonPassword.PbkdfAlgo.SHA1:
                return ISalmonPbkdfProvider.PBKDF_SHA1;
            case SalmonPassword.PbkdfAlgo.SHA256:
                return ISalmonPbkdfProvider.PBKDF_SHA256;
        }
        throw new SalmonSecurityException("Unknown pbkdf algorithm");
    }

    /// <summary>
    ///  Get a key derived from a text password.
	/// </summary>
	///  <param name="password">The text password.</param>
    ///  <param name="salt">The salt needs to be at least 24 bytes.</param>
    ///  <param name="iterations">Iterations to use. Make sure you use a high number according to your hardware specs.</param>
    ///  <param name="outputBytes">The length of the output key.</param>
    ///  <param name="pbkdfAlgo">The hash algorithm to use.</param>
    ///  <returns>The key.</returns>
    ///  <exception cref="SalmonSecurityException"></exception>
    byte[] GetKey(string password, byte[] salt, int iterations, int outputBytes, SalmonPassword.PbkdfAlgo pbkdfAlgo);
}
