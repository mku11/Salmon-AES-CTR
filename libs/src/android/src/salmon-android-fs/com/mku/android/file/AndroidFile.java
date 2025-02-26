package com.mku.android.file;
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

import com.mku.android.streams.AndroidFileStream;
import com.mku.android.salmon.drive.AndroidDrive;
import com.mku.file.IRealFile;
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the IRealFile for Android using Storage Access Framework that supports read/write to external SD cards.
 * This class is used by the AndroidDrive implementation so you can use SalmonFile wrapper transparently
 */
public class AndroidFile implements IRealFile {
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

    public IRealFile createDirectory(String dirName) {
        DocumentFile dir = documentFile.createDirectory(dirName);
        if (dir == null)
            return null;
        clearCache();
        AndroidFile newDir = new AndroidFile(dir, AndroidDrive.getContext());
        return newDir;

    }

    public IRealFile createFile(String filename) {
        DocumentFile doc = documentFile.createFile("*/*", filename);
        // for some reason android storage access framework even though it supports auto rename
        // somehow it includes the extension. to protect that we temporarily use another extension
        doc.renameTo(filename + ".dat");
        doc.renameTo(filename);
        clearCache();
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
            ((AndroidFile)getParent()).clearCache();
        }
        return res;
    }

    /**
     * True if file exists.
     *
     * @return True if exists
     */
    public boolean exists() {
        return documentFile.exists();
    }

    /**
     * Get the absolute path on the physical drive.
     *
     * @return The absolute path
     */
    public String getAbsolutePath() {
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
    public String getBaseName() {
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
    public IRealFile getParent() {
        DocumentFile parentDocumentFile = documentFile.getParentFile();
		if(parentDocumentFile == null)
			return null;
        AndroidFile parent = new AndroidFile(parentDocumentFile, AndroidDrive.getContext());
        return parent;
    }

    /**
     * Get the path.
     *
     * @return The file path
     */
    public String getPath() {
        return documentFile.getUri().toString();
    }

    /**
     * True if it is a directory.
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
     * True if it is a file.
     *
     * @return True if is file
     */
    public boolean isFile() {
        return !isDirectory();
    }

    /**
     * Get the last modified date in milliseconds.
     *
     * @return Last modified date in milliseconds
     */
    public long lastModified() {
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
    public long length() {
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
    public IRealFile[] listFiles() {
        DocumentFile[] files = documentFile.listFiles();
        if (files == null)
            return new AndroidFile[0];
        List<IRealFile> realFiles = new ArrayList<>();
        List<IRealFile> realDirs = new ArrayList<>();
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
    public IRealFile move(IRealFile newDir) throws IOException {
        return move(newDir, null, null);
    }

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @return The moved file
     * @throws IOException Thrown if error during IO
     */
    public IRealFile move(IRealFile newDir, String newName) throws IOException {
        return move(newDir, newName, null);
    }

    /**
     * Move this file to another directory.
     *
     * @param newDir           The target directory.
     * @param progressListener Observer to notify of the move progress.
     * @return The moved file
     * @throws IOException Thrown if error during IO
     */
    public IRealFile move(IRealFile newDir, String newName, BiConsumer<Long, Long> progressListener)
            throws IOException {
        // target directory is the same
        if(getParent().getPath().equals(newDir.getPath()))
        {
            throw new IOException("Source and Target directory are the same");
        }

        AndroidFile androidDir = (AndroidFile)newDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        {
            if (progressListener != null)
                progressListener.accept(0L, 1L);
            if (newName != null)
                renameTo(System.currentTimeMillis() + ".dat");

            // TEST: does the documentFile reflect the new name?
            Uri uri = DocumentsContract.moveDocument(AndroidDrive.getContext().getContentResolver(),
                    documentFile.getUri(), documentFile.getParentFile().getUri(), androidDir.documentFile.getUri());
            if (progressListener != null)
                progressListener.accept(1L, 1L);
            IRealFile file = androidDir.getChild(getBaseName());
            if (file != null && newName != null)
                file.renameTo(newName);
            if(getParent()!=null)
                ((AndroidFile)getParent()).clearCache();
            androidDir.clearCache();
            clearCache();
            return file;
        }
        else
        {
            return copy(newDir, newName, true, progressListener);
        }
    }

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @return The new file
     * @throws IOException Thrown if error during IO
     */
    public IRealFile copy(IRealFile newDir) throws IOException {
        return copy(newDir, null, null);
    }

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @return The new file
     * @throws IOException Thrown if error during IO
     */
    public IRealFile copy(IRealFile newDir, String newName) throws IOException {
        return copy(newDir, newName, null);
    }

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @param progressListener Observer to notify of the copy progress.
     * @return The new file
     * @throws IOException Thrown if error during IO
     */
    public IRealFile copy(IRealFile newDir, String newName, BiConsumer<Long,Long> progressListener)
            throws IOException {
        return copy(newDir, newName, false, progressListener);
    }

    /**
     * Copy this file to another directory
     *
     * @param newDir
     * @param progressListener
     * @param delete
     * @return
     * @throws IOException
     */
    private IRealFile copy(IRealFile newDir, String newName,
                           boolean delete, BiConsumer<Long,Long> progressListener)
            throws IOException {
        if (newDir == null || !newDir.exists())
            throw new IOException("Target directory does not exists");

        newName = newName !=null?newName: getBaseName();
        IRealFile dir = newDir.getChild(newName);
        if (dir != null)
            throw new IOException("Target file/directory already exists");
        if (isDirectory())
        {
            IRealFile file = newDir.createDirectory(newName);
            return file;
        }
        else
        {
            IRealFile newFile = newDir.createFile(newName);
            IRealFile.copyFileContents(this, newFile, delete, progressListener);
            return newFile;
        }
    }

    /**
     * Get a child file in this directory.
     *
     * @param filename The name of the file or directory to match.
     * @return The child file
     */
    public IRealFile getChild(String filename) {
        DocumentFile[] documentFiles = documentFile.listFiles();
        for (DocumentFile documentFile : documentFiles) {
            if (documentFile.getName().equals(filename))
                return new AndroidFile(documentFile, context);
        }
        return null;
    }

    /**
     * Rename file.
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
        IRealFile parent = getParent();
        if (parent != null) {
            IRealFile dir = parent.createDirectory(getBaseName());
            return dir.exists() && dir.isDirectory();
        }
        return false;
    }
	
	/**
     * Clear cache properties
     */
    public boolean reset() {
		
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

    /**
     * Clear the cache
     */
    public void clearCache()
    {
        _basename = null;
        _childrenCount = null;
        _isDirectory = null;
        _lastModified = null;
        _length = null;
    }
}
