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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertArrayEquals;

import android.app.Activity;
import android.view.View;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;

import com.mku.android.file.AndroidDrive;
import com.mku.salmon.SalmonDecryptor;
import com.mku.salmon.SalmonDefaultOptions;
import com.mku.salmon.SalmonEncryptor;
import com.mku.salmon.io.SalmonStream;
import com.mku.salmon.transform.ISalmonCTRTransformer;
import com.mku.salmon.transform.SalmonTransformerFactory;
import com.mku.salmon.vault.android.R;
import com.mku.salmon.vault.model.SalmonVaultManager;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;

import org.hamcrest.Matcher;
import org.junit.Assert;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AndroidTestHelper {
    public static String TEST_TEXT = "This is another test that could be very long if used correctly.";

    public static String TEST_KEY = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP"; // 256bit
    public static byte[] TEST_KEY_BYTES = TEST_KEY.getBytes(Charset.defaultCharset());
    public static String TEST_NONCE = "12345678"; // 8 bytes
    public static byte[] TEST_NONCE_BYTES = TEST_NONCE.getBytes(Charset.defaultCharset());

    public static void changeVault(String testVault) {
        throw new UnsupportedOperationException();
    }

    private static final Random random;

    static {
        random = new Random(System.currentTimeMillis());
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

    public static SalmonFile login(Activity activity, String vaultDir, String pass) throws Exception {
        AndroidDrive.initialize(activity);
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
        int children = getFile(new SalmonFile[]{srcSalmonFile});
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
        Assert.assertEquals(children, getFile(new SalmonFile[]{child}));
        if (delete)
            srcSalmonFile.delete();
    }

    private static SalmonFile getChild(SalmonFile currDir, String srcFile) {
        SalmonFile[] files = currDir.listFiles();
        SalmonFile f = null;
        for (SalmonFile file : files) {
            try {
                if (file.getBaseName().equals(srcFile)) {
                    f = file;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return f;
    }

    private static int getIndexFromAdapter(Activity activity, SalmonFile testFile) {
        List<SalmonFile> files = SalmonVaultManager.getInstance().getFileItemList();
        return Arrays.asList(files).indexOf(testFile);
    }

    private static int getFile(SalmonFile[] files) {
        int total = 0;
        for (SalmonFile file : files) {
            total++;
            if (file.isDirectory()) {
                total += getFile(file.listFiles());
            }
        }
        return total;
    }

    private static SalmonFile getFileFromAdapter(Activity activity, String filename) throws Exception {
        List<SalmonFile> files = SalmonVaultManager.getInstance().getFileItemList();
        for (SalmonFile file : files) {
            if (file.getBaseName().equals(filename)) {
                return file;
            }
        }
        return null;
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
        for (SalmonFile file : files) {
            try {
                if (file.getBaseName().equals(testDir))
                    found = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Assert.assertTrue(found);
    }

    public static byte[] defaultAESCTRTransform(byte[] plainText, byte[] testKeyBytes, byte[] testNonceBytes, boolean encrypt)
            throws Exception {
        if (testNonceBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(testNonceBytes, 0, tmp, 0, testNonceBytes.length);
            testNonceBytes = tmp;
        }
        SecretKeySpec key = new SecretKeySpec(testKeyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        IvParameterSpec ivSpec = new IvParameterSpec(testNonceBytes);
        // mode doesn't make a difference since the encryption is symmetrical
        if (encrypt)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        else
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] encrypted = cipher.doFinal(plainText);
        return encrypted;
    }

    public static byte[] getRandArray(int size) {
        byte[] data = new byte[size];
        random.nextBytes(data);
        return data;
    }

    public static void encryptAndDecryptByteArrayDef(int size, boolean enableLog) throws Exception {
        byte[] data = AndroidTestHelper.getRandArray(size);
        encryptAndDecryptByteArrayDef(data, enableLog);
    }

    public static void encryptAndDecryptByteArrayDef(byte[] data, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = AndroidTestHelper.defaultAESCTRTransform(data, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, true);
        long t2 = System.currentTimeMillis();
        byte[] decData = AndroidTestHelper.defaultAESCTRTransform(encData, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false);
        long t3 = System.currentTimeMillis();

        if (enableLog) {
            System.out.println("Perf enc: " + (t2 - t1));
            System.out.println("Perf dec: " + (t3 - t2));
            System.out.println("Perf Total: " + (t3 - t1));
        }
//        assertArrayEquals(data, decData);
    }

    public static void encryptAndDecryptByteArray(int size, boolean enableLog) throws Exception {
        byte[] data = AndroidTestHelper.getRandArray(size);
        encryptAndDecryptByteArray(data, 1, enableLog);
    }

    public static void encryptAndDecryptByteArray(int size, int threads, boolean enableLog) throws Exception {
        byte[] data = AndroidTestHelper.getRandArray(size);
        encryptAndDecryptByteArray(data, threads, enableLog);
    }

    public static void encryptAndDecryptByteArray(byte[] data, int threads, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = new SalmonEncryptor(threads).encrypt(data, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false);
        long t2 = System.currentTimeMillis();
        byte[] decData = new SalmonDecryptor(threads).decrypt(encData, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false);
        long t3 = System.currentTimeMillis();

        if (enableLog) {
            System.out.println("Perf enc time: " + (t2 - t1));
            System.out.println("Perf dec time: " + (t3 - t2));
            System.out.println("Perf Total: " + (t3 - t1));
        }
        //assertArrayEquals(data, decData);
    }

    public static void encryptAndDecryptByteArrayNative(int size, boolean enableLog) throws Exception {
        byte[] data = AndroidTestHelper.getRandArray(size);
        encryptAndDecryptByteArrayNative(data, enableLog);
    }

    public static void encryptAndDecryptByteArrayNative(byte[] data, boolean enableLog) throws Exception {
        long t1 = System.currentTimeMillis();
        byte[] encData = AndroidTestHelper.nativeCTRTransform(data, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, true,
                SalmonStream.getAesProviderType());
        long t2 = System.currentTimeMillis();
        byte[] decData = AndroidTestHelper.nativeCTRTransform(encData, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false,
                SalmonStream.getAesProviderType());
        long t3 = System.currentTimeMillis();

        assertArrayEquals(data, decData);
        if (enableLog) {
            System.out.println("Perf enc time: " + (t2 - t1));
            System.out.println("Perf dec time: " + (t3 - t2));
            System.out.println("Perf Total: " + (t3 - t1));
        }
    }

    public static byte[] nativeCTRTransform(byte[] input, byte[] testKeyBytes, byte[] testNonceBytes,
                                            boolean encrypt, SalmonStream.ProviderType providerType)
            throws Exception {
        if (testNonceBytes.length < 16) {
            byte[] tmp = new byte[16];
            System.arraycopy(testNonceBytes, 0, tmp, 0, testNonceBytes.length);
            testNonceBytes = tmp;
        }
        ISalmonCTRTransformer transformer = SalmonTransformerFactory.create(providerType);
        transformer.init(testKeyBytes, testNonceBytes);
        byte[] output = new byte[input.length];
        transformer.resetCounter();
        transformer.syncCounter(0);
        if (encrypt)
            transformer.encryptData(input, 0, output, 0, input.length);
        else
            transformer.decryptData(input, 0, output, 0, input.length);
        return output;
    }

    public static void EncryptAndDecryptTextCompatible() throws Exception {
        String plainText = AndroidTestHelper.TEST_TEXT;
        for (int i = 0; i < 15; i++)
            plainText += plainText;
        SalmonDefaultOptions.setBufferSize(plainText.length());

        byte[] bytes = plainText.getBytes(Charset.defaultCharset());
        byte[] encBytesDef = AndroidTestHelper.defaultAESCTRTransform(bytes,
                AndroidTestHelper.TEST_KEY_BYTES, AndroidTestHelper.TEST_NONCE_BYTES, true);
        byte[] decBytesDef = AndroidTestHelper.defaultAESCTRTransform(encBytesDef,
                AndroidTestHelper.TEST_KEY_BYTES, AndroidTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(bytes, decBytesDef);
        byte[] encBytes = new SalmonEncryptor().encrypt(bytes, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(encBytesDef, encBytes);
        byte[] decBytes = new SalmonDecryptor().decrypt(encBytes, AndroidTestHelper.TEST_KEY_BYTES,
                AndroidTestHelper.TEST_NONCE_BYTES, false);

        assertArrayEquals(bytes, decBytes);
        System.out.println("Compatible test complete");
    }
}
