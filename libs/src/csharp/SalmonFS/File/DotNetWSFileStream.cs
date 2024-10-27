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
using System.Text;
using System.Net.Http.Headers;
using Mku.Streams;
using System.Threading.Tasks;
using Mku.Convert;
using System.Web;
using System.Collections.Specialized;

namespace Mku.File;


/**
 * An advanced Salmon File Stream implementation for java files.
 * This class is used internally for random file access of remote physical (real) files.
 */
public class DotNetWSFileStream : Stream
{
    private static readonly string PATH = "path";
    private static readonly string POSITION = "position";
    private static readonly string LENGTH = "length";

    private HttpClient client;
    /**
     * The network input stream associated.
     */
    private Stream inputStream;

    /**
     * The network output stream associated.
     */
    private Stream outputStream;
    private bool closed;
    private DotNetWSFile file;
    /**
     * Maximum amount of bytes allowed to skip forwards when seeking otherwise will open a new connection
     */
    private long MaxNetBytesSkip { get; set; } = 32768;

    private bool canWrite;
    private long position;
    private HttpResponseMessage httpResponse;
    private HttpResponseMessage outHttpResponse;

    public DotNetWSFileStream(DotNetWSFile file, FileAccess access)
    {
        this.file = file;
        if (access == FileAccess.Write)
        {
            this.canWrite = true;
        }
    }

    public override bool CanRead => !canWrite;

    public override bool CanSeek => true;

    public override bool CanWrite => canWrite;

    public override long Length => file.Length;

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
                // cannot reuse stream
                if (this.inputStream != null)
                    Console.WriteLine("cannot reuse: " + (value - position));
                this.Reset();
            }
            this.position = value;
        }
    }

    public override void Flush()
    {
        try
        {
            if (outputStream != null)
                outputStream.Flush();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }

    public override int Read(byte[] buffer, int offset, int count)
    {
        int res = GetInputStream().Read(buffer, offset, count);
        position += res;
        return res;
    }

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

    public override void SetLength(long value)
    {
        if (this.closed)
            throw new IOException("Stream is closed");
        CreateClient();
        HttpResponseMessage httpResponse = null;
        try
        {
            UriBuilder builder = new UriBuilder(file.ServicePath + "/api/setLength");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = file.Path;
            query[LENGTH] = value + "";
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            throw;
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }
        Reset();
    }

    public override void Write(byte[] buffer, int offset, int count)
    {
        GetOutputStream().Write(buffer, offset, count);
        position += Math.Min(buffer.Length, count);
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
                UriBuilder builder = new UriBuilder(file.ServicePath + "/api/get");
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                query[PATH] = file.Path;
                query[POSITION] = startPosition + "";
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
                SetDefaultHeaders(requestMessage);
                SetServiceAuth(requestMessage);
                httpResponse = client.Send(requestMessage);
                CheckStatus(httpResponse, startPosition > 0 ? HttpStatusCode.PartialContent : HttpStatusCode.OK);
                Stream stream = new BufferedStream(httpResponse.Content.ReadAsStream());
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

    private Stream GetOutputStream()
    {
        if (this.closed)
            throw new IOException("Stream is closed");
        if (this.outputStream == null)
        {
            CreateClient();
            HttpRequestMessage requestMessage = null;
            BlockingInputOutputAdapterStream outputStream = null;
            long startPosition = this.Position;
            try
            {
                outputStream = new BlockingInputOutputAdapterStream();
                this.outputStream = outputStream;

                UriBuilder builder = new UriBuilder(file.ServicePath + "/api/upload");
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                query[PATH] = file.Path;
                query[POSITION] = startPosition + "";
                builder.Query = query.ToString();
                string url = builder.ToString();

                requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
                MultipartFormDataContent content = new MultipartFormDataContent();
                Stream pipedInputStream = outputStream.InputStream;
                content.Add(new StreamContent(pipedInputStream), "file", file.BaseName);
                requestMessage.Content = content;
                SetDefaultHeaders(requestMessage);
                SetServiceAuth(requestMessage);

                Task.Run(() =>
                {
                    try
                    {
                        outHttpResponse = client.Send(requestMessage);
                        CheckStatus(outHttpResponse, startPosition > 0 ? HttpStatusCode.PartialContent : HttpStatusCode.OK);
                        outputStream.SetReceived(true);
                    }
                    catch (Exception e)
                    {
                        Console.Error.WriteLine(e);
                        throw;
                    }
                    finally
                    {
                        if (this.outputStream != null)
                        {
                            try
                            {
                                this.outputStream.Close();
                            }
                            catch (IOException e)
                            {
                                Console.Error.WriteLine(e);
                                throw;
                            }
                        }
                        if (outHttpResponse != null)
                        {
                            try
                            {
                                outHttpResponse.Dispose();
                            }
                            catch (IOException e)
                            {
                                Console.Error.WriteLine(e);
                                throw e;
                            }
                        }
                    }
                });
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                throw;
            }
        }
        if (this.outputStream == null)
            throw new IOException("Could not retrieve stream");
        return this.outputStream;
    }

    public override void Close()
    {
        Reset();
        this.closed = true;
    }

    public void Reset()
    {
        if (this.inputStream != null)
            this.inputStream.Close();
        this.inputStream = null;

        if (this.outputStream != null)
            this.outputStream.Close();
        this.outputStream = null;

        if (httpResponse != null)
            httpResponse.Dispose();
        if (outHttpResponse != null)
            outHttpResponse.Dispose();

        if (client != null)
            client.Dispose();
        client = null;

    }

    private void CheckStatus(HttpResponseMessage httpResponse, HttpStatusCode status)
    {
        if (httpResponse.StatusCode != status)
        {
            string msg = "";
            Stream stream = null;
            MemoryStream ms = null;
            try
            {
                ms = new MemoryStream();
                stream = httpResponse.Content.ReadAsStream();
                stream.CopyTo(ms);
                msg = UTF8Encoding.UTF8.GetString(ms.ToArray());
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
            finally
            {
                if (ms != null)
                    ms.Dispose();
                if (stream != null)
                    stream.Dispose();
            }
            throw new IOException(httpResponse.StatusCode
                    + " " + httpResponse.ReasonPhrase + "\n"
                    + msg);
        }
    }
    private void SetServiceAuth(HttpRequestMessage httpRequestMessage)
    {
        string encoding = new Base64().Encode(UTF8Encoding.UTF8.GetBytes(file.ServiceCredentials.ServiceUser + ":" + file.ServiceCredentials.ServicePassword));
        httpRequestMessage.Headers.Authorization = new AuthenticationHeaderValue("Basic", encoding);
    }

    private void SetDefaultHeaders(HttpRequestMessage requestMessage)
    {
        requestMessage.Headers.Add("Cache", "no-store");
        requestMessage.Headers.Add("Keep-Alive", "true");
    }
}
