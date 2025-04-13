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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.mku.android.fs.file.AndroidFile;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.android.salmonfs.drive.AndroidDrive;
import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;
import com.mku.fs.file.WSFile;
import com.mku.salmonfs.drive.HttpDrive;
import com.mku.salmonfs.drive.WSDrive;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class SalmonFSAndroidTestHelper {
    // copy the test files to the device before running

    public static void setTestParams(Context context, String testDir,
                                     TestMode testMode) throws Exception {
        // overwrite with android implementation
        if (testMode == TestMode.Local) {
            SalmonFSTestHelper.driveClassType = AndroidDrive.class;
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.driveClassType = HttpDrive.class;
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.driveClassType = WSDrive.class;
        }

        SalmonFSTestHelper.TEST_ROOT_DIR = getFile(testDir, true);
        if (!SalmonFSTestHelper.TEST_ROOT_DIR.exists())
            SalmonFSTestHelper.TEST_ROOT_DIR.mkdir();

        System.out.println("setting android test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.getDisplayPath());

        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if (testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new WSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_DIR = SalmonFSTestHelper.createDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL);
        SalmonFSTestHelper.createTestFiles();
        SalmonFSTestHelper.createHttpFiles();
    }

    public static IFile getFile(String filepath, boolean isDirectory) {
        IFile file;
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(AndroidFileSystem.getContext(), android.net.Uri.parse(filepath));
        else
            docFile = DocumentFile.fromSingleUri(AndroidFileSystem.getContext(), android.net.Uri.parse(filepath));
        file = new AndroidFile(docFile);
        return file;
    }

    public static ViewAction delay(long delay) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "delay " + delay + " ms";
            }

            @Override
            public void perform(UiController uiController, View view) {
                uiController.loopMainThreadForAtLeast(delay);
            }
        };
    }

    public static String getTestDir(MainActivity activity) throws InterruptedException {
        String testDir = activity.getVaultLocation();
        if (testDir == null) {
            activity.getMainExecutor().execute(() -> {
                Toast.makeText(activity, "Set a test folder before running tests", Toast.LENGTH_LONG).show();
            });
            Thread.sleep(60000);
            return null;
        }
        return testDir;
    }

    public static MainActivity getMainActivity(ActivityScenarioRule<MainActivity> activityScenarioRule) {
        final MainActivity[] activityHolder = {null};
        activityScenarioRule.getScenario().onActivity(act -> {
            activityHolder[0] = act;
        });
        onView(isRoot()).perform(delay(1000));
        return activityHolder[0];
    }

    static class StringMatcher extends BaseMatcher<String> {
        String pattern;

        public StringMatcher(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(Object actual) {
            return ((String) actual).endsWith(pattern);
        }

        @Override
        public void describeTo(Description description) {

        }
    }
}
