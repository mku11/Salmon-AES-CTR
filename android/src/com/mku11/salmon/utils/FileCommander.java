package com.mku11.salmon.utils;
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
import androidx.arch.core.util.Function;

import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;
import com.mku11.salmonfs.SalmonFileImporter;
import com.mku11.salmonfs.SalmonFileSearcher;

import java.util.ArrayList;

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
                    OnProgressChanged.apply((int) (i * 100.0F / filesToImport.length));
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

        for (int i = 0; i < filesToExport.length; i++) {
            if (stopJobs)
                break;

            IRealFile realFile;
            try {
                SalmonFile fileToExport = filesToExport[i];
                realFile = fileExporter.exportFile(fileToExport, exportDir, true, null, i, filesToExport.length);
                exportedFiles.add(realFile);
                if (OnProgressChanged != null)
                    OnProgressChanged.apply((int) (i * 100.0F / filesToExport.length));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (OnFinished != null)
            OnFinished.apply(exportedFiles.toArray(new IRealFile[0]));
        return true;
    }


    public void doDeleteFiles(Function<SalmonFile, Void> OnFinished, SalmonFile[] files) {
        stopJobs = false;
        for (SalmonFile ifile : files) {
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

    public boolean isRunning() {
        return fileSearcher.isRunning() || fileImporter.isRunning() || fileExporter.isRunning();
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
        public final int fileProgress;
        public final int processedFiles;
        public final int totalFiles;
        public final String filename;

        public FileTaskProgress(String filename, int fileProgress, int processedFiles, int totalFiles) {
            this.fileProgress = fileProgress;
            this.processedFiles = processedFiles;
            this.totalFiles = totalFiles;
            this.filename = filename;
        }
    }

    public void doCopyFiles(SalmonFile[] files, SalmonFile dir, boolean move, Function<FileTaskProgress, Void> OnProgressChanged) throws Exception {
        stopJobs = false;
        int count = 0;
        int total = getFilesRecurrsively(files);
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
                            OnProgressChanged.apply(new FileTaskProgress(ifile.getBaseName(), (int) (position * 100F / length), finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            } else {
                ifile.copy(dir, (position, length) -> {
                    if (OnProgressChanged != null) {
                        try {
                            OnProgressChanged.apply(new FileTaskProgress(ifile.getBaseName(), (int) (position * 100F / length), finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            count++;
        }
    }

    private int getFilesRecurrsively(SalmonFile[] files) {
        int total = 0;
        for (SalmonFile file : files) {
            total++;
            if (file.isDirectory()) {
                total += getFilesRecurrsively(file.listFiles());
            }
        }
        return total;
    }
}
