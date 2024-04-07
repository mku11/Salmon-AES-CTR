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
import com.mku.func.Consumer;
import com.mku.func.Function;
import com.mku.salmon.SalmonFile;
import com.mku.utils.FileCommander;
import com.mku.utils.FileSearcher;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Facade class for file operations.
 */
public class SalmonFileCommander extends FileCommander {
    /**
     * Instantiate a new file commander object.
     *
     * @param importBufferSize The buffer size to use for importing files.
     * @param exportBufferSize The buffer size to use for exporting files.
     * @param threads The threads to use for import and export
     */
    public SalmonFileCommander(int importBufferSize, int exportBufferSize, int threads) {
        super(new SalmonFileImporter(importBufferSize, threads),
                new SalmonFileExporter(exportBufferSize, threads),
                new FileSearcher());
    }

    public SalmonFile[] importFiles(IRealFile[] filesToImport, IVirtualFile importDir,
                                      boolean deleteSource, boolean integrity,
                                      Consumer<RealFileTaskProgress> onProgressChanged,
                                      Function<IRealFile, String> autoRename,
                                      BiConsumer<IRealFile, Exception> onFailed) throws Exception {
        IVirtualFile[] files = super.importFiles(filesToImport, importDir, deleteSource, integrity, onProgressChanged,
                autoRename, onFailed);
        return Arrays.stream(files).map((x) -> (SalmonFile) x).collect(Collectors.toList()).toArray(new SalmonFile[0]);
    }
}
