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
using Salmon.Vault.Services;
using Salmon.Vault.ViewModel;
using System;

namespace Salmon.Vault.View;

public partial class ContentViewer : System.Windows.Window
{
    private IWebBrowserService webBrowserService;
    private bool intialized;
    public SalmonFileViewModel SalmonFileViewModel { get; set; }
    public ContentViewerViewModel ViewModel { get; set; }

    public ContentViewer(SalmonFileViewModel viewModel)
    {
        InitializeComponent();
        SalmonFileViewModel = viewModel;
        ViewModel = (ContentViewerViewModel) DataContext;
        WebView.EnsureCoreWebView2Async();
        WebView.Loaded += WebView_Loaded;
        Unloaded += ContentViewer_Unloaded;
        Closing += (sender, e) =>
        {
            ViewModel.OnClose();
        };
    }

    private void ContentViewer_Unloaded(object sender, EventArgs e)
    {
        WebView.Source = new Uri("about:blank");
        ViewModel.OnClose();
        webBrowserService.Release();
    }

    private void WebView_Loaded(object sender, EventArgs e)
    {
        WebView.NavigationCompleted += WebView_NavigationCompleted;
        WebView.Source = new Uri("about:blank");
    }

    private void WebView_NavigationCompleted(object sender, Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs e)
    {
        if (!intialized)
            SetupWebView();
        intialized = true;
    }

    private void SetupWebView()
    {
        webBrowserService = ServiceLocator.GetInstance().Resolve<IWebBrowserService>();
        webBrowserService.SetWebBrowserHandle(WebView);
        ViewModel.Load(SalmonFileViewModel);
    }
}