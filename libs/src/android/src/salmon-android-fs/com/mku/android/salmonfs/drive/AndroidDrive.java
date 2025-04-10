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
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.android.fs.file.AndroidSharedFileObserver;
import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmonfs.drive.Drive;
import com.mku.fs.drive.utils.FileExporter;
import com.mku.salmonfs.drive.utils.AesFileExporter;
import com.mku.salmonfs.file.AesFile;

/**
 * Implementation of a virtual drive for android.
 */
public class AndroidDrive extends AesDrive {
    private static final String TAG = AndroidDrive.class.getName();
    private static final int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static final int ENC_THREADS = 4;

    /**
     * Protected constructor, use open() and create() instead.
     */
    protected AndroidDrive() {
        super();
    }


    /**
     * Helper method that opens and initializes an AndroidDrive
     *
     * @param dir       The URL that hosts the drive. This can be either a raw URL
     *                  or a REST API URL, see Salmon Web Service for usage.
     * @param password  The password.
     * @param sequencer The nonce sequencer that will be used for encryption.
     * @return The drive.
     * @throws IOException Thrown if error occurs during opening the drive.
     */
	// TODO: Check why it is working with Drive.class for android
    public static AesDrive1 open(IFile dir, String password, INonceSequencer sequencer) throws IOException {
        return AesDrive.openDrive(dir, AndroidDrive.class, password, sequencer);
    }

    /**
     * Helper method that creates and initializes a AndroidDrive
     *
     * @param dir       The directory that will host the drive.
     * @param password  The password.
     * @param sequencer The nonce sequencer that will be used for encryption.
     * @return The drive.
     * @throws IOException If error occurs during creating the drive.
     */
	 // TODO: Check why it is working with Drive.class for android
    public static AesDrive1 create(IFile dir, String password, INonceSequencer sequencer) throws IOException {
        return AesDrive.createDrive(dir, AndroidDrive.class, password, sequencer);
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

        AndroidFile sharedDir = new AndroidFile(DocumentFile.fromFile(privateDir));
        AesFileExporter fileExporter = new AesFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
		FileExporter.FileExportOptions exportOptions = new FileExporter.FileExportOptions();
        exportOptions.integrity = true;
        fileExporter.exportFile(aesFile, sharedDir, exportOptions);
        return cacheFile;
    }

    /**
     * Get the private directory that will be used for sharing encrypted context with
     * other apps on the android device.
     *
     * @return The private directory
     */
    public IFile getPrivateDir() {
        java.io.File sharedDir = new java.io.File(AndroidFileSystem.getContext().getCacheDir(),
                getShareDirectoryName());
        if (!sharedDir.exists())
            sharedDir.mkdir();
        return new File(sharedDir.getAbsolutePath());
    }

    /**
     * Fired when unlock succeeds.
     */
    @Override
    public void onUnlockSuccess() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(AndroidFileSystem.getContext().getCacheDir());
    }

    /**
     * Fired when unlock fails.
     */
    @Override
    public void onUnlockError() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(AndroidFileSystem.getContext().getCacheDir());
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
