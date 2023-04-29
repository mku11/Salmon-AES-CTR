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
using Salmon.Streams;
using System;
using System.IO;

namespace Salmon.FS
{
    public interface IRealFile
    {
        bool Exists();
        bool Delete();
        Stream GetInputStream();
        Stream GetOutputStream();
        bool RenameTo(string newFilename);
        long Length();
        long LastModified();
        string GetAbsolutePath();
        string GetPath();
        bool IsFile();
        bool IsDirectory();
        IRealFile[] ListFiles();
        string GetBaseName();
        IRealFile CreateDirectory(string dataDirName);
        IRealFile GetParent();
        IRealFile CreateFile(string filename);
        IRealFile Move(IRealFile newDir, AbsStream.OnProgressChanged progressListener);
        IRealFile Copy(IRealFile newDir, AbsStream.OnProgressChanged progressListener);
        IRealFile GetChild(string filename);
        bool Mkdir();
    }
}
