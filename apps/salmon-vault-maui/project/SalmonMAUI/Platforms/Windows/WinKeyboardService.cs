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

using Microsoft.Maui;
using Microsoft.UI.Xaml.Input;
using Salmon.Vault.Services;
using System;
using static Salmon.Vault.Services.IKeyboardService;

namespace Salmon.Vault.MAUI.WinUI;

public class WinKeyboardService : IKeyboardService
{
    public event EventHandler<MetaKeyEventArgs> OnMetaKey;
    public event EventHandler<KeyEventArgs> OnKey;

    public WinKeyboardService()
    {
        MauiWinUIWindow window = (MauiWinUIWindow)MauiWinUIApplication.Current
            .Application.Windows[0].Handler.PlatformView;
        window.Content.KeyDown += Content_KeyDown;
        window.Content.KeyUp += Content_KeyUp;
    }

    private void Content_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        MetaKey? metaKey = GetMetaKey(e);
        if (metaKey != null)
            OnMetaKey(this, new MetaKeyEventArgs() { MetaKey = (MetaKey)metaKey, Down = true });
        else
            OnKey(this, new KeyEventArgs() { Key = e.Key.ToString(), Down = true });
    }

    private void Content_KeyUp(object sender, KeyRoutedEventArgs e)
    {
        MetaKey? metaKey = GetMetaKey(e);
        if (metaKey != null)
            OnMetaKey(this, new MetaKeyEventArgs() { MetaKey = (MetaKey)metaKey, Down = false });
        else
            OnKey(this, new KeyEventArgs() { Key = e.Key.ToString(), Down = false });
    }

    private MetaKey? GetMetaKey(KeyRoutedEventArgs e)
    {
        switch (e.Key)
        {
            case Windows.System.VirtualKey.Control:
            case Windows.System.VirtualKey.RightControl:
            case Windows.System.VirtualKey.LeftControl:
                return MetaKey.Ctrl;
            case Windows.System.VirtualKey.Shift:
            case Windows.System.VirtualKey.RightShift:
            case Windows.System.VirtualKey.LeftShift:
                return MetaKey.Shift;
        }
        return null;
    }
}