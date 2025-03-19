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

using Mku.FS.Streams;
using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.RegularExpressions;
using System.Web;
using static Mku.FS.File.IFile;
using MemoryStream = Mku.Streams.MemoryStream;

namespace Mku.FS.File;

/// <summary>
///  RealFile implementation for remote files via Salmon Web Service.
/// </summary>
public class HttpFile : IFile
{
    /// <summary>
    /// The directory separator.
    /// </summary>
    public static readonly string Separator = "/";

    private static HttpClient client = new HttpClient();
    private string filePath;
    private Response response;

    /// <summary>
    ///  Instantiate a remote file represented by the filepath provided.
    ///  The REST API server path (ie https://localhost:8080/)
    /// </summary>
    ///  <param name="path">The filepath.</param>
    public HttpFile(string path)
    {
        this.filePath = path;
    }

    /// <summary>
    ///  Create a directory under this directory.
	/// </summary>
	///  <param name="dirName">The name of the new directory.</param>
    ///  <returns>The newly created directory.</returns>
    public IFile CreateDirectory(string dirName)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    private Response GetResponse()
    {
        if (this.response == null)
        {
            HttpResponseMessage httpResponse = null;
            try
            {
                UriBuilder builder = new UriBuilder(this.filePath);
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
                SetDefaultHeaders(requestMessage);
                httpResponse = client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                this.response = new Response()
                {
                    Length = httpResponse.Content.Headers.ContentLength??0,
                    LastModified = httpResponse.Content.Headers.LastModified?.ToUnixTimeMilliseconds()??0,
                    ContentType = httpResponse.Content.Headers.ContentType.ToString(),
                    StatusCode = httpResponse.StatusCode
                };
            }
            finally
            {
                if (httpResponse != null)
                    httpResponse.Dispose();
            }
        }
        return this.response;
    }

    /// <summary>
    ///  Create a file under this directory.
	/// </summary>
	///  <param name="filename">The name of the new file.</param>
    ///  <returns>The newly created file.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile CreateFile(string filename)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  Delete this file or directory.
	/// </summary>
	///  <returns>True if deletion is successful.</returns>
    public bool Delete()
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  True if file or directory exists.
	/// </summary>
	///  <returns>True if file/directory exists</returns>
    public bool Exists => this.GetResponse().StatusCode == HttpStatusCode.OK || this.GetResponse().StatusCode == HttpStatusCode.PartialContent;

    /// <summary>
    ///  Get the absolute path on the physical disk. For C# this is the same as the filepath.
	/// </summary>
	///  <returns>The absolute path.</returns>
    public string AbsolutePath => filePath;

    /// <summary>
    ///  Get the name of this file or directory.
	/// </summary>
	///  <returns>The name of this file or directory.</returns>
    public string Name
    {
        get
        {
            if (this.filePath == null)
                throw new Exception("Filepath is not assigned");
            string nFilePath = this.filePath;
            if (nFilePath.EndsWith("/"))
                nFilePath = nFilePath.Substring(0, nFilePath.Length - 1);
            string[] parts = nFilePath.Split(HttpFile.Separator);
            string basename = parts[parts.Length - 1];
            if (basename == null)
                throw new Exception("Could not get basename");
            if (basename.Contains("%"))
            {
                basename = HttpUtility.UrlDecode(basename);
            }
            return basename;
        }
    }

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The stream to read from.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetInputStream()
    {
        return new HttpFileStream(this, FileAccess.Read);
    }

    /// <summary>
    ///  Get a stream for writing to this file.
	/// </summary>
	///  <returns>The stream to write to.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetOutputStream()
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem"); ;
    }


    /// <summary>
    ///  Get the parent directory of this file or directory.
    /// </summary>
    ///  <returns>The parent directory.</returns>
    public IFile Parent
    {
        get
        {
            if (filePath.Length == 0 || filePath.Equals("/"))
                return null;
            string path = filePath;
            if (path.EndsWith("/"))
                path = path.Substring(0, path.Length - 1);
            int index = path.LastIndexOf("/");
            if (index == -1)
                return null;
            HttpFile parent = new HttpFile(path.Substring(0, index));
            return parent;
        }
    }

    /// <summary>
    ///  Get the path of this file. For C# this is the same as the absolute filepath.
    /// </summary>
    ///  <returns>The path</returns>
    public string Path => filePath;

    /// <summary>
    ///  True if this is a directory.
    /// </summary>
    ///  <returns>True if directory</returns>
    public bool IsDirectory
    {
        get
        {
            Response res = this.GetResponse();
            if (res == null)
                throw new Exception("Could not get response");
            if (res.ContentType == null)
                throw new Exception("Could not get content type");
            return res.ContentType.ToString().StartsWith("text/html");
        }
    }

    /// <summary>
    ///  True if this is a file.
    /// </summary>
    ///  <returns>True if file</returns>
    public bool IsFile => !IsDirectory;

    /// <summary>
    ///  Get the last modified date on disk.
    /// </summary>
    ///  <returns>The last modified date</returns>
    public long LastDateModified => this.GetResponse().LastModified;

    /// <summary>
    ///  Get the size of the file on disk.
    /// </summary>
    ///  <returns>The length</returns>
    public long Length => this.GetResponse().Length;

    /// <summary>
    ///  Get the count of files and subdirectories
    /// </summary>
    ///  <returns>The children count</returns>
    public int ChildrenCount => this.ListFiles().Length;

    /// <summary>
    ///  List all files under this directory.
    /// </summary>
    ///  <returns>The list of files.</returns>
    public IFile[] ListFiles()
    {
        List<IFile> files = new List<IFile>();
        if (this.IsDirectory)
        {
            Stream stream = null;
            MemoryStream ms = null;
            try
            {
                stream = this.GetInputStream();
                ms = new MemoryStream();
                stream.CopyTo(ms);
                string contents = UTF8Encoding.UTF8.GetString(ms.ToArray());

                Match matcher = Regex.Match(contents, "HREF=\"(.+?)\"", RegexOptions.IgnoreCase);
                while (matcher.Success)
                {
                    string filename = matcher.Groups[1].Value;
                    if (!filename.Contains(":") && !filename.Contains(".."))
                    {
                        if (filename.Contains("%"))
                        {
                            filename = HttpUtility.UrlDecode(filename);
                        }
                        IFile file = new HttpFile(this.filePath + HttpFile.Separator + filename);
                        files.Add(file);
                    }
                    matcher = matcher.NextMatch();
                }
            }
            finally
            {
                if (ms != null)
                    ms.Close();
                if (stream != null)
                {
                    stream.Close();
                }
            }
        }
        return files.ToArray();
    }

    /// <summary>
    ///  Move this file or directory under a new directory. Not supported.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The moved file. Use this file for subsequent operations instead of the original.</returns>
    public IFile Move(IFile newDir, MoveOptions options = null)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  Move this file or directory under a new directory. Not supported.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The copied file. Use this file for subsequent operations instead of the original.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Copy(IFile newDir, CopyOptions options = null)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  Get the file or directory under this directory with the provided name.
    /// </summary>
    ///  <param name="filename">The name of the file or directory.</param>
    ///  <returns>The child file</returns>
    public IFile GetChild(string filename)
    {
        if (IsFile)
            return null;
        string nFilepath = this.GetChildPath(filename);
        HttpFile child = new HttpFile(nFilepath);
        return child;
    }

    /// <summary>
    ///  Rename the current file or directory.
    /// </summary>
    ///  <param name="newFilename">The new name for the file or directory.</param>
    ///  <returns>True if successfully renamed.</returns>
    public bool RenameTo(string newFilename)
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  Create this directory under the current filepath.
    /// </summary>
    ///  <returns>True if created.</returns>
    public bool Mkdir()
    {
        throw new NotSupportedException("Unsupported Operation, readonly filesystem");
    }

    /// <summary>
    ///  Reset cached properties 
    /// </summary>
    public void Reset()
    {
        this.response = null;
    }

    private string GetChildPath(String filename)
    {
        string nFilepath = this.filePath;
        if (!nFilepath.EndsWith(HttpFile.Separator))
            nFilepath += HttpFile.Separator;
        nFilepath += filename;
        return nFilepath;
    }

    /// <summary>
    /// Returns a string representation of this object
    /// </summary>
    /// <returns>The string representation</returns>
    override
    public string ToString()
    {
        return filePath;
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

    class Response
    {
        public HttpStatusCode StatusCode { get; internal set; }
        internal long Length { get; set; }
        internal string ContentType { get; set; }
        internal long LastModified { get; set; }
    }
}
