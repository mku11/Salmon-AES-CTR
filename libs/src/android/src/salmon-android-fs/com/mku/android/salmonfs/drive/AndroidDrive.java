package com.mku.android.salmonfs.drive;
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

import androidx.documentfile.provider.DocumentFile;

import com.mku.android.fs.file.AndroidFile;
import com.mku.android.fs.file.AndroidSharedFileObserver;
import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmonfs.drive.Drive;
import com.mku.salmonfs.drive.utils.AesFileExporter;
import com.mku.salmonfs.file.AesFile;

/**
 * Implementation of a virtual drive for android.
 */
public class AndroidDrive extends Drive {
    private static final String TAG = AndroidDrive.class.getName();
    private static final int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static final int ENC_THREADS = 4;
    private static Context context;

    /**
     * Initialize the Android Drive. This needs to run before you attempt to
     * create or open any virtual drives.
     *
     * @param context The context
     */
    public static void initialize(Context context) {
        AndroidDrive.context = context.getApplicationContext();
    }

    /**
     * Get the Android context.
     *
     * @return The Android context
     */
    public static Context getContext() {
        return context;
    }

    /**
     * Protected constructor, use open() and create() instead.
     */
    protected AndroidDrive() {
        super();
    }

    /**
     * Copy file to shared folder
     *
     * @param aesFile The file
     * @return The shared file
     * @throws Exception Thrown when error occured
     */
    public java.io.File copyToSharedFolder(AesFile aesFile) throws Exception {
        java.io.File privateDir = new java.io.File(getPrivateDir().getDisplayPath());
        java.io.File cacheFile = new java.io.File(privateDir, aesFile.getName());
        AndroidSharedFileObserver.removeFileObserver(cacheFile);
        cacheFile.delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.fromFile(privateDir), context);
        AesFileExporter fileExporter = new AesFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        fileExporter.exportFile(aesFile, sharedDir, null, false, true, null);
        return cacheFile;
    }

    /**
     * Get the private directory that will be used for sharing encrypted context with
     * other apps on the android device.
     *
     * @return The private directory
     */
    public IFile getPrivateDir() {
        java.io.File sharedDir = new java.io.File(context.getCacheDir(), getShareDirectoryName());
        if (!sharedDir.exists())
            sharedDir.mkdir();
        return new File(sharedDir.getAbsolutePath());
    }

    /**
     * Get the real file hosted on the android device.
     *
     * @param uri         The real file uri
     * @param isDirectory True if filepath corresponds to a directory.
     * @return The real file
     */
    public IFile getRealFile(String uri, boolean isDirectory) {
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(context, Uri.parse(uri));
        else
            docFile = DocumentFile.fromSingleUri(context, Uri.parse(uri));
        AndroidFile file = new AndroidFile(docFile, context);
        return file;
    }

    /**
     * Fired when unlock succeeds.
     */
    @Override
    public void onUnlockSuccess() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(context.getCacheDir());
    }

    /**
     * Fired when unlock fails.
     */
    @Override
    public void onUnlockError() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(context.getCacheDir());
    }

    /**
     * Clear the cache and the private folder that is used to share files with
     * other apps.
     *
     * @param file The cache file or directory
     */
    private void clearCache(java.io.File file) {
        if (file.exists() && file.isDirectory()) {
            java.io.File[] cacheFiles = file.listFiles();
            if (cacheFiles != null) {
                for (java.io.File cacheFile : cacheFiles) {
                    clearCache(cacheFile);
                }
            }
        } else {
            file.delete();
        }
    }
}
