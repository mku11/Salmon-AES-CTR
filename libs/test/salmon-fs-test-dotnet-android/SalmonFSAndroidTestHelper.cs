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

using Uri = Android.Net.Uri;
using Android.Content;
using Android.Widget;
using AndroidX.DocumentFile.Provider;
using Mku.Android.FS.File;
using Mku.Android.SalmonFS.Drive;
using Mku.FS.File;
using Mku.SalmonFS.Drive;
using System;
using Java.Lang;

namespace Mku.Salmon.Test;

public class SalmonFSAndroidTestHelper {
    // copy the test files to the device before running

    public static void SetTestParams(Context context, string testDir,
                                     TestMode testMode) {
        // overwrite with android implementation
        if (testMode == TestMode.Local) {
            SalmonFSTestHelper.DriveClassType = typeof(AndroidDrive);
        } else if (testMode == TestMode.Http) {
            SalmonFSTestHelper.DriveClassType = typeof(HttpDrive);
        } else if (testMode == TestMode.WebService) {
            SalmonFSTestHelper.DriveClassType = typeof(WSDrive);
        }

        SalmonFSTestHelper.TEST_ROOT_DIR = GetFile(testDir, true);
        if (!SalmonFSTestHelper.TEST_ROOT_DIR.Exists)
            SalmonFSTestHelper.TEST_ROOT_DIR.Mkdir();

        Console.WriteLine("setting android test path: " + SalmonFSTestHelper.TEST_ROOT_DIR.DisplayPath);

        SalmonFSTestHelper.TEST_INPUT_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_INPUT_DIRNAME);
        if (testMode == TestMode.WebService)
            SalmonFSTestHelper.TEST_OUTPUT_DIR = new WSFile("/", SalmonFSTestHelper.WS_SERVER_URL, SalmonFSTestHelper.credentials);
        else
            SalmonFSTestHelper.TEST_OUTPUT_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_OUTPUT_DIRNAME);
        SalmonFSTestHelper.WS_TEST_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.WS_TEST_DIRNAME);
        SalmonFSTestHelper.HTTP_TEST_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.HTTP_TEST_DIRNAME);
        SalmonFSTestHelper.TEST_SEQ_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_SEQ_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_AUTH_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_AUTH_DIRNAME);
        SalmonFSTestHelper.TEST_EXPORT_DIR = SalmonFSTestHelper.CreateDir(SalmonFSTestHelper.TEST_ROOT_DIR, SalmonFSTestHelper.TEST_EXPORT_DIRNAME);
        SalmonFSTestHelper.HTTP_VAULT_DIR = new HttpFile(SalmonFSTestHelper.HTTP_VAULT_DIR_URL, SalmonFSTestHelper.httpCredentials);
        SalmonFSTestHelper.CreateTestFiles();
        SalmonFSTestHelper.CreateHttpFiles();

        HttpSyncClient.AllowClearTextTraffic = true; // only for testing purposes
    }

    public static IFile GetFile(string filepath, bool isDirectory) {
        IFile file;
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.FromTreeUri(AndroidFileSystem.GetContext(), Uri.Parse(filepath));
        else
            docFile = DocumentFile.FromSingleUri(AndroidFileSystem.GetContext(), Uri.Parse(filepath));
        file = new AndroidFile(docFile);
        return file;
    }

    public static string GetTestDir(MainActivity activity) {
        string testDir = activity.GetVaultLocation();
        if (testDir == null) {
            activity.RunOnUiThread(delegate {
                Toast.MakeText(activity, "Set a test folder before running tests", ToastLength.Long).Show();
            });
            Thread.Sleep(60000);
            return null;
        }
        return testDir;
    }
}
