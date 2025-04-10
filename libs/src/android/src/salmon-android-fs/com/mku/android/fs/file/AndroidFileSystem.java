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

import androidx.documentfile.provider.DocumentFile;

import com.mku.android.salmonfs.drive.AndroidDrive;
import com.mku.fs.drive.utils.FileExporter;
import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmonfs.drive.Drive;
import com.mku.salmonfs.drive.utils.AesFileExporter;
import com.mku.salmonfs.file.AesFile;

/**
 * Utility for Android file system
 */
public class AndroidFileSystem {
    private static Context context;

    /**
     * Initialize the Android Drive before creating or opening any virtual drives.
     *
     * @param context The context
     */
    public static void initialize(Context context) {
        AndroidFileSystem.context = context.getApplicationContext();
    }

    /**
     * Get the Android context.
     *
     * @return The Android context
     */
    public static Context getContext() {
        if(context == null)
            throw new RuntimeException("Use AndroidFileSystem.initialize() before using any file");
        return context;
    }
	
    /**
     * Get the real file hosted on the android device.
     *
     * @param uri         The content uri (ie: content://)
     * @param isDirectory True if filepath corresponds to a directory.
     * @return The real file
     */
    public static IFile getRealFile(String uri, boolean isDirectory) {
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(AndroidFileSystem.getContext(), Uri.parse(uri));
        else
            docFile = DocumentFile.fromSingleUri(AndroidFileSystem.getContext(), Uri.parse(uri));
        AndroidFile file = new AndroidFile(docFile);
        return file;
    }

}