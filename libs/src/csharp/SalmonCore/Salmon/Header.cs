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

using Mku.Streams;
using BitConverter = Mku.Convert.BitConverter;

namespace Mku.Salmon;

/// <summary>
///  Header embedded in the AesStream. Header contains nonce and other information for
///  decrypting the stream.
/// </summary>
public class Header
{
    /// <summary>
    /// 
    /// Header length
    /// </summary>
    public static readonly long HEADER_LENGTH = 16;

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
    /// Construct a header from data.
    /// </summary>
    /// <param name="headerData">The data</param>
    public Header(byte[] headerData)
    {
        this.HeaderData = headerData;
    }

    /// <summary>
    ///  Parse the header data from the stream
	/// </summary>
	///  <param name="stream">The stream.</param>
    ///  <returns>The header</returns>
    ///  <exception cref="System.IO.IOException">Thrown if error during IO</exception>
    public static Header ReadHeaderData(RandomAccessStream stream)
    {
        if (stream.Length == 0)
            return null;
        long pos = stream.Position;

        stream.Position = 0;

        byte[] headerData = new byte[Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH];
        stream.Read(headerData, 0, headerData.Length);

        Header header = new Header(headerData);
        MemoryStream ms = new MemoryStream(header.HeaderData);
        header.MagicBytes = new byte[Generator.MAGIC_LENGTH];
        ms.Read(header.MagicBytes, 0, header.MagicBytes.Length);
        byte[] versionBytes = new byte[Generator.VERSION_LENGTH];
        ms.Read(versionBytes, 0, Generator.VERSION_LENGTH);
        header.Version = versionBytes[0];
        byte[] chunkSizeHeader = new byte[Generator.CHUNK_SIZE_LENGTH];
        ms.Read(chunkSizeHeader, 0, chunkSizeHeader.Length);
        header.ChunkSize = (int)BitConverter.ToLong(chunkSizeHeader, 0, Generator.CHUNK_SIZE_LENGTH);
        header.Nonce = new byte[Generator.NONCE_LENGTH];
        ms.Read(header.Nonce, 0, header.Nonce.Length);

        stream.Position = pos;
        return header;
    }

    /// <summary>
    /// Write nonce and chunk size to the header.
    /// </summary>
    /// <param name="stream"></param>
    /// <param name="nonce"></param>
    /// <param name="chunkSize"></param>
    /// <returns>The new header that was written</returns>
    public static Header WriteHeader(RandomAccessStream stream, byte[] nonce, int chunkSize)
    {
        byte[] magicBytes = Generator.GetMagicBytes();
        byte version = Generator.VERSION;
        byte[] versionBytes = new byte[] { version };
        byte[] chunkSizeBytes = BitConverter.ToBytes(chunkSize, Generator.CHUNK_SIZE_LENGTH);

        byte[] headerData = new byte[Generator.MAGIC_LENGTH + Generator.VERSION_LENGTH
                + Generator.CHUNK_SIZE_LENGTH + Generator.NONCE_LENGTH];
        MemoryStream ms = new MemoryStream(headerData);
        ms.Write(magicBytes, 0, magicBytes.Length);
        ms.Write(versionBytes, 0, versionBytes.Length);
        ms.Write(chunkSizeBytes, 0, chunkSizeBytes.Length);
        ms.Write(nonce, 0, nonce.Length);
        ms.Position = 0;
        Header header = ReadHeaderData(ms);

        long pos = stream.Position;
        stream.Position = 0;
        ms.Position = 0;
        ms.CopyTo(stream);
        ms.Close();
        stream.Flush();
        stream.Position = pos;

        return header;
    }
}
