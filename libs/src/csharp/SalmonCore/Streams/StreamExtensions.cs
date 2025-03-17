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
using System.IO;

namespace Mku.Streams;

/// <summary>
///  Stream Extensions.
/// </summary>
public static class StreamExtensions
{
    private static readonly int DEFAULT_BUFFER_SIZE = 256 * 1024;

    /// <summary>
    ///  Write stream contents to another stream.
	/// </summary>
    ///  <param name="srcStream">The source stream.</param>
	///  <param name="destStream">The target stream.</param>
    ///  <param name="bufferSize">The buffer size to be used when copying.</param>
    ///  <param name="progressListener">The listener to notify when progress changes.</param>
    public static void CopyTo(this Stream srcStream, Stream destStream, int bufferSize = 0, Action<long,long> progressListener = null)
    {
        if (!srcStream.CanRead)
            throw new IOException("Target stream not readable");
        if (!destStream.CanWrite)
            throw new IOException("Target stream not writable");
        if (bufferSize <= 0)
            bufferSize = StreamExtensions.DEFAULT_BUFFER_SIZE;
        int bytesRead;
        long pos = srcStream.Position;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = srcStream.Read(buffer, 0, bufferSize)) > 0)
        {
            destStream.Write(buffer, 0, bytesRead);
            if (progressListener != null)
                progressListener(srcStream.Position, srcStream.Length);
        }
        destStream.Flush();
        srcStream.Position = pos;
    }

    /// <summary>
    ///  Write stream contents to another stream.
	/// </summary>
    ///  <param name="srcStream">The source stream.</param>
	///  <param name="destStream">The target stream.</param>
    ///  <param name="progressListener">The listener to notify when progress changes.</param>
    public static void CopyTo(this Stream srcStream, Stream destStream, Action<long,long> progressListener = null)
    {
        CopyTo(srcStream, destStream, 0, progressListener);
    }
}
