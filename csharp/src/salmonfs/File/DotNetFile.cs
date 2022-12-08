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
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Salmon.FS;
using Salmon.Streams;
using static Salmon.Streams.AbsStream;

namespace Salmon.Net.FS
{
    /// <summary>
    /// Salmon RealFile implementation for .Net
    /// </summary>
    public class DotNetFile : IRealFile
    {
        private const int DEFAULT_ENC_BUFFER_SIZE = 32768;

        private string filePath = null;

        public DotNetFile(string path)
        {
            this.filePath = path;
        }

        public IRealFile CreateDirectory(string dirName)
        {
            string nDirPath = filePath + Path.DirectorySeparatorChar + dirName;
            Directory.CreateDirectory(nDirPath);
            DotNetFile dotNetDir = new DotNetFile(nDirPath);
            return dotNetDir;

        }

        public IRealFile CreateFile(string filename)
        {
            string nFilePath = filePath + Path.DirectorySeparatorChar + filename;
            File.Create(nFilePath).Close();
            DotNetFile dotNetFile = new DotNetFile(nFilePath);
            return dotNetFile;
        }

        public bool Delete()
        {
            if (IsDirectory())
            {
                IRealFile[] files = ListFiles();
                foreach (IRealFile file in files)
                {
                    if(file.IsDirectory())
                        Directory.Delete(file.GetAbsolutePath());
                    else
                        File.Delete(file.GetAbsolutePath());
                }
                Directory.Delete(filePath);
            } else
                File.Delete(filePath);
            return !Exists();
        }

        public bool Exists()
        {
            return File.Exists(filePath) || Directory.Exists(filePath);
        }

        public string GetAbsolutePath()
        {
            return filePath;
        }

        public string GetBaseName()
        {
            return new FileInfo(filePath).Name;
        }

        public Stream GetInputStream(int bufferSize = 0)
        {
            if (bufferSize == 0)
                bufferSize = DEFAULT_ENC_BUFFER_SIZE;
            FileStream stream = File.Open(filePath, FileMode.OpenOrCreate, FileAccess.Read, FileShare.ReadWrite);
            BufferedStream bufferedStream = new BufferedStream(stream, bufferSize);
            return bufferedStream;
        }

        public Stream GetOutputStream(int bufferSize = 0)
        {
            if (bufferSize == 0)
                bufferSize = DEFAULT_ENC_BUFFER_SIZE;
            FileStream stream = File.Open(filePath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite);
            BufferedStream bufferedStream = new BufferedStream(stream, bufferSize);
            return bufferedStream;
        }

        public IRealFile GetParent()
        {
            string dirPath = Path.GetDirectoryName(filePath);
            DotNetFile parent = new DotNetFile(dirPath);
            return parent;
        }

        public string GetPath()
        {
            return filePath;
        }

        public bool IsDirectory()
        {
            return File.GetAttributes(filePath).HasFlag(FileAttributes.Directory);
        }

        public bool IsFile()
        {
            return !IsDirectory();
        }

        public long LastModified()
        {
            return new DateTimeOffset(new FileInfo(filePath).LastWriteTime).ToUnixTimeMilliseconds();
        }

        public long Length()
        {
            return new FileInfo(filePath).Length;
        }

        public IRealFile[] ListFiles()
        {
            IList<DotNetFile> children = new List<DotNetFile>();
            string[] files = Directory.GetFiles(filePath);
            foreach (string file in files)
            {
                children.Add(new DotNetFile(file));
            }
            string[] dirs = Directory.GetDirectories(filePath);
            foreach (string dir in dirs)
            {
                children.Add(new DotNetFile(dir));
            }
            return children.ToArray();
        }

        public IRealFile Move(IRealFile newDir, AbsStream.OnProgressChanged progressListener)
        {
            string nFilePath = newDir.GetAbsolutePath() + Path.DirectorySeparatorChar + GetBaseName();
            File.Move(filePath, nFilePath);
            return new DotNetFile(nFilePath);
        }

        public IRealFile Copy(IRealFile newDir, AbsStream.OnProgressChanged progressListener)
        {
            if (this.IsDirectory())
            {
                IRealFile dir = newDir.CreateDirectory(this.GetBaseName());
                foreach (IRealFile ifile in ListFiles())
                {
                    ifile.Copy(dir, progressListener);
                }
                return dir;
            }
            else
            {
                IRealFile newFile = newDir.CreateFile(GetBaseName());
                Stream source = GetInputStream();
                Stream target = newFile.GetOutputStream();
                try
                {
                    source.CopyTo(target);
                }
                catch (Exception)
                {
                    newFile.Delete();
                    return null;
                }
                finally
                {
                    source.Close();
                    target.Close();
                }
                return newFile;
            }
        }

        public IRealFile GetChild(string filename)
        {
            if (IsFile())
                return null;
            DotNetFile child = new DotNetFile(filePath + Path.DirectorySeparatorChar + filename);
            return child;
        }

        public bool RenameTo(string newFilename)
        {
            string newFilepath = GetParent().GetPath() + Path.DirectorySeparatorChar + newFilename;
            if (IsDirectory())
                Directory.Move(filePath, newFilepath);
            else
                File.Move(filePath, newFilepath);
            filePath = newFilepath;
            return true;
        }

        public bool Mkdir()
        {
            Directory.CreateDirectory(filePath);
            return true;
        }
    }
}