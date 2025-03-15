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
using global::Android.Content;
using global::Android.OS;
using global::Android.Provider;
using System.IO;
using System;
using System.Collections.Generic;
using Mku.FS.File;
using Mku.Android.SalmonFS.Drive;
using Mku.Android.FS.Streams;

namespace Mku.Android.FS.File;

/// <summary>
///  Implementation of the IFile for Android using Storage Access Framework that supports read/write to external SD cards.
///  This class is used by the AndroidDrive implementation so you can use AesFile wrapper transparently
/// </summary>
///
public class AndroidFile : IFile
{
    private Context context;
    private DocumentFile documentFile;

    // the DocumentFile interface can be slow so we cache some attrs
    private string _basename = null;
    private long? _length;
    private long? _lastModified;
    private int? _childrenCount;
    private bool? _isDirectory;

    /// <summary>
    ///  Construct an AndroidFile wrapper from an Android DocumentFile.
	/// </summary>
	///  <param name="documentFile">The Android DocumentFile that will be associated to</param>
    ///  <param name="context">     Android Context</param>
    public AndroidFile(DocumentFile documentFile, Context context)
    {
        this.documentFile = documentFile;
        this.context = context;
    }

    public IFile CreateDirectory(string dirName)
    {
        DocumentFile dir = documentFile.CreateDirectory(dirName);
        if (dir == null)
            return null;
        ClearCache();
        AndroidFile newDir = new AndroidFile(dir, AndroidDrive.Context);
        return newDir;

    }

    public IFile CreateFile(string filename)
    {
        DocumentFile doc = documentFile.CreateFile("*/*", filename);
        // for some reason android storage access framework even though it supports auto rename
        // somehow it includes the extension. to protect that we temporarily use another extension
        doc.RenameTo(filename + ".dat");
        doc.RenameTo(filename);
        ClearCache();
        AndroidFile newFile = new AndroidFile(doc, AndroidDrive.Context);
        return newFile;
    }

    /// <summary>
    ///  Delete this file.
	/// </summary>
	///  <returns>True if deletion is successful.</returns>
    public bool Delete()
    {
        bool res = documentFile.Delete();
        if (res && Parent != null)
        {
            (Parent as AndroidFile).ClearCache();
        }
        return res;
    }

    /// <summary>
    ///  True if file exists.
	/// </summary>
	///  <returns>True if exists</returns>
    public bool Exists => documentFile.Exists();


    /// <summary>
    ///  Get the absolute path on the physical drive.
	/// </summary>
	///  <returns>The absolute path</returns>
    public string AbsolutePath
    {
        get
        {
            try
            {
                string path = Uri.Decode(documentFile.Uri.ToString());
                int index = path.LastIndexOf(":");
                return "/" + path.Substring(index + 1);
            }
            catch (Exception ex)
            {
                return documentFile.Uri.Path;
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

            if (documentFile != null)
            {
                _basename = documentFile.Name;
            }
            return _basename;
        }
    }

    /// <summary>
    ///  Get a stream for reading.
	/// </summary>
	///  <returns>The input stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetInputStream()
    {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
        return androidFileStream;
    }

    /// <summary>
    ///  Get a stream for writing.
	/// </summary>
	///  <returns>The output stream</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public Stream GetOutputStream()
    {
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
            DocumentFile parentDocumentFile = documentFile.ParentFile;
            if (parentDocumentFile == null)
                return null;
            AndroidFile parent = new AndroidFile(parentDocumentFile, AndroidDrive.Context);
            return parent;
        }
    }

    /// <summary>
    ///  Get the path.
	/// </summary>
	///  <returns>The path</returns>
    public string Path => documentFile.Uri.ToString();

    /// <summary>
    ///  True if it is a directory.
	/// </summary>
	///  <returns>True if directory</returns>
	// WORKAROUND: documentFile.isDirectory() is very slow so we try alternatively 
    public bool IsDirectory
    {
        get
        {
            if (_isDirectory != null)
                return (bool)_isDirectory;
            _isDirectory = documentFile.IsDirectory;
            return (bool)_isDirectory;
        }
    }

    /// <summary>
    ///  True if it is a file.
	/// </summary>
	///  <returns>True if file</returns>
    public bool IsFile => !IsDirectory;

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
            _lastModified = documentFile.LastModified();
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
            _length = documentFile.Length();
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
                _childrenCount = documentFile.ListFiles().Length;
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
        DocumentFile[] files = documentFile.ListFiles();
        if (files == null)
            return new AndroidFile[0];
        List<IFile> realFiles = new List<IFile>();
        List<IFile> realDirs = new List<IFile>();
        for (int i = 0; i < files.Length; i++)
        {
            AndroidFile file = new AndroidFile(files[i], context);
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
    ///  <param name="progressListener">Observer to notify of the move progress.</param>
    ///  <returns>The moved file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Move(IFile newDir, string newName = null,
        Action<long,long> progressListener = null)
    {
        // target directory is the same
        if(Parent.Path.Equals(newDir.Path))
        {
            throw new Exception("Source and Target directory are the same");
        }

        AndroidFile androidDir = (AndroidFile)newDir;
        if (Build.VERSION.SdkInt >= BuildVersionCodes.N)
        {
            if (progressListener != null)
                progressListener(0, 1);
            if (newName != null)
                RenameTo(Mku.Time.Time.CurrentTimeMillis() + ".dat");
            // TEST: does the documentFile reflect the new name?
            Uri uri = DocumentsContract.MoveDocument(AndroidDrive.Context.ContentResolver,
                    documentFile.Uri, documentFile.ParentFile.Uri, androidDir.documentFile.Uri);
            if (progressListener != null)
                progressListener(1, 1);
            IFile file = androidDir.GetChild(Name);
            if (file != null && newName != null)
                file.RenameTo(newName);
            if(Parent!=null)
                (Parent as AndroidFile).ClearCache();
            androidDir.ClearCache();
            ClearCache();
            return file;
        }
        else
        {
            return Copy(newDir, newName, true, progressListener);
        }
    }

    /// <summary>
    ///  Copy this file to another directory.
	/// </summary>
	///  <param name="newDir">The target directory.</param>
    ///  <param name="progressListener">Observer to notify of the copy progress.</param>
    ///  <returns>The new file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    public IFile Copy(IFile newDir, string newName = null,
        Action<long,long> progressListener = null)
    {
        return Copy(newDir, newName, false, progressListener);
    }

    /// <summary>
    ///  Copy to another directory
    /// </summary>
    ///  <param name="newDir">The destination directory</param>
    ///  <param name="delete">True to delete when complete</param>
    ///  <param name="progressListener">The progess listener</param>
    ///  <returns>The new file</returns>
    ///  <exception cref="IOException">Thrown if error during IO</exception>
    private IFile Copy(IFile newDir, string newName = null,
        bool delete = false, Action<long,long> progressListener = null)
    {
        if (newDir == null || !newDir.Exists)
            throw new IOException("Target directory does not exists");

        newName = newName ?? Name;
        IFile dir = newDir.GetChild(newName);
        if (dir != null)
            throw new IOException("Target file/directory already exists");
        if (IsDirectory)
        {
            IFile file = newDir.CreateDirectory(newName);
            return file;
        }
        else
        {
            IFile newFile = newDir.CreateFile(newName);
            IFile.CopyFileContents(this, newFile, delete, progressListener);
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
        DocumentFile[] documentFiles = documentFile.ListFiles();
        foreach (DocumentFile documentFile in documentFiles)
        {
            if (documentFile.Name.Equals(filename))
                return new AndroidFile(documentFile, context);
        }
        return null;
    }

    /// <summary>
    ///  Rename file.
	/// </summary>
	///  <param name="newFilename">The new filename</param>
    ///  <returns>True if renamed</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public bool RenameTo(string newFilename)
    {
        DocumentsContract.RenameDocument(context.ContentResolver, documentFile.Uri, newFilename);
        //FIXME: we should also get a new documentFile since the old is renamed
        documentFile = ((AndroidFile)Parent.GetChild(newFilename)).documentFile;
        _basename = newFilename;
        _lastModified = null;
        return true;
    }

    /// <summary>
    ///  Create this directory.
	/// </summary>
	///  <returns>True if directory created</returns>
    public bool Mkdir()
    {
        IFile parent = Parent;
        if (parent != null)
        {
            IFile dir = parent.CreateDirectory(Name);
            return dir.Exists && dir.IsDirectory;
        }
        return false;
    }
	
	/// <summary>
    ///  Clear cache properties
	/// </summary>
    public void Reset()
    {
		
    }

    /// <summary>
    ///  Get a file descriptor corresponding to this file.
	/// </summary>
	///  <param name="mode">The mode</param>
    ///  <returns>The parcel file descriptor</returns>
    ///  <exception cref="FileNotFoundException">Thrown if file is not found</exception>
    public ParcelFileDescriptor GetFileDescriptor(string mode)
    {
        return AndroidDrive.Context.ContentResolver.OpenFileDescriptor(documentFile.Uri, mode);
    }

    /// <summary>
    ///  Returns a string representation of this object
    ///  <returns>The string represenation</returns>
	/// </summary>
	///
    override
    public string ToString()
    {
        return documentFile.Uri.ToString();
    }

    public void ClearCache()
    {
        _basename = null;
        _childrenCount = null;
        _isDirectory = null;
        _lastModified = null;
        _length = null;
    }
}
