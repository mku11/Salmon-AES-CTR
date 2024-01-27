package com.mku.salmon.test;

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

import com.mku.convert.BitConverter;
import com.mku.file.IRealFile;
import com.mku.file.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.io.InputStreamWrapper;
import com.mku.io.MemoryStream;
import com.mku.salmon.SalmonDefaultOptions;
import com.mku.salmon.SalmonRangeExceededException;
import com.mku.salmon.SalmonSecurityException;
import com.mku.salmon.integrity.SalmonIntegrityException;
import com.mku.salmonfs.*;
import com.mku.sequence.ISalmonSequenceSerializer;
import com.mku.sequence.SalmonFileSequencer;
import com.mku.sequence.SalmonSequenceException;
import com.mku.sequence.SalmonSequenceSerializer;
import com.mku.utils.SalmonFileCommander;
import com.mku.utils.SalmonFileExporter;
import com.mku.utils.SalmonFileImporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonFSJavaTestRunner extends SalmonJavaTestRunner {

    static {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
    }

    @Test
    public void shouldAuthenticateNegative() throws Exception {
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        try {
            SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_FALSE_PASSWORD);
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }

        assertTrue(wrongPassword);
    }

    @Test
    public void shouldCatchNotAuthenticatedNegative() throws Exception {
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonDriveManager.closeDrive();
        try {
            SalmonDriveManager.openDrive(vaultDir);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }

        assertTrue(wrongPassword);

    }

    @Test
    public void shouldAuthenticatePositive() throws Exception {
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonDriveManager.closeDrive();
        try {
            SalmonDriveManager.openDrive(vaultDir);
            SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
            SalmonFile virtualRoot = SalmonDriveManager.getDrive().getVirtualRoot();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }

        assertFalse(wrongPassword);
    }

    @Test
    public void shouldImportAndExportNoIntegrityBitFlipDataNoCatch() throws Exception {
        boolean integrityFailed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    false, true, 24 + 10, true, false, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndExportNoIntegrity() throws Exception {
        boolean integrityFailed = false;
        try {
            SalmonFileImporter.setEnableLog(true);
            SalmonFileImporter.setEnableLogDetails(true);
            SalmonFileExporter.setEnableLog(true);
            SalmonFileExporter.setEnableLogDetails(true);
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    false, false, 0, true, false,
                    false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndSearchFiles() throws Exception {
        JavaFSTestHelper.importAndSearch(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS);
    }

    @Test
    public void shouldImportAndCopyFile() throws Exception {
        boolean integrityFailed = false;
        try {
            JavaFSTestHelper.importAndCopy(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, "subdir", false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndMoveFile() throws Exception {
        boolean integrityFailed = false;
        try {
            JavaFSTestHelper.importAndCopy(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, "subdir", true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipData() throws Exception {
        boolean integrityFailed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, true, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertTrue(integrityFailed);
    }

    @Test
    public void shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch() {
        boolean integrityFailed = false;
        boolean failed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 24 + 10, false, false, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        } catch (Exception ex) {
            failed = true;
        }

        assertFalse(integrityFailed);

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportNoAppliedIntegrityYesVerifyIntegrityNoBitFlipDataShouldCatch() {
        boolean failed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, false,
                    false, true);
        } catch (Exception ex) {
            failed = true;
        }

        assertTrue(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch() throws Exception {
        boolean failed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 36, false,
                    true, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrity() throws Exception {
        boolean failed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipHeader() throws Exception {
        boolean integrityFailed = false;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS, JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, true, 20, false,
                    true, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                integrityFailed = true;
        }

        assertTrue(integrityFailed);
    }

    @Test
    public void shouldImportAndExportIntegrity() throws Exception {
        boolean importSuccess = true;
        try {
            JavaFSTestHelper.importAndExport(TestHelper.generateFolder(TEST_VAULT2_DIR), TestHelper.TEST_PASSWORD, TEST_IMPORT_FILE,
                    JavaFSTestHelper.ENC_IMPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_IMPORT_THREADS,
                    JavaFSTestHelper.ENC_EXPORT_BUFFER_SIZE, JavaFSTestHelper.ENC_EXPORT_THREADS,
                    true, false, 0, true,
                    true, true);
        } catch (IOException ex) {
            ex.printStackTrace();
            if (ex.getCause() instanceof SalmonIntegrityException)
                importSuccess = false;
        }
        assertTrue(importSuccess);
    }

    @Test
    public void shouldCatchVaultMaxFiles() {
        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);

        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        String seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;

        JavaFSTestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        JavaFSTestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        JavaFSTestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        seqFile = vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1;
        JavaFSTestHelper.testMaxFiles(vaultDir, seqFile, TEST_IMPORT_TINY_FILE,
                TestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    @Test
    public void shouldCreateFileWithoutVault() throws Exception {
        JavaFSTestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR, false, -1, true);
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught() throws Exception {

        boolean caught = false;
        try {
            JavaFSTestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), TestHelper.TEST_KEY_BYTES,
                    true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR,
                    true, 45, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught() {
        String text = TestHelper.TEST_TEXT;
        for (int i = 0; i < text.length(); i++) {
            boolean caught = false;
            boolean failed = false;
            try {
                JavaFSTestHelper.shouldCreateFileWithoutVault(text.getBytes(Charset.defaultCharset()), TestHelper.TEST_KEY_BYTES,
                        true, false, 64,
                        TestHelper.TEST_HMAC_KEY_BYTES, TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                        TEST_OUTPUT_DIR, true, i, false);
            } catch (IOException ex) {
                if (ex.getCause() instanceof SalmonIntegrityException)
                    caught = true;
            } catch (Exception ex) {
                ex.printStackTrace();
                failed = true;
            }
            assertFalse(caught);
            assertFalse(failed);
        }
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual() throws Exception {

        boolean caught = false;
        boolean failed = false;
        try {
            JavaFSTestHelper.shouldCreateFileWithoutVault(TestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), TestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    TestHelper.TEST_HMAC_KEY_BYTES,
                    TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES,
                    TEST_OUTPUT_DIR,
                    true, 24 + 32 + 5, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof SalmonIntegrityException)
                caught = true;
        } catch (Error ex) {
            failed = true;
        }

        assertFalse(caught);

        assertTrue(failed);
    }

    @Test
    public void shouldExportAndImportAuth() throws Exception {
        String vault = TestHelper.generateFolder(TEST_VAULT_DIR);
        String importFilePath = TEST_IMPORT_TINY_FILE;
        JavaFSTestHelper.exportAndImportAuth(vault, importFilePath);
    }

    @Test
    public void testExamples() throws Exception {
        JavaFSTestHelper.testExamples();
    }

    @Test
    public void shouldEncryptAndDecryptStream() throws Exception {
        byte[] data = JavaFSTestHelper.getRealFileContents(TEST_IMPORT_FILE);
        JavaFSTestHelper.encryptAndDecryptStream(data, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES);
    }

    @Test
    public void ShouldEncryptAndReadFileInputStream() throws Exception {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        SalmonFile file = JavaFSTestHelper.shouldCreateFileWithoutVault(data, TestHelper.TEST_KEY_BYTES,
                true, true, 64, TestHelper.TEST_HMAC_KEY_BYTES,
                TestHelper.TEST_FILENAME_NONCE_BYTES, TestHelper.TEST_NONCE_BYTES, TEST_OUTPUT_DIR,
                false, -1, true);
        SalmonFileInputStream fileInputStream = new SalmonFileInputStream(file,
                3, 50, 2, 12);

        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        JavaFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
    }

    @Test
    public void shouldCreateDriveAndOpenFsFolder() throws Exception {
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        JavaFile sequenceFile = new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1);
        ISalmonSequenceSerializer serializer = new SalmonSequenceSerializer();
        SalmonFileSequencer sequencer = new SalmonFileSequencer(sequenceFile, serializer);
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        boolean wrongPassword = false;
        SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        rootDir.listFiles();
        SalmonDriveManager.getDrive().close();

        // reopen but open the fs folder instead it should still login
        try {
            SalmonDrive drive = SalmonDriveManager.openDrive(vaultDir + "/fs");
            assertTrue(drive.hasConfig());
            SalmonDriveManager.getDrive().authenticate(TestHelper.TEST_PASSWORD);
        } catch (SalmonAuthException ignored) {
            wrongPassword = true;
        }

        assertFalse(wrongPassword);
    }

    @Test
    public void shouldCreateWinFileSequencer() throws SalmonSequenceException, IOException, SalmonRangeExceededException {
        JavaFSTestHelper.shouldTestFileSequencer();
    }

    @Test
    public void ShouldPerformOperationsRealFiles() throws IOException {
        boolean caught = false;
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        JavaFile dir = new JavaFile(vaultDir);
        JavaFile file = new JavaFile(TEST_IMPORT_TINY_FILE);
        IRealFile file1 = file.copy(dir);
        IRealFile file2;
        try {
            file2 = file.copy(dir);
        } catch (Exception ex) {
            System.err.println(ex);
            caught = true;
        }
        assertEquals(true, caught);
        file2 = file.copy(dir, IRealFile.autoRename.apply(file));

        assertEquals(2, dir.getChildrenCount());
        assertTrue(dir.getChild(file.getBaseName()).exists());
        assertTrue(dir.getChild(file.getBaseName()).isFile());
        assertTrue(dir.getChild(file2.getBaseName()).exists());
        assertTrue(dir.getChild(file2.getBaseName()).isFile());

        IRealFile dir1 = dir.createDirectory("folder1");
        assertTrue(dir.getChild("folder1").exists());
        assertTrue(dir.getChild("folder1").isDirectory());
        assertEquals(3, dir.getChildrenCount());

        IRealFile folder1 = dir.createDirectory("folder2");
        assertTrue(folder1.exists());
        boolean renamed = folder1.renameTo("folder3");
        assertTrue(renamed);
        assertFalse(dir.getChild("folder2").exists());
        assertTrue(dir.getChild("folder3").exists());
        assertTrue(dir.getChild("folder3").isDirectory());
        assertEquals(4, dir.getChildrenCount());
        boolean delres = dir.getChild("folder3").delete();
        assertTrue(delres);
        assertFalse(dir.getChild("folder3").exists());
        assertEquals(3, dir.getChildrenCount());

        file1.move(dir.getChild("folder1"));
        file2.move(dir.getChild("folder1"));

        IRealFile file3 = file.copy(dir);
        caught = false;
        try {
            file3.move(dir.getChild("folder1"));
        } catch (Exception ex) {
            System.err.println(ex);
            caught = true;
        }
        assertTrue(caught);
        IRealFile file4 = file3.move(dir.getChild("folder1"), IRealFile.autoRename.apply(file3));
        assertTrue(file4.exists());
        assertEquals(3, dir.getChild("folder1").getChildrenCount());

        IRealFile folder2 = dir.getChild("folder1").createDirectory("folder2");
        for (IRealFile rfile : dir.getChild("folder1").listFiles())
            rfile.copy(folder2);
        assertEquals(4, dir.getChild("folder1").getChildrenCount());
        assertEquals(4, dir.getChild("folder1").getChild("folder2").getChildrenCount());

        // recursive copy
        IRealFile folder3 = dir.createDirectory("folder4");
        dir.getChild("folder1").copyRecursively(folder3);
        int count1 = JavaFSTestHelper.GetChildrenCountRecursively(dir.getChild("folder1"));
        int count2 = JavaFSTestHelper.GetChildrenCountRecursively(dir.getChild("folder4").getChild("folder1"));
        assertEquals(count1, count2);

        IRealFile dfile = dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild(file.getBaseName());
        assertTrue(dfile.exists());
        assertTrue(dfile.delete());
        assertEquals(3, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
        dir.getChild("folder1").copyRecursively(folder3, null, IRealFile.autoRename, false, null);
        assertEquals(2, dir.getChildrenCount());
        assertEquals(1, dir.getChild("folder4").getChildrenCount());
        assertEquals(7, dir.getChild("folder4").getChild("folder1").getChildrenCount());
        assertEquals(6, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
        assertEquals(0, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild("folder2").getChildrenCount());

        dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild(file.getBaseName()).delete();
        dir.getChild("folder4").getChild("folder1").getChild(file.getBaseName()).delete();
        ArrayList<IRealFile> failed = new ArrayList<IRealFile>();
        dir.getChild("folder1").copyRecursively(folder3, null, null, false, (failedFile, ex) ->
        {
            failed.add(failedFile);
        });
        assertEquals(4, failed.size());
        assertEquals(2, dir.getChildrenCount());
        assertEquals(1, dir.getChild("folder4").getChildrenCount());
        assertEquals(7, dir.getChild("folder4").getChild("folder1").getChildrenCount());
        assertEquals(6, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
        assertEquals(0, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild("folder2").getChildrenCount());

        dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild(file.getBaseName()).delete();
        dir.getChild("folder4").getChild("folder1").getChild(file.getBaseName()).delete();
        ArrayList<IRealFile> failedmv = new ArrayList<IRealFile>();
        dir.getChild("folder1").moveRecursively(dir.getChild("folder4"), null, IRealFile.autoRename, false, (failedFile, ex) ->
        {
            failedmv.add(failedFile);
        });
        assertEquals(4, failed.size());
        assertEquals(1, dir.getChildrenCount());
        assertEquals(1, dir.getChild("folder4").getChildrenCount());
        assertEquals(9, dir.getChild("folder4").getChild("folder1").getChildrenCount());
        assertEquals(8, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
        assertEquals(0, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild("folder2").getChildrenCount());
    }

    @Test
    public void ShouldReadFromFileMultithreaded() throws Exception {
        boolean caught = false;
        String vaultDir = TestHelper.generateFolder(TEST_VAULT2_DIR);
        IRealFile file = new JavaFile(TEST_IMPORT_MEDIUM_FILE);

        SalmonDriveManager.setVirtualDriveClass(JavaDrive.class);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(new JavaFile(vaultDir + "/" + TestHelper.TEST_SEQUENCER_FILE1), new SalmonSequenceSerializer());
        SalmonDriveManager.setSequencer(sequencer);
        SalmonDrive drive = SalmonDriveManager.createDrive(vaultDir, TestHelper.TEST_PASSWORD);
        SalmonFile[] sfiles = new SalmonFileCommander(SalmonDefaultOptions.getBufferSize(), SalmonDefaultOptions.getBufferSize(), 2).importFiles(new IRealFile[]{file},
                drive.getVirtualRoot(), false, true, null, null, null);

        SalmonFileInputStream fileInputStream1 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        MessageDigest md51 = MessageDigest.getInstance("MD5");
        DigestInputStream dis = new DigestInputStream(fileInputStream1, md51);
        byte[] buff1 = new byte[SalmonDefaultOptions.getBufferSize()];
        while (dis.read(buff1, 0, buff1.length) > -1) ;
        byte[] hash1 = dis.getMessageDigest().digest();
        dis.close();
        String h1 = BitConverter.toHex(hash1);

        SalmonFileInputStream fileInputStream2 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        MessageDigest md52 = MessageDigest.getInstance("MD5");
        DigestInputStream dis2 = new DigestInputStream(fileInputStream2, md51);
        byte[] buff2 = new byte[SalmonDefaultOptions.getBufferSize()];
        while (dis2.read(buff2, 0, buff2.length) > -1) ;
        byte[] hash2 = dis2.getMessageDigest().digest();
        dis2.close();
        String h2 = BitConverter.toHex(hash2);
        assertEquals(h1, h2);

        long pos = Math.abs(new Random().nextLong() % file.length());

        fileInputStream1 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        fileInputStream1.skip(pos);
        MemoryStream ms1 = new MemoryStream();
        JavaFSTestHelper.copyStream(fileInputStream1, ms1);
        ms1.flush();
        ms1.position(0);
        fileInputStream1.reset();
        MessageDigest m1 = MessageDigest.getInstance("MD5");
        InputStreamWrapper msa1 = new InputStreamWrapper(ms1);
        DigestInputStream dism1 = new DigestInputStream(msa1, m1);
        byte[] buffera1 = new byte[SalmonDefaultOptions.getBufferSize()];
        int bytes;
        while ((bytes = dism1.read(buffera1, 0, buffera1.length)) > -1) {
            int x = bytes;
        }
        byte[] hash3 = dism1.getMessageDigest().digest();
        dism1.close();
        String h3 = BitConverter.toHex(hash3);
        fileInputStream1.close();
        ms1.close();
        msa1.close();
        dism1.close();

        fileInputStream2 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        fileInputStream2.skip(pos);
        MemoryStream ms2 = new MemoryStream();
        JavaFSTestHelper.copyStream(fileInputStream2, ms2);
        ms2.flush();
        ms2.position(0);
        MessageDigest m2 = MessageDigest.getInstance("MD5");
        InputStreamWrapper msa2 = new InputStreamWrapper(ms2);
        DigestInputStream dism2 = new DigestInputStream(msa2, m2);
        byte[] buffera2 = new byte[SalmonDefaultOptions.getBufferSize()];
        while (dism2.read(buffera2, 0, buffera2.length) > -1) ;
        byte[] hash4 = dism2.getMessageDigest().digest();
        dism2.close();
        String h4 = BitConverter.toHex(hash4);
        fileInputStream2.close();
        ms2.close();
        msa2.close();
        dism2.close();
        assertEquals(h3, h4);
    }
}
