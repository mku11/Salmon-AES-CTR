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
using System.Net.Http;
using System.Net;
using System.Web;
using System.Collections.Specialized;
using Mku.FS.File;

namespace Mku.FS.Streams;

/// <summary>
///  An advanced Salmon File Stream implementation for java files.
/// This class is used internally for random file access of remote physical (real) files.
/// </summary>
public class HttpFileStream : Stream
{
    private HttpClient client;
    private Stream inputStream;
    private bool closed;
    private HttpFile file;

    /// <summary>
    /// Maximun number of bytes that stream can skip ahead without resetting the network connection.
    /// </summary>
    public long MaxNetBytesSkip { get; set; } = 32768;

    private long position;
    private HttpResponseMessage httpResponse;

    /// <summary>
    /// Stream for HttpFile.
    /// </summary>
    /// <param name="file">The HTTP file</param>
    /// <param name="access">The file access</param>
    /// <exception cref="NotSupportedException"></exception>
    public HttpFileStream(HttpFile file, FileAccess access)
    {
        this.file = file;
        if (access == FileAccess.Write)
        {
            throw new NotSupportedException("Unsupported Operation, readonly filesystem");
        }
    }

    /// <summary>
    /// Check if stream can read.
    /// </summary>
    public override bool CanRead => true;

    /// <summary>
    /// Check if stream can seek.
    /// </summary>
    public override bool CanSeek => true;

    /// <summary>
    /// Check if stream can write.
    /// </summary>
    public override bool CanWrite => false;

    /// <summary>
    /// The length of the stream.
    /// </summary>
    public override long Length => file.Length;

    /// <summary>
    /// The position of the stream
    /// </summary>
    public override long Position
    {
        get
        {
            return position;
        }
        set
        {
            // if the new position is forwards we can skip a small amount rather opening up a new connection
            if (this.position < value && value - position < MaxNetBytesSkip && this.inputStream != null)
            {
                inputStream.Seek(value, SeekOrigin.Begin);
            }
            else if (this.position != value)
            {
                this.Reset();
            }
            this.position = value;
        }
    }

    /// <summary>
    /// Flusth the stream. Not supported.
    /// </summary>
    /// <exception cref="NotSupportedException"></exception>
    public override void Flush()
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    /// Read from the stream.
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to read</param>
    /// <returns></returns>
    public override int Read(byte[] buffer, int offset, int count)
    {
        int res = GetInputStream().Read(buffer, offset, count);
        position += res;
        return res;
    }

    /// <summary>
    /// Seek to a position.
    /// </summary>
    /// <param name="offset">The offset</param>
    /// <param name="origin">The type of seek</param>
    /// <returns></returns>
    public override long Seek(long offset, SeekOrigin origin)
    {
        long pos = this.position;

        if (origin == SeekOrigin.Begin)
            pos = offset;
        else if (origin == SeekOrigin.Current)
            pos += offset;
        else if (origin == SeekOrigin.End)
            pos = file.Length - offset;

        this.Position = pos;
        return this.position;
    }

    /// <summary>
    /// Set the length of the stream.
    /// </summary>
    /// <param name="value"></param>
    /// <exception cref="NotSupportedException"></exception>
    public override void SetLength(long value)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    /// Write to the stream. Not supported.
    /// </summary>
    /// <param name="buffer">The buffer</param>
    /// <param name="offset">The offset</param>
    /// <param name="count">The number of bytes to write</param>
    /// <exception cref="NotSupportedException"></exception>
    public override void Write(byte[] buffer, int offset, int count)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    private Stream GetInputStream()
    {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.inputStream == null)
        {
            CreateClient();
            long startPosition = this.Position;
            try
            {
                UriBuilder builder = new UriBuilder(file.Path);
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
                SetDefaultHeaders(requestMessage);
                if (this.position > 0)
                    requestMessage.Headers.Add("Range", "bytes=" + this.position + "-");
                httpResponse = client.Send(requestMessage);
                CheckStatus(httpResponse, startPosition > 0 ? HttpStatusCode.PartialContent : HttpStatusCode.OK);
                Stream stream = httpResponse.Content.ReadAsStream();
                this.inputStream = stream;
                return stream;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                throw;
            }
        }
        if (this.inputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.inputStream;
    }

    private void CreateClient()
    {
        if (client != null)
            throw new IOException("A connection is already open");
        client = new HttpClient();
    }

    /// <summary>
    /// Close the stream.
    /// </summary>
    public override void Close()
    {
        Reset();
        this.closed = true;
    }

    /// <summary>
    /// Reset the stream.
    /// </summary>
    public void Reset()
    {
        if (this.inputStream != null)
            this.inputStream.Close();
        this.inputStream = null;

        if (httpResponse != null)
            httpResponse.Dispose();

        if (client != null)
            client.Dispose();
        client = null;

    }

    private void CheckStatus(HttpResponseMessage httpResponse, HttpStatusCode status)
    {
        if (httpResponse.StatusCode != status)
        {
            throw new IOException(httpResponse.StatusCode
                    + " " + httpResponse.ReasonPhrase);
        }
    }

    private void SetDefaultHeaders(HttpRequestMessage requestMessage)
    {
        requestMessage.Headers.Add("Cache", "no-store");
        requestMessage.Headers.Add("Connection", "keep-alive");
    }
}
