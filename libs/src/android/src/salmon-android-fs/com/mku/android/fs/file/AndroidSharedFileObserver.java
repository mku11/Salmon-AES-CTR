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

import android.os.Build;
import android.os.FileObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.mku.func.Consumer;
import com.mku.salmonfs.file.AesFile;

import java.io.File;
import java.util.HashMap;

/**
 * Implementation of a file observer that detects when a share file has been edited
 * by external apps. Use this to get notified to re-import the file into the drive.
 */
public class AndroidSharedFileObserver extends FileObserver {
    /**
     * Keeps references of all the live fileObservers otherwise they will stop firing events.
     */
    private static final HashMap<String, AndroidSharedFileObserver> fileObservers = new HashMap<String, AndroidSharedFileObserver>();

    /**
     * The associated salmon file.
     */
    private AesFile aesFile;

    /**
     * The callback to fire when the shared file changes.
     */
    private Consumer<AndroidSharedFileObserver> onFileContentsChanged;

    /**
     * Instantiate a file observer associated with an encrypted file.
     * @param file The shared file.
     * @param aesFile The AesFile that is associated. This file will be updated with
     *                   the contents of the shared file after the file contents are changed.
     * @param onFileContentsChanged Callback is called the shared file contents change.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AndroidSharedFileObserver(File file, AesFile aesFile,
                                      Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        super(file, FileObserver.CLOSE_WRITE);
        this.aesFile = aesFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    /**
     * Instantiate a file observer associated with an encrypted file.
     * @param filePath The filepath for the shared filed.
     * @param aesFile The AesFile that is associated. This file will be updated with
     *                   the contents of the shared file after the file contents are changed.
     * @param onFileContentsChanged Callback is called the shared file contents change.
     */
    private AndroidSharedFileObserver(String filePath, AesFile aesFile,
                                      Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        super(filePath, FileObserver.CLOSE_WRITE);
        this.aesFile = aesFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    /**
     * Create a file observer that will detect if the file contents of a shared salmon file have changed
     *
     * @param cacheFile             The temporary private decrypted cached file
     * @param aesFile            The encrypted file that is associated with the shared file
     * @param onFileContentsChanged Action notifier when file contents change
     * @return The shared file observer
     */
    public static AndroidSharedFileObserver createFileObserver(File cacheFile, AesFile aesFile,
                                                               Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        AndroidSharedFileObserver fileObserver;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            fileObserver = new AndroidSharedFileObserver(cacheFile,
                    aesFile, onFileContentsChanged);
        } else {
            fileObserver = new AndroidSharedFileObserver(cacheFile.getPath(),
                    aesFile, onFileContentsChanged);
        }
        fileObservers.put(cacheFile.getPath(), fileObserver);
        return fileObserver;
    }

    /**
     * Remove the shared filed associated with this observer.
     * @param cacheFile The cache file.
     */
    public static void removeFileObserver(File cacheFile) {
        if (fileObservers.containsKey(cacheFile.getPath())) {
            AndroidSharedFileObserver fileObserver = fileObservers.get(cacheFile.getPath());
            fileObserver.onFileContentsChanged = null;
            fileObservers.remove(cacheFile.getPath());
        }
    }

    /**
     * Clear all file observers. Call this to release any observes you no longer need.
     */
    public static void clearFileObservers() {
        fileObservers.clear();
    }

    /**
     * When a file event happens.
     * @param e The type of event that happened
     * @param path The path to the monitored file
     */
    @Override
    public void onEvent(int e, @Nullable String path) {
        if (onFileContentsChanged != null)
            onFileContentsChanged.accept(this);
    }

    /**
     * Returns the encrypted salmon file that is associated with the shared file
     * @return The file
     */
    public AesFile getAesFile() {
        return aesFile;
    }

    /**
     * Set the salmon file associated with the shared file to observer.
     * @param aesFile The file
     */
    public void setAesFile(AesFile aesFile) {
        this.aesFile = aesFile;
    }
}

