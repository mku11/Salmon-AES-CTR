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
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Salmon.Streams;
using static Salmon.SalmonIntegrity;
using static Salmon.Streams.SalmonStream;
using Salmon.Net.FS;
using System.Reflection;

namespace Salmon.Test.Utils
{
    class TestHelper
    {
        private const int TEXT_ITERATIONS = 20;
        public static string SeekAndGetSubstringByRead(SalmonStream reader, int seek, int readCount, SeekOrigin seekOrigin)
        {
            reader.Seek(seek, seekOrigin);
            MemoryStream encOuts2 = new MemoryStream();
            
            byte[] bytes = new byte[readCount];
            int bytesRead;
            long totalBytesRead = 0;
            while (totalBytesRead < readCount && (bytesRead = reader.Read(bytes, 0, bytes.Length)) > 0)
            {
                // we skip the alignment offset and start reading the bytes we need
                encOuts2.Write(bytes, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            string decText1 = System.Text.UTF8Encoding.Default.GetString(encOuts2.ToArray());
            encOuts2.Close();
            return decText1;
        }

        public static string readFileTest(SalmonFile salmonFile, int bufferSize)
        {
            string contents = null;
            string filename = salmonFile.GetBaseName();
            Stream ins = salmonFile.GetInputStream(bufferSize);
            MemoryStream outs = new MemoryStream();
            ins.CopyTo(outs, bufferSize);
            outs.Flush();
            contents = System.Text.UTF8Encoding.Default.GetString(outs.ToArray());
            outs.Close();
            return contents;
        }

        public static string GetChecksum(IRealFile realFile)
        {
            MD5 md5 = MD5.Create();
            FileStream stream = File.OpenRead(realFile.GetAbsolutePath());
            byte[] hash = md5.ComputeHash(stream);
            string hashString = Convert.ToBase64String(hash);
            return hashString;
        }

        public static void EncryptWriteDecryptRead(string text, byte[] key, byte[] iv,
            int encBufferSize = 0, int decBufferSize = 0, bool testIntegrity = false, int? chunkSize = null, 
            byte [] hmacKey = null, bool flipBits = false, string header = null, int? maxTextLength = null)
        {
            string testText = text;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < TEXT_ITERATIONS; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();
            if(maxTextLength != null && maxTextLength < plainText.Length)
                plainText = plainText.Substring(0, (int) maxTextLength);

            int headerLength = 0;
            if(header!=null)
                headerLength = System.Text.UTF8Encoding.UTF8.GetBytes(header).Length;
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            byte[] encBytes = Encrypt(inputBytes, key, iv, encBufferSize, header: header,
                integrity: testIntegrity, chunkSize: chunkSize, hmacKey: hmacKey);
            if(flipBits)
                encBytes[encBytes.Length / 2] = 0;

            // Use SalmonStrem to read from cipher byte array and MemoryStream to Write to byte array
            byte[] outputByte2 = Decrypt(encBytes, key, iv, decBufferSize, 
                integrity: testIntegrity, chunkSize: chunkSize, hmacKey: hmacKey,
                headerLength: header != null ? (int?)headerLength : null);
            string decText = System.Text.UTF8Encoding.Default.GetString(outputByte2);

            Console.WriteLine(plainText);
            Console.WriteLine(decText);
            Assert.AreEqual(plainText, decText);
        }


        public static byte[] Encrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
            bool integrity = false, int? chunkSize = null, byte [] hmacKey = null, 
            string header = null)
        {
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            byte[] headerData = null;
            if (header != null)
            {
                headerData = System.Text.UTF8Encoding.UTF8.GetBytes(header);
                outs.Write(headerData);
            }
            SalmonStream writer = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs, 
                headerData: headerData, 
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            
            if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
            {
                ins.WriteTo(writer);
                writer.Flush();
            }
            else
            { // use our manual buffer to test
                int bytesRead = 0;
                byte[] buffer = new byte[bufferSize];
                while ((bytesRead = ins.Read(buffer, 0, buffer.Length)) > 0)
                {
                    writer.Write(buffer, 0, bytesRead);
                }
                writer.Flush();
            }
            byte[] bytes = outs.ToArray();
            writer.Close();
            ins.Close();
            return bytes;
        }

        public static byte[] Decrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
            bool integrity = false, int? chunkSize = null, byte [] hmacKey = null,
            int? headerLength = null)
        {
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            byte[] headerData = null;
            if (headerLength != null)
            {
                headerData = new byte[(int)headerLength];
                ins.Read(headerData, 0, headerData.Length);
            }
            SalmonStream reader = new SalmonStream(key, iv, EncryptionMode.Decrypt, ins,
                headerData: headerData ,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);            

            if (bufferSize == 0) // use the internal buffersize of the memorystream to copy
            {
                reader.CopyTo(outs);
                outs.Flush();
            }
            else
            { // use our manual buffer to test
                int bytesRead = 0;
                byte[] buffer = new byte[bufferSize];
                while ((bytesRead = reader.Read(buffer, 0, buffer.Length)) > 0)
                {
                    outs.Write(buffer, 0, bytesRead);
                }
                outs.Flush();
            }
            byte[] bytes = outs.ToArray();
            reader.Close();
            outs.Close();
            return bytes;
        }

        public static string GenerateFolder(string dirPath)
        {
            long time = SalmonTime.CurrentTimeMillis();
            DirectoryInfo dir = System.IO.Directory.CreateDirectory(dirPath + "_" + time);
            return dir.FullName;
        }

        internal static void ImportAndExport(string vaultDir, string pass, string importFile,
            int importBufferSize, int importThreads, int exportBufferSize, int exportThreads, bool integrity = false,
            bool bitflip = false, long flipPosition = -1, bool shouldBeEqual = true, 
            bool? overrideApplyFileIntegrity = null, bool? overrideVerifyFileIntegrity = null, 
            byte[] vaultNonce = null)
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            SalmonDriveManager.SetDriveLocation(TestHelper.GenerateFolder(vaultDir));
            SalmonFile RootDir;
            if (!SalmonDriveManager.GetDrive().HasConfig())
            {
                SalmonDriveManager.GetDrive().SetPassword(pass);
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            }
            else
            {
                SalmonDriveManager.GetDrive().Authenticate(pass);
            }
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(integrity);
            if(vaultNonce!=null)
                SalmonDriveManager.GetDrive().GetKey().SetVaultNonce(vaultNonce);

            SalmonFile salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();

            DotNetFile fileToImport = new DotNetFile(importFile);
            string hashPreImport = TestHelper.GetChecksum(fileToImport);

            // import
            SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
            SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, salmonRootDir, false, integrity: overrideApplyFileIntegrity);
            Assert.IsNotNull(salmonFile);
            Assert.IsTrue(salmonFile.Exists());
            SalmonFile[] salmonFiles = SalmonDriveManager.GetDrive().GetVirtualRoot().ListFiles();
            long realFileSize = fileToImport.Length();
            foreach (SalmonFile file in salmonFiles)
            {
                if (file.GetBaseName().Equals(fileToImport.GetBaseName()))
                {
                    if (shouldBeEqual)
                    {
                        Assert.IsTrue(file.Exists());
                        long fileSize = file.GetSize();
                        Assert.AreEqual(realFileSize, fileSize);
                    }
                }
            }

            // export
            SalmonFileExporter fileExporter = new SalmonFileExporter(exportBufferSize, exportThreads);
            if(bitflip)
                FlipBit(salmonFile, flipPosition);

            IRealFile exportFile = fileExporter.ExportFile(salmonFile, SalmonDriveManager.GetDrive().GetExportDir(), true, integrity: overrideVerifyFileIntegrity);

            string hashPostExport = TestHelper.GetChecksum(exportFile);
            if (shouldBeEqual)
            {
                Assert.AreEqual(hashPreImport, hashPostExport);
            }
        }

        private static void FlipBit(SalmonFile salmonFile, long position)
        {
            Stream stream = salmonFile.GetRealFile().GetOutputStream();
            stream.Position = position;
            stream.Write(new byte[] { 1 }, 0, 1);
            stream.Close();
        }

        internal static void SeekAndRead(string text, byte [] key, byte[] iv, 
            bool integrity = false, int chunkSize = 0, byte [] hmacKey = null,
            bool alignToChunk = true)
        {
            string testText = text;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < TEXT_ITERATIONS; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();

            // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs, 
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            ins.CopyTo(encWriter);
            ins.Close();
            encWriter.Flush();
            encWriter.Close();
            byte[] encBytes = outs.ToArray();

            // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
            MemoryStream encIns = new MemoryStream(encBytes);
            SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            string correctText = null;
            string decText = null;

            correctText = plainText.Substring(0, 6);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring(0, 6);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, 6, SeekOrigin.Begin);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring((int)decReader.Position + 4, 4);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 4, 4, SeekOrigin.Current);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring((int)decReader.Position + 6, 4);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 6, 4, SeekOrigin.Current);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring((int)decReader.Position + 10, 6);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 10, 6, SeekOrigin.Current);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring(12, 8);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 12, 8, SeekOrigin.Begin);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            correctText = plainText.Substring(plainText.Length - 14, 7);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 14, 7, SeekOrigin.End);
            Assert.AreEqual(correctText, decText);

            correctText = plainText.Substring(plainText.Length - 27, 12);
            decText = TestHelper.SeekAndGetSubstringByRead(decReader, 27, 12, SeekOrigin.End);
            Assert.AreEqual(correctText, decText);
            TestCounter(decReader);

            encIns.Close();
            decReader.Close();
        }

        internal static void SeekTestCounterAndBlock(string text, byte[] key, byte[] iv,
            bool integrity = false, int chunkSize = 0, byte[] hmacKey = null,
            bool alignToChunk = true)
        {
            string testText = text;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < TEXT_ITERATIONS; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();

            // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            ins.CopyTo(encWriter);
            ins.Close();
            encWriter.Flush();
            encWriter.Close();
            byte[] encBytes = outs.ToArray();

            // Use SalmonStrem to read from cipher text and seek and read to different positions in the stream
            MemoryStream encIns = new MemoryStream(encBytes);
            SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            for(int i=0; i<100; i++)
            {
                decReader.Position += 7;
                TestCounter(decReader);
            }

            encIns.Close();
            decReader.Close();
        }

        /// <summary>
        /// Assumes little endianess though this test might fail for some archs than x86
        /// </summary>
        /// <param name="decReader"></param>
        private static void TestCounter(SalmonStream decReader)
        {
            long expectedBlock = decReader.Position / decReader.GetBlockSize();
            Assert.AreEqual(expectedBlock, decReader.GetBlock());
            
            int counterBlock = BitConverter.ToInt32(decReader.GetCounter(), 0);
            int expectedCounterValue = (int) decReader.GetBlock();
            Assert.AreEqual(expectedCounterValue, counterBlock);

            int fileCounter = BitConverter.ToInt32(decReader.GetCounter(), 4);
            int expectedFileCounter = BitConverter.ToInt32(decReader.GetNonce(), 0);
            Assert.AreEqual(expectedFileCounter, fileCounter);

            long nonce = BitConverter.ToInt64(decReader.GetCounter(), 8);
            long expectedNonce = BitConverter.ToInt64(decReader.GetNonce(), 4);
            Assert.AreEqual(expectedNonce, nonce);
        }

        internal static void SeekAndWrite(string text, byte[] key, byte[] iv,
            long seek, int writeCount, string textToWrite, bool alignToChunk = false,
            bool integrity = false, int chunkSize = 0, byte[] hmacKey = null,
            bool setAllowRangeWrite = false
            )
        {
            string testText = text;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < TEXT_ITERATIONS; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();

            // Use SalmonStream read from text byte array and MemoryStream to Write to byte array
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, outs,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            ins.CopyTo(encWriter);
            ins.Close();
            encWriter.Flush();
            encWriter.Close();
            byte[] encBytes = outs.ToArray();

            // partial write
            byte[] writeBytes = System.Text.UTF8Encoding.Default.GetBytes(textToWrite);
            MemoryStream pOuts = new MemoryStream(encBytes);
            SalmonStream partialWriter = new SalmonStream(key, iv, EncryptionMode.Encrypt, pOuts,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            long alignedPosition = seek;
            int alignOffset = 0;
            int count = writeCount;
            if (alignToChunk && partialWriter.GetChunkSize() > 0)
            {
                int bytesRead;
                // if we have enabled integrity we align the position and the buffer
                if (seek % partialWriter.GetChunkSize() != 0)
                {
                    alignedPosition = seek / partialWriter.GetChunkSize() * partialWriter.GetChunkSize();
                    alignOffset = (int) (seek % partialWriter.GetChunkSize());
                    count = count + alignOffset;

                    if (count > partialWriter.GetChunkSize() && count % partialWriter.GetChunkSize() != 0)
                        count = count / partialWriter.GetChunkSize() * partialWriter.GetChunkSize() + partialWriter.GetChunkSize();
                    else if (count < partialWriter.GetChunkSize())
                        count = partialWriter.GetChunkSize();
                    
                }

                // Read the whole chunk from the stream
                byte[] buffer = new byte[count];
                SalmonStream inputStream = new SalmonStream(key, iv, EncryptionMode.Encrypt, new MemoryStream(encBytes),
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
                inputStream.Seek(alignedPosition, SeekOrigin.Begin);
                bytesRead = inputStream.Read(buffer, 0, count);
                inputStream.Close();
                Array.Copy(writeBytes, 0, buffer, alignOffset, writeCount);
                writeBytes = buffer;
                count = bytesRead;
            }
            if(setAllowRangeWrite)
                partialWriter.SetAllowRangeWrite(setAllowRangeWrite);
            partialWriter.Seek(alignedPosition, SeekOrigin.Begin);
            partialWriter.Write(writeBytes, 0, count);
            partialWriter.Close();
            pOuts.Close();


            // Use SalmonStrem to read from cipher text and test if the write was succesfull
            MemoryStream encIns = new MemoryStream(encBytes);
            SalmonStream decReader = new SalmonStream(key, iv, EncryptionMode.Decrypt, encIns,
                integrity: integrity, chunkSize: chunkSize, hmacKey: hmacKey);
            string decText = TestHelper.SeekAndGetSubstringByRead(decReader, 0, text.Length, SeekOrigin.Begin);

            Assert.AreEqual(text.Substring(0, (int)seek), decText.Substring(0, (int)seek));
            Assert.AreEqual(textToWrite, decText.Substring((int) seek, writeCount));
            Assert.AreEqual(text.Substring((int)seek + writeCount), decText.Substring((int)seek + writeCount));
            TestCounter(decReader);


            encIns.Close();
            decReader.Close();
        }

        internal static void ShouldCreateFileWithoutVault(string text, byte [] key, bool applyIntegrity, bool verifyIntegrity, int chunkSize, byte [] hmacKey,
            byte[] filenameNonce, byte[] fileNonce, string outputDir, bool flipBit = false, int flipPosition = -1)
        {
            // write file
            IRealFile realDir = new DotNetFile(outputDir);
            SalmonFile dir = new SalmonFile(realDir);
            string filename = "test_" + SalmonTime.CurrentTimeMillis() + ".txt";
            SalmonFile newFile = dir.CreateFile(filename, key, filenameNonce, fileNonce);
            if(applyIntegrity)
                newFile.SetApplyIntegrity(true, hmacKey, requestChunkSize: chunkSize);
            Stream stream = newFile.GetOutputStream();
            byte[] testBytes = System.Text.UTF8Encoding.Default.GetBytes(text);
            stream.Write(testBytes);
            stream.Flush();
            stream.Close();
            string realFilePath = newFile.GetRealFile().GetAbsolutePath();
            
            // tamper
            if (flipBit)
            {
                IRealFile realTmpFile = newFile.GetRealFile();
                Stream realStream = realTmpFile.GetOutputStream();
                realStream.Position = flipPosition;
                realStream.Write(new byte[] { 0}, 0, 1);
                realStream.Flush();
                realStream.Close();
            }

            // open file for read
            IRealFile realFile = new DotNetFile(realFilePath);
            SalmonFile readFile = new SalmonFile(realFile);
            readFile.SetEncryptionKey(key);
            readFile.SetRequestedNonce(fileNonce);
            if(verifyIntegrity)
                readFile.SetVerifyIntegrity(true, hmacKey);
            SalmonStream inStream = readFile.GetInputStream();
            byte[] textBytes = new byte[testBytes.Length];
            inStream.Read(textBytes, 0, textBytes.Length);
            string textString = System.Text.UTF8Encoding.Default.GetString(textBytes);
            Assert.AreEqual(text, textString);
        }

        internal static void TestCounterValue(string text, byte [] key, byte [] nonce, long counter)
        {
            byte[] testTextBytes = System.Text.UTF8Encoding.Default.GetBytes(text);
            MemoryStream ms = new MemoryStream(testTextBytes);
            SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms);
            stream.SetAllowRangeWrite(true);

            // creating enormous files to test is overkill and since the law was made for man and not the other way around 
            // we resort to reflection to test this.
            MethodInfo methodInfo = stream.GetType().GetMethod("IncrementCounter", BindingFlags.Instance | BindingFlags.NonPublic);
            methodInfo.Invoke(stream, new object[] { counter });

            if (stream != null)
                stream.Close();
        }
    }
}
