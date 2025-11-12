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

import com.mku.fs.drive.utils.FileCommander;
import com.mku.fs.drive.utils.FileSearcher;
import com.mku.fs.file.IFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.salmon.sequence.SequenceException;
import com.mku.salmonfs.file.AesFile;

import java.util.ArrayList;

/**
 * Facade class for encrypted file operations in batch.
 */
public class AesFileCommander extends FileCommander {
    /**
     * Instantiate a new file commander object.
     *
     * @param importBufferSize The buffer size to use for importing files.
     * @param exportBufferSize The buffer size to use for exporting files.
     * @param threads          The threads to use for import and export
     */
    public AesFileCommander(int importBufferSize, int exportBufferSize, int threads) {
        super(new AesFileImporter(importBufferSize, threads),
                new AesFileExporter(exportBufferSize, threads),
                new FileSearcher());
    }

    /**
     * Import IFile(s) into the drive.
     *
     * @param filesToImport The files to import.
     * @param importDir     The target directory.
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    @Override
    public AesFile[] importFiles(IFile[] filesToImport, IVirtualFile importDir) throws Exception {
        return importFiles(filesToImport, importDir, null);
    }

    /**
     * Import IFile(s) into the drive.
     *
     * @param filesToImport The files to import.
     * @param importDir     The target directory.
     * @param options       The options
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    @Override
    public AesFile[] importFiles(IFile[] filesToImport, IVirtualFile importDir,
                                 BatchImportOptions options) throws Exception {
        IVirtualFile[] files = super.importFiles(filesToImport, importDir, options);
        ArrayList<AesFile> sfiles = new ArrayList<>();
        for (IVirtualFile file : files)
            sfiles.add((AesFile) file);
        return sfiles.toArray(new AesFile[0]);
    }

    /**
     * Export AesFile(s) from the drive.
     *
     * @param filesToExport The files to export.
     * @param exportDir     The export target directory
     * @param options       The options
     * @return The exported files
     * @throws Exception Thrown if error occurs during export
     */
    public IFile[] exportFiles(IVirtualFile[] filesToExport, IFile exportDir, BatchExportOptions options)
            throws Exception {
        return super.exportFiles(filesToExport, exportDir, options);
    }
	
    /**
     * Handle the error
     * @param ex The exception
     * @return True if recoverable
     */
    protected boolean onError(Exception ex) throws Exception {
		if (ex instanceof SequenceException)
            throw ex;
		else
			return false;
    }
}
