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
using System.IO;
using System.Security.Cryptography;
using System;
using System.Text;
using BitConverter = Mku.Convert.BitConverter;
using Mku.Salmon.Streams;
using System.Diagnostics;
using Mku.Salmon.Sequence;
using System.Collections.Generic;
using Mku.FS.File;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.Utils;
using Mku.SalmonFS.Sequence;
using Mku.SalmonFS.File;
using Mku.FS.Drive.Utils;
using Mku.SalmonFS.Auth;
using Mku.SalmonFS.Streams;

namespace Mku.Salmon.Test;


public enum TestMode
{
    Local,
    Http,
    WebService
}
public class SalmonFSTestHelper
{
    internal static TestMode currTestMode;
    // dirs
    internal static Type DriveClassType { get; set; } // drive class type
    internal static IFile TEST_ROOT_DIR; // root dir for testing
    internal static string TEST_INPUT_DIRNAME = "input";
    internal static string TEST_OUTPUT_DIRNAME = "output";
    internal static string TEST_VAULT_DIRNAME = "vault";
    internal static string TEST_OPER_DIRNAME = "files";
    internal static string TEST_EXPORT_AUTH_DIRNAME = "auth";
    internal static string TEST_IMPORT_TINY_FILENAME = "tiny_test.txt";
    internal static string TEST_IMPORT_SMALL_FILENAME = "small_test.dat";
    internal static string TEST_IMPORT_MEDIUM_FILENAME = "medium_test.dat";
    internal static string TEST_IMPORT_LARGE_FILENAME = "large_test.dat";
    internal static string TEST_IMPORT_HUGE_FILENAME = "huge_test.dat";
    internal static string TINY_FILE_CONTENTS = "This is a new file created that will be used for testing encryption and decryption.";
    internal static string TEST_SEQ_DIRNAME = "seq";
    internal static string TEST_SEQ_FILENAME = "fileseq.xml";
    internal static string TEST_EXPORT_AUTH_FILENAME = "export.slma";

    // Web service
    internal static string WS_SERVER_URL = "http://localhost:8080";
    // static WS_SERVER_URL = "https://localhost:8443"; // for testing from the Web browser
    internal static string WS_TEST_DIRNAME = "ws";
    internal static WSFile.Credentials credentials = new WSFile.Credentials("user", "password");

    // HTTP server (Read-only)
    internal static string HTTP_SERVER_URL = "http://localhost:8000";
    internal static string HTTP_SERVER_VIRTUAL_URL = SalmonFSTestHelper.HTTP_SERVER_URL + "/test";
    internal static string HTTP_TEST_DIRNAME = "httpserv";
    internal static string HTTP_VAULT_DIRNAME = "vault";
    internal static string HTTP_VAULT_DIR_URL = SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL
            + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME + "/" + SalmonFSTestHelper.HTTP_VAULT_DIRNAME;
    internal static string HTTP_VAULT_FILES_DIR_URL = SalmonFSTestHelper.HTTP_VAULT_DIR_URL + "/fs";

    // performance
    internal static int ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
    internal static int ENC_IMPORT_THREADS = 1;
    internal static int ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
    internal static int ENC_EXPORT_THREADS = 1;
    internal static int TEST_FILE_INPUT_STREAM_THREADS = 1;
    internal static bool TEST_USE_FILE_INPUT_STREAM = false;

    // progress
    internal static bool ENABLE_FILE_PROGRESS = true;

    // test dirs and files
    internal static IFile TEST_INPUT_DIR;
    internal static IFile TEST_OUTPUT_DIR;
    internal static IFile TEST_IMPORT_TINY_FILE;
    internal static IFile TEST_IMPORT_SMALL_FILE;
    internal static IFile TEST_IMPORT_MEDIUM_FILE;
    internal static IFile TEST_IMPORT_LARGE_FILE;
    internal static IFile TEST_IMPORT_HUGE_FILE;
    internal static IFile TEST_IMPORT_FILE;
    internal static IFile WS_TEST_DIR;
    internal static IFile HTTP_TEST_DIR;
    internal static IFile HTTP_VAULT_DIR;
    internal static IFile TEST_HTTP_TINY_FILE;
    internal static IFile TEST_HTTP_SMALL_FILE;
    internal static IFile TEST_HTTP_MEDIUM_FILE;
    internal static IFile TEST_HTTP_LARGE_FILE;
    internal static IFile TEST_HTTP_HUGE_FILE;
    internal static IFile TEST_HTTP_FILE;
    internal static IFile TEST_SEQ_DIR;
    internal static IFile TEST_EXPORT_AUTH_DIR;
    internal static AesFileImporter fileImporter;
    internal static AesFileExporter fileExporter;
    internal static SequenceSerializer sequenceSerializer = new SequenceSerializer();
    internal static readonly Random random = new Random((int)Time.Time.CurrentTimeMillis());

    public static void SetTestParams(string testDir, TestMode testMode)
    {
        currTestMode = testMode;

        if (testMode == TestMode.Local)
        {
            SalmonFSTestHelper.DriveClassType = typeof(Drive);
        }
        else if (testMode == TestMode.Http)
        {
            SalmonFSTestHelper.DriveClassType = typeof(HttpDrive);
        }
        else if (testMode == TestMode.WebService)
        {
            SalmonFSTestHelper.DriveClassType = typeof(WSDrive);
        }

        SalmonFSTestHelper.TEST_ROOT_DIR = new FS.File.File(testDir);
        if (!SalmonFSTestHelper.TEST_ROOT_DIR.Exists)
            SalmonFSTestHelper.TEST_ROOT_DIR.Mkdir();

        Console.WriteLine("setting test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.AbsolutePath);

        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if (testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new WSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
        SalmonFSTestHelper.CreateTestFiles();
        SalmonFSTestHelper.CreateHttpFiles();
        SalmonFSTestHelper.CreateHttpVault();
    }

    public static IFile CreateDir(IFile parent, string dirName)
    {
        IFile dir = parent.GetChild(dirName);
        if (!dir.Exists)
            dir.Mkdir();
        return dir;
    }

    static void CreateTestFiles()
    {
        SalmonFSTestHelper.TEST_IMPORT_TINY_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_HUGE_FILE = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;

        SalmonFSTestHelper.CreateFile(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE, 1024 * 1024);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE, 12 * 1024 * 1024);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE, 48 * 1024 * 1024);
        // this.createFileRandomData(TEST_IMPORT_HUGE_FILE,512*1024*1024);
    }

    static void CreateHttpFiles()
    {
        SalmonFSTestHelper.TEST_HTTP_TINY_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_SMALL_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_LARGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_HUGE_FILE = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_IMPORT_HUGE_FILENAME);
        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_TINY_FILE;

        SalmonFSTestHelper.CreateFile(SalmonFSTestHelper.TEST_HTTP_TINY_FILE, SalmonFSTestHelper.TINY_FILE_CONTENTS);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_HTTP_SMALL_FILE, 1024 * 1024);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_HTTP_MEDIUM_FILE, 12 * 1024 * 1024);
        SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_HTTP_LARGE_FILE, 48 * 1024 * 1024);
        // SalmonFSTestHelper.CreateFileRandomData(SalmonFSTestHelper.TEST_HTTP_HUGE_FILE, 512*1024*1024);
    }

    static void CreateHttpVault()
    {
        IFile httpVaultDir = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.HTTP_VAULT_DIRNAME);
        if (httpVaultDir != null && httpVaultDir.Exists)
            return;

        httpVaultDir.Mkdir();
        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(httpVaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile rootDir = drive.Root;
        IFile[] importFiles = new IFile[]{SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE,
                SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE,
                SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE
        };
        AesFileImporter importer = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        foreach (IFile importFile in importFiles)
        {
            importer.ImportFile(importFile, rootDir, null, false, true, null);
        }
        importer.Close();
    }

    public static void CreateFile(string path, string contents)
    {
        IFile file = new FS.File.File(path);
        Stream stream = file.GetOutputStream();
        byte[] data = UTF8Encoding.UTF8.GetBytes(contents);
        stream.Write(data, 0, data.Length);
        stream.Flush();
        stream.Close();
    }

    public static void CreateFile(IFile file, string contents)
    {
        Stream stream = file.GetOutputStream();
        byte[] data = UTF8Encoding.UTF8.GetBytes(contents);
        stream.Write(data, 0, data.Length);
        stream.Flush();
        stream.Close();
    }

    public static void CreateFileRandomData(IFile file, long size)
    {
        if (file.Exists)
            return;
        byte[] data = new byte[65536];
        Stream stream = file.GetOutputStream();
        int len = 0;
        while (size > 0)
        {
            random.NextBytes(data);
            len = (int)Math.Min(size, data.Length);
            stream.Write(data, 0, len);
            size -= len;
        }
        stream.Flush();
        stream.Close();
    }

    internal static void Initialize()
    {
        SalmonFSTestHelper.fileImporter = new AesFileImporter(SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
        SalmonFSTestHelper.fileExporter = new AesFileExporter(SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS);
    }

    internal static void Close()
    {
		if(SalmonFSTestHelper.fileImporter != null)
			SalmonFSTestHelper.fileImporter.Close();
		if(SalmonFSTestHelper.fileExporter != null)
			SalmonFSTestHelper.fileExporter.Close();
    }

    public static FileSequencer CreateSalmonFileSequencer()
    {
        // always create the sequencer files locally
        IFile seqDir = GenerateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        IFile seqFile = seqDir.GetChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);
        return new FileSequencer(seqFile, SalmonFSTestHelper.sequenceSerializer);
    }

    public static IFile GenerateFolder(string name)
    {
        return GenerateFolder(name, TEST_OUTPUT_DIR);
    }

    public static IFile GenerateFolder(string name, IFile parent, bool rand = true)
    {
        string dirName = name + (rand ? "_" + Time.Time.CurrentTimeMillis() : "");
        IFile dir = parent.GetChild(dirName);
        if (!dir.Exists)
            dir.Mkdir();
        Console.WriteLine("generated folder: " + dir.AbsolutePath);
        return dir;
    }

    public static string GetChecksum(IFile realFile)
    {
        Stream stream = realFile.GetInputStream();
        return GetChecksumStream(stream);
    }

    public static string GetChecksumStream(Stream stream)
    {
        try
        {
            MD5 md5 = MD5.Create();
            byte[] hash = md5.ComputeHash(new BufferedStream(stream, 256 * 1024));
            string hashstring = System.Convert.ToBase64String(hash);
            return hashstring;
        }
        finally
        {
            if (stream != null)
                stream.Close();
        }
    }

    public static void ImportAndExport(IFile vaultDir, string pass, IFile importFile,
                                       bool bitflip, long flipPosition, bool shouldBeEqual,
                                       bool ApplyFileIntegrity, bool VerifyFileIntegrity)
    {
        FileSequencer sequencer = CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        AesFile rootDir = drive.Root;
        rootDir.ListFiles();

        IFile fileToImport = importFile;
        string hashPreImport = SalmonFSTestHelper.GetChecksum(fileToImport);

        // import
        Action<long, long> printImportProgress = (position, length) =>
        {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                Console.WriteLine("importing file: " + position + "/" + length);
        };
        AesFile salmonFile = SalmonFSTestHelper.fileImporter.ImportFile(fileToImport, rootDir, null, false, ApplyFileIntegrity, printImportProgress);

        int chunkSize = salmonFile.FileChunkSize;
        if (chunkSize > 0 && !VerifyFileIntegrity)
            salmonFile.SetVerifyIntegrity(false);

        Assert.IsTrue(salmonFile.Exists);
        string hashPostImport = SalmonFSTestHelper.GetChecksumStream(salmonFile.GetInputStream());
        if (shouldBeEqual)
            Assert.AreEqual(hashPreImport, hashPostImport);

        AesFile[] salmonFiles = rootDir.ListFiles();
        long realFileSize = fileToImport.Length;
        foreach (AesFile file in salmonFiles)
        {
            if (file.Name.Equals(fileToImport.BaseName))
            {
                if (shouldBeEqual)
                {

                    Assert.IsTrue(file.Exists);
                    long fileSize = file.Length;

                    Assert.AreEqual(realFileSize, fileSize);
                }
            }
        }

        // export
        Action<long, long> printExportProgress = (position, length) =>
        {
            if (SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                Console.WriteLine("exporting file: " + position + "/" + length);
        };
        if (bitflip)
            FlipBit(salmonFile, flipPosition);
        int chunkSize2 = salmonFile.FileChunkSize;
        if (chunkSize2 > 0 && VerifyFileIntegrity)
            salmonFile.SetVerifyIntegrity(true);
        IFile exportFile = SalmonFSTestHelper.fileExporter.ExportFile(salmonFile, drive.ExportDir, null, false, VerifyFileIntegrity, printExportProgress);

        string hashPostExport = SalmonFSTestHelper.GetChecksum(exportFile);
        if (shouldBeEqual)
            Assert.AreEqual(hashPreImport, hashPostExport);
    }

	
    public static AesDrive OpenDrive(IFile vaultDir, Type driveClassType, string testPassword, FileSequencer sequencer = null)
    {
        if (driveClassType == typeof(WSDrive))
        {
            // use the remote service instead
            return WSDrive.Open(vaultDir, testPassword, sequencer);
        }
        else
            return Drive.OpenDrive(vaultDir, driveClassType, testPassword, sequencer);
    }

    public static AesDrive CreateDrive(IFile vaultDir, Type driveClassType, string pass, FileSequencer sequencer)
    {
        if (driveClassType == typeof(WSDrive))
            return WSDrive.Create(vaultDir, pass, sequencer);
        else
            return AesDrive.CreateDrive(vaultDir, driveClassType, pass, sequencer);
    }

    public static void ImportAndSearch(IFile vaultDir, string pass, IFile importFile,
                                           int importBufferSize, int importThreads)
    {
        FileSequencer sequencer = CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        AesFile rootDir = drive.Root;
        IFile fileToImport = importFile;
        string rbasename = fileToImport.BaseName;

        // import
        AesFileImporter fileImporter = new AesFileImporter(importBufferSize, importThreads);
        AesFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);

        // trigger the cache to add the filename
        string basename = salmonFile.Name;


        Assert.IsNotNull(salmonFile);

        Assert.IsTrue(salmonFile.Exists);

        FileSearcher searcher = new FileSearcher();
        IVirtualFile[] files = searcher.Search(rootDir, basename, true, null, null);


        Assert.IsTrue(files.Length > 0);

        Assert.AreEqual(files[0].Name, basename);

    }

    public static void ImportAndCopy(IFile vaultDir, string pass, IFile importFile,
                                     int importBufferSize, int importThreads, string newDir, bool move)
    {
        FileSequencer sequencer = CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, DriveClassType, pass, sequencer);
        AesFile rootDir = drive.Root;
        IFile fileToImport = importFile;
        string rbasename = fileToImport.BaseName;

        // import
        AesFileImporter fileImporter = new AesFileImporter(importBufferSize, importThreads);
        AesFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);

        // trigger the cache to add the filename
        string basename = salmonFile.Name;

        Assert.IsNotNull(salmonFile);

        Assert.IsTrue(salmonFile.Exists);

        string checkSumBefore = GetChecksum(salmonFile.RealFile);
        AesFile newDir1 = rootDir.CreateDirectory(newDir);
        AesFile newFile;
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

        Assert.AreEqual(salmonFile.Name, newFile.Name);
    }

    private static void FlipBit(AesFile salmonFile, long position)
    {
        Stream stream = salmonFile.RealFile.GetOutputStream();
        stream.Position = position;
        stream.Write(new byte[] { 1 }, 0, 1);
        stream.Flush();
        stream.Close();
    }


    public static AesFile ShouldCreateFileWithoutVault(byte[] testBytes, byte[] key, bool applyIntegrity, bool verifyIntegrity, int chunkSize, byte[] hashKey,
                                                    byte[] filenameNonce, byte[] fileNonce, bool flipBit, int flipPosition, bool checkData)
    {
        // write file
        IFile realDir = SalmonFSTestHelper.GenerateFolder("encfiles", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        AesFile dir = new AesFile(realDir);
        string filename = "test_" + Mku.Time.Time.CurrentTimeMillis() + "." + flipPosition + ".txt";
        AesFile newFile = dir.CreateFile(filename, key, filenameNonce, fileNonce);
        Console.WriteLine("new file: " + newFile.Path);
        if (applyIntegrity)
            newFile.SetApplyIntegrity(true, hashKey, chunkSize);
        else
            newFile.SetApplyIntegrity(false);
        Stream stream = newFile.GetOutputStream();

        stream.Write(testBytes, 0, testBytes.Length);
        stream.Flush();
        stream.Close();
        IFile realFile = newFile.RealFile;

        // tamper
        if (flipBit)
        {
            IFile realTmpFile = newFile.RealFile;
            Stream realStream = realTmpFile.GetOutputStream();
            realStream.Position = flipPosition;
            realStream.Write(new byte[] { 0 }, 0, 1);
            realStream.Flush();
            realStream.Close();
        }

        // open file for read
        AesFile readFile = new AesFile(realFile);
        readFile.EncryptionKey = key;
        readFile.RequestedNonce = fileNonce;
        if (verifyIntegrity)
            readFile.SetVerifyIntegrity(true, hashKey);
        else
            readFile.SetVerifyIntegrity(false);

        AesStream inStream = readFile.GetInputStream();
        byte[] textBytes = new byte[testBytes.Length];
        int bytesRead = inStream.Read(textBytes, 0, textBytes.Length);
        inStream.Close();
        if (checkData)
            CollectionAssert.AreEqual(testBytes, textBytes);
        return readFile;
    }

    public static void ExportAndImportAuth(IFile vault, IFile importFilePath)
    {
        // emulate 2 different devices with different sequencers
        FileSequencer sequencer1 = CreateSalmonFileSequencer();
        FileSequencer sequencer2 = CreateSalmonFileSequencer();

        // set to the first sequencer and create the vault
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vault, DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer1);
        // import a test file
        AesFile rootDir = drive.Root;
        IFile fileToImport = importFilePath;
        AesFileImporter fileImporter = new AesFileImporter(0, 0);
        AesFile salmonFileA1 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceA1 = BitConverter.ToLong(salmonFileA1.RequestedNonce, 0, Generator.NONCE_LENGTH);
        drive.Close();

        // open with another device (different sequencer) and export auth id
        drive = SalmonFSTestHelper.OpenDrive(vault, DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        string authId = drive.GetAuthId();
        bool success = false;
        try
        {
            // import a test file should fail because not authorized
            rootDir = drive.Root;
            fileToImport = importFilePath;
            fileImporter = new AesFileImporter(0, 0);
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
        IFile exportAuthDir = GenerateFolder(SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR);
        IFile exportFile = exportAuthDir.CreateFile(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        AuthConfig.ExportAuthFile(drive, authId, exportFile);
        IFile exportAuthFile = exportAuthDir.GetChild(SalmonFSTestHelper.TEST_EXPORT_AUTH_FILENAME);
        AesFile salmonCfgFile = new AesFile(exportAuthFile, drive);
        long nonceCfg = BitConverter.ToLong(salmonCfgFile.FileNonce, 0, Generator.NONCE_LENGTH);
        // import another test file
        rootDir = drive.Root;
        fileToImport = importFilePath;
        fileImporter = new AesFileImporter(0, 0);
        AesFile salmonFileA2 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceA2 = BitConverter.ToLong(salmonFileA2.FileNonce, 0, Generator.NONCE_LENGTH);
        drive.Close();

        //reopen with second device(sequencer) and import auth file
        drive = SalmonFSTestHelper.OpenDrive(vault, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer2);
        AuthConfig.ImportAuthFile(drive, exportAuthFile);
        // now import a 3rd file
        rootDir = drive.Root;
        fileToImport = importFilePath;
        AesFile salmonFileB1 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceB1 = BitConverter.ToLong(salmonFileB1.FileNonce, 0, Generator.NONCE_LENGTH);
        AesFile salmonFileB2 = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
        long nonceB2 = BitConverter.ToLong(salmonFileB2.FileNonce, 0, Generator.NONCE_LENGTH);
        drive.Close();


        Assert.AreEqual(nonceA1, nonceCfg - 1);

        Assert.AreEqual(nonceCfg, nonceA2 - 2);

        Assert.AreNotEqual(nonceA2, nonceB1);

        Assert.AreEqual(nonceB1, nonceB2 - 2);
    }


    public static void TestMaxFiles(IFile vaultDir, IFile seqFile, IFile importFile,
                                    byte[] testMaxNonce, long offset, bool shouldImport)
    {
        bool importSuccess;
        try
        {
            TestSalmonFileSequencer sequencer = new TestSalmonFileSequencer(seqFile, new SequenceSerializer(),
                testMaxNonce, offset);
            AesDrive drive;
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
            AesFile rootDir = drive.Root;
            rootDir.ListFiles();
            IFile fileToImport = importFile;
            AesFileImporter fileImporter = new AesFileImporter(0, 0);
            AesFile salmonFile = fileImporter.ImportFile(fileToImport, rootDir, null, false, false, null);
            importSuccess = salmonFile != null;
        }
        catch (Exception ex)
        {
            importSuccess = false;
            Console.Error.WriteLine(ex);
        }

        Assert.AreEqual(shouldImport, importSuccess);
    }

    public class TestSalmonFileSequencer : FileSequencer
    {
        private byte[] testMaxNonce;
        private long offset;

        public TestSalmonFileSequencer(IFile sequenceFile, INonceSequenceSerializer serializer, byte[] testMaxNonce, long offset) : base(sequenceFile, serializer)
        {
            this.testMaxNonce = testMaxNonce;
            this.offset = offset;
        }

        override
        public void InitSequence(string driveId, string authId, byte[] startNonce, byte[] maxNonce)
        {
            long nMaxNonce = BitConverter.ToLong(testMaxNonce, 0, Generator.NONCE_LENGTH);
            startNonce = BitConverter.ToBytes(nMaxNonce + offset, Generator.NONCE_LENGTH);
            maxNonce = BitConverter.ToBytes(nMaxNonce, Generator.NONCE_LENGTH);
            base.InitSequence(driveId, authId, startNonce, maxNonce);
        }
    }

    public static void TestRawFile() {
        string text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IFile dir = GenerateFolder("test");
        string filename = "file.txt";
        IFile testFile = dir.CreateFile(filename);
        byte[] bytes = UTF8Encoding.UTF8.GetBytes(text);

        // write to file
        Stream wstream = testFile.GetOutputStream();
        int idx = 0;
        while (idx < text.Length) {
            int len = Math.Min(BUFF_SIZE, text.Length - idx);
            wstream.Write(bytes, idx, len);
            idx += len;
        }
        wstream.Flush();
        wstream.Close();

        // read a file
        IFile writeFile = dir.GetChild(filename);
        Stream rstream = writeFile.GetInputStream();
        byte[] readBuff = new byte[BUFF_SIZE];
        int bytesRead = 0;
        MemoryStream lstream = new MemoryStream();
        while ((bytesRead = rstream.Read(readBuff, 0, readBuff.Length)) > 0) {
            lstream.Write(readBuff, 0, bytesRead);
        }
        byte[] lbytes = lstream.ToArray();
        string str = UTF8Encoding.UTF8.GetString(lbytes);
        // console.log(str);
        rstream.Close();

        Assert.AreEqual(str, text);
    }

    public static void TestEncDecFile() {
        String text = SalmonFSTestHelper.TINY_FILE_CONTENTS;
        int BUFF_SIZE = 16;
        IFile dir = GenerateFolder("test");
        String filename = "file.dat";
        IFile testFile = dir.CreateFile(filename);
        byte[] bytes =  UTF8Encoding.UTF8.GetBytes(text);
        byte[] key = Generator.GetSecureRandomBytes(32);
        byte[] nonce = Generator.GetSecureRandomBytes(8);

        IFile wfile = dir.GetChild(filename);
        AesFile encFile = new AesFile(wfile);
        nonce = Generator.GetSecureRandomBytes(8);
        encFile.EncryptionKey = key;
        encFile.RequestedNonce = nonce;
        Stream stream = encFile.GetOutputStream();
        int idx = 0;
        while (idx < text.Length) {
            int len = Math.Min(BUFF_SIZE, text.Length - idx);
            stream.Write(bytes, idx, len);
            idx += len;
        }
        stream.Flush();
        stream.Close();

        // decrypt an encrypted file
        IFile rfile = dir.GetChild(filename);
        AesFile encFile2 = new AesFile(rfile);
        encFile2.EncryptionKey = key;
        Stream stream2 = encFile2.GetInputStream();
        byte[] decBuff = new byte[BUFF_SIZE];
        MemoryStream lstream = new MemoryStream();
        int bytesRead = 0;

        while ((bytesRead = stream2.Read(decBuff, 0, decBuff.Length)) > 0) {
            lstream.Write(decBuff, 0, bytesRead);
        }
        byte[] lbytes = lstream.ToArray();
        string decString2 = UTF8Encoding.UTF8.GetString(lbytes);
        stream2.Close();

        Assert.AreEqual(decString2, text);
    }
    public static void EncryptAndDecryptStream(byte[] data, byte[] key, byte[] nonce)
    {
        MemoryStream encOutStream = new MemoryStream();
        AesStream encryptor = new AesStream(key, nonce, EncryptionMode.Encrypt, encOutStream);
        Stream inputStream = new MemoryStream(data);
        inputStream.CopyTo(encryptor);
        encOutStream.Position = 0;
        byte[] encData = encOutStream.ToArray();
        encryptor.Flush();
        encryptor.Close();
        encOutStream.Close();
        inputStream.Close();

        Stream encInputStream = new MemoryStream(encData);
        AesStream decryptor = new AesStream(key, nonce, EncryptionMode.Decrypt, encInputStream);
        MemoryStream outStream = new MemoryStream();
        decryptor.CopyTo(outStream);
        outStream.Position = 0;
        byte[] decData = outStream.ToArray();
        decryptor.Close();
        encInputStream.Close();
        outStream.Close();

        CollectionAssert.AreEqual(data, decData);
    }

    public static byte[] GetRealFileContents(IFile filePath)
    {
        IFile file = filePath;
        Stream ins = file.GetInputStream();
        MemoryStream outs = new MemoryStream();
        ins.CopyTo(outs);
        outs.Position = 0;
        outs.Flush();
        outs.Close();
        return outs.ToArray();
    }

    public static void SeekAndReadFileInputStream(byte[] data, AesFileInputStream fileInputStream,
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
        FileSequencer sequencer = CreateSalmonFileSequencer();

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
        catch (RangeExceededException ex)
        {
            Debug.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);
    }

    public static int GetChildrenCountRecursively(IFile realFile)
    {
        int count = 1;
        if (realFile.IsDirectory)
        {
            foreach (IFile child in realFile.ListFiles())
            {
                count += GetChildrenCountRecursively(child);
            }
        }
        return count;
    }


    public static void CopyStream(AesFileInputStream src, MemoryStream dest)
    {
        int bufferSize = 256 * 1024;
        int bytesRead;
        byte[] buffer = new byte[bufferSize];
        while ((bytesRead = src.Read(buffer, 0, bufferSize)) > 0)
        {
            dest.Write(buffer, 0, bytesRead);
        }
        dest.Flush();
    }


    internal static void ShouldReadFile(IFile vaultPath, string filename)
    {
        IFile localFile = SalmonFSTestHelper.TEST_INPUT_DIR.GetChild(filename);
        string localChkSum = GetChecksum(localFile);

        IFile vaultDir = vaultPath;
        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        IVirtualFile root = drive.Root;
        IVirtualFile file = root.GetChild(filename);
        Console.WriteLine("file size: " + file.Length);
        Console.WriteLine("file last modified: " + file.LastDateModified);
        Assert.IsTrue(file.Exists);

        Stream stream = file.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms);
        ms.Flush();
        ms.Position = 0;
        string digest = SalmonFSTestHelper.GetChecksumStream(ms);
        ms.Close();
        stream.Close();
        Assert.AreEqual(digest, localChkSum);
    }

    internal static void SeekAndReadHttpFile(byte[] data, IVirtualFile file, bool isEncrypted,
                                    int buffersCount, int bufferSize, int backOffset)
    {
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    0, 32, 0, 32,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    220, 8, 2, 8,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    100, 2, 0, 2,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    6, 16, 0, 16,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    50, 40, 0, 40,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    124, 50, 0, 50,
                    buffersCount, bufferSize, backOffset);
        SalmonFSTestHelper.SeekAndReadFileStream(data, file, isEncrypted,
                    250, 10, 0, 10,
                    buffersCount, bufferSize, backOffset);
    }

    // shouldReadLength should be equal to length
    // when checking Http files since the return buffer
    // might give us more data than requested
    static void SeekAndReadFileStream(byte[] data, IVirtualFile file, bool isEncrypted,
                                      int start, int length, int readOffset, int shouldReadLength,
                                      int buffersCount, int bufferSize, int backOffset)
    {
        byte[]
    buffer = new byte[length + readOffset];

        AesFileInputStream stream = null;
        if (SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM && isEncrypted)
        {
            // multi threaded
            stream = new AesFileInputStream((AesFile)file, buffersCount, bufferSize, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, backOffset);
        }
        else
        {
            //TODO: support IFile streams
            //            RandomAccessStream fileStream;
            //            if (isEncrypted) {
            //                fileStream =  file.getInputStream();
            //            } else {
            //                fileStream = new JavaHttpFileStream(file);
            //            }
            //            stream = new InputStreamWrapper(fileStream);
        }

        stream.Position = start;
        byte[] buff = new byte[length];
        stream.Read(buff, 0, length);
        for (int i = 0; i < length; i++)
        {
            buffer[readOffset + i] = buff[i];
        }
        byte[] tdata = new byte[buffer.Length];
        for (int i = 0; i < shouldReadLength; i++)
        {
            tdata[readOffset + i] = data[start + i];
        }
        Console.WriteLine(string.Join(" ", tdata));
        Console.WriteLine(string.Join(" ", buffer));
        stream.Close();
        CollectionAssert.AreEqual(tdata, buffer);
    }

    public static void ExportFiles(AesFile[] files, IFile dir, int threads = 1)
    {
        int bufferSize = 256 * 1024;
        AesFileCommander commander = new AesFileCommander(bufferSize, bufferSize, threads);

        List<string> hashPreExport = new List<string>();
        foreach (AesFile file in files)
            hashPreExport.Add(SalmonFSTestHelper.GetChecksumStream(file.GetInputStream()));

        // export files
        IFile[] filesExported = commander.ExportFiles(files, dir, false, true,
            (taskProgress) =>
            {
                if (!SalmonFSTestHelper.ENABLE_FILE_PROGRESS)
                    return;
                try
                {
                    Console.WriteLine("file exporting: " + taskProgress.File.Name + ": "
                    + taskProgress.ProcessedBytes + "/" + taskProgress.TotalBytes + " bytes");
                }
                catch (Exception e)
                {
                    Console.Error.Write(e);
                }
            }, IFile.AutoRename, (sfile, ex) =>
            {
                // file failed to import
                Console.Error.WriteLine(ex);
                Console.WriteLine("export failed: " + sfile.Name + "\n" + ex);
            });

        Console.WriteLine("Files exported");

        for (int i = 0; i < files.Length; i++)
        {
            Stream stream = filesExported[i].GetInputStream();
            string hashPostImport = SalmonFSTestHelper.GetChecksumStream(stream);
            stream.Close();
            Assert.AreEqual(hashPostImport, hashPreExport[i]);
        }

        // close the file commander
        commander.Close();
    }
}