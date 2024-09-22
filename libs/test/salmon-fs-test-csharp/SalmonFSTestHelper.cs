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
using Mku.Sequence;
using Mku.Utils;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.IO;
using System.Security.Cryptography;
using System;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;
using Mku.Salmon.Streams;
using Mku.Salmon.Text;
using System.Diagnostics;
using Mku.Salmon.Utils;
using Mku.Salmon.Sequence;
using Mku.Salmon.Drive;
using System.Collections.Generic;
using System.Net;

namespace Mku.Salmon.Test;

public class SalmonFSTestHelper
{
    internal static Type DriveClassType { get; set; } // drive class type
    internal static string TEST_ROOT_DIR = "d:\\tmp\\";
    internal static string TEST_OUTPUT_DIR = SalmonFSTestHelper.TEST_ROOT_DIR + "output\\";
    internal static string TEST_VAULT_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "enc";
    internal static string TEST_VAULT2_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "enc2";
    internal static string TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR + "export\\";
    internal static string TEST_DATA_DIR_FOLDER = SalmonFSTestHelper.TEST_ROOT_DIR + "testdata\\";
    internal static string TEST_IMPORT_TINY_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "tiny_test.txt";
    internal static string TEST_IMPORT_SMALL_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "small_test.zip";
    internal static string TEST_IMPORT_MEDIUM_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "medium_test.zip";
    internal static string TEST_IMPORT_LARGE_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "large_test.mp4";
    internal static string TEST_IMPORT_HUGE_FILE = SalmonFSTestHelper.TEST_DATA_DIR_FOLDER + "huge.zip";
    internal static string TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

    internal static string TEST_SEQUENCER_DIR = SalmonFSTestHelper.TEST_OUTPUT_DIR;
    internal static string TEST_SEQUENCER_FILENAME = "fileseq.xml";

    internal static string TEST_EXPORT_FILENAME = "export.slma";


    public static string VAULT_HOST = "http://localhost:8080";
    public static string VAULT_URL = VAULT_HOST + ""; // same
    public static string VAULT_PASSWORD = "test";


    internal static int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    internal static int ENC_IMPORT_THREADS = 1;
    internal static int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    internal static int ENC_EXPORT_THREADS = 1;

    private static readonly int REAL_FILE_BUFFER_SIZE = 512 * 1024;

    internal static int TEST_FILE_INPUT_STREAM_THREADS = 1;
    internal static bool TEST_USE_FILE_INPUT_STREAM = false;

    static bool ENABLE_FILE_PROGRESS = false;

    internal static string TEST_SEQUENCER_FILE1 = "seq1.xml";
    internal static string TEST_SEQUENCER_FILE2 = "seq2.xml";

    public static Dictionary<string, string> users;
    private static DotNetWSFile.Credentials credentials1 = new DotNetWSFile.Credentials("user1", "pass1");

    internal static SalmonFileImporter fileImporter;
    internal static SalmonFileExporter fileExporter;

    internal static void Initialize()
    {
        // TODO: ToSync global importer/exporter
        SalmonFSTestHelper.fileImporter = new SalmonFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new SalmonFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    internal static void Close()
    {
        SalmonFSTestHelper.fileImporter.Close();
        SalmonFSTestHelper.fileExporter.Close();
    }

    public static IRealFile GenerateFolder(string dirPath, Type driveClassType = null)
    {
        driveClassType ??= SalmonFSTestHelper.DriveClassType;
        long time = Time.Time.CurrentTimeMillis();
        if (driveClassType == typeof(DotNetWSDrive))
        {
            IRealFile dir = new DotNetWSFile("/remote_" + time, SalmonFSTestHelper.VAULT_URL,
                    SalmonFSTestHelper.credentials1);
            if (!dir.Mkdir())
                throw new Exception("Could not generate folder");
            return dir;
        }
        else
        {
            DirectoryInfo dir = Directory.CreateDirectory(dirPath + "_" + time);
            if (dir == null)
                return null;
            return new DotNetFile(dir.FullName);
        }
    }

    public static string GetChecksum(IRealFile realFile)
    {
        Stream stream = realFile.GetInputStream();
        return GetChecksum(stream, REAL_FILE_BUFFER_SIZE);
    }


    public static string GetChecksum(Stream inputStream, int bufferSize)
    {
        try
        {
            MD5 md5 = MD5.Create();
            byte[] hash = md5.ComputeHash(new BufferedStream(inputStream, bufferSize));
            string hashstring = System.Convert.ToBase64String(hash);
            return hashstring;
        }
        finally
        {
            if (inputStream != null)
                inputStream.Close();
        }
    }

    public static void ImportAndExport(IRealFile vaultDir, string pass, string importFile,
                                       int importBufferSize, int importThreads, int exportBufferSize, int exportThreads, bool integrity,
                                       bool bitflip, long flipPosition, bool shouldBeEqual,
                                       bool ApplyFileIntegrity, bool VerifyFileIntegrity)
    {
        SalmonFileSequencer sequencer = CreateSalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        SalmonFile rootDir = drive.Root;
        rootDir.ListFiles();

        DotNetFile fileToImport = new DotNetFile(importFile);
        string hashPreImport = SalmonFSTestHelper.GetChecksum(fileToImport);

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, ApplyFileIntegrity, null);
        Assert.IsNotNull(salmonFile);
        Assert.IsTrue(salmonFile.Exists);

        int? chunkSize = salmonFile.FileChunkSize;
        if (chunkSize != null && chunkSize > 0 && !VerifyFileIntegrity)
            salmonFile.SetVerifyIntegrity(false, null);
        SalmonStream sstream = salmonFile.GetInputStream();
        String hashPostImport = SalmonFSTestHelper.GetChecksum(sstream, sstream.BufferSize);
        if (shouldBeEqual)
        {
            Assert.AreEqual(hashPreImport, hashPostImport);
        }

        // get fresh copy of file
        SalmonFile[] salmonFiles = rootDir.ListFiles();
        long realFileSize = fileToImport.Length;
        foreach (SalmonFile file in salmonFiles)
        {
            if (file.BaseName.Equals(fileToImport.BaseName))
            {
                if (shouldBeEqual)
                {

                    Assert.IsTrue(file.Exists);
                    long fileSize = file.Size;

                    Assert.AreEqual(realFileSize, fileSize);
                }
            }
        }

        // export
        SalmonFileExporter fileExporter = new SalmonFileExporter(exportBufferSize, exportThreads);
        if (bitflip)
            FlipBit(salmonFile, flipPosition);
        int? chunkSize2 = salmonFile.FileChunkSize;
        if (chunkSize2 != null && chunkSize2 > 0 && VerifyFileIntegrity)
            salmonFile.SetVerifyIntegrity(true, null);
        IRealFile exportFile = fileExporter.ExportFile(salmonFile, drive.ExportDir, null, false, VerifyFileIntegrity, null);

        string hashPostExport = SalmonFSTestHelper.GetChecksum(exportFile);
        if (shouldBeEqual)
        {

            Assert.AreEqual(hashPreImport, hashPostExport);
        }
    }

    public static SalmonDrive CreateDrive(IRealFile vaultDir, Type driveClassType, string pass, SalmonFileSequencer sequencer)
    {
        if (driveClassType == typeof(DotNetWSDrive))
            return DotNetWSDrive.Create(vaultDir, pass, sequencer, credentials1.ServiceUser,
                    credentials1.ServicePassword);
        else
            return SalmonDrive.CreateDrive(vaultDir, driveClassType, pass, sequencer);
    }

    public static void ImportAndSearch(IRealFile vaultDir, string pass, string importFile,
                                           int importBufferSize, int importThreads)
    {
        SalmonFileSequencer sequencer = CreateSalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        SalmonFile rootDir = drive.Root;
        DotNetFile fileToImport = new DotNetFile(importFile);
        string rbasename = fileToImport.BaseName;

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);

        // trigger the cache to add the filename
        string basename = salmonFile.BaseName;


        Assert.IsNotNull(salmonFile);

        Assert.IsTrue(salmonFile.Exists);

        FileSearcher searcher = new FileSearcher();
        IVirtualFile[] files = searcher.Search(rootDir, basename, true, null, null);


        Assert.IsTrue(files.Length > 0);

        Assert.AreEqual(files[0].BaseName, basename);

    }

    public static void ImportAndCopy(IRealFile vaultDir, string pass, string importFile,
                                     int importBufferSize, int importThreads, string newDir, bool move)
    {
        SalmonFileSequencer sequencer = CreateSalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        SalmonFile rootDir = drive.Root;
        DotNetFile fileToImport = new DotNetFile(importFile);
        string rbasename = fileToImport.BaseName;

        // import
        SalmonFileImporter fileImporter = new SalmonFileImporter(importBufferSize, importThreads);
        SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);

        // trigger the cache to add the filename
        string basename = salmonFile.BaseName;

        Assert.IsNotNull(salmonFile);

        Assert.IsTrue(salmonFile.Exists);

        string checkSumBefore = GetChecksum(salmonFile.RealFile);
        SalmonFile newDir1 = rootDir.CreateDirectory(newDir);
        SalmonFile newFile;
        if (move)
            newFile = salmonFile.Move(newDir1, null);
        else
            newFile = salmonFile.Copy(newDir1, null);
        Assert.IsNotNull(newFile);
        string checkSumAfter = GetChecksum(newFile.RealFile);
        Assert.AreEqual(checkSumBefore, checkSumAfter);

        if (!move)
        {
            IVirtualFile file = rootDir.GetChild(fileToImport.BaseName);
            string checkSumOrigAfter = GetChecksum(file.RealFile);
            Assert.AreEqual(checkSumBefore, checkSumOrigAfter);
        }

        Assert.AreEqual(salmonFile.BaseName, newFile.BaseName);
    }

    private static void FlipBit(SalmonFile salmonFile, long position)
    {
        Stream stream = salmonFile.RealFile.GetOutputStream();
        stream.Position = position;
        stream.Write(new byte[] { 1 }, 0, 1);
        stream.Flush();
        stream.Close();
    }


    public static SalmonFile ShouldCreateFileWithoutVault(byte[] testBytes, byte[] key, bool applyIntegrity, bool verifyIntegrity, int chunkSize, byte[] hashKey,
                                                    byte[] filenameNonce, byte[] fileNonce, string outputDir, bool flipBit, int flipPosition, bool checkData)
    {
        // write file
        IRealFile realDir = new DotNetFile(outputDir);
        SalmonFile dir = new SalmonFile(realDir, null);
        string filename = "test_" + Mku.Time.Time.CurrentTimeMillis() + ".txt";
        SalmonFile newFile = dir.CreateFile(filename, key, filenameNonce, fileNonce);
        if (applyIntegrity)
            newFile.SetApplyIntegrity(true, hashKey, chunkSize);
        Stream stream = newFile.GetOutputStream();

        stream.Write(testBytes, 0, testBytes.Length);
        stream.Flush();
        stream.Close();
        string realFilePath = newFile.RealFile.Path;

        // tamper
        if (flipBit)
        {
            IRealFile realTmpFile = newFile.RealFile;
            Stream realStream = realTmpFile.GetOutputStream();
            realStream.Position = flipPosition;
            realStream.Write(new byte[] { 0 }, 0, 1);
            realStream.Flush();
            realStream.Close();
        }

        // open file for read
        IRealFile realFile = new DotNetFile(realFilePath);
        SalmonFile readFile = new SalmonFile(realFile, null);
        readFile.EncryptionKey = key;
        readFile.RequestedNonce = fileNonce;
        if (verifyIntegrity)
            readFile.SetVerifyIntegrity(true, hashKey);
        SalmonStream inStream = readFile.GetInputStream();
        byte[] textBytes = new byte[testBytes.Length];
        inStream.Read(textBytes, 0, textBytes.Length);
        inStream.Close();
        if (checkData)
            CollectionAssert.AreEqual(testBytes, textBytes);
        return readFile;
    }

    public static void ExportAndImportAuth(IRealFile vault, string importFilePath)
    {
        IRealFile seqFile1 = vault.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        IRealFile seqFile2 = vault.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE2);

        // emulate 2 different devices with different sequencers
        SalmonFileSequencer sequencer1 = CreateSalmonFileSequencer(seqFile1, new SalmonSequenceSerializer());
        SalmonFileSequencer sequencer2 = CreateSalmonFileSequencer(seqFile2, new SalmonSequenceSerializer());

        // set to the first sequencer and create the vault
        SalmonDrive drive = SalmonFSTestHelper.CreateDrive(vault, DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        SalmonFile rootDir = drive.Root;
        IRealFile fileToImport = new DotNetFile(importFilePath);
        SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0);
        SalmonFile salmonFileA1 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceA1 = BitConverter.ToLong(salmonFileA1.RequestedNonce, 0, SalmonGenerator.NONCE_LENGTH);
        drive.Close();

        // open with another device (different sequencer) and export auth id
        drive = SalmonFSTestHelper.OpenDrive(vault, DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        string authId = drive.GetAuthId();
        bool success = false;
        try
        {
            // import a test file should fail because not authorized
            rootDir = drive.Root;
            fileToImport = new DotNetFile(importFilePath);
            fileImporter = new SalmonFileImporter(0, 0);
            fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
            success = true;
        }
        catch (Exception)
        {

        }

        Assert.IsFalse(success);
        drive.Close();

        //reopen with first device sequencer and export the auth file with the auth id from the second device
        drive = SalmonFSTestHelper.OpenDrive(vault, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        IRealFile exportFile = vault.GetChild(SalmonFSTestHelper.TEST_EXPORT_FILENAME);
        SalmonAuthConfig.ExportAuthFile(drive, authId, exportFile);
        IRealFile exportAuthFile = vault.GetChild(SalmonFSTestHelper.TEST_EXPORT_FILENAME);
        SalmonFile salmonCfgFile = new SalmonFile(exportAuthFile, drive);
        long nonceCfg = BitConverter.ToLong(salmonCfgFile.FileNonce, 0, SalmonGenerator.NONCE_LENGTH);
        // import another test file
        rootDir = drive.Root;
        fileToImport = new DotNetFile(importFilePath);
        fileImporter = new SalmonFileImporter(0, 0);
        SalmonFile salmonFileA2 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceA2 = BitConverter.ToLong(salmonFileA2.FileNonce, 0, SalmonGenerator.NONCE_LENGTH);
        drive.Close();

        //reopen with second device(sequencer) and import auth file
        drive = SalmonFSTestHelper.OpenDrive(vault, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        SalmonAuthConfig.ImportAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = drive.Root;
        fileToImport = new DotNetFile(importFilePath);
        SalmonFile salmonFileB1 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceB1 = BitConverter.ToLong(salmonFileB1.FileNonce, 0, SalmonGenerator.NONCE_LENGTH);
        SalmonFile salmonFileB2 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceB2 = BitConverter.ToLong(salmonFileB2.FileNonce, 0, SalmonGenerator.NONCE_LENGTH);
        drive.Close();


        Assert.AreEqual(nonceA1, nonceCfg - 1);

        Assert.AreEqual(nonceCfg, nonceA2 - 2);

        Assert.AreNotEqual(nonceA2, nonceB1);

        Assert.AreEqual(nonceB1, nonceB2 - 2);
    }


    public static void TestMaxFiles(IRealFile vaultDir, IRealFile seqFile, string importFile,
                                    byte[] testMaxNonce, long offset, bool shouldImport)
    {
        bool importSuccess;
        IRealFile seqDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_SEQUENCER_DIR + "/seq", typeof(DotNetDrive));
        seqFile = seqDir.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        try
        {
            TestSalmonFileSequencer sequencer = new TestSalmonFileSequencer(seqFile, new SalmonSequenceSerializer(),
                testMaxNonce, offset);
            SalmonDrive drive;
            try
            {
                drive = SalmonFSTestHelper.OpenDrive(vaultDir, DriveClassType,
                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
                drive.Close();
            }
            catch (Exception ex)
            {
                drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType,
                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            }
            SalmonFile rootDir = drive.Root;
            rootDir.ListFiles();
            DotNetFile fileToImport = new DotNetFile(importFile);
            SalmonFileImporter fileImporter = new SalmonFileImporter(0, 0);
            SalmonFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        }
        catch (Exception ex)
        {
            importSuccess = false;
            Console.Error.WriteLine(ex);
        }

        Assert.AreEqual(shouldImport, importSuccess);
    }

    public class TestSalmonFileSequencer : SalmonFileSequencer
    {
        private byte[] testMaxNonce;
        private long offset;

        public TestSalmonFileSequencer(IRealFile sequenceFile, INonceSequenceSerializer serializer, byte[] testMaxNonce, long offset) : base(sequenceFile, serializer)
        {
            this.testMaxNonce = testMaxNonce;
            this.offset = offset;
        }

        override
        public void InitSequence(string driveId, string authId, byte[] startNonce, byte[] maxNonce)
        {
            long nMaxNonce = BitConverter.ToLong(testMaxNonce, 0, SalmonGenerator.NONCE_LENGTH);
            startNonce = BitConverter.ToBytes(nMaxNonce + offset, SalmonGenerator.NONCE_LENGTH);
            maxNonce = BitConverter.ToBytes(nMaxNonce, SalmonGenerator.NONCE_LENGTH);
            base.InitSequence(driveId, authId, startNonce, maxNonce);
        }
    }
    public static SalmonDrive OpenDrive(IRealFile vaultDir, Type driveClassType, String testPassword, SalmonFileSequencer sequencer)
    {
        if (driveClassType == typeof(DotNetWSDrive))
        {
            // use the remote service instead
            return DotNetWSDrive.Open(vaultDir, testPassword, sequencer,
                    credentials1.ServiceUser, credentials1.ServicePassword);
        }
        else
            return SalmonFSTestHelper.OpenDrive(vaultDir, driveClassType, testPassword, sequencer);
    }

    public static void TestExamples()
    {
        string text = "This is a plaintext that will be used for testing";
        string testFile = "D:/tmp/file.txt";
        IRealFile tFile = new DotNetFile(testFile);
        if (tFile.Exists)
            tFile.Delete();
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);
        byte[] key = SalmonGenerator.GetSecureRandomBytes(32); // 256-bit key
        byte[] nonce = SalmonGenerator.GetSecureRandomBytes(8); // 64-bit nonce

        // Example 1: encrypt byte array
        byte[] encBytes = new SalmonEncryptor().Encrypt(bytes, key, nonce, false);
        // decrypt byte array
        byte[] decBytes = new SalmonDecryptor().Decrypt(encBytes, key, nonce, false);

        CollectionAssert.AreEqual(bytes, decBytes);

        // Example 2: encrypt string and save the nonce in the header
        nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
        string encText = SalmonTextEncryptor.EncryptString(text, key, nonce, true);
        // decrypt string
        string decText = SalmonTextDecryptor.DecryptString(encText, key, null, true);

        Assert.AreEqual(text, decText);

        // Example 3: encrypt data to an output stream
        MemoryStream encOutStream = new MemoryStream(); // or any other writeable Stream like to a file
        nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
                                                         // pass the output stream to the SalmonStream
        SalmonStream encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream,
                null, false, null, null);
        // encrypt and write with a single call, you can also Seek() and Write()
        encryptor.Write(bytes, 0, bytes.Length);
        // encrypted data are now written to the encOutStream.
        encOutStream.Position = 0;
        byte[] encData = encOutStream.ToArray();
        encryptor.Flush();
        encryptor.Close();
        encOutStream.Close();
        //decrypt a stream with encoded data
        Stream encInputStream = new MemoryStream(encData); // or any other readable Stream like from a file
        SalmonStream decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream,
                null, false, null, null);
        byte[] decBuffer = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() before Read()
        int bytesRead = decryptor.Read(decBuffer, 0, decBuffer.Length);
        // encrypted data are now in the decBuffer
        string decString = UTF8Encoding.UTF8.GetString(decBuffer, 0, bytesRead);
        Console.WriteLine(decString);
        decryptor.Close();
        encInputStream.Close();

        Assert.AreEqual(text, decString);

        // Example 4: encrypt to a file, the SalmonFile has a virtual file system API
        // with copy, move, rename, delete operations
        SalmonFile encFile = new SalmonFile(new DotNetFile(testFile), null);
        nonce = SalmonGenerator.GetSecureRandomBytes(8); // always get a fresh nonce!
        encFile.EncryptionKey = key;
        encFile.RequestedNonce = nonce;
        Stream stream = encFile.GetOutputStream();
        // encrypt data and write with a single call
        stream.Write(bytes, 0, bytes.Length);
        stream.Flush();
        stream.Close();
        // decrypt an encrypted file
        SalmonFile encFile2 = new SalmonFile(new DotNetFile(testFile), null);
        encFile2.EncryptionKey = key;
        Stream stream2 = encFile2.GetInputStream();
        byte[] decBuff = new byte[1024];
        // decrypt and read data with a single call, you can also Seek() to any position before Read()
        int encBytesRead = stream2.Read(decBuff, 0, decBuff.Length);
        string decString2 = UTF8Encoding.UTF8.GetString(decBuff, 0, encBytesRead);
        Console.WriteLine(decString2);
        stream2.Close();

        Assert.AreEqual(text, decString2);
    }

    public static void EncryptAndDecryptStream(byte[] data, byte[] key, byte[] nonce)
    {
        MemoryStream encOutStream = new MemoryStream();
        SalmonStream encryptor = new SalmonStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        Stream inputStream = new MemoryStream(data);
        inputStream.CopyTo(encryptor);
        encOutStream.Position = 0;
        byte[] encData = encOutStream.ToArray();
        encryptor.Flush();
        encryptor.Close();
        encOutStream.Close();
        inputStream.Close();

        Stream encInputStream = new MemoryStream(encData);
        SalmonStream decryptor = new SalmonStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        MemoryStream outStream = new MemoryStream();
        decryptor.CopyTo(outStream);
        outStream.Position = 0;
        byte[] decData = outStream.ToArray();
        decryptor.Close();
        encInputStream.Close();
        outStream.Close();

        CollectionAssert.AreEqual(data, decData);
    }

    public static byte[] GetRealFileContents(string filePath)
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

    public static void SeekAndReadFileInputStream(byte[] data, SalmonFileInputStream fileInputStream,
                                                  int start, int length, int readOffset, int shouldReadLength)
    {
        byte[] buffer = new byte[length + readOffset];
        fileInputStream.Position = start;
        int bytesRead = fileInputStream.Read(buffer, readOffset, length);
        Assert.AreEqual(shouldReadLength, bytesRead);
        byte[] tdata = new byte[buffer.Length];
        Array.Copy(data, start, tdata, readOffset, shouldReadLength);
        CollectionAssert.AreEqual(tdata, buffer);
    }

    public static void ShouldTestFileSequencer()
    {
        IRealFile file = new DotNetFile(TEST_SEQUENCER_DIR + "\\" + TEST_SEQUENCER_FILENAME);
        if (file.Exists)
            file.Delete();
        SalmonFileSequencer sequencer = CreateSalmonFileSequencer(file,
            new SalmonSequenceSerializer());

        sequencer.CreateSequence("AAAA", "AAAA");
        sequencer.InitSequence("AAAA", "AAAA",
            Mku.Convert.BitConverter.ToBytes(1, 8),
            Mku.Convert.BitConverter.ToBytes(4, 8));
        byte[] nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(1, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(2, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        nonce = sequencer.NextNonce("AAAA");
        Assert.AreEqual(3, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));

        bool caught = false;
        try
        {
            nonce = sequencer.NextNonce("AAAA");
            Assert.AreNotEqual(5, Mku.Convert.BitConverter.ToLong(nonce, 0, 8));
        }
        catch (SalmonRangeExceededException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);
    }

    public static int GetChildrenCountRecursively(IRealFile realFile)
    {
        int count = 1;
        if (realFile.IsDirectory)
        {
            foreach (IRealFile child in realFile.ListFiles())
            {
                count += GetChildrenCountRecursively(child);
            }
        }
        return count;
    }

    public static SalmonFileSequencer CreateSalmonFileSequencer(IRealFile javaFile, INonceSequenceSerializer sequenceSerializer)
    {
        if (DriveClassType == typeof(DotNetWSDrive))
        {
            // use a local sequencer for testing since the current path is remote
            IRealFile seqDir = GenerateFolder(TEST_SEQUENCER_DIR + "/seq", typeof(DotNetDrive));
            IRealFile seqFile = seqDir.GetChild(TEST_SEQUENCER_FILENAME);
            return new SalmonFileSequencer(seqFile, new SalmonSequenceSerializer());
        }
        else
            return new SalmonFileSequencer(javaFile, sequenceSerializer);
    }

}