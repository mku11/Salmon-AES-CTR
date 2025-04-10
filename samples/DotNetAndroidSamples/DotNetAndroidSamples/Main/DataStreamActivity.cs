using AndroidX.AppCompat.App;
using Android.Widget;
using Android.OS;
using System;
using DotNetAndroidSamples;
using Google.Android.Material.TextField;
using Mku.Android.FS.File;
using Mku.Salmon.Samples.Samples;
using Mku.Salmon.Streams;
using BitConverter = Mku.Convert.BitConverter;
using Android.App;
using System.Threading.Tasks;

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class DataStreamActivity : AppCompatActivity
{
    private TextInputEditText password;
    private Spinner dataSize;
    private EditText outputText;

    private Button encryptButton;
    private Button decryptButton;

    private static readonly string defaultPassword = "test123";

    private byte[] key;
    private byte[] data;
    private byte[] encData;
    private byte[] nonce;

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);

        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;

        SetContentView(Resource.Layout.activity_data_stream);

        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);
        dataSize = (Spinner)FindViewById(Resource.Id.DATA_SIZE);

        encryptButton = (Button)FindViewById(Resource.Id.ENCRYPT_BUTTON);
        encryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                EncryptDataStream();
            });
        };

        decryptButton = (Button)FindViewById(Resource.Id.DECRYPT_BUTTON);
        decryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                DecryptDataStream();
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
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;
    }

    public void EncryptDataStream()
    {
        ClearLog();

        // generate a key
        Log("generating keys and random data...");
        key = SamplesCommon.GetKeyFromPassword(password.Text.ToString());

        // Always request a new random secure nonce!
        // if you want to you can embed the nonce in the header data
        // see Encryptor implementation
        nonce = Generator.GetSecureRandomBytes(8); // 64 bit nonce
        Log("Created nonce: " + BitConverter.ToHex(nonce));

        // generate random data
        data = SamplesCommon.GenerateRandomData(
                int.Parse((string)dataSize.SelectedItem) * 1024 * 1024);

        try
        {
            Log("starting encryption...");
            encData = DataStreamSample.EncryptDataStream(data, key, nonce, Log);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    public void DecryptDataStream()
    {

        try
        {
            Log("starting decryption...");
            byte[] decData = DataStreamSample.DecryptDataStream(encData, key, nonce, Log);
            Log("done");
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }

    }
}
