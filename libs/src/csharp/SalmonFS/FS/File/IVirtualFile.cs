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
using System.IO;
using static Mku.FS.File.IFile;

namespace Mku.FS.File;

/// <summary>
/// A virtual file. Read-only operations are included. Since write operations can be implementation
/// specific ie for encryption they can be implemented by extending this class.
/// </summary>
public abstract class IVirtualFile
{
    /// <summary>
    ///  Opens a Stream that will be used for reading the file contents.
	/// </summary>
	///  <returns>The input stream</returns>
    public abstract Stream GetInputStream();


    /// <summary>
    ///  Opens a stream for writing contents.
    /// </summary>
    ///  <returns>The output stream</returns>
    public abstract Stream GetOutputStream();

    /// <summary>
    ///  Lists files and directories under this directory
    /// </summary>
    /// <returns>An array of files and subdirectories</returns>
    public abstract IVirtualFile[] ListFiles();

    /// <summary>
    /// Get a file/subdirectory with this filename.
    /// </summary>
    /// <param name="filename">The filename to match</param>
    /// <returns>The file/subdirectory</returns>
    public abstract IVirtualFile GetChild(string filename);

    /// <summary>
    ///  True if this is a file
    /// </summary>
    public abstract bool IsFile { get; }

    /// <summary>
    ///  True if this is a directory
    /// </summary>
    public abstract bool IsDirectory { get; }

    /// <summary>
    ///  The path of the real file stored
    /// </summary>
    public abstract string Path { get; }

    /// <summary>
    ///  The path of the real file
    /// </summary>
    public abstract string RealPath { get; }

    /// <summary>
    /// The real encrypted file on the physical disk.
    /// </summary>
    public abstract IFile RealFile { get; protected set; }

    /// <summary>
    ///  The base name for the file
    /// </summary>
    public abstract string Name { get; }

    /// <summary>
    ///  Returns the virtual parent directory
    /// </summary>
    public abstract IVirtualFile Parent { get; }

    /// <summary>
    ///  Delete this file.
    /// </summary>
    public abstract void Delete();

    /// <summary>
    ///  Create this directory
    /// </summary>
    public abstract void Mkdir();

    /// <summary>
    ///  The last date modified in milliseconds
    /// </summary>
    public abstract long LastDateModified { get; }

    /// <summary>
    ///  The virtual size of the file excluding the header and hash signatures.
    /// </summary>
    public abstract long Length { get; }

    /// <summary>
    ///  True if this file/directory exists
    /// </summary>
    public abstract bool Exists { get; }

    /// <summary>
    ///  Creates a directory under this directory
	/// </summary>
	///  <param name="dirName">The name of the directory to be created</param>
    public abstract IVirtualFile CreateDirectory(string dirName);

    /// <summary>
    ///  Create a file under this directory
	/// </summary>
	///  <param name="realFilename">The real file name of the file</param>
    public abstract IVirtualFile CreateFile(string realFilename);

    /// <summary>
    ///  Rename the virtual file name
    /// </summary>
    ///  <param name="newFilename">The new filename this file will be renamed to</param>
    public abstract void Rename(string newFilename);

    /// <summary>
    ///  Move file to another directory.
    /// </summary>
    ///  <param name="dir">Target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The moved file</returns>
    public abstract IVirtualFile Move(IVirtualFile dir, MoveOptions options = null);

    /// <summary>
    ///  Copy a file to another directory.
	/// </summary>
	///  <param name="dir">Target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The new file</returns>
    public abstract IVirtualFile Copy(IVirtualFile dir, CopyOptions options = null);

    /// <summary>
    /// Move a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="options">The options</param>
    public abstract void MoveRecursively(IVirtualFile dest, VirtualRecursiveMoveOptions options = null);

    /// <summary>
    /// Copy a directory recursively
    /// </summary>
    /// <param name="dest">The destination directory</param>
    /// <param name="options">The options</param>
    public abstract void CopyRecursively(IVirtualFile dest, VirtualRecursiveCopyOptions options = null);

    /// <summary>
    /// Delete a directory recursively
    /// </summary>
    /// <param name="options">The options</param>
    public abstract void DeleteRecursively(VirtualRecursiveDeleteOptions options = null);


    /// <summary>
    ///  Directory copy options (recursively)
    /// </summary>
    public class VirtualRecursiveCopyOptions
    {
        /// <summary>
        ///  Callback when file with same name exists
        /// </summary>
        public Func<IVirtualFile, string> autoRename;

        /// <summary>
        ///  True to autorename folders
        /// </summary>
        public bool autoRenameFolders = false;

        /// <summary>
        ///  Callback when file changes
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        ///  Callback where progress changed
        /// </summary>
        public Action<IVirtualFile, long, long> onProgressChanged;
    }

    /// <summary>
    ///  Directory move options (recursively)
    /// </summary>
    public class VirtualRecursiveMoveOptions
    {
        /// <summary>
        ///  Callback when file with the same name exists
        /// </summary>
        public Func<IVirtualFile, string> autoRename;

        /// <summary>
        ///  True to autorename folders
        /// </summary>
        public bool autoRenameFolders = false;

        /// <summary>
        ///  Callback when file failed
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        ///  Callback when progress changes
        /// </summary>
        public Action<IVirtualFile, long, long> onProgressChanged;
    }

    /// <summary>
    ///  Directory move options (recursively)
    /// </summary>
    public class VirtualRecursiveDeleteOptions
    {
        /// <summary>
        ///  Callback when file failed
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        ///  Callback when progress changed
        /// </summary>
        public Action<IVirtualFile, long, long> onProgressChanged;
    }
}
