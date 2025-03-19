package com.mku.android.fs.file;
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

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.mku.android.fs.streams.AndroidFileStream;
import com.mku.android.salmonfs.drive.AndroidDrive;
import com.mku.fs.file.IFile;
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the IFile for Android using Storage Access Framework that supports read/write to external SD cards.
 * This class is used by the AndroidDrive implementation so you can use AesFile wrapper transparently
 */
public class AndroidFile implements IFile {
    //TODO: remove the context
    private final Context context;
    private DocumentFile documentFile;

    // the DocumentFile interface can be slow so we cache some attrs
    private String _basename = null;
    private Long _length;
    private Long _lastModified;
    private Integer _childrenCount;
    private Boolean _isDirectory;

    /**
     * Construct an AndroidFile wrapper from an Android DocumentFile.
     *
     * @param documentFile The Android DocumentFile that will be associated to
     * @param context      Android Context
     */
    public AndroidFile(DocumentFile documentFile, Context context) {
        this.documentFile = documentFile;
        this.context = context;

    }

	/**
     * Create a directory under this directory.
     *
     * @param dirName The directory name.
	 * @return The new directory.
     */
    public IFile createDirectory(String dirName) {
        DocumentFile dir = documentFile.createDirectory(dirName);
        if (dir == null)
            return null;
        reset();
        AndroidFile newDir = new AndroidFile(dir, AndroidDrive.getContext());
        return newDir;
    }

	/**
     * Create an empty file under this directory.
     *
     * @param filename The file name.
	 * @return The new file.
     */
    public IFile createFile(String filename) {
        DocumentFile doc = documentFile.createFile("*/*", filename);
        // for some reason android storage access framework even though it supports auto rename
        // somehow it includes the extension. to protect that we temporarily use another extension
        doc.renameTo(filename + ".dat");
        doc.renameTo(filename);
        reset();
        AndroidFile newFile = new AndroidFile(doc, AndroidDrive.getContext());
        return newFile;
    }

    /**
     * Delete this file.
     *
     * @return True if deletion is successful.
     */
    public boolean delete() {

        boolean res = documentFile.delete();
        if (res && getParent() != null)
        {
            ((AndroidFile)getParent()).reset();
        }
        return res;
    }

    /**
     * Check if file exists.
     *
     * @return True if exists
     */
    public boolean exists() {
        return documentFile.exists();
    }

    /**
     * Get the display path on the physical drive.
     *
     * @return The display path
     */
    public String getDisplayPath() {
        try {
            String path = Uri.decode(documentFile.getUri().toString());
            int index = path.lastIndexOf(":");
            return "/" + path.substring(index + 1);
        } catch (Exception ex) {
            return documentFile.getUri().getPath();
        }
    }

    /**
     * Get the base name of this file.
     *
     * @return The base name
     */
    public String getName() {
        if (_basename != null)
            return _basename;

        if (documentFile != null)
            _basename = documentFile.getName();
        return _basename;
    }

    /**
     * Get a stream for reading.
     *
     * @return The input stream
     * @throws FileNotFoundException Thrown if file is not found
     */
    public RandomAccessStream getInputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
        return androidFileStream;
    }

    /**
     * Get a stream for writing.
     *
     * @return The output stream
     * @throws FileNotFoundException Thrown if file not found
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "rw");
        return androidFileStream;
    }

    /**
     * Get the parent directory.
     *
     * @return The parent directory
     */
    public IFile getParent() {
        DocumentFile parentDocumentFile = documentFile.getParentFile();
		if(parentDocumentFile == null)
			return null;
        AndroidFile parent = new AndroidFile(parentDocumentFile, AndroidDrive.getContext());
        return parent;
    }

    /**
     * Get the path on the physical disk.
     *
     * @return The file path
     */
    public String getPath() {
        return documentFile.getUri().toString();
    }

    /**
     * Check if this is a directory.
     *
     * @return True if directory
     */
    public boolean isDirectory() {
        if (_isDirectory != null)
            return (boolean) _isDirectory;
        _isDirectory = documentFile.isDirectory();
        return (boolean) _isDirectory;
    }

    /**
     * Check if this is a file.
     *
     * @return True if is file
     */
    public boolean isFile() {
        return !isDirectory();
    }

    /**
     * Get the last modified date in milliseconds.
     *
     * @return Last modified date in milliseconds.
     */
    public long getLastDateModified() {
        if (_lastModified != null)
            return _lastModified;
        _lastModified = documentFile.lastModified();
        return _lastModified;
    }

    /**
     * Get the size of the file.
     *
     * @return The size
     */
    public long getLength() {
        if (_length != null)
            return _length;
        _length = documentFile.length();
        return _length;
    }

    /**
     * Get the count of files and subdirectories
     */
    public int getChildrenCount() {
        if (_childrenCount != null)
            return (int) _childrenCount;
        if (isDirectory())
            _childrenCount = documentFile.listFiles().length;
        else
            _childrenCount = 0;
        return (int) _childrenCount;
    }

    /**
     * List files and directories.
     *
     * @return The files and subdirectories
     */
    public IFile[] listFiles() {
        DocumentFile[] files = documentFile.listFiles();
        if (files == null)
            return new AndroidFile[0];
        List<IFile> realFiles = new ArrayList<>();
        List<IFile> realDirs = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            AndroidFile file = new AndroidFile(files[i], context);
            if (files[i].isDirectory())
                realDirs.add(file);
            else
                realFiles.add(file);
        }
        realDirs.addAll(realFiles);
        return realDirs.toArray(new AndroidFile[0]);
    }

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @return The moved file
     * @throws IOException Thrown if error during IO
     */
    public IFile move(IFile newDir) throws IOException {
        return move(newDir, null);
    }

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @param options The options.
     * @return The moved file
     * @throws IOException Thrown if error during IO
     */
    public IFile move(IFile newDir, IFile.MoveOptions options)
            throws IOException {
		if(options == null)
			options = new IFile.MoveOptions();
        // target directory is the same
        if(getParent().getPath().equals(newDir.getPath()))
        {
            throw new IOException("Source and Target directory are the same");
        }

        AndroidFile androidDir = (AndroidFile)newDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            if (options.onProgressChanged != null)
                options.onProgressChanged.accept(0L, this.getLength());
            if (options.newFilename != null)
                renameTo(System.currentTimeMillis() + ".dat");

            // TEST: does the documentFile reflect the new name?
            Uri uri = DocumentsContract.moveDocument(AndroidDrive.getContext().getContentResolver(),
                    documentFile.getUri(), documentFile.getParentFile().getUri(), androidDir.documentFile.getUri());
            IFile file = androidDir.getChild(getName());
            if (file != null && options.newFilename != null)
                file.renameTo(options.newFilename);
            if(getParent()!=null)
                ((AndroidFile)getParent()).reset();
            androidDir.reset();
            reset();
			if (options.onProgressChanged != null)
                options.onProgressChanged.accept(1L, file.getLength());
            return file;
        }
        else
        {
            return copy(newDir, options.newFilename, true, options.onProgressChanged);
        }
    }

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @return The new file
     * @throws IOException Thrown if error during IO
     */
    public IFile copy(IFile newDir) throws IOException {
        return copy(newDir, null);
    }

    /**
     * Copy this file to another directory with a new filename with a progress.
     *
     * @param newDir           The target directory.
     * @param options The options
     * @return The new file
     * @throws IOException Thrown if error during IO
     */
    public IFile copy(IFile newDir, IFile.CopyOptions options)
            throws IOException {
		if(options == null)
			options = new IFile.CopyOptions();
        return copy(newDir, options.newFilename, false, options.onProgressChanged);
    }

    private IFile copy(IFile newDir, String newName,
                           boolean delete, BiConsumer<Long,Long> onProgressChanged)
            throws IOException {
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");

        newName = newName !=null?newName: getName();
        IFile dir = newDir.getChild(newName);
        if (dir != null)
            throw new IOException("Target file/directory already exists");
        if (isDirectory())
        {
            IFile file = newDir.createDirectory(newName);
            return file;
        }
        else
        {
            IFile newFile = newDir.createFile(newName);
			IFile.CopyContentsOptions copyContentOptions = new IFile.CopyContentsOptions();
			copyContentOptions.onProgressChanged = onProgressChanged;
            boolean res = IFile.copyFileContents(this, newFile, copyContentOptions);
			if(res && delete)
				this.delete();
            return newFile;
        }
    }

    /**
     * Get a child file or directory under this directory.
     *
     * @param filename The name of the file or directory to match.
     * @return The child file
     */
    public IFile getChild(String filename) {
        DocumentFile[] documentFiles = documentFile.listFiles();
        for (DocumentFile documentFile : documentFiles) {
            if (documentFile.getName().equals(filename))
                return new AndroidFile(documentFile, context);
        }
        return null;
    }

    /**
     * Rename this file.
     *
     * @param newFilename The new filename
     * @return True if file renamed
     * @throws FileNotFoundException Thrown if file is not found
     */
    public boolean renameTo(String newFilename) throws FileNotFoundException {
        DocumentsContract.renameDocument(context.getContentResolver(), documentFile.getUri(), newFilename);
        //FIXME: we should also get a new documentFile since the old is renamed
        documentFile = ((AndroidFile) getParent().getChild(newFilename)).documentFile;
        _basename = newFilename;
        _lastModified = null;
        return true;
    }

    /**
     * Create this directory.
     *
     * @return True if directory created
     */
    public boolean mkdir() {
        IFile parent = getParent();
        if (parent != null) {
            IFile dir = parent.createDirectory(getName());
            return dir.exists() && dir.isDirectory();
        }
        return false;
    }
	
	/**
     * Clear cache properties.
     */
    public void reset() {
		_basename = null;
        _childrenCount = null;
        _isDirectory = null;
        _lastModified = null;
        _length = null;
    }

    /**
     * Get a file descriptor corresponding to this file.
     *
     * @param mode The mode
     * @return The parcel file descriptor
     * @throws FileNotFoundException Thrown if file is not found
     */
    public ParcelFileDescriptor getFileDescriptor(String mode) throws FileNotFoundException {
        return AndroidDrive.getContext().getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }

    /**
     * Returns a string representation of this object
     */
    @Override
    public String toString() {
        return documentFile.getUri().toString();
    }
}
