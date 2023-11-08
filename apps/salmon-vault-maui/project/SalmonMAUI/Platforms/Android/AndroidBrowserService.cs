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

using Android.Webkit;
using Microsoft.Maui.Platform;
using Mku.SalmonFS;
using Salmon.Vault.Services;
using System;
using System.Collections.Generic;
using System.IO;

namespace Salmon.Vault.MAUI.ANDROID;

public class AndroidBrowserService : IWebBrowserService
{
    private static readonly int BUFFER_SIZE = 4 * 1024 * 1024;

    private Android.Webkit.WebView webView;
    private SalmonWebViewClient webViewClient;

    public AndroidBrowserService()
    {

    }

    public void SetResponse(string url, string mimeType, long contentLength,
        int bufferSize, bool buffered, Func<long, Stream> GetStream)
    {
        webViewClient = new SalmonWebViewClient(url, mimeType, contentLength, GetStream);
        webView.SetWebViewClient(webViewClient);
    }

    public void SetWebBrowserHandle(object webView)
    {
        this.webView = (webView as MauiWebView);
        InitWebView();
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

    private class SalmonWebViewClient : WebViewClient
    {
        private string mimeType;
        private string url;
        private long contentLength;
        private Func<long, Stream> GetStream;

        public SalmonWebViewClient(string url, string mimeType, long contentLength, Func<long, Stream> GetStream)
        {
            this.url = url;
            this.mimeType = mimeType;
            this.contentLength = contentLength;
            this.GetStream = GetStream;
        }

        override
        public WebResourceResponse
        ShouldInterceptRequest(Android.Webkit.WebView view, IWebResourceRequest request)
        {
            if (!request.Url.ToString().Equals(url))
            {
                // disable external web resources
                return null;
            }

            // Android WebView doesn't support partial content
            Stream decStream = GetStream(0);
            Dictionary<string, string> responseHeaders = new Dictionary<string, string>();
            responseHeaders["Content-Length"] = contentLength + "";
            responseHeaders["Content-Type"] = mimeType;
            responseHeaders["Cache-Control"] = "no-cache, no-store, must-revalidate";
            responseHeaders["Pragma"] = "no-cache";
            int code = 200;
            string msg = "OK";
            WebResourceResponse res = new WebResourceResponse(mimeType, "UTF-8", code, msg, responseHeaders, decStream);
            return res;
        }
    }

    public void Release()
    {
        this.webView.StopLoading();
        this.webView.ClearCache(true);
        this.webView.ClearHistory();
        this.webView.Destroy();
        this.webView = null;
    }
}