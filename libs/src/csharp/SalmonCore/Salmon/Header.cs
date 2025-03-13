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

using System.IO;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Header embedded in the AesStream. Header contains nonce and other information for
///  decrypting the stream.
/// </summary>
public class Header
{
    /// <summary>
    ///  Magic bytes.
    /// </summary>
    public byte[] MagicBytes { get; set; } = new byte[Generator.MAGIC_LENGTH];

    /// <summary>
    ///  Format version from <see cref="Generator.VERSION"/>.
    /// </summary>
    public byte Version { get; set; }

    /// <summary>
    ///  Chunk size used for data integrity.
    /// </summary>
    public int ChunkSize { get; set; }

    /// <summary>
    ///  Starting nonce for the CTR mode. This is the upper part of the Counter.
    /// </summary>
    public byte[] Nonce { get; set; }

    /// <summary>
    ///  Binary data.
    /// </summary>
    public byte[] HeaderData { get; set; }

    /// <summary>
    ///  Parse the header data from the stream
	/// </summary>
	///  <param name="stream">The stream.</param>
    ///  <returns>The header</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public static Header ParseHeaderData(Stream stream)
    {
        Header header = new Header();
        header.MagicBytes = new byte[Generator.MAGIC_LENGTH];
        stream.Read(header.MagicBytes, 0, header.MagicBytes.Length);
        byte[] versionBytes = new byte[Generator.VERSION_LENGTH];
        stream.Read(versionBytes, 0, Generator.VERSION_LENGTH);
        header.Version = versionBytes[0];
        byte[] chunkSizeHeader = new byte[Generator.CHUNK_SIZE_LENGTH];
        stream.Read(chunkSizeHeader, 0, chunkSizeHeader.Length);
        header.ChunkSize = (int)BitConverter.ToLong(chunkSizeHeader, 0, Generator.CHUNK_SIZE_LENGTH);
        header.Nonce = new byte[Generator.NONCE_LENGTH];
        stream.Read(header.Nonce, 0, header.Nonce.Length);
        stream.Position = 0;
        header.HeaderData = new byte[Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH];
        stream.Read(header.HeaderData, 0, header.HeaderData.Length);

        return header;
    }
}
