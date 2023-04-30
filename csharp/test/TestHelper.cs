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
using Microsoft.VisualStudio.TestTools.UnitTesting;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Security;
using Org.BouncyCastle.Utilities;
using Salmon;
using Salmon.FS;
using Salmon.Net.FS;
using Salmon.Streams;
using Salmon.Test.Utils;
using System;
using System.Collections.Generic;
using System.IO;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using static Salmon.FS.SalmonSequenceConfig;
using static Salmon.SalmonIntegrity;
using static Salmon.Streams.SalmonStream;
using static System.Net.Mime.MediaTypeNames;

namespace Salmon.Test.Utils
{
    internal class TestHelper
    {
        public static readonly int ENC_IMPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        public static readonly int ENC_IMPORT_THREADS = 4;
        public static readonly int ENC_EXPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        public static readonly int ENC_EXPORT_THREADS = 4;

        public static readonly int TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        public static readonly int TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;


        public static readonly string TEST_PASSWORD = @"test123";
        public static readonly string TEST_FALSE_PASSWORD = @"falsepass";

        public static readonly long MAX_ENC_COUNTER = (long)Math.Pow(256, 7);

        public static readonly byte[] TEXT_VAULT_MAX_FILE_NONCE = {
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        public static readonly string TEST_SEQUENCER_FILENAME1 = "seq1.xml";
        public static readonly string TEST_SEQUENCER_FILENAME2 = "seq2.xml";
        private static readonly string TEST_AUTH_FILENAME = "export.slma";

        public static string TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
        public static string TEST_NONCE = "12345678"; // 8 bytes
        public static string TEST_FILENAME_NONCE = "ABCDEFGH"; // 8 bytes
        public static string TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes

        public static string TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
        public static string TEST_TINY_TEXT = "test.txt";
        public static string TEST_TEXT = "This is another test that could be very long if used correct.";
        public static string TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

        public static byte[] TEST_KEY_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_KEY);
        public static byte[] TEST_NONCE_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_NONCE);
        public static byte[] TEST_FILENAME_NONCE_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_FILENAME_NONCE);
        public static byte[] TEST_HMAC_KEY_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_HMAC_KEY);
        public static int TEST_PERF_SIZE = 40 * 1024 * 1024;
        public const int TEXT_ITERATIONS = 20;
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
            string decText1 = Encoding.Default.GetString(encOuts2.ToArray());
            encOuts2.Close();
            return decText1;
        }

        public static string readFileTest(SalmonFile salmonFile, int bufferSize)
        {
            string contents = null;
            string filename = salmonFile.GetBaseName();
            Stream ins = salmonFile.GetInputStream();
            MemoryStream outs = new MemoryStream();
            ins.CopyTo(outs, bufferSize);
            outs.Flush();
            contents = Encoding.Default.GetString(outs.ToArray());
            outs.Close();
            return contents;
        }

        public static string GetChecksum(IRealFile realFile)
        {
            MD5 md5 = MD5.Create();
            FileStream stream = File.OpenRead(realFile.GetAbsolutePath());
            byte[] hash = md5.ComputeHash(stream);
            string hashstring = Convert.ToBase64String(hash);
            return hashstring;
        }

        public static void EncryptWriteDecryptRead(string text, byte[] key, byte[] iv,
            int encBufferSize = 0, int decBufferSize = 0, bool testIntegrity = false, int? chunkSize = null,
            byte[] hmacKey = null, bool flipBits = false, string header = null, int? maxTextLength = null)
        {
            string testText = text;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < TEXT_ITERATIONS; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();
            if (maxTextLength != null && maxTextLength < plainText.Length)
                plainText = plainText.Substring(0, (int)maxTextLength);

            int headerLength = 0;
            if (header != null)
                headerLength = System.Text.UTF8Encoding.UTF8.GetBytes(header).Length;
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            byte[] encBytes = Encrypt(inputBytes, key, iv, encBufferSize, header: header,
                integrity: testIntegrity, chunkSize: chunkSize, hmacKey: hmacKey);
            if (flipBits)
                encBytes[encBytes.Length / 2] = 0;

            // Use SalmonStrem to read from cipher byte array and MemoryStream to Write to byte array
            byte[] outputByte2 = Decrypt(encBytes, key, iv, decBufferSize,
                integrity: testIntegrity, chunkSize: chunkSize, hmacKey: hmacKey,
                headerLength: header != null ? (int?)headerLength : null);
            string decText = Encoding.Default.GetString(outputByte2);

            Console.WriteLine(plainText);
            Console.WriteLine(decText);
            Assert.AreEqual(plainText, decText);
        }


        public static byte[] Encrypt(byte[] inputBytes, byte[] key, byte[] iv, int bufferSize,
            bool integrity = false, int? chunkSize = null, byte[] hmacKey = null,
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
            bool integrity = false, int? chunkSize = null, byte[] hmacKey = null,
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
                headerData: headerData,
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
            bool? overrideApplyFileIntegrity = null, bool? overrideVerifyFileIntegrity = null)
        {

            SalmonDriveManager.CreateDrive(vaultDir, pass);
            SalmonFile rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            rootDir.ListFiles();
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(integrity);

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
            if (bitflip)
                FlipBit(salmonFile, flipPosition);

            IRealFile exportFile = fileExporter.ExportFile(salmonFile, SalmonDriveManager.GetDrive().GetExportDir(), true, integrity: overrideVerifyFileIntegrity);

            string hashPostExport = TestHelper.GetChecksum(exportFile);
            if (shouldBeEqual)
            {
                Assert.AreEqual(hashPreImport, hashPostExport);
            }
        }


        public static void ImportAndSearch(string vaultDir, string pass, string importFile,
                                           int importBufferSize, int importThreads)
        {

            SalmonDriveManager.CreateDrive(vaultDir, pass);
            SalmonFile rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            rootDir.ListFiles();
            SalmonFile salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            DotNetFile fileToImport = new DotNetFile(importFile);
            string rbasename = fileToImport.GetBaseName();

            // import
            SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads, null);
            SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, salmonRootDir, false);

            // trigger the cache to add the filename
            string basename = salmonFile.GetBaseName();

            Assert.IsNotNull(salmonFile);
            Assert.IsTrue(salmonFile.Exists());

            //TODO:
            //SalmonFileSearcher searcher = new SalmonFileSearcher();
            //SalmonFile[] files = searcher.search(salmonRootDir, basename, true, null);
            //Assert.IsTrue(files.Length > 0);
            //Assert.Equals(files[0].GetBaseName(), basename);

        }


        public static void ImportAndCopy(string vaultDir, string pass, string importFile,
                                         int importBufferSize, int importThreads, string newDir, bool move)
        {

            SalmonDriveManager.CreateDrive(vaultDir, pass);
            SalmonFile rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            rootDir.ListFiles();
            SalmonFile salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            DotNetFile fileToImport = new DotNetFile(importFile);
            string rbasename = fileToImport.GetBaseName();

            // import
            SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads, null);
            SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, salmonRootDir, false);

            // trigger the cache to add the filename
            string basename = salmonFile.GetBaseName();

            Assert.IsNotNull(salmonFile);
            Assert.IsTrue(salmonFile.Exists());

            string checkSumBefore = GetChecksum(salmonFile.GetRealFile());
            SalmonFile newDir1 = salmonRootDir.CreateDirectory(newDir);
            SalmonFile newFile;
            if (move)
                newFile = salmonFile.Move(newDir1, null);
            else
                newFile = salmonFile.Copy(newDir1, null);
            Assert.IsNotNull(newFile);
            string checkSumAfter = GetChecksum(newFile.GetRealFile());
            Assert.Equals(checkSumBefore, checkSumAfter);
            Assert.Equals(salmonFile.GetBaseName(), newFile.GetBaseName());
        }

        private static void FlipBit(SalmonFile salmonFile, long position)
        {
            Stream stream = salmonFile.GetRealFile().GetOutputStream();
            stream.Position = position;
            stream.Write(new byte[] { 1 }, 0, 1);
            stream.Flush();
            stream.Close();
        }

        internal static void SeekAndRead(string text, byte[] key, byte[] iv,
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
            for (int i = 0; i < 100; i++)
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
            long expectedBlock = decReader.Position / SalmonStream.AES_BLOCK_SIZE;
            Assert.AreEqual(expectedBlock, decReader.GetBlock());

            long counterBlock = BitConverter.ToLong(decReader.GetCounter(), SalmonGenerator.NONCE_LENGTH, SalmonGenerator.BLOCK_SIZE - SalmonGenerator.NONCE_LENGTH);
            long expectedCounterValue = decReader.GetBlock();
            Assert.AreEqual(expectedCounterValue, counterBlock);

            long nonce = BitConverter.ToLong(decReader.GetCounter(), 0, SalmonGenerator.NONCE_LENGTH);
            long expectedNonce = BitConverter.ToLong(decReader.GetNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            Assert.AreEqual(expectedNonce, nonce);
        }

        internal static void SeekAndWrite(string text, byte[] key, byte[] iv,
            long seek, int writeCount, string textToWrite, bool alignToChunk = false,
            bool integrity = false, int chunkSize = 0, byte[] hmacKey = null,
            bool allowRangeWrite = false
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
                    alignOffset = (int)(seek % partialWriter.GetChunkSize());
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
            if (allowRangeWrite)
                partialWriter.SetAllowRangeWrite(allowRangeWrite);
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
            Assert.AreEqual(textToWrite, decText.Substring((int)seek, writeCount));
            Assert.AreEqual(text.Substring((int)seek + writeCount), decText.Substring((int)seek + writeCount));
            TestCounter(decReader);


            encIns.Close();
            decReader.Close();
        }

        internal static void ShouldCreateFileWithoutVault(string text, byte[] key, bool applyIntegrity, bool verifyIntegrity, int chunkSize, byte[] hmacKey,
            byte[] filenameNonce, byte[] fileNonce, string outputDir, bool flipBit = false, int flipPosition = -1)
        {
            // write file
            IRealFile realDir = new DotNetFile(outputDir);
            SalmonFile dir = new SalmonFile(realDir);
            string filename = "test_" + SalmonTime.CurrentTimeMillis() + ".txt";
            SalmonFile newFile = dir.CreateFile(filename, key, filenameNonce, fileNonce);
            if (applyIntegrity)
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
                realStream.Write(new byte[] { 0 }, 0, 1);
                realStream.Flush();
                realStream.Close();
            }

            // open file for read
            IRealFile realFile = new DotNetFile(realFilePath);
            SalmonFile readFile = new SalmonFile(realFile);
            readFile.SetEncryptionKey(key);
            readFile.SetRequestedNonce(fileNonce);
            if (verifyIntegrity)
                readFile.SetVerifyIntegrity(true, hmacKey);
            SalmonStream inStream = readFile.GetInputStream();
            byte[] textBytes = new byte[testBytes.Length];
            inStream.Read(textBytes, 0, textBytes.Length);
            string textstring = Encoding.Default.GetString(textBytes);
            Assert.AreEqual(text, textstring);
        }

        internal static void TestCounterValue(string text, byte[] key, byte[] nonce, long counter)
        {
            byte[] testTextBytes = System.Text.UTF8Encoding.Default.GetBytes(text);
            MemoryStream ms = new MemoryStream(testTextBytes);
            SalmonStream stream = new SalmonStream(key, nonce, EncryptionMode.Encrypt, ms);
            stream.SetAllowRangeWrite(true);

            // we resort to reflection to test this.
            MethodInfo methodInfo = stream.GetType().GetMethod("IncreaseCounter", BindingFlags.Instance | BindingFlags.NonPublic);
            methodInfo.Invoke(stream, new object[] { counter });

            if (stream != null)
                stream.Close();
        }


        public static byte[] DefaultAESCTRTransform(byte[] plainText, byte[] testKeyBytes, byte[] testNonceBytes)
        {
            if (testNonceBytes.Length < 16)
            {
                byte[] tmp = new byte[16];
                Array.Copy(testNonceBytes, 0, tmp, 0, testNonceBytes.Length);
                testNonceBytes = tmp;
            }
            IBufferedCipher cipher = CipherUtilities.GetCipher("AES/CTR/NoPadding");
            cipher.Init(true, new ParametersWithIV(ParameterUtilities.CreateKeyParameter("AES", testKeyBytes), testNonceBytes));
            byte[] encryptedBytes = cipher.DoFinal(plainText);
            return encryptedBytes;
        }


        public static void ExportAndImportAuth(string vault, string importFilePath)
        {
            string exportAuthFilePath = vault + "/" + TEST_AUTH_FILENAME;
            string seqFile1 = vault + "/" + TEST_SEQUENCER_FILENAME1;
            string seqFile2 = vault + "/" + TEST_SEQUENCER_FILENAME2;

            // emulate 2 different devices with different sequencers
            FileSequencer sequencer1 = new FileSequencer(new DotNetFile(seqFile1), new SalmonSequenceParser());
            FileSequencer sequencer2 = new FileSequencer(new DotNetFile(seqFile2), new SalmonSequenceParser());

            // set to the first sequencer and create the vault
            SalmonDriveManager.SetSequencer(sequencer1);
            SalmonDriveManager.CreateDrive(vault, TestHelper.TEST_PASSWORD);
            SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_PASSWORD);
            // import a test file
            SalmonFile salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            IRealFile fileToImport = SalmonDriveManager.GetDrive().GetFile(importFilePath, false);
            SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0, null);
            SalmonFile salmonFileA1 = fileImporter.ImportFile(fileToImport, salmonRootDir, false);
            long nonceA1 = BitConverter.ToLong(salmonFileA1.GetRequestedNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            SalmonDriveManager.CloseDrive();

            // open with another device (different sequencer) and export auth id
            SalmonDriveManager.SetSequencer(sequencer2);
            SalmonDriveManager.OpenDrive(vault);
            SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_PASSWORD);
            string authID = SalmonDriveManager.GetAuthID();
            bool success = false;
            try
            {
                // import a test file should fail because not authorized
                salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                fileToImport = SalmonDriveManager.GetDrive().GetFile(importFilePath, false);
                fileImporter = new SalmonFileImporter(0, 0, null);
                fileImporter.ImportFile(fileToImport, salmonRootDir, false);
                success = true;
            }
            catch (Exception ignored)
            {

            }
            Assert.IsFalse(success);
            SalmonDriveManager.CloseDrive();

            //reopen with first device sequencer and export the auth file with the auth id from the second device
            SalmonDriveManager.SetSequencer(sequencer1);
            SalmonDriveManager.OpenDrive(vault);
            SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_PASSWORD);
            SalmonDriveManager.ExportAuthFile(authID, vault, TEST_AUTH_FILENAME);
            IRealFile configFile = SalmonDriveManager.GetDrive().GetFile(exportAuthFilePath, false);
            SalmonFile salmonCfgFile = new SalmonFile(configFile, SalmonDriveManager.GetDrive());
            long nonceCfg = BitConverter.ToLong(salmonCfgFile.GetFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            // import another test file
            salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            fileToImport = SalmonDriveManager.GetDrive().GetFile(importFilePath, false);
            fileImporter = new SalmonFileImporter(0, 0, null);
            SalmonFile salmonFileA2 = fileImporter.ImportFile(fileToImport, salmonRootDir, false);
            long nonceA2 = BitConverter.ToLong(salmonFileA2.GetFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            SalmonDriveManager.CloseDrive();

            //reopen with second device(sequencer) and import auth file
            SalmonDriveManager.SetSequencer(sequencer2);
            SalmonDriveManager.OpenDrive(vault);
            SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_PASSWORD);
            SalmonDriveManager.ImportAuthFile(exportAuthFilePath);
            // now import a 3rd file
            salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            fileToImport = SalmonDriveManager.GetDrive().GetFile(importFilePath, false);
            SalmonFile salmonFileB1 = fileImporter.ImportFile(fileToImport, salmonRootDir, false);
            long nonceB1 = BitConverter.ToLong(salmonFileB1.GetFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            SalmonFile salmonFileB2 = fileImporter.ImportFile(fileToImport, salmonRootDir, false);
            long nonceB2 = BitConverter.ToLong(salmonFileB2.GetFileNonce(), 0, SalmonGenerator.NONCE_LENGTH);
            SalmonDriveManager.CloseDrive();

            Assert.AreEqual(nonceA1, nonceCfg - 1);
            Assert.AreEqual(nonceCfg, nonceA2 - 2);
            Assert.AreNotEqual(nonceA2, nonceB1);
            Assert.AreEqual(nonceB1, nonceB2 - 2);
        }

        class MockUserSequencer : FileSequencer
        {
            byte[] testMaxNonce;
            long offset;
            internal MockUserSequencer(IRealFile file, byte[] testMaxNonce, long offset) : base(file, new SalmonSequenceParser())
            {
                this.testMaxNonce = testMaxNonce;
                this.offset = offset;
            }
            public override void InitSequence(string driveID, string authID, byte[] startNonce, byte[] maxNonce)
            {
                long nMaxNonce = BitConverter.ToLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
                startNonce = BitConverter.ToBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
                maxNonce = BitConverter.ToBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
                base.InitSequence(driveID, authID, startNonce, maxNonce);
            }
        };

        public static void TestMaxFiles(string vaultDir, string seqFile, string importFile,
                                        byte[] testMaxNonce, long offset, bool shouldImport)
        {
            bool importSuccess;
            try
            {
                importSuccess = true;

                MockUserSequencer sequencer = new MockUserSequencer(new DotNetFile(seqFile), testMaxNonce, offset);

                SalmonDriveManager.SetSequencer(sequencer);
                try
                {
                    SalmonDrive drive = SalmonDriveManager.OpenDrive(vaultDir);
                    drive.Authenticate(TestHelper.TEST_PASSWORD);
                }
                catch (Exception ex)
                {
                    SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
                }
                SalmonFile rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                rootDir.ListFiles();
                SalmonFile salmonRootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                DotNetFile fileToImport = new DotNetFile(importFile);
                SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0, null);
                SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, salmonRootDir, false);
                if (salmonFile != null)
                    importSuccess = true;
                else
                    importSuccess = false;
            }
            catch (Exception ex)
            {
                importSuccess = false;
                Console.Error.WriteLine(ex);
            }
            Assert.AreEqual(shouldImport, importSuccess);
        }

        public static void TestExamples()
        {
            string text = "This is a plaintext that will be used for testing";
            string testFile = "D:/tmp/file.txt";
            IRealFile tFile = new DotNetFile(testFile);
            if (tFile.Exists())
                tFile.Delete();
            byte[] bytes = Encoding.UTF8.GetBytes(text);
            byte[] key = SalmonGenerator.GetSecureRandomBytes(32); // 256 bit key
            byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64 bit nonce

            // Example 1: encrypt byte array
            byte[] encBytes = SalmonEncryptor.Encrypt(bytes, key, nonce, false);
            // decrypt byte array
            byte[] decBytes = SalmonEncryptor.Decrypt(encBytes, key, nonce, false);
            CollectionAssert.AreEqual(bytes, decBytes);

            // Example 2: encrypt string and save the nonce in the header
            nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
            string encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);
            // decrypt string
            string decText = SalmonTextEncryptor.DecryptString(encText, key, null, true);
            Assert.AreEqual(text, decText);

            // Example 3: encrypt data to an output stream
            Stream encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
            nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
            // pass the output stream to the SalmonStream
            SalmonStream encrypter = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
            // encrypt and write with a single call, you can also Seek() and Write()
            encrypter.Write(bytes, 0, bytes.Length);
            // encrypted data are now written to the encOutStream.
            encOutStream.Position = 0;
            byte[] encData = (encOutStream as MemoryStream).ToArray();
            encrypter.Flush();
            encrypter.Close();
            encOutStream.Close();
            //decrypt a stream with encoded data
            Stream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
            SalmonStream decrypter = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
            byte[] decBuffer = new byte[1024];
            // decrypt and read data with a single call, you can also Seek() before Read()
            int bytesRead = decrypter.Read(decBuffer, 0, decBuffer.Length);
            // encrypted data are now in the decBuffer
            string decString = Encoding.UTF8.GetString(decBuffer, 0, bytesRead);
            Console.WriteLine(decString);
            decrypter.Close();
            encInputStream.Close();
            Assert.AreEqual(text, decString);

            // Example 4: encrypt to a file
            SalmonFile encFile = new SalmonFile(new DotNetFile(testFile));
            nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
            encFile.SetEncryptionKey(key);
            encFile.SetRequestedNonce(nonce);
            Stream stream = encFile.GetOutputStream();
            // encrypt data and write with a single call
            stream.Write(bytes, 0, bytes.Length);
            stream.Flush();
            stream.Close();
            // decrypt an encrypted file
            SalmonFile encFile2 = new SalmonFile(new DotNetFile(testFile));
            encFile2.SetEncryptionKey(key);
            Stream stream2 = encFile2.GetInputStream();
            byte[] decBuff = new byte[1024];
            // decrypt and read data with a single call, you can also Seek() to any position before Read()
            int encBytesRead = stream2.Read(decBuff, 0, decBuff.Length);
            string decString2 = Encoding.UTF8.GetString(decBuff, 0, encBytesRead);
            Console.WriteLine(decString2);
            stream2.Close();
            Assert.AreEqual(text, decString2);

        }

        public static void EncryptAndDecryptStream(byte[] data, byte[] key, byte[] nonce)
        {
            Stream encOutStream = new MemoryStream();
            SalmonStream encrypter = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
            Stream inputStream = new MemoryStream(data);
            inputStream.CopyTo(encrypter);
            encOutStream.Position = 0;
            byte[] encData = (encOutStream as MemoryStream).ToArray();
            encrypter.Flush();
            encrypter.Close();
            encOutStream.Close();
            inputStream.Close();

            Stream encInputStream = new MemoryStream(encData);
            SalmonStream decrypter = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
            Stream outStream = new MemoryStream();
            decrypter.CopyTo(outStream);
            outStream.Position = 0;
            byte[] decData = (outStream as MemoryStream).ToArray();
            decrypter.Close();
            encInputStream.Close();
            outStream.Close();

            CollectionAssert.AreEqual(data, decData);
        }

        internal static byte[] GetRealFileContents(string filePath)
        {
            IRealFile file = new DotNetFile(filePath);
            Stream ins = file.GetInputStream();
            MemoryStream outs = new MemoryStream();
            ins.CopyTo(outs);
            outs.Position = 0;
            outs.Flush();
            outs.Close();
            return outs.ToArray();
        }

        public static byte[] GetRandArray(int size)
        {
            Random random = new Random();
            byte[] data = new byte[size];
            random.NextBytes(data);
            return data;
        }

        internal static void EncryptAndDecryptByteArray(int size, int threads = 1)
        {
            byte[] data = TestHelper.GetRandArray(size);
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = SalmonEncryptor.Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads: threads);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = SalmonEncryptor.Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads: threads);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }

        internal static void EncryptAndDecryptByteArrayDef(int size)
        {
            byte[] data = TestHelper.GetRandArray(size);
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = TestHelper.DefaultAESCTRTransform(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = TestHelper.DefaultAESCTRTransform(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }

        internal static void CopyMemory(int size)
        {
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] data = TestHelper.GetRandArray(size);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] data1 = new byte[data.Length];
            Array.Copy(data, data1, data.Length);
            long t3 = SalmonTime.CurrentTimeMillis();
            Console.WriteLine("gen time: " + (t2 - t1));
            Console.WriteLine("copy time: " + (t3 - t2));

            byte[] mem = new byte[16];
            MemoryStream ms = new MemoryStream(mem);
            ms.Write(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 3, 2);
            byte[] output = ms.ToArray();
            Console.WriteLine("write: " + string.Join(", ", output));
            byte[] buff = new byte[16];
            ms.Position = 0;
            ms.Read(buff, 1, 4);
            Console.WriteLine("read: " + string.Join(", ", buff));
        }
    }
}
