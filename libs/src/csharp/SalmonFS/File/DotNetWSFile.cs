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


namespace Mku.File;

/// <summary>
///  RealFile implementation for remote files via Salmon Web Service.
/// </summary>
public class DotNetWSFile : IRealFile
{
    private static readonly string PATH = "path";
    private static readonly string DEST_DIR = "destDir";
    private static readonly string FILENAME = "filename";
    
    public static readonly string Separator = "/";
    private static HttpClient client = new HttpClient();
    private string filePath;
    public string ServicePath { get; private set; }
    public Credentials ServiceCredentials { get; set; }

    /// <summary>
    /// Salmon Web service credentials
    /// </summary>
    public class Credentials
    {
        public string ServiceUser { get; private set; }
        public string ServicePassword { get; private set; }

        public Credentials(string serviceUser, string servicePassword)
        {
            this.ServiceUser = serviceUser;
            this.ServicePassword = servicePassword;
        }
    }

    private class Response
    {
        [JsonPropertyName("path")]
        public string Path { get; set; }
        [JsonPropertyName("name")]
        public string Name { get; set; }
        [JsonPropertyName("length")]
        public long Length { get; set; }
        [JsonPropertyName("lastModified")]
        public long LastModified { get; set; }
        [JsonPropertyName("directory")]
        public bool IsDirectory { get; set; }
        [JsonPropertyName("file")]
        public bool IsFile { get; set; }
        [JsonPropertyName("present")]
        public bool Exists { get; set; }
        public HttpResponseHeaders Headers { get; set; }
    }

    /// <summary>
    ///  Instantiate a remote file represented by the filepath provided.
    ///  The REST API server path (ie https://localhost:8080/)
	/// </summary>
	///  <param name="path">The filepath.</param>
    public DotNetWSFile(string path, string servicePath, Credentials credentials)
    {
        this.filePath = path;
        this.ServicePath = servicePath;
        this.ServiceCredentials = credentials;
    }

    /// <summary>
    ///  Create a directory under this directory.
	/// </summary>
	///  <param name="dirName">The name of the new directory.</param>
    ///  <returns>The newly created directory.</returns>
    public IRealFile CreateDirectory(string dirName)
    {
        string nDirPath = filePath + Separator + dirName;
        HttpResponseMessage httpResponse = null;
        try
        {
            Dictionary<string, string> parameters = new Dictionary<string, string> { { PATH, nDirPath } };
            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, this.ServicePath + "/api/mkdir");
            requestMessage.Content = new FormUrlEncodedContent(parameters);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            DotNetWSFile dir = new DotNetWSFile(nDirPath, ServicePath, ServiceCredentials);
            return dir;
        }
        catch (IOException e)
        {
            Console.Error.WriteLine(e);
            throw e;
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
                    throw e;
                }
            }
        }
    }

    private Response GetResponse()
    {
        HttpResponseMessage httpResponse = null;
        Response response = null;
        try
        {
            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Get, this.ServicePath + "/api/info"
                + "?" + PATH + "=" + filePath);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
            response.Headers = httpResponse.Headers;
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }
        return response;
    }

    /// <summary>
    ///  Create a file under this directory.
	/// </summary>
	///  <param name="filename">The name of the new file.</param>
    ///  <returns>The newly created file.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IRealFile CreateFile(string filename)
    {
        string nFilePath = filePath + Separator + filename;

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
            httpResponse = client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            DotNetWSFile file = new DotNetWSFile(nFilePath, ServicePath, ServiceCredentials);
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
            IRealFile[] files = ListFiles();
            foreach (IRealFile file in files)
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
                    httpResponse = client.Send(requestMessage);
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
            dirHttpResponse = client.Send(requestMessage);
            CheckStatus(dirHttpResponse, HttpStatusCode.OK);
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
        return !Exists;
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
    public string AbsolutePath => filePath;

    /// <summary>
    ///  Get the name of this file or directory.
	/// </summary>
	///  <returns>The name of this file or directory.</returns>
    public string BaseName => GetResponse().Name;

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The stream to read from.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetInputStream()
    {
        return new DotNetWSFileStream(this, FileAccess.Read);
    }

    /// <summary>
    ///  Get a stream for writing to this file.
	/// </summary>
	///  <returns>The stream to write to.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetOutputStream()
    {
        return new DotNetWSFileStream(this, FileAccess.Write);
    }


    /// <summary>
    ///  Get the parent directory of this file or directory.
    /// </summary>
    ///  <returns>The parent directory.</returns>
    public IRealFile Parent
    {
        get
        {
            string dirPath = Directory.GetParent(filePath).FullName;
            DotNetWSFile parent = new DotNetWSFile(dirPath, ServicePath, ServiceCredentials);
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
    public long LastModified => GetResponse().LastModified;

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
                        httpResponse = client.Send(requestMessage);
                        CheckStatus(httpResponse, HttpStatusCode.OK);
                        int res = getFileListCount(httpResponse.Content);
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


    private int getFileListCount(HttpContent content)
    {
        Response[] responses = (Response[])content.ReadFromJsonAsync(typeof(Response[])).Result;
        return responses.Length;
    }

    private Response[] parseFileList(HttpContent content)
    {
        Response[] responses = (Response[])content.ReadFromJsonAsync(typeof(Response[])).Result;
        return responses;
    }

    /// <summary>
    ///  List all files under this directory.
    /// </summary>
    ///  <returns>The list of files.</returns>
    public IRealFile[] ListFiles()
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
            httpResponse = client.Send(requestMessage);
            CheckStatus(httpResponse, HttpStatusCode.OK);
            files = parseFileList(httpResponse.Content);
        }
        finally
        {
            if (httpResponse != null)
                httpResponse.Dispose();
        }

        if (files == null)
            return new DotNetWSFile[0];

        List<DotNetWSFile> realFiles = new List<DotNetWSFile>();
        List<DotNetWSFile> realDirs = new List<DotNetWSFile>();
        for (int i = 0; i < files.Length; i++)
        {
            DotNetWSFile file = new DotNetWSFile(files[i].Path, ServicePath, ServiceCredentials);
            if (file.IsDirectory)
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
    ///  <param name="newName">The new name.</param>
    ///  <param name="progressListener">Observer to notify when progress changes.</param>
    ///  <returns>The moved file. Use this file for subsequent operations instead of the original.</returns>
    public IRealFile Move(IRealFile newDir, string newName = null, Action<long, long> progressListener = null)
    {
        newName = newName ?? BaseName;
        //TODO: ToSync
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exist");
        IRealFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");

        if (IsDirectory)
        {
            throw new IOException("Could not move directory use IRealFile moveRecursively() instead");
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
                httpResponse = client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
                response.Headers = httpResponse.Headers;
                newFile = new DotNetWSFile(response.Path, ServicePath, ServiceCredentials);
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
    ///  <param name="newName">The new file name</param>
    ///  <param name="progressListener">Observer to notify when progress changes.</param>
    ///  <returns>The copied file. Use this file for subsequent operations instead of the original.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IRealFile Copy(IRealFile newDir, string newName = null, Action<long, long> progressListener = null)
    {
        newName = newName ?? BaseName;
        //TODO: ToSync
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exist");
        IRealFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");

        if (IsDirectory)
        {
            throw new IOException("Could not copy directory use IRealFile copyRecursively() instead");
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
                httpResponse = client.Send(requestMessage);
                CheckStatus(httpResponse, HttpStatusCode.OK);
                Response response = (Response)httpResponse.Content.ReadFromJsonAsync(typeof(Response)).Result;
                response.Headers = httpResponse.Headers;
                newFile = new DotNetWSFile(response.Path, ServicePath, ServiceCredentials);
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
    public IRealFile GetChild(string filename)
    {
        if (IsFile)
            return null;
        DotNetWSFile child = new DotNetWSFile(filePath + System.IO.Path.DirectorySeparatorChar + filename,
            ServicePath, ServiceCredentials);
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
            UriBuilder builder = new UriBuilder(ServicePath + "/api/rename");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            query[FILENAME] = newFilename;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Put, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = client.Send(requestMessage);
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
            UriBuilder builder = new UriBuilder(ServicePath + "/api/mkdir");
            NameValueCollection query = HttpUtility.ParseQueryString(builder.Query);
            query[PATH] = filePath;
            builder.Query = query.ToString();
            string url = builder.ToString();

            HttpRequestMessage requestMessage = new HttpRequestMessage(HttpMethod.Post, url);
            SetDefaultHeaders(requestMessage);
            SetServiceAuth(requestMessage);
            httpResponse = client.Send(requestMessage);
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
        string encoding = new Base64().Encode(UTF8Encoding.UTF8.GetBytes(ServiceCredentials.ServiceUser + ":" + ServiceCredentials.ServicePassword));
        httpRequestMessage.Headers.Authorization = new AuthenticationHeaderValue("Basic", encoding);
    }

    private void SetDefaultHeaders(HttpRequestMessage requestMessage)
    {
        requestMessage.Headers.Add("Cache", "no-store");
        requestMessage.Headers.Add("Keep-Alive", "true");
    }
}
