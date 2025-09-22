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
using System.Threading.Tasks;
using Mku.Salmon.Transform;
using Mku.Android.Salmon.Transform;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class WebServiceDriveActivity : AppCompatActivity
{
    public const int REQUEST_IMPORT_FILES = 1002;
    public const int REQUEST_EXPORT_FILES = 1003;
    private EditText wsURL;
    private EditText wsUserName;
    private TextInputEditText wsPassword;
    private EditText drivePath;
    private TextInputEditText password;
    private Button createDriveButton;
    private Button openDriveButton;
    private Button importFilesButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;
    private AesDrive wsDrive;

    private static readonly string defaultPassword = "test123";
    private static readonly string defaultWsServicePath = "https://localhost:8443";
    private static readonly string defaultUserName = "user";
    private static readonly string defaultWsPassword = "password";
    private static readonly string defaultDrivePath = "/example_drive_" + Time.Time.CurrentTimeMillis();

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

		// enable only if you're testing with an HTTP server
		// In all other cases you should be using an HTTPS server
        // HttpSyncClient.AllowClearTextTraffic = true;

        SetContentView(Resource.Layout.activity_web_service_drive);

        wsURL = (EditText)FindViewById(Resource.Id.WEB_SERVICE_LOCATION);
        wsURL.Text = defaultWsServicePath;
        wsUserName = (EditText)FindViewById(Resource.Id.WEB_SERVICE_USER);
        wsUserName.Text = defaultUserName;
        wsPassword = (TextInputEditText)FindViewById(Resource.Id.WEB_SERVICE_PASSWORD);
        wsPassword.Text = defaultWsPassword;
        drivePath = (EditText)FindViewById(Resource.Id.DRIVE_PATH);
        drivePath.Text = defaultDrivePath;
        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);
        password.Text = defaultPassword;

        createDriveButton = (Button)FindViewById(Resource.Id.CREATE_DRIVE_BUTTON);
        createDriveButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                CreateDrive();
            });
        };

        openDriveButton = (Button)FindViewById(Resource.Id.OPEN_DRIVE_BUTTON);
        openDriveButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                OpenDrive();
            });
        };

        importFilesButton = (Button)FindViewById(Resource.Id.IMPORT_FILES_BUTTON);
        importFilesButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_IMPORT_FILES, false, true);
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

    public void CreateDrive()
    {
        ClearLog();
        try
        {
            IFile driveDir = new WSFile(drivePath.Text.ToString(),
                    wsURL.Text.ToString(),
                    new Credentials(wsUserName.Text.ToString(),
                            wsPassword.Text.ToString()));
            if (!driveDir.Exists)
            {
                driveDir.Mkdir();
            }
            wsDrive = DriveSample.CreateDrive(driveDir, password.Text.ToString(), Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void OpenDrive()
    {
        ClearLog();
        try
        {
            IFile driveDir = new WSFile(drivePath.Text.ToString(),
                    wsURL.Text.ToString(),
                    new Credentials(wsUserName.Text.ToString(),
                            wsPassword.Text.ToString()));
            wsDrive = DriveSample.OpenDrive(driveDir, password.Text.ToString(), Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void ImportFiles(IFile[] filesToImport)
    {
        try
        {
            DriveSample.ImportFiles(wsDrive, filesToImport, Log);
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
            DriveSample.ListFiles(wsDrive, Log);
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
            DriveSample.ExportFiles(wsDrive, exportDir, Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void CloseDrive()
    {
        DriveSample.CloseDrive(wsDrive, Log);
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Uri uri = data.Data;
        switch (requestCode)
        {
            case REQUEST_IMPORT_FILES:
                IFile[] filesToImport = AndroidFileChooser.GetFiles(this, data);
                Task.Run(() =>
                {
                    ImportFiles(filesToImport);
                });
                break;
            case REQUEST_EXPORT_FILES:
                AndroidFileChooser.SetUriPermissions(this, data, uri);
                IFile exportDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
                Task.Run(() =>
                {
                    ExportFiles(exportDir);
                });
                break;
        }
    }
}