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

// using Microsoft.AspNetCore.Cryptography.KeyDerivation;

using System.Security.Cryptography;

namespace Mku.Salmon.Password;

/// <summary>
///  Provides pbkdf via C# default algorithm.
/// </summary>
public class SalmonDefaultPbkdfProvider : ISalmonPbkdfProvider
{
    /// <summary>
    ///  Get a key derived from a text password
	/// </summary>
	///  <param name="password">The text password.</param>
    ///  <param name="salt">The salt needs to be at least 24 bytes.</param>
    ///  <param name="iterations">Iterations to use. Make sure you use a high number according to your hardware specs.</param>
    ///  <param name="outputBytes">The length of the output key.</param>
    ///  <param name="pbkdfAlgo">The hash algorithm to use.</param>
    ///  <returns>The key.</returns>
    ///  <exception cref="SalmonSecurityException"></exception>
    public byte[] GetKey(string password, byte[] salt, int iterations, int outputBytes, SalmonPassword.PbkdfAlgo pbkdfAlgo)
    {
#if !NETSTANDARD
        // we are using SHA256
        return Rfc2898DeriveBytes.Pbkdf2(password, salt, iterations, HashAlgorithmName.SHA256, outputBytes);
#else
        throw new NotSupportedException();
#endif
    }
}
