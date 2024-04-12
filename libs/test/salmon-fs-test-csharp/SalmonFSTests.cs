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
using Mku.Sequence;
using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.IO;
using System.Text;
using System.Collections.Generic;
using System.Security.Cryptography;
using Mku.Salmon.Utils;
using Mku.Salmon.Sequence;
using Mku.Salmon.Streams;
using Mku.Salmon.Drive;

namespace Mku.Salmon.Test;

[TestClass]
public class SalmonFSTests
{
    static SalmonFSTests()
    {
        SalmonFSTestHelper.DriveClassType = typeof(DotNetDrive);
    }


    [TestInitialize]
    public void BeforeEach()
    {
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 2;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 2;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.Initialize();
        SalmonFSTestHelper.Initialize();
    }

    [TestCleanup]
    public void AfterEach()
    {
        SalmonFSTestHelper.Close();
        SalmonCoreTestHelper.Close();
    }



    [TestMethod]
    public void ShouldCatchNotAuthorizeNegative()
    {
        IRealFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonDrive.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        drive.Close();
        try
        {
            drive = SalmonDrive.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, sequencer);
            SalmonFile rootDir = drive.Root;
            rootDir.ListFiles();
        }
        catch (SalmonAuthException)
        {
            wrongPassword = true;
        }

        Assert.IsTrue(wrongPassword);

    }

    [TestMethod]
    public void ShouldAuthorizePositive()
    {
        IRealFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonDrive.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        drive.Close();
        try
        {
            drive = SalmonDrive.OpenDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            SalmonFile virtualRoot = drive.Root;
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    false, true, 24 + 10, false, false, false);
        }
        catch (IOException ex)
        {
            if (ex.InnerException.GetType() == typeof(IntegrityException))
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    false, false, 0, true, false,
                    false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
                integrityFailed = true;
        }

        Assert.IsFalse(integrityFailed);
    }

    [TestMethod]

    public void ShouldImportAndSearchFiles()
    {
        SalmonFSTestHelper.ImportAndSearch(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS);
    }

    [TestMethod]

    public void ShouldImportAndCopyFile()
    {
        bool integrityFailed = false;
        try
        {
            SalmonFSTestHelper.ImportAndCopy(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", false);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
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
            SalmonFSTestHelper.ImportAndCopy(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", true);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, true, true);
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, false, false);
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 36, false,
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS,
                    SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 20, false,
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
            SalmonFSTestHelper.ImportAndExport(SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS,
                    SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, true);
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine(ex);
            if (ex.GetType() == typeof(IntegrityException))
                importSuccess = false;
        }
        Assert.IsTrue(importSuccess);
    }


    [TestMethod]
    public void ShouldCatchVaultMaxFiles()
    {
        IRealFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        IRealFile seqFile = vaultDir.GetChild( SalmonFSTestHelper.TEST_SEQUENCER_FILE1);

        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.GetChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.TestMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    [TestMethod]
    public void ShouldCreateFileWithoutVault()
    {
        SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR, false, -1, true);
    }


    [TestMethod]
    public void ShouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught()
    {

        bool caught = false;
        try
        {
            SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR,
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
        for (int i = 0; i < text.Length; i++)
        {
            bool caught = false;
            bool failed = false;
            try
            {
                SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(text), SalmonCoreTestHelper.TEST_KEY_BYTES,
                        true, false, 64,
                        SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                        SalmonFSTestHelper.TEST_OUTPUT_DIR, true, i, false);
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
        bool failed = false;
        try
        {
            SalmonFSTestHelper.ShouldCreateFileWithoutVault(UTF8Encoding.UTF8.GetBytes(SalmonCoreTestHelper.TEST_TEXT), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonFSTestHelper.TEST_OUTPUT_DIR,
                    true, 24 + 32 + 5, true);
        }
        catch (IOException ex)
        {
            if (ex.GetType() == typeof(IntegrityException))
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
        IRealFile vault = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT_DIR);
        string importFilePath = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
        SalmonFSTestHelper.ExportAndImportAuth(vault, importFilePath);
    }

    [TestMethod]
    public void TestExamples()
    {
        SalmonFSTestHelper.TestExamples();
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
        SalmonFile file = SalmonFSTestHelper.ShouldCreateFileWithoutVault(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR,
                false, -1, true);
        SalmonFileInputStream fileInputStream = new SalmonFileInputStream(file,
                3, 50, 2, 12);

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
        IRealFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        DotNetFile sequenceFile = new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        INonceSequenceSerializer serializer = new SalmonSequenceSerializer();
        SalmonFileSequencer sequencer = new SalmonFileSequencer(sequenceFile, serializer);
        SalmonDrive drive = SalmonDrive.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        bool wrongPassword = false;
        SalmonFile rootDir = drive.Root;
        rootDir.ListFiles();
        drive.Close();

        // reopen but open the fs folder instead it should still login
        try
        {
            drive = SalmonDrive.OpenDrive(vaultDir.GetChild("/fs"), SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            Assert.IsTrue(drive.HasConfig());
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
        SalmonFSTestHelper.ShouldTestFileSequencer();
    }

    [TestMethod]
    public void ShouldPerformOperationsRealFiles()
    {
        bool caught = false;
        IRealFile dir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        DotNetFile file = new DotNetFile(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE);
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
        int count1 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder1"));
        int count2 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.GetChild("folder4").GetChild("folder1"));
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
        IRealFile vaultDir = SalmonFSTestHelper.GenerateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        IRealFile file = new DotNetFile(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE);

        SalmonFileSequencer sequencer = new SalmonFileSequencer(new DotNetFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDrive drive = SalmonDrive.CreateDrive(vaultDir, SalmonFSTestHelper.DriveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        SalmonFile[] sfiles = new SalmonFileCommander(0, 0, 2).ImportFiles(new IRealFile[] { file },
            drive.Root, false, true, null, null, null);

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
