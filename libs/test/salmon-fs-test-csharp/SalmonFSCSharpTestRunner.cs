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
using Mku.Salmon.Integrity;
using Mku.SalmonFS;
using Mku.Sequence;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;
using System.Collections.Generic;
using Mku.Utils;
using System.Security.Cryptography;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonFSCSharpTestRunner : SalmonCSharpTestRunner
{

    static SalmonFSCSharpTestRunner()
    {
        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);
    }

    [TestMethod]
    public void ShouldAuthenticateNegative()
    {
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.Sequencer = sequencer;
        SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
        bool wrongPassword = false;
        SalmonFile rootDir = SalmonDriveManager.Drive.VirtualRoot;
        rootDir.ListFiles();
        try
        {
            SalmonDriveManager.Drive.Authenticate(TestHelper.TEST_FALSE_PASSWORD);
        }
        catch (SalmonAuthException)
        {
            wrongPassword = true;
        }

        Assert.IsTrue(wrongPassword);
    }

    [TestMethod]
    public void ShouldCatchNotAuthenticatedNegative()
    {
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.Sequencer = sequencer;
        SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
        bool wrongPassword = false;
        SalmonDriveManager.CloseDrive();
        try
        {
            SalmonDriveManager.OpenDrive(vaultDir);
            SalmonFile rootDir = SalmonDriveManager.Drive.VirtualRoot;
            rootDir.ListFiles();
        }
        catch (SalmonAuthException)
        {
            wrongPassword = true;
        }

        Assert.IsTrue(wrongPassword);

    }

    [TestMethod]
    public void ShouldAuthenticatePositive()
    {
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.Sequencer = sequencer;
        SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
        bool wrongPassword = false;
        SalmonDriveManager.CloseDrive();
        try
        {
            SalmonDriveManager.OpenDrive(vaultDir);
            SalmonDriveManager.Drive.Authenticate(TestHelper.TEST_PASSWORD);
            SalmonFile virtualRoot = SalmonDriveManager.Drive.VirtualRoot;
        }
        catch (SalmonAuthException)
        {
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    false, true, 24 + 10, false, false, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                integrityFailed = true;
        }

        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]

    public void ShouldImportAndExportNoIntegrity()
    {
        bool integrityFailed = false;
        try
        {
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    false, false, 0, true, false,
                    false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
                integrityFailed = true;
        }

        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]

    public void ShouldImportAndSearchFiles()
    {
        CSharpFSTestHelper.ImportAndSearch(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS);
    }

    [TestMethod]

    public void ShouldImportAndCopyFile()
    {
        bool integrityFailed = false;
        try
        {
            CSharpFSTestHelper.ImportAndCopy(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, "subdir", false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
                integrityFailed = true;
        }

        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]

    public void ShouldImportAndMoveFile()
    {
        bool integrityFailed = false;
        try
        {
            CSharpFSTestHelper.ImportAndCopy(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, "subdir", true);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
                integrityFailed = true;
        }

        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]

    public void ShouldImportAndExportIntegrityBitFlipData()
    {
        bool integrityFailed = false;
        try
        {
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, true, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, false, false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, false,
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 36, false,
                    true, false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS,
                    CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS, CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 20, false,
                    true, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
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
            CSharpFSTestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    CSharpFSTestHelper.ENC_IMPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_IMPORT_THREADS,
                    CSharpFSTestHelper.ENC_EXPORT_BUFFER_SIZE, CSharpFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, true);
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            if (ex.GetType() == typeof(SalmonIntegrityException))
                importSuccess = false;
        }
        Assert.IsTrue(importSuccess);
    }


    [TestMethod]
    public void ShouldCatchVaultMaxFiles()
    {
        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);

        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        string seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;

        CSharpFSTestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        CSharpFSTestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        CSharpFSTestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        CSharpFSTestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    [TestMethod]
    public void ShouldCreateFileWithoutVault()
    {
        CSharpFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR, false, -1, true);
    }


    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught()
    {

        bool caught = false;
        try
        {
            CSharpFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                    true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR,
                    true, 45, true);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }

        Assert.IsTrue(caught);
    }


    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught()
    {
        string text = TestHelper.TEST_TEXT;
        for (int i = 0; i < text.Length; i++)
        {
            bool caught = false;
            bool failed = false;
            try
            {
                CSharpFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(text), TestHelper.TEST_KEY_BYTES,
                        true, false, 64,
                        TestHelper.TEST_HMAC_KEY_BYTES, TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                        TEST_OUTPUT_DIR, true, i, false);
            }
            catch (IOException ex)
            {
                if (ex.GetType() == typeof(SalmonIntegrityException))
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
        bool failed = false;
        try
        {
            CSharpFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(TestHelper.TEST_TEXT), TestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TEST_OUTPUT_DIR,
                    true, 24 + 32 + 5, true);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(SalmonIntegrityException))
                caught = true;
        }
        catch (Exception)
        {
            failed = true;
        }

        Assert.IsFalse(caught);

        Assert.IsTrue(failed);
    }


    [TestMethod]

    public void ShouldExportAndImportAuth()
    {
        string vault = TestHelper.GenerateFolder(TEST_VAULT_DIR);
        string importFilePath = TEST_IMPORT_TINY_FILE;
        CSharpFSTestHelper.ExportAndImportAuth(vault, importFilePath);
    }

    [TestMethod]
    public void TestExamples()
    {
        CSharpFSTestHelper.TestExamples();
    }


    [TestMethod]
    public void ShouldEncryptAndDecryptStream()
    {
        byte[] data = CSharpFSTestHelper.GetRealFileContents(TEST_IMPORT_FILE);
        CSharpFSTestHelper.EncryptAndDecryptStream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
    }

    [TestMethod]
    public void ShouldEncryptAndReadFileInputStream()
    {
        byte[] data = new byte[256];
        for (int i = 0; i < data.Length; i++)
        {
            data[i] = (byte)i;
        }
        SalmonFile file = CSharpFSTestHelper.ShouldCreateFileWithoutVault(data, TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR,
                false, -1, true);
        SalmonFileInputStream fileInputStream = new SalmonFileInputStream(file,
                3, 50, 2, 12);

        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        CSharpFSTestHelper.SeekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
    }

    [TestMethod]
    public void ShouldCreateDriveAndOpenFsFolder()
    {
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        DotNetFile sequenceFile = new DotNetFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1);
        ISalmonSequenceSerializer serializer = new SalmonSequenceSerializer();
        SalmonFileSequencer sequencer = new SalmonFileSequencer(sequenceFile, serializer);
        SalmonDriveManager.Sequencer = sequencer;
        SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
        bool wrongPassword = false;
        SalmonFile rootDir = SalmonDriveManager.Drive.VirtualRoot;
        rootDir.ListFiles();
        SalmonDriveManager.Drive.Close();

        // reopen but open the fs folder instead it should still login
        try
        {
            SalmonDrive drive = SalmonDriveManager.OpenDrive(vaultDir + "/fs");
            Assert.IsTrue(drive.HasConfig());
            SalmonDriveManager.Drive.Authenticate(TestHelper.TEST_PASSWORD);
        }
        catch (SalmonAuthException)
        {
            wrongPassword = true;
        }

        Assert.IsFalse(wrongPassword);
    }


    [TestMethod]
    public void ShouldCreateWinFileSequencer()
    {
        CSharpFSTestHelper.ShouldTestFileSequencer();
    }

    [TestMethod]
    public void ShouldPerformOperationsRealFiles()
    {
        bool caught = false;
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        DotNetFile dir = new DotNetFile(vaultDir);
        DotNetFile file = new DotNetFile(TEST_IMPORT_TINY_FILE);
        IRealFile file1 = file.Copy(dir);
        IRealFile file2;
        try
        {
            file2 = file.Copy(dir);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }
        Assert.AreEqual(true, caught);
        file2 = file.Copy(dir, IRealFile.AutoRename(file));

        Assert.AreEqual(2, dir.ChildrenCount);
        Assert.IsTrue(dir.GetChild(file.BaseName).Exists);
        Assert.IsTrue(dir.GetChild(file.BaseName).IsFile);
        Assert.IsTrue(dir.GetChild(file2.BaseName).Exists);
        Assert.IsTrue(dir.GetChild(file2.BaseName).IsFile);

        IRealFile dir1 = dir.CreateDirectory("folder1");
        Assert.IsTrue(dir.GetChild("folder1").Exists);
        Assert.IsTrue(dir.GetChild("folder1").IsDirectory);
        Assert.AreEqual(3, dir.ChildrenCount);



        IRealFile folder1 = dir.CreateDirectory("folder2");
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

        IRealFile file3 = file.Copy(dir);
        caught = false;
        try
        {
            file3.Move(dir.GetChild("folder1"));
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            caught = true;
        }
        Assert.IsTrue(caught);
        IRealFile file4 = file3.Move(dir.GetChild("folder1"), IRealFile.AutoRename(file3));
        Assert.IsTrue(file4.Exists);
        Assert.AreEqual(3, dir.GetChild("folder1").ChildrenCount);

        IRealFile folder2 = dir.GetChild("folder1").CreateDirectory("folder2");
        foreach (IRealFile rfile in dir.GetChild("folder1").ListFiles())
            rfile.Copy(folder2);
        Assert.AreEqual(4, dir.GetChild("folder1").ChildrenCount);
        Assert.AreEqual(4, dir.GetChild("folder1").GetChild("folder2").ChildrenCount);

        // recursive copy
        IRealFile folder3 = dir.CreateDirectory("folder4");
        dir.GetChild("folder1").CopyRecursively(folder3);
        int count1 = CSharpFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder1"));
        int count2 = CSharpFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder4").GetChild("folder1"));
        Assert.AreEqual(count1, count2);

        IRealFile dfile = dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.BaseName);
        Assert.IsTrue(dfile.Exists);
        Assert.IsTrue(dfile.Delete());
        Assert.AreEqual(3, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
        dir.GetChild("folder1").CopyRecursively(folder3, null, IRealFile.AutoRename, false, null);
        Assert.AreEqual(2, dir.ChildrenCount);
        Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        Assert.AreEqual(7, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        Assert.AreEqual(6, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
        Assert.AreEqual(0, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild("folder2").ChildrenCount);

        dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.BaseName).Delete();
        dir.GetChild("folder4").GetChild("folder1").GetChild(file.BaseName).Delete();
        List<IRealFile> failed = new List<IRealFile>();
        dir.GetChild("folder1").CopyRecursively(folder3, null, null, false, (file, ex) =>
        {
            failed.Add(file);
        });
        Assert.AreEqual(4, failed.Count);
        Assert.AreEqual(2, dir.ChildrenCount);
        Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        Assert.AreEqual(7, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        Assert.AreEqual(6, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
        Assert.AreEqual(0, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild("folder2").ChildrenCount);


        dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild(file.BaseName).Delete();
        dir.GetChild("folder4").GetChild("folder1").GetChild(file.BaseName).Delete();
        List<IRealFile> failedmv = new List<IRealFile>();
        dir.GetChild("folder1").MoveRecursively(dir.GetChild("folder4"), null, IRealFile.AutoRename, false, (file, ex) =>
        {
            failedmv.Add(file);
        });
        Assert.AreEqual(4, failed.Count);
        Assert.AreEqual(1, dir.ChildrenCount);
        Assert.AreEqual(1, dir.GetChild("folder4").ChildrenCount);
        Assert.AreEqual(9, dir.GetChild("folder4").GetChild("folder1").ChildrenCount);
        Assert.AreEqual(8, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").ChildrenCount);
        Assert.AreEqual(0, dir.GetChild("folder4").GetChild("folder1").GetChild("folder2").GetChild("folder2").ChildrenCount);
    }

    [TestMethod]
    public void ShouldReadFromFileMultithreaded()
    {
        bool caught = false;
        string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
        IRealFile file = new DotNetFile(TEST_IMPORT_LARGE_FILE);

        SalmonDriveManager.VirtualDriveClass = typeof(DotNetDrive);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.Sequencer = sequencer;
        SalmonDrive drive = SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
        SalmonFile[] sfiles = new SalmonFileCommander(0, 0, 2).ImportFiles(new IRealFile[] { file },
            drive.VirtualRoot, false, true, null, null, null);

        SalmonFileInputStream fileInputStream1 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        MD5 md51 = MD5.Create();
        byte[] hash1 = md51.ComputeHash(fileInputStream1);
        string h1 = Mku.Convert.BitConverter.ToHex(hash1);

        SalmonFileInputStream fileInputStream2 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        MD5 md52 = MD5.Create();
        byte[] hash2 = md52.ComputeHash(fileInputStream2);
        string h2 = Mku.Convert.BitConverter.ToHex(hash2);
        Assert.AreEqual(h1, h2);

        long pos = new Random().NextInt64(file.Length);
        // long pos = 0;

        fileInputStream1.Position = pos;
        MemoryStream ms1 = new MemoryStream();
        fileInputStream1.CopyTo(ms1);
        ms1.Flush();
        ms1.Position = 0;
        MD5 m1 = MD5.Create();
        byte[] hash3 = m1.ComputeHash(ms1);
        string h3 = Mku.Convert.BitConverter.ToHex(hash3);

        fileInputStream2.Position = pos;
        MemoryStream ms2 = new MemoryStream();
        fileInputStream2.CopyTo(ms2);
        ms2.Flush();
        ms2.Position = 0;
        MD5 m2 = MD5.Create();
        byte[] hash4 = m2.ComputeHash(ms2);
        string h4 = Mku.Convert.BitConverter.ToHex(hash4);

        Assert.AreEqual(h3, h4);
    }
}
