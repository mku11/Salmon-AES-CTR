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

namespace Mku.Salmon.Streams;

/// <summary>
///  AES provider types. List of AES implementations that currently supported.
///  <para>Default see: <see cref="Default">Default</see></para>
///  <para>Aes CPU-Accelerated see: <see cref="AesIntrinsics">AesIntrinsics</see></para>
///  <para>Aes Native see: <see cref="Aes">Aes</see></para>
///  <para>Aes GPU-Accelerated see: <see cref="AesGPU">AesGPU</see></para>
///  </summary>
public enum ProviderType
{
    /// <summary>
    ///  Default .NET AES cipher.
    /// </summary>
    Default = 0,
    /// <summary>
    ///  Salmon builtin AES intrinsics. This needs the SalmonNative library to be loaded. See: <see href="https://github.com/mku11/Salmon-AES-CTR#readme">README.md</see>
    /// </summary>
    AesIntrinsics = 1,
    /// <summary>
    ///  Salmon builtin AES. Works with almost all hardware. This needs the SalmonNative library to be loaded. See: <see href="https://github.com/mku11/Salmon-AES-CTR#readme">README.md</see>
    /// </summary>
    Aes = 2,
    /// <summary>
    ///  Salmon builtin AES GPU. This needs OpenCL, a compatible Graphics card, and the SalmonNative library to be loaded. See: <see href="https://github.com/mku11/Salmon-AES-CTR#readme">README.md</see>
    /// </summary>
    AesGPU = 3
}
