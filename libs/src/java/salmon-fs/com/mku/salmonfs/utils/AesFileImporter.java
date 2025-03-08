package com.mku.salmonfs.utils;
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

import com.mku.fs.file.IRealFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.func.BiConsumer;
import com.mku.salmonfs.file.AesFile;
import com.mku.fs.drive.utils.FileImporter;

import java.io.IOException;

public class AesFileImporter extends FileImporter {
    public AesFileImporter(int bufferSize, int threads) {
        super.initialize(bufferSize, threads);
    }

    protected void onPrepare(IVirtualFile importedFile, boolean integrity) throws IOException {
        ((AesFile) importedFile).setAllowOverwrite(true);
        // we use default chunk file size
        ((AesFile) importedFile).setApplyIntegrity(integrity, null, null);
    }

    protected long getMinimumPartSize(IVirtualFile file) throws IOException {
        return ((AesFile) file).getMinimumPartSize();
    }

    public AesFile importFile(IRealFile fileToImport, IVirtualFile dir, String filename,
                              boolean deleteSource, boolean integrity, BiConsumer<Long,Long> onProgress) throws Exception {
        return (AesFile) super.importFile(fileToImport, dir, filename, deleteSource, integrity, onProgress);
    }
}