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


import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.android.salmon.sequence.AndroidSequenceSerializer;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

// make sure you run the java tests first to generate the test files
// also all random content of test files should be generated with random seed zero
// so they will match with the HTTP test server
@RunWith(AndroidJUnit4.class)
public class SalmonFSHttpAndroidTests {
    private MainActivity activity;
    private static SalmonFSHttpTests salmonFSHttpTests;
    private boolean initialized;

    static TestMode oldTestMode = null;

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    static {
        if (System.getProperty("HTTP_SERVER_URL") == null
                || System.getProperty("HTTP_SERVER_URL").equals("")) {
            System.setProperty("HTTP_SERVER_URL", "http://192.168.1.4:8000");
        }
    }

    @BeforeClass
    public static void beforeClass() {
        SalmonFSTestHelper.sequenceSerializer = new AndroidSequenceSerializer();
        SalmonFSTestHelper.ENABLE_FILE_PROGRESS = true;

        salmonFSHttpTests = new SalmonFSHttpTests();
    }


    @Before
    public synchronized void beforeEach() throws Exception {
        activity = SalmonFSAndroidTestHelper.getMainActivity(activityScenarioRule);

        if(initialized)
            return;
        initialized = true;

        AndroidFileSystem.initialize(activity);
        String testDir = SalmonFSAndroidTestHelper.getTestDir(activity);
        if (testDir == null)
            throw new RuntimeException("Set a test dir in the device before running tests");

        SalmonFSHttpTests.oldTestMode = SalmonFSTestHelper.currTestMode;

        // use TestMode: Http only
		TestMode testMode = TestMode.Http;
		int threads = System.getProperty("ENC_THREADS") != null && !System.getProperty("ENC_THREADS").equals("") ?
			Integer.parseInt(System.getProperty("ENC_THREADS")) : 1;

        SalmonFSAndroidTestHelper.setTestParams(activity, testDir, testMode);
		
		System.out.println("testDir: " + testDir);
        System.out.println("testMode: " + testMode);
		System.out.println("threads: " + threads);
        System.out.println("http server url: " + SalmonFSTestHelper.HTTP_SERVER_URL);
        System.out.println("HTTP_VAULT_DIR_URL: " + SalmonFSTestHelper.HTTP_VAULT_DIR_URL);

        SalmonFSTestHelper.TEST_HTTP_FILE = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE;

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
		if(aesProviderType != null && !aesProviderType.equals(""))
			providerType = ProviderType.valueOf(aesProviderType);
		System.out.println("ProviderType: " + providerType);
		
        AesStream.setAesProviderType(providerType);
    }

    @AfterClass
    public static void afterAll() throws Exception {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
        SalmonFSTestHelper.setTestParams(SalmonFSTestHelper.TEST_ROOT_DIR.getPath(), oldTestMode);
    }


    @Test
    public void shouldCatchNotAuthorizeNegative() {
        salmonFSHttpTests.shouldCatchNotAuthorizeNegative();
    }

    @Test
    public void shouldAuthorizePositive() throws IOException {
        salmonFSHttpTests.shouldAuthorizePositive();
    }

    @Test
    public void shouldReadFromFileTiny() throws NoSuchAlgorithmException, IOException {
        salmonFSHttpTests.shouldReadFromFileTiny();
    }

    @Test
    public void shouldReadFromFileSmall() throws NoSuchAlgorithmException, IOException {
        salmonFSHttpTests.shouldReadFromFileSmall();
    }

    @Test
    public void shouldReadFromFileMedium() throws NoSuchAlgorithmException, IOException {
        salmonFSHttpTests.shouldReadFromFileMedium();
    }

    @Test
    public void shouldReadFromFileLarge() throws NoSuchAlgorithmException, IOException {
        salmonFSHttpTests.shouldReadFromFileLarge();
    }

    @Test
    public void shouldSeekAndReadEncryptedFileStreamFromDrive() throws IOException {
        salmonFSHttpTests.shouldSeekAndReadEncryptedFileStreamFromDrive();
    }

    @Test
    public void shouldListFilesFromDrive() throws IOException {
        salmonFSHttpTests.shouldListFilesFromDrive();
    }


    @Test
    public void ShouldExportFileFromDrive() throws Exception {
        salmonFSHttpTests.ShouldExportFileFromDrive();
    }

    @Test
    public void ShouldReadRawFile() throws IOException, NoSuchAlgorithmException {
        salmonFSHttpTests.ShouldReadRawFile();
    }
}
