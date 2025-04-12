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

import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;
import com.mku.fs.file.IVirtualFile;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmonfs.drive.AesDrive;
import com.mku.salmonfs.drive.HttpDrive;
import com.mku.salmonfs.file.AesFile;
import com.mku.salmonfs.handler.AesStreamHandler;
import com.mku.streams.InputStreamWrapper;
import com.mku.streams.MemoryStream;
import com.mku.streams.RandomAccessStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SalmonFSHttpTests {
    static TestMode oldTestMode = null;

    @BeforeAll
    static void beforeAll() throws Exception {
        SalmonFSHttpTests.oldTestMode = SalmonFSTestHelper.currTestMode;
        String testDir = System.getProperty("TEST_DIR");
        // use TestMode: Http only
        TestMode testMode = TestMode.Http;
        int threads = System.getProperty("ENC_THREADS") != null && !System.getProperty("ENC_THREADS").equals("") ?
                Integer.parseInt(System.getProperty("ENC_THREADS")) : 1;

        SalmonFSTestHelper.setTestParams(testDir, testMode);

        System.out.println("testDir: " + testDir);
        System.out.println("testMode: " + testMode);
        System.out.println("threads: " + threads);
        System.out.println("http server url: " + SalmonFSTestHelper.HTTP_SERVER_URL);
        System.out.println("HTTP_VAULT_DIR_URL: " + SalmonFSTestHelper.HTTP_VAULT_DIR_URL);

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_HTTP_LARGE_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = threads;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = true;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks -i

        // use the native library
        ProviderType providerType = ProviderType.Default;
        String aesProviderType = System.getProperty("AES_PROVIDER_TYPE");
        System.out.println("ProviderTypeEnv: " + aesProviderType);
        if (aesProviderType != null && !aesProviderType.equals(""))
            providerType = ProviderType.valueOf(aesProviderType);
        System.out.println("ProviderType: " + providerType);

        AesStream.setAesProviderType(providerType);
    }

    @AfterAll
    static void afterAll() throws Exception {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
        SalmonFSTestHelper.setTestParams(SalmonFSTestHelper.TEST_ROOT_DIR.getPath(), oldTestMode);
    }


    @Test
    void shouldCatchNotAuthorizeNegative() {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        boolean wrongPassword = false;
        try {
            AesDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_FALSE_PASSWORD, null);
        } catch (Exception ex) {
            ex.printStackTrace();
            wrongPassword = true;
        }
        assertTrue(wrongPassword);
    }

    @Test
    void shouldAuthorizePositive() throws IOException {
        boolean wrongPassword = false;
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        try {
            AesDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
            IVirtualFile root = drive.getRoot();
        } catch (Exception ex) {
            ex.printStackTrace();
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
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        AesDrive drive = AesDrive.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD, null);
        IVirtualFile root = drive.getRoot();
        AesFile encFile = (AesFile) root.getChild(SalmonFSTestHelper.TEST_IMPORT_SMALL_FILENAME);
        assertEquals(encFile.getName(), encFile.getName());

        RandomAccessStream encStream = encFile.getInputStream();
        MemoryStream ms = new MemoryStream();
        encStream.copyTo(ms);
        byte[] data = ms.toArray();
        ms.close();
        encStream.close();
        SalmonFSTestHelper.seekAndReadHttpFile(data, encFile, 3, 50, 12);
    }

    @Test
    void shouldListFilesFromDrive() throws IOException {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        AesDrive drive = HttpDrive.open(vaultDir, SalmonCoreTestHelper.TEST_PASSWORD);
        IVirtualFile root = drive.getRoot();
        IVirtualFile[] files = root.listFiles();
        List<String> filenames = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            String filename = files[i].getName();
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
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        int threads = 1;
        AesDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        AesFile file = drive.getRoot().getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        IFile exportDir = SalmonFSTestHelper.generateFolder("export_http", SalmonFSTestHelper.TEST_EXPORT_DIR, false);
        IFile localFile = exportDir.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        if (localFile.exists())
            localFile.delete();
        SalmonFSTestHelper.exportFiles(new AesFile[]{file}, exportDir);
        drive.close();
    }

    @Test
    public void ShouldReadRawFile() throws IOException, NoSuchAlgorithmException {
        IFile localFile = SalmonFSTestHelper.HTTP_TEST_DIR.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        String localChkSum = SalmonFSTestHelper.getChecksum(localFile);
        IFile httpRoot = new HttpFile(SalmonFSTestHelper.HTTP_SERVER_VIRTUAL_URL + "/" + SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        IFile httpFile = httpRoot.getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getName());
        RandomAccessStream stream = httpFile.getInputStream();
        String digest = SalmonFSTestHelper.getChecksumStream(stream.asReadStream());
        stream.close();
        assertEquals(digest, localChkSum);
    }


    @Test
    public void testStreamHandler() throws Exception {
        IFile vaultDir = SalmonFSTestHelper.HTTP_VAULT_DIR;
        AesDrive drive = SalmonFSTestHelper.openDrive(vaultDir, SalmonFSTestHelper.driveClassType, SalmonCoreTestHelper.TEST_PASSWORD);
        AesFile file = drive.getRoot().getChild(SalmonFSTestHelper.TEST_HTTP_FILE.getName());

        String url = AesStreamHandler.getInstance().register("test123", file);
        URLConnection conn = new URL(url).openConnection();
        conn.connect();
        InputStream stream = conn.getInputStream();
        String chksum = SalmonFSTestHelper.getChecksumStream(stream);
        stream.close();

        RandomAccessStream encStream = file.getInputStream();
        InputStreamWrapper streamWrapper = new InputStreamWrapper(encStream);
        String chksum2 = SalmonFSTestHelper.getChecksumStream(streamWrapper);
        assertEquals(chksum2, chksum);
        encStream.close();

        drive.close();
    }
}
