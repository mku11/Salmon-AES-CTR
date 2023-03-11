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

import android.os.Build;
import android.os.FileObserver;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.arch.core.util.Function;

import com.mku11.salmonfs.SalmonFile;

import java.io.File;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Implementation of a file observer that will detect if the file contents have changed
 */
public class AndroidSharedFileObserver extends FileObserver {
    // we need to keep a reference of all the live fileObservers because they will stop firing events
    private static final HashMap<String, AndroidSharedFileObserver> fileObservers = new HashMap<String, AndroidSharedFileObserver>();

    private SalmonFile salmonFile;
    private Consumer<AndroidSharedFileObserver> onFileContentsChanged;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AndroidSharedFileObserver(File file, SalmonFile salmonFile,
                                      Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        super(file, FileObserver.CLOSE_WRITE);
        this.salmonFile = salmonFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    private AndroidSharedFileObserver(String filePath, SalmonFile salmonFile,
                                      Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        super(filePath, FileObserver.CLOSE_WRITE);
        this.salmonFile = salmonFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    /**
     * Create a file observer that will detect if the file contents of a shared salmon file have changed
     *
     * @param cacheFile             The temporary private decrypted cached file
     * @param salmonFile            The encrypted file that is associated with the shared file
     * @param onFileContentsChanged Action notifier when file contents change
     */
    public static AndroidSharedFileObserver createFileObserver(File cacheFile, SalmonFile salmonFile,
                                                               Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
        AndroidSharedFileObserver fileObserver;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            fileObserver = new AndroidSharedFileObserver(cacheFile,
                    salmonFile, onFileContentsChanged);
        } else {
            fileObserver = new AndroidSharedFileObserver(cacheFile.getPath(),
                    salmonFile, onFileContentsChanged);
        }
        fileObservers.put(cacheFile.getPath(), fileObserver);
        return fileObserver;
    }

    public static void removeFileObserver(File cacheFile) {
        if (fileObservers.containsKey(cacheFile.getPath())) {
            AndroidSharedFileObserver fileObserver = fileObservers.get(cacheFile.getPath());
            fileObserver.onFileContentsChanged = null;
            fileObservers.remove(cacheFile.getPath());
        }
    }

    public static void clearFileObservers() {
        fileObservers.clear();
    }

    @Override
    public void onEvent(int e, @Nullable String path) {
        if (onFileContentsChanged != null)
            onFileContentsChanged.accept(this);
    }

    /**
     * Returns the encrypted salmon file that is associated with the shared file
     */
    public SalmonFile getSalmonFile() {
        return salmonFile;
    }

    public void setSalmonFile(SalmonFile salmonFile) {
        this.salmonFile = salmonFile;
    }
}

