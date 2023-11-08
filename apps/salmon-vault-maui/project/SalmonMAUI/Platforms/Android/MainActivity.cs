using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics.Drawables;
using Android.OS;
using Android.Views;
using Android.Widget;
using AndroidX.Core.View;
using Microsoft.Maui;
using Mku.Android.File;
using Mku.File;
using Mku.Salmon.Transform;
using Mku.SalmonFS;
using Salmon.Transform;
using Salmon.Vault.Extensions;
using Salmon.Vault.Main;
using Salmon.Vault.Model;
using Salmon.Vault.Services;
using Salmon.Vault.View;
using Salmon.Vault.ViewModel;
using System;
using System.Diagnostics;

namespace Salmon.Vault.MAUI.ANDROID;

[Activity(MainLauncher = true, Theme = "@style/Theme.MaterialComponents",
    Label = "@string/app_name", Logo = "@drawable/logo_128x128",
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode | ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
public class MainActivity : MauiAppCompatActivity
{
    public MainViewModel ViewModel { get; private set; }

    override
    protected void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);
        SetupServices();
        SupportActionBar.SetDisplayShowTitleEnabled(true);
        SupportActionBar.SetDisplayUseLogoEnabled(true);
        SupportActionBar.SetLogo(Resource.Drawable.logo_48x48);
        SupportActionBar.SetBackgroundDrawable(new ColorDrawable(Android.Graphics.Color.ParseColor("#2d3343")));
    }

    private void SetupServices()
    {
        SalmonNativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidDrive.Initialize(this.ApplicationContext);
        SalmonDriveManager.VirtualDriveClass = typeof(AndroidDrive);
        ServiceLocator.GetInstance().Register(typeof(IFileService), new AndroidFileService(this));
        ServiceLocator.GetInstance().Register(typeof(IFileDialogService), new AndroidFileDialogService(this));
        ServiceLocator.GetInstance().Register(typeof(IWebBrowserService), new AndroidBrowserService());
        ServiceLocator.GetInstance().Register(typeof(IKeyboardService), new AndroidKeyboardService());
        ServiceLocator.GetInstance().Register(typeof(IMediaPlayerService), new AndroidMediaPlayerService(this));
        MainWindow.OnAttachViewModel = AttachViewModel;
    }

    public void AttachViewModel(MainViewModel viewModel)
    {
        ViewModel = viewModel;
    }

    public override bool OnPrepareOptionsMenu(IMenu menu)
    {
        if (ViewModel == null)
            return true;

        MenuCompat.SetGroupDividerEnabled(menu, true);
        menu.Clear();

        menu.Add(1, ActionType.OPEN_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.OpenVault))
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(1, ActionType.CREATE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.NewVault))
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(1, ActionType.CLOSE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.CloseVault))
                .SetShowAsAction(ShowAsAction.Never);

        if (SalmonVaultManager.Instance.IsJobRunning)
        {
            menu.Add(2, ActionType.STOP.Ordinal(), 0, Resources.GetString(Resource.String.Stop))
                    .SetShowAsAction(ShowAsAction.Never);
        }

        if (SalmonVaultManager.Instance.FileManagerMode == SalmonVaultManager.Mode.Copy
            || SalmonVaultManager.Instance.FileManagerMode == SalmonVaultManager.Mode.Move)
        {
            menu.Add(3, ActionType.PASTE.Ordinal(), 0, Resources.GetString(Resource.String.Paste));
        }
        menu.Add(3, ActionType.IMPORT.Ordinal(), 0, Resources.GetString(Resource.String.ImportFiles))
                .SetIcon(Android.Resource.Drawable.IcMenuAdd)
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(3, ActionType.NEW_FOLDER.Ordinal(), 0, GetString(Resource.String.NewFolder))
                .SetIcon(Android.Resource.Drawable.IcInputAdd);

        if (ViewModel.IsMultiSelection)
        {
            menu.Add(3, ActionType.COPY.Ordinal(), 0, Resources.GetString(Resource.String.Copy));
            menu.Add(3, ActionType.CUT.Ordinal(), 0, Resources.GetString(Resource.String.Cut));
            menu.Add(3, ActionType.DELETE.Ordinal(), 0, Resources.GetString(Resource.String.Delete));
            menu.Add(3, ActionType.EXPORT.Ordinal(), 0, Resources.GetString(Resource.String.ExportFiles));
        }

        menu.Add(4, ActionType.REFRESH.Ordinal(), 0, Resources.GetString(Resource.String.Refresh))
                .SetIcon(Android.Resource.Drawable.IcMenuRotate)
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(4, ActionType.SORT.Ordinal(), 0, Resources.GetString(Resource.String.Sort))
                .SetIcon(Android.Resource.Drawable.IcMenuSortAlphabetically);
        menu.Add(4, ActionType.SEARCH.Ordinal(), 0, Resources.GetString(Resource.String.Search))
                .SetIcon(Android.Resource.Drawable.IcMenuSearch);

        if (ViewModel != null && SalmonVaultManager.Instance.CurrDir != null)
        {
            menu.Add(5, ActionType.IMPORT_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.ImportAuthFile))
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(5, ActionType.EXPORT_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.ExportAuthFile))
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(5, ActionType.REVOKE_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.RevokeAuth))
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(5, ActionType.DISPLAY_AUTH_ID.Ordinal(), 0, Resources.GetString(Resource.String.DisplayAuthID))
                    .SetShowAsAction(ShowAsAction.Never);
        }

        menu.Add(6, ActionType.SETTINGS.Ordinal(), 0, Resources.GetString(Resource.String.Settings))
                .SetIcon(Android.Resource.Drawable.IcMenuPreferences);
        menu.Add(6, ActionType.ABOUT.Ordinal(), 0, Resources.GetString(Resource.String.About))
                .SetIcon(Android.Resource.Drawable.IcMenuInfoDetails);
        menu.Add(6, ActionType.EXIT.Ordinal(), 0, Resources.GetString(Resource.String.Exit))
                .SetIcon(Android.Resource.Drawable.IcMenuCloseClearCancel);

        return base.OnPrepareOptionsMenu(menu);
    }

    public override bool OnOptionsItemSelected(IMenuItem item)
    {
        ActionType type = (Enum.GetValues(typeof(ActionType)) as ActionType[])[item.ItemId];
        ViewModel.OnCommandClicked(type);
        base.OnOptionsItemSelected(item);
        return false;
    }

    protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
    {
        base.OnActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        Android.Net.Uri uri = data.Data;
        if (requestCode == SalmonVaultManager.REQUEST_OPEN_VAULT_DIR)
        {
            ActivityCommon.SetUriPermissions(data, uri);
            IRealFile file = ServiceLocator.GetInstance().Resolve<IFileService>().GetFile(uri.ToString(), true);
            Action<string> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
            callback(file.Path);
        }
        else if (requestCode == SalmonVaultManager.REQUEST_CREATE_VAULT_DIR)
        {
            ActivityCommon.SetUriPermissions(data, uri);
            IRealFile file = ServiceLocator.GetInstance().Resolve<IFileService>().GetFile(uri.ToString(), true);
            Action<string> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
            callback(file.Path);
        }
        else if (requestCode == SalmonVaultManager.REQUEST_IMPORT_FILES)
        {
            string[] filesToImport;
            try
            {
                filesToImport = ActivityCommon.GetFilesFromIntent(this, data);
                Action <string[]> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
                callback(filesToImport);
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                Toast.MakeText(this, Resources.GetString(Resource.String.CouldNotImportFiles), ToastLength.Long).Show();
            }
        }
    }
}
