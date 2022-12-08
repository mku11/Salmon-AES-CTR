package com.mku11.salmon.file;
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
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.mku11.salmon.main.SalmonApplication;
import com.mku11.salmon.streams.AbsStream;
import com.mku11.salmonfs.IRealFile;

import java.io.FileNotFoundException;

/**
 * Implementation of the IRealFile for Android using Storage Access Framework that supports read/write to external SD cards.
 * This class is used by the AndroidDrive implementation so you can use SalmonFile wrapper transparently
 */
public class AndroidFile implements IRealFile {
    private DocumentFile documentFile;
    private String basename = null;
    private final Context context;

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
        AndroidFile newDir = new AndroidFile(dir, SalmonApplication.getInstance().getApplicationContext());
        return newDir;

    }

    public IRealFile createFile(String filename) {
        DocumentFile doc = documentFile.createFile("", filename);
        // for some reason android storage access framework even though it supports auto rename
        // somehow it includes the extension. to protect that we temporarily use another extension
        doc.renameTo(filename + ".dat");
        doc.renameTo(filename);
        AndroidFile newFile = new AndroidFile(doc, SalmonApplication.getInstance().getApplicationContext());
        return newFile;
    }

    public boolean delete() {
        return documentFile.delete();
    }

    public boolean exists() {
        return documentFile.exists();
    }

    public String getAbsolutePath() {
        return documentFile.getUri().getPath();
    }

    public String getBaseName() {
        if (basename != null)
            return basename;

        if (documentFile != null) {
            basename = documentFile.getName();
        }
        return basename;
    }

    public AbsStream getInputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "r");
        return androidFileStream;
    }

    public AbsStream getOutputStream() throws FileNotFoundException {
        AndroidFileStream androidFileStream = new AndroidFileStream(this, "rw");
        return androidFileStream;
    }

    public IRealFile getParent() {
        DocumentFile parentDocumentFile = documentFile.getParentFile();
        AndroidFile parent = new AndroidFile(parentDocumentFile, SalmonApplication.getInstance().getApplicationContext());
        return parent;
    }

    public String getPath() {
        return documentFile.getUri().toString();
    }

    public boolean isDirectory() {
        return documentFile.isDirectory();
    }

    public boolean isFile() {
        return documentFile.isFile();
    }

    public long lastModified() {
        return documentFile.lastModified();
    }

    public long length() {
        return documentFile.length();
    }

    public IRealFile[] listFiles() {
        DocumentFile[] files = documentFile.listFiles();
        if (files == null)
            return new AndroidFile[0];

        IRealFile[] realFiles = new AndroidFile[files.length];
        for (int i = 0; i < files.length; i++) {
            realFiles[i] = new AndroidFile(files[i], context);
        }
        return realFiles;
    }

    public IRealFile move(IRealFile newDir, AbsStream.OnProgressListener progressListener) throws Exception {
        AndroidFile androidDir = (AndroidFile) newDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.moveDocument(SalmonApplication.getInstance().getApplicationContext().getContentResolver(),
                    documentFile.getUri(), documentFile.getParentFile().getUri(), androidDir.documentFile.getUri());
            return androidDir.getChild(documentFile.getName());
        } else {
            return copy(newDir, progressListener, true);
        }
    }

    public IRealFile copy(IRealFile newDir, AbsStream.OnProgressListener progressListener) throws Exception {
        return copy(newDir, progressListener, false);
    }

    private IRealFile copy(IRealFile newDir, AbsStream.OnProgressListener progressListener, boolean delete) throws Exception {
        if (this.isDirectory()) {
            IRealFile dir = newDir.createDirectory(getBaseName());
            for (IRealFile ifile : this.listFiles()) {
                ((AndroidFile) ifile).copy(dir, progressListener, delete);
            }
            if (delete)
                this.delete();
            return dir;
        } else {
            IRealFile newFile = newDir.createFile(getBaseName());
            AbsStream source = getInputStream();
            AbsStream target = newFile.getOutputStream();
            try {
                source.copyTo(target, progressListener);
            } catch (Exception ex) {
                newFile.delete();
                return null;
            } finally {
                source.close();
                target.close();
                if (delete)
                    this.delete();
            }
            return newFile;
        }
    }

    public IRealFile getChild(String filename) {
        DocumentFile[] documentFiles = documentFile.listFiles();
        for (DocumentFile documentFile : documentFiles) {
            if (documentFile.getName().equals(filename))
                return new AndroidFile(documentFile, context);
        }
        return null;
    }

    public boolean renameTo(String newFilename) throws FileNotFoundException {
        DocumentsContract.renameDocument(context.getContentResolver(), documentFile.getUri(), newFilename);
        //FIXME: we should also get a new documentFile since the old is renamed
        documentFile = ((AndroidFile) getParent().getChild(newFilename)).documentFile;
        basename = newFilename;
        return true;
    }

    public boolean mkdir() {
        IRealFile parent = getParent();
        if (parent != null) {
            IRealFile dir = parent.createDirectory(getBaseName());
            return dir.exists() && dir.isDirectory();
        }
        return false;
    }

    public ParcelFileDescriptor getFileDescriptor(String mode) throws FileNotFoundException {
        return SalmonApplication.getInstance().getApplicationContext().getContentResolver().openFileDescriptor(documentFile.getUri(), mode);
    }
}
