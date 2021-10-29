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
using System.Text;
using Salmon.Test.Utils;
using Salmon.FS;
using Salmon.Net.FS;
using Salmon.Streams;
using static Salmon.SalmonIntegrity;
using static Salmon.Streams.SalmonStream;
using System.Reflection;

namespace Salmon.Test
{
    [TestClass]
    public class SalmonTestRunner
    {
        private static readonly int ENC_IMPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        private static readonly int ENC_IMPORT_THREADS = 4;
        private static readonly int ENC_EXPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        private static readonly int ENC_EXPORT_THREADS = 4;

        private static readonly int TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        private static readonly int TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;

        private static readonly string TEST_OUTPUT_DIR = @"e:\\tmp\output";
        private static readonly string TEST_VAULT_DIR = @"e:\\tmp\output\enc";
        private static readonly string TEST_VAULT2_DIR = @"e:\\tmp\output\enc2";
        
        private static readonly string TEST_IMPORT_TINY_FILE = @"e:\\tmp\testdata\tiny_test.txt";
        private static readonly string TEST_IMPORT_SMALL_FILE = @"e:\\tmp\testdata\small_test.zip";
        private static readonly string TEST_IMPORT_MEDIUM_FILE = @"e:\\tmp\testdata\medium_test.zip";
        private static readonly string TEST_IMPORT_LARGE_FILE = @"e:\\tmp\testdata\large_test.mp4";
        private static readonly string TEST_IMPORT_HUGE_FILE = @"e:\\tmp\testdata\huge.zip";
        private static readonly string TEST_IMPORT_FILE = TEST_IMPORT_SMALL_FILE;

        private static readonly string TEST_PASSWORD = @"test123";
        private static readonly string TEST_FALSE_PASSWORD = @"falsepass";

        private static readonly uint MAX_ENC_COUNTER = 4294967295;

        static string TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
        static string TEST_NONCE = "123456789012"; //12bytes
        static string TEST_FILENAME_NONCE = "ABCDEFGHIJKL"; //12bytes
        static string TEST_HMAC_KEY = "12345678901234561234567890123456"; //32bytes

        static string TEST_HEADER = "SOMEHEADERDATASOMEHEADER";
        static string TEST_TINY_TEXT = "test.txt";
        static string TEST_TEXT = "This is another test that could be very long if used correct.";
        static string TEST_TEXT_WRITE = "THIS*TEXT*IS*NOW*OVERWRITTEN*WITH*THIS";

        static byte[] TEST_KEY_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_KEY);
        static byte[] TEST_NONCE_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_NONCE);
        static byte[] TEST_FILENAME_NONCE_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_FILENAME_NONCE);
        static byte[] TEST_HMAC_KEY_BYTES = System.Text.UTF8Encoding.Default.GetBytes(TEST_HMAC_KEY);

        // 12 byte nonce ready to overflow if a new file is imported
        private static readonly byte[] TEXT_VAULT_MAX_FILE_NONCE = { 
            0xFF,0xFF,0xFF,0x7F, // vault file counter, incremented for each file created under virtual drive
            0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA // random part of nonce, unique for every virtual drive
        };

        [TestMethod]
        public void ShouldEncryptAndDecryptText()
        {
            string plainText = TEST_TINY_TEXT;

            string encText = SalmonTextEncryptor.EncryptString(plainText, TEST_KEY_BYTES, TEST_NONCE_BYTES);
            string decText = SalmonTextEncryptor.DecryptString(encText, TEST_KEY_BYTES);
            Assert.AreEqual(plainText, decText);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamNoBuffersSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamLargeBuffersAlignSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, TEST_ENC_BUFFER_SIZE, TEST_DEC_BUFFER_SIZE);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamLargeBuffersNoAlignSpecified()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, TEST_ENC_BUFFER_SIZE + 3, TEST_DEC_BUFFER_SIZE + 3);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 16 * 2, 16 * 2);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamDecNoAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                encBufferSize: 16 * 2, decBufferSize: 16 * 2 + 3);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamEncNoAlignedBuffer()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                encBufferSize: 16 * 2 + 3, decBufferSize: 16 * 2);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                TEST_ENC_BUFFER_SIZE, TEST_ENC_BUFFER_SIZE,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityWithHeaderNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, header: TEST_HEADER,
                maxTextLength: 64
                );
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityWithChunksSpecifiedWithHeaderNoBufferSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, header: TEST_HEADER,
                chunkSize: 128
                );
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                TEST_ENC_BUFFER_SIZE, TEST_ENC_BUFFER_SIZE,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, chunkSize: 32);
        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedBufferSmallerAlignedPositive()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
            128, 128,
            testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, chunkSize: 64);

        }

        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedEncBufferNotAlignedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, chunkSize: 32);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 16 * 2 + 3, 16 * 2,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES);
            }
            catch (SalmonIntegrityException ex)
            {
                caught = true;
            }
            Assert.IsTrue(caught);


        }


        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksSpecifiedDecBufferNotAligned()
        {
            TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
            testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, chunkSize: 32);
        }


        [TestMethod]
        public void ShouldEncryptDecryptUsingStreamTestIntegrityMultipleChunksNoBufferSpecifiedDecBufferNotAlignedNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 16 * 2, 16 * 2 + 3,
                testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                    TEST_ENC_BUFFER_SIZE, TEST_ENC_BUFFER_SIZE,
                    testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, flipBits: true);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.EncryptWriteDecryptRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                    TEST_ENC_BUFFER_SIZE, TEST_ENC_BUFFER_SIZE,
                    testIntegrity: true, hmacKey: TEST_HMAC_KEY_BYTES, chunkSize: 32, flipBits: true);
            }
            catch (SalmonIntegrityException ex)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldNotReadFromStreamEncryptionMode()
        {
            string testText = TEST_TEXT;
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
            SalmonStream encWriter = new SalmonStream(TEST_KEY_BYTES, TEST_NONCE_BYTES, EncryptionMode.Encrypt, outs);
            try
            {
                encWriter.CopyTo(outs);
            }
            catch (Exception ex)
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
            string testText = TEST_TEXT;
            // very long test
            StringBuilder tBuilder = new StringBuilder();
            for (int i = 0; i < 10; i++)
            {
                tBuilder.Append(testText);
            }
            string plainText = tBuilder.ToString();
            bool caught = false;
            byte[] inputBytes = System.Text.UTF8Encoding.Default.GetBytes(plainText);
            byte[] encBytes = TestHelper.Encrypt(inputBytes, TEST_KEY_BYTES, TEST_NONCE_BYTES, TEST_ENC_BUFFER_SIZE);

            MemoryStream ins = new MemoryStream(encBytes);
            MemoryStream outs = new MemoryStream();
            SalmonStream encWriter = new SalmonStream(TEST_KEY_BYTES, TEST_NONCE_BYTES, EncryptionMode.Decrypt, ins);
            try
            {
                ins.CopyTo(encWriter);
            }
            catch (Exception ex)
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
            TestHelper.SeekAndRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES);
        }


        [TestMethod]
        public void ShouldSeekAndTestBlockAndCounter()
        {
            TestHelper.SeekTestCounterAndBlock(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES);
        }


        [TestMethod]
        public void ShouldSeekAndReadWithIntegrity()
        {
            TestHelper.SeekAndRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, integrity: true, hmacKey: TEST_HMAC_KEY_BYTES);

        }
        [TestMethod]
        public void ShouldSeekAndReadWithIntegrityMultiChunks()
        {
            TestHelper.SeekAndRead(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, integrity: true, chunkSize: 32, hmacKey: TEST_HMAC_KEY_BYTES);
        }

        [TestMethod]
        public void ShouldSeekAndWriteNoIntegrity()
        {
            TestHelper.SeekAndWrite(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 5, TEST_TEXT_WRITE.Length, TEST_TEXT_WRITE, setAllowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteNoIntegrityNoAllowRangeWriteNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.SeekAndWrite(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, 5, TEST_TEXT_WRITE.Length, TEST_TEXT_WRITE, setAllowRangeWrite: false);
            }
            catch (SalmonSecurityException ex)
            {
                caught = true;
            }
            Assert.IsTrue(caught);

        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityAligned()
        {
            TestHelper.SeekAndWrite(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                5, TEST_TEXT_WRITE.Length, TEST_TEXT_WRITE, alignToChunk: true,
                integrity: true, hmacKey: TEST_HMAC_KEY_BYTES, setAllowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityAlignedMultiChunks()
        {
            TestHelper.SeekAndWrite(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                5, TEST_TEXT_WRITE.Length, TEST_TEXT_WRITE, alignToChunk: true,
                integrity: true, chunkSize: 32, hmacKey: TEST_HMAC_KEY_BYTES, setAllowRangeWrite: true);
        }

        [TestMethod]
        public void ShouldSeekAndWriteWithIntegrityNotAlignedMultiChunksNegative()
        {
            bool caught = false;
            try
            {
                TestHelper.SeekAndWrite(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES,
                5, TEST_TEXT_WRITE.Length, TEST_TEXT_WRITE, alignToChunk: false,
                integrity: true, chunkSize: 32, hmacKey: TEST_HMAC_KEY_BYTES, setAllowRangeWrite: true);
            }
            catch (SalmonIntegrityException ex)
            {
                caught = true;
            }
            Assert.IsTrue(caught);
        }

        [TestMethod]
        public void ShouldAuthenticateNegative()
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            SalmonDriveManager.SetDriveLocation(TestHelper.GenerateFolder(TEST_VAULT2_DIR));
            SalmonFile RootDir;
            bool wrongPassword = false;
            if (!SalmonDriveManager.GetDrive().HasConfig())
            {
                SalmonDriveManager.GetDrive().SetPassword(TEST_PASSWORD);
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            }
            try
            {
                SalmonDriveManager.GetDrive().Authenticate(TEST_FALSE_PASSWORD);
            }
            catch (SalmonAuthException ex)
            {
                wrongPassword = true;
            }
            Assert.IsTrue(wrongPassword);
        }

        [TestMethod]
        public void ShouldCatchNotAuthenticatedNegative()
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            SalmonDriveManager.SetDriveLocation(TestHelper.GenerateFolder(TEST_VAULT2_DIR));
            SalmonFile RootDir;
            bool wrongPassword = false;
            SalmonDriveManager.GetDrive().SetPassword(TEST_PASSWORD);

            // log out 
            SalmonDriveManager.GetDrive().Authenticate(null);

            try
            {
                // access but not authenticated
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            }
            catch (SalmonAuthException ex)
            {
                wrongPassword = true;
            }
            Assert.IsTrue(wrongPassword);

        }

        [TestMethod]
        public void ShouldAuthenticatePositive()
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            SalmonDriveManager.SetDriveLocation(TestHelper.GenerateFolder(TEST_VAULT2_DIR));
            SalmonFile RootDir;
            bool wrongPassword = false;
            SalmonDriveManager.GetDrive().SetPassword(TEST_PASSWORD);

            // log out 
            SalmonDriveManager.GetDrive().Authenticate(null);

            try
            {
                // log back in
                SalmonDriveManager.GetDrive().Authenticate(TEST_PASSWORD);
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
            }
            catch (SalmonAuthException ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: false, bitflip: true, 24 + 10, shouldBeEqual: false);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: false);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 24 + 10, shouldBeEqual: false);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 24 + 10, shouldBeEqual: false, 
                overrideApplyFileIntegrity: false);
            }
            catch (SalmonIntegrityException ex)
            {
                integrityFailed = true;
            }
            catch (Exception ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: false, shouldBeEqual: false,
                overrideApplyFileIntegrity: false, overrideVerifyFileIntegrity: true);
            }
            catch (Exception ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, flipPosition: 36, shouldBeEqual: false,
                overrideApplyFileIntegrity: true, overrideVerifyFileIntegrity: false);
            }
            catch (Exception ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                    ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: false, shouldBeEqual: true,
                overrideApplyFileIntegrity: true, overrideVerifyFileIntegrity: false);
            }
            catch (Exception ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true, bitflip: true, 20, shouldBeEqual: false);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS,
                integrity: true);
                importSuccess = true;
            }
            catch (Exception ex)
            {
                importSuccess = false;
            }
            Assert.IsTrue(importSuccess);

        }


        [TestMethod]
        public void ShouldCatchVaultMaxFiles()
        {
            bool importSuccess = false;
            try
            {
                TestHelper.ImportAndExport(TEST_VAULT2_DIR, TEST_PASSWORD, TEST_IMPORT_FILE,
                ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS, ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS, 
                vaultNonce: TEXT_VAULT_MAX_FILE_NONCE);
                importSuccess = true;
            }
            catch (Exception ex)
            {
                importSuccess = false;
                Console.WriteLine(ex);
            }
            Assert.IsFalse(importSuccess);
        }

        [TestMethod]
        public void ShouldCreateFileWithoutVault()
        {
            TestHelper.ShouldCreateFileWithoutVault(text: TEST_TEXT, key: TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: true, chunkSize: 64, hmacKey: TEST_HMAC_KEY_BYTES,
                filenameNonce: TEST_FILENAME_NONCE_BYTES, fileNonce: TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR);
        }

        [TestMethod]
        public void ShouldCatchCTROverflow()
        {
            bool caught = false;
            try
            {
                TestHelper.TestCounterValue(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, MAX_ENC_COUNTER + 1L);
            } catch (Exception ex)
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
                TestHelper.TestCounterValue(TEST_TEXT, TEST_KEY_BYTES, TEST_NONCE_BYTES, MAX_ENC_COUNTER);
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
                TestHelper.ShouldCreateFileWithoutVault(text: TEST_TEXT, key: TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: true, chunkSize: 64, hmacKey: TEST_HMAC_KEY_BYTES,
                filenameNonce: TEST_FILENAME_NONCE_BYTES, fileNonce: TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR, 
                flipBit: true, flipPosition: 45);
            }
            catch (SalmonIntegrityException ex)
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
                TestHelper.ShouldCreateFileWithoutVault(text: TEST_TEXT, key: TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: false, chunkSize: 64, hmacKey: TEST_HMAC_KEY_BYTES,
                filenameNonce: TEST_FILENAME_NONCE_BYTES, fileNonce: TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR,
                flipBit: true, flipPosition: 45);
            }
            catch (SalmonIntegrityException ex)
            {
                caught = true;
            }
            catch (Exception ex)
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
                TestHelper.ShouldCreateFileWithoutVault(text: TEST_TEXT, key: TEST_KEY_BYTES,
                applyIntegrity: true, verifyIntegrity: false, chunkSize: 64, hmacKey: TEST_HMAC_KEY_BYTES,
                filenameNonce: TEST_FILENAME_NONCE_BYTES, fileNonce: TEST_NONCE_BYTES, outputDir: TEST_OUTPUT_DIR,
                flipBit: true, flipPosition: 24 + 32 + 5);
            }
            catch (SalmonIntegrityException ex)
            {
                caught = true;
            }
            catch (Exception ex)
            {
                failed = true;
            }
            Assert.IsFalse(caught);
            Assert.IsTrue(failed);
        }
    }
}
