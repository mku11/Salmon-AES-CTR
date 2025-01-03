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
import com.mku.salmon.drive.JavaDrive;
import com.mku.file.JavaFile;
import com.mku.salmon.drive.JavaWSDrive;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.MemoryStream;
import com.mku.salmon.SalmonAuthException;
import com.mku.salmon.integrity.SalmonIntegrity;
import com.mku.integrity.IntegrityException;
import com.mku.salmon.*;
import com.mku.salmon.streams.SalmonFileInputStream;
import com.mku.salmon.utils.SalmonFileCommander;
import com.mku.sequence.INonceSequenceSerializer;
import com.mku.salmon.sequence.SalmonFileSequencer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonFSTests {
    @BeforeAll
    static void beforeAll() throws Exception {
		SalmonFSTestHelper.setOutputDir(System.getProperty("testDir"));
		
        // SalmonStream.setAesProviderType(ProviderType.AesGPU);
        // local drive
        SalmonFSTestHelper.setDriveClassType(JavaDrive.class);

        // remote drive
        // make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks
        // SalmonFSTestHelper.setDriveClassType(JavaWSDrive.class);

		//SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
		//SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
    }
	
    @BeforeEach
    void beforeEach() {
        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 2;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 2;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();
    }

    @AfterEach
    void afterEach() {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
    }

    @Test
    public void shouldCatchNotAuthorizeNegative() throws Exception {
        IRealFile vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer(
                new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1),
                SalmonFSTestHelper.getSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.createDrive(vaultDir, JavaDrive.class,
                SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        boolean wrongPassword = false;
        drive.close();
        try {
            drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, sequencer);
            SalmonFile rootDir = drive.getRoot();
            rootDir.listFiles();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }
        assertTrue(wrongPassword);
    }

    @Test
    public void shouldAuthorizePositive() throws Exception {
        IRealFile vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer(
                new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1),
                SalmonFSTestHelper.getSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.createDrive(vaultDir, JavaDrive.class,
                SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        boolean wrongPassword = false;
        drive.close();
        try {
            drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType,
                    SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            SalmonFile virtualRoot = drive.getRoot();
        } catch (SalmonAuthException ex) {
            wrongPassword = true;
        }

        assertFalse(wrongPassword);
    }

    @Test
    public void shouldImportAndExportNoIntegrityBitFlipDataNoCatch() throws Exception {
        boolean integrityFailed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 24 + 10, false, false, false);
        } catch (Exception ex) {
            integrityFailed = true;
        }
        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndExportNoIntegrity() throws Exception {
        boolean integrityFailed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    false, 0, true, false,
                    false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
            else
                throw ex;
        }

        assertFalse(integrityFailed);
    }

    @Test
    public void shouldImportAndSearchFiles() throws Exception {
        SalmonFSTestHelper.importAndSearch(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE);
    }

    @Test
    public void shouldImportAndCopyFile() throws Exception {
        boolean failed = false;
        try {
            SalmonFSTestHelper.importAndCopy(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", false);
        } catch (Exception ex) {
            ex.printStackTrace();
            failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndMoveFile() throws Exception {
        boolean failed = false;
        try {
            SalmonFSTestHelper.importAndCopy(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR), SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE, SalmonFSTestHelper.ENC_IMPORT_THREADS, "subdir", true);
        } catch (Exception ex) {
            ex.printStackTrace();
            failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipData() throws Exception {
        boolean integrityFailed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 24 + 10, false, true, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        }

        assertTrue(integrityFailed);
    }

    @Test
    public void shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch() {
        boolean integrityFailed = false;
        boolean failed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 24 + 10, false, false, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        } catch (Exception ex) {
            failed = true;
        }

        assertFalse(integrityFailed);

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch() throws Exception {
        boolean failed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 36, false,
                    true, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrity() throws Exception {
        boolean failed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    false, 0, true,
                    true, false);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                failed = true;
        }

        assertFalse(failed);
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipHeader() throws Exception {
        boolean integrityFailed = false;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    true, 20, false,
                    true, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                integrityFailed = true;
        }

        assertTrue(integrityFailed);
    }

    @Test
    public void shouldImportAndExportIntegrity() throws Exception {
        boolean importSuccess = true;
        try {
            SalmonFSTestHelper.importAndExport(SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR),
                    SalmonCoreTestHelper.TEST_PASSWORD, SalmonFSTestHelper.TEST_IMPORT_FILE,
                    false, 0, true,
                    true, true);
        } catch (IOException ex) {
            ex.printStackTrace();
            importSuccess = false;
        }
        assertTrue(importSuccess);
    }

    @Test
    public void shouldCatchVaultMaxFiles() {
        IRealFile vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        IRealFile seqFile = vaultDir.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);

        SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -2, true);

        // we need 2 nonces once of the filename the other for the file
        // so this should fail
        vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, -1, false);

        vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 0, false);

        vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        seqFile = vaultDir.getChild(SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        SalmonFSTestHelper.testMaxFiles(vaultDir, seqFile, SalmonFSTestHelper.TEST_IMPORT_TINY_FILE,
                SalmonCoreTestHelper.TEXT_VAULT_MAX_FILE_NONCE, 1, false);
    }

    @Test
    public void shouldCreateFileWithoutVault() throws Exception {
        SalmonFSTestHelper.shouldCreateFileWithoutVault(SalmonCoreTestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR, false, -1, true);
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught() throws Exception {

        boolean caught = false;
        try {
            SalmonFSTestHelper.shouldCreateFileWithoutVault(SalmonCoreTestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR,
                    true, 45, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        }

        assertTrue(caught);
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught() {
        String text = SalmonCoreTestHelper.TEST_TEXT;
        for (int i = 0; i < text.length(); i++) {
            boolean caught = false;
            boolean failed = false;
            try {
                SalmonFSTestHelper.shouldCreateFileWithoutVault(text.getBytes(Charset.defaultCharset()), SalmonCoreTestHelper.TEST_KEY_BYTES,
                        true, false, 64,
                        SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES, SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                        SalmonFSTestHelper.TEST_OUTPUT_DIR, true, i, false);
            } catch (IOException ex) {
                if (ex.getCause() instanceof IntegrityException)
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
            SalmonFSTestHelper.shouldCreateFileWithoutVault(SalmonCoreTestHelper.TEST_TEXT.getBytes(Charset.defaultCharset()), SalmonCoreTestHelper.TEST_KEY_BYTES,
                    true, false, 64,
                    SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                    SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES,
                    SalmonFSTestHelper.TEST_OUTPUT_DIR,
                    true, 24 + 32 + 5, true);
        } catch (IOException ex) {
            if (ex.getCause() instanceof IntegrityException)
                caught = true;
        } catch (Error ex) {
            failed = true;
        }

        assertFalse(caught);

        assertTrue(failed);
    }

    @Test
    public void shouldExportAndImportAuth() throws Exception {
        IRealFile vault = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT_DIR);
        String importFilePath = SalmonFSTestHelper.TEST_IMPORT_TINY_FILE;
        SalmonFSTestHelper.exportAndImportAuth(vault, importFilePath);
    }

    @Test
    public void testExamples() throws Exception {
        SalmonFSTestHelper.testExamples();
    }

    @Test
    public void shouldEncryptAndDecryptStream() throws Exception {
        byte[] data = SalmonFSTestHelper.getRealFileContents(SalmonFSTestHelper.TEST_IMPORT_FILE);
        SalmonFSTestHelper.encryptAndDecryptStream(data, SalmonCoreTestHelper.TEST_KEY_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES);
    }

    @Test
    public void ShouldEncryptAndReadFileInputStream() throws Exception {
        byte[] data = new byte[256];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        SalmonFile file = SalmonFSTestHelper.shouldCreateFileWithoutVault(data, SalmonCoreTestHelper.TEST_KEY_BYTES,
                true, true, 64, SalmonCoreTestHelper.TEST_HMAC_KEY_BYTES,
                SalmonCoreTestHelper.TEST_FILENAME_NONCE_BYTES, SalmonCoreTestHelper.TEST_NONCE_BYTES, SalmonFSTestHelper.TEST_OUTPUT_DIR,
                false, -1, true);
        SalmonFileInputStream fileInputStream = new SalmonFileInputStream(file,
                3, 50, 2, 12);

        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 0, 32, 0, 32);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 220, 8, 2, 8);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 100, 2, 0, 2);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 6, 16, 0, 16);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 50, 40, 0, 40);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 124, 50, 0, 50);
        SalmonFSTestHelper.seekAndReadFileInputStream(data, fileInputStream, 250, 10, 0, 6);
    }

    @Test
    public void shouldCreateDriveAndOpenFsFolder() throws Exception {
        IRealFile vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        JavaFile sequenceFile = new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1);
        INonceSequenceSerializer serializer = SalmonFSTestHelper.getSequenceSerializer();
        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer(sequenceFile, serializer);
        SalmonDrive drive = SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        boolean wrongPassword = false;
        SalmonFile rootDir = drive.getRoot();
        rootDir.listFiles();
        drive.close();

        // reopen but open the fs folder instead it should still login
        try {
            drive = SalmonFSTestHelper.openDrive(vaultDir.getChild("fs"), SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
            assertTrue(drive.hasConfig());
        } catch (SalmonAuthException ignored) {
            wrongPassword = true;
        }

        assertFalse(wrongPassword);
    }

    @Test
    public void shouldCreateWinFileSequencer() throws IOException {
        SalmonFSTestHelper.shouldTestFileSequencer();
    }

    @Test
    public void ShouldPerformOperationsRealFiles() throws IOException {
        boolean caught = false;
        IRealFile dir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        JavaFile file = new JavaFile(SalmonFSTestHelper.TEST_IMPORT_TINY_FILE);
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
            rfile.copyRecursively(folder2);
        assertEquals(4, dir.getChild("folder1").getChildrenCount());
        assertEquals(3, dir.getChild("folder1").getChild("folder2").getChildrenCount());

        // recursive copy
        IRealFile folder3 = dir.createDirectory("folder4");
        dir.getChild("folder1").copyRecursively(folder3);
        int count1 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.getChild("folder1"));
        int count2 = SalmonFSTestHelper.GetChildrenCountRecursively(dir.getChild("folder4").getChild("folder1"));
        assertEquals(count1, count2);

        IRealFile dfile = dir.getChild("folder4").getChild("folder1").getChild("folder2").getChild(file.getBaseName());
        assertTrue(dfile.exists());
        assertTrue(dfile.delete());
        assertEquals(2, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
        dir.getChild("folder1").copyRecursively(folder3, null, IRealFile.autoRename, false, null);
        assertEquals(2, dir.getChildrenCount());
        assertEquals(1, dir.getChild("folder4").getChildrenCount());
        assertEquals(7, dir.getChild("folder4").getChild("folder1").getChildrenCount());
        assertEquals(5, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());

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
        assertEquals(5, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());

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
        assertEquals(7, dir.getChild("folder4").getChild("folder1").getChild("folder2").getChildrenCount());
    }

    @Test
    public void ShouldReadFromFileMultithreaded() throws Exception {
        IRealFile vaultDir = SalmonFSTestHelper.generateFolder(SalmonFSTestHelper.TEST_VAULT2_DIR);
        IRealFile file = new JavaFile(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE);

        SalmonFileSequencer sequencer = SalmonFSTestHelper.createSalmonFileSequencer(new JavaFile(vaultDir + "/" + SalmonFSTestHelper.TEST_SEQUENCER_FILE1), SalmonFSTestHelper.getSequenceSerializer());
        SalmonDrive drive = SalmonFSTestHelper.createDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, sequencer);
        SalmonFileCommander fileCommander = new SalmonFileCommander(SalmonIntegrity.DEFAULT_CHUNK_SIZE, SalmonIntegrity.DEFAULT_CHUNK_SIZE, 2);
        SalmonFile[] sfiles = fileCommander.importFiles(new IRealFile[]{file},
                drive.getRoot(), false, true, null, null, null);

        SalmonFileInputStream fileInputStream1 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        MessageDigest md51 = MessageDigest.getInstance("SHA-256");
        DigestInputStream dis = new DigestInputStream(fileInputStream1, md51);
        byte[] buff1 = new byte[256 * 1024];
        while (dis.read(buff1, 0, buff1.length) > -1) ;
        byte[] hash1 = dis.getMessageDigest().digest();
        dis.close();
        String h1 = BitConverter.toHex(hash1);

        SalmonFileInputStream fileInputStream2 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 1, 256 * 1024);
        MessageDigest md52 = MessageDigest.getInstance("SHA-256");
        DigestInputStream dis2 = new DigestInputStream(fileInputStream2, md51);
        byte[] buff2 = new byte[256 * 1024];
        while (dis2.read(buff2, 0, buff2.length) > -1) ;
        byte[] hash2 = dis2.getMessageDigest().digest();
        dis2.close();
        String h2 = BitConverter.toHex(hash2);
        assertEquals(h1, h2);

        long pos = Math.abs(new Random().nextLong() % file.length());

        fileInputStream1 = new SalmonFileInputStream(sfiles[0], 4, 4 * 1024 * 1024, 4, 256 * 1024);
        fileInputStream1.skip(pos);
        MemoryStream ms1 = new MemoryStream();
        SalmonFSTestHelper.copyStream(fileInputStream1, ms1);
        ms1.flush();
        ms1.setPosition(0);
        fileInputStream1.reset();
        MessageDigest m1 = MessageDigest.getInstance("SHA-256");
        InputStreamWrapper msa1 = new InputStreamWrapper(ms1);
        DigestInputStream dism1 = new DigestInputStream(msa1, m1);
        byte[] buffera1 = new byte[256 * 1024];
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
        SalmonFSTestHelper.copyStream(fileInputStream2, ms2);
        ms2.flush();
        ms2.setPosition(0);
        MessageDigest m2 = MessageDigest.getInstance("SHA-256");
        InputStreamWrapper msa2 = new InputStreamWrapper(ms2);
        DigestInputStream dism2 = new DigestInputStream(msa2, m2);
        byte[] buffera2 = new byte[256 * 1024];
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
