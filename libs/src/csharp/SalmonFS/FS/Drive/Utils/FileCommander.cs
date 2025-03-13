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
using System.Threading.Tasks;

namespace Mku.FS.Drive.Utils;

/// <summary>
///  Facade class for file operations.
/// </summary>
public class FileCommander
{
    public FileImporter FileImporter { get; private set; }
    public FileExporter FileExporter { get; private set; }
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
    ///  <param name="deleteSource">True if you want to delete the source files.</param>
    ///  <param name="integrity">True to apply integrity to imported files</param>
    ///  <param name="OnProgressChanged">Observer to notify when progress changes.</param>
    ///  <param name="AutoRename">Function to rename file if another file with the same filename exists.</param>
    ///  <param name="OnFailed">Observer to notify when a file fails importing.</param>
    ///  <returns>The imported files.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public virtual IVirtualFile[] ImportFiles(IRealFile[] filesToImport, IVirtualFile importDir,
        bool deleteSource, bool integrity,
        Action<RealFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<IRealFile, Exception> OnFailed)
    {
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
                deleteSource, integrity,
                OnProgressChanged, AutoRename, OnFailed,
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
            files[file.BaseName] = file;
        }
        return files;
    }

    private void ImportRecursively(IRealFile fileToImport, IVirtualFile importDir,
        bool deleteSource, bool integrity,
        Action<RealFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename, Action<IRealFile, Exception> OnFailed,
        List<IVirtualFile> importedFiles, ref int count, int total,
        Dictionary<string, IVirtualFile> existingFiles)
    {
        IVirtualFile sfile = existingFiles.ContainsKey(fileToImport.BaseName) ? existingFiles[fileToImport.BaseName] : null;
        if (fileToImport.IsDirectory)
        {
            if (OnProgressChanged != null)
                OnProgressChanged(new RealFileTaskProgress(fileToImport, 0, 1, count, total));
            if (sfile == null || !sfile.Exists)
                sfile = importDir.CreateDirectory(fileToImport.BaseName);
            else if (sfile != null && sfile.Exists && sfile.IsFile && AutoRename != null)
                sfile = importDir.CreateDirectory(AutoRename(fileToImport));
            if (OnProgressChanged != null)
                OnProgressChanged(new RealFileTaskProgress(fileToImport, 1, 1, count, total));
            count++;
            Dictionary<string, IVirtualFile> nExistingFiles = GetExistingFiles(sfile);
            foreach (IRealFile child in fileToImport.ListFiles())
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
                string filename = fileToImport.BaseName;
                if (sfile != null && (sfile.Exists || sfile.IsDirectory) && AutoRename != null)
                    filename = AutoRename(fileToImport);
                int finalCount = count;
                sfile = FileImporter.ImportFile(fileToImport, importDir, filename,
                deleteSource, integrity,
                (bytes, totalBytes) =>
                {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new RealFileTaskProgress(fileToImport,
                                bytes, totalBytes, finalCount, total));
                    }
                });
                existingFiles[sfile.BaseName] = sfile;
                importedFiles.Add(sfile);
                count++;
            }
            catch (SequenceException ex)
            {
                throw ex;
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
    ///  <param name="deleteSource">True if you want to delete the source files.</param>
    ///  <param name="integrity">True to use integrity verification before exporting files</param>
    ///  <param name="OnProgressChanged">Observer to notify when progress changes.</param>
    ///  <param name="AutoRename">Function to rename file if another file with the same filename exists.</param>
    ///  <param name="OnFailed">Observer to notify when a file fails exporting.</param>
    ///  <returns>True if complete successfully.</returns>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public IRealFile[] ExportFiles(IVirtualFile[] filesToExport, IRealFile exportDir,
        bool deleteSource, bool integrity,
        Action<IVirtualFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<IVirtualFile, Exception> OnFailed)
    {
        stopJobs = false;
        List<IRealFile> exportedFiles = new List<IRealFile>();

        int total = 0;
        for (int i = 0; i < filesToExport.Length; i++)
        {
            if (stopJobs)
                break;
            total += GetCountRecursively(filesToExport[i]);
        }
        Dictionary<string, IRealFile> existingFiles = GetExistingFiles(exportDir);

        int count = 0;
        for (int i = 0; i < filesToExport.Length; i++)
        {
            if (stopJobs)
                break;

            ExportRecursively(filesToExport[i], exportDir,
                deleteSource, integrity,
                OnProgressChanged, AutoRename, OnFailed,
                exportedFiles, ref count, total,
                existingFiles);

        }
        return exportedFiles.ToArray();
    }

    private Dictionary<string, IRealFile> GetExistingFiles(IRealFile exportDir)
    {
        Dictionary<string, IRealFile> files = new Dictionary<string, IRealFile>();
        foreach (IRealFile file in exportDir.ListFiles())
        {
            if (stopJobs)
                break;
            files[file.BaseName] = file;
        }
        return files;
    }

    private void ExportRecursively(IVirtualFile fileToExport, IRealFile exportDir,
        bool deleteSource, bool integrity,
        Action<IVirtualFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<IVirtualFile, Exception> OnFailed,
        List<IRealFile> exportedFiles, ref int count, int total,
        Dictionary<string, IRealFile> existingFiles)
    {
        IRealFile rfile = existingFiles.ContainsKey(fileToExport.BaseName) ? existingFiles[fileToExport.BaseName] : null;

        if (fileToExport.IsDirectory)
        {
            if (rfile == null || !rfile.Exists)
                rfile = exportDir.CreateDirectory(fileToExport.BaseName);
            else if (rfile != null && rfile.IsFile && AutoRename != null)
                rfile = exportDir.CreateDirectory(AutoRename(rfile));
            if (OnProgressChanged != null)
                OnProgressChanged(new IVirtualFileTaskProgress(fileToExport, 1, 1, count, total));
            count++;
            Dictionary<string, IRealFile> nExistingFiles = GetExistingFiles(rfile);
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
                string filename = fileToExport.BaseName;
                if (rfile != null && rfile.Exists && AutoRename != null)
                    filename = AutoRename(rfile);
                int finalCount = count;
                rfile = FileExporter.ExportFile(fileToExport, exportDir, filename, deleteSource, integrity,
                    (bytes, totalBytes) =>
                {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new IVirtualFileTaskProgress(fileToExport,
                                bytes, totalBytes, finalCount, total));
                    }
                });
                existingFiles[rfile.BaseName] = rfile;
                exportedFiles.Add(rfile);
                count++;
            }
            catch (SequenceException ex)
            {
                throw ex;
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

    private int GetCountRecursively(IRealFile file)
    {
        int count = 1;
        if (file.IsDirectory)
        {
            foreach (IRealFile child in file.ListFiles())
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
    ///  <param name="OnProgressChanged">The progress change observer to notify.</param>
    ///  <param name="OnFailed">The observer to notify when failures occur.</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public void DeleteFiles(IVirtualFile[] filesToDelete, Action<IVirtualFileTaskProgress> OnProgressChanged,
        Action<IVirtualFile, Exception> OnFailed)
    {
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
            IVirtualFile.DeleteRecursively((file, position, length) =>
            {
                if (stopJobs)
                    throw new TaskCanceledException();
                if (OnProgressChanged != null)
                {
                    try
                    {
                        OnProgressChanged(new IVirtualFileTaskProgress(
                        file, position, length, count, total));
                    }
                    catch (Exception)
                    {
                    }
                }
                if (position == (long)length)
                    count++;
            }, OnFailed);
        }
    }


    /// <summary>
    ///  Copy files to another directory.
	/// </summary>
	///  <param name="filesToCopy">The array of files to copy.</param>
    ///  <param name="dir">The target directory.</param>
    ///  <param name="move">True if moving files instead of copying.</param>
    ///  <param name="OnProgressChanged">The progress change observer to notify.</param>
    ///  <param name="AutoRename">The auto rename function to use when files with same filename are found.</param>
    ///  <param name="autoRenameFolders">Apply autorename to folders also (default is true)</param>
    ///  <param name="OnFailed">The observer to notify when failures occur.</param>
    ///  <exception cref="Exception">Thrown if error during operation</exception>
    public void CopyFiles(IVirtualFile[] filesToCopy, IVirtualFile dir, bool move,
        Action<IVirtualFileTaskProgress> OnProgressChanged,
        Func<IVirtualFile, string> AutoRename,
        bool autoRenameFolders,
        Action<IVirtualFile, Exception> OnFailed)
    {
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

            if (move)
            {
                IVirtualFile.MoveRecursively(dir, (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (OnProgressChanged != null)
                    {
                        try
                        {
                            OnProgressChanged(new IVirtualFileTaskProgress(
                            file, position, length, count, total));
                        }
                        catch (Exception)
                        {
                        }
                    }
                    if (position == (long)length)
                        count++;
                }, AutoRename, autoRenameFolders, OnFailed);
            }
            else
            {
                IVirtualFile.CopyRecursively(dir, (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (OnProgressChanged != null)
                    {
                        try
                        {
                            OnProgressChanged(new IVirtualFileTaskProgress(file, position, length, count, total));
                        }
                        catch (Exception ignored)
                        {
                        }
                    }
                    if (position == (long)length)
                        count++;
                }, AutoRename, autoRenameFolders, OnFailed);
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
    ///  <param name="any">True if you want to match any term otherwise match all terms.</param>
    ///  <param name="OnResultFound">Callback interface to receive notifications when results found.</param>
    ///  <param name="OnSearchEvent">Callback interface to receive status events.</param>
    ///  <returns>An array with all the results found.</returns>
    public IVirtualFile[] Search(IVirtualFile dir, string terms, bool any,
                               FileSearcher.OnResultFoundListener OnResultFound,
                               Action<FileSearcher.SearchEvent> OnSearchEvent)
    {
        return FileSearcher.Search(dir, terms, any, OnResultFound, OnSearchEvent);
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
        public IRealFile File { get; }

        internal RealFileTaskProgress(IRealFile file, long processedBytes, long totalBytes,
                                     int processedFiles, int totalFiles) :
            base(processedBytes, totalBytes, processedFiles, totalFiles)
        {
            this.File = file;
        }
    }
}
