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

using Mku.Salmon.Bridge;

namespace Mku.Salmon.Transform;

/// <summary>
/// Generic Native AES transformer. Extend this with your specific 
/// native transformer.
/// </summary>
public class NativeTransformer : AESCTRTransformer
{
    /// <summary>
    /// The native proxy to use for loading libraries for different platforms and operating systems.
    /// Default is the Salmon Native windows proxy.
    /// </summary>
    public static INativeProxy NativeProxy { get; set; }  = new NativeProxy();

    private static readonly object lockObj = new object();

    /// <summary>
    ///  AES Implementation type
    /// </summary>
    public int ImplType { get; set; }

    /// <summary>
    /// Construct a NativeTransformer for using the native aes c library
    /// </summary>
    /// <param name="implType">The AES native implementation see ProviderType enum</param>
    public NativeTransformer(int implType)
    {
        ImplType = implType;
    }

    /// <summary>
    ///  Initialize the native Aes intrinsics transformer.
	/// </summary>
	///  <param name="key">The AES key to use.</param>
    ///  <param name="nonce">The nonce to use.</param>
    ///  <exception cref="SecurityException">Thrown when error with security</exception>
    override
    public void Init(byte[] key, byte[] nonce)
    {
        NativeProxy.SalmonInit(ImplType);
        byte[] expandedKey = new byte[AESCTRTransformer.EXPANDED_KEY_SIZE];
        NativeProxy.SalmonExpandKey(key, expandedKey);
        ExpandedKey = expandedKey;
        base.Init(key, nonce);
    }

    /// <summary>
    ///  Encrypt the data.
	/// </summary>
	///  <param name="srcBuffer">The source byte array.</param>
    ///  <param name="srcOffset">The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte array.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">The number of bytes to transform.</param>
    ///  <returns>The number of bytes transformed.</returns>
    override
    public int EncryptData(byte[] srcBuffer, int srcOffset,
                           byte[] destBuffer, int destOffset, int count)
    {
		if (Key == null)
            throw new SecurityException("No key found, run init first");
        if (Counter == null)
            throw new SecurityException("No counter found, run init first");
			
        // we block for AES GPU since it's not entirely thread safe
        if (ImplType == 3)
        {
            lock (lockObj)
            {
                return NativeProxy.SalmonTransform(ExpandedKey, Counter,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
            }
        }
        else
        {
            return NativeProxy.SalmonTransform(ExpandedKey, Counter,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
        }
    }

    /// <summary>
    ///  Decrypt the data.
	/// </summary>
	///  <param name="srcBuffer">The source byte array.</param>
    ///  <param name="srcOffset">The source byte offset.</param>
    ///  <param name="destBuffer">The destination byte array.</param>
    ///  <param name="destOffset">The destination byte offset.</param>
    ///  <param name="count">The number of bytes to transform.</param>
    ///  <returns>The number of bytes transformed.</returns>
    override
    public int DecryptData(byte[] srcBuffer, int srcOffset,
                            byte[] destBuffer, int destOffset, int count)
    {
		if (Key == null)
            throw new SecurityException("No key found, run init first");
        if (Counter == null)
            throw new SecurityException("No counter found, run init first");
		
        // we block for AES GPU since it's not entirely thread safe
        if (ImplType == 3)
        {
            lock (lockObj)
            {
                return NativeProxy.SalmonTransform(ExpandedKey, Counter,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
            }
        } else
        {
            return NativeProxy.SalmonTransform(ExpandedKey, Counter,
                srcBuffer, srcOffset,
                destBuffer, destOffset, count);
        }
    }
}
