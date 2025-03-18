package com.mku.salmonfs.drive.utils;
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

import com.mku.fs.drive.utils.FileImporter;
import com.mku.fs.file.IFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.salmonfs.file.AesFile;

import java.io.IOException;

/**
 * Imports files into an encrypted virtual drive.
 */
public class AesFileImporter extends FileImporter {


    /**
     * Instantiates a file importer for encrypted files.
     */
    public AesFileImporter() {
        super.initialize(0, 1);
    }

    /**
     * Instantiates a file importer for encrypted files.
     *
     * @param bufferSize The buffer size to be used.
     */
    public AesFileImporter(int bufferSize) {
        super.initialize(bufferSize, 1);
    }

    /**
     * Instantiates a file importer for encrypted files.
     *
     * @param bufferSize The buffer size to be used.
     * @param threads    The parallel threads to use
     */
    public AesFileImporter(int bufferSize, int threads) {
        super.initialize(bufferSize, threads);
    }

    /**
     * Runs before import
     *
     * @param targetFile The target file imported
     * @param integrity  If integrity verification is enabled
     * @throws IOException If there is a problem with the file preparation.
     */
    protected void onPrepare(IVirtualFile targetFile, boolean integrity) throws IOException {
        ((AesFile) targetFile).setAllowOverwrite(true);
        // we use default chunk file size
        ((AesFile) targetFile).setApplyIntegrity(integrity);
    }


    /**
     * Get the minimum part of file that can be imported in parallel.
     *
     * @param file The file
     * @return The number of bytes
     * @throws IOException If there was a problem calculating the size.
     */
    protected long getMinimumPartSize(IVirtualFile file) throws IOException {
        return ((AesFile) file).getMinimumPartSize();
    }

    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in into the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @return The imported file
     * @throws Exception Thrown if error occurs during import
     */
    public AesFile importFile(IFile fileToImport, IVirtualFile dir) throws Exception {
        return importFile(fileToImport, dir, null);
    }

    /**
     * Imports a real file into the drive.
     *
     * @param fileToImport The source file that will be imported in into the drive.
     * @param dir          The target directory in the drive that the file will be imported
     * @param options      The options
     * @return The imported file
     * @throws Exception Thrown if error occurs during import
     */
    public AesFile importFile(IFile fileToImport, IVirtualFile dir, FileImportOptions options) throws Exception {
        return (AesFile) super.importFile(fileToImport, dir, options);
    }
}