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
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Collections.Generic;
using Mku.Salmon.Drive;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonFSHttpTests
{
    static TestMode oldTestMode;

    [ClassInitialize]
    public static void ClassInitialize(TestContext testContext)
    {
        SalmonFSHttpTests.oldTestMode = SalmonFSTestHelper.currTestMode;

        // use TestMode: Http only
        SalmonFSTestHelper.SetTestParams(Environment.GetEnvironmentVariable("SALMON_TEST_DIR") 
            ?? "d:\\tmp\\salmon\\test", TestMode.Http);

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = 1;
        SalmonCoreTestHelper.TEST_DEC_THREADS = 1;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 1;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 1;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.Initialize();
        SalmonFSTestHelper.Initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks

        // use the native library
        // SalmonStream.AesProviderType = ProviderType.AesIntrinsics;
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
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        bool wrongPassword = false;
        try
        {
            SalmonDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, null);
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
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        try
        {
            SalmonDrive drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
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
    public void ShouldReadFromRealFileTiny()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
    }

    [TestMethod]
    public void ShouldReadFromRealFileSmall()
    {
        SalmonFSTestHelper.ShouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
    }

    [TestMethod]
    public void ShouldSeekAndReadEncryptedFileStreamFromDrive()
    {
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        SalmonDrive drive = SalmonDrive.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.Root;
        IVirtualFile encFile = root.GetChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        Assert.AreEqual(encFile.BaseName, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);

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
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        SalmonDrive drive = DotNetHttpDrive.Open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.Root;
        IVirtualFile[] files = root.ListFiles();
        List<string> filenames = new List<string>();
        for (int i = 0; i < files.Length; i++)
        {
            string filename = files[i].BaseName;
            filenames.Add(filename);
        }
        Assert.AreEqual(files.Length, 4);
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME));
        Assert.IsTrue(filenames.Contains(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME));
    }
}
