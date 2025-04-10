using Android.App;
using Android.Content;
using Android.OS;
using Android.Widget;
using AndroidX.AppCompat.App;
using DotNetAndroidSamples;
using Google.Android.Material.TextField;
using Mku.Android.FS.File;
using Mku.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Samples.Utils;
using Mku.Salmon.Streams;
using Mku.SalmonFS.Drive;
using System;
using System.Threading.Tasks;
using Uri = Android.Net.Uri;
using System.Threading.Tasks;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class LocalDriveActivity : AppCompatActivity
{
    public const int REQUEST_OPEN_DRIVE = 1000;
    public const int REQUEST_CREATE_DRIVE = 1001;
    public const int REQUEST_IMPORT_FILES = 1002;
    public const int REQUEST_EXPORT_FILES = 1003;
    private TextInputEditText password;
    private Button createDriveButton;
    private Button openDriveButton;
    private Button importFilesButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;
    private AesDrive localDrive;
    private int threads = 1;
    private static readonly string defaultPassword = "test123";

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

        SetContentView(Resource.Layout.activity_local_drive);

        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);

        createDriveButton = (Button)FindViewById(Resource.Id.CREATE_DRIVE_BUTTON);
        createDriveButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_CREATE_DRIVE, true);
        };

        openDriveButton = (Button)FindViewById(Resource.Id.OPEN_DRIVE_BUTTON);
        openDriveButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_OPEN_DRIVE, true);
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

        AndroidFileSystem.Initialize(this);
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
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;
    }

    public void CreateDrive(IFile driveDir)
    {
        ClearLog();
        try
        {
            localDrive = DriveSample.CreateDrive(driveDir, password.Text.ToString(), Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void OpenDrive(IFile dir)
    {
        ClearLog();
        try
        {
            localDrive = DriveSample.OpenDrive(dir, password.Text.ToString(), Log);
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
            DriveSample.ImportFiles(localDrive, filesToImport, threads, Log);
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
            DriveSample.ListFiles(localDrive, Log);
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
            DriveSample.ExportFiles(localDrive, exportDir, threads, Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    private void CloseDrive()
    {
        DriveSample.CloseDrive(localDrive, Log);
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Uri uri = data.Data;
        AndroidFileChooser.SetUriPermissions(this, data, uri);
        switch (requestCode)
        {
            case REQUEST_OPEN_DRIVE:
                IFile driveDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
                Task.Run(() =>
                {
                    OpenDrive(driveDir);
                });
                break;
            case REQUEST_CREATE_DRIVE:
                IFile newDriveDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
                Task.Run(() =>
                {
                    CreateDrive(newDriveDir);
                });
                break;
            case REQUEST_IMPORT_FILES:
                IFile[] filesToImport = AndroidFileChooser.GetFiles(this, data);
                Task.Run(() =>
                {
                    ImportFiles(filesToImport);
                });
                break;
            case REQUEST_EXPORT_FILES:
                IFile exportDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
                Task.Run(() =>
                {
                    ExportFiles(exportDir);
                });
                break;
        }
    }
}