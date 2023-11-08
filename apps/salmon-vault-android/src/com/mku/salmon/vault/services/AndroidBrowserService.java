package com.mku.salmon.vault.services;/*
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


import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.mku.func.Function;

import java.io.InputStream;
import java.util.HashMap;

public class AndroidBrowserService implements IWebBrowserService
{
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;

    private WebView webView;
    private SalmonWebViewClient webViewClient;

    public AndroidBrowserService()
    {

    }

    public void setResponse(String url, String mimeType, long contentLength,
        int bufferSize, boolean buffered, Function<Long, InputStream> GetStream)
    {
        webViewClient = new SalmonWebViewClient(url, mimeType, contentLength, GetStream);
        webView.setWebViewClient(webViewClient);
    }

    public void setWebBrowserHandle(Object webView)
    {
        this.webView = (WebView) webView;
        initWebView();
    }

    private void initWebView()
    {
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false); // old zoom buttons
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.getSettings().setBlockNetworkImage(true);
    }

    private class SalmonWebViewClient extends WebViewClient
    {
        private String mimeType;
        private String url;
        private long contentLength;
        private Function<Long, InputStream> GetStream;

        public SalmonWebViewClient(String url, String mimeType, long contentLength, Function<Long, InputStream> GetStream)
        {
            this.url = url;
            this.mimeType = mimeType;
            this.contentLength = contentLength;
            this.GetStream = GetStream;
        }

        @Override
        public WebResourceResponse
        shouldInterceptRequest(WebView view, WebResourceRequest request)
        {
            if (!request.getUrl().toString().equals(url))
            {
                // disable external web resources
                return null;
            }

            // Android WebView doesn't support partial content
            InputStream decStream = GetStream.apply(0L);
            HashMap<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("Content-Length", contentLength + "");
            responseHeaders.put("Content-Type",  mimeType);
            responseHeaders.put("Cache-Control",  "no-cache, no-store, must-revalidate");
            responseHeaders.put("Pragma", "no-cache");
            int code = 200;
            String msg = "OK";
            WebResourceResponse res = new WebResourceResponse(mimeType, "UTF-8", code, msg, responseHeaders, decStream);
            return res;
        }
    }

    public void release()
    {
        this.webView.stopLoading();
        this.webView.clearCache(true);
        this.webView.clearHistory();
        this.webView.destroy();
        this.webView = null;
    }
}