using Android.Content;
using AndroidX.AppCompat.App;
using Android.App;
using Android.Widget;
using Android.OS;
using System;
using Mku.Salmon.Samples.Main;

namespace DotNetAndroidSamples;

[Activity(Label = "@string/app_name", MainLauncher = true, Theme = "@style/AppTheme",
    ScreenOrientation = Android.Content.PM.ScreenOrientation.Portrait)]
public class MainActivity : AppCompatActivity
{
    private Button textButton;
    private Button dataButton;
    private Button dataStreamButton;
    private Button fileButton;
    private Button localDriveButton;
    private Button httpDriveButton;
    private Button webServiceButton;

    protected override void OnCreate(Bundle savedInstanceState)
    {
        base.OnCreate(savedInstanceState);

        // Set our view from the "main" layout resource
        SetContentView(Resource.Layout.activity_main);


        textButton = (Button)FindViewById(Resource.Id.TEXT_BUTTON);
        textButton.Click += (s, e) =>
        {
            RunActivity(typeof(TextActivity));
        };

        dataButton = (Button)FindViewById(Resource.Id.DATA_BUTTON);
        dataButton.Click += (s, e) =>
        {
            RunActivity(typeof(DataActivity));
        };

        dataStreamButton = (Button)FindViewById(Resource.Id.DATA_STREAM_BUTTON);
        dataStreamButton.Click += (s, e) =>
        {
            RunActivity(typeof(DataStreamActivity));
        };

        fileButton = (Button)FindViewById(Resource.Id.FILE_BUTTON);
        fileButton.Click += (s, e) =>
        {
            RunActivity(typeof(FileActivity));
        };

        localDriveButton = (Button)FindViewById(Resource.Id.LOCAL_DRIVE_BUTTON);
        localDriveButton.Click += (s, e) =>
        {
            RunActivity(typeof(LocalDriveActivity));
        };

        httpDriveButton = (Button)FindViewById(Resource.Id.HTTP_DRIVE_BUTTON);
        httpDriveButton.Click += (s, e) =>
        {
            RunActivity(typeof(HttpDriveActivity));
        };

        webServiceButton = (Button)FindViewById(Resource.Id.WEB_SERVICE_BUTTON);
        webServiceButton.Click += (s, e) =>
        {
            RunActivity(typeof(WebServiceDriveActivity));
        };

    }

    private void RunActivity(Type cls)
    {
        StartActivity(new Intent(this, cls));
    }
}