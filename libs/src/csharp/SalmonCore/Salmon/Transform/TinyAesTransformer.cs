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

namespace Mku.Salmon.Transform;

/// <summary>
///  Salmon AES transformer implemented with TinyAES backend.
/// </summary>
public class TinyAesTransformer : SalmonNativeTransformer
{
    /// <summary>
    ///  The constant to pass to the native code while initializing.
    /// </summary>
    public static readonly int AES_IMPL_TINY_AES = 2;

    /// <summary>
    ///  Initialiaze the native transformer to use the Tiny AES implementation.
	/// </summary>
	///  <param name="key">  The AES key to use.</param>
    ///  <param name="nonce">The nonce to use.</param>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    override
    public void Init(byte[] key, byte[] nonce)
    {
		NativeProxy.SalmonInit(AES_IMPL_TINY_AES);
        base.Init(key, nonce);
    }
}
