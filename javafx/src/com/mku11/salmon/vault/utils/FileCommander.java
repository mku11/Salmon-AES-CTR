package com.mku11.salmon.vault.utils;
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

import com.mku11.salmon.SalmonTime;
import com.mku11.salmon.vault.controller.SalmonFileItem;
import com.mku11.salmonfs.*;

import java.util.ArrayList;
import java.util.function.Function;

public class FileCommander {
    private final int exportBufferSize;
    private final int importBufferSize;
    private SalmonFileImporter fileImporter;
    private SalmonFileExporter fileExporter;
    private SalmonFileSearcher fileSearcher;
    private boolean stopJobs;

    public FileCommander(int importBufferSize, int exportBufferSize) {
        this.importBufferSize = importBufferSize;
        this.exportBufferSize = exportBufferSize;
        setupFileTools();
    }

    private void setupFileTools() {
        fileImporter = new SalmonFileImporter(importBufferSize, exportBufferSize, null);
        fileExporter = new SalmonFileExporter(importBufferSize, exportBufferSize);
        fileSearcher = new SalmonFileSearcher();
    }

    public void cancelJobs() {
        stopJobs = true;
        fileImporter.stop();
        fileExporter.stop();
        fileSearcher.stop();
    }

    public boolean doImportFiles(IRealFile[] filesToImport, SalmonFile importDir, boolean deleteSource,
                                 Function<Integer, Void> OnProgressChanged, Function<SalmonFile[], Void> OnFinished) {
        stopJobs = false;
        ArrayList<SalmonFile> importedFiles = new ArrayList<>();
        for (int i = 0; i < filesToImport.length; i++) {
            if (stopJobs)
                break;
            SalmonFile salmonFile = null;
            try {
                salmonFile = fileImporter.importFile(filesToImport[i], importDir, deleteSource, null, i, filesToImport.length);
                if (OnProgressChanged != null)
                    OnProgressChanged.apply((int) ((i + 1) * 100F / filesToImport.length));
            } catch (Exception e) {
                e.printStackTrace();
            }
            importedFiles.add(salmonFile);
        }
        if (OnFinished != null)
            OnFinished.apply(importedFiles.toArray(new SalmonFile[0]));
        return true;
    }

    public boolean doExportFiles(SalmonFile[] filesToExport, Function<Integer, Void> OnProgressChanged,
                                 Function<IRealFile[], Void> OnFinished) {
        stopJobs = false;
        ArrayList<IRealFile> exportedFiles = new ArrayList<>();
        IRealFile exportDir = SalmonDriveManager.getDrive().getExportDir();
        exportDir = exportDir.createDirectory(SalmonTime.currentTimeMillis() + "");

        for (int i = 0; i < filesToExport.length; i++) {
            if (stopJobs)
                break;

            IRealFile realFile;
            try {
                SalmonFile fileToExport = filesToExport[i];
                if (fileToExport.isDirectory()) {
                    if (!exportDir.exists())
                        exportDir.mkdir();
                    String targetDirName = fileToExport.getBaseName();
                    realFile = exportDir.createDirectory(targetDirName);
                } else {
                    realFile = fileExporter.exportFile(fileToExport, exportDir, true, null, i, filesToExport.length);
                }
                exportedFiles.add(realFile);
                if (OnProgressChanged != null)
                    OnProgressChanged.apply((int) ((i + 1) * 100F / filesToExport.length));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (OnFinished != null)
            OnFinished.apply(exportedFiles.toArray(new IRealFile[0]));
        return true;
    }


    public void doDeleteFiles(Function<SalmonFileItem, Void> OnFinished, SalmonFileItem[] files) {
        stopJobs = false;
        for (SalmonFileItem ifile : files) {
            if (stopJobs)
                break;
            ifile.delete();
            if (OnFinished != null)
                OnFinished.apply(ifile);
        }
    }

    public void setFileImporterOnTaskProgressChanged(SalmonFileImporter.OnProgressChanged listener) {
        fileImporter.onTaskProgressChanged = listener;
    }

    public void setFileExporterOnTaskProgressChanged(SalmonFileExporter.OnProgressChanged listener) {
        fileExporter.onTaskProgressChanged = listener;
    }

    public boolean isFileSearcherRunning() {
        return fileSearcher.isRunning();
    }

    public boolean isFileSearcherStopped() {
        return fileSearcher.isStopped();
    }

    public void stopFileSearch() {
        fileSearcher.stop();
    }

    public SalmonFile[] search(SalmonFile dir, String value, boolean any, SalmonFileSearcher.OnResultFoundListener OnResultFound) {
        return fileSearcher.search(dir, value, any, OnResultFound);
    }

    public boolean isStopped() {
        return stopJobs;
    }

    public static class FileTaskProgress {
        public double fileProgress;
        public int processedFiles;
        public int totalFiles;
        public String filename;

        public FileTaskProgress(String filename, double fileProgress, int processedFiles, int totalFiles) {
            this.fileProgress = fileProgress;
            this.processedFiles = processedFiles;
            this.totalFiles = totalFiles;
            this.filename = filename;
        }
    }

    public void DoCopyFiles(SalmonFile[] files, SalmonFile dir, boolean move, Function<FileTaskProgress, Void> OnProgressChanged) throws Exception {
        stopJobs = false;
        int count = 0;
        int total = GetFilesRecurrsively(files);
        for (SalmonFile ifile : files) {
            if (dir.getPath().startsWith(ifile.getPath()))
                continue;
            if (stopJobs)
                break;
            int finalCount = count;
            if (move) {
                ifile.move(dir, (position, length) -> {
                    if (OnProgressChanged != null) {
                        try {
                            OnProgressChanged.apply(new FileTaskProgress(ifile.getBaseName(), position / (double) length, finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            } else {
                ifile.copy(dir, (position, length) -> {
                    if (OnProgressChanged != null) {
                        try {
                            OnProgressChanged.apply(new FileTaskProgress(ifile.getBaseName(), position / (double) length, finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            count++;
        }
    }

    private int GetFilesRecurrsively(SalmonFile[] files) {
        int total = 0;
        for (SalmonFile file : files) {
            total++;
            if (file.isDirectory()) {
                total += GetFilesRecurrsively(file.listFiles());
            }
        }
        return total;
    }

}
