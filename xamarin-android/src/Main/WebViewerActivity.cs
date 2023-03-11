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
using System.IO;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Content.Res;
using Android.OS;
using Android.Views;
using Android.Webkit;
using AndroidX.AppCompat.App;
using Salmon.Droid.Utils;
using Salmon.FS;
using Java.Lang;
using Exception = System.Exception;
using Thread = Java.Lang.Thread;
using Android.Widget;
using static Salmon.SalmonIntegrity;

namespace Salmon.Droid.Main
{
    [Activity(Label = "@string/app_name", ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    public class WebViewerActivity : AppCompatActivity
    {
        private static readonly string TAG = typeof(WebViewerActivity).Name;
        private static readonly int ENC_BUFFER_SIZE = 2 * 1024 * 1024;

        private const int SEARCH = 1;

        public WebView webView;
        private string mimeType;
        private static SalmonFile salmonFile = null;
        private WebViewerClient webViewClient;
        private BufferedStream stream;

        public void LoadContentAsync()
        {
            new Thread(new Runnable(() =>
            {
                
                try
                {
                    LoadContent();
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }

            })).Start();
        }

        public class WebViewerClient : WebViewClient
        {
            private BufferedStream stream;
            private string mimeType;

            public void SetStream(string mimeType, BufferedStream stream)
            {
                this.stream = stream;
                this.mimeType = mimeType;
            }


            public override WebResourceResponse ShouldInterceptRequest(WebView view, IWebResourceRequest request)
            {
                WebResourceResponse res = new WebResourceResponse(mimeType, "UTF-8", stream);
                if (res == null)
                    return base.ShouldInterceptRequest(view, request);
                return res;

            }

        }

        private void SetupWebViewClient()
        {
            webViewClient = new WebViewerClient();
            webView.SetWebViewClient(webViewClient);

        }


        protected void LoadContent()
        {
            string filename = salmonFile.GetBaseName();
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename).ToLower();
            mimeType = null;
            try
            {
                mimeType = MimeTypeMap.Singleton.GetMimeTypeFromExtension(ext);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
            if (mimeType == null || mimeType.Trim().Equals(""))
            {
                mimeType = "text/plain";
            }
            
            try
            {
                Streams.SalmonStream encStream = salmonFile.GetInputStream();
                // in order for the webview not to crash we suppress Exceptions
                encStream.SetFailSilently(true);
                // we load with a buffer as an example
                stream = new BufferedStream(encStream, ENC_BUFFER_SIZE);
                webViewClient.SetStream(mimeType, stream);
                RunOnUiThread(new Runnable(() =>
                {
                    webView.LoadUrl("file:///android_asset/imagedata.dat");
                }));
            }
            catch (SalmonIntegrityException ex)
            {
                ex.PrintStackTrace();
                RunOnUiThread(new Runnable(() =>
                {
                    Toast.MakeText(this, Resources.GetString(Resource.String.FileCorrupOrTampered), ToastLength.Long).Show();
                }));
                
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
                RunOnUiThread(new Runnable(() =>
                {
                    Toast.MakeText(this, "Error: " + ex.Message, ToastLength.Long).Show();
                }));
                
            }

        }

        protected override void OnNewIntent(Intent intent)
        {
            base.OnNewIntent(intent);
            LoadContentAsync();
        }

        protected override void OnCreate(Bundle icicle)
        {
            base.OnCreate(icicle);
            ResetScreen();
            Init();
            LoadContentAsync();
        }

        protected override void OnDestroy()
        {
            if (stream != null)
                stream.Close();
            stream = null;
            base.OnDestroy();
        }

        public override bool OnPrepareOptionsMenu(IMenu menu)
        {
            menu.Clear();
            menu.Add(0, SEARCH, 0, Resources.GetString(Resource.String.Search))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuSearch))
                .SetShowAsAction(ShowAsAction.Always);
            return true;
        }


        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            switch (item.ItemId)
            {
                case SEARCH:
                    PromptTextSearch();
                    return true;
            }
            return false;
        }

        private void PromptTextSearch()
        {
            ActivityCommon.PromptEdit(this, Resources.GetString(Resource.String.Search),
                "Keywords", "", null, (string value, bool option) =>{
                    webView.FindAllAsync(value);
                });
        }

        private void Init()
        {
            SetContentView(Resource.Layout.webviewer);
            webView = (WebView) FindViewById(Resource.Id.webview);
            webView.VerticalScrollBarEnabled = false;
            webView.HorizontalScrollBarEnabled = false;

            InitWebView();
            SetupWebViewClient();

        }

        private void InitWebView()
        {
            webView.Settings.SetSupportZoom(true);
            if (Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.Honeycomb)
            {
                webView.Settings.BuiltInZoomControls = true;
                webView.Settings.DisplayZoomControls = false; // old zoom buttons
            }
            else
            {
                webView.Settings.BuiltInZoomControls = false;
            }
            webView.Settings.LoadWithOverviewMode = true;
            webView.Settings.UseWideViewPort = true;
            webView.Settings.SetSupportMultipleWindows(true);
            webView.Settings.BlockNetworkImage = true;
        }

        public override void OnConfigurationChanged(Configuration c)
        {
            base.OnConfigurationChanged(c);
            LoadContent();
        }

        public void ResetScreen()
        {
            Window.AddFlags(WindowManagerFlags.KeepScreenOn);
        }

        public static void SetContentFile(SalmonFile contentFile)
        {
            salmonFile = contentFile;
        }
    }
}