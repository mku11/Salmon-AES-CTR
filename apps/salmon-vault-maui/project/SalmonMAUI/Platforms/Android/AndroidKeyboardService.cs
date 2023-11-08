
using Android.App;
using AndroidX.DocumentFile.Provider;
using Mku.Android.File;
using Mku.File;
using Salmon.Vault.Services;
using System;

namespace Salmon.Vault.MAUI.ANDROID;

public class AndroidKeyboardService : IKeyboardService
{
    private Activity activity;

    public AndroidKeyboardService()
    {
            
    }

    public event EventHandler<IKeyboardService.MetaKeyEventArgs> OnMetaKey;
    public event EventHandler<IKeyboardService.KeyEventArgs> OnKey;
}