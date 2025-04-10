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

namespace Mku.Salmon.Samples.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ScreenOrientation = global::Android.Content.PM.ScreenOrientation.Portrait)]
public class TextActivity : AppCompatActivity
{
    private TextInputEditText password;
    private Button encryptButton;
    private Button decryptButton;

    private EditText plainText;
    private EditText encryptedText;
    private EditText outputText;

    private static readonly string defaultPassword = "test123";
    private static readonly string text = "This is a plain text that will be encrypted";

    private byte[] key;

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);
        SetContentView(Resource.Layout.activity_text);

        password = (TextInputEditText)FindViewById(Resource.Id.TEXT_PASSWORD);

        encryptButton = (Button)FindViewById(Resource.Id.ENCRYPT_BUTTON);
        encryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                EncryptText();
            });
        };

        decryptButton = (Button)FindViewById(Resource.Id.DECRYPT_BUTTON);
        decryptButton.Click += (s, e) =>
        {
            Task.Run(() =>
            {
                DecryptText();
            });
        };

        plainText = (EditText)FindViewById(Resource.Id.PLAIN_TEXT);
        encryptedText = (EditText)FindViewById(Resource.Id.ENCRYPTED_TEXT);
        outputText = (EditText)FindViewById(Resource.Id.OUTPUT_TEXT);

        password.Text = defaultPassword;
        plainText.Text = text;
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

    public void ClearEncText()
    {
        RunOnUiThread(() =>
        {
            encryptedText.Text = "";
        });
    }

    private void Initialize()
    {
        AndroidFileSystem.Initialize(this);
        AesStream.AesProviderType = ProviderType.Default;
    }

    private void EncryptText()
    {
        ClearEncText();
        ClearLog();

        // generate an encryption key from the text password
        key = SamplesCommon.GetKeyFromPassword(password.Text.ToString());

        try
        {
            string encText = TextSample.EncryptText(plainText.Text, key);
            RunOnUiThread(() =>
            {
                encryptedText.Text = encText;
            });
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }

    private void DecryptText()
    {
        try
        {
            string decText = TextSample.DecryptText(encryptedText.Text.ToString(), key);
            Log("Decrypted Text: " + "\n" + decText);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Log(e.Message);
        }
    }
}