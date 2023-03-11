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
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.app.Activity;
import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;

import com.mku.android.salmonvault.R;
import com.mku11.salmon.file.AndroidDrive;
import com.mku11.salmon.main.SalmonActivity;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import org.hamcrest.Matcher;
import org.junit.Assert;

import java.util.Arrays;

public class AndroidTestHelper {
    public static void changeVault(String testVault) {
        throw new UnsupportedOperationException();
    }

    public static ViewAction sleep(long delay) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @Override
            public String getDescription() {
                return "";
            }

            @Override
            public void perform(UiController uiController, View view) {
                uiController.loopMainThreadForAtLeast(delay);
            }
        };
    }

    public static SalmonFile login(String vaultDir, String pass) throws Exception {
        SalmonDriveManager.setVirtualDriveClass(AndroidDrive.class);
        SalmonDriveManager.openDrive(AndroidTestHelper.generateFolder(vaultDir));

        if (!SalmonDriveManager.getDrive().hasConfig()) {
            SalmonDriveManager.getDrive().setPassword(pass);
            SalmonFile rootDir = SalmonDriveManager.getDrive().getVirtualRoot();
            rootDir.listFiles();
        } else {
            SalmonDriveManager.getDrive().authenticate(pass);
        }

        SalmonFile salmonRootDir = SalmonDriveManager.getDrive().getVirtualRoot();
        return salmonRootDir;
    }

    private static String generateFolder(String vaultDir) {
        throw new UnsupportedOperationException();
    }

    public static void testCopy(Activity activity, SalmonFile currDir, String testDir, String testImportFile1,
                                String testSubdir, String testImportFile2, String testNewDir, boolean move) throws Exception {
        AndroidTestHelper.createDir(activity, currDir, testDir);
        AndroidTestHelper.importFile(activity, currDir, testImportFile1);
        AndroidTestHelper.createDir(activity, currDir, testSubdir);
        AndroidTestHelper.importFile(activity, currDir, testImportFile2);
        AndroidTestHelper.createDir(activity, currDir, testNewDir);
        AndroidTestHelper.copyFile(activity, currDir, testDir, testNewDir, move);


        AndroidTestHelper.deleteFile(currDir, testDir);
        AndroidTestHelper.deleteFile(currDir, testNewDir);
    }

    private static void deleteFile(SalmonFile currDir, String filename) {
        SalmonFile file = getChild(currDir, filename);
        file.delete();
        Assert.assertFalse(file.exists());
    }

    private static void copyFile(Activity activity, SalmonFile currDir, String srcFile, String destDir, boolean delete) throws Exception {
        SalmonFile srcSalmonFile = getFileFromAdapter(activity, srcFile);
        int children = getFilesRecurrsively(new SalmonFile[] {srcSalmonFile});
        Assert.assertNotNull(srcSalmonFile);
        int index = getIndexFromAdapter(activity, srcSalmonFile);
        onView(isRoot()).perform(sleep(1000));
        activity.openOptionsMenu();
        onView(ViewMatchers.withText("Multi Select")).perform(click());
        onView(withId(R.id.list))
                .perform(RecyclerViewActions.actionOnItemAtPosition(index, click()));
        activity.openOptionsMenu();
        onView(isRoot()).perform(sleep(3000));
        onView(ViewMatchers.withText("Move")).perform(click());

        SalmonFile destSalmonDir = getFileFromAdapter(activity, destDir);
        Assert.assertNotNull(destSalmonDir);
        int indexDest = getIndexFromAdapter(activity, destSalmonDir);
        onView(isRoot()).perform(sleep(1000));
        onView(withId(R.id.list))
                .perform(RecyclerViewActions.actionOnItemAtPosition(indexDest, click()));
        activity.openOptionsMenu();
        onView(ViewMatchers.withText("Paste")).perform(click());
        SalmonFile child = getChild(currDir, srcFile);
        Assert.assertNotNull(child);
        Assert.assertEquals(children, getFilesRecurrsively(new SalmonFile[]{child}));
        if(delete)
            srcSalmonFile.delete();
    }

    private static SalmonFile getChild(SalmonFile currDir, String srcFile) {
        SalmonFile[] files = currDir.listFiles();
        SalmonFile f = null;
        for(SalmonFile file : files) {
            try {
                if(file.getBaseName().equals(srcFile)) {
                    f = file;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return f;
    }

    private static int getIndexFromAdapter(Activity activity, SalmonFile testFile) {
        SalmonFile [] files = ((SalmonActivity)activity).getSalmonFiles();
        return Arrays.asList(files).indexOf(testFile);
    }

    private static int getFilesRecurrsively(SalmonFile [] files) {
        int total = 0;
        for(SalmonFile file : files) {
            total++;
            if(file.isDirectory()) {
                total += getFilesRecurrsively(file.listFiles());
            }
        }
        return total;
    }

    private static SalmonFile getFileFromAdapter(Activity activity, String testDir) throws Exception {
        SalmonFile [] files = ((SalmonActivity) activity).getSalmonFiles();
        int index = -1;
        for(int i = 0; i< files.length; i++) {
            if(files[i].getBaseName().equals(testDir)){
                index = i;
            }
        }
        SalmonFile testFile = null;
        if(index > -1){
            testFile = files[index];
        }
        return testFile;
    }

    private static void importFile(Activity activity, SalmonFile currDir, String testImportFile1) {
        throw new UnsupportedOperationException();
    }

    private static void createDir(Activity activity, SalmonFile currDir, String testDir) {
        onView(isRoot()).perform(sleep(1000));
        activity.openOptionsMenu();
        onView(isRoot()).perform(sleep(3000));
        onView(ViewMatchers.withText("New Folder")).perform(click());
        onView(ViewMatchers.withHint("Folder name")).perform(typeText(testDir));
        onView(ViewMatchers.withText("OK")).perform(click());
        onView(isRoot()).perform(sleep(3000));
        SalmonFile[] files = currDir.listFiles();
        boolean found = false;
        for(SalmonFile file : files) {
            try {
                if(file.getBaseName().equals(testDir))
                    found=true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Assert.assertTrue(found);
    }
}
