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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.mku11.salmon.main.ActivityCommon;
import com.mku11.salmon.main.SalmonApplication;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonDrive;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;

import java.util.List;

/**
 * Implementation of a virtual drive for android.
 */
public class AndroidDrive extends SalmonDrive {
    public static final String TAG = AndroidDrive.class.getName();
    private static final int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static final int ENC_THREADS = 4;

    /**
     * Instantiate a virtual Drive for android under a real directory path
     *
     * @param realRoot The path of the real directory
     */
    public AndroidDrive(String realRoot) {
        super(realRoot);
    }

    public static java.io.File copyToSharedFolder(SalmonFile salmonFile) throws Exception {
        java.io.File privateDir = getPrivateDir();
        java.io.File cacheFile = new java.io.File(privateDir, salmonFile.getBaseName());
        AndroidSharedFileObserver.removeFileObserver(cacheFile);
        cacheFile.delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.fromFile(privateDir), SalmonApplication.getInstance().getApplicationContext());
        SalmonFileExporter fileExporter = new SalmonFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        fileExporter.exportFile(salmonFile, sharedDir, false, null, 1, 1);
        return cacheFile;
    }

    protected static java.io.File getPrivateDir() {
        java.io.File sharedDir = new java.io.File(SalmonApplication.getInstance().getApplicationContext().getCacheDir(), SHARE_DIR);
        if (!sharedDir.exists())
            sharedDir.mkdir();
        return sharedDir;
    }

    public static IRealFile getFile(DocumentFile docFile) {
        return new AndroidFile(docFile, SalmonApplication.getInstance().getApplicationContext());
    }

    public IRealFile getFile(String filepath, boolean isDirectory) {
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(SalmonApplication.getInstance().getApplicationContext(), Uri.parse(filepath));
        else
            docFile = DocumentFile.fromSingleUri(SalmonApplication.getInstance().getApplicationContext(), Uri.parse(filepath));
        AndroidFile file = new AndroidFile(docFile, SalmonApplication.getInstance().getApplicationContext());
        return file;
    }

    @Override
    public void onAuthenticationSuccess() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(SalmonApplication.getInstance().getApplicationContext().getCacheDir());
    }

    @Override
    protected void onAuthenticationError() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(SalmonApplication.getInstance().getApplicationContext().getCacheDir());
    }

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
