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

using Mku.Salmon.Streams;

namespace Mku.Salmon.Transform;

/// <summary>
///  Creates an AES transformer object.
/// </summary>
public class SalmonTransformerFactory
{

    /// <summary>
    ///  Create an encryption transformer implementation.
	/// </summary>
	///  <param name="type">The supported provider type.</param>
    ///  <returns>The transformer.</returns>
    ///  <exception cref="SalmonSecurityException">Thrown when error with security</exception>
    public static ISalmonCTRTransformer Create(ProviderType type)
    {
        switch (type)
        {
            case ProviderType.Default:
                return new SalmonDefaultTransformer();
            case ProviderType.AesIntrinsics:
                return new SalmonAesIntrTransformer();
            case ProviderType.TinyAES:
                return new TinyAesTransformer();
        }
        throw new SalmonSecurityException("Unknown Transformer type");
    }
}
