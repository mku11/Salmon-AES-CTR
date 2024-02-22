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

import androidx.documentfile.provider.DocumentFile;

import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.file.IRealFile;
import com.mku.salmonfs.SalmonDrive;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileExporter;

/**
 * Implementation of a virtual drive for android.
 */
public class AndroidDrive extends SalmonDrive {
    public static final String TAG = AndroidDrive.class.getName();
    private static final int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static final int ENC_THREADS = 4;
    private static Context context;

    /**
     * Initialize the Android Drive. This needs to run before you attempt to
     * create or open any virtual drives.
     * @param context
     */
    public static void initialize(Context context) {
        AndroidDrive.context = context.getApplicationContext();
    }

    /**
     * Get the Android context.
     * @return
     */
    public static Context getContext(){
        return context;
    }

    /**
     * Instantiate a virtual Drive for android under a real directory path
     *
     * @param realRoot The path of the real directory
     * @param createIfNotExists Create the drive if it doesn't exist.
     */
    public AndroidDrive(String realRoot, boolean createIfNotExists) {
        super(realRoot, createIfNotExists);
    }

    public java.io.File copyToSharedFolder(SalmonFile salmonFile) throws Exception {
        java.io.File privateDir = getPrivateDir();
        java.io.File cacheFile = new java.io.File(privateDir, salmonFile.getBaseName());
        AndroidSharedFileObserver.removeFileObserver(cacheFile);
        cacheFile.delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.fromFile(privateDir), context);
        SalmonFileExporter fileExporter = new SalmonFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        fileExporter.exportFile(salmonFile, sharedDir, null,  false, true, null);
        return cacheFile;
    }

    /**
     * Get the private directory that will be used for sharing encrypted context with
     * other apps on the android device.
     * @return
     */
    protected java.io.File getPrivateDir() {
        java.io.File sharedDir = new java.io.File(context.getCacheDir(), getShareDirectoryName());
        if (!sharedDir.exists())
            sharedDir.mkdir();
        return sharedDir;
    }

    /**
     * Get the real file hosted on the android device.
     * @param filepath
     * @param isDirectory True if filepath corresponds to a directory.
     * @return
     */
    public IRealFile getRealFile(String filepath, boolean isDirectory) {
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(context, Uri.parse(filepath));
        else
            docFile = DocumentFile.fromSingleUri(context, Uri.parse(filepath));
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
    protected void onUnlockError() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(context.getCacheDir());
    }

    /**
     * Clear the cache and the private folder that is used to share files with
     * other apps.
     * @param file
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
