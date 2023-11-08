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

#if ANDROID
using Microsoft.Maui.Controls.PlatformConfiguration.AndroidSpecific;
#endif
using Microsoft.Maui.Controls;
using Salmon.Vault.Services;
using Salmon.Vault.ViewModel;
using System;

namespace Salmon.Vault.View;

[QueryProperty(nameof(SalmonFileViewModel), "SalmonFileViewModel")]
public partial class ContentViewer : ContentPage
{
    private IWebBrowserService webBrowserService;
    private bool intialized;

    public SalmonFileViewModel SalmonFileViewModel { get; set; }

    ContentViewerViewModel ViewModel { get; set; }
    public ContentViewer()
    {
        InitializeComponent();
        ViewModel = (ContentViewerViewModel)BindingContext;
        WebView.Loaded += WebView_Loaded;
        Unloaded += ContentViewer_Unloaded;
    }

    private void ContentViewer_Unloaded(object sender, EventArgs e)
    {
        WebView.Source = new UrlWebViewSource() { Url = "about:blank" };
        ViewModel.OnClose();
        webBrowserService.Release();
    }

    private void WebView_Loaded(object sender, EventArgs e)
    {
        WebView.Navigated += WebView_Navigated;
        WebView.Source = new UrlWebViewSource() { Url = "about:blank" };
    }

    private void WebView_Navigated(object sender, WebNavigatedEventArgs e)
    {
        if (!intialized)
            SetupWebView();
        intialized = true;
    }

    private void SetupWebView()
    {
#if ANDROID
        WebView.On<Microsoft.Maui.Controls.PlatformConfiguration.Android>()
            .EnableZoomControls(true);
#endif
        webBrowserService = ServiceLocator.GetInstance().Resolve<IWebBrowserService>();
        webBrowserService.SetWebBrowserHandle(WebView.Handler.PlatformView);
        ViewModel.Load(SalmonFileViewModel);
    }
}
