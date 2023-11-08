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

using Mku.Convert;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Header embedded in the SalmonStream. Header contains nonce and other information for
///  decrypting the stream.
/// </summary>
public class SalmonHeader
{
    /// <summary>
    ///  Magic bytes.
    /// </summary>
    public byte[] MagicBytes { get; set; } = new byte[SalmonGenerator.MAGIC_LENGTH];

    /// <summary>
    ///  Format version from <see cref="SalmonGenerator.VERSION"/>.
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
    ///  <returns></returns>
    ///  <exception cref="IOException"></exception>
    public static SalmonHeader ParseHeaderData(Stream stream)
    {
        SalmonHeader header = new SalmonHeader();
        header.MagicBytes = new byte[SalmonGenerator.MAGIC_LENGTH];
        stream.Read(header.MagicBytes, 0, header.MagicBytes.Length);
        byte[] versionBytes = new byte[SalmonGenerator.VERSION_LENGTH];
        stream.Read(versionBytes, 0, SalmonGenerator.VERSION_LENGTH);
        header.Version = versionBytes[0];
        byte[] chunkSizeHeader = new byte[SalmonGenerator.CHUNK_SIZE_LENGTH];
        stream.Read(chunkSizeHeader, 0, chunkSizeHeader.Length);
        header.ChunkSize = (int)BitConverter.ToLong(chunkSizeHeader, 0, SalmonGenerator.CHUNK_SIZE_LENGTH);
        header.Nonce = new byte[SalmonGenerator.NONCE_LENGTH];
        stream.Read(header.Nonce, 0, header.Nonce.Length);
        stream.Position = 0;
        header.HeaderData = new byte[SalmonGenerator.MAGIC_LENGTH + SalmonGenerator.VERSION_LENGTH
                + SalmonGenerator.CHUNK_SIZE_LENGTH + SalmonGenerator.NONCE_LENGTH];
        stream.Read(header.HeaderData, 0, header.HeaderData.Length);

        return header;
    }
}
