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
using static Mku.FS.File.IFile;
using Mku.Streams;
using FileStream = Mku.FS.Streams.FileStream;

namespace Mku.FS.File;

/// <summary>
///  Salmon RealFile implementation for C#.
/// </summary>
public class File : IFile
{
	/// <summary>
    /// Directory separator.
    /// </summary>
	public static readonly string Separator = System.IO.Path.DirectorySeparatorChar.ToString();
	
    private string filePath;

    /// <summary>
    ///  Instantiate a real file represented by the filepath provided.
	/// </summary>
	///  <param name="path">The filepath.</param>
    public File(string path)
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
        string nDirPath = filePath + System.IO.Path.DirectorySeparatorChar + dirName;
        Directory.CreateDirectory(nDirPath);
        File dotNetDir = new File(nDirPath);
        return dotNetDir;
    }

    /// <summary>
    ///  Create a file under this directory.
	/// </summary>
	///  <param name="filename">The name of the new file.</param>
    ///  <returns>The newly created file.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile CreateFile(string filename)
    {
        string nFilePath = filePath + File.Separator + filename;
        System.IO.File.Create(nFilePath).Close();
        File dotNetFile = new File(nFilePath);
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
            IFile[] files = ListFiles();
            foreach (IFile file in files)
            {
                if (file.IsDirectory)
                    Directory.Delete(file.DisplayPath);
                else
                    System.IO.File.Delete(file.DisplayPath);
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
	///  <returns>True if file/directory exists</returns>
    public bool Exists => System.IO.File.Exists(filePath) || Directory.Exists(filePath);

    /// <summary>
    ///  Get the absolute path on the physical disk. For C# this is the same as the filepath.
	/// </summary>
	///  <returns>The absolute path.</returns>
    public string DisplayPath => filePath;

    /// <summary>
    ///  Get the name of this file or directory.
	/// </summary>
	///  <returns>The name of this file or directory.</returns>
    public string Name => new FileInfo(filePath).Name;

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The stream to read from.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetInputStream()
    {
        return new FileStream(this, FileAccess.Read);
    }

    /// <summary>
    ///  Get a stream for writing to this file.
	/// </summary>
	///  <returns>The stream to write to.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetOutputStream()
    {
        return new FileStream(this, FileAccess.Write);
    }

    /// <summary>
    ///  Get the parent directory of this file or directory.
	/// </summary>
	///  <returns>The parent directory.</returns>
    public IFile Parent
    {
        get
        {
            string dirPath = Directory.GetParent(filePath).FullName;
            File parent = new File(dirPath);
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
    public bool IsDirectory => Directory.Exists(filePath) && System.IO.File.GetAttributes(filePath).HasFlag(FileAttributes.Directory);

    /// <summary>
    ///  True if this is a file.
	/// </summary>
	///  <returns>True if file</returns>
    public bool IsFile => System.IO.File.Exists(filePath);

    /// <summary>
    ///  Get the last modified date on disk.
	/// </summary>
	///  <returns>The last modified date</returns>
    public long LastDateModified => new DateTimeOffset(new FileInfo(filePath).LastWriteTime).ToUnixTimeMilliseconds();

    /// <summary>
    ///  Get the size of the file on disk.
	/// </summary>
	///  <returns>The length</returns>
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
	///  <returns>The children count</returns>
    public int ChildrenCount => IsDirectory?Directory.GetDirectories(filePath).Length + Directory.GetFiles(filePath).Length:0;

    /// <summary>
    ///  List all files under this directory.
	/// </summary>
	///  <returns>The list of files.</returns>
    public IFile[] ListFiles()
    {
        IList<File> children = new List<File>();

        string[] dirs = Directory.GetDirectories(filePath);
        foreach (string dir in dirs)
        {
            children.Add(new File(dir));
        }

        string[] files = Directory.GetFiles(filePath);
        foreach (string file in files)
        {
            children.Add(new File(file));
        }
        return children.ToArray();
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
        string nFilePath = newDir.DisplayPath + File.Separator + newName;
        if (options.onProgressChanged != null)
            options.onProgressChanged(0L, this.Length);
        if (IsDirectory)
            System.IO.Directory.Move(filePath, nFilePath);
        else if (IsFile)
            System.IO.File.Move(filePath, nFilePath);
        if (options.onProgressChanged != null)
            options.onProgressChanged(newFile.Length, newFile.Length);
        return new File(nFilePath);
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
            throw new IOException("Target directory does not exists");
        IFile newFile = newDir.GetChild(newName);
        if (newFile != null && newFile.Exists)
            throw new IOException("Another file/directory already exists");
        if (IsDirectory)
        {
            return newDir.CreateDirectory(newName);
        }
        else
        {
            newFile = newDir.CreateFile(newName);
            CopyContentsOptions copyContentOptions = new CopyContentsOptions();
            copyContentOptions.onProgressChanged = options.onProgressChanged;
            bool res = IFile.CopyFileContents(this, newFile, copyContentOptions);
            return res ? newFile : null;
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
        File child = new File(filePath + File.Separator + filename);
        return child;
    }

    /// <summary>
    ///  Rename the current file or directory.
	/// </summary>
	///  <param name="newFilename">The new name for the file or directory.</param>
    ///  <returns>True if successfully renamed.</returns>
    public bool RenameTo(string newFilename)
    {
        string newFilepath = Parent.Path + File.Separator + newFilename;
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
    ///  Reset cached properties 
    /// </summary>
    public void Reset()
    {
        
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
}
