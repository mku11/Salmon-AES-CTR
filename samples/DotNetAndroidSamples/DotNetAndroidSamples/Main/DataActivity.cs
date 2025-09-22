using AndroidX.AppCompat.App;
using Android.Widget;
using Android.OS;
using System;
using DotNetAndroidSamples;
using Google.Android.Material.TextField;
using Mku.Android.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using Android.App;
using System.Threading.Tasks;
using Mku.Salmon.Transform;
using Mku.Android.Salmon.Transform;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class DataActivity : AppCompatActivity
{
    private TextInputEditText password;
    private Spinner dataSize;
    private Spinner threads;
    private Spinner integrity;
    private EditText outputText;

    private Button encryptButton;
    private Button decryptButton;

    private static readonly string defaultPassword = "test123";
    private byte[] key;
    byte[] integrityKey = null;
    private byte[] data;
    private byte[] encData;

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

        SetContentView(Resource.Layout.activity_data);

        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);
        dataSize = (Spinner)FindViewById(Resource.Id.DATA_SIZE);
        threads = (Spinner)FindViewById(Resource.Id.THREADS);
        integrity = (Spinner)FindViewById(Resource.Id.DATA_INTEGRITY);

        encryptButton = (Button)FindViewById(Resource.Id.ENCRYPT_BUTTON);
        encryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                EncryptData();
            });
        };

        decryptButton = (Button)FindViewById(Resource.Id.DECRYPT_BUTTON);
        decryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                DecryptData();
            });
        };

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

    public void Initialize()
    {
		AesNativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.AesIntrinsics;
    }

    public void EncryptData()
    {
        ClearLog();

        // generate an encryption key from the text password
        Log("generating keys and random data...");
        key = SamplesCommon.GetKeyFromPassword(password.Text.ToString());

        if ((integrity.SelectedItem).Equals("Enable"))
        {
            // generate an HMAC key
            integrityKey = Generator.GetSecureRandomBytes(32);
        }

        // generate random data
        data = SamplesCommon.GenerateRandomData(
                int.Parse((string)dataSize.SelectedItem) * 1024 * 1024);

        try
        {
            Log("starting encryption...");
            encData = DataSample.EncryptData(data, key, integrityKey,
                    int.Parse((string)threads.SelectedItem), Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void DecryptData()
    {
        Log("starting decryption...");
        try
        {
            byte[] decData = DataSample.DecryptData(encData, key, integrityKey,
                    int.Parse((string)threads.SelectedItem), Log);
            Log("done");
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }
}