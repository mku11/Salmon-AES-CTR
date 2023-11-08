using Android.App;
using Android.Content;
using Android.Runtime;
using Microsoft.Maui;
using Microsoft.Maui.Controls.Compatibility.Platform.Android;
using Microsoft.Maui.Graphics;
using Microsoft.Maui.Hosting;
using System;

namespace Salmon.Vault.MAUI.ANDROID;

[Application]
public class MainApplication : MauiApplication
{
    public Context Instance { get; private set; }
    public MainApplication(IntPtr handle, JniHandleOwnership ownership)
        : base(handle, ownership)
    {
        Instance = this;
    }

    protected override MauiApp CreateMauiApp()
    {
        // WORKAROUND: https://github.com/dotnet/maui/issues/7906
        Microsoft.Maui.Handlers.EntryHandler.Mapper.AppendToMapping("NoUnderline", (h, v) =>
        {
            h.PlatformView.BackgroundTintList =
                Android.Content.Res.ColorStateList.ValueOf(Colors.Transparent.ToAndroid());
        });
        return MauiProgram.CreateMauiApp();
    }
}
