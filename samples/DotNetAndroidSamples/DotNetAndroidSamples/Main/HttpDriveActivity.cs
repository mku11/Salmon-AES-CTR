using AndroidX.AppCompat.App;
using Android.Content;
using Android.Widget;
using Android.OS;
using System;
using DotNetAndroidSamples;
using Google.Android.Material.TextField;
using Mku.Android.FS.File;
using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using Mku.SalmonFS.Drive;
using Mku.Salmon.Samples.Utils;
using System.Threading.Tasks;
using Android.App;
using Uri = Android.Net.Uri;
using Mku.Salmon.Transform;
using Mku.Android.Salmon.Transform;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class HttpDriveActivity : AppCompatActivity
{
    public const int REQUEST_EXPORT_FILES = 1003;
    private EditText httpURL;
    private TextInputEditText password;
    private Button openDriveButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;

    private int threads = 1;
    private static readonly string defaultPassword = "test";
    string defaultHttpDriveURL = "https://localhost/testvault";
    string httpUser = "user";
    string httpPassword = "password";

    private AesDrive httpDrive;

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

        // enable only if you're testing with an HTTP server
		// In all other cases you should be using an HTTPS server
        // HttpSyncClient.AllowClearTextTraffic = true;

        SetContentView(Resource.Layout.activity_http_drive);

        httpURL = (EditText)FindViewById(Resource.Id.HTTP_DRIVE_LOCATION);
        httpURL.Text = defaultHttpDriveURL;
        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);

        openDriveButton = (Button)FindViewById(Resource.Id.OPEN_DRIVE_BUTTON);
        openDriveButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                OpenDrive();
            });
        };

        listFilesButton = (Button)FindViewById(Resource.Id.LIST_DRIVE_BUTTON);
        listFilesButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                ListFiles();
            });
        };
        exportFilesButton = (Button)FindViewById(Resource.Id.EXPORT_FILES_BUTTON);
        exportFilesButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_EXPORT_FILES, true);
        };

        closeDriveButton = (Button)FindViewById(Resource.Id.CLOSE_DRIVE_BUTTON);
        closeDriveButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                CloseDrive();
            });
        };

        outputText = (EditText)FindViewById(Resource.Id.OUTPUT_TEXT);
        password.Text = defaultPassword;

        AesStream.AesProviderType = ProviderType.Default;

        Initialize();
    }

    public void Log(string msg)
    {
        RunOnUiThread(() =>
        {
            outputText.Append(msg + "\n");
        });
    }

    public void ClearLog()
    {
        RunOnUiThread(() =>
        {
            outputText.Text = "";
        });
    }

    private void Initialize()
    {
		AesNativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;
    }

    public void OpenDrive()
    {
        ClearLog();
        try
        {
            IFile driveDir = new HttpFile(httpURL.Text.ToString(), new Credentials(httpUser, httpPassword));
            httpDrive = DriveSample.OpenDrive(driveDir, password.Text.ToString(),
                    Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void ListFiles()
    {
        try
        {
            DriveSample.ListFiles(httpDrive, Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void ExportFiles(IFile exportDir)
    {

        try
        {
            DriveSample.ExportFiles(httpDrive, exportDir, threads, Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void CloseDrive()
    {
        DriveSample.CloseDrive(httpDrive, Log);
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Uri uri = data.Data;
        AndroidFileChooser.SetUriPermissions(this, data, uri);
        if (requestCode == REQUEST_EXPORT_FILES)
        {
            IFile exportDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
            Task.Run(() =>
            {
                ExportFiles(exportDir);
            });
        }
    }
}