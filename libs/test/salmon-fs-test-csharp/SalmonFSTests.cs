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

using Mku.Salmon.Integrity;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;
using System.Collections.Generic;
using System.Security.Cryptography;
using Mku.Salmon.Streams;
using Mku.FS.File;
using Mku.SalmonFS.Sequence;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.Auth;
using Mku.SalmonFS.File;
using Mku.SalmonFS.Streams;
using Mku.SalmonFS.Drive.Utils;
using Mku.FS.Drive.Utils;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonFSTests
{
    [ClassInitialize]
    public static void ClassInitialize(TestContext testContext)
    {
        // use TestMode: Local, WebService. Http is tested only in SalmonFSHttpTests.
        string testDir = Environment.GetEnvironmentVariable("TEST_DIR") != null
            && !Environment.GetEnvironmentVariable("TEST_DIR").Equals("") ? Environment.GetEnvironmentVariable("TEST_DIR") : "d:\\tmp\\salmon\\test";
        TestMode testMode = Environment.GetEnvironmentVariable("TEST_MODE") != null
            && !Environment.GetEnvironmentVariable("TEST_DIR").Equals("") ?
            (TestMode)Enum.Parse(typeof(TestMode), Environment.GetEnvironmentVariable("TEST_MODE")) : TestMode.Local;
        int threads = Environment.GetEnvironmentVariable("ENC_THREADS") != null && !Environment.GetEnvironmentVariable("ENC_THREADS").Equals("") ?
            int.Parse(Environment.GetEnvironmentVariable("ENC_THREADS")) : 1;

        SalmonFSTestHelper.SetTestParams(testDir, testMode);
        Console.WriteLine("testDir: " + testDir);
        Console.WriteLine("testMode: " + testMode);
        Console.WriteLine("threads: " + threads);
        Console.WriteLine("ws server url: " + SalmonFSTestHelper.WS_SERVER_URL);

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.Initialize();
        SalmonFSTestHelper.Initialize();

        ProviderType providerType = ProviderType.Default;
        string aesProviderType = Environment.GetEnvironmentVariable("AES_PROVIDER_TYPE");
        if (aesProviderType != null && !aesProviderType.Equals(""))
            providerType = (ProviderType)Enum.Parse(typeof(ProviderType), aesProviderType);
        Console.WriteLine("ProviderType: " + providerType);

        AesStream.AesProviderType = providerType;
    }

    [ClassCleanup]
    public static void ClassCleanup()
    {
        SalmonFSTestHelper.Close();
        SalmonCoreTestHelper.Close();
    }

    [TestMethod]
    public void ShouldCatchNotAuthorizeNegative()
    {
        IFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        drive.Close();
        try
        {
            drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, sequencer);
            AesFile rootDir = drive.Root;
            rootDir.ListFiles();
        }
        catch (AuthException)
        {
            wrongPassword = true;
        }

        Assert.IsTrue(wrongPassword);

    }

    [TestMethod]
    public void ShouldAuthorizePositive()
    {
        IFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, typeof(Drive),
                SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        drive.Close();
        try
        {
            drive = SalmonFSTestHelper.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            AesFile virtualRoot = drive.Root;
        }
        catch (AuthException ex)
        {
            Console.Error.WriteLine(ex);
            wrongPassword = true;
        }

        Assert.IsFalse(wrongPassword);
    }

    [TestMethod]
    public void ShouldImportAndExportNoIntegrityBitFlipDataNoCatch()
    {
        bool integrityFailed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false,
                false, false);
        }
        catch (Exception ex)
        {
            integrityFailed = true;
        }
        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]
    public void ShouldImportAndExportNoIntegrity()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                false, 0, true,
                false, false);
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
        }

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndSearchFiles()
    {
        SalmonFSTestHelper.ImportAndSearch(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
            SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
            SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
    }

    [TestMethod]
    public void ShouldImportAndCopyFile()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndCopy(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", false);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
        }

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndMoveFile()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndCopy(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", true);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            failed = true;
        }

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndExportIntegrityBitFlipData()
    {
        bool integrityFailed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, true, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                integrityFailed = true;
        }

        Assert.IsTrue(integrityFailed);
    }


    [TestMethod]
    public void ShouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch()
    {
        bool integrityFailed = false;
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 24 + 10, false, false, false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
                integrityFailed = true;
        }
        catch (Exception)
        {
            failed = true;
        }

        Assert.IsFalse(integrityFailed);

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndExportNoAppliedIntegrityYesVerifyIntegrityNoBitFlipDataShouldCatch()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                false, 0, false,
                false, true);
        }
        catch (Exception)
        {
            failed = true;
        }

        Assert.IsTrue(failed);
    }

    [TestMethod]
    public void ShouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                true, 36, false,
                true, false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
                failed = true;
        }

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndExportAppliedIntegrityNoVerifyIntegrity()
    {
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    false, 0, true,
                    true, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                failed = true;
        }

        Assert.IsFalse(failed);
    }

    [TestMethod]
    public void ShouldImportAndExportIntegrityBitFlipHeader()
    {
        bool integrityFailed = false;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 20, false,
                    true, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                integrityFailed = true;
        }

        Assert.IsTrue(integrityFailed);
    }

    [TestMethod]
    public void ShouldImportAndExportIntegrity()
    {
        bool importSuccess = true;
        try
        {
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    false, 0, true,
                    true, true);
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            importSuccess = false;
        }
        Assert.IsTrue(importSuccess);
    }

    [TestMethod]
    public void ShouldCatchVaultMaxFiles()
    {
        IFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        IFile seqDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_SEQ_DIRNAME, SalmonFSTestHelper.TEST_SEQ_DIR, true);
        IFile seqFile = seqDir.GetChild(SalmonFSTestHelper.TEST_SEQ_FILENAME);


        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    [TestMethod]
    public void ShouldCreateFileWithoutVault()
    {
        SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, -1, true);
    }


    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught()
    {

        bool caught = false;
        try
        {
            SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 45, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }

    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught()
    {
        string text = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < 5; i++)
        {
            bool caught = false;
            bool failed = false;
            try
            {
                SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(text), SalmonCoreTestHelper.TEST_KEY_BYTES,
                        true, false, 64,
                        SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                        true, i, false);
            }
            catch (IOException ex)
            {
                if (ex.GetType() == typeof(IntegrityException))
                    caught = true;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                failed = true;
            }
            Assert.IsFalse(caught);
            Assert.IsFalse(failed);
        }
    }

    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual()
    {

        bool caught = false;
        try
        {
            SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    true, 24 + 32 + 5, false);
        }
        catch (Exception ex)
        {
            if (ex.GetType() == typeof(IntegrityException)) {
                caught = true;
				Console.Error.WriteLine(ex);
            } else {
                throw;
			}
        }

        Assert.IsFalse(caught);
    }

    [TestMethod]
    public void ShouldExportAndImportAuth()
    {
        IFile vault = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        IFile importFilePath = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
        SalmonFSTestHelper.ExportAndImportAuth(vault, importFilePath);
    }

    [TestMethod]
    public void ShouldEncryptAndDecryptStream()
    {
        byte[] data = SalmonFSTestHelper.GetRealFileContents(SalmonFSTestHelper.TEST_IMPORT_FILE);
        SalmonFSTestHelper.EncryptAndDecryptStream(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
    }

    [TestMethod]
    public void ShouldEncryptAndReadFileInputStream()
    {
        byte[] data = new byte[256];
        for (int i = 0; i < data.Length; i++)
        {
            data[i] = (byte)i;
        }
        AesFile file = SalmonFSTestHelper.ShouldCreateFileWithoutVault(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                false, -1, true);
        AesFileInputStream fileInputStream = new AesFileInputStream(file, 3, 100, SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS, 12);

        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        SalmonFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
    }

    [TestMethod]
    public void ShouldCreateDriveAndOpenFsFolder()
    {
        IFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        AesFile rootDir = drive.Root;
        rootDir.ListFiles();
        drive.Close();

        // reopen but open the fs folder instead it should still login
        try
        {
            drive = SalmonFSTestHelper.OpenDrive(vaultDir.GetChild("fs"), SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            Assert.IsTrue(drive.HasConfig());
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            wrongPassword = true;
        }

        Assert.IsFalse(wrongPassword);
    }


    [TestMethod]
    public void ShouldCreateWinFileSequencer()
    {
        SalmonFSTestHelper.ShouldTestFileSequencer();
    }

    [TestMethod]
    public void ShouldPerformOperationsRealFiles()
    {
        bool caught = false;
        IFile dir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        IFile file = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
        IFile file1 = file.Copy(dir);
        IFile file2;
        try
        {
            file2 = file.Copy(dir);
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex.Message);
            caught = true;
        }
        Assert.AreEqual(true, caught);
        IFile.CopyOptions copyOptions = new IFile.CopyOptions();
        copyOptions.newFilename = IFile.AutoRename(file);
        file2 = file.Copy(dir, copyOptions);

        Assert.AreEqual(2, dir.ChildrenCount);
        Assert.IsTrue(dir.GetChild(file.Name).Exists);
        Assert.IsTrue(dir.GetChild(file.Name).IsFile);
        Assert.IsTrue(dir.GetChild(file2.Name).Exists);
        Assert.IsTrue(dir.GetChild(file2.Name).IsFile);

        IFile dir1 = dir.CreateDirectory("folder1");
        Assert.IsTrue(dir.GetChild("folder1").Exists);
        Assert.IsTrue(dir.GetChild("folder1").IsDirectory);
        Assert.AreEqual(3, dir.ChildrenCount);

        IFile folder1 = dir.CreateDirectory("folder2");
        Assert.IsTrue(folder1.Exists);
        bool renamed = folder1.RenameTo("folder3");
        Assert.IsTrue(renamed);
        Assert.IsFalse(dir.GetChild("folder2").Exists);
        Assert.IsTrue(dir.GetChild("folder3").Exists);
        Assert.IsTrue(dir.GetChild("folder3").IsDirectory);
        Assert.AreEqual(4, dir.ChildrenCount);
        bool delres = dir.GetChild("folder3").Delete();
        Assert.IsTrue(delres);
        Assert.IsFalse(dir.GetChild("folder3").Exists);
        Assert.AreEqual(3, dir.ChildrenCount);

        file1.Move(dir.GetChild("folder1"));
        file2.Move(dir.GetChild("folder1"));

        IFile file3 = file.Copy(dir);
        caught = false;
        try
        {
            file3.Move(dir.GetChild("folder1"));
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex.Message);
            caught = true;
        }
        Assert.IsTrue(caught);
        IFile.MoveOptions moveOptions = new IFile.MoveOptions();
        moveOptions.newFilename = IFile.AutoRename(file3);
        IFile file4 = file3.Move(dir.GetChild("folder1"), moveOptions);
        Assert.IsTrue(file4.Exists);
        Assert.AreEqual(3, dir.GetChild("folder1").ChildrenCount);

        IFile folder2 = dir.GetChild("folder1").CreateDirectory("folder2");
        foreach (IFile rfile in dir.GetChild("folder1").ListFiles())
            rfile.CopyRecursively(folder2);
        Assert.AreEqual(4, dir.GetChild("folder1").ChildrenCount);
        Assert.AreEqual(3, dir.GetChild("folder1").GetChild("folder2").ChildrenCount);

        // recursive copy
        IFile folder3 = dir.CreateDirectory("folder4");
        dir.GetChild("folder1").CopyRecursively(folder3);
        int count1 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder1"));
        int count2 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder4").GetChild("folder1"));
        Assert.AreEqual(count1, count2);

        IFile dfile = dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.Name);
        Assert.IsTrue(dfile.Exists);
        Assert.IsTrue(dfile.Delete());
        Assert.AreEqual(2, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
		
		// FIXME:
        // IFile.RecursiveCopyOptions recCopyOptions = new IFile.RecursiveCopyOptions();
        // recCopyOptions.autoRename = IFile.AutoRename;
        // dir.GetChild("folder1").CopyRecursively(folder3, recCopyOptions);
        // Assert.AreEqual(2, dir.ChildrenCount);
        // Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        // Assert.AreEqual(7, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        // Assert.AreEqual(5, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);

        dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.Name).Delete();
        dir.GetChild("folder4").GetChild("folder1").GetChild(file.Name).Delete();
        List<IFile> failed = new List<IFile>();
        recCopyOptions = new IFile.RecursiveCopyOptions();
        recCopyOptions.onFailed = (file, ex) =>
        {
            failed.Add(file);
        };
        dir.GetChild("folder1").CopyRecursively(folder3, recCopyOptions);
        Assert.AreEqual(4, failed.Count);
        Assert.AreEqual(2, dir.ChildrenCount);
        Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        Assert.AreEqual(7, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        Assert.AreEqual(5, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);


        dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.Name).Delete();
        dir.GetChild("folder4").GetChild("folder1").GetChild(file.Name).Delete();
        List<IFile> failedmv = new List<IFile>();
        IFile.RecursiveMoveOptions recMoveOptions = new IFile.RecursiveMoveOptions();
        recMoveOptions.autoRename = IFile.AutoRename;
        recMoveOptions.onFailed = (file, ex) =>
        {
            failedmv.Add(file);
        };
        dir.GetChild("folder1").MoveRecursively(dir.GetChild("folder4"), recMoveOptions);
        Assert.AreEqual(4, failed.Count);
        Assert.AreEqual(1, dir.ChildrenCount);
        Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        Assert.AreEqual(9, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        Assert.AreEqual(7, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
    }

    [TestMethod]
    public void ShouldReadFromFileMultithreaded()
    {
        IFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIRNAME);
        IFile file = SalmonFSTestHelper.TEST_IMPORT_FILE;

		long pos = 3 * Integrity.Integrity.DEFAULT_CHUNK_SIZE + 3;

        Stream stream = file.GetInputStream().AsReadStream();
        stream.Seek(pos, SeekOrigin.Current);
        String h1 = SalmonCoreTestHelper.GetChecksumStream(stream);
        stream.Close();

        FileSequencer sequencer = SalmonFSTestHelper.CreateSalmonFileSequencer();
        AesDrive drive = SalmonFSTestHelper.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        AesFileCommander fileCommander = new AesFileCommander(Integrity.Integrity.DEFAULT_CHUNK_SIZE, Integrity.Integrity.DEFAULT_CHUNK_SIZE,
			SalmonFSTestHelper.ENC_IMPORT_THREADS);

        FileCommander.BatchImportOptions importOptions = new FileCommander.BatchImportOptions();
        importOptions.integrity = true;
        AesFile[] sfiles = fileCommander.ImportFiles(new IFile[] { file }, drive.Root, importOptions);
        fileCommander.Close();
		Console.WriteLine("files imported");

		Console.WriteLine("using 1 thread to read");
        AesFileInputStream fileInputStream1 = new AesFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, Integrity.Integrity.DEFAULT_CHUNK_SIZE);
		Console.WriteLine("seeking to: " + pos);
        fileInputStream1.Seek(pos, SeekOrigin.Current);
        MD5 md51 = MD5.Create();
        byte[] hash1 = md51.ComputeHash(fileInputStream1);
        string h2 = Mku.Convert.BitConverter.ToHex(hash1);
        fileInputStream1.Close();
		Assert.AreEqual(h1, h2);

		Console.WriteLine("using 2 threads to read");
        AesFileInputStream fileInputStream2 = new AesFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 2, Integrity.Integrity.DEFAULT_CHUNK_SIZE);
		Console.WriteLine("seeking to: " + pos);
        fileInputStream2.Seek(pos, SeekOrigin.Current);
        MD5 md52 = MD5.Create();
        byte[] hash2 = md52.ComputeHash(fileInputStream2);
        string h3 = Mku.Convert.BitConverter.ToHex(hash2);
        fileInputStream2.Close();
        Assert.AreEqual(h1, h3);
    }

    [TestMethod]
    public void TestRawFile()
    {
        SalmonFSTestHelper.TestRawFile();
    }

    [TestMethod]
    public void TestEncDecFile()
    {
        SalmonFSTestHelper.TestEncDecFile();
    }
}
