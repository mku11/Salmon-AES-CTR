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

using Mku.FS.Drive.Utils;
using System;
using System.IO;
using static Mku.Streams.RandomAccessStreamExtensions;

namespace Mku.FS.File;

/// <summary>
///  Interface that represents a real file. This class is used internally by the virtual disk to
///  import, store, and export the encrypted files.
///  Extend this to provide an interface to any file system, platform, or API ie: on disk, memory, network, or cloud.
///  <para>See: <see cref="File"/></para>
/// </summary>
public interface IFile
{
    /// <summary>
    ///  True if this file exists.
	/// </summary>
	///  <returns>True if exists</returns>
    bool Exists { get; }

    /// <summary>
    ///  Delete this file.
	/// </summary>
	///  <returns>True if file deleted</returns>
    bool Delete();

    /// <summary>
    ///  Get a stream for reading the file.
	/// </summary>
	///  <returns>The input stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    Stream GetInputStream();

    /// <summary>
    ///  Get a stream for writing to the file.
	/// </summary>
	///  <returns>The output stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    Stream GetOutputStream();

    /// <summary>
    ///  Rename file.
	/// </summary>
	///  <param name="newFilename">The new filename</param>
    ///  <returns>True if success.</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    bool RenameTo(string newFilename);

    /// <summary>
    ///  Get the length for the file.
	/// </summary>
	///  <returns>The length.</returns>
    long Length { get; }

    /// <summary>
    ///  Get the count of files and subdirectories
	/// </summary>
	///  <returns>The length.</returns>
    int ChildrenCount { get; }

    /// <summary>
    ///  Get the last modified date of the file.
	/// </summary>
	///  <returns>The last modified date in milliseconds</returns>
    long LastModified { get; }

    /// <summary>
    ///  Get the absolute path of the file on disk.
	/// </summary>
	///  <returns>The absolute path</returns>
    string AbsolutePath { get; }

    /// <summary>
    ///  Get the original filepath of this file. This might symlinks or merged folders. To get the absolute path
    ///  use <see cref="AbsolutePath"/>
	/// </summary>
	///  <returns>The path</returns>
    string Path { get; }

    /// <summary>
    ///  True if this is a file.
	/// </summary>
	///  <returns>True if file</returns>
    bool IsFile { get; }

    /// <summary>
    ///  True if this is a directory.
	/// </summary>
	///  <returns>True if directory</returns>
    bool IsDirectory { get; }

    /// <summary>
    ///  Get all files and directories under this directory.
	/// </summary>
	///  <returns>The files and subdirectories</returns>
    IFile[] ListFiles();

    /// <summary>
    ///  Get the basename of the file.
	/// </summary>
	///  <returns>The base name</returns>
    string BaseName { get; }

    /// <summary>
    ///  Create the directory with the name provided under this directory.
	/// </summary>
	///  <param name="dirName">Directory name.</param>
    ///  <returns>The newly created directory.</returns>
    IFile CreateDirectory(string dirName);

    /// <summary>
    ///  Get the parent directory of this file/directory.
	/// </summary>
	///  <returns>The parent directory.</returns>
    IFile Parent { get; }


    /// <summary>
    ///  Create an empty file with the provided name.
	/// </summary>
	///  <param name="filename">The name for the new file.</param>
    ///  <returns>The newly create file.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    IFile CreateFile(string filename);

    /// <summary>
    ///  Move this file or directory recursively to another directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="newName">The new file name</param>
    ///  <param name="progressListener">Observer to notify of the move progress.</param>
    ///  <returns>The file after the move. Use this instance for any subsequent file operations.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    IFile Move(IFile newDir, string newName = null, Action<long, long> progressListener = null);

    /// <summary>
    ///  Copy this file or directory recursively to another directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="newName">The new file name</param>
    ///  <param name="progressListener">Observer to notify of the copy progress.</param>
    ///  <returns>The file after the copy. Use this instance for any subsequent file operations.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    IFile Copy(IFile newDir, string newName = null, Action<long,long> progressListener = null);

    /// <summary>
    ///  Get the file/directory matching the name provided under this directory.
	/// </summary>
	///  <param name="filename">The name of the file or directory to match.</param>
    ///  <returns>The file that was matched.</returns>
    IFile GetChild(string filename);

    /// <summary>
    ///  Create a directory with the current filepath.
	/// </summary>
	///  <returns>True if directory created</returns>
    bool Mkdir();

    /// <summary>
    ///  Reset cached properties 
	/// </summary>
    void Reset();

    /// <summary>
    /// Copy contents of a file to another file.
    /// </summary>
    /// <param name="src">The source file</param>
    /// <param name="dest">The destination file</param>
    /// <param name="delete">True to delete the source file on success</param>
    /// <param name="progressListener">The progress listener</param>
    /// <returns>True if success</returns>
    public static bool CopyFileContents(IFile src, IFile dest, bool delete, Action<long,long> progressListener)
    {
        Stream source = src.GetInputStream();
        Stream target = dest.GetOutputStream();
        try
        {
            source.CopyTo(target, progressListener);
        }
        catch (Exception)
        {
            dest.Delete();
            return false;
        }
        finally
        {
            source.Close();
            target.Close();
        }
        if (delete)
            src.Delete();
        return true;
    }

    /// <summary>
    /// Copy a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="AutoRename">The autorename function to use when renaming files if they exist</param>
    /// <param name="autoRenameFolders">Apply autorename to folders also (default is true)</param>
    /// <param name="OnFailed">Callback when copy fails</param>
    public sealed void CopyRecursively(IFile dest,
        Action<IFile, long, long> progressListener = null,
        Func<IFile, string> AutoRename = null,
        bool autoRenameFolders = true,
        Action<IFile, Exception> OnFailed = null)
    {
        string newFilename = BaseName;
        IFile newFile;
        newFile = dest.GetChild(newFilename);
        if (IsFile)
        {
            if (newFile != null && newFile.Exists)
            {
                if (AutoRename != null)
                {
                    newFilename = AutoRename(this);
                }
                else
                {
                    if (OnFailed != null)
                        OnFailed(this, new Exception("Another file exists"));
                    return;
                }
            }
            this.Copy(dest, newFilename, (position, length) =>
            {
                if (progressListener != null)
                {
                    progressListener(this, position, length);
                }
            });
        }
        else if (this.IsDirectory)
        {
            if (progressListener != null)
                progressListener(this, 0, 1);
            if (dest.AbsolutePath.StartsWith(this.AbsolutePath))
            {
                if (progressListener != null)
                    progressListener(this, 1L, 1L);
                return;
            }
            if (newFile != null && newFile.Exists && AutoRename != null && autoRenameFolders)
                newFile = dest.CreateDirectory(AutoRename(this));
            else if (newFile == null || !newFile.Exists)
                newFile = dest.CreateDirectory(newFilename);
            if (progressListener != null)
                progressListener(this, 1, 1);

            foreach (IFile child in this.ListFiles())
            {
                child.CopyRecursively(newFile, progressListener, AutoRename, autoRenameFolders, OnFailed);
            }
        }
    }

    /// <summary>
    /// Move a directory recursively
    /// </summary>
    /// <param name="dest">The directory to move to</param>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="AutoRename">The autorename function to use when renaming files if they exist</param>
    /// <param name="autoRenameFolders">Apply autorename to folders also (default is true)</param>
    /// <param name="OnFailed">Callback when move fails</param>
    public sealed void MoveRecursively(IFile dest,
        Action<IFile, long, long> progressListener = null, Func<IFile, string> AutoRename = null,
        bool autoRenameFolders = true, Action<IFile, Exception> OnFailed = null)
    {
        // target directory is the same
        if (Parent.Path.Equals(dest.Path))
        {
            if (progressListener != null)
            {
                progressListener(this, 0, 1);
                progressListener(this, 1, 1);
            }
            return;
        }

        string newFilename = BaseName;
        IFile newFile;
        newFile = dest.GetChild(newFilename);
        if (IsFile)
        {
            if (newFile != null && newFile.Exists)
            {
                if (newFile.Path.Equals(this.Path))
                    return;
                if (AutoRename != null)
                {
                    newFilename = AutoRename(this);
                }
                else
                {
                    if (OnFailed != null)
                        OnFailed(this, new Exception("Another file exists"));
                    return;
                }
            }
            this.Move(dest, newFilename, (position, length) =>
            {
                if (progressListener != null)
                {
                    progressListener(this, position, length);
                }
            });
        }
        else if (this.IsDirectory)
        {
            if (progressListener != null)
                progressListener(this, 0, 1);
            if ((newFile != null && newFile.Exists && AutoRename != null && autoRenameFolders)
                || newFile == null || !newFile.Exists)
            {
                newFile = Move(dest, AutoRename(this));
                return;
            }
            if (progressListener != null)
                progressListener(this, 1, 1);

            foreach (IFile child in this.ListFiles())
            {
                child.MoveRecursively(newFile, progressListener, AutoRename, autoRenameFolders, OnFailed);
            }
            if (!this.Delete())
            {
                OnFailed(this, new Exception("Could not delete source directory"));
                return;
            }
        }
    }

    /// <summary>
    /// Delete a directory recursively
    /// </summary>
    /// <param name="progressListener">The progress listener</param>
    /// <param name="OnFailed">Callback when delete fails</param>
    public sealed void DeleteRecursively(Action<IFile, long, long> progressListener = null,
        Action<IFile, Exception> OnFailed = null)
    {
        if (IsFile)
        {
            progressListener(this, 0, 1);
            if (!this.Delete())
            {
                OnFailed(this, new Exception("Could not delete file"));
                return;
            }
            progressListener(this, 1, 1);
        }
        else if (this.IsDirectory)
        {
            foreach (IFile child in this.ListFiles())
            {
                child.DeleteRecursively(progressListener, OnFailed);
            }
            if (!this.Delete())
            {
                OnFailed(this, new Exception("Could not delete directory"));
                return;
            }
        }
    }

    /// <summary>
    /// Get an auto generated copy of the name for a file.
    /// </summary>
    /// <param name="file">The file</param>
    /// <returns>The new file name</returns>
    public static string AutoRename(IFile file)
    {
        return AutoRename(file.BaseName);
    }

    /// <summary>
    /// Get an auto generated copy of a filename
    /// </summary>
    /// <param name="filename">The file name</param>
    /// <returns>The new file name</returns>
    public static string AutoRename(string filename)
    {
        string ext = FileUtils.GetExtensionFromFileName(filename);
        string filenameNoExt;
        if (ext.Length > 0)
            filenameNoExt = filename.Substring(0, filename.Length - ext.Length - 1);
        else
            filenameNoExt = filename;
        string newFilename = filenameNoExt + " (" + DateTime.Now.ToString("HHmmssfff") + ")";
        if (ext.Length > 0)
            newFilename += "." + ext;
        return newFilename;
    }
}

