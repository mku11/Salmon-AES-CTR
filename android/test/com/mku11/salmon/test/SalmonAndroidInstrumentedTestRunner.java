package com.mku11.salmon.test;

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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static com.mku11.salmon.test.AndroidTestHelper.sleep;
import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.mku11.salmon.main.SalmonActivity;
import com.mku11.salmon.SalmonEncryptor;
import com.mku11.salmon.streams.SalmonStream;
import com.mku11.salmonfs.SalmonFile;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;

@RunWith(AndroidJUnit4.class)
public class SalmonAndroidInstrumentedTestRunner {
    private static final String TEST_VAULT = "/home/vv";
    private static final String TEST_PASSWORD = "test";
    private static final String TEST_DIR = "dir";
    private static final String TEST_IMPORT_FILE1 = "/home/testfiles/test1.mp4";
    private static final String TEST_IMPORT_FILE2 = "/home/testfiles/test2.mp4";
    private static final String TEST_SUBDIR = "subdir";
    private static final String TEST_NEW_DIR = "newdir";


    @Test
    public void useAppContext() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.mku11.salmon", appContext.getPackageName());
    }


    @Rule
    public ActivityScenarioRule<SalmonActivity> activityScenarioRule =
            new ActivityScenarioRule<>(SalmonActivity.class);

    @Test
    public void copyFiles() throws Exception {
        final Activity[] activity = {null};
        activityScenarioRule.getScenario().onActivity(act -> {
            activity[0] = act;
        });
        onView(isRoot()).perform(sleep(1000));
        AndroidTestHelper.changeVault(TEST_VAULT);
        SalmonFile rootDir = AndroidTestHelper.login(TEST_VAULT, TEST_PASSWORD);
        AndroidTestHelper.testCopy(activity[0], rootDir, TEST_DIR, TEST_IMPORT_FILE1, TEST_SUBDIR, TEST_IMPORT_FILE2, TEST_NEW_DIR, false);
        activity[0].finish();
    }

    @Test
    public void ShouldEncryptAndDecryptTextCompatible() throws Exception {
        final Activity[] activity = {null};
        activityScenarioRule.getScenario().onActivity(act -> {
            activity[0] = act;
        });
        onView(isRoot()).perform(sleep(1000));
        String plainText = TestHelper.TEST_TEXT;
        for(int i=0; i<8; i++)
            plainText += plainText;
        SalmonStream.setProviderType(SalmonStream.ProviderType.AesIntrinsics);
        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = TestHelper.defaultAESCTRTransform(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = TestHelper.defaultAESCTRTransform(encBytesDef, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES, false);
        Assert.assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = SalmonEncryptor.encrypt(bytes, TestHelper.TEST_KEY_BYTES, TestHelper.TEST_NONCE_BYTES,false);
        Assert.assertArrayEquals(encBytesDef, encBytes);
        activity[0].finish();
    }

}