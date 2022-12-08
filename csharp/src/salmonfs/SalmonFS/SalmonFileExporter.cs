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
using Salmon.Streams;
using System;
using System.Threading;

namespace Salmon.FS
{
    public class SalmonFileExporter
    {
        private static bool enableLog = false;
        private static bool enableLogDetails = false;

        private const int DEFAULT_ENC_BUFFER_SIZE = 4 * 1024 * 1024;
        private const int DEFAULT_ENC_THREADS = 1;
        private const int MIN_FILE_SIZE = 1 * 1024 * 1024;
        

        private int bufferSize = DEFAULT_ENC_BUFFER_SIZE;
        private int threads = DEFAULT_ENC_THREADS;
        private bool stopped = false;
        private bool failed = false;
        private Exception lastException;

        public delegate void OnProgressChanged(object sender, long bytesRead, long totalBytesRead, string message);
        public OnProgressChanged OnTaskProgressChanged;

        public SalmonFileExporter(int bufferSize = DEFAULT_ENC_BUFFER_SIZE, int threads = DEFAULT_ENC_THREADS)
        {
            this.bufferSize = bufferSize;
            this.threads = threads;
        }

        public static void SetEnableLog(bool value)
        {
            enableLog = value;
        }


        public static void SetEnableLogDetails(bool value)
        {
            enableLogDetails = value;
        }

        public void Stop()
        {
            stopped = true;
        }

        public bool IsRunning()
        {
            return !stopped;
        }

        /// <summary>
        /// Export a file from the vault to the external directory path
        /// </summary>
        /// <param name="fileToExport">The file that will be exported</param>
        /// <param name="exportDir">The external directory the file will be exported to</param>
        /// <param name="deleteSource">Delete the source file when the export finishes successfully</param>
        /// <returns></returns>
        public IRealFile ExportFile(SalmonFile fileToExport, IRealFile exportDir, bool deleteSource, bool? integrity = null)
        {
            stopped = false;
            long startTime = 0;
            if (enableLog)
            {
                startTime = SalmonTime.CurrentTimeMillis();
            }
            long totalBytesWritten = 0;
            failed = false;

            if (!exportDir.Exists())
                exportDir.Mkdir();
            string targetFileName = fileToExport.GetBaseName();
            IRealFile tfile = exportDir.GetChild(targetFileName);
            if (tfile != null && tfile.Exists())
            {
                String filename = SalmonDriveManager.GetDrive().GetFileNameWithoutExtension(targetFileName);
                String ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(targetFileName);
                targetFileName = filename + "_" + SalmonTime.CurrentTimeMillis() + "." + ext;
            }
            IRealFile exportFile = exportDir.CreateFile(targetFileName);

            if (integrity != null)
                fileToExport.SetVerifyIntegrity((bool)integrity);

            AutoResetEvent done = new AutoResetEvent(false);
            long fileSize = fileToExport.GetSize();
            long partSize = fileSize;
            int runningThreads = 1;
            if (fileSize > MIN_FILE_SIZE)
            {
                partSize = (int)Math.Ceiling(fileSize / (float)threads);

                // if we want to check integrity we align to the HMAC Chunk size otherwise to the AES Block
                long minPartSize = fileToExport.GetMinimumPartSize();

                // calculate the last chunk size
                long rem = partSize % minPartSize;
                if (rem != 0)
                    partSize += minPartSize - rem;

                runningThreads = (int)(fileSize / partSize);
                if (fileSize % partSize != 0)
                    runningThreads++;
            }

            for (int i = 0; i < runningThreads; i++)
            {
                int index = i;
                ThreadPool.QueueUserWorkItem(state => {
                    long start = partSize * index;
                    long length = Math.Min(partSize, fileSize - start);
                    ExportFilePart(fileToExport, exportFile, start, length, ref totalBytesWritten);
                    runningThreads--;
                    if (runningThreads == 0)
                        done.Set();
                });
            }
            done.WaitOne();
            if (stopped)
                exportFile.Delete();
            else if (deleteSource)
                fileToExport.GetRealFile().Delete();
            if (lastException != null)
                throw lastException;
            if (enableLog)
            {
                long total = SalmonTime.CurrentTimeMillis() - startTime;
                Console.WriteLine("SalmonFileExporter AesType: " + SalmonStream.GetProviderType() + " File: " + fileToExport.GetBaseName() + " verified and exported "
                        + totalBytesWritten + " bytes in: " + total + " ms"
                        + ", avg speed: " + totalBytesWritten / (float)total + " bytes/sec");
            }
            if (stopped || failed)
                return null;
            return exportFile;
        }

        /// <summary>
        /// Export a file chunk from the vault
        /// </summary>
        /// <param name="fileToExport">The file the part belongs to</param>
        /// <param name="exportFile">The file to copy the exported part to</param>
        /// <param name="start">The start position on the file</param>
        /// <param name="count">The length of the bytes to be decrypted</param>
        /// <param name="totalBytesWritten">The total bytes that were written to the external file</param>
        private void ExportFilePart(SalmonFile fileToExport, IRealFile exportFile, long start, long count, ref long totalBytesWritten)
        {
            long startTime = SalmonTime.CurrentTimeMillis();
            long totalChunkBytesWritten = 0;

            System.IO.Stream targetStream = null;
            System.IO.Stream sourceStream = null;

            try
            {
                targetStream = exportFile.GetOutputStream(bufferSize);
                targetStream.Position = start;

                sourceStream = fileToExport.GetInputStream(bufferSize);
                sourceStream.Position = start;

                byte[] bytes = new byte[bufferSize];
                int bytesRead = 0;
                if (enableLogDetails)
                {
                    Console.WriteLine("SalmonFileExporter: FileChunk: " + fileToExport.GetRealFile().GetBaseName() + ": "
                            + fileToExport.GetBaseName() + " start = " + start + " count = " + count);
                }

                while ((bytesRead = sourceStream.Read(bytes, 0, Math.Min(bytes.Length, 
                    (int)(count - totalChunkBytesWritten)))) > 0 && totalChunkBytesWritten < count)
                {
                    if (stopped)
                        break;

                    targetStream.Write(bytes, 0, bytesRead);
                    totalChunkBytesWritten += bytesRead;

                    totalBytesWritten += bytesRead;
                    NotifyProgressListener(totalBytesWritten, fileToExport.GetSize(), "Exporting File: " 
                        + fileToExport.GetBaseName());
                }
                if (enableLogDetails)
                {
                    long total = SalmonTime.CurrentTimeMillis() - startTime;
                    Console.WriteLine("SalmonFileExporter: File Chunk: " + fileToExport.GetBaseName() + " exported " + totalChunkBytesWritten
                            + " bytes in: " + total + " ms"
                            + ", avg speed: " + totalBytesWritten / (float)total + " bytes/sec");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
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
                if(sourceStream != null)
                {
                    sourceStream.Close();
                }
            }
        }

        private void NotifyProgressListener(long bytesRead, long totalBytes, string message)
        {
            if (OnTaskProgressChanged != null)
                OnTaskProgressChanged.Invoke(this, bytesRead, totalBytes, message);
        }        
    }
}