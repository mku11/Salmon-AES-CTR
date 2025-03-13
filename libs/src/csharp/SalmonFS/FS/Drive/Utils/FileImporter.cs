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
/// Import files from the real file system to the drive
/// </summary>
public abstract class FileImporter
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
    /// Prepare the files before import
    /// </summary>
    /// <param name="importedFile">The imported virtual file</param>
    /// <param name="integrity">True if apply integrity</param>
    protected abstract void OnPrepare(IVirtualFile importedFile, bool integrity);

    /// <summary>
    /// Get the minimum required part size for splitting a virtual file
    /// </summary>
    /// <param name="file">The virtual file</param>
    /// <returns>The part size</returns>
    protected abstract long GetMinimumPartSize(IVirtualFile file);

    /// <summary>
    ///  Constructs a file importer that can be used to import files to the drive
	/// </summary>
	///  <param name="bufferSize">Buffer size to be used when encrypting files.</param>
    ///                    If using integrity this value has to be a multiple of the Chunk size.
    ///                    If not using integrity it should be a multiple of the AES block size for better performance
    ///  <param name="threads">The threads to use</param>
    public void Initialize(int bufferSize, int threads)
    {
        this.bufferSize = bufferSize;
        if (this.bufferSize == 0)
            this.bufferSize = DEFAULT_BUFFER_SIZE;
        this.threads = threads;
        if (this.threads == 0)
            this.threads = DEFAULT_THREADS;
    }

    /// <summary>
    ///  Stops all current importing tasks
    /// </summary>
    public void Stop()
    {
        stopped = true;
    }

    /// <summary>
    ///  True if importer is currently running a job.
	/// </summary>
	///  <returns>True if running</returns>
    public bool IsRunning()
    {
        return !stopped;
    }

    /// <summary>
    ///  Imports a real file into the drive.
    /// </summary>
    ///  <param name="fileToImport">The source file that will be imported in to the drive.</param>
    ///  <param name="dir">         The target directory in the drive that the file will be imported</param>
    ///  <param name="filename">The filename to use</param>
    ///  <param name="deleteSource">If true delete the source file.</param>
    ///  <param name="integrity">True to enable integrity</param>
    ///  <param name="OnProgress">    Progress observer</param>
    ///  <returns>The IVirtualFile that was imported</returns>
    virtual
    public IVirtualFile ImportFile(IRealFile fileToImport, IVirtualFile dir, string filename,
        bool deleteSource, bool integrity, Action<long, long> OnProgress)
    {
        if (IsRunning())
            throw new Exception("Another import is running");
        if (fileToImport.IsDirectory)
            throw new Exception("Cannot import directory, use FileCommander instead");

        filename = filename ?? fileToImport.BaseName;
        long[] totalBytesRead = new long[] { 0 };
        IVirtualFile importedFile;
        try
        {
            stopped = false;
            failed = false;
            lastException = null;

            importedFile = dir.CreateFile(filename);
            this.OnPrepare(importedFile, integrity);

            long fileSize = fileToImport.Length;
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = GetMinimumPartSize(importedFile);
            if (partSize > minPartSize && threads > 1)
            {
                partSize = (int)Math.Ceiling(fileSize / (float)threads);
                if(partSize > minPartSize)
					partSize -= partSize % minPartSize;
				else
					partSize = minPartSize;
                runningThreads = (int)(fileSize / partSize);
            }

            if (runningThreads == 1)
            {
                ImportFilePart(fileToImport, importedFile, 0, fileSize, totalBytesRead, OnProgress);
            }
            else
            {
                this.SubmitImportJobs(runningThreads, partSize, fileToImport, importedFile, totalBytesRead, integrity, OnProgress);
            }

            if (stopped)
                importedFile.RealFile.Delete();
            else if (deleteSource)
                fileToImport.Delete();
            if (lastException != null)
                throw lastException;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
            stopped = true;
            throw ex;
        }
        if (stopped || failed)
        {
            stopped = true;
            return null;
        }
        stopped = true;
        return importedFile;
    }

    private void SubmitImportJobs(int runningThreads, long partSize, IRealFile fileToImport, IVirtualFile importedFile, long[] totalBytesRead, bool integrity, Action<long, long> OnProgress)
    {
        long fileSize = fileToImport.Length;

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
                    ImportFilePart(fileToImport, importedFile, start, length, totalBytesRead, OnProgress);
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
    ///  Import a file part into a file in the drive.
    /// </summary>
    ///  <param name="fileToImport">  The external file that will be imported</param>
    ///  <param name="salmonFile">    The file that will be imported to</param>
    ///  <param name="start">         The start position of the byte data that will be imported</param>
    ///  <param name="count">         The length of the file content that will be imported</param>
    ///  <param name="totalBytesRead">The total bytes read from the external file</param>
    ///  <param name="OnProgress">    Progress observer</param>
    private void ImportFilePart(IRealFile fileToImport, IVirtualFile salmonFile,
                                long start, long count, long[] totalBytesRead, Action<long, long> OnProgress)
    {
        long totalPartBytesRead = 0;

        Stream targetStream = null;
        Stream sourceStream = null;

        try
        {
            targetStream = salmonFile.GetOutputStream();
            targetStream.Position = start;

            sourceStream = fileToImport.GetInputStream();
            sourceStream.Position = start;

            byte[] bytes = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = sourceStream.Read(bytes, 0, (int)Math.Min((long)bytes.Length, count - totalPartBytesRead))) > 0
                    && totalPartBytesRead < count)
            {
                if (stopped)
                    break;

                targetStream.Write(bytes, 0, bytesRead);
                totalPartBytesRead += bytesRead;

                totalBytesRead[0] += bytesRead;
                if (OnProgress != null)
                    OnProgress(totalBytesRead[0], fileToImport.Length);
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
    /// Close this importer
    /// </summary>
    public void Close()
    {
        
    }
}