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

import com.mku.fs.drive.utils.FileExporter;
import com.mku.fs.file.IVirtualFile;
import com.mku.salmonfs.file.AesFile;

import java.io.IOException;

/**
 * Exports files from an encrypted virtual drive.
 */
public class AesFileExporter extends FileExporter {
	
	/**
     * Instantiates a file exporter for encrypted files.
     *
     */
	public AesFileExporter() {
        super.initialize(0, 1);
    }

    /**
     * Instantiates a file exporter for encrypted files.
     *
     * @param bufferSize The buffer size to be used.
     */
    public AesFileExporter(int bufferSize) {
        super.initialize(bufferSize, 1);
    }

    /**
     * Instantiates a file exporter for encrypted files.
     *
     * @param bufferSize The buffer size to be used.
     * @param threads    The parallel threads to use
     */
    public AesFileExporter(int bufferSize, int threads) {
        super.initialize(bufferSize, threads);
    }

    /**
     * Runs before export
     *
     * @param sourceFile The file that will be imported
     * @param integrity  If integrity verification is enabled
     * @throws IOException If there is a problem with the file preparation.
     */
    protected void onPrepare(IVirtualFile sourceFile, boolean integrity) throws IOException {
        // we use the drive hash key for integrity verification
        ((AesFile) sourceFile).setVerifyIntegrity(integrity);
    }

    /**
     * Get the minimum part of file that can be exported in parallel.
     *
     * @param file The file
     * @return The number of bytes
     * @throws IOException If there was a problem calculating the size.
     */
    protected long getMinimumPartSize(IVirtualFile file) throws IOException {
        return ((AesFile) file).getMinimumPartSize();
    }
}
