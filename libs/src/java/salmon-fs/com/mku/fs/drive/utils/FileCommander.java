package com.mku.fs.drive.utils;
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
import com.mku.func.Consumer;
import com.mku.func.Function;
import com.mku.salmon.sequence.SequenceException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CancellationException;

/**
 * Facade class for file operations.
 */
public class FileCommander {
    private FileImporter fileImporter;
    private FileExporter fileExporter;
    private FileSearcher fileSearcher;
    private boolean stopJobs;

    /**
     * Instantiate a new file commander object.
     *
     * @param fileImporter The importer to use
     * @param fileExporter The exporter to use
     * @param fileSearcher The searcher to use
     */
    public FileCommander(FileImporter fileImporter, FileExporter fileExporter, FileSearcher fileSearcher) {
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.fileSearcher = fileSearcher;
    }

    /**
     * Get the file importer.
     *
     * @return The file importer.
     */
    public FileImporter getFileImporter() {
        return fileImporter;
    }

    /**
     * Get the file exporter.
     *
     * @return The file exporter.
     */
    public FileExporter getFileExporter() {
        return fileExporter;
    }

    /**
     * Get the file searcher.
     *
     * @return The file searcher.
     */
    public FileSearcher getFileSearcher() {
        return fileSearcher;
    }

    /**
     * Import IRealFile(s) into the drive.
     *
     * @param filesToImport The files to import.
     * @param importDir     The target directory.
     * @param deleteSource  True if you want to delete the source files when import complete.
     * @param integrity     True to apply integrity to imported files.
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile[] importFiles(IRealFile[] filesToImport, IVirtualFile importDir,
                                      boolean deleteSource, boolean integrity) throws Exception {
        return importFiles(filesToImport, importDir, deleteSource, integrity, null, null, null);
    }

    /**
     * Import IRealFile(s) into the drive.
     *
     * @param filesToImport The files to import.
     * @param importDir     The target directory.
     * @param deleteSource  True if you want to delete the source files when import complete.
     * @param integrity     True to apply integrity to imported files.
     * @param autoRename    Function to rename file if another file with the same filename exists
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile[] importFiles(IRealFile[] filesToImport, IVirtualFile importDir,
                                      boolean deleteSource, boolean integrity,
                                      Function<IRealFile, String> autoRename) throws Exception {
        return importFiles(filesToImport, importDir, deleteSource, integrity, autoRename, null, null);
    }


    /**
     * Import IRealFile(s) into the drive.
     *
     * @param filesToImport The files to import.
     * @param importDir     The target directory.
     * @param deleteSource  True if you want to delete the source files when import complete.
     * @param integrity     True to apply integrity to imported files.
     * @param autoRename    Function to rename file if another file with the same filename exists
     * @param onFailed      Observer to notify when a file fails importing
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile[] importFiles(IRealFile[] filesToImport, IVirtualFile importDir,
                                      boolean deleteSource, boolean integrity,
                                      Function<IRealFile, String> autoRename,
                                      BiConsumer<IRealFile, Exception> onFailed) throws Exception {
        return importFiles(filesToImport, importDir, deleteSource, integrity, autoRename, onFailed, null);
    }

    /**
     * Import IRealFile(s) into the drive.
     *
     * @param filesToImport     The files to import.
     * @param importDir         The target directory.
     * @param deleteSource      True if you want to delete the source files when import complete.
     * @param integrity         True to apply integrity to imported files.
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails importing
     * @param onProgressChanged Observer to notify when progress changes.
     * @return The imported files.
     * @throws Exception Thrown if error occurs during import
     */
    public IVirtualFile[] importFiles(IRealFile[] filesToImport, IVirtualFile importDir,
                                      boolean deleteSource, boolean integrity,
                                      Function<IRealFile, String> autoRename,
                                      BiConsumer<IRealFile, Exception> onFailed,
                                      Consumer<RealFileTaskProgress> onProgressChanged) throws Exception {
        stopJobs = false;
        ArrayList<IVirtualFile> importedFiles = new ArrayList<>();

        int total = 0;
        for (int i = 0; i < filesToImport.length; i++) {
            if (stopJobs)
                break;
            total += getCountRecursively(filesToImport[i]);
        }
        int[] count = new int[1];
        HashMap<String, IVirtualFile> existingFiles = getExistingFiles(importDir);
        for (int i = 0; i < filesToImport.length; i++) {
            if (stopJobs)
                break;
            importRecursively(filesToImport[i], importDir,
                    deleteSource, integrity,
                    onProgressChanged, autoRename, onFailed,
                    importedFiles, count, total,
                    existingFiles);
        }
        return importedFiles.toArray(new IVirtualFile[0]);
    }

    private HashMap<String, IVirtualFile> getExistingFiles(IVirtualFile importDir) {
        HashMap<String, IVirtualFile> files = new HashMap<>();
        for (IVirtualFile file : importDir.listFiles()) {
            if (stopJobs)
                break;
            try {
                files.put(file.getName(), file);
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    private void importRecursively(IRealFile fileToImport, IVirtualFile importDir,
                                   boolean deleteSource, boolean integrity,
                                   Consumer<RealFileTaskProgress> onProgressChanged,
                                   Function<IRealFile, String> autoRename, BiConsumer<IRealFile, Exception> onFailed,
                                   ArrayList<IVirtualFile> importedFiles, int[] count, int total,
                                   HashMap<String, IVirtualFile> existingFiles) throws IOException {
        IVirtualFile sfile = existingFiles.containsKey(fileToImport.getName())
                ? existingFiles.get(fileToImport.getName()) : null;
        if (fileToImport.isDirectory()) {
            if (onProgressChanged != null)
                onProgressChanged.accept(new RealFileTaskProgress(fileToImport, 0, 1, count[0], total));
            if (sfile == null || !sfile.exists())
                sfile = importDir.createDirectory(fileToImport.getName());
            else if (sfile != null && sfile.exists() && sfile.isFile() && autoRename != null)
                sfile = importDir.createDirectory(autoRename.apply(fileToImport));
            if (onProgressChanged != null)
                onProgressChanged.accept(new RealFileTaskProgress(fileToImport, 1, 1, count[0], total));
            count[0]++;
            HashMap<String, IVirtualFile> nExistingFiles = getExistingFiles(sfile);
            for (IRealFile child : fileToImport.listFiles()) {
                if (stopJobs)
                    break;
                importRecursively(child, sfile, deleteSource, integrity, onProgressChanged,
                        autoRename, onFailed, importedFiles, count, total,
                        nExistingFiles);
            }
            if (deleteSource && !stopJobs)
                fileToImport.delete();
        } else {
            try {
                String filename = fileToImport.getName();
                if (sfile != null && (sfile.exists() || sfile.isDirectory()) && autoRename != null)
                    filename = autoRename.apply(fileToImport);
                sfile = fileImporter.importFile(fileToImport, importDir, filename, deleteSource, integrity,
                        (bytes, totalBytes) -> {
                            if (onProgressChanged != null) {
                                onProgressChanged.accept(new RealFileTaskProgress(fileToImport,
                                        bytes, totalBytes, count[0], total));
                            }
                        });
                existingFiles.put(sfile.getName(), sfile);
                importedFiles.add(sfile);
                count[0]++;
            } catch (SequenceException ex) {
                throw ex;
            } catch (Exception ex) {
                if (onFailed != null)
                    onFailed.accept(fileToImport, ex);
            }
        }
    }

    /**
     * Export IVirtualFile(s) from the drive.
     *
     * @param filesToExport The files to export.
     * @param exportDir     The export target directory
     * @param deleteSource  True if you want to delete the source files
     * @param integrity     True to use integrity verification before exporting files
     * @return The exported files
     * @throws Exception Thrown if error occurs during export
     */
    public IRealFile[] exportFiles(IVirtualFile[] filesToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity)
            throws Exception {
        return exportFiles(filesToExport, exportDir, deleteSource, integrity, null, null, null);
    }


    /**
     * Export IVirtualFile(s) from the drive.
     *
     * @param filesToExport The files to export.
     * @param exportDir     The export target directory
     * @param deleteSource  True if you want to delete the source files
     * @param integrity     True to use integrity verification before exporting files
     * @param autoRename    Function to rename file if another file with the same filename exists
     * @return The exported files
     * @throws Exception Thrown if error occurs during export
     */
    public IRealFile[] exportFiles(IVirtualFile[] filesToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Function<IRealFile, String> autoRename)
            throws Exception {
        return exportFiles(filesToExport, exportDir, deleteSource, integrity, autoRename, null, null);
    }


    /**
     * Export IVirtualFile(s) from the drive.
     *
     * @param filesToExport The files to export.
     * @param exportDir     The export target directory
     * @param deleteSource  True if you want to delete the source files
     * @param integrity     True to use integrity verification before exporting files
     * @param autoRename    Function to rename file if another file with the same filename exists
     * @param onFailed      Observer to notify when a file fails exporting
     * @return The exported files
     * @throws Exception Thrown if error occurs during export
     */
    public IRealFile[] exportFiles(IVirtualFile[] filesToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Function<IRealFile, String> autoRename,
                                   BiConsumer<IVirtualFile, Exception> onFailed)
            throws Exception {
        return exportFiles(filesToExport, exportDir, deleteSource, integrity, autoRename, onFailed, null);
    }

    /**
     * Export IVirtualFile(s) from the drive.
     *
     * @param filesToExport     The files to export.
     * @param exportDir         The export target directory
     * @param deleteSource      True if you want to delete the source files
     * @param integrity         True to use integrity verification before exporting files
     * @param autoRename        Function to rename file if another file with the same filename exists
     * @param onFailed          Observer to notify when a file fails exporting
     * @param onProgressChanged Observer to notify when progress changes.
     * @return The exported files
     * @throws Exception Thrown if error occurs during export
     */
    public IRealFile[] exportFiles(IVirtualFile[] filesToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Function<IRealFile, String> autoRename,
                                   BiConsumer<IVirtualFile, Exception> onFailed,
                                   Consumer<VirtualFileTaskProgress> onProgressChanged)
            throws Exception {
        stopJobs = false;
        ArrayList<IRealFile> exportedFiles = new ArrayList<>();

        int total = 0;
        for (int i = 0; i < filesToExport.length; i++) {
            if (stopJobs)
                break;
            total += getCountRecursively(filesToExport[i]);
        }

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
            if (stopJobs)
                break;
            files.put(file.getName(), file);
        }
        return files;
    }

    private void exportRecursively(IVirtualFile fileToExport, IRealFile exportDir,
                                   boolean deleteSource, boolean integrity,
                                   Consumer<VirtualFileTaskProgress> onProgressChanged,
                                   Function<IRealFile, String> autoRename,
                                   BiConsumer<IVirtualFile, Exception> onFailed,
                                   ArrayList<IRealFile> exportedFiles, int[] count, int total,
                                   HashMap<String, IRealFile> existingFiles)
            throws Exception {
        IRealFile rfile = existingFiles.containsKey(fileToExport.getName())
                ? existingFiles.get(fileToExport.getName()) : null;

        if (fileToExport.isDirectory()) {
            if (rfile == null || !rfile.exists())
                rfile = exportDir.createDirectory(fileToExport.getName());
            else if (rfile != null && rfile.isFile() && autoRename != null)
                rfile = exportDir.createDirectory(autoRename.apply(rfile));
            if (onProgressChanged != null)
                onProgressChanged.accept(new VirtualFileTaskProgress(fileToExport, 1, 1, count[0], total));
            count[0]++;
            HashMap<String, IRealFile> nExistingFiles = getExistingFiles(rfile);
            for (IVirtualFile child : fileToExport.listFiles()) {
                if (stopJobs)
                    break;
                exportRecursively(child, rfile, deleteSource, integrity, onProgressChanged,
                        autoRename, onFailed, exportedFiles, count, total,
                        nExistingFiles);
            }
            if (deleteSource && !stopJobs) {
                fileToExport.delete();
            }
        } else {
            try {
                String filename = fileToExport.getName();
                if (rfile != null && rfile.exists() && autoRename != null)
                    filename = autoRename.apply(rfile);
                rfile = fileExporter.exportFile(fileToExport, exportDir, filename, deleteSource, integrity,
                        (bytes, totalBytes) -> {
                            if (onProgressChanged != null) {
                                onProgressChanged.accept(new VirtualFileTaskProgress(fileToExport,
                                        bytes, totalBytes, count[0], total));
                            }
                        });
                existingFiles.put(rfile.getName(), rfile);
                exportedFiles.add(rfile);
                count[0]++;
            } catch (SequenceException ex) {
                throw ex;
            } catch (Exception ex) {
                if (onFailed != null)
                    onFailed.accept(fileToExport, ex);
            }
        }
    }

    private int getCountRecursively(IVirtualFile file) {
        int count = 1;
        if (file.isDirectory()) {
            for (IVirtualFile child : file.listFiles()) {
                if (stopJobs)
                    break;
                count += getCountRecursively(child);
            }
        }
        return count;
    }

    private int getCountRecursively(IRealFile file) {
        int count = 1;
        if (file.isDirectory()) {
            for (IRealFile child : file.listFiles()) {
                if (stopJobs)
                    break;
                count += getCountRecursively(child);
            }
        }
        return count;
    }

    /**
     * Delete files from a drive.
     *
     * @param filesToDelete The files to delete.
     */
    public void deleteFiles(IVirtualFile[] filesToDelete) {
        deleteFiles(filesToDelete, null, null);
    }

    /**
     * Delete files from a drive.
     *
     * @param filesToDelete The files to delete.
     * @param onFailed      The observer to notify when a file has failed.
     */
    public void deleteFiles(IVirtualFile[] filesToDelete,
                            BiConsumer<IVirtualFile, Exception> onFailed) {
        deleteFiles(filesToDelete, onFailed, null);
    }

    /**
     * Delete files from a drive.
     *
     * @param filesToDelete     The files to delete.
     * @param onFailed          The observer to notify when a file has failed.
     * @param onProgressChanged The observer to notify when each file is deleted.
     */
    public void deleteFiles(IVirtualFile[] filesToDelete,
                            BiConsumer<IVirtualFile, Exception> onFailed,
                            Consumer<VirtualFileTaskProgress> onProgressChanged) {
        stopJobs = false;
        int[] count = new int[1];
        int total = 0;
        for (int i = 0; i < filesToDelete.length; i++) {
            if (stopJobs)
                break;
            total += getCountRecursively(filesToDelete[i]);
        }
        for (IVirtualFile virtualFile : filesToDelete) {
            if (stopJobs)
                break;
            int finalTotal = total;
            virtualFile.deleteRecursively(onFailed, (file, position, length) ->
            {
                if (stopJobs)
                    throw new CancellationException();
                if (onProgressChanged != null) {
                    try {
                        onProgressChanged.accept(new VirtualFileTaskProgress(
                                file, position, length, count[0], finalTotal));
                    } catch (Exception ex) {
                    }
                }
                if (position == (long) length)
                    count[0]++;
            });
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
     * @param autoRenameFolders True to autorename folders
     * @param onFailed          The observer to notify when failures occur
     * @throws Exception Thrown if error occurs during copying
     */
    public void copyFiles(IVirtualFile[] filesToCopy, IVirtualFile dir, boolean move, Consumer<VirtualFileTaskProgress> onProgressChanged,
                          Function<IVirtualFile, String> autoRename,
                          boolean autoRenameFolders, BiConsumer<IVirtualFile, Exception> onFailed)
            throws Exception {
        stopJobs = false;
        int[] count = new int[1];
        int total = 0;
        for (int i = 0; i < filesToCopy.length; i++) {
            if (stopJobs)
                break;
            total += getCountRecursively(filesToCopy[i]);
        }
        final int finalTotal = total;
        for (IVirtualFile VirtualFile : filesToCopy) {
            if (stopJobs)
                break;
            if (dir.getRealFile().getPath().startsWith(VirtualFile.getRealFile().getPath()))
                continue;
            if (move) {
                VirtualFile.moveRecursively(dir, autoRename, autoRenameFolders, onFailed,
                        (file, position, length) ->
                        {
                            if (stopJobs)
                                throw new CancellationException();
                            if (onProgressChanged != null) {
                                try {
                                    onProgressChanged.accept(new VirtualFileTaskProgress(
                                            file, position, length, count[0], finalTotal));
                                } catch (Exception ex) {
                                }
                            }
                            if (position == (long) length)
                                count[0]++;
                        });
            } else {
                VirtualFile.copyRecursively(dir, autoRename, autoRenameFolders, onFailed,
                        (file, position, length) ->
                        {
                            if (stopJobs)
                                throw new CancellationException();
                            if (onProgressChanged != null) {
                                try {
                                    onProgressChanged.accept(new VirtualFileTaskProgress(file, position, length, count[0], finalTotal));
                                } catch (Exception ignored) {
                                }
                            }
                            if (position == (long) length)
                                count[0]++;
                        });
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
     * Check if file search is currently running.
     *
     * @return True if search is running
     */
    public boolean isFileSearcherRunning() {
        return fileSearcher.isRunning();
    }

    /**
     * Check if jobs are currently running.
     *
     * @return True if a job is running
     */
    public boolean isRunning() {
        return fileSearcher.isRunning() || fileImporter.isRunning() || fileExporter.isRunning();
    }

    /**
     * Check if file search stopped.
     *
     * @return True if file search is stopped
     */
    public boolean isFileSearcherStopped() {
        return fileSearcher.isStopped();
    }

    /**
     * Stop the current file search.
     */
    public void stopFileSearch() {
        fileSearcher.stop();
    }


    /**
     * Search for files in a drive.
     *
     * @param dir   The directory to start the search.
     * @param terms The terms to search for.
     * @param any   True if you want to match any term otherwise match all terms.
     * @return An array with all the results found.
     */
    public IVirtualFile[] search(IVirtualFile dir, String terms, boolean any) {
        return search(dir, terms, any);
    }

    /**
     * Search for files in a drive.
     *
     * @param dir           The directory to start the search.
     * @param terms         The terms to search for.
     * @param any           True if you want to match any term otherwise match all terms.
     * @param OnResultFound Callback interface to receive notifications when results found.
     * @param OnSearchEvent Callback interface to receive status events.
     * @return An array with all the results found.
     */
    public IVirtualFile[] search(IVirtualFile dir, String terms, boolean any,
                                 FileSearcher.OnResultFoundListener OnResultFound,
                                 Consumer<FileSearcher.SearchEvent> OnSearchEvent) {
        return fileSearcher.search(dir, terms, any, OnResultFound, OnSearchEvent);
    }

    /**
     * Check if all jobs are stopped.
     *
     * @return True if jobs are stopped
     */
    public boolean areJobsStopped() {
        return stopJobs;
    }

    /**
     * Get number of files recursively for the files provided.
     *
     * @param files Total number of files and files under subdirectories.
     * @return The number of all files and subdirectories
     */
    private int getFiles(IVirtualFile[] files) {
        int total = 0;
        for (IVirtualFile file : files) {
            if (stopJobs)
                break;
            total++;
            if (file.isDirectory()) {
                total += getFiles(file.listFiles());
            }
        }
        return total;
    }

    /**
     * Close the commander and release resources.
     */
    public void close() {
        fileImporter.close();
        fileExporter.close();
    }

    /**
     * Rename an encrypted file
     *
     * @param ifile       The file to rename
     * @param newFilename The new file name
     * @throws IOException Thrown if there is an IO error.
     */
    public void renameFile(IVirtualFile ifile, String newFilename) throws IOException {
        ifile.rename(newFilename);
    }

    /**
     * Task progress for File(s).
     */
    private static class FileTaskProgress {
        private final long processedBytes;
        private final long totalBytes;
        private final int processedFiles;
        private final int totalFiles;

        /**
         * Get the file size.
         *
         * @return The file size in bytes.
         */
        public long getTotalBytes() {
            return totalBytes;
        }

        /**
         * Get the bytes processed.
         *
         * @return The byte processed.
         */
        public long getProcessedBytes() {
            return processedBytes;
        }

        /**
         * Get the number of files processed.
         *
         * @return The number of files processed.
         */
        public int getProcessedFiles() {
            return processedFiles;
        }

        /**
         * Get the total number of files submitted.
         *
         * @return The total number of files submitted.
         */
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

    /**
     * Task progress for IVirtualFile(s).
     */
    public class VirtualFileTaskProgress extends FileTaskProgress {
        private final IVirtualFile file;


        /**
         * Get the associated file.
         *
         * @return The file.
         */
        public IVirtualFile getFile() {
            return file;
        }

        private VirtualFileTaskProgress(IVirtualFile file, long processedBytes, long totalBytes,
                                        int processedFiles, int totalFiles) {
            super(processedBytes, totalBytes, processedFiles, totalFiles);
            this.file = file;
        }
    }

    /**
     * Task progress for IRealFile(s).
     */
    public class RealFileTaskProgress extends FileTaskProgress {
        /**
         * Get the associated file.
         *
         * @return
         */
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
