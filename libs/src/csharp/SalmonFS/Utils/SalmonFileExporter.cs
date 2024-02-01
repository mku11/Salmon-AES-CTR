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
using Mku.Salmon.IO;
using Mku.SalmonFS;

namespace Mku.Utils;

/// <summary>
/// Exports files from the drive to the real filesystem
/// </summary>
public class SalmonFileExporter
{
    /// <summary>
    ///  The global default buffer size to use when reading/writing on the SalmonStream.
    /// </summary>
    private static readonly int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /// <summary>
    ///  The global default threads to use.
    /// </summary>
    private static readonly int DEFAULT_THREADS = 1;

    /// <summary>
    ///  True if multithreading is enabled.
    /// </summary>
    private static readonly bool enableMultiThread = true;

    private static bool enableLog;
    private static bool enableLogDetails;

    /// <summary>
    ///  Current buffer size.
    /// </summary>
    private readonly int bufferSize;

    /// <summary>
    ///  Current threads.
    /// </summary>
    private readonly int threads;

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
    /// Instantiate a file exporter.
    /// </summary>
    /// <param name="bufferSize"></param>
    /// <param name="threads"></param>
    public SalmonFileExporter(int bufferSize, int threads)
    {
        if (bufferSize == 0)
            bufferSize = DEFAULT_BUFFER_SIZE;
        if (threads == 0)
            threads = DEFAULT_THREADS;
        this.bufferSize = bufferSize;
        this.threads = threads;
    }

    /// <summary>
    /// Enable logging operations.
    /// </summary>
    /// <param name="value"></param>
    public static void SetEnableLog(bool value)
    {
        enableLog = value;
    }

    /// <summary>
    /// Enable detailed log.
    /// </summary>
    /// <param name="value"></param>
    public static void SetEnableLogDetails(bool value)
    {
        enableLogDetails = value;
    }

    /// <summary>
    /// True if an operation is currently running.
    /// </summary>
    /// <returns></returns>
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
    ///  <param name="filename">    The filename to use</param>
    ///  <param name="deleteSource">Delete the source file when the export finishes successfully</param>
    ///  <param name="integrity">True to verify integrity</param>
    public IRealFile ExportFile(SalmonFile fileToExport, IRealFile exportDir, string filename,
        bool deleteSource, bool integrity, Action<long, long> OnProgress)
    {
        if(IsRunning())
            throw new Exception("Another export is running");
        if (fileToExport.IsDirectory)
            throw new Exception("Cannot export directory, use SalmonFileCommander instead");

        IRealFile exportFile;
        filename = filename ?? fileToExport.BaseName;
        try
        {
            if (!enableMultiThread && threads != 1)
                throw new NotSupportedException("Multithreading is not supported");

            long startTime = 0;
            stopped = false;
            if (enableLog)
            {
                startTime = Mku.Time.Time.CurrentTimeMillis();
            }
            long[] totalBytesWritten = new long[] { 0 };
            failed = false;

            if (!exportDir.Exists)
                exportDir.Mkdir();
            exportFile = exportDir.CreateFile(filename);
            // we use the drive hash key for integrity verification
            fileToExport.SetVerifyIntegrity(integrity, null);

            long fileSize = fileToExport.Size;
            int runningThreads = 1;
            long partSize = fileSize;

            // if we want to check integrity we align to the chunk size otherwise to the AES Block
            long minPartSize = SalmonFileUtils.GetMinimumPartSize(fileToExport);
            if (partSize > minPartSize && threads > 1)
            {
                partSize = (int)Math.Ceiling(fileSize / (float)threads);
                partSize -= partSize % minPartSize;
                runningThreads = (int)(fileSize / partSize);
            }

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
            if (stopped)
                exportFile.Delete();
            else if (deleteSource)
                fileToExport.RealFile.Delete();
            if (lastException != null)
                throw lastException;
            if (enableLog)
            {
                long total = Mku.Time.Time.CurrentTimeMillis() - startTime;
                Console.WriteLine("SalmonFileExporter AesType: " + SalmonStream.AesProviderType 
                    + " File: " + fileToExport.BaseName + " verified and exported "
                        + totalBytesWritten[0] + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalBytesWritten[0] / (float)total + " Kbytes/sec");
            }
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
        return exportFile;
    }

    /// <summary>
    ///  Export a file part from the drive.
	/// </summary>
	///  <param name="fileToExport">     The file the part belongs to</param>
    ///  <param name="exportFile">       The file to copy the exported part to</param>
    ///  <param name="start">            The start position on the file</param>
    ///  <param name="count">            The length of the bytes to be decrypted</param>
    ///  <param name="totalBytesWritten">The total bytes that were written to the external file</param>
    private void ExportFilePart(SalmonFile fileToExport, IRealFile exportFile, long start, long count, 
        long[] totalBytesWritten, Action<long, long> OnProgress)
    {
        long startTime = Mku.Time.Time.CurrentTimeMillis();
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
            if (enableLogDetails)
            {
                Console.WriteLine("SalmonFileExporter: FilePart: " + fileToExport.RealFile.BaseName + ": "
                        + fileToExport.BaseName + " start = " + start + " count = " + count);
            }

            while ((bytesRead = sourceStream.Read(bytes, 0, Math.Min(bytes.Length,
                    (int)(count - totalPartBytesWritten)))) > 0 && totalPartBytesWritten < count)
            {
                if (stopped)
                    break;

                targetStream.Write(bytes, 0, bytesRead);
                totalPartBytesWritten += bytesRead;

                totalBytesWritten[0] += bytesRead;
                if (OnProgress != null)
                    OnProgress(totalBytesWritten[0], fileToExport.Size);
            }
            if (enableLogDetails)
            {
                long total = Mku.Time.Time.CurrentTimeMillis() - startTime;
                Console.WriteLine("SalmonFileExporter: File Part: " + fileToExport.BaseName + " exported " + totalPartBytesWritten
                        + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalPartBytesWritten / (float)total + " Kbytes/sec");
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
}
