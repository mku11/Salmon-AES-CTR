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
import com.mku.salmon.SalmonDrive;
import com.mku.salmon.drive.JavaHttpDrive;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_SMALL_FILE;

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
        // SalmonStream.setAesProviderType(ProviderType.AesIntrinsics);
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
    void shouldReadFromRealFileTiny() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_TINY_FILENAME);
    }

    @Test
    void shouldReadFromRealFileSmall() throws NoSuchAlgorithmException, IOException {
        SalmonFSTestHelper.shouldReadFile(SalmonFSTestHelper.HTTP_VAULT_DIR, SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
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
}
