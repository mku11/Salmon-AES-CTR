package com.mku.utils;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CancellationException;

import com.mku.func.BiConsumer;
import com.mku.func.Consumer;
import com.mku.file.IRealFile;
import com.mku.func.Function;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmonfs.SalmonAuthException;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.sequence.SalmonSequenceException;

/**
 * Facade class for file operations.
 */
public class SalmonFileCommander {
    private SalmonFileImporter fileImporter;
    private SalmonFileExporter fileExporter;
    private SalmonFileSearcher fileSearcher;
    private boolean stopJobs;

    /**
     * Instantiate a new file commander object.
     *
     * @param importBufferSize The buffer size to use for importing files.
     * @param exportBufferSize The buffer size to use for exporting files.
     */
    public SalmonFileCommander(int importBufferSize, int exportBufferSize, int threads) {
        fileImporter = new SalmonFileImporter(importBufferSize, threads);
        fileExporter = new SalmonFileExporter(exportBufferSize, threads);
        fileSearcher = new SalmonFileSearcher();
    }

    /**
     * Import files to the drive.
     *
     * @param filesToImport     The files to import.
     * @param importDir         The target directory.
     * @param deleteSource      True if you want to delete the source files when import complete.
     * @param integrity         True to apply integrity to imported files.
     * @param onProgressChanged Observer to notify when progress changes.
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails importing
     * @return The imported files if completes successfully.
     * @throws Exception
     */
    public SalmonFile[] importFiles(IRealFile[] filesToImport, SalmonFile importDir,
                                    boolean deleteSource, boolean integrity,
                                    Consumer<RealFileTaskProgress> onProgressChanged,
                                    Function<IRealFile, String> autoRename,
                                    BiConsumer<IRealFile, Exception> onFailed) throws Exception {
        stopJobs = false;
        ArrayList<SalmonFile> importedFiles = new ArrayList<>();

        int total = 0;
        for (int i = 0; i < filesToImport.length; i++)
            total += getCountRecursively(filesToImport[i]);
        int[] count = new int[1];
        HashMap<String, SalmonFile> existingFiles = getExistingFiles(importDir);
        for (int i = 0; i < filesToImport.length; i++) {
            if (stopJobs)
                break;
            importRecursively(filesToImport[i], importDir,
                    deleteSource, integrity,
                    onProgressChanged, autoRename, onFailed,
                    importedFiles, count, total,
                    existingFiles);
        }
        return importedFiles.toArray(new SalmonFile[0]);
    }

    private HashMap<String, SalmonFile> getExistingFiles(SalmonFile importDir) {
        HashMap<String, SalmonFile> files = new HashMap<>();
        for (SalmonFile file : importDir.listFiles()) {
            try {
                files.put(file.getBaseName(), file);
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    private void importRecursively(IRealFile fileToImport, SalmonFile importDir,
                                   boolean deleteSource, boolean integrity,
                                   Consumer<RealFileTaskProgress> onProgressChanged,
                                   Function<IRealFile, String> autoRename, BiConsumer<IRealFile, Exception> onFailed,
                                   ArrayList<SalmonFile> importedFiles, int[] count, int total,
                                   HashMap<String, SalmonFile> existingFiles) throws Exception {
        SalmonFile sfile = existingFiles.getOrDefault(fileToImport.getBaseName(), null);
        if (fileToImport.isDirectory()) {
            if (onProgressChanged != null)
                onProgressChanged.accept(new RealFileTaskProgress(fileToImport, 0, 1, count[0], total));
            if (sfile == null || !sfile.exists())
                sfile = importDir.createDirectory(fileToImport.getBaseName());
            else if (sfile != null && sfile.exists() && sfile.isFile() && autoRename != null)
                sfile = importDir.createDirectory(autoRename.apply(fileToImport));
            if (onProgressChanged != null)
                onProgressChanged.accept(new RealFileTaskProgress(fileToImport, 1, 1, count[0], total));
			count[0]++;
            HashMap<String, SalmonFile> nExistingFiles = getExistingFiles(sfile);
            for (IRealFile child : fileToImport.listFiles())
                importRecursively(child, sfile, deleteSource, integrity, onProgressChanged,
                        autoRename, onFailed, importedFiles, count, total,
                        nExistingFiles);
            if (deleteSource)
                fileToImport.delete();
        } else {
            try {
                String filename = fileToImport.getBaseName();
                if (sfile != null && (sfile.exists() || sfile.isDirectory()) && autoRename != null)
                    filename = autoRename.apply(fileToImport);
                sfile = fileImporter.importFile(fileToImport, importDir, filename, deleteSource, integrity,
                        (bytes, totalBytes) -> {
                            if (onProgressChanged != null) {
                                onProgressChanged.accept(new RealFileTaskProgress(fileToImport,
                                        bytes, totalBytes, count[0], total));
                            }
                        });
                importedFiles.add(sfile);
                count[0]++;
            } catch (SalmonSequenceException ex) {
                throw ex;
            } catch (Exception ex) {
                if (onFailed != null)
                    onFailed.accept(fileToImport, ex);
            }
        }
    }

    /**
     * Export a file from a drive.
     *
     * @param filesToExport     The files to export.
     * @param exportDir         The export target directory
     * @param deleteSource      True if you want to delete the source files
     * @param integrity         True to use integrity verification before exporting files
     * @param onProgressChanged Observer to notify when progress changes.
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails exporting
     * @return The exported files
     * @throws Exception
     */
    public IRealFile[] exportFiles(SalmonFile[] filesToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Consumer<SalmonFileTaskProgress> onProgressChanged,
                                   Function<IRealFile, String> autoRename,
                                   BiConsumer<SalmonFile, Exception> onFailed)
            throws Exception {
        stopJobs = false;
        ArrayList<IRealFile> exportedFiles = new ArrayList<>();

        int total = 0;
        for (int i = 0; i < filesToExport.length; i++)
            total += getCountRecursively(filesToExport[i]);

        HashMap<String, IRealFile> existingFiles = getExistingFiles(exportDir);

        int[] count = new int[1];
        for (int i = 0; i < filesToExport.length; i++) {
            if (stopJobs)
                break;
            exportRecursively(filesToExport[i], exportDir,
                    deleteSource, integrity,
                    onProgressChanged, autoRename, onFailed,
                    exportedFiles, count, total,
                    existingFiles);
        }
        return exportedFiles.toArray(new IRealFile[0]);
    }

    private HashMap<String, IRealFile> getExistingFiles(IRealFile exportDir) {
        HashMap<String, IRealFile> files = new HashMap<>();
        for (IRealFile file : exportDir.listFiles()) {
            files.put(file.getBaseName(), file);
        }
        return files;
    }

    private void exportRecursively(SalmonFile fileToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Consumer<SalmonFileTaskProgress> onProgressChanged,
                                   Function<IRealFile, String> autoRename,
                                   BiConsumer<SalmonFile, Exception> onFailed,
                                   ArrayList<IRealFile> exportedFiles, int[] count, int total,
                                   HashMap<String, IRealFile> existingFiles)
            throws Exception {
        IRealFile rfile = existingFiles.getOrDefault(fileToExport.getBaseName(), null);

        if (fileToExport.isDirectory()) {
            if (rfile == null || !rfile.exists())
                rfile = exportDir.createDirectory(fileToExport.getBaseName());
            else if (rfile != null && rfile.isFile() && autoRename != null)
                rfile = exportDir.createDirectory(autoRename.apply(rfile));
			if (onProgressChanged != null)
                onProgressChanged.accept(new SalmonFileTaskProgress(fileToExport, 1, 1, count[0], total));
			count[0]++;
            HashMap<String, IRealFile> nExistingFiles = getExistingFiles(rfile);
            for (SalmonFile child : fileToExport.listFiles())
                exportRecursively(child, rfile, deleteSource, integrity, onProgressChanged,
                        autoRename, onFailed, exportedFiles, count, total,
                        nExistingFiles);
            if (deleteSource) {
                fileToExport.delete();
            }
        } else {
            try {
                String filename = fileToExport.getBaseName();
                if (rfile != null && rfile.exists() && autoRename != null)
                    filename = autoRename.apply(rfile);
                rfile = fileExporter.exportFile(fileToExport, exportDir, filename, deleteSource, integrity,
                        (bytes, totalBytes) -> {
                            if (onProgressChanged != null) {
                                onProgressChanged.accept(new SalmonFileTaskProgress(fileToExport,
                                        bytes, totalBytes, count[0], total));
                            }
                        });
                exportedFiles.add(rfile);
                count[0]++;
            } catch (SalmonSequenceException ex) {
                throw ex;
            } catch (Exception ex) {
                if (onFailed != null)
                    onFailed.accept(fileToExport, ex);
            }
        }
    }

    private int getCountRecursively(SalmonFile file) {
        int count = 1;
        if (file.isDirectory()) {
            for (SalmonFile child : file.listFiles()) {
                count += getCountRecursively(child);
            }
        }
        return count;
    }

    private int getCountRecursively(IRealFile file) {
        int count = 1;
        if (file.isDirectory()) {
            for (IRealFile child : file.listFiles()) {
                count += getCountRecursively(child);
            }
        }
        return count;
    }

    /**
     * Delete files.
     *
     * @param filesToDelete         The files to delete.
     * @param OnProgressChanged The observer to notify when each file is deleted.
     * @param onFailed The observer to notify when a file has failed.
     */
    public void deleteFiles(SalmonFile[] filesToDelete, Consumer<SalmonFileTaskProgress> OnProgressChanged,
                            BiConsumer<SalmonFile, Exception> onFailed)
    {
        stopJobs = false;
        int[] count = new int[1];
        int total = 0;
        for (int i = 0; i < filesToDelete.length; i++)
            total += getCountRecursively(filesToDelete[i]);
        for (SalmonFile salmonFile : filesToDelete)
        {
            if (stopJobs)
                break;
            int finalTotal = total;
            salmonFile.DeleteRecursively((file, position, length) ->
            {
                if (stopJobs)
                    throw new CancellationException();
                if (OnProgressChanged != null)
                {
                    try
                    {
                        OnProgressChanged.accept(new SalmonFileTaskProgress(
                                file, position, length, count[0], finalTotal));
                    }
                    catch (Exception ex)
                    {
                    }
                }
				if (position == (long) length)
					count[0]++;
            }, onFailed);
        }
    }

    /**
     * Copy files to another directory.
     *
     * @param filesToCopy       The array of files to copy.
     * @param dir               The target directory.
     * @param move              True if moving files instead of copying.
     * @param onProgressChanged The progress change observer to notify.
     * @param autoRename        The auto rename function to use when files with same filename are found
     * @param onFailed          The observer to notify when failures occur
     * @throws Exception
     */
    public void copyFiles(SalmonFile[] filesToCopy, SalmonFile dir, boolean move, Consumer<SalmonFileTaskProgress> onProgressChanged,
                          Function<SalmonFile, String> autoRename,
                          boolean autoRenameFolders, BiConsumer<SalmonFile, Exception> onFailed)
            throws Exception {
        stopJobs = false;
        int[] count = new int[1];
        int total = 0;
        for (int i = 0; i < filesToCopy.length; i++)
            total += getCountRecursively(filesToCopy[i]);
        final int finalTotal = total;
        for (SalmonFile salmonFile : filesToCopy) {
            if (dir.getRealFile().getPath().startsWith(salmonFile.getRealFile().getPath()))
                continue;

            if (stopJobs)
                break;

            if (move) {
                salmonFile.moveRecursively(dir, (file, position, length) ->
                {
                    if (stopJobs)
                        throw new CancellationException();
                    if (onProgressChanged != null) {
                        try {
                            onProgressChanged.accept(new SalmonFileTaskProgress(
                                    file, position, length, count[0], finalTotal));
                        } catch (Exception ex) {
                        }
                    }
					if (position == (long) length)
                        count[0]++;
                }, autoRename, autoRenameFolders, onFailed);
            } else {
                salmonFile.copyRecursively(dir, (file, position, length) ->
                {
                    if (stopJobs)
                        throw new CancellationException();
                    if (onProgressChanged != null) {
                        try {
                            onProgressChanged.accept(new SalmonFileTaskProgress(file, position, length, count[0], finalTotal));
                        } catch (Exception ignored) {
                        }
                    }
					if (position == (long) length)
                        count[0]++;
                }, autoRename, autoRenameFolders, onFailed);
            }
        }
    }

    /**
     * Cancel all jobs.
     */
    public void cancel() {
        stopJobs = true;
        fileImporter.stop();
        fileExporter.stop();
        fileSearcher.stop();
    }

    /**
     * True if the file search is currently running.
     *
     * @return
     */
    public boolean isFileSearcherRunning() {
        return fileSearcher.isRunning();
    }

    /**
     * True if jobs are currently running.
     *
     * @return
     */
    public boolean isRunning() {
        return fileSearcher.isRunning() || fileImporter.isRunning() || fileExporter.isRunning();
    }

    /**
     * True if file search stopped.
     *
     * @return
     */
    public boolean isFileSearcherStopped() {
        return fileSearcher.isStopped();
    }

    /**
     * Stop file search.
     */
    public void stopFileSearch() {
        fileSearcher.stop();
    }

    /**
     * Search
     *
     * @param dir           The directory to start the search.
     * @param terms         The terms to search for.
     * @param any           True if you want to match any term otherwise match all terms.
     * @param OnResultFound Callback interface to receive notifications when results found.
     * @param OnSearchEvent Callback interface to receive status events.
     * @return An array with all the results found.
     */
    public SalmonFile[] search(SalmonFile dir, String terms, boolean any,
                               SalmonFileSearcher.OnResultFoundListener OnResultFound,
                               Consumer<SalmonFileSearcher.SearchEvent> OnSearchEvent) {
        return fileSearcher.search(dir, terms, any, OnResultFound, OnSearchEvent);
    }

    /**
     * True if all jobs are stopped.
     *
     * @return
     */
    public boolean areJobsStopped() {
        return stopJobs;
    }

    /**
     * Get number of files recursively for the files provided.
     *
     * @param files Total number of files and files under subdirectories.
     * @return
     */
    private int getFiles(SalmonFile[] files) {
        int total = 0;
        for (SalmonFile file : files) {
            total++;
            if (file.isDirectory()) {
                total += getFiles(file.listFiles());
            }
        }
        return total;
    }

    public void close() {
        fileImporter.close();
        fileExporter.close();
    }
	
	/**
     * Rename an encrypted file
     *
     */
	public void renameFile(SalmonFile ifile, String newFilename) 
		throws IOException, SalmonSecurityException, SalmonIntegrityException, 
		SalmonAuthException, SalmonSequenceException, SalmonRangeExceededException
    {
        ifile.rename(newFilename);
    }

    /**
     * File task progress class.
     */
    private static class FileTaskProgress {
        private final long processedBytes;
        private final long totalBytes;
        private final int processedFiles;
        private final int totalFiles;

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getProcessedBytes() {
            return processedBytes;
        }

        public int getProcessedFiles() {
            return processedFiles;
        }

        public int getTotalFiles() {
            return totalFiles;
        }

        private FileTaskProgress(long processedBytes, long totalBytes, int processedFiles, int totalFiles) {
            this.processedBytes = processedBytes;
            this.totalBytes = totalBytes;
            this.processedFiles = processedFiles;
            this.totalFiles = totalFiles;
        }
    }

    public class SalmonFileTaskProgress extends FileTaskProgress {
        private final SalmonFile file;

        public SalmonFile getFile() {
            return file;
        }

        private SalmonFileTaskProgress(SalmonFile file, long processedBytes, long totalBytes,
                                       int processedFiles, int totalFiles) {
            super(processedBytes, totalBytes, processedFiles, totalFiles);
            this.file = file;
        }
    }


    public class RealFileTaskProgress extends FileTaskProgress {
        public IRealFile getFile() {
            return file;
        }

        private final IRealFile file;

        private RealFileTaskProgress(IRealFile file, long processedBytes, long totalBytes,
                                     int processedFiles, int totalFiles) {
            super(processedBytes, totalBytes, processedFiles, totalFiles);
            this.file = file;
        }
    }
}
