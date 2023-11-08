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

using Mku.File;
using Mku.SalmonFS;
using Mku.Sequence;

namespace Mku.Utils;

/// <summary>
///  Facade class for file operations.
/// </summary>
public class SalmonFileCommander
{
    private SalmonFileImporter fileImporter;
    private SalmonFileExporter fileExporter;
    private SalmonFileSearcher fileSearcher;
    private bool stopJobs;

    /// <summary>
    ///  Instantiate a new file commander object.
	/// </summary>
	///  <param name="importBufferSize">The buffer size to use for importing files.</param>
    ///  <param name="exportBufferSize">The buffer size to use for exporting files.</param>
	///  <param name="threads">Paraller threads to use.</param>
    public SalmonFileCommander(int importBufferSize, int exportBufferSize, int threads)
    {
        fileImporter = new SalmonFileImporter(importBufferSize, threads);
        fileExporter = new SalmonFileExporter(exportBufferSize, threads);
        fileSearcher = new SalmonFileSearcher();
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
    ///  <exception cref="Exception"></exception>
    public SalmonFile[] ImportFiles(IRealFile[] filesToImport, SalmonFile importDir,
        bool deleteSource, bool integrity,
        Action<RealFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<IRealFile, Exception> OnFailed)
    {
        stopJobs = false;
        List<SalmonFile> importedFiles = new List<SalmonFile>();

        int total = 0;
        for (int i = 0; i < filesToImport.Length; i++)
            total += GetCountRecursively(filesToImport[i]);
        int count = 0;
        Dictionary<string, SalmonFile> existingFiles = GetExistingFiles(importDir);
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

    private Dictionary<string, SalmonFile> GetExistingFiles(SalmonFile importDir)
    {
        Dictionary<string, SalmonFile> files = new Dictionary<string, SalmonFile>();
        foreach (SalmonFile file in importDir.ListFiles())
        {
            files[file.BaseName] = file;
        }
        return files;
    }

    private void ImportRecursively(IRealFile fileToImport, SalmonFile importDir,
        bool deleteSource, bool integrity,
        Action<RealFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename, Action<IRealFile, Exception> OnFailed,
        List<SalmonFile> importedFiles, ref int count, int total,
        Dictionary<string, SalmonFile> existingFiles)
    {
        SalmonFile sfile = existingFiles.ContainsKey(fileToImport.BaseName) ? existingFiles[fileToImport.BaseName] : null;
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
            Dictionary<string, SalmonFile> nExistingFiles = GetExistingFiles(sfile);
            foreach (IRealFile child in fileToImport.ListFiles())
            {
                ImportRecursively(child, sfile, deleteSource, integrity, OnProgressChanged,
                    AutoRename, OnFailed, importedFiles, ref count, total, nExistingFiles);
            }
            if (deleteSource)
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
                sfile = fileImporter.ImportFile(fileToImport, importDir, filename,
                deleteSource, integrity,
                (bytes, totalBytes) =>
                {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new RealFileTaskProgress(fileToImport,
                                bytes, totalBytes, finalCount, total));
                    }
                });
                importedFiles.Add(sfile);
                count++;
            }
            catch (SalmonSequenceException ex)
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
    ///  <exception cref="Exception"></exception>
    public IRealFile[] ExportFiles(SalmonFile[] filesToExport, IRealFile exportDir,
        bool deleteSource, bool integrity,
        Action<SalmonFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<SalmonFile, Exception> OnFailed)
    {
        stopJobs = false;
        List<IRealFile> exportedFiles = new List<IRealFile>();

        int total = 0;
        for (int i = 0; i < filesToExport.Length; i++)
            total += GetCountRecursively(filesToExport[i]);

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
            files[file.BaseName] = file;
        }
        return files;
    }

    private void ExportRecursively(SalmonFile fileToExport, IRealFile exportDir,
        bool deleteSource, bool integrity,
        Action<SalmonFileTaskProgress> OnProgressChanged,
        Func<IRealFile, string> AutoRename,
        Action<SalmonFile, Exception> OnFailed,
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
                OnProgressChanged(new SalmonFileTaskProgress(fileToExport, 1, 1, count, total));
            count++;
            Dictionary<string, IRealFile> nExistingFiles = GetExistingFiles(rfile);
            foreach (SalmonFile child in fileToExport.ListFiles())
                ExportRecursively(child, rfile, deleteSource, integrity, OnProgressChanged,
                    AutoRename, OnFailed, exportedFiles, ref count, total, nExistingFiles);
            if (deleteSource)
            {
                fileToExport.Delete();
                if (OnProgressChanged != null)
                {
                    OnProgressChanged(new SalmonFileTaskProgress(fileToExport, 1, 1, count, total));
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
                rfile = fileExporter.ExportFile(fileToExport, exportDir, filename, deleteSource, integrity,
                    (bytes, totalBytes) =>
                {
                    if (OnProgressChanged != null)
                    {
                        OnProgressChanged(new SalmonFileTaskProgress(fileToExport,
                                bytes, totalBytes, finalCount, total));
                    }
                });
                exportedFiles.Add(rfile);
                count++;
            }
            catch (SalmonSequenceException ex)
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

    private int GetCountRecursively(SalmonFile file)
    {
        int count = 1;
        if (file.IsDirectory)
        {
            foreach (SalmonFile child in file.ListFiles())
            {
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
    ///  <exception cref="Exception"></exception>
    public void DeleteFiles(SalmonFile[] filesToDelete, Action<SalmonFileTaskProgress> OnProgressChanged,
        Action<SalmonFile, Exception> OnFailed)
    {
        stopJobs = false;
        int count = 0;
        int total = 0;
        for (int i = 0; i < filesToDelete.Length; i++)
            total += GetCountRecursively(filesToDelete[i]);
        foreach (SalmonFile salmonFile in filesToDelete)
        {
            if (stopJobs)
                break;
            salmonFile.DeleteRecursively((file, position, length) =>
            {
                if (stopJobs)
                    throw new TaskCanceledException();
                if (OnProgressChanged != null)
                {
                    try
                    {
                        OnProgressChanged(new SalmonFileTaskProgress(
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
    ///  <exception cref="Exception"></exception>
    public void CopyFiles(SalmonFile[] filesToCopy, SalmonFile dir, bool move,
        Action<SalmonFileTaskProgress> OnProgressChanged,
        Func<SalmonFile, string> AutoRename,
        bool autoRenameFolders,
        Action<SalmonFile, Exception> OnFailed)
    {
        stopJobs = false;
        int count = 0;
        int total = 0;
        for (int i = 0; i < filesToCopy.Length; i++)
            total += GetCountRecursively(filesToCopy[i]);
        foreach (SalmonFile salmonFile in filesToCopy)
        {
            if (dir.RealFile.Path.StartsWith(salmonFile.RealFile.Path))
                continue;

            if (stopJobs)
                break;

            if (move)
            {
                salmonFile.MoveRecursively(dir, (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (OnProgressChanged != null)
                    {
                        try
                        {
                            OnProgressChanged(new SalmonFileTaskProgress(
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
                salmonFile.CopyRecursively(dir, (file, position, length) =>
                {
                    if (stopJobs)
                        throw new TaskCanceledException();
                    if (OnProgressChanged != null)
                    {
                        try
                        {
                            OnProgressChanged(new SalmonFileTaskProgress(file, position, length, count, total));
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
        fileImporter.Stop();
        fileExporter.Stop();
        fileSearcher.Stop();
    }

    /// <summary>
    ///  True if the file search is currently running.
	/// </summary>
	///  <returns></returns>
    public bool IsFileSearcherRunning()
    {
        return fileSearcher.IsRunning();
    }

    /// <summary>
    ///  True if jobs are currently running.
	/// </summary>
	///  <returns></returns>
    public bool IsRunning()
    {
        return fileSearcher.IsRunning() || fileImporter.IsRunning() || fileExporter.IsRunning();
    }

    /// <summary>
    ///  True if file search stopped.
	/// </summary>
	///  <returns></returns>
    public bool IsFileSearcherStopped()
    {
        return fileSearcher.IsStopped();
    }

    /// <summary>
    ///  Stop file search.
    /// </summary>
    public void StopFileSearch()
    {
        fileSearcher.Stop();
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
    public SalmonFile[] Search(SalmonFile dir, string terms, bool any,
                               SalmonFileSearcher.OnResultFoundListener OnResultFound,
                               Action<SalmonFileSearcher.SearchEvent> OnSearchEvent)
    {
        return fileSearcher.Search(dir, terms, any, OnResultFound, OnSearchEvent);
    }

    /// <summary>
    ///  True if all jobs are stopped.
	/// </summary>
	///  <returns></returns>
    public bool AreJobsStopped()
    {
        return stopJobs;
    }

    /// <summary>
    /// Close the file commander and associated resources
    /// </summary>
    public void Close()
    {
        fileImporter.Close();
        fileExporter.Close();
    }

    /// <summary>
    /// Rename an encrypted file
    /// </summary>
    /// <param name="ifile"></param>
    /// <param name="newFilename"></param>
    public void RenameFile(SalmonFile ifile, string newFilename)
    {
        ifile.Rename(newFilename);
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
    public class SalmonFileTaskProgress : FileTaskProgress
    {
        /// <summary>
        /// The file associated
        /// </summary>
        public SalmonFile File { get; }

        internal SalmonFileTaskProgress(SalmonFile file, long processedBytes, long totalBytes,
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
