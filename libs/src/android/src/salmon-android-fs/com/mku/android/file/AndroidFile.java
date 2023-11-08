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

import com.mku.file.IRealFile;
import com.mku.io.RandomAccessStream;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
     * @return
     */
    public boolean exists() {
        return documentFile.exists();
    }

    /**
     * Get the absolute path on the physical drive.
     *
     * @return
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
     * @return
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
     * @return
     * @throws FileNotFoundException
     */
    public RandomAccessStream getInputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
        return androidFileStream;
    }

    /**
     * Get a stream for writing.
     *
     * @return
     * @throws FileNotFoundException
     */
    public RandomAccessStream getOutputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "rw");
        return androidFileStream;
    }

    /**
     * Get the parent directory.
     *
     * @return
     */
    public IRealFile getParent() {
        DocumentFile parentDocumentFile = documentFile.getParentFile();
        AndroidFile parent = new AndroidFile(parentDocumentFile, AndroidDrive.getContext());
        return parent;
    }

    /**
     * Get the path.
     *
     * @return
     */
    public String getPath() {
        return documentFile.getUri().toString();
    }

    /**
     * True if it is a directory.
     *
     * @return
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
     * @return
     */
    public boolean isFile() {
        return !isDirectory();
    }

    /**
     * Get the last modified time in milliseconds.
     *
     * @return
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
     * @return
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
     * @return
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
     * Move this fiel to another directory.
     *
     * @param newDir           The target directory.
     * @return
     * @throws IOException
     */
    public IRealFile move(IRealFile newDir) throws IOException {
        return move(newDir, null, null);
    }

    /**
     * Move this fiel to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @return
     * @throws IOException
     */
    public IRealFile move(IRealFile newDir, String newName) throws IOException {
        return move(newDir, newName, null);
    }

    /**
     * Move this fiel to another directory.
     *
     * @param newDir           The target directory.
     * @param progressListener Observer to notify of the move progress.
     * @return
     * @throws IOException
     */
    public IRealFile move(IRealFile newDir, String newName, RandomAccessStream.OnProgressListener progressListener)
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
                progressListener.onProgressChanged(0, 1);
            if (newName != null)
                renameTo(System.currentTimeMillis() + ".dat");

            // TEST: does the documentFile reflect the new name?
            Uri uri = DocumentsContract.moveDocument(AndroidDrive.getContext().getContentResolver(),
                    documentFile.getUri(), documentFile.getParentFile().getUri(), androidDir.documentFile.getUri());
            if (progressListener != null)
                progressListener.onProgressChanged(1, 1);
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
     * @return
     * @throws IOException
     */
    public IRealFile copy(IRealFile newDir) throws IOException {
        return copy(newDir, null, null);
    }

    /**
     * Copy this file to another directory.
     *
     * @param newDir           The target directory.
     * @param newName          The new filename
     * @return
     * @throws IOException
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
     * @return
     * @throws IOException
     */
    public IRealFile copy(IRealFile newDir, String newName,
                          RandomAccessStream.OnProgressListener progressListener)
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
                           boolean delete, RandomAccessStream.OnProgressListener progressListener)
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
     * @return
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
     * @return
     * @throws FileNotFoundException
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
     * @return
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
     * Get a file descriptor corresponding to this file.
     *
     * @param mode
     * @return
     * @throws FileNotFoundException
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

    public void clearCache()
    {
        _basename = null;
        _childrenCount = null;
        _isDirectory = null;
        _lastModified = null;
        _length = null;
    }
}
