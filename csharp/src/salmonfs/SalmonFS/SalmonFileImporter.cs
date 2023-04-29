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
    public class SalmonFileImporter
    {
        private static bool enableLog;
        private static bool enableLogDetails;

        private const int DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024;
        private const int DEFAULT_THREADS = 1;
        private const int MIN_FILE_SIZE = 1 * 1024 * 1024;

        private int bufferSize;
        private int threads;
        private bool stopped = false;
        private bool failed = false;

        public delegate void OnProgressChanged(IRealFile file, long bytesRead, long totalBytesRead, string message);
        /// <summary>
        /// Informs when the task progress has changed
        /// </summary>
        public OnProgressChanged OnTaskProgressChanged;

        /// <summary>
        /// Constructs a file importer that can be used to import files to the salmon vault
        /// </summary>
        /// <param name="bufferSize">Buffer size to be used when encrypting files. 
        /// If using integrity this value has to be a multiple of the Chunk size.
        /// If not using integrity it should be a multiple of the AES block size for better performance</param>
        /// <param name="threads"></param>
        public SalmonFileImporter(int bufferSize = DEFAULT_BUFFER_SIZE, int threads = DEFAULT_THREADS, bool? overrideIntegrity = null)
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

        /// <summary>
        /// Stops all current importing tasks
        /// </summary>
        public void Stop()
        {
            stopped = true;
        }

        public bool IsRunning()
        {
            return !stopped;
        }

        

        /// <summary>
        /// Imports a real file into the file vault.
        /// </summary>
        /// <param name="fileToImport">The external file that will be imported</param>
        /// <param name="dir">The directory inside the vault that it will be imported</param>
        /// <param name="deleteSource">If true delete the source file</param>
        /// <returns></returns>
        public SalmonFile ImportFile(IRealFile fileToImport, SalmonFile dir, bool deleteSource, bool? integrity = null)
        {
            stopped = false;
            long startTime = 0;
            if (enableLog)
            {
                startTime = SalmonTime.CurrentTimeMillis();
            }
            long totalBytesRead = 0;
            failed = false;
            
            AutoResetEvent done = new AutoResetEvent(false);
            SalmonFile salmonFile = dir.CreateFile(fileToImport.GetBaseName());
            salmonFile.SetAllowOverwrite(true);
            if (integrity != null)
                salmonFile.SetApplyIntegrity((bool)integrity);
            long fileSize = fileToImport.Length();
            int runningThreads = 1;
            long partSize = fileSize;

            if (fileSize > MIN_FILE_SIZE)
            {
                partSize = (int)Math.Ceiling(fileSize / (float)threads);
                // if we want to check integrity we align to the HMAC Chunk size instead of the AES Block
                long minimumPartSize = salmonFile.GetMinimumPartSize();
                long rem = partSize % minimumPartSize;
                if (rem != 0)
                    partSize += minimumPartSize - rem;

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
                    ImportFileChunk(fileToImport, salmonFile, start, length, ref totalBytesRead);
                    runningThreads--;
                    if(runningThreads == 0)
                        done.Set();
                });
            }
            done.WaitOne();
            if (stopped)
                salmonFile.GetRealFile().Delete();
            else
            {
                if (deleteSource)
                    fileToImport.Delete();
            }
            if (enableLog)
            {
                long total = SalmonTime.CurrentTimeMillis() - startTime;
                Console.WriteLine("SalmonFileImporter AesType: " + SalmonStream.GetProviderType() + " File: " + fileToImport.GetBaseName()
                    + " imported and signed " + totalBytesRead + " bytes in total time: " + total + " ms"
                    + ", avg speed: " + totalBytesRead / (float)total + " bytes/sec");
            }
            if (stopped || failed)
                return null;
            return salmonFile;
        }

        /// <summary>
        /// Import a file chunk into a file in the vault
        /// </summary>
        /// <param name="fileToImport">The external file that will be imported</param>
        /// <param name="salmonFile">The file that will be imported to</param>
        /// <param name="start">The start position of the byte data that will be imported</param>
        /// <param name="count">The length of the file content that will be imported</param>
        /// <param name="totalBytesRead">The total bytes read from the external file</param>
        private void ImportFileChunk(IRealFile fileToImport, SalmonFile salmonFile,
            long start, long count, ref long totalBytesRead)
        {
            long startTime = SalmonTime.CurrentTimeMillis();
            long totalChunkBytesRead = 0;

            SalmonStream targetStream = null;
            System.IO.Stream sourceStream = null;

            try
            {
                targetStream = salmonFile.GetOutputStream();
                targetStream.Position = start;

                sourceStream = fileToImport.GetInputStream();
                sourceStream.Position = start;

                byte[] bytes = new byte[bufferSize];
                int bytesRead = 0;
                if (enableLogDetails)
                {
                    Console.WriteLine("SalmonFileImporter: FileChunk: " + salmonFile.GetRealFile().GetBaseName() + ": " + salmonFile.GetBaseName()
                            + " start = " + start + " count = " + count);
                }
                while ((bytesRead = sourceStream.Read(bytes, 0, Math.Min(bytes.Length, (int)(count - totalChunkBytesRead)))) > 0 
                    && totalChunkBytesRead < count)
                {
                    if (stopped)
                        break;

                    targetStream.Write(bytes, 0, bytesRead);
                    totalChunkBytesRead += bytesRead;
                    
                    totalBytesRead += bytesRead;
                    NotifyProgressListener(fileToImport, totalBytesRead, fileToImport.Length(), "Importing File: " + fileToImport.GetBaseName());
                }
                if (enableLogDetails)
                {
                    long total = SalmonTime.CurrentTimeMillis() - startTime;
                    Console.WriteLine("SalmonFileImporter: File Chunk: " + fileToImport.GetBaseName()
                            + " imported " + totalChunkBytesRead + " bytes in: " + total + " ms"
                            + ", avg speed: " + totalChunkBytesRead / (float)total + " bytes/sec");
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                failed = true;
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

        private void NotifyProgressListener(IRealFile file, long bytesRead, long totalBytes, string message)
        {
            if (OnTaskProgressChanged != null)
                OnTaskProgressChanged.Invoke(file, bytesRead, totalBytes, message);
        }
    }
}