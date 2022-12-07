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
using Microsoft.AspNetCore.StaticFiles;
using Salmon.FS;
using Salmon.Net.FS;
using SalmonFS.Media;
using System;
using System.IO;
using System.Web;

namespace SalmonFS.Stream
{
    public class SalmonFSConnection
    {
        private static readonly int BUFFER_SIZE = 0;
        private static readonly int ENC_THREADS = 2;

        private SalmonFile salmonFile;
        private SalmonMediaDataSource salmonMediaDataSource;
        private long pendingSeek = 0;

        protected SalmonFSConnection(string url)
        {
            SetFile(url.Replace("http://localhost/", ""));
        }

        public void Disconnect()
        {
            try
            {
                salmonMediaDataSource.Close();
            }
            catch (IOException e)
            {
                Console.Error.WriteLine(e);
            }
        }


        public bool usingProxy()
        {
            return false;
        }

        private void SetFile(string path)
        {
            path = HttpUtility.UrlDecode(path);
            IRealFile rfile = new DotNetFile(path);
            salmonFile = new SalmonFile(rfile, SalmonDriveManager.GetDrive());
        }


        public void Connect()
        {

        }

        public void SetRequestProperty(string key, string value)
        {
            if (key.Equals("Range"))
            {
                string val = value.Split("=")[1];
                pendingSeek = long.Parse(val.Split("-")[0]);
            }
        }

        public string GetContentType()
        {

            string type = null;
            try
            {
                var provider = new FileExtensionContentTypeProvider();
                provider.TryGetContentType(salmonFile.GetBaseName(), out type);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
            return type;
        }

        public int GetContentLength()
        {
            try
            {
                return (int)salmonFile.GetSize();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
            return 0;
        }

        public int getResponseCode()
        {
            if (pendingSeek > 0)
                return 206;
            else
                return 200;
        }

        public long getContentLengthLong()
        {
            try
            {
                return salmonFile.GetSize();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
            return 0;
        }


        public System.IO.Stream getInputStream()
        {
            if (salmonMediaDataSource == null)
            {
                try
                {
                    salmonMediaDataSource = new SalmonMediaDataSource(salmonFile, BUFFER_SIZE, ENC_THREADS);
                    if (pendingSeek > 0)
                    {
                        salmonMediaDataSource.Seek(pendingSeek, SeekOrigin.Begin);
                        pendingSeek = 0;
                    }
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            }
            return salmonMediaDataSource;
        }
    }
}