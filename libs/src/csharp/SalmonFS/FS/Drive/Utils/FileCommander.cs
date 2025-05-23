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

using Mku.FS.File;
using Mku.Salmon.Sequence;
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using static Mku.FS.Drive.Utils.FileCommander;

namespace Mku.FS.Drive.Utils;

/// <summary>
///  Facade class for file operations.
/// </summary>
public class FileCommander
{
    /// <summary>
    /// Get the file importer.
    /// </summary>
    public FileImporter FileImporter { get; private set; }
    /// <summary>
    /// Get the file exporter.
    /// </summary>
    public FileExporter FileExporter { get; private set; }

    /// <summary>
    /// Get the file searcher.
    /// </summary>
    public FileSearcher FileSearcher { get; private set; }

    private bool stopJobs;

    /// <summary>
    ///  Instantiate a new file commander object.
    /// </summary>
    public FileCommander(FileImporter fileImporter, FileExporter fileExporter, FileSearcher fileSearcher)
    {
        this.FileImporter = fileImporter;
        this.FileExporter = fileExporter;
        this.FileSearcher = fileSearcher;
    }

    /// <summary>
    ///  Import files to the drive.
	/// </summary>
	///  <param name="filesToImport">The files to import.</param>
    ///  <param name="importDir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>The imported files.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public virtual IVirtualFile[] ImportFiles(IFile[] filesToImport, IVirtualFile importDir,
        BatchImportOptions options = null)
    {
        if (options == null)
            options = new BatchImportOptions();
        stopJobs = false;
        List<IVirtualFile> importedFiles = new List<IVirtualFile>();

        int total = 0;
        for (int i = 0; i < filesToImport.Length; i++)
        {
            if (stopJobs)
                break;
            total += GetCountRecursively(filesToImport[i]);
        }
        int count = 0;
        Dictionary<string, IVirtualFile> existingFiles = GetExistingFiles(importDir);
        for (int i = 0; i < filesToImport.Length; i++)
        {
            if (stopJobs)
                break;
            ImportRecursively(filesToImport[i], importDir,
                options.deleteSource, options.integrity,
                options.onProgressChanged, options.autoRename, options.onFailed,
                importedFiles, ref count, total,
                existingFiles);
        }
        return importedFiles.ToArray();
    }

    private Dictionary<string, IVirtualFile> GetExistingFiles(IVirtualFile importDir)
    {
        Dictionary<string, IVirtualFile> files = new Dictionary<string, IVirtualFile>();
        foreach (IVirtualFile file in importDir.ListFiles())
        {
            if (stopJobs)
                break;
            files[file.Name] = file;
        }
        return files;
    }

    private void ImportRecursively(IFile fileToImport, IVirtualFile importDir,
        bool deleteSource, bool integrity,
        Action<RealFileTaskProgress> OnProgressChanged,
        Func<IFile, string> AutoRename, Action<IFile, Exception> OnFailed,
        List<IVirtualFile> importedFiles, ref int count, int total,
        Dictionary<string, IVirtualFile> existingFiles)
    {
        IVirtualFile sfile = existingFiles.ContainsKey(fileToImport.Name) ? existingFiles[fileToImport.Name] : null;
        if (fileToImport.IsDirectory)
        {
            if (OnProgressChanged != null)
                OnProgressChanged(new RealFileTaskProgress(fileToImport, 0, 1, count, total));
            if (sfile == null || !sfile.Exists)
                sfile = importDir.CreateDirectory(fileToImport.Name);
            else if (sfile != null && sfile.Exists && sfile.IsFile && AutoRename != null)
                sfile = importDir.CreateDirectory(AutoRename(fileToImport));
            if (OnProgressChanged != null)
                OnProgressChanged(new RealFileTaskProgress(fileToImport, 1, 1, count, total));
            count++;
            Dictionary<string, IVirtualFile> nExistingFiles = GetExistingFiles(sfile);
            foreach (IFile child in fileToImport.ListFiles())
            {
                if (stopJobs)
                    break;
                ImportRecursively(child, sfile, deleteSource, integrity, OnProgressChanged,
                    AutoRename, OnFailed, importedFiles, ref count, total, nExistingFiles);
            }
            if (deleteSource && !stopJobs)
                fileToImport.Delete();
        }
        else
        {
            try
            {
                string filename = fileToImport.Name;
                if (sfile != null && (sfile.Exists || sfile.IsDirectory) && AutoRename != null)
                    filename = AutoRename(fileToImport);
                int finalCount = count;

                FileImporter.FileImportOptions importOptions = new FileImporter.FileImportOptions();
                importOptions.filename = filename;
                importOptions.deleteSource = deleteSource;
                importOptions.integrity = integrity;
                importOptions.onProgressChanged = (bytes, totalBytes) =>
                {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new RealFileTaskProgress(fileToImport,
                                bytes, totalBytes, finalCount, total));
                    }
                };
                sfile = FileImporter.ImportFile(fileToImport, importDir, importOptions);
                existingFiles[sfile.Name] = sfile;
                importedFiles.Add(sfile);
                count++;
            }
            catch (SequenceException ex)
            {
                throw;
            }
            catch (Exception ex)
            {
                if (OnFailed != null)
                    OnFailed(fileToImport, ex);
            }
        }
    }

    /// <summary>
    ///  Export a file from a drive.
    /// </summary>
    ///  <param name="filesToExport">The files to export.</param>
    ///  <param name="exportDir">The export target directory.</param>
    ///  <param name="options">The options</param>
    ///  <returns>True if complete successfully.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public IFile[] ExportFiles(IVirtualFile[] filesToExport, IFile exportDir,
        BatchExportOptions options = null)
    {
        if (options == null)
            options = new BatchExportOptions();
        stopJobs = false;
        List<IFile> exportedFiles = new List<IFile>();

        int total = 0;
        for (int i = 0; i < filesToExport.Length; i++)
        {
            if (stopJobs)
                break;
            total += GetCountRecursively(filesToExport[i]);
        }
        Dictionary<string, IFile> existingFiles = GetExistingFiles(exportDir);

        int count = 0;
        for (int i = 0; i < filesToExport.Length; i++)
        {
            if (stopJobs)
                break;

            ExportRecursively(filesToExport[i], exportDir,
                options.deleteSource, options.integrity,
                options.onProgressChanged, options.autoRename, options.onFailed,
                exportedFiles, ref count, total,
                existingFiles);
        }
        return exportedFiles.ToArray();
    }

    private Dictionary<string, IFile> GetExistingFiles(IFile exportDir)
    {
        Dictionary<string, IFile> files = new Dictionary<string, IFile>();
        foreach (IFile file in exportDir.ListFiles())
        {
            if (stopJobs)
                break;
            files[file.Name] = file;
        }
        return files;
    }

    private void ExportRecursively(IVirtualFile fileToExport, IFile exportDir,
        bool deleteSource, bool integrity,
        Action<IVirtualFileTaskProgress> OnProgressChanged,
        Func<IFile, string> AutoRename,
        Action<IVirtualFile, Exception> OnFailed,
        List<IFile> exportedFiles, ref int count, int total,
        Dictionary<string, IFile> existingFiles)
    {
        IFile rfile = existingFiles.ContainsKey(fileToExport.Name) ? existingFiles[fileToExport.Name] : null;

        if (fileToExport.IsDirectory)
        {
            if (rfile == null || !rfile.Exists)
                rfile = exportDir.CreateDirectory(fileToExport.Name);
            else if (rfile != null && rfile.IsFile && AutoRename != null)
                rfile = exportDir.CreateDirectory(AutoRename(rfile));
            if (OnProgressChanged != null)
                OnProgressChanged(new IVirtualFileTaskProgress(fileToExport, 1, 1, count, total));
            count++;
            Dictionary<string, IFile> nExistingFiles = GetExistingFiles(rfile);
            foreach (IVirtualFile child in fileToExport.ListFiles())
            {
                if (stopJobs)
                    break;
                ExportRecursively(child, rfile, deleteSource, integrity, OnProgressChanged,
                    AutoRename, OnFailed, exportedFiles, ref count, total, nExistingFiles);
            }
            if (deleteSource && !stopJobs)
            {
                fileToExport.Delete();
                if (OnProgressChanged != null)
                {
                    OnProgressChanged(new IVirtualFileTaskProgress(fileToExport, 1, 1, count, total));
                }
            }
            count++;
        }
        else
        {
            try
            {
                string filename = fileToExport.Name;
                if (rfile != null && rfile.Exists && AutoRename != null)
                    filename = AutoRename(rfile);
                int finalCount = count;

                FileExporter.FileExportOptions exportOptions = new FileExporter.FileExportOptions();
                exportOptions.filename = filename;
                exportOptions.deleteSource = deleteSource;
                exportOptions.integrity = integrity;
                exportOptions.onProgressChanged = (bytes, totalBytes)=> {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new IVirtualFileTaskProgress(fileToExport,
                                bytes, totalBytes, finalCount, total));
                    }
                };
                rfile = FileExporter.ExportFile(fileToExport, exportDir, exportOptions);
                existingFiles[rfile.Name] = rfile;
                exportedFiles.Add(rfile);
                count++;
            }
            catch (SequenceException ex)
            {
                throw;
            }
            catch (Exception ex)
            {
                if (OnFailed != null)
                    OnFailed(fileToExport, ex);
            }
        }

    }

    private int GetCountRecursively(IVirtualFile file)
    {
        int count = 1;
        if (file.IsDirectory)
        {
            foreach (IVirtualFile child in file.ListFiles())
            {
                if (stopJobs)
                    break;
                count += GetCountRecursively(child);
            }
        }
        return count;
    }

    private int GetCountRecursively(IFile file)
    {
        int count = 1;
        if (file.IsDirectory)
        {
            foreach (IFile child in file.ListFiles())
            {
                if (stopJobs)
                    break;
                count += GetCountRecursively(child);
            }
        }
        return count;
    }

    /// <summary>
    ///  Delete files.
	/// </summary>
	///  <param name="filesToDelete">The array of files to delete.</param>
    ///  <param name="options">The options.</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public void DeleteFiles(IVirtualFile[] filesToDelete, BatchDeleteOptions options = null)
    {
        if (options == null)
            options = new BatchDeleteOptions();
        stopJobs = false;
        int count = 0;
        int total = 0;
        for (int i = 0; i < filesToDelete.Length; i++)
        {
            if (stopJobs)
                break;
            total += GetCountRecursively(filesToDelete[i]);
        }
        foreach (IVirtualFile IVirtualFile in filesToDelete)
        {
            if (stopJobs)
                break;

            IVirtualFile.VirtualRecursiveDeleteOptions deleteOptions = new IVirtualFile.VirtualRecursiveDeleteOptions();
            deleteOptions.onFailed = options.onFailed;
            deleteOptions.onProgressChanged = (file, position, length) =>
            {
                if (stopJobs)
                    throw new TaskCanceledException();
                if (options.onProgressChanged != null)
                {
                    try
                    {
                        options.onProgressChanged(new IVirtualFileTaskProgress(
                        file, position, length, count, total));
                    }
                    catch (Exception)
                    {
                    }
                }
                if (position == (long)length)
                    count++;
            };
            IVirtualFile.DeleteRecursively(deleteOptions);
        }
    }

    /// <summary>
    ///  Copy files to another directory.
	/// </summary>
	///  <param name="filesToCopy">The array of files to copy.</param>
    ///  <param name="dir">The target directory.</param>
    ///  <param name="options">The options</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public void CopyFiles(IVirtualFile[] filesToCopy, IVirtualFile dir, BatchCopyOptions options = null)
    {
        if (options == null)
            options = new BatchCopyOptions();
        stopJobs = false;
        int count = 0;
        int total = 0;
        for (int i = 0; i < filesToCopy.Length; i++)
        {
            if (stopJobs)
                break;
            total += GetCountRecursively(filesToCopy[i]);
        }

        foreach (IVirtualFile IVirtualFile in filesToCopy)
        {
            if (stopJobs)
                break;
            if (dir.RealFile.Path.StartsWith(IVirtualFile.RealFile.Path))
                continue;

            if (options.move)
            {
                IVirtualFile.VirtualRecursiveMoveOptions moveOptions = new IVirtualFile.VirtualRecursiveMoveOptions();
                moveOptions.autoRename = options.autoRename;
                moveOptions.autoRenameFolders = options.autoRenameFolders;
                moveOptions.onFailed = options.onFailed;
                moveOptions.onProgressChanged = (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (options.onProgressChanged != null)
                    {
                        try
                        {
                            options.onProgressChanged(new IVirtualFileTaskProgress(
                            file, position, length, count, total));
                        }
                        catch (Exception)
                        {
                        }
                    }
                    if (position == (long)length)
                        count++;
                };
                IVirtualFile.MoveRecursively(dir, moveOptions);
            }
            else
            {
                IVirtualFile.VirtualRecursiveCopyOptions copyOptions = new IVirtualFile.VirtualRecursiveCopyOptions();
                copyOptions.autoRename = options.autoRename;
                copyOptions.autoRenameFolders = options.autoRenameFolders;
                copyOptions.onFailed = options.onFailed;
                copyOptions.onProgressChanged = (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (options.onProgressChanged != null)
                    {
                        try
                        {
                            options.onProgressChanged(new IVirtualFileTaskProgress(file, position, length, count, total));
                        }
                        catch (Exception ignored)
                        {
                        }
                    }
                    if (position == length)
                        count++;
                };
                IVirtualFile.CopyRecursively(dir, copyOptions);
            }
        }
    }

    /// <summary>
    ///  Cancel all jobs.
    /// </summary>
    public void Cancel()
    {
        stopJobs = true;
        FileImporter.Stop();
        FileExporter.Stop();
        FileSearcher.Stop();
    }

    /// <summary>
    ///  True if the file search is currently running.
	/// </summary>
	///  <returns>True if file search is running</returns>
    public bool IsFileSearcherRunning()
    {
        return FileSearcher.IsRunning();
    }

    /// <summary>
    ///  True if jobs are currently running.
	/// </summary>
	///  <returns>True if running</returns>
    public bool IsRunning()
    {
        return FileSearcher.IsRunning() || FileImporter.IsRunning() || FileExporter.IsRunning();
    }

    /// <summary>
    ///  True if file search stopped.
	/// </summary>
	///  <returns>True if file searcher is running</returns>
    public bool IsFileSearcherStopped()
    {
        return FileSearcher.IsStopped();
    }

    /// <summary>
    ///  Stop file search.
    /// </summary>
    public void StopFileSearch()
    {
        FileSearcher.Stop();
    }

    /// <summary>
    ///  Search
	/// </summary>
	///  <param name="dir">The directory to start the search.</param>
    ///  <param name="terms">The terms to search for.</param>
    ///  <param name="options">The options.</param>
    ///  <returns>An array with all the results found.</returns>
    public IVirtualFile[] Search(IVirtualFile dir, string terms, FileSearcher.SearchOptions options = null)
    {
        return FileSearcher.Search(dir, terms, options);
    }

    /// <summary>
    ///  True if all jobs are stopped.
	/// </summary>
	///  <returns>True if jobs are stopped</returns>
    public bool AreJobsStopped()
    {
        return stopJobs;
    }

    /// <summary>
    /// Close the file commander and associated resources
    /// </summary>
    public void Close()
    {
        FileImporter.Close();
        FileExporter.Close();
    }

    /// <summary>
    /// Rename an encrypted file
    /// </summary>
    /// <param name="file">The file</param>
    /// <param name="newFilename">The new file name</param>
    public void RenameFile(IVirtualFile file, string newFilename)
    {
        file.Rename(newFilename);
    }

    /// <summary>
    ///  File task progress
    /// </summary>
    public class FileTaskProgress
    {
        /// <summary>
        /// Processed files
        /// </summary>
        public readonly long ProcessedBytes;

        /// <summary>
        /// Processed files
        /// </summary>
        public readonly long TotalBytes;

        /// <summary>
        /// Processed files
        /// </summary>
        public readonly int ProcessedFiles;

        /// <summary>
        /// Total Files to process
        /// </summary>
        public int TotalFiles;

        internal FileTaskProgress(long processedBytes, long totalBytes, int processedFiles, int totalFiles)
        {
            this.ProcessedBytes = processedBytes;
            this.TotalBytes = totalBytes;
            this.ProcessedFiles = processedFiles;
            this.TotalFiles = totalFiles;
        }
    }

    /// <summary>
    /// Task progress for encrypted file operations
    /// </summary>
    public class IVirtualFileTaskProgress : FileTaskProgress
    {
        /// <summary>
        /// The file associated
        /// </summary>
        public IVirtualFile File { get; }

        internal IVirtualFileTaskProgress(IVirtualFile file, long processedBytes, long totalBytes,
                                       int processedFiles, int totalFiles) :
            base(processedBytes, totalBytes, processedFiles, totalFiles)
        {
            this.File = file;
        }
    }

    /// <summary>
    /// Task progress for real file operations
    /// </summary>
    public class RealFileTaskProgress : FileTaskProgress
    {
        /// <summary>
        /// The file associated
        /// </summary>
        public IFile File { get; }

        internal RealFileTaskProgress(IFile file, long processedBytes, long totalBytes,
                                     int processedFiles, int totalFiles) :
            base(processedBytes, totalBytes, processedFiles, totalFiles)
        {
            this.File = file;
        }
    }

    /// <summary>
    /// Batch delet options
    /// </summary>
    public class BatchDeleteOptions
    {
        /// <summary>
        /// Callback when delete fails
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changes
        /// </summary>
        public Action<IVirtualFileTaskProgress> onProgressChanged;
    }


    /// <summary>
    /// Batch import options
    /// </summary>
    public class BatchImportOptions
    {
        /// <summary>
        /// Delete the source file when complete.
        /// </summary>
        public bool deleteSource = false;

        /// <summary>
        /// True to enable integrity
        /// </summary>
        public bool integrity = false;

        /// <summary>
        /// Callback when a file with the same name exists
        /// </summary>
        public Func<IFile, string> autoRename;

        /// <summary>
        /// Callback when import fails
        /// </summary>
        public Action<IFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changes
        /// </summary>
        public Action<RealFileTaskProgress> onProgressChanged;
    }

    /// <summary>
    /// Batch export options
    /// </summary>
    public class BatchExportOptions
    {
        /// <summary>
        /// Delete the source file when complete.
        /// </summary>
        public bool deleteSource = false;

        /// <summary>
        /// True to enable integrity
        /// </summary>
        public bool integrity = false;

        /// <summary>
        /// Callback when a file with the same name exists
        /// </summary>
        public Func<IFile, string> autoRename;

        /// <summary>
        /// Callback when import fails
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changes
        /// </summary>
        public Action<IVirtualFileTaskProgress> onProgressChanged;
    }

    /// <summary>
    /// Batch copy options
    /// </summary>
    public class BatchCopyOptions
    {
        /// <summary>
        /// True to move, false to copy
        /// </summary>
        public bool move = false;

        /// <summary>
        /// Callback when another file with the same name exists.
        /// </summary>
        public Func<IVirtualFile, String> autoRename;

        /// <summary>
        /// True to autorename folders
        /// </summary>
        public bool autoRenameFolders = false;

        /// <summary>
        /// Callback when copy fails
        /// </summary>
        public Action<IVirtualFile, Exception> onFailed;

        /// <summary>
        /// Callback when progress changes.
        /// </summary>
        public Action<IVirtualFileTaskProgress> onProgressChanged;
    }
}
