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
using Mku.Salmon.Samples.Utils;
using Android.App;
using Uri = Android.Net.Uri;
using System.Threading.Tasks;
using Mku.Salmon.Transform;
using Mku.Android.Salmon.Transform;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class FileActivity : AppCompatActivity
{
    private const int REQUEST_SAVE_FILE = 1000;
    private const int REQUEST_LOAD_FILE = 1001;
    private TextInputEditText password;
    private EditText plainText;
    private EditText decryptedText;
    private EditText outputText;
    private Button saveButton;
    private Button loadButton;

    private static readonly string defaultPassword = "test123";
    private string text = "This is a plain text that will be encrypted";

    private byte[] key;
    byte[] integrityKey = null;


    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

        SetContentView(Resource.Layout.activity_file);

        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);

        saveButton = (Button)FindViewById(Resource.Id.SAVE_BUTTON);
        saveButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_SAVE_FILE, true);
        };

        loadButton = (Button)FindViewById(Resource.Id.LOAD_BUTTON);
        loadButton.Click += (s, e) =>
        {
            AndroidFileChooser.OpenFilesystem(this, REQUEST_LOAD_FILE, false);
        };

        plainText = (EditText)FindViewById(Resource.Id.PLAIN_TEXT);
        plainText.Text = text;
        decryptedText = (EditText)FindViewById(Resource.Id.DECRYPTED_TEXT);
        outputText = (EditText)FindViewById(Resource.Id.OUTPUT_TEXT);
        password.Text = defaultPassword;

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
		NativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;
    }

    public void SaveFile(IFile dir)
    {
        bool integrity = true;

        ClearLog();
        // generate an encryption key from the text password
        key = SamplesCommon.GetKeyFromPassword(password.Text.ToString());

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity)
        {
            // generate an HMAC key
            integrityKey = Generator.GetSecureRandomBytes(32);
        }

        string filename = "encrypted_data.dat";
        IFile file = dir.GetChild(filename);
        if (file.Exists)
        {
            file.Delete();
            file = dir.GetChild(filename);
        }

        try
        {
            FileSample.EncryptTextToFile(plainText.Text.ToString(), key, integrityKey, file, Log);
            Log("file saved");
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void LoadFile(IFile file)
    {
        try
        {
            string decText = FileSample.DecryptTextFromFile(key, integrityKey, file, Log);
            Log("file loaded");
            RunOnUiThread(() =>
            {
                decryptedText.Text = decText;
            });
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
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
            case REQUEST_SAVE_FILE:
                IFile saveDir = AndroidFileSystem.GetRealFile(uri.ToString(), true);
                Task.Run(() =>
                {
                    SaveFile(saveDir);
                });
                break;
            case REQUEST_LOAD_FILE:
                IFile filesToImport = AndroidFileChooser.GetFiles(this, data)[0];
                Task.Run(() =>
                {
                    LoadFile(filesToImport);
                });
                break;
        }
    }
}