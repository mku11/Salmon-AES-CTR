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
using System;
using System.IO;
using System.Threading.Tasks;

namespace Mku.FS.Drive.Utils;

/// <summary>
/// Exports files from the drive to the real filesystem
/// </summary>
public abstract class FileExporter
{
    /// <summary>
    ///  The global default buffer size to use when reading/writing on streams.
    /// </summary>
    private static readonly int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /// <summary>
    ///  The global default threads to use.
    /// </summary>
    private static readonly int DEFAULT_THREADS = 1;

    /// <summary>
    ///  Current buffer size.
    /// </summary>
    private int bufferSize;

    /// <summary>
    ///  Current threads.
    /// </summary>
    private int threads;

    /// <summary>
    ///  True if last job was stopped by the user.
    /// </summary>
    private bool stopped = true;

    /// <summary>
    ///  Failed if last job was failed.
    /// </summary>
    private bool failed = false;

    /// <summary>
    ///  Last exception occurred.
    /// </summary>
    private Exception lastException;

    /// <summary>
    /// Prepare the files before export
    /// </summary>
    /// <param name="sourceFile">The file to export</param>
    /// <param name="integrity">True to verify integrity</param>
    protected abstract void OnPrepare(IVirtualFile sourceFile, bool integrity);

    /// <summary>
    /// Get the minimum required part size for splitting a virtual file for export
    /// </summary>
    /// <param name="file">The virtual file</param>
    /// <returns>The part size</returns>
    protected abstract long GetMinimumPartSize(IVirtualFile file);

    /// <summary>
    /// Instantiate a file exporter.
    /// </summary>
    /// <param name="bufferSize">The buffer size</param>
    /// <param name="threads">The threads</param>
    public void Initialize(int bufferSize = 0, int threads = 1)
    {
        if (bufferSize <= 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (threads <= 0)
            threads = DEFAULT_THREADS;
        this.bufferSize = bufferSize;
        this.threads = threads;
    }

    /// <summary>
    /// True if an operation is currently running.
    /// </summary>
    /// <returns>True if running</returns>
    public bool IsRunning()
    {
        return !stopped;
    }

    /// <summary>
    /// </summary>
    public void Stop()
    {
        stopped = true;
    }

    /// <summary>
    ///  Export a file from the drive to the external directory path
	/// </summary>
	///  <param name="fileToExport">The file that will be exported</param>
    ///  <param name="exportDir">   The external directory the file will be exported to</param>
    ///  <param name="options">     The options</param>
    public IFile ExportFile(IVirtualFile fileToExport, IFile exportDir, FileExportOptions options)
    {
        if (options == null)
            options = new FileExportOptions();
        if (IsRunning())
            throw new Exception("Another export is running");
        if (fileToExport.IsDirectory)
            throw new Exception("Cannot export directory, use FileCommander instead");

        IFile exportFile;
        string filename = options.filename ?? fileToExport.Name;
        try
        {
            stopped = false;
            long[] totalBytesWritten = new long[] { 0 };
            failed = false;
            lastException = null;

            if (!exportDir.Exists)
                exportDir.Mkdir();
            exportFile = exportDir.CreateFile(filename);
            OnPrepare(fileToExport, options.integrity);

            long fileSize = fileToExport.Length;
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = GetMinimumPartSize(fileToExport);
            if (partSize > minPartSize && threads > 1)
            {
                partSize = (int)Math.Ceiling(fileSize / (float)threads);
                if (partSize > minPartSize)
                    partSize -= partSize % minPartSize;
                else
                    partSize = minPartSize;
                runningThreads = (int)(fileSize / partSize);
            }

            if (runningThreads == 1)
            {
                ExportFilePart(fileToExport, exportFile, 0, fileSize, totalBytesWritten, options.onProgressChanged);
            }
            else
            {
                this.SubmitExportJobs(runningThreads, partSize, fileToExport, exportFile, totalBytesWritten, options.integrity, options.onProgressChanged);
            }

            if (stopped)
                exportFile.Delete();
            else if (options.deleteSource)
                fileToExport.RealFile.Delete();
            if (lastException != null)
                throw lastException;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
            stopped = true;
            throw;
        }

        if (stopped || failed)
        {
            stopped = true;
            return null;
        }
        stopped = true;
        return exportFile;
    }

    private void SubmitExportJobs(int runningThreads, long partSize, IVirtualFile fileToExport, IFile exportFile, long[] totalBytesWritten, bool integrity, Action<long, long> OnProgress)
    {
        long fileSize = fileToExport.Length;

        Task[] tasks = new Task[runningThreads];
        long finalPartSize = partSize;
        int finalRunningThreads = runningThreads;
        for (int i = 0; i < runningThreads; i++)
        {
            int index = i;
            tasks[i] = Task.Run(() =>
            {
                long start = finalPartSize * index;
                long length;
                if (index == finalRunningThreads - 1)
                    length = fileSize - start;
                else
                    length = finalPartSize;
                try
                {
                    ExportFilePart(fileToExport, exportFile, start, length, totalBytesWritten, OnProgress);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            });
        }
        Task.WaitAll(tasks);
    }

    /// <summary>
    ///  Export a file part from the drive.
    /// </summary>
    ///  <param name="fileToExport">     The file the part belongs to</param>
    ///  <param name="exportFile">       The file to copy the exported part to</param>
    ///  <param name="start">            The start position on the file</param>
    ///  <param name="count">            The length of the bytes to be decrypted</param>
    ///  <param name="totalBytesWritten">The total bytes that were written to the external file</param>
    ///  <param name="OnProgress">The progress listener</param>
    private void ExportFilePart(IVirtualFile fileToExport, IFile exportFile, long start, long count,
        long[] totalBytesWritten, Action<long, long> OnProgress)
    {
        long totalPartBytesWritten = 0;

        Stream targetStream = null;
        Stream sourceStream = null;

        try
        {
            targetStream = exportFile.GetOutputStream();
            targetStream.Position = start;

            sourceStream = fileToExport.GetInputStream();
            sourceStream.Position = start;

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = sourceStream.Read(bytes, 0, (int)Math.Min((long)bytes.Length,
                    count - totalPartBytesWritten))) > 0 && totalPartBytesWritten < count)
            {
                if (stopped)
                    break;

                targetStream.Write(bytes, 0, bytesRead);
                totalPartBytesWritten += bytesRead;

                totalBytesWritten[0] += bytesRead;
                if (OnProgress != null)
                    OnProgress(totalBytesWritten[0], fileToExport.Length);
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
            lastException = ex;
            Stop();
        }
        finally
        {
            if (targetStream != null)
            {
                targetStream.Flush();
                targetStream.Close();
            }
            if (sourceStream != null)
            {
                sourceStream.Close();
            }
        }
    }

    /// <summary>
    /// Close the exporter
    /// </summary>
    public void Close()
    {

    }

    /// <summary>
    /// File importer options
    /// </summary>
    public class FileExportOptions
    {
        /// <summary>
        /// Override the filename
        /// </summary>
        public string filename;

        /// <summary>
        /// Delete the source file after completion.
        /// </summary>
        public bool deleteSource = false;

        /// <summary>
        /// True to enable integrity.
        /// </summary>
        public bool integrity = false;

        /// <summary>
        /// Callback when progress changes
        /// </summary>
        public Action<long, long> onProgressChanged;
    }
}
