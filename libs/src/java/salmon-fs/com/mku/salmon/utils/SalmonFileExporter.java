package com.mku.salmon.utils;
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

import com.mku.file.IRealFile;
import com.mku.file.IVirtualFile;
import com.mku.func.BiConsumer;
import com.mku.streams.RandomAccessStream;
import com.mku.salmon.SalmonFile;
import com.mku.utils.FileExporter;
import com.mku.utils.FileImporter;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalmonFileExporter extends FileExporter {
    public SalmonFileExporter(int bufferSize, int threads) {
        super.initialize(bufferSize, threads);
    }
    protected void onPrepare(IVirtualFile sourceFile, boolean integrity) throws IOException {
        // we use the drive hash key for integrity verification
        ((SalmonFile) sourceFile).setVerifyIntegrity(integrity, null);
    }

    protected long getMinimumPartSize(IVirtualFile file) throws IOException {
        return ((SalmonFile) file).getMinimumPartSize();
    }
}
