using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using static Mku.IO.RandomAccessStreamExtensions;

namespace Mku.File;
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


/// <summary>
///  Salmon RealFile implementation for C#.
/// </summary>
public class DotNetFile : IRealFile
{
    private string filePath;

    /// <summary>
    ///  Instantiate a real file represented by the filepath provided.
	/// </summary>
	///  <param name="path">The filepath.</param>
    public DotNetFile(string path)
    {
        this.filePath = path;
    }

    /// <summary>
    ///  Create a directory under this directory.
	/// </summary>
	///  <param name="dirName">The name of the new directory.</param>
    ///  <returns>The newly created directory.</returns>
    public IRealFile CreateDirectory(string dirName)
    {
        string nDirPath = filePath + System.IO.Path.DirectorySeparatorChar + dirName;
        Directory.CreateDirectory(nDirPath);
        DotNetFile dotNetDir = new DotNetFile(nDirPath);
        return dotNetDir;
    }

    /// <summary>
    ///  Create a file under this directory.
	/// </summary>
	///  <param name="filename">The name of the new file.</param>
    ///  <returns>The newly created file.</returns>
    ///  <exception cref="IOException"></exception>
    public IRealFile CreateFile(string filename)
    {
        string nFilePath = filePath + System.IO.Path.DirectorySeparatorChar + filename;
        System.IO.File.Create(nFilePath).Close();
        DotNetFile dotNetFile = new DotNetFile(nFilePath);
        return dotNetFile;
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
                if (file.IsDirectory)
                    Directory.Delete(file.AbsolutePath);
                else
                    System.IO.File.Delete(file.AbsolutePath);
            }
            Directory.Delete(filePath);
        }
        else
            System.IO.File.Delete(filePath);
        return !Exists;
    }

    /// <summary>
    ///  True if file or directory exists.
	/// </summary>
	///  <returns></returns>
    public bool Exists => System.IO.File.Exists(filePath) || Directory.Exists(filePath);

    /// <summary>
    ///  Get the absolute path on the physical disk. For C# this is the same as the filepath.
	/// </summary>
	///  <returns>The absolute path.</returns>
    public string AbsolutePath => filePath;

    /// <summary>
    ///  Get the name of this file or directory.
	/// </summary>
	///  <returns>The name of this file or directory.</returns>
    public string BaseName => new FileInfo(filePath).Name;

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The stream to read from.</returns>
    ///  <exception cref="FileNotFoundException"></exception>
    public Stream GetInputStream()
    {
        return System.IO.File.Open(filePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
    }

    /// <summary>
    ///  Get a stream for writing to this file.
	/// </summary>
	///  <returns>The stream to write to.</returns>
    ///  <exception cref="FileNotFoundException"></exception>
    public Stream GetOutputStream()
    {
        return System.IO.File.Open(filePath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite);
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
            DotNetFile parent = new DotNetFile(dirPath);
            return parent;
        }
    }

    /// <summary>
    ///  Get the path of this file. For C# this is the same as the absolute filepath.
	/// </summary>
	///  <returns></returns>
    public string Path => filePath;

    /// <summary>
    ///  True if this is a directory.
	/// </summary>
	///  <returns></returns>
    public bool IsDirectory => Directory.Exists(filePath) && System.IO.File.GetAttributes(filePath).HasFlag(FileAttributes.Directory);

    /// <summary>
    ///  True if this is a file.
	/// </summary>
	///  <returns></returns>
    public bool IsFile => !IsDirectory;

    /// <summary>
    ///  Get the last modified date on disk.
	/// </summary>
	///  <returns></returns>
    public long LastModified => new DateTimeOffset(new FileInfo(filePath).LastWriteTime).ToUnixTimeMilliseconds();

    /// <summary>
    ///  Get the size of the file on disk.
	/// </summary>
	///  <returns></returns>
    public long Length
    {
        get
        {
            if (IsDirectory)
                return 0;
            else
                return new FileInfo(filePath).Length;
        }
    }

    /// <summary>
    ///  Get the count of files and subdirectories
	/// </summary>
	///  <returns></returns>
    public int ChildrenCount => IsDirectory?Directory.GetDirectories(filePath).Length + Directory.GetFiles(filePath).Length:0;

    /// <summary>
    ///  List all files under this directory.
	/// </summary>
	///  <returns>The list of files.</returns>
    public IRealFile[] ListFiles()
    {
        IList<DotNetFile> children = new List<DotNetFile>();

        string[] dirs = Directory.GetDirectories(filePath);
        foreach (string dir in dirs)
        {
            children.Add(new DotNetFile(dir));
        }

        string[] files = Directory.GetFiles(filePath);
        foreach (string file in files)
        {
            children.Add(new DotNetFile(file));
        }
        return children.ToArray();
    }

    /// <summary>
    ///  Move this file or directory under a new directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="progressListener">Observer to notify when progress changes.</param>
    ///  <returns>The moved file. Use this file for subsequent operations instead of the original.</returns>
    public IRealFile Move(IRealFile newDir, string newName = null, OnProgressListener progressListener = null)
    {
        newName = newName ?? BaseName;
        string nFilePath = newDir.AbsolutePath + System.IO.Path.DirectorySeparatorChar + newName;
        if (IsDirectory)
            System.IO.Directory.Move(filePath, nFilePath);
        else if (IsFile)
            System.IO.File.Move(filePath, nFilePath);
        return new DotNetFile(nFilePath);
    }

    /// <summary>
    ///  Move this file or directory under a new directory.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="newName">The new file name</param>
    ///  <param name="progressListener">Observer to notify when progress changes.</param>
    ///  <returns>The copied file. Use this file for subsequent operations instead of the original.</returns>
    ///  <exception cref="IOException"></exception>
    public IRealFile Copy(IRealFile newDir, string newName = null, OnProgressListener progressListener = null)
    {
        newName = newName ?? BaseName;
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exists");
        IRealFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");
        if (IsDirectory)
        {
            return newDir.CreateDirectory(newName);
        }
        else
        {
            newFile = newDir.CreateFile(newName);
            bool res = IRealFile.CopyFileContents(this, newFile, false, progressListener);
            return res ? newFile : null;
        }
    }

    /// <summary>
    ///  Get the file or directory under this directory with the provided name.
	/// </summary>
	///  <param name="filename">The name of the file or directory.</param>
    ///  <returns></returns>
    public IRealFile GetChild(string filename)
    {
        if (IsFile)
            return null;
        DotNetFile child = new DotNetFile(filePath + System.IO.Path.DirectorySeparatorChar + filename);
        return child;
    }

    /// <summary>
    ///  Rename the current file or directory.
	/// </summary>
	///  <param name="newFilename">The new name for the file or directory.</param>
    ///  <returns>True if successfully renamed.</returns>
    public bool RenameTo(string newFilename)
    {
        string newFilepath = Parent.Path + System.IO.Path.DirectorySeparatorChar + newFilename;
        if (IsDirectory)
            Directory.Move(filePath, newFilepath);
        else
            System.IO.File.Move(filePath, newFilepath);
        filePath = newFilepath;
        return true;
    }

    /// <summary>
    ///  Create this directory under the current filepath.
	/// </summary>
	///  <returns>True if created.</returns>
    public bool Mkdir()
    {
        Directory.CreateDirectory(filePath);
        return true;
    }

    /// <summary>
    /// Returns a string representation of this object
    /// </summary>
    /// <returns></returns>
    override
    public string ToString()
    {
        return filePath;
    }
}
