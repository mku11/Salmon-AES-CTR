package com.mku.salmon.test;

/*
MIT License

Copyright (c) 2025 Max Kas

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

import com.mku.file.IRealFile;
import com.mku.file.IVirtualFile;
import com.mku.file.JavaHttpFile;
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.SalmonFile;
import com.mku.salmon.drive.JavaHttpDrive;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.SalmonStream;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonFSHttpTests {
    static TestMode oldTestMode = null;

    @BeforeAll
    static void beforeAll() throws Exception {
        SalmonFSHttpTests.oldTestMode = SalmonFSTestHelper.currTestMode;

        // use TestMode: Http only
        SalmonFSTestHelper.setTestParams(System.getProperty("testDir"), TestMode.Http);

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_IMPORT_LARGE_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = 2;
        SalmonCoreTestHelper.TEST_DEC_THREADS = 2;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = 2;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = 2;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks

        // use the native library
        SalmonStream.setAesProviderType(ProviderType.Default);
    }

    @AfterAll
    static void afterAll() throws Exception {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
        SalmonFSTestHelper.setTestParams(SalmonFSTestHelper.TEST_ROOT_DIR.getPath(), oldTestMode);
    }


    @Test
    void shouldCatchNotAuthorizeNegative() {
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        boolean wrongPassword = false;
        try {
            SalmonDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, null);
        } catch (Exception ex) {
            System.err.println(ex);
            wrongPassword = true;
        }
        assertTrue(wrongPassword);
    }

    @Test
    void shouldAuthorizePositive() throws IOException {
        boolean wrongPassword = false;
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        try {
            SalmonDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
            IVirtualFile root = drive.getRoot();
        } catch (Exception ex) {
            System.err.println(ex);
            wrongPassword = true;
        }

        assertFalse(wrongPassword);
    }

    @Test
    void shouldReadFromFileTiny() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
    }

    @Test
    void shouldReadFromFileSmall() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
    }

    @Test
    void shouldReadFromFileMedium() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME);
    }

    @Test
    void shouldReadFromFileLarge() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME);
    }

    @Test
    void shouldSeekAndReadEncryptedFileStreamFromDrive() throws IOException {
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        SalmonDrive drive = SalmonDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.getRoot();
        IVirtualFile encFile = root.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        assertEquals(encFile.getBaseName(), SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);

        RandomAccessStream encStream = encFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        encStream.copyTo(ms);
        byte[] data = ms.toArray();
        ms.close();
        encStream.close();
        SalmonFSTestHelper.seekAndReadHttpFile(data, encFile, true, 3, 50, 12);
    }

    @Test
    void shouldListFilesFromDrive() throws IOException {
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        SalmonDrive drive = JavaHttpDrive.open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.getRoot();
        IVirtualFile[] files = root.listFiles();
        List<String> filenames = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            String filename = files[i].getBaseName();
            filenames.add(filename);
        }
        assertEquals(files.length, 4);
        assertTrue(filenames.contains(SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME));
        assertTrue(filenames.contains(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME));
        assertTrue(filenames.contains(SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILENAME));
        assertTrue(filenames.contains(SalmonFSTestHelper.TEST_IMPORT_LARGE_FILENAME));
    }


    @Test
    public void ShouldExportFileFromDrive() throws Exception {
        IRealFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        int threads = 1;
        SalmonDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        SalmonFile file = drive.getRoot().getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getBaseName());
        IRealFile exportDir = SalmonFSTestHelper.generateFolder("export_http", SalmonFSTestHelper.TEST_OUTPUT_DIR, false);
        IRealFile localFile = exportDir.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getBaseName());
        if (localFile.exists())
            localFile.delete();
        SalmonFSTestHelper.exportFiles(new SalmonFile[]{file}, exportDir, threads);
        drive.close();
    }

    @Test
    public void ShouldReadRawFile() throws IOException, NoSuchAlgorithmException {
        IRealFile localFile = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getBaseName());
        String localChkSum = SalmonFSTestHelper.getChecksum(localFile);
        IRealFile httpRoot = new JavaHttpFile(SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        IRealFile httpFile = httpRoot.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getBaseName());
        RandomAccessStream stream = httpFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        stream.copyTo(ms);
        ms.flush();
        ms.setPosition(0);
        String digest = SalmonFSTestHelper.getChecksumStream(new ByteArrayInputStream(ms.toArray()));
        ms.close();
        stream.close();
        assertEquals(digest, localChkSum);
    }
}
