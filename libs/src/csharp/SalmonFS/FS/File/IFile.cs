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
using Mku.Streams;
using System;
using System.IO;

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
    RandomAccessStream GetInputStream();

    /// <summary>
    ///  Get a stream for writing to the file.
	/// </summary>
	///  <returns>The output stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    RandomAccessStream GetOutputStream();

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
    long LastDateModified { get; }

    /// <summary>
    ///  Get the display path of the file on disk.
	/// </summary>
	///  <returns>The display path</returns>
    string DisplayPath { get; }

    /// <summary>
    ///  Get the original filepath of this file. This might symlinks or merged folders. To get the display path
    ///  use <see cref="DisplayPath"/>
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
    string Name { get; }

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
    ///  <param name="options">The options</param>
    ///  <returns>The file after the move. Use this instance for any subsequent file operations.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    IFile Move(IFile newDir, MoveOptions options = null);

    /// <summary>
    ///  Copy this file or directory recursively to another directory.
    /// </summary>
    ///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The file after the copy. Use this instance for any subsequent file operations.</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    IFile Copy(IFile newDir, CopyOptions options = null);

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
    /// Get the service credentials.
    /// </summary>
    Credentials ServiceCredentials { get; }

    /// <summary>
    /// Copy contents of a file to another file.
    /// </summary>
    /// <param name="src">The source file</param>
    /// <param name="dest">The destination file</param>
    /// <param name="options">The options</param>
    /// <returns>True if success</returns>
    public static bool CopyFileContents(IFile src, IFile dest, CopyContentsOptions options)
    {
        RandomAccessStream source = src.GetInputStream();
        RandomAccessStream target = dest.GetOutputStream();
        try
        {
            source.CopyTo(target, options.onProgressChanged);
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
        return true;
    }

    /// <summary>
    /// Copy a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="options">The options</param>
    public sealed void CopyRecursively(IFile dest, RecursiveCopyOptions options = null)
    {
        if (options == null)
            options = new RecursiveCopyOptions();
        string newFilename = Name;
        IFile newFile;
        newFile = dest.GetChild(newFilename);
        if (IsFile)
        {
            if (newFile != null && newFile.Exists)
            {
                if (options.autoRename != null)
                {
                    newFilename = options.autoRename(this);
                }
                else
                {
                    if (options.onFailed != null)
                        options.onFailed(this, new Exception("Another directory/file exists"));
                    return;
                }
            }
            RecursiveCopyOptions finalOptions = options;
            CopyOptions copyOptions = new CopyOptions();
            copyOptions.newFilename = newFilename;
            copyOptions.onProgressChanged = (position, length) =>
            {
                if (options.onProgressChanged != null)
                {
                    options.onProgressChanged(this, position, length);
                }
            };
            this.Copy(dest, copyOptions);
        }
        else if (this.IsDirectory)
        {
            if (options.onProgressChanged != null)
                options.onProgressChanged(this, 0, 1);
            if (dest.DisplayPath.StartsWith(this.DisplayPath))
            {
                if (options.onProgressChanged != null)
                    options.onProgressChanged(this, 1L, 1L);
                return;
            }
			
            if (newFile != null && newFile.Exists && options.autoRename != null && options.autoRenameFolders)
                newFile = dest.CreateDirectory(options.autoRename(this));
            else if (newFile == null || !newFile.Exists)
                newFile = dest.CreateDirectory(newFilename);
			else if (newFile != null && newFile.Exists && newFile.IsFile) {
				if (options.onFailed != null)
					options.onFailed(this, new Exception("Another file exists"));
				return;
			}
			
            if (options.onProgressChanged != null)
                options.onProgressChanged(this, 1, 1);

            foreach (IFile child in this.ListFiles())
            {
                child.CopyRecursively(newFile, options);
            }
        }
    }

    /// <summary>
    /// Move a directory recursively
    /// </summary>
    /// <param name="dest">The directory to move to</param>
    /// <param name="options">The options</param>
    public sealed void MoveRecursively(IFile dest, RecursiveMoveOptions options = null)
    {
        if (options == null)
            options = new RecursiveMoveOptions();
        // target directory is the same
        if (Parent.Path.Equals(dest.Path))
        {
            if (options.onProgressChanged != null)
            {
                options.onProgressChanged(this, 0, 1);
                options.onProgressChanged(this, 1, 1);
            }
            return;
        }

        string newFilename = Name;
        IFile newFile;
        newFile = dest.GetChild(newFilename);
        if (IsFile)
        {
            if (newFile != null && newFile.Exists)
            {
                if (newFile.Path.Equals(this.Path))
                    return;
                if (options.autoRename != null)
                {
                    newFilename = options.autoRename(this);
                }
                else
                {
                    if (options.onFailed != null)
                        options.onFailed(this, new Exception("Another directory/file exists"));
                    return;
                }
            }
            RecursiveMoveOptions finalOptions = options;
            MoveOptions moveOptions = new MoveOptions();
            moveOptions.newFilename = newFilename;
            moveOptions.onProgressChanged = (position, length) =>
            {
                if (options.onProgressChanged != null)
                {
                    options.onProgressChanged(this, position, length);
                }
            };
            this.Move(dest, moveOptions);
        }
        else if (this.IsDirectory)
        {
            if (options.onProgressChanged != null)
                options.onProgressChanged(this, 0, 1);
            
            if (dest.DisplayPath.StartsWith(this.DisplayPath))
            {
                if (options.onProgressChanged != null)
                    options.onProgressChanged(this, 1L, 1L);
                return;
            }
			
            if (newFile != null && newFile.Exists && options.autoRename != null && options.autoRenameFolders)
                newFile = dest.CreateDirectory(options.autoRename(this));
            else if (newFile == null || !newFile.Exists)
                newFile = dest.CreateDirectory(newFilename);
			else if (newFile != null && newFile.Exists && newFile.IsFile) {
				if (options.onFailed != null)
					options.onFailed(this, new Exception("Another file exists"));
				return;
			}
			
            if (options.onProgressChanged != null)
                options.onProgressChanged(this, 1, 1);

            foreach (IFile child in this.ListFiles())
            {
                child.MoveRecursively(newFile, options);
            }
            if (!this.Delete())
            {
                options.onFailed(this, new Exception("Could not delete source directory"));
                return;
            }
        }
    }

    /// <summary>
    /// Delete a directory recursively
    /// </summary>
    /// <param name="options">The options</param>
    public sealed void DeleteRecursively(RecursiveDeleteOptions options = null)
    {
        if (options == null)
            options = new RecursiveDeleteOptions();
        if (IsFile)
        {
            options.onProgressChanged(this, 0, 1);
            if (!this.Delete())
            {
                if (options.onFailed != null)
                    options.onFailed(this, new Exception("Could not delete file"));
            }
            options.onProgressChanged(this, 1, 1);
        }
        else if (this.IsDirectory)
        {
            foreach (IFile child in this.ListFiles())
            {
                child.DeleteRecursively(options);
            }
            if (!this.Delete())
            {
                if (options.onFailed != null)
                    options.onFailed(this, new Exception("Could not delete directory"));
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
        return AutoRename(file.Name);
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
		string newFilename = filenameNoExt;
		int index = newFilename.LastIndexOf(" (");
		if (index >= 0)
			newFilename = newFilename.Substring(0,index);
		string copySuffix = DateTime.Now.ToString("HHmmssfff") 
			+ (int) Math.Round(new Random().NextDouble()*100);
        newFilename += " (" + copySuffix + ")";
        if (ext.Length > 0)
            newFilename += "." + ext;
        return newFilename;
    }

    /// <summary>
    /// File copy options
    /// </summary>
    public class CopyOptions
    {
        /// <summary>
        /// Override filename
        /// </summary>
        public string newFilename;

        /// <summary>
        /// Callback where progress changed
        /// </summary>
        public Action<long, long> onProgressChanged;
    }

    /// <summary>
    /// File move options
    /// </summary>
    public class MoveOptions
    {
        /// <summary>
        /// Override filename
        /// </summary>
        public string newFilename;

        /// <summary>
        /// Callback where progress changed
        /// </summary>
        public Action<long, long> onProgressChanged;
    }

    /// <summary>
    /// Directory copy options (recursively)
    /// </summary>
    public class RecursiveCopyOptions
    {
        /// <summary>
        /// Callback when file with same name exists
        /// </summary>
        public Func<IFile, string> autoRename;

        /// <summary>
        /// True to autorename folders
        /// </summary>
        public bool autoRenameFolders = false;

        /// <summary>
        /// Callback when file changes
        /// </summary>
        public Action<IFile, Exception> onFailed;

        /// <summary>
        /// Callback where progress changed
        /// </summary>
        public Action<IFile, long, long> onProgressChanged;
    }

    /// <summary>
    /// Directory move options (recursively)
    /// </summary>
    public class RecursiveMoveOptions
    {
        /// <summary>
        /// Callback when file with the same name exists
        /// </summary>
        public Func<IFile, string> autoRename;

        /// <summary>
        /// True to autorename folders
        /// </summary>
        public bool autoRenameFolders = false;

        /// <summary>
        /// Callback when file failed
        /// </summary>
        public Action<IFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changes
        /// </summary>
        public Action<IFile, long, long> onProgressChanged;
    }

    /// <summary>
    /// Directory move options (recursively)
    /// </summary>
    public class RecursiveDeleteOptions
    {
        /// <summary>
        /// Callback when file failed
        /// </summary>
        public Action<IFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changed
        /// </summary>
        public Action<IFile, long, long> onProgressChanged;
    }

    /// <summary>
    /// Directory move options (recursively)
    /// </summary>
    public class CopyContentsOptions
    {
        /// <summary>
        /// Callback when progress changed
        /// </summary>
        public Action<long, long> onProgressChanged;
    }
}

