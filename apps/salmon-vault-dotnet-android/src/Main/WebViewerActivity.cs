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
using Android.Content;
using Android.Content.PM;
using Android.Content.Res;
using Android.OS;
using Android.Views;
using Android.Webkit;
using AndroidX.AppCompat.App;
using Salmon.Vault.Utils;
using Java.Lang;
using Exception = System.Exception;
using Thread = Java.Lang.Thread;
using Mku.Utils;
using Mku.Salmon.Integrity;
using Mku.SalmonFS;
using Mku.Salmon.IO;
using Salmon.Vault.DotNetAndroid;
using Android.Systems;
using Java.IO;
using Salmon.Vault.Main;
using static Google.Android.Material.Tabs.TabLayout;
using Math = System.Math;
using Kotlin.Jvm;
using Android.App;
using Android.Widget;
using Salmon.Vault.Extensions;
using System.IO;
using System.Linq;
using Salmon.Vault.Dialog;
using Salmon.Vault.Prefs;
using System.Threading.Tasks;

namespace Salmon.Vault.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
public class WebViewerActivity : AppCompatActivity
{
    private static readonly string TAG = nameof(WebViewerActivity);
    private static readonly int SWIPE_DISTANCE_THRESHOLD = 100;
    private static readonly int SWIPE_VELOCITY_THRESHOLD = 1200;
    private static readonly int ENC_BUFFER_SIZE = 512 * 1024;

    private static SalmonFile[] fileList = null;
    private static int pos;

    public WebView webView;
    private SalmonWebViewClient webViewClient;
    private BufferedInputStream stream;
    private TextView mTitle;
    private readonly object swipeObj = new object();

    public static void SetContentFiles(int position, SalmonFile[] salmonFiles)
    {
        pos = position;
        fileList = salmonFiles;
    }

    public void LoadContentAsync()
    {
        Task.Run(() =>
        {
            try
            {
                LoadContent();
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
        });
    }

    private void SetupWebViewClient()
    {
        webViewClient = new SalmonWebViewClient();
        webView.SetWebViewClient(webViewClient);
    }

    protected void LoadContent()
    {
        string filename = fileList[pos].BaseName;
        string ext = SalmonFileUtils.GetExtensionFromFileName(filename).ToLower();
        string mimeType = null;
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
            SalmonStream encStream = fileList[pos].GetInputStream();

            // in order for the webview not to crash we suppress Exceptions
            encStream.FailSilently = true;

            // we inject our SalmonStream into the webview client
            BufferedStream stream = new BufferedStream(encStream, ENC_BUFFER_SIZE);
            webViewClient.SetStream(mimeType, stream);
            RunOnUiThread(() =>
            {
                mTitle.Text = filename;
                webView.LoadUrl("file:android_asset/imagedata.dat");
            });
        }
        catch (SalmonIntegrityException ex)
        {
            ex.PrintStackTrace();
            RunOnUiThread(() =>
            {
                Toast.MakeText(this, GetString(Resource.String.FileCorruptOrTampered), ToastLength.Long).Show();
            });
        }
        catch (Exception ex)
        {
            ex.PrintStackTrace();
            RunOnUiThread(() =>
            {
                Toast.MakeText(this, "Error: " + ex.Message, ToastLength.Long).Show();
            });
        }

    }

    override
    protected void OnNewIntent(Intent intent)
    {
        base.OnNewIntent(intent);
        LoadContentAsync();
    }

    override
    protected void OnCreate(Bundle icicle)
    {
        base.OnCreate(icicle);
        SetupWindow();
        ResetScreen();
        Init();
        LoadContentAsync();
    }

    private void PromptTextSearch()
    {
        SalmonDialog.PromptEdit(GetString(Resource.String.Search), "Keywords", (string value, bool isChecked) =>
        {
            webView.FindAllAsync(value);
        }, "", false, false, false, GetString(Resource.String.MatchAnyTerm));
    }

    private void SetupWindow()
    {
        if (SalmonPreferences.HideScreenContents)
            Window.SetFlags(WindowManagerFlags.Secure, WindowManagerFlags.Secure);
    }

    override
    protected void OnDestroy()
    {
        if (stream != null)
        {
            try
            {
                stream.Close();
            }
            catch (System.IO.IOException e)
            {
                e.PrintStackTrace();
            }
        }
        stream = null;
        base.OnDestroy();
    }

    override
    public bool OnPrepareOptionsMenu(IMenu menu)
    {
        menu.Clear();
        menu.Add(0, (int)ActionType.SEARCH, 0, GetString(Resource.String.Search))
                .SetIcon(Android.Resource.Drawable.IcMenuSearch)
                .SetShowAsAction(ShowAsAction.Never);
        return true;
    }

    override
    public bool OnOptionsItemSelected(IMenuItem item)
    {
        if (System.Enum.GetValues(typeof(ActionType))
            .Cast<ActionType>().ToArray()[item.ItemId] == ActionType.SEARCH)
        {
            PromptTextSearch();
            return true;
        }
        return false;
    }

    private void Init()
    {
        SetContentView(Resource.Layout.webviewer);
        webView = (WebView)FindViewById(Resource.Id.webview);
        webView.VerticalScrollBarEnabled = false;
        webView.HorizontalScrollBarEnabled = false;
        mTitle = (TextView)FindViewById(Resource.Id.title);

        InitWebView();
        InitGestures();
        SetupWebViewClient();
        SetupActionBar();
    }

    private void SetupActionBar()
    {
        try
        {
            if (fileList.Length > 0 && !SalmonFileUtils.IsText(fileList[0].BaseName))
            {
                if (SupportActionBar != null)
                    SupportActionBar.Hide();
            }
        }
        catch (Exception e)
        {
            e.PrintStackTrace();
        }
    }

    private void InitGestures()
    {
        GestureDetector gd = new GestureDetector(this, new SalmonSimpleOnGestureListener(this));
        webView.Touch += (s, args) =>
        {
            gd.OnTouchEvent(args.Event);
            args.Handled = false;
        };
    }

    private class SalmonSimpleOnGestureListener : GestureDetector.SimpleOnGestureListener
    {
        WebViewerActivity activity;
        public SalmonSimpleOnGestureListener(WebViewerActivity activity)
        {
            this.activity = activity;
        }

        override
        public bool OnDoubleTap(MotionEvent e)
        {
            return false;
        }

        override
        public void OnLongPress(MotionEvent e)
        {
            base.OnLongPress(e);
        }

        override
        public bool OnDoubleTapEvent(MotionEvent e)
        {
            return false;
        }

        override
        public bool OnSingleTapConfirmed(MotionEvent e)
        {
            return false;
        }

        override
        public bool OnDown(MotionEvent e)
        {
            return false;
        }

        override
        public bool OnFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            float distanceX = e2.GetX() - e1.GetX();
            float distanceY = e2.GetY() - e1.GetY();
            if (Math.Abs(distanceX) > Math.Abs(distanceY)
                    && Math.Abs(distanceX) > SWIPE_DISTANCE_THRESHOLD
                    && Math.Abs(velocityX) > SWIPE_VELOCITY_THRESHOLD)
            {
                activity.OnSwipe((int)(-1 * distanceX));
                return true;
            }
            return false;
        }
    }

    private void OnSwipe(int interval)
    {
        lock (swipeObj)
        {
            if (interval > 0)
            {
                PlayNextItem();
            }
            else if (interval < 0)
            {
                PlayPreviousItem();
            }
        }
    }

    [Synchronized]
    private void PlayPreviousItem()
    {
        if (pos > 0)
        {
            pos--;
            LoadContentAsync();
        }
    }

    [Synchronized]
    private void PlayNextItem()
    {
        if (pos < fileList.Length - 1)
        {
            pos++;
            LoadContentAsync();
        }
    }

    private void InitWebView()
    {
        webView.Settings.BuiltInZoomControls = true;
        webView.Settings.DisplayZoomControls = false; // old zoom buttons
        webView.Settings.LoadWithOverviewMode = true;
        webView.Settings.UseWideViewPort = true;
        webView.Settings.SetSupportMultipleWindows(true);
        webView.Settings.BlockNetworkImage = true;
    }

    override
    public void OnConfigurationChanged(Configuration c)
    {
        base.OnConfigurationChanged(c);
        try
        {
            LoadContent();
        }
        catch (Exception exception)
        {
            exception.PrintStackTrace();
        }
    }

    public void ResetScreen()
    {
        Window.AddFlags(WindowManagerFlags.KeepScreenOn);
    }

    private class SalmonWebViewClient : WebViewClient
    {
        private BufferedStream stream;
        private string mimeType;

        public void SetStream(string mimeType, BufferedStream stream)
        {
            this.stream = stream;
            this.mimeType = mimeType;
        }

        override
        public WebResourceResponse
        ShouldInterceptRequest(WebView view, IWebResourceRequest request)
        {
            WebResourceResponse res = new WebResourceResponse(mimeType, "UTF-8", stream);
            if (res == null)
                return base.ShouldInterceptRequest(view, request);
            return res;
        }
    }
}