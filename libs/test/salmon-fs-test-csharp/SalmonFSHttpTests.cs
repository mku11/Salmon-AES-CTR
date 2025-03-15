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
using System;
using System.IO;
using System.Collections.Generic;
using Mku.Salmon.Streams;
using Mku.FS.File;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.File;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonFSHttpTests
{
    static TestMode oldTestMode;

    [ClassInitialize]
    public static void ClassInitialize(TestContext testContext)
    {
        SalmonFSHttpTests.oldTestMode = SalmonFSTestHelper.currTestMode;
		string testDir = Environment.GetEnvironmentVariable("TEST_DIR") 
			?? "d:\\tmp\\salmon\\test";
		// use TestMode: Http only
		TestMode testMode = TestMode.Http;
		int threads = Environment.GetEnvironmentVariable("ENC_THREADS") != null && !Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
			int.Parse(Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;
			
        SalmonFSTestHelper.SetTestParams(testDir, testMode);
		Console.WriteLine("testDir: " + testDir);
        Console.WriteLine("testMode: " + testMode);
		Console.WriteLine("threads: " + threads);
        Console.WriteLine("http server url: " + SalmonFSTestHelper.HTTP_SERVER_URL);
        Console.WriteLine("HTTP_VAULT_DIR_URL: " + SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
		
        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.Initialize();
        SalmonFSTestHelper.Initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks

        ProviderType providerType = ProviderType.Default;
		string aesProviderType = Environment.GetEnvironmentVariable("AES_PROVIDER_TYPE");
		if(aesProviderType != null && !aesProviderType.Equals(""))
			providerType = (ProviderType) Enum.Parse(typeof(ProviderType), aesProviderType);
		Console.WriteLine("ProviderType: " + providerType);
        AesStream.AesProviderType = providerType;
    }

    [ClassCleanup]
    public static void ClassCleanup()
    {
        SalmonFSTestHelper.Close();
        SalmonCoreTestHelper.Close();
        SalmonFSTestHelper.SetTestParams(SalmonFSTestHelper.TEST_ROOT_DIR.Path, oldTestMode);
    }


    [TestMethod]
    public void ShouldCatchNotAuthorizeNegative()
    {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        bool wrongPassword = false;
        try
        {
            AesDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, null);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            wrongPassword = true;
        }
        Assert.IsTrue(wrongPassword);
    }

    [TestMethod]
    public void ShouldAuthorizePositive()
    {
        bool wrongPassword = false;
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        try
        {
            AesDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
            IVirtualFile root = drive.Root;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            wrongPassword = true;
        }

        Assert.IsFalse(wrongPassword);
    }

    [TestMethod]
    public void ShouldReadFromFileTiny()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
    }

    [TestMethod]
    public void ShouldReadFromFileSmall()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
    }

    public void ShouldReadFromFileMedium()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
    }

    public void ShouldReadFromFileLarge()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
    }

    [TestMethod]
    public void ShouldSeekAndReadEncryptedFileStreamFromDrive()
    {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        AesDrive drive = AesDrive.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.Root;
        IVirtualFile encFile = root.GetChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        Assert.AreEqual(encFile.Name, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);

        Stream encStream = encFile.GetInputStream();
        MemoryStream ms = new MemoryStream();
        encStream.CopyTo(ms);
        byte[] data = ms.ToArray();
        ms.Close();
        encStream.Close();
        SalmonFSTestHelper.SeekAndReadHttpFile(data, encFile, true, 3, 50, 12);
    }

    [TestMethod]
    public void ShouldListFilesFromDrive()
    {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        AesDrive drive = HttpDrive.Open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD);
        IVirtualFile root = drive.Root;
        IVirtualFile[] files = root.ListFiles();
        List<string> filenames = new List<string>();
        for (int i = 0; i < files.Length; i++)
        {
            string filename = files[i].Name;
            filenames.Add(filename);
        }
        Assert.AreEqual(files.Length, 4);
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME));
    }

    [TestMethod]
    public void ShouldExportFileFromDrive()
    {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        int threads = 1;
        AesDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        AesFile file = drive.Root.GetChild(SalmonFSTestHelper.TEST_HTTP_FILE.Name);
        IFile exportDir = SalmonFSTestHelper.GenerateFolder("export_http", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        IFile localFile = exportDir.GetChild(SalmonFSTestHelper.TEST_HTTP_FILE.Name);
        if (localFile.Exists)
            localFile.Delete();
        SalmonFSTestHelper.ExportFiles([file], exportDir, threads);
        drive.Close();
    }

    [TestMethod]
    public void ShouldReadRawFile()
    {
        IFile localFile = SalmonFSTestHelper.HTTP_TEST_DIR.GetChild(SalmonFSTestHelper.TEST_HTTP_FILE.Name);
        string localChkSum = SalmonFSTestHelper.GetChecksum(localFile);
        IFile httpRoot = new HttpFile(SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        IFile httpFile = httpRoot.GetChild(SalmonFSTestHelper.TEST_HTTP_FILE.Name);
        Stream stream = httpFile.GetInputStream();
        MemoryStream ms = new MemoryStream();
        stream.CopyTo(ms);
        ms.Flush();
        ms.Position = 0;
        string digest = SalmonFSTestHelper.GetChecksumStream(ms);
        ms.Close();
        stream.Close();
        Assert.AreEqual(digest, localChkSum);
    }
}
