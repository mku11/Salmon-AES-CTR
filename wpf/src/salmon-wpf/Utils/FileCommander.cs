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
using Salmon.FS;
using Salmon.Model;
using SalmonFS;
using System;
using System.Collections.Generic;

namespace Salmon
{
    public class FileCommander
    {
        private int exportBufferSize;
        private int importBufferSize;
        private SalmonFileImporter fileImporter;
        private SalmonFileExporter fileExporter;
        private SalmonFileSearcher fileSearcher;
        private bool stopJobs;

        public FileCommander(int importBufferSize, int exportBufferSize)
        {
            this.importBufferSize = importBufferSize;
            this.exportBufferSize = exportBufferSize;
            SetupFileTools();
        }

        private void SetupFileTools()
        {
            fileImporter = new SalmonFileImporter(importBufferSize, exportBufferSize, null);
            fileExporter = new SalmonFileExporter(importBufferSize, exportBufferSize);
            fileSearcher = new SalmonFileSearcher();
        }

        public void CancelJobs()
        {
            stopJobs = true;
            fileImporter.Stop();
            fileExporter.Stop();
            fileSearcher.Stop();
        }

        public bool DoImportFiles(IRealFile[] filesToImport, SalmonFile importDir, bool deleteSource,
                                     Action<int> OnProgressChanged, Action<SalmonFile[]> OnFinished)
        {
            stopJobs = false;
            List<SalmonFile> importedFiles = new List<SalmonFile>();
            for (int i = 0; i < filesToImport.Length; i++)
            {
                if (stopJobs)
                    break;
                SalmonFile salmonFile = null;
                try
                {
                    salmonFile = fileImporter.ImportFile(filesToImport[i], importDir, deleteSource, null);
                    int finalI = i;
                    if (OnProgressChanged != null)
                        OnProgressChanged.Invoke((int)((finalI+1) * 100F / filesToImport.Length));
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                importedFiles.Add(salmonFile);
            }
            if (OnFinished != null)
                OnFinished.Invoke(importedFiles.ToArray());
            return true;
        }

        public bool DoExportFiles(SalmonFile[] filesToExport, Action<int> OnProgressChanged,
                                     Action<IRealFile[]> OnFinished)
        {
            stopJobs = false;
            List<IRealFile> exportedFiles = new List<IRealFile>();
            IRealFile exportDir = SalmonDriveManager.GetDrive().GetExportDir();
            exportDir = exportDir.CreateDirectory(SalmonTime.CurrentTimeMillis() + "");

            for (int i = 0; i < filesToExport.Length; i++)
            {
                if (stopJobs)
                    break;

                IRealFile realFile = null;
                try
                {
                    SalmonFile fileToExport = filesToExport[i];
                    if (fileToExport.IsDirectory())
                    {
                        if (!exportDir.Exists())
                            exportDir.Mkdir();
                        string targetDirName = fileToExport.GetBaseName();
                        realFile = exportDir.CreateDirectory(targetDirName);
                    }
                    else
                    {
                        realFile = fileExporter.ExportFile(fileToExport, exportDir, true, null);
                    }
                    exportedFiles.Add(realFile);
                    int finalI = i;
                    if (OnProgressChanged != null)
                        OnProgressChanged.Invoke((int)((finalI+1) * 100F / filesToExport.Length));
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine(ex);
                }
            }
            if (OnFinished != null)
                OnFinished.Invoke(exportedFiles.ToArray());
            return true;
        }


        public void DoDeleteFiles(Action<SalmonFileItem> OnFinished, SalmonFileItem[] files)
        {
            stopJobs = false;
            foreach (SalmonFileItem ifile in files)
            {
                if (stopJobs)
                    break;
                ifile.Delete();
                if (OnFinished != null)
                    OnFinished.Invoke(ifile);
            }
        }

        public void SetFileImporterOnTaskProgressChanged(SalmonFileImporter.OnProgressChanged listener)
        {
            fileImporter.OnTaskProgressChanged = listener;
        }

        public void SetFileExporterOnTaskProgressChanged(SalmonFileExporter.OnProgressChanged listener)
        {
            fileExporter.OnTaskProgressChanged = listener;
        }

        public bool isFileSearcherRunning()
        {
            return fileSearcher.IsRunning();
        }

        public bool isRunning()
        {
            return fileSearcher.IsRunning() || fileImporter.IsRunning() || fileExporter.IsRunning();
        }


        public bool isFileSearcherStopped()
        {
            return fileSearcher.IsStopped();
        }

        public void StopFileSearch()
        {
            fileSearcher.Stop();
        }

        public SalmonFile[] Search(SalmonFile dir, string value, bool any, SalmonFileSearcher.OnResultFound OnResultFound)
        {
            return fileSearcher.Search(dir, value, any, OnResultFound);
        }

        public bool isStopped()
        {
            return stopJobs;
        }

        public class FileTaskProgress
        {
            public double fileProgress;
            public int processedFiles;
            public int totalFiles;
            public string filename;
            public FileTaskProgress(string filename, double fileProgress, int processedFiles, int totalFiles)
            {
                this.fileProgress = fileProgress;
                this.processedFiles = processedFiles;
                this.totalFiles = totalFiles;
                this.filename = filename;
            }
        }

        public void DoCopyFiles(SalmonFile[] files, SalmonFile dir, bool move, Action<FileTaskProgress> OnProgressChanged)
        {
            stopJobs = false;
            int count = 0;
            int total = GetFilesRecurrsively(files);
            foreach (SalmonFile ifile in files)
            {
                if (dir.GetPath().StartsWith(ifile.GetPath()))
                    continue;

                if (stopJobs)
                    break;
                int finalCount = count;
                if (move)
                {
                    ifile.Move(dir, (position, length) => {
                        if (OnProgressChanged != null)
                        {
                            try
                            {
                                OnProgressChanged.Invoke(new FileTaskProgress(ifile.GetBaseName(), position / (double)length, finalCount, total));
                            }
                            catch (Exception) { }
                        }
                    });
                }
                else
                {
                    ifile.Copy(dir, (position, length) => {
                        if (OnProgressChanged != null)
                        {
                            try
                            {
                                OnProgressChanged.Invoke(new FileTaskProgress(ifile.GetBaseName(), position / (double)length, finalCount, total));
                            }
                            catch (Exception) { }
                        }
                    });
                }
                count++;
            }
        }

        private int GetFilesRecurrsively(SalmonFile[] files)
        {
            int total = 0;
            foreach (SalmonFile file in files)
            {
                total++;
                if (file.IsDirectory())
                {
                    total += GetFilesRecurrsively(file.ListFiles());
                }
            }
            return total;
        }

    }
}