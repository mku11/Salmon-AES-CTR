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

import com.mku.android.convert.Base64;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.android.salmon.sequence.AndroidSequenceSerializer;
import com.mku.salmon.encode.Base64Utils;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class SalmonFSAndroidTests {
    private MainActivity activity;
    private static SalmonFSTests salmonFSTests;
    private boolean initialized;

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

    static {
        if (System.getProperty("WS_SERVER_URL") == null
                || System.getProperty("WS_SERVER_URL").equals("")) {
            System.setProperty("WS_SERVER_URL", "http://192.168.1.4:8080");
        }
    }

    @BeforeClass
    public static void beforeClass() {
        SalmonFSTestHelper.sequenceSerializer = new AndroidSequenceSerializer();
        SalmonFSTestHelper.ENABLE_FILE_PROGRESS = true;

        salmonFSTests = new SalmonFSTests();
    }

    @AfterClass
    public static void afterAll() {
        SalmonFSTestHelper.close();
        SalmonCoreTestHelper.close();
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

        TestMode testMode = System.getProperty("TEST_MODE") != null && !System.getProperty("TEST_MODE").equals("") ?
                TestMode.valueOf(System.getProperty("TEST_MODE")) : TestMode.Local;
        int threads = System.getProperty("ENC_THREADS") != null && !System.getProperty("ENC_THREADS").equals("") ?
                Integer.parseInt(System.getProperty("ENC_THREADS")) : 1;

        SalmonFSAndroidTestHelper.setTestParams(activity, testDir, testMode);

        System.out.println("testDir: " + testDir);
        System.out.println("testMode: " + testMode);
        System.out.println("threads: " + threads);
        System.out.println("ws server url: " + SalmonFSTestHelper.WS_SERVER_URL);

        SalmonFSTestHelper.TEST_IMPORT_FILE = SalmonFSTestHelper.TEST_IMPORT_MEDIUM_FILE;

        // SalmonCoreTestHelper.TEST_ENC_BUFFER_SIZE = 1 * 1024 * 1024;
        // SalmonCoreTestHelper.TEST_DEC_BUFFER_SIZE = 1 * 1024 * 1024;
        SalmonCoreTestHelper.TEST_ENC_THREADS = threads;
        SalmonCoreTestHelper.TEST_DEC_THREADS = threads;

        SalmonFSTestHelper.ENC_IMPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_IMPORT_THREADS = threads;
        SalmonFSTestHelper.ENC_EXPORT_BUFFER_SIZE = 512 * 1024;
        SalmonFSTestHelper.ENC_EXPORT_THREADS = threads;

        SalmonFSTestHelper.TEST_FILE_INPUT_STREAM_THREADS = 2;
        SalmonFSTestHelper.TEST_USE_FILE_INPUT_STREAM = false;

        SalmonCoreTestHelper.initialize();
        SalmonFSTestHelper.initialize();

        // for remote drive make sure you turn on the web service either manually
        // or start the test case from gradle:
        // gradlew.bat :salmon-ws:test --tests "com.mku.salmon.ws.fs.service.test.SalmonWSTests.testStartServer" --rerun-tasks -i

        // use the native library
        ProviderType providerType = ProviderType.Default;
        String aesProviderType = System.getProperty("AES_PROVIDER_TYPE");
        if (aesProviderType != null && !aesProviderType.equals(""))
            providerType = ProviderType.valueOf(aesProviderType);
        System.out.println("ProviderType: " + providerType);

        AesStream.setAesProviderType(providerType);

        // setup for devices that do not support Java's Base64
        // Base64Utils.setBase64(new Base64());
    }

    @Test
    public void shouldCatchNotAuthorizeNegative() throws Exception {
        salmonFSTests.shouldCatchNotAuthorizeNegative();
    }

    @Test
    public void shouldAuthorizePositive() throws Exception {
        salmonFSTests.shouldAuthorizePositive();
    }

    @Test
    public void shouldImportAndExportNoIntegrityBitFlipDataNoCatch() {
        salmonFSTests.shouldImportAndExportNoIntegrityBitFlipDataNoCatch();
    }

    @Test
    public void shouldImportAndExportNoIntegrity() throws Exception {
        salmonFSTests.shouldImportAndExportNoIntegrity();
    }

    @Test
    public void shouldImportAndSearchFiles() throws Exception {
        salmonFSTests.shouldImportAndSearchFiles();
    }

    @Test
    public void shouldImportAndCopyFile() throws Exception {
        salmonFSTests.shouldImportAndCopyFile();
    }

    @Test
    public void shouldImportAndMoveFile() throws Exception {
        salmonFSTests.shouldImportAndMoveFile();
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipData() throws Exception {
        salmonFSTests.shouldImportAndExportIntegrityBitFlipData();
    }

    @Test
    public void shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch() {
        salmonFSTests.shouldImportAndExportNoAppliedIntegrityBitFlipDataShouldNotCatch();
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch() throws Exception {
        salmonFSTests.shouldImportAndExportAppliedIntegrityNoVerifyIntegrityBitFlipDataShouldNotCatch();
    }

    @Test
    public void shouldImportAndExportAppliedIntegrityNoVerifyIntegrity() throws Exception {
        salmonFSTests.shouldImportAndExportAppliedIntegrityNoVerifyIntegrity();
    }

    @Test
    public void shouldImportAndExportIntegrityBitFlipHeader() throws Exception {
        salmonFSTests.shouldImportAndExportIntegrityBitFlipHeader();
    }

    @Test
    public void shouldImportAndExportIntegrity() throws Exception {
        salmonFSTests.shouldImportAndExportIntegrity();
    }

    @Test
    public void shouldCatchVaultMaxFiles() {
        salmonFSTests.shouldCatchVaultMaxFiles();
    }

    @Test
    public void shouldCreateFileWithoutVault() throws Exception {
        salmonFSTests.shouldCreateFileWithoutVault();
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught() throws Exception {
        salmonFSTests.shouldCreateFileWithoutVaultApplyAndVerifyIntegrityFlipCaught();
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught() {
        salmonFSTests.shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipHMACNotCaught();
    }

    @Test
    public void shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual() throws Exception {
        salmonFSTests.shouldCreateFileWithoutVaultApplyIntegrityNoVerifyIntegrityFlipDataNotCaughtNotEqual();
    }

    @Test
    public void shouldExportAndImportAuth() throws Exception {
        salmonFSTests.shouldExportAndImportAuth();
    }

    @Test
    public void shouldEncryptAndDecryptStream() throws Exception {
        salmonFSTests.shouldEncryptAndDecryptStream();
    }

    @Test
    public void ShouldEncryptAndReadFileInputStream() throws Exception {
        salmonFSTests.ShouldEncryptAndReadFileInputStream();
    }

    @Test
    public void shouldCreateDriveAndOpenFsFolder() throws Exception {
        salmonFSTests.shouldCreateDriveAndOpenFsFolder();
    }

    @Test
    public void shouldCreateFileSequencer() throws IOException {
        salmonFSTests.shouldCreateFileSequencer();
    }

    @Test
    public void ShouldPerformOperationsRealFiles() throws IOException {
        salmonFSTests.ShouldPerformOperationsRealFiles();
    }

    @Test
    public void ShouldReadFromFileMultithreaded() throws Exception {
        salmonFSTests.ShouldReadFromFileMultithreaded();
    }

    @Test
    public void testRawTextFile() throws IOException {
        salmonFSTests.testRawTextFile();
    }


    @Test
    public void testRawFile() throws IOException {
        salmonFSTests.testRawFile();
    }

    @Test
    public void testEncDecFile() throws IOException {
        salmonFSTests.testEncDecFile();
    }
}