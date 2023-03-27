package com.mku11.salmonfs;
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

import java.util.ArrayList;

import com.mku11.salmon.func.BiConsumer;
import com.mku11.salmon.func.Consumer;

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

    public boolean importFiles(IRealFile[] filesToImport, SalmonFile importDir, boolean deleteSource,
                               BiConsumer<Integer, SalmonFile> OnProgressChanged, Consumer<SalmonFile[]> OnFinished) throws Exception {
        stopJobs = false;
        ArrayList<SalmonFile> importedFiles = new ArrayList<>();
        for (int i = 0; i < filesToImport.length; i++) {
            if (stopJobs)
                break;
            SalmonFile salmonFile = null;
            salmonFile = fileImporter.importFile(filesToImport[i], importDir, deleteSource, null, i, filesToImport.length);
            importedFiles.add(salmonFile);
            if (OnProgressChanged != null)
                OnProgressChanged.accept((int) (i * 100.0F / filesToImport.length), salmonFile);
        }
        if (OnFinished != null)
            OnFinished.accept(importedFiles.toArray(new SalmonFile[0]));
        return true;
    }

    public boolean exportFiles(SalmonFile[] filesToExport, BiConsumer<Integer, SalmonFile> OnProgressChanged,
                               Consumer<IRealFile[]> OnFinished) throws Exception {
        stopJobs = false;
        ArrayList<IRealFile> exportedFiles = new ArrayList<>();
        IRealFile exportDir = SalmonDriveManager.getDrive().getExportDir();

        for (int i = 0; i < filesToExport.length; i++) {
            if (stopJobs)
                break;

            IRealFile realFile;
            SalmonFile fileToExport = filesToExport[i];

            //TODO: export files under directories recursively
            if(fileToExport.isDirectory())
                continue;
            realFile = fileExporter.exportFile(fileToExport, exportDir, true, null, i, filesToExport.length);
            exportedFiles.add(realFile);
            if (OnProgressChanged != null)
                OnProgressChanged.accept((int) (i * 100.0F / filesToExport.length), fileToExport);
        }
        if (OnFinished != null)
            OnFinished.accept(exportedFiles.toArray(new IRealFile[0]));
        return true;
    }


    public void deleteFiles(SalmonFile[] files, Consumer<SalmonFile> OnFileDeleted) {
        stopJobs = false;
        for (SalmonFile ifile : files) {
            if (stopJobs)
                break;
            ifile.delete();
            if (OnFileDeleted != null)
                OnFileDeleted.accept(ifile);
        }
    }


    public void copyFiles(SalmonFile[] files, SalmonFile dir, boolean move, Consumer<FileTaskProgress> OnProgressChanged) throws Exception {
        stopJobs = false;
        int count = 0;
        int total = getFiles(files);
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
                            OnProgressChanged.accept(new FileTaskProgress(ifile.getBaseName(), (int) (position * 100F / length), finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            } else {
                ifile.copy(dir, (position, length) -> {
                    if (OnProgressChanged != null) {
                        try {
                            OnProgressChanged.accept(new FileTaskProgress(ifile.getBaseName(), (int) (position * 100F / length), finalCount, total));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            count++;
        }
    }

    public void cancel() {
        stopJobs = true;
        fileImporter.stop();
        fileExporter.stop();
        fileSearcher.stop();
    }

    public void setImporterProgressListener(SalmonFileImporter.OnProgressChanged listener) {
        fileImporter.onTaskProgressChanged = listener;
    }

    public void setExporterProgressListener(SalmonFileExporter.OnProgressChanged listener) {
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
}
