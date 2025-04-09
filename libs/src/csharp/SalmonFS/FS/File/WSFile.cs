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
using Mku.FS.Streams;
using Mku.Streams;
using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json.Serialization;
using System.Web;
using static Mku.FS.File.HttpFile;
using static Mku.FS.File.IFile;


namespace Mku.FS.File;

/// <summary>
///  RealFile implementation for remote files via Salmon Web Service.
/// </summary>
public class WSFile : IFile
{
    /// <summary>
    /// Directory separator.
    /// </summary>
    public static readonly string Separator = "/";

    private static readonly string PATH = "path";
    private static readonly string DEST_DIR = "destDir";
    private static readonly string FILENAME = "filename";

    /// <summary>
    /// The HTTP client
    /// </summary>
    public static HttpSyncClient Client { get; set; } = new HttpSyncClient();
    private string filePath;
    private Response response;

    /// <summary>
    /// Get the service path.
    /// </summary>
    public string ServicePath { get; private set; }

    /// <summary>
    /// Get the service credentials.
    /// </summary>
    public Credentials ServiceCredentials { get; set; }

    /// <summary>
    /// Salmon Web service credentials
    /// </summary>
    public class Credentials
    {
        /// <summary>
        /// Get the user name.
        /// </summary>
        public string ServiceUser { get; private set; }

        /// <summary>
        /// Get the password.
        /// </summary>
        public string ServicePassword { get; private set; }

        /// <summary>
        /// Construct the credentials.
        /// </summary>
        /// <param name="serviceUser"></param>
        /// <param name="servicePassword"></param>
        public Credentials(string serviceUser, string servicePassword)
        {
            this.ServiceUser = serviceUser;
            this.ServicePassword = servicePassword;
        }
    }

    /// <summary>
    /// Response from the web service.
    /// </summary>
    public class Response
    {
        /// <summary>
        /// The virtual path.
        /// </summary>
        [JsonPropertyName("path")]
        public string Path { get; set; }

        /// <summary>
        /// The file name.
        /// </summary>
        [JsonPropertyName("name")]
        public string Name { get; set; }

        /// <summary>
        /// The lenght of the file.
        /// </summary>
        [JsonPropertyName("length")]
        public long Length { get; set; }

        /// <summary>
        /// Last Date modified.
        /// </summary>
        [JsonPropertyName("lastModified")]
        public long LastModified { get; set; }

        /// <summary>
        /// True if it's a directory
        /// </summary>
        [JsonPropertyName("directory")]
        public bool IsDirectory { get; set; }

        /// <summary>
        /// True if it's a file.
        /// </summary>
        [JsonPropertyName("file")]
        public bool IsFile { get; set; }

        /// <summary>
        /// True if it exists.
        /// </summary>
        [JsonPropertyName("present")]
        public bool Exists { get; set; }

        /// <summary>
        /// Headers from the Http response
        /// </summary>
        public HttpResponseHeaders Headers { get; set; }
    }

    /// <summary>
    ///  Instantiate a remote file represented by the filepath provided.
    ///  The REST API server path (ie https://localhost:8080/)
    /// </summary>
    ///  <param name="path">The filepath.</param>
    /// <param name="servicePath">The service path</param>
    /// <param name="credentials">The service credentials</param>
    public WSFile(string path, string servicePath, Credentials credentials)
    {
        if (!path.StartsWith(WSFile.Separator))
            path = WSFile.Separator + path;
        this.filePath = path;
        this.ServicePath = servicePath;
        this.ServiceCredentials = credentials;
    }

    /// <summary>
    ///  Create a directory under this directory.
	/// </summary>
	///  <param name="dirName">The name of the new directory.</param>
    ///  <returns>The newly created directory.</returns>
    public IFile CreateDirectory(string dirName)
    {
        string nDirPath = this.GetChildPath(dirName);
        HttpResponseMessage httpResponse = null;
        try
        {
            UriBuilder builder = new UriBuilder(ServicePath + "/api/mkdir");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = nDirPath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = Client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            WSFile dir = new WSFile(nDirPath, ServicePath, ServiceCredentials);
            return dir;
        }
        catch (IOException e)
        {
            Console.Error.WriteLine(e);
            throw;
        }
        finally
        {
            if (httpResponse != null)
            {
                try
                {
                    httpResponse.Dispose();
                }
                catch (IOException e)
                {
                    Console.Error.WriteLine(e);
                    throw;
                }
            }
        }
    }

    private Response GetResponse()
    {
        if (this.response == null)
        {
            HttpResponseMessage httpResponse = null;
            try
            {
                UriBuilder builder = new UriBuilder(ServicePath + "/api/info");
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                query[PATH] = filePath;
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
                SetDefaultHeaders(requestMessage);
                SetServiceAuth(requestMessage);
                httpResponse = Client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                this.response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
                this.response.Headers = httpResponse.Headers;
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
        String nFilePath = this.GetChildPath(filename);
        HttpResponseMessage httpResponse = null;
        try
        {
            UriBuilder builder = new UriBuilder(ServicePath + "/api/create");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = nFilePath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = Client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            WSFile file = new WSFile(nFilePath, ServicePath, ServiceCredentials);
            return file;
        }
        catch (Exception e)
        {
            throw;
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }
    }

    /// <summary>
    ///  Delete this file or directory.
	/// </summary>
	///  <returns>True if deletion is successful.</returns>
    public bool Delete()
    {
        if (IsDirectory)
        {
            IFile[] files = ListFiles();
            foreach (IFile file in files)
            {
                HttpResponseMessage httpResponse = null;
                try
                {
                    UriBuilder builder = new UriBuilder(ServicePath + "/api/list");
                    NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                    query[PATH] = file.Path;
                    builder.Query = query.ToString();
                    string url = builder.ToString();

                    HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
                    SetDefaultHeaders(requestMessage);
                    SetServiceAuth(requestMessage);
                    httpResponse = Client.Send(requestMessage);
                    CheckStatus(httpResponse, HttpStatusCode.OK);
                }
                catch (Exception ex)
                {
                    throw;
                }
                finally
                {
                    if (httpResponse != null)
                        httpResponse.Dispose();
                }
            }
        }
        HttpResponseMessage dirHttpResponse = null;
        try
        {
            UriBuilder builder = new UriBuilder(ServicePath + "/api/delete");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Delete, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            dirHttpResponse = Client.Send(requestMessage);
            CheckStatus(dirHttpResponse, HttpStatusCode.OK);
            this.Reset();
            return true;
        }
        catch (Exception ex)
        {
            throw;
        }
        finally
        {
            if (dirHttpResponse != null)
                dirHttpResponse.Dispose();
        }
    }

    /// <summary>
    ///  True if file or directory exists.
	/// </summary>
	///  <returns>True if file/directory exists</returns>
    public bool Exists => GetResponse().Exists;

    /// <summary>
    ///  Get the absolute path on the physical disk. For C# this is the same as the filepath.
	/// </summary>
	///  <returns>The absolute path.</returns>
    public string DisplayPath => filePath;

    /// <summary>
    ///  Get the name of this file or directory.
	/// </summary>
	///  <returns>The name of this file or directory.</returns>
    public string Name => GetResponse().Name;

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The stream to read from.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetInputStream()
    {
        Reset();
        return new WSFileStream(this, FileAccess.Read);
    }

    /// <summary>
    ///  Get a stream for writing to this file.
	/// </summary>
	///  <returns>The stream to write to.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetOutputStream()
    {
        Reset();
        return new WSFileStream(this, FileAccess.Write);
    }


    /// <summary>
    ///  Get the parent directory of this file or directory.
    /// </summary>
    ///  <returns>The parent directory.</returns>
    public IFile Parent
    {
        get
        {
            if (filePath.Length == 0 || filePath.Equals(WSFile.Separator))
                return null;
            string path = filePath;
            if (path.EndsWith(WSFile.Separator))
                path = path.Substring(0, path.Length - 1);
            int index = path.LastIndexOf(WSFile.Separator);
            if (index == -1)
                return null;
            WSFile parent = new WSFile(path.Substring(0, index), ServicePath, ServiceCredentials);
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
    public bool IsDirectory => GetResponse().IsDirectory;

    /// <summary>
    ///  True if this is a file.
    /// </summary>
    ///  <returns>True if file</returns>
    public bool IsFile => GetResponse().IsFile;

    /// <summary>
    ///  Get the last modified date on disk.
    /// </summary>
    ///  <returns>The last modified date</returns>
    public long LastDateModified => GetResponse().LastModified;

    /// <summary>
    ///  Get the size of the file on disk.
    /// </summary>
    ///  <returns>The length</returns>
    public long Length => GetResponse().Length;

    /// <summary>
    ///  Get the count of files and subdirectories
    /// </summary>
    ///  <returns>The children count</returns>
    public int ChildrenCount
    {
        get
        {
            try
            {
                if (IsDirectory)
                {
                    HttpResponseMessage httpResponse = null;
                    Response response = null;
                    try
                    {
                        UriBuilder builder = new UriBuilder(ServicePath + "/api/list");
                        NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                        query[PATH] = filePath;
                        builder.Query = query.ToString();
                        string url = builder.ToString();

                        HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
                        SetDefaultHeaders(requestMessage);
                        SetServiceAuth(requestMessage);
                        httpResponse = Client.Send(requestMessage);
                        CheckStatus(httpResponse, HttpStatusCode.OK);
                        int res = GetFileListCount(httpResponse.Content);
                        return res;
                    }
                    finally
                    {
                        if (httpResponse != null)
                            httpResponse.Dispose();
                    }
                }
            }
            catch (Exception ex)
            {
                throw;
            }
            return 0;
        }
    }


    private int GetFileListCount(HttpContent content)
    {
        Response[] responses = (Response[])content.ReadFromJsonAsync(typeof(Response[])).Result;
        return responses.Length;
    }

    private Response[] ParseFileList(HttpContent content)
    {
        Response[] responses = (Response[])content.ReadFromJsonAsync(typeof(Response[])).Result;
        return responses;
    }

    /// <summary>
    ///  List all files under this directory.
    /// </summary>
    ///  <returns>The list of files.</returns>
    public IFile[] ListFiles()
    {
        HttpResponseMessage httpResponse = null;
        Response[] files = null;
        try
        {
            UriBuilder builder = new UriBuilder(ServicePath + "/api/list");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = Client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            files = ParseFileList(httpResponse.Content);
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }

        if (files == null)
            return new WSFile[0];

        List<WSFile> realFiles = new List<WSFile>();
        List<WSFile> realDirs = new List<WSFile>();
        for (int i = 0; i < files.Length; i++)
        {
            WSFile file = new WSFile(files[i].Path, ServicePath, ServiceCredentials);
            if (files[i].IsDirectory)
                realDirs.Add(file);
            else
                realFiles.Add(file);
        }
        realDirs.AddRange(realFiles);
        return realDirs.ToArray();
    }

    /// <summary>
    ///  Move this file or directory under a new directory.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The moved file. Use this file for subsequent operations instead of the original.</returns>
    public IFile Move(IFile newDir, MoveOptions options = null)
    {
        if (options == null)
            options = new MoveOptions();
        string newName = options.newFilename ?? Name;
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exist");
        IFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");
        if (IsDirectory)
        {
            throw new IOException("Could not move directory use IFile moveRecursively() instead");
        }
        else
        {
            HttpResponseMessage httpResponse = null;
            try
            {
                UriBuilder builder = new UriBuilder(ServicePath + "/api/move");
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                query[PATH] = filePath;
                query[DEST_DIR] = newDir.Path;
                query[FILENAME] = newName;
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Put, url);
                SetDefaultHeaders(requestMessage);
                SetServiceAuth(requestMessage);
                httpResponse = Client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
                response.Headers = httpResponse.Headers;
                newFile = new WSFile(response.Path, ServicePath, ServiceCredentials);
                Reset();
                return newFile;
            }
            catch (Exception ex)
            {
                throw;
            }
            finally
            {
                if (httpResponse != null)
                    httpResponse.Dispose();
            }
        }
    }

    /// <summary>
    ///  Move this file or directory under a new directory.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The copied file. Use this file for subsequent operations instead of the original.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Copy(IFile newDir, CopyOptions options = null)
    {
        if (options == null)
            options = new CopyOptions();
        string newName = options.newFilename ?? Name;
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exist");
        IFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");

        if (IsDirectory)
        {
            throw new IOException("Could not copy directory use IFile copyRecursively() instead");
        }
        else
        {
            HttpResponseMessage httpResponse = null;
            try
            {
                UriBuilder builder = new UriBuilder(ServicePath + "/api/copy");
                NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
                query[PATH] = filePath;
                query[DEST_DIR] = newDir.Path;
                query[FILENAME] = newName;
                builder.Query = query.ToString();
                string url = builder.ToString();

                HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
                SetDefaultHeaders(requestMessage);
                SetServiceAuth(requestMessage);
                httpResponse = Client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
                response.Headers = httpResponse.Headers;
                newFile = new WSFile(response.Path, ServicePath, ServiceCredentials);
                Reset();
                return newFile;
            }
            catch (Exception ex)
            {
                throw;
            }
            finally
            {
                if (httpResponse != null)
                    httpResponse.Dispose();
            }
        }
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
        WSFile child = new WSFile(nFilepath, ServicePath, ServiceCredentials);
        return child;
    }

    /// <summary>
    ///  Rename the current file or directory.
    /// </summary>
    ///  <param name="newFilename">The new name for the file or directory.</param>
    ///  <returns>True if successfully renamed.</returns>
    public bool RenameTo(string newFilename)
    {
        HttpResponseMessage httpResponse = null;
        try
        {
            Reset();
            UriBuilder builder = new UriBuilder(ServicePath + "/api/rename");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            query[FILENAME] = newFilename;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Put, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = Client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
            response.Headers = httpResponse.Headers;
            this.filePath = response.Path;
            return true;
        }
        catch (Exception ex)
        {
            throw;
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }
    }

    /// <summary>
    ///  Create this directory under the current filepath.
    /// </summary>
    ///  <returns>True if created.</returns>
    public bool Mkdir()
    {
        HttpResponseMessage httpResponse = null;
        try
        {
            Reset();
            UriBuilder builder = new UriBuilder(ServicePath + "/api/mkdir");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = Client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
            response.Headers = httpResponse.Headers;
            this.filePath = response.Path;
            return true;
        }
        catch (Exception ex)
        {
            throw;
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }
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
            string msg = "";
            Stream stream = null;
            System.IO.MemoryStream ms = null;
            try
            {
                ms = new System.IO.MemoryStream();
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
                    ms.Close();
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
        string encoding = new Base64().Encode(UTF8Encoding.UTF8.GetBytes(ServiceCredentials.ServiceUser + ":" + ServiceCredentials.ServicePassword));
        httpRequestMessage.Headers.Authorization = new AuthenticationHeaderValue("Basic", encoding);
    }

    private void SetDefaultHeaders(HttpRequestMessage requestMessage)
    {
        requestMessage.Headers.Add("Cache", "no-store");
    }
}
