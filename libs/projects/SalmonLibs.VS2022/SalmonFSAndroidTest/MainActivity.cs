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

using Uri = Android.Net.Uri;

using System;
using Android.App;
using Android.Content;
using Android.OS;
using Android.Preferences;
using Android.Provider;
using Android.Widget;
using AndroidX.DocumentFile.Provider;
using Mku.Android.FS.File;
using Mku.FS.File;
using System.Threading.Tasks;
using System.Threading;
using SalmonFSAndroidTest;
using Android.Util;
using Android.Content.PM;
using Mku.Salmon.Transform;
using Mku.Android.Salmon.Transform;

namespace Mku.Salmon.Test;

[Activity(Label = "@string/app_name", MainLauncher = true, Theme = "@style/AppTheme", 
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
public class MainActivity : Activity
{
    private static readonly int REQUEST_OPEN_FOLDER = 1000;
    private static readonly string TAG = "SalmonFSAndroidTest";

    private Button testFolder;
    private TextView output;
    private ISharedPreferences prefs;

    static MainActivity()
    {
        if (System.Environment.GetEnvironmentVariable("HTTP_SERVER_URL") == null
                || System.Environment.GetEnvironmentVariable("HTTP_SERVER_URL").Equals(""))
        {
            System.Environment.SetEnvironmentVariable("HTTP_SERVER_URL", "http://192.168.1.4");
        }

        if (System.Environment.GetEnvironmentVariable("WS_SERVER_URL") == null
                || System.Environment.GetEnvironmentVariable("WS_SERVER_URL").Equals(""))
        {
            System.Environment.SetEnvironmentVariable("WS_SERVER_URL", "http://192.168.1.4:8080");
        }
    }

    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        prefs = PreferenceManager.GetDefaultSharedPreferences(ApplicationContext);
        SetContentView(Resource.Layout.activity_main);
        testFolder = (Button)FindViewById(Resource.Id.TEST_FOLDER_BUTTON);
		
		NativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidFileSystem.Initialize(this);
		
        testFolder.Click += (s, e) =>
        {
            OpenFilesystem(this, REQUEST_OPEN_FOLDER);
        };
        output = (TextView)FindViewById(Resource.Id.OUTPUT_TEXT);
        Task.Run(() => {
            Thread.Sleep(1000);

            System.Environment.SetEnvironmentVariable("TEST_MODE", "Local");
            RunFSTests();

            System.Environment.SetEnvironmentVariable("TEST_MODE", "WebService");
            RunFSTests();

            RunFSHttpTests();
        });
    }

    private void Print(string msg)
    {
        Log.Debug(TAG, msg);
        RunOnUiThread(() =>
        {
            output.Append(msg + "\n");
        });
    }

    protected void RunFSTests()
    {
        SalmonFSAndroidTests salmonFSAndroidTests = new SalmonFSAndroidTests();
        salmonFSAndroidTests.Activity = this;
        salmonFSAndroidTests.BeforeAll(Print);
        salmonFSAndroidTests.Run(Print);
        salmonFSAndroidTests.AfterAll();
    }

    protected void RunFSHttpTests()
    {
        SalmonFSHttpAndroidTests salmonFSHttpAndroidTests = new SalmonFSHttpAndroidTests();
        salmonFSHttpAndroidTests.Activity = this;
        salmonFSHttpAndroidTests.BeforeAll(Print);
        salmonFSHttpAndroidTests.Run((msg) =>
        {
            Log.Debug(TAG, msg);
            RunOnUiThread(() =>
            {
                output.Append(msg + "\n");
            });
        });
        salmonFSHttpAndroidTests.AfterAll();
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Uri uri = data.Data;
        if (requestCode == REQUEST_OPEN_FOLDER)
        {
            SetUriPermissions(data, uri);
            IFile file = GetFile(uri.ToString(), true);
            SetVaultLocation(file.Path);
        }
    }

    public IFile GetFile(String filepath, bool isDirectory)
    {
        IFile file;
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.FromTreeUri(this, Uri.Parse(filepath));
        else
            docFile = DocumentFile.FromSingleUri(this, Uri.Parse(filepath));
        file = new AndroidFile(docFile);
        return file;
    }

    public static void OpenFilesystem(Activity activity, int resultCode)
    {
        Intent intent = new Intent(Intent.ActionOpenDocumentTree);
        intent.AddFlags(ActivityFlags.GrantPersistableUriPermission);
        intent.AddFlags(ActivityFlags.GrantReadUriPermission);
        intent.AddFlags(ActivityFlags.GrantWriteUriPermission);
        string prompt = "Open Directory";
        intent.PutExtra(DocumentsContract.ExtraPrompt, prompt);
        intent.PutExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.PutExtra(Intent.ExtraLocalOnly, true);
        try
        {
            activity.StartActivityForResult(intent, resultCode);
        }
        catch (Exception e)
        {
            Toast.MakeText(activity, "Could not start file picker: " + e.Message, ToastLength.Long).Show();
        }
    }

    public void SetUriPermissions(Intent data, Uri uri)
    {
        ActivityFlags takeFlags = 0;
        if (data != null)
            takeFlags = data.Flags;
        takeFlags &= (ActivityFlags.GrantReadUriPermission | ActivityFlags.GrantWriteUriPermission);

        try
        {
            GrantUriPermission(PackageName, uri, ActivityFlags.GrantReadUriPermission);
            GrantUriPermission(PackageName, uri, ActivityFlags.GrantWriteUriPermission);
            GrantUriPermission(PackageName, uri, ActivityFlags.GrantPersistableUriPermission);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            string err = "Could not grant uri perms to activity: " + ex;
            Toast.MakeText(this, err, ToastLength.Long).Show();
        }

        try
        {
            ContentResolver.TakePersistableUriPermission(uri, takeFlags);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            string err = "Could not take Persistable perms: " + ex;
            Toast.MakeText(this, err, ToastLength.Long).Show();
        }
    }

    public string GetVaultLocation()
    {
        return prefs.GetString("vaultLocation", null);
    }

    public void SetVaultLocation(String value)
    {
        ISharedPreferencesEditor editor = prefs.Edit();
        editor.PutString("vaultLocation", value);
        editor.Apply();
    }
}