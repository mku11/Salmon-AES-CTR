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

using Uri = Android.Net.Uri;

using AndroidX.DocumentFile.Provider;
using Android.OS;
using Android.Provider;
using System.IO;
using System;
using System.Collections.Generic;
using Mku.FS.File;
using Mku.Android.FS.Streams;
using Mku.Streams;

namespace Mku.Android.FS.File;

/// <summary>
///  Implementation of the IFile for Android using Storage Access Framework that supports read/write to external SD cards.
///  This class is used by the AndroidDrive implementation so you can use AesFile wrapper transparently
/// </summary>
///
public class AndroidFile : IFile
{
    /// <summary>
    /// Directory separator.
    /// </summary>
    public static readonly string Separator = "/";
	
	/// <summary>
	/// Uri separator
	/// </summary>
	public static readonly string UriSeparator = "%2F";

    public DocumentFile DocumentFile { get; private set; }
    private AndroidFile parent;
    private string name;

    // the DocumentFile interface can be slow so we cache some attrs
    private string _basename = null;
    private long? _length;
    private long? _lastModified;
    private int? _childrenCount;
    private bool? _isDirectory;
    private bool? _isFile;

    /// <summary>
    /// Get the service credentials.
    /// </summary>
    public Credentials ServiceCredentials { get; set; }

    /// <summary>
    /// Construct an AndroidFile wrapper from an Android DocumentFile.
    /// </summary>
    /// <param name="DocumentFile">The Android DocumentFile that will be associated to</param>
    /// <param name="parent">The parent if available</param>
    protected AndroidFile(DocumentFile DocumentFile, AndroidFile parent)
    {
        this.DocumentFile = DocumentFile;
        this.parent = parent;
    }

    /// <summary>
    /// Construct an AndroidFile.
    /// </summary>
    /// <param name="parent">The parent file.</param>
    /// <param name="name">The file name.</param>
    protected AndroidFile(AndroidFile parent, String name)
    {
        this.parent = parent;
        this.name = name;
    }

    /// <summary>
    ///  Construct an AndroidFile from an Android DocumentFile.
    /// </summary>
    ///  <param name="DocumentFile">The Android DocumentFile that will be associated to</param>
    public AndroidFile(DocumentFile DocumentFile)
    {
        this.DocumentFile = DocumentFile;
    }

    public IFile CreateDirectory(string dirName)
    {
        DocumentFile dir = DocumentFile.CreateDirectory(dirName);
        if (dir == null)
            return null;
        Reset();
        AndroidFile newDir = new AndroidFile(dir, this);
        return newDir;

    }

    public IFile CreateFile(string filename)
    {
        DocumentFile doc = DocumentFile.CreateFile("*/*", filename);
        // for some reason android storage access framework even though it supports auto rename
        // somehow it includes the extension. to protect that we temporarily use another extension
        doc.RenameTo(filename + ".dat");
        doc.RenameTo(filename);
        Reset();
        AndroidFile newFile = new AndroidFile(doc, this);
        return newFile;
    }

    /// <summary>
    ///  Delete this file.
	/// </summary>
	///  <returns>True if deletion is successful.</returns>
    public bool Delete()
    {
        bool res = DocumentFile.Delete();
        if (res && Parent != null)
        {
            Parent.Reset();
        }
        return res;
    }

    /// <summary>
    ///  True if file exists.
	/// </summary>
	///  <returns>True if exists</returns>
    public bool Exists => DocumentFile != null && DocumentFile.Exists();


    /// <summary>
    ///  Get the absolute path on the physical drive.
	/// </summary>
	///  <returns>The absolute path</returns>
    public string DisplayPath
    {
        get
        {
            try
            {
                string path = Uri.Decode(DocumentFile.Uri.ToString());
                int index = path.LastIndexOf(":");
                return AndroidFile.Separator + path.Substring(index + 1);
            }
            catch (Exception ex)
            {
                return DocumentFile.Uri.Path;
            }
        }
    }

    /// <summary>
    ///  Get the base name of this file.
	/// </summary>
	///  <returns>The base name</returns>
    public string Name
    {
        get
        {
            if (_basename != null)
                return _basename;
            if (DocumentFile == null)
                _basename = this.name;
            else
                _basename = DocumentFile.Name;
            return _basename;
        }
    }

    /// <summary>
    ///  Get a stream for reading.
	/// </summary>
	///  <returns>The input stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetInputStream()
    {
        Reset();
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
        return androidFileStream;
    }

    /// <summary>
    ///  Get a stream for writing.
	/// </summary>
	///  <returns>The output stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public RandomAccessStream GetOutputStream()
    {
        if (!this.Exists)
        {
            IFile parent = this.Parent;
            if (parent == null)
                throw new Java.Lang.Error("Could not get parent");
            try
            {
                AndroidFile nFile = (AndroidFile)parent.CreateFile(this.Name);
                this.DocumentFile = nFile.DocumentFile;
                this.Reset();
            }
            catch (Exception e)
            {
                throw new FileNotFoundException("Could not find file");
            }
        }
        this.Reset();
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "rw");
        return androidFileStream;
    }

    /// <summary>
    ///  Get the parent directory.
	/// </summary>
	///  <returns>The parent directory</returns>
    public IFile Parent
    {
        get
        {
            if (this.parent != null)
                return this.parent;
            DocumentFile parentDocumentFile = DocumentFile.ParentFile;
			if (parentDocumentFile == null) {
				int idx = this.Path.LastIndexOf(UriSeparator);
				if(idx >= 0) {
					string parentFilePath = this.Path.Substring(0, idx);
					try {
						parentDocumentFile = DocumentFile.FromTreeUri(AndroidFileSystem.GetContext(),
							Uri.Parse(parentFilePath));
					return new AndroidFile(parentDocumentFile, null);
				} catch (Exception ex) {
					Console.Error.WriteLine(ex);
				}
				}
			}
            return null;
        }
    }

    /// <summary>
    ///  Get the path.
	/// </summary>
	///  <returns>The path</returns>
    public string Path => DocumentFile.Uri.ToString();

    /// <summary>
    ///  True if it is a directory.
	/// </summary>
	///  <returns>True if directory</returns>
	// WORKAROUND: DocumentFile.isDirectory() is very slow so we try alternatively 
    public bool IsDirectory
    {
        get
        {
            if (_isDirectory != null)
                return (bool)_isDirectory;
            _isDirectory = DocumentFile != null && DocumentFile.IsDirectory;
            return (bool)_isDirectory;
        }
    }

    /// <summary>
    ///  True if it is a file.
	/// </summary>
	///  <returns>True if file</returns>
    public bool IsFile
    {
        get
        {
            if (_isFile != null)
                return (bool)_isFile;
            _isFile = DocumentFile != null && DocumentFile.IsFile;
            return (bool)_isFile;
        }
    }

    /// <summary>
    ///  Get the last modified time in milliseconds.
	/// </summary>
	///  <returns>The last modified date in milliseconds</returns>
    public long LastDateModified
    {
        get
        {
            if (_lastModified != null)
                return (long)_lastModified;
            _lastModified = DocumentFile != null ? DocumentFile.LastModified() : 0;
            return (long)_lastModified;
        }
    }

    /// <summary>
    ///  Get the size of the file.
	/// </summary>
	///  <returns>The length</returns>
    public long Length
    {
        get
        {
            if (_length != null)
                return (long)_length;
            _length = DocumentFile != null ? DocumentFile.Length() : 0;
            return (long)_length;
        }
    }

    /// <summary>
    ///  Get the count of files and subdirectories
	/// </summary>
	///  <returns>The children count</returns>
    public int ChildrenCount
    {
        get
        {
            if (_childrenCount != null)
                return (int)_childrenCount;
            if (IsDirectory)
                _childrenCount = DocumentFile != null ? DocumentFile.ListFiles().Length : 0;
            else
                _childrenCount = 0;
            return (int)_childrenCount;
        }
    }

    /// <summary>
    ///  List files and directories.
	/// </summary>
	///  <returns>The files and subdirectories</returns>
    public IFile[] ListFiles()
    {
        DocumentFile[] files = DocumentFile.ListFiles();
        if (files == null)
            return new AndroidFile[0];
        List<IFile> realFiles = new List<IFile>();
        List<IFile> realDirs = new List<IFile>();
        for (int i = 0; i < files.Length; i++)
        {
            AndroidFile file = new AndroidFile(files[i], this);
            if (files[i].IsDirectory)
                realDirs.Add(file);
            else
                realFiles.Add(file);
        }
        realDirs.AddRange(realFiles);
        return realDirs.ToArray();
    }

    /// <summary>
    ///  Move this file to another directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The moved file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Move(IFile newDir, IFile.MoveOptions options = null)
    {
        if (options == null)
            options = new IFile.MoveOptions();

        // target directory is the same
        if (Parent.Path.Equals(newDir.Path))
        {
            throw new Exception("Source and Target directory are the same");
        }

        AndroidFile androidDir = (AndroidFile)newDir;
        if (Build.VERSION.SdkInt >= BuildVersionCodes.N)
        {
            if (options.onProgressChanged != null)
                options.onProgressChanged(0, this.Length);
            if (options.newFilename != null)
                RenameTo(Mku.Time.Time.CurrentTimeMillis() + ".dat");

            // store the name before the move
            this.name = Name;
            Uri uri = DocumentsContract.MoveDocument(AndroidFileSystem.GetContext().ContentResolver,
                    DocumentFile.Uri, Uri.Parse(Parent.Path), androidDir.DocumentFile.Uri);
            IFile file = androidDir.GetChild(Name);
            if (file != null && options.newFilename != null)
                file.RenameTo(options.newFilename);
            if (Parent != null)
                (Parent as AndroidFile).Reset();
            androidDir.Reset();
            Reset();
            if (options.onProgressChanged != null)
                options.onProgressChanged(file.Length, file.Length);
            return file;
        }
        else
        {
            return Copy(newDir, options.newFilename, true, options.onProgressChanged);
        }
    }

    /// <summary>
    ///  Copy this file to another directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="options">The options.</param>
    ///  <returns>The new file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Copy(IFile newDir, IFile.CopyOptions options = null)
    {
        if (options == null)
            options = new IFile.CopyOptions();
        return Copy(newDir, options.newFilename, false, options.onProgressChanged);
    }

    /// <summary>
    ///  Copy to another directory
    /// </summary>
    ///  <param name="newDir">The destination directory</param>
    ///  <param name="delete">True to delete when complete</param>
    ///  <param name="onProgressChanged">The progess listener</param>
    ///  <returns>The new file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    private IFile Copy(IFile newDir, string newName = null,
        bool delete = false, Action<long, long> onProgressChanged = null)
    {
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exists");

        newName = newName ?? Name;
        IFile dir = newDir.GetChild(newName);
        if (dir != null && dir.Exists)
            throw new IOException("Target file/directory already exists");
        if (IsDirectory)
        {
            IFile file = newDir.CreateDirectory(newName);
            return file;
        }
        else
        {
            IFile newFile = newDir.CreateFile(newName);
            IFile.CopyContentsOptions copyContentOptions = new IFile.CopyContentsOptions();
            copyContentOptions.onProgressChanged = onProgressChanged;
            bool res = IFile.CopyFileContents(this, newFile, copyContentOptions);
            if (res && delete)
			{
				if(!this.Delete()) {
					throw new Exception("Could not delete file/directory");
				}
			}
            return newFile;
        }
    }

    /// <summary>
    ///  Get a child file in this directory.
	/// </summary>
	///  <param name="filename">The name of the file or directory to match.</param>
    ///  <returns>The child file</returns>
    public IFile GetChild(string filename)
    {
        DocumentFile[] DocumentFiles = DocumentFile.ListFiles();
        foreach (DocumentFile DocumentFile in DocumentFiles)
        {
            if (DocumentFile.Name.Equals(filename))
                return new AndroidFile(DocumentFile, this);
        }
        // return an empty file
        return new AndroidFile(this, filename);
    }

    /// <summary>
    ///  Rename file.
	/// </summary>
	///  <param name="newFilename">The new filename</param>
    ///  <returns>True if renamed</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public bool RenameTo(string newFilename)
    {
        Reset();
        bool isDir = IsDirectory;
        Uri uri = DocumentsContract.RenameDocument(AndroidFileSystem.GetContext().ContentResolver,
                DocumentFile.Uri, newFilename);
        if(isDir)
            DocumentFile = DocumentFile.FromTreeUri(AndroidFileSystem.GetContext(), uri);
        else
            DocumentFile = DocumentFile.FromSingleUri(AndroidFileSystem.GetContext(), uri);
        name = newFilename;
        return true;
    }

    /// <summary>
    ///  Create this directory.
	/// </summary>
	///  <returns>True if directory created</returns>
    public bool Mkdir()
    {
        Reset();
        IFile parent = Parent;
        if (parent != null)
        {
            IFile dir = parent.CreateDirectory(Name);
            this.DocumentFile = ((AndroidFile) dir).DocumentFile;
            return dir.Exists && dir.IsDirectory;
        }
        return false;
    }

    /// <summary>
    ///  Clear cache properties
    /// </summary>
    public void Reset()
    {
        _basename = null;
        _childrenCount = null;
        _isDirectory = null;
        _isFile = null;
        _lastModified = null;
        _length = null;
    }

    /// <summary>
    ///  Get a file descriptor corresponding to this file.
	/// </summary>
	///  <param name="mode">The mode</param>
    ///  <returns>The parcel file descriptor</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public ParcelFileDescriptor GetFileDescriptor(string mode)
    {
        return AndroidFileSystem.GetContext().ContentResolver.OpenFileDescriptor(DocumentFile.Uri, mode);
    }

    /// <summary>
    ///  Returns a string representation of this object
    ///  <returns>The string represenation</returns>
	/// </summary>
	///
    override
    public string ToString()
    {
        if (DocumentFile == null)
        {
            if (parent != null)
                return parent.DisplayPath + Separator + this.name;
            else
                return this.name;
        }
        return DisplayPath;
    }
}
