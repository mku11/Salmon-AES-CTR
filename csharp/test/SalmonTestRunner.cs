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
using Org.BouncyCastle.Utilities;
using Salmon.FS;
using Salmon.Net.FS;
using Salmon.Streams;
using Salmon.Test.Utils;
using System;
using System.IO;
using System.Text;

namespace Salmon.Test
{
    [TestClass]
    public class SalmonTestRunner
    {
        private static readonly string TEST_OUTPUT_DIR = @"d:\tmp\output";
        private static readonly string TEST_VAULT_DIR = @"d:\tmp\output\enc";
        private static readonly string TEST_VAULT2_DIR = @"d:\tmp\output\enc2";
        private static readonly string TEST_EXPORT_AUTH_DIR = @"d:\tmp\output\export";
        private static readonly string TEST_IMPORT_TINY_FILE = @"d:\tmp\testdata\tiny_test.txt";
        private static readonly string TEST_IMPORT_SMALL_FILE = @"d:\tmp\testdata\small_test.zip";
        private static readonly string TEST_IMPORT_MEDIUM_FILE = @"d:\tmp\testdata\medium_test.zip";
        private static readonly string TEST_IMPORT_LARGE_FILE = @"d:\tmp\testdata\large_test.mp4";
        private static readonly string TEST_IMPORT_HUGE_FILE = @"d:\tmp\testdata\huge.zip";
        private static readonly string TEST_IMPORT_FILE = TEST_IMPORT_SMALL_FILE;
        private static readonly string TEST_PIPE_NAME = "SalmonService";

        static SalmonTestRunner()
        {
            SalmonStream.SetProviderType(SalmonStream.ProviderType.AesIntrinsics);
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            SalmonStream.SetEnableLogDetails(false);
            //SalmonEncryptor.SetBufferSize(16);

            SetupFileSequencer();
            //SetupServiceSequencer();
        }


        [TestInitialize]
        public void Setup()
        {
            
        }

        [TestCleanup]
        public void Finish()
        {

        }

        private static void SetupServiceSequencer()
        {
            WinClientSequencer serviceSequencer = new WinClientSequencer(TEST_PIPE_NAME);
            SalmonDriveManager.SetSequencer(serviceSequencer);
        }

        private static void SetupFileSequencer()
        {
            IRealFile dir = new DotNetFile(TEST_OUTPUT_DIR);
            if (!dir.Exists())
                dir.Mkdir();
            IRealFile file = dir.GetChild(TestHelper.TEST_SEQUENCER_FILENAME1);
            if (file.Exists())
                file.Delete();
            FileSequencer sequencer = new FileSequencer(file, new SalmonSequenceParser());
            SalmonDriveManager.SetSequencer(sequencer);
        }

        [TestMethod]
        public void ShouldEncryptAndDecryptText()
        {
            string plainText = TestHelper.TEST_TINY_TEXT;

            string encText = SalmonTextEncryptor.EncryptString(plainText, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
            string decText = SalmonTextEncryptor.DecryptString(encText, TestHelper.TEST_KEY_BYTES, null, true);
            Assert.AreEqual(plainText, decText);
        }

        [TestMethod]
        public void ShouldEncryptAndDecryptTextCompatible()
        {
            string plainText = TestHelper.TEST_TEXT;
            for (int i = 0; i < 8; i++)
                plainText += plainText;

            SalmonStream.SetProviderType(SalmonStream.ProviderType.AesIntrinsics);
            byte[] bytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            byte[] encBytesDef = TestHelper.DefaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
            byte[] decBytesDef = TestHelper.DefaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
            CollectionAssert.AreEqual(bytes, decBytesDef);
            byte[] encBytes = SalmonEncryptor.Encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
            CollectionAssert.AreEqual(encBytesDef, encBytes);
            byte[] decBytes = SalmonEncryptor.Decrypt(encBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
            CollectionAssert.AreEqual(bytes, decBytes);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamNoBuffersSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_DEC_BUFFER_SIZE);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE + 3, TestHelper.TEST_DEC_BUFFER_SIZE + 3);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamDecNoAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                encBufferSize: 16 * 2, decBufferSize: 16 * 2 + 3);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamEncNoAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                encBufferSize: 16 * 2 + 3, decBufferSize: 16 * 2);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, header: TestHelper.TEST_HEADER,
                maxTextLength: 64
                );
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, header: TestHelper.TEST_HEADER,
                chunkSize: 128
                );
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
            128, 128,
            testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 64);

        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);


        }


        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedEncBufferNotAlignedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);


        }


        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned()
        {
            TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
            testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
        }


        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);


        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, flipBits: true);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
TestHelper.TEST_ENC_BUFFER_SIZE, TestHelper.TEST_ENC_BUFFER_SIZE,
                    testIntegrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32, flipBits: true);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldNotReadFromStreamEncryptionMode()
        {
            string testText = TestHelper.TEST_TEXT;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < 10; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();
            bool caught = false;
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            MemoryStream ins = new MemoryStream(inputBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Encrypt, outs);
            try
            {
                encWriter.CopyTo(outs);
            }
            catch (Exception)
            {
                caught = true;
            }
            ins.Close();
            encWriter.Flush();
            encWriter.Close();
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldNotWriteToStreamDecryptionMode()
        {
            string testText = TestHelper.TEST_TEXT;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < 10; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();
            bool caught = false;
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            byte[] encBytes = TestHelper.Encrypt(inputBytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.TEST_ENC_BUFFER_SIZE);

            MemoryStream ins = new MemoryStream(encBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, SalmonStream.EncryptionMode.Decrypt, ins);
            try
            {
                ins.CopyTo(encWriter);
            }
            catch (Exception)
            {
                caught = true;
            }
            ins.Close();
            encWriter.Flush();
            encWriter.Close();
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldSeekAndReadNoIntegrity()
        {
            TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
        }


        [TestMethod]
        public void ShouldSeekAndTestBlockAndCounter()
        {
            TestHelper.SeekTestCounterAndBlock(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
        }


        [TestMethod]
        public void ShouldSeekAndReadWithIntegrity()
        {
            TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);

        }
        [TestMethod]
        public void ShouldSeekAndReadWithIntegrityMultiChunks()
        {
            TestHelper.SeekAndRead(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, integrity: true, chunkSize: 32, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldSeekAndWriteNoIntegrity()
        {
            TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, allowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, allowRangeWrite: false);
            }
            catch (SalmonSecurityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);

        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityAligned()
        {
            TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, alignToChunk: true,
                integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, allowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityAlignedMultiChunks()
        {
            TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, alignToChunk: true,
                integrity: true, chunkSize: 32, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, allowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.SeekAndWrite(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                5, TestHelper.TEST_TEXT_WRITE.Length, TestHelper.TEST_TEXT_WRITE, alignToChunk: false,
                integrity: true, chunkSize: 32, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, allowRangeWrite: true);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldAuthenticateNegative()
        {
            string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
            SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
            bool wrongPassword = false;
            SalmonFile rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            rootDir.ListFiles();
            try
            {
                SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_FALSE_PASSWORD);
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
            SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
            bool wrongPassword = false;
            SalmonDriveManager.CloseDrive();
            SalmonDriveManager.OpenDrive(vaultDir);
            try
            {
                SalmonFile RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
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
            SalmonDriveManager.CreateDrive(vaultDir, TestHelper.TEST_PASSWORD);
            bool wrongPassword = false;
            SalmonDriveManager.CloseDrive();

            try
            {
                SalmonDriveManager.OpenDrive(vaultDir);
                SalmonDriveManager.GetDrive().Authenticate(TestHelper.TEST_PASSWORD);
                SalmonFile RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: false, bitflip: true, 24 + 10, shouldBeEqual: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 24 + 10, shouldBeEqual: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 24 + 10, shouldBeEqual: false,
                overrideApplyFileIntegrity: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: false, shouldBeEqual: false,
                overrideApplyFileIntegrity: false, overrideVerifyFileIntegrity: true);
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, flipPosition: 36, shouldBeEqual: false,
                overrideApplyFileIntegrity: true, overrideVerifyFileIntegrity: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: false, shouldBeEqual: true,
                overrideApplyFileIntegrity: true, overrideVerifyFileIntegrity: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 20, shouldBeEqual: false);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                integrityFailed = true;
            }
            Assert.IsTrue(integrityFailed);
        }

        [TestMethod]
        public void ShouldImportAndExportIntegrity()
        {
            bool importSuccess = false;
            try
            {
                TestHelper.ImportAndExport(TestHelper.GenerateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
TestHelper.ENC_IMPORT_BUFFER_SIZE, TestHelper.ENC_IMPORT_THREADS, TestHelper.ENC_EXPORT_BUFFER_SIZE, TestHelper.ENC_EXPORT_THREADS,
                integrity: true);
                importSuccess = true;
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                importSuccess = false;
            }
            Assert.IsTrue(importSuccess);

        }


        [TestMethod]
        public void ShouldCatchVaultMaxFiles()
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));

            string vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
            string seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILENAME1;
            TestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                    TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

            // we need 2 nonces once of the filename the other for the file
            // so this should fail
            vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
            seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILENAME1;
            TestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                    TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

            vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
            seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILENAME1;
            TestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

            vaultDir = TestHelper.GenerateFolder(TEST_VAULT2_DIR);
            seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILENAME1;
            TestHelper.TestMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                    TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
        }

        [TestMethod]
        public void ShouldCreateFileWithoutVault()
        {
            TestHelper.ShouldCreateFileWithoutVault(text: TestHelper.TEST_TEXT, key: TestHelper.TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: true, chunkSize: 64, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES,
                filenameNonce: TestHelper.TEST_FILENAME_NONCE_BYTES, fileNonce: TestHelper.TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR);
        }

        [TestMethod]
        public void ShouldCatchCTROverflow()
        {
            bool caught = false;
            try
            {
                TestHelper.TestCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                caught = true;
            }
            Assert.IsTrue(caught);
        }


        [TestMethod]
        public void ShouldHoldCTRValue()
        {
            bool caught = false;
            try
            {
                TestHelper.TestCounterValue(TestHelper.TEST_TEXT, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, TestHelper.MAX_ENC_COUNTER - 1L);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                caught = true;
            }
            Assert.IsFalse(caught);
        }

        [TestMethod]
        public void ShouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught()
        {

            bool caught = false;
            try
            {
                TestHelper.ShouldCreateFileWithoutVault(text: TestHelper.TEST_TEXT, key: TestHelper.TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: true, chunkSize: 64, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES,
                filenameNonce: TestHelper.TEST_FILENAME_NONCE_BYTES, fileNonce: TestHelper.TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR,
                flipBit: true, flipPosition: 45);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }


        [TestMethod]
        public void ShouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught()
        {

            bool caught = false;
            bool failed = false;
            try
            {
                TestHelper.ShouldCreateFileWithoutVault(text: TestHelper.TEST_TEXT, key: TestHelper.TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: false, chunkSize: 64, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES,
                filenameNonce: TestHelper.TEST_FILENAME_NONCE_BYTES, fileNonce: TestHelper.TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR,
                flipBit: true, flipPosition: 45);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
                caught = true;
            }
            catch (Exception)
            {
                failed = true;
            }
            Assert.IsFalse(caught);
            Assert.IsFalse(failed);
        }



        [TestMethod]
        public void ShouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual()
        {

            bool caught = false;
            bool failed = false;
            try
            {
                TestHelper.ShouldCreateFileWithoutVault(text: TestHelper.TEST_TEXT, key: TestHelper.TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: false, chunkSize: 64, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES,
                filenameNonce: TestHelper.TEST_FILENAME_NONCE_BYTES, fileNonce: TestHelper.TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR,
                flipBit: true, flipPosition: 24 + 32 + 5);
            }
            catch (SalmonIntegrity.SalmonIntegrityException)
            {
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
        public void ShouldCalcHMac256()
        {
            byte[]
            bytes = System.Text.UTF8Encoding.UTF8.GetBytes(TestHelper.TEST_TEXT);
            byte[]
            hmac = SalmonIntegrity.CalculateHMAC(bytes, 0, bytes.Length, TestHelper.TEST_HMAC_KEY_BYTES, null);
            for (int i = 0; i < hmac.Length; i++)
                Console.Write(hmac[i].ToString("x2") + " ");
            Console.WriteLine();
        }


        [TestMethod]
        public void ShouldConvert()
        {
            int num1 = 12564;
            byte[] bytes = BitConverter.ToBytes(num1, 4);
            int num2 = (int)BitConverter.ToLong(bytes, 0, 4);
            Assert.AreEqual(num1, num2);

            long lnum1 = 1256445783493L;
            bytes = BitConverter.ToBytes(lnum1, 8);
            long lnum2 = BitConverter.ToLong(bytes, 0, 8);
            Assert.AreEqual(lnum1, lnum2);



        }


        [TestMethod]
        public void ShouldExportAndImportAuth()
        {
            string vault = TestHelper.GenerateFolder(TEST_VAULT_DIR);
            string importFilePath = TEST_IMPORT_TINY_FILE;
            TestHelper.ExportAndImportAuth(vault, importFilePath);
        }


        [TestMethod]
        public void TestExamples()
        {
            TestHelper.TestExamples();
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtypStream()
        {
            byte[] data = TestHelper.GetRealFileContents(TEST_IMPORT_FILE);
            TestHelper.EncryptAndDecryptStream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptStreamPerformanceDef()
        {
            Console.WriteLine("SalmonStream Def");
            SalmonStream.SetProviderType(SalmonStream.ProviderType.Default);
            TestHelper.EncryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE);
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptStreamPerformanceDefParallel()
        {
            Console.WriteLine("SalmonStream Def");
            SalmonStream.SetProviderType(SalmonStream.ProviderType.Default);
            TestHelper.EncryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE, threads: 4);        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptStreamPerformanceIntr()
        {
            Console.WriteLine("SalmonStream Intr");
            SalmonStream.SetProviderType(SalmonStream.ProviderType.AesIntrinsics);
            TestHelper.EncryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE);
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptStreamPerformanceIntrParallel()
        {
            Console.WriteLine("SalmonStream Intr");
            SalmonStream.SetProviderType(SalmonStream.ProviderType.AesIntrinsics);
            TestHelper.EncryptAndDecryptByteArray(TestHelper.TEST_PERF_SIZE, threads: 4);
        }


        [TestMethod]
        public void ShouldEncryptAndDecrtyptStreamPerformanceDefault()
        {
            Console.WriteLine("Default");
            TestHelper.EncryptAndDecryptByteArrayDef(TestHelper.TEST_PERF_SIZE);
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptArrayMultipleThreads()
        {
            byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024);
            //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = SalmonEncryptor.Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads: 2);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = SalmonEncryptor.Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false, threads: 2);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrity()
        {
            SalmonEncryptor.SetBufferSize(2 * 1024 * 1024);
            byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024 + 3);
            //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = SalmonEncryptor.Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 
                false, threads: 2, integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = SalmonEncryptor.Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, 
                false, threads: 2, integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrityCustomChunkSize()
        {
            byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024);
            //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = SalmonEncryptor.Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, threads: 2, integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = SalmonEncryptor.Decrypt(encData, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                false, threads: 2, integrity: true, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }

        [TestMethod]
        public void ShouldEncryptAndDecrtyptArrayMultipleThreadsIntegrityCustomChunkSizeStoreHeader()
        {
            byte[] data = TestHelper.GetRandArray(1 * 1024 * 1024);
            //byte[] data = Encoding.UTF8.GetBytes("This is another test that is better");
            long t1 = SalmonTime.CurrentTimeMillis();
            byte[] encData = SalmonEncryptor.Encrypt(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,
                storeHeaderData: true, threads: 2, integrity: false, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES, chunkSize: 32);
            long t2 = SalmonTime.CurrentTimeMillis();
            byte[] decData = SalmonEncryptor.Decrypt(encData, TestHelper.TEST_KEY_BYTES, hasHeaderData: true,
                threads: 1, hmacKey: TestHelper.TEST_HMAC_KEY_BYTES);
            long t3 = SalmonTime.CurrentTimeMillis();
            CollectionAssert.AreEqual(data, decData);
            Console.WriteLine("enc time: " + (t2 - t1));
            Console.WriteLine("dec time: " + (t3 - t2));
        }


        [TestMethod]
        public void ShouldCopyMemory()
        {
            TestHelper.CopyMemory(4 * 1024 * 1024);
        }
    }
}
