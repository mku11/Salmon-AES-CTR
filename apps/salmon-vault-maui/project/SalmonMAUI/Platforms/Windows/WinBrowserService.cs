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

using Microsoft.Maui.Platform;
using Microsoft.Web.WebView2.Core;
using Mku.SalmonFS;
using Salmon.Vault.Services;
using System;
using System.Collections.Generic;
using System.IO;

namespace Salmon.Vault.MAUI.WinUI;

// see https://learn.microsoft.com/en-us/microsoft-edge/webview2/how-to/webresourcerequested?tabs=dotnet
public class WinBrowserService : IWebBrowserService
{
    private MauiWebView webView;
    public WinBrowserService()
    {

    }

    public void SetResponse(string url, string mimeType, long contentLength, int bufferSize, 
        bool buffered, Func<long, Stream> GetStream)
    {
        webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
        webView.CoreWebView2.WebResourceRequested += delegate 
            (CoreWebView2 sender, CoreWebView2WebResourceRequestedEventArgs args)
        {
            if (!args.Request.Uri.ToString().Equals(url))
            {
                // disable external web resources
                args.Response = this.webView.CoreWebView2.Environment.CreateWebResourceResponse(null, 404, "Not Found", null);
                return;
            }

            bool isPartial = false;
            int position = 0;
            Dictionary<string, string> headers = GetHeaders(args.Request);
            if (headers.ContainsKey("Range"))
            {
                isPartial = true;
                position = int.Parse(headers["Range"].Split("=")[1].Split("-")[0]);
            }

            Stream stream = GetStream(position);

            long minContentLength = buffered?Math.Min(bufferSize, contentLength - position): contentLength - position;
            string responseHeaders = "Content-Length: " + minContentLength + "\r\n" +
                    "Content-Type: " + mimeType + "\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate" + "\r\n" +
                    "Pragma: no-cache" + "\r\n";
            int code;
            string msg;

            if (isPartial || buffered)
            {
                responseHeaders += "Content-Range: bytes " + position + "-" + (position + minContentLength - 1) + "/" + contentLength + "\r\n";
                code = 206;
                msg = "Partial Content";
            }
            else
            {
                code = 200;
                msg = "OK";
            }
            Windows.Storage.Streams.IRandomAccessStream ras = stream.AsRandomAccessStream();
            CoreWebView2WebResourceResponse response = webView.CoreWebView2.Environment.CreateWebResourceResponse(ras, code, msg, responseHeaders);
            args.Response = response;
        };
    }

    private Dictionary<string, string> GetHeaders(CoreWebView2WebResourceRequest request)
    {
        Dictionary<string, string> headers = new Dictionary<string, string>();
        foreach (var header in request.Headers)
        {
            headers[header.Key] = header.Value;
        }
        return headers;
    }

    public void SetWebBrowserHandle(object webView)
    {
        this.webView = (webView as MauiWebView);
        // disable downloading
        this.webView.CoreWebView2.DownloadStarting += (CoreWebView2 sender, CoreWebView2DownloadStartingEventArgs args) =>
        {
            args.Handled = true;
        };
    }

    public void Release()
    {
        this.webView.CoreWebView2.Stop();
        this.webView.CoreWebView2.CookieManager.DeleteAllCookies();
        this.webView.CoreWebView2.CallDevToolsProtocolMethodAsync("Network.clearBrowserCache", "{}");
        this.webView.Close();
        this.webView = null;
    }
}