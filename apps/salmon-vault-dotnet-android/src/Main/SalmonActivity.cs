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
using Android.OS;
using Android.Views;
using AndroidX.AppCompat.App;
using AndroidX.DocumentFile.Provider;
using AndroidX.RecyclerView.Widget;
using Salmon.Vault.Utils;
using Java.Lang;
using Exception = System.Exception;
using Thread = System.Threading.Thread;
using Semaphore = Java.Util.Concurrent.Semaphore;
using AndroidX.Core.View;
using Toolbar = AndroidX.AppCompat.Widget.Toolbar;
using Mku.SalmonFS;
using Mku.Utils;
using Mku.File;

using Mku.Android.File;
using Salmon.Vault.DotNetAndroid;
using Mku.Salmon.Transform;
using Salmon.Transform;
using System.Linq;
using Android.Widget;
using Android.App;
using System.Collections.Generic;
using Salmon.Vault.Extensions;
using Salmon.Vault.Dialog;
using Salmon.Vault.Model;
using Salmon.Vault.Services;
using System;
using System.ComponentModel;
using Salmon.Vault.MAUI.ANDROID;
using System.Threading.Tasks;

namespace Salmon.Vault.Main;

[Activity(Label = "@string/app_name", MainLauncher = true, Theme = "@style/AppTheme",
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
public class SalmonActivity : AppCompatActivity
{
    private static readonly string TAG = typeof(SalmonApplication).Name;

    private static readonly long MAX_FILE_SIZE_TO_SHARE = 50 * 1024 * 1024;
    private static readonly long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;

    private List<SalmonFile> fileItemList = new List<SalmonFile>();

    private Semaphore done = new Semaphore(1);

    private TextView pathText;
    private RecyclerView listView;
    private FileAdapter adapter;
    private View progressLayout;
    private TextView statusText;
    private ProgressBar fileProgress;
    private ProgressBar filesProgress;
    private TextView fileProgressText;
    private TextView filesProgressText;

    private SortType sortType = SortType.Default;
    private SalmonVaultManager manager;

    protected override void OnCreate(Bundle bundle)
    {
        base.OnCreate(bundle);
        SetupServices();
        SetupWindow();
        SetContentView(Resource.Layout.main);
        SetupControls();
        SetupSalmonManager();
    }

    protected void SetupServices()
    {
        ServiceLocator.GetInstance().Register(typeof(ISettingsService), new AndroidSettingsService());
        ServiceLocator.GetInstance().Register(typeof(IFileService), new AndroidFileService(this));
        ServiceLocator.GetInstance().Register(typeof(IFileDialogService), new AndroidFileDialogService(this));
        ServiceLocator.GetInstance().Register(typeof(IWebBrowserService), new AndroidBrowserService());
        ServiceLocator.GetInstance().Register(typeof(IKeyboardService), new AndroidKeyboardService(this));
    }

    private void SetupWindow()
    {
        Window.SetFlags(WindowManagerFlags.Secure, WindowManagerFlags.Secure);
        WindowUtils.UiActivity = this;
    }

    private void SetupControls()
    {
        fileProgress = (ProgressBar)FindViewById(Resource.Id.fileProgress);
        filesProgress = (ProgressBar)FindViewById(Resource.Id.filesProgress);
        fileProgressText = (TextView)FindViewById(Resource.Id.fileProgressText);
        filesProgressText = (TextView)FindViewById(Resource.Id.filesProgressText);

        statusText = (TextView)FindViewById(Resource.Id.status);
        progressLayout = FindViewById(Resource.Id.progress_layout);
        progressLayout.Visibility = ViewStates.Gone;
        pathText = (TextView)FindViewById(Resource.Id.path);
        pathText.Text = "";
        listView = (RecyclerView)FindViewById(Resource.Id.list);
        listView.SetLayoutManager(new LinearLayoutManager(this));
        listView.AddItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.Vertical));
        RegisterForContextMenu(listView);
        adapter = CreateAdapter();
        adapter.OnCacheCleared += (sender, e) => ClearRecyclerViewCache();
        listView.SetAdapter(adapter);
        Toolbar toolbar = (Toolbar)FindViewById(Resource.Id.toolbar);
        SetSupportActionBar(toolbar);
        SupportActionBar.SetDisplayShowTitleEnabled(true);
        SupportActionBar.SetDisplayUseLogoEnabled(true);
        SupportActionBar.SetLogo(Resource.Drawable.logo_48x48);
    }

    private void ClearRecyclerViewCache()
    {
        listView.GetRecycledViewPool().Clear();
        listView.SetRecycledViewPool(new RecyclerView.RecycledViewPool());
    }

    protected FileAdapter CreateAdapter()
    {
        return new FileAdapter(this, fileItemList, (int pos) =>
        {
            try
            {
                return OpenItem(pos);
            }
            catch (Exception exception)
            {
                exception.PrintStackTrace();
            }
            return false;
        });
    }

    private void SetupSalmonManager()
    {
        try
        {

            SalmonNativeTransformer.NativeProxy = new AndroidNativeProxy();
            AndroidDrive.Initialize(this.ApplicationContext);
            SalmonDriveManager.VirtualDriveClass = typeof(AndroidDrive);

            manager = CreateVaultManager();
            manager.PromptExitOnBack = true;
            manager.OpenListItem = OpenListItem;
            manager.PropertyChanged += Manager_PropertyChanged;
            manager.UpdateListItem = UpdateListItem;
            manager.OnFileItemAdded = FileItemAdded;
            adapter.PropertyChanged += Adapter_PropertyChanged;
            WindowUtils.RunOnMainThread(() =>
            {
                manager.Initialize();
            }, 1000);
        }
        catch (Exception e)
        {
            e.PrintStackTrace();
        }
    }

    private void UpdateListItem(SalmonFile file)
    {
        int index = fileItemList.IndexOf(file);
        if (index >= 0)
            adapter.NotifyItemChanged(index);
    }

    private void Manager_PropertyChanged(object sender, PropertyChangedEventArgs e)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            if (e.PropertyName == "FileItemList")
            {
                UpdateFileAdapter();

                adapter.SelectAll(false);
                adapter.SetMultiSelect(false);
            }
            else if (e.PropertyName.Equals("CurrentItem"))
            {
                SelectItem(manager.CurrentItem);
            }
            else if (e.PropertyName == "SelectedFiles")
            {
                if (manager.SelectedFiles.Count == 0)
                {
                    adapter.SelectAll(false);
                    adapter.SetMultiSelect(false);
                }
            }
            else if (e.PropertyName == "Status")
            {
                statusText.Text = manager.Status;

            }
            else if (e.PropertyName == "IsJobRunning")
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    if (manager.FileManagerMode != SalmonVaultManager.Mode.Search)
                    {
                        progressLayout.Visibility = manager.IsJobRunning ? ViewStates.Visible : ViewStates.Gone;
                    }
                    if (!manager.IsJobRunning)
                        statusText.Text = "";
                }, manager.IsJobRunning ? 0 : 1000);
            }
            else if (e.PropertyName == "Path")
            {
                pathText.Text = manager.Path;
                listView.ScrollToPosition(0);
            }
            else if (e.PropertyName == "FileProgress")
            {
                fileProgress.Progress = (int)(manager.FileProgress * 100);
                fileProgressText.Text = fileProgress.Progress + " %";
            }
            else if (e.PropertyName == "FilesProgress")
            {
                filesProgress.Progress = (int)(manager.FilesProgress * 100);
                filesProgressText.Text = filesProgress.Progress + " %";
            }
        });
    }

    private void SelectItem(SalmonFile file)
    {
        try
        {
            int index = 0;
            foreach (SalmonFile viewFile in fileItemList)
            {
                if (viewFile == file)
                {
                    int finalIndex = index;
                    WindowUtils.RunOnMainThread(() =>
                    {
                        try
                        {
                            listView.ScrollToPosition(finalIndex);
                        }
                        catch (Exception ex)
                        {
                            ex.PrintStackTrace();
                        }
                    });
                    break;
                }
                index++;
            }
        }
        catch (Exception ex)
        {
            ex.PrintStackTrace();
        }
    }

    private void Adapter_PropertyChanged(object sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == "SelectedFiles")
        {
            manager.SelectedFiles.Clear();
            foreach (SalmonFile file in adapter.SelectedFiles)
                manager.SelectedFiles.Add(file);
        }
    }

    private void UpdateFileAdapter()
    {
        if (manager.FileItemList == null)
        {
            fileItemList.Clear();
            adapter.NotifyDataSetChanged();
        }
        else
        {
            fileItemList.Clear();
            fileItemList.AddRange(manager.FileItemList);
            adapter.NotifyDataSetChanged();
        }
    }

    private void FileItemAdded(int position, SalmonFile file)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            fileItemList.Insert(position, file);
            adapter.NotifyItemInserted(position);
        });
    }

    public override bool OnPrepareOptionsMenu(IMenu menu)
    {
        MenuCompat.SetGroupDividerEnabled(menu, true);
        menu.Clear();

        menu.Add(1, ActionType.OPEN_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.OpenVault))
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(1, ActionType.CREATE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.NewVault))
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(1, ActionType.CLOSE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.CloseVault))
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(1, ActionType.CHANGE_PASSWORD.Ordinal(), 0, Resources.GetString(Resource.String.ChangePasswordTitle))
                .SetShowAsAction(ShowAsAction.Never);

        if (manager.IsJobRunning)
        {
            menu.Add(2, ActionType.STOP.Ordinal(), 0, Resources.GetString(Resource.String.Stop))
                    .SetShowAsAction(ShowAsAction.Never);
        }

        if (manager.FileManagerMode == SalmonVaultManager.Mode.Copy || manager.FileManagerMode == SalmonVaultManager.Mode.Move)
        {
            menu.Add(3, ActionType.PASTE.Ordinal(), 0, Resources.GetString(Resource.String.Paste));
        }
        menu.Add(3, ActionType.IMPORT.Ordinal(), 0, Resources.GetString(Resource.String.ImportFiles))
                .SetIcon(Android.Resource.Drawable.IcMenuAdd)
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(3, ActionType.NEW_FOLDER.Ordinal(), 0, GetString(Resource.String.NewFolder))
                .SetIcon(Android.Resource.Drawable.IcInputAdd);

        if (adapter.GetMode() == FileAdapter.Mode.MULTI_SELECT)
        {
            menu.Add(3, ActionType.COPY.Ordinal(), 0, Resources.GetString(Resource.String.Copy));
            menu.Add(3, ActionType.CUT.Ordinal(), 0, Resources.GetString(Resource.String.Cut));
            menu.Add(3, ActionType.DELETE.Ordinal(), 0, Resources.GetString(Resource.String.Delete));
            menu.Add(3, ActionType.EXPORT.Ordinal(), 0, Resources.GetString(Resource.String.ExportFiles));
            menu.Add(3, ActionType.EXPORT_AND_DELETE.Ordinal(), 0, Resources.GetString(Resource.String.ExportAndDeleteFiles));
        }

        menu.Add(4, ActionType.REFRESH.Ordinal(), 0, Resources.GetString(Resource.String.Refresh))
                .SetIcon(Android.Resource.Drawable.IcMenuRotate)
                .SetShowAsAction(ShowAsAction.Never);
        menu.Add(4, ActionType.SORT.Ordinal(), 0, Resources.GetString(Resource.String.Sort))
                .SetIcon(Android.Resource.Drawable.IcMenuSortAlphabetically);
        menu.Add(4, ActionType.SEARCH.Ordinal(), 0, Resources.GetString(Resource.String.Search))
                .SetIcon(Android.Resource.Drawable.IcMenuSearch);

        if (adapter.GetMode() == FileAdapter.Mode.SINGLE_SELECT)
        {
            menu.Add(4, ActionType.MULTI_SELECT.Ordinal(), 0, GetString(Resource.String.MultiSelect))
                    .SetIcon(Android.Resource.Drawable.IcMenuAgenda);
        }
        else
        {
            menu.Add(4, ActionType.SELECT_ALL.Ordinal(), 0, GetString(Resource.String.SelectAll))
                    .SetIcon(Android.Resource.Drawable.IcMenuAgenda);
            menu.Add(4, ActionType.UNSELECT_ALL.Ordinal(), 0, GetString(Resource.String.UnselectAll))
                    .SetIcon(Android.Resource.Drawable.IcMenuAgenda);
            menu.Add(4, ActionType.SINGLE_SELECT.Ordinal(), 0, GetString(Resource.String.SingleSelect))
                    .SetIcon(Android.Resource.Drawable.IcMenuAgenda);
        }

        if (SalmonDriveManager.Drive != null)
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
        switch ((ActionType)item.ItemId)
        {
            case ActionType.OPEN_VAULT:
                SalmonDialogs.PromptOpenVault();
                break;
            case ActionType.CREATE_VAULT:
                SalmonDialogs.PromptCreateVault();
                break;
            case ActionType.CLOSE_VAULT:
                manager.CloseVault();
                break;
            case ActionType.CHANGE_PASSWORD:
                SalmonDialogs.PromptChangePassword();
                break;

            case ActionType.REFRESH:
                manager.Refresh();
                return true;
            case ActionType.IMPORT:
                SalmonDialogs.PromptImportFiles();
                return true;
            case ActionType.EXPORT:
                manager.ExportSelectedFiles(false);
                return true;
            case ActionType.EXPORT_AND_DELETE:
                manager.ExportSelectedFiles(true);
                return true;
            case ActionType.NEW_FOLDER:
                SalmonDialogs.PromptNewFolder();
                return true;
            case ActionType.COPY:
                manager.CopySelectedFiles();
                adapter.SetMultiSelect(false, false);
                return true;
            case ActionType.CUT:
                manager.CutSelectedFiles();
                adapter.SetMultiSelect(false, false);
                return true;
            case ActionType.DELETE:
                SalmonDialogs.PromptDelete();
                return true;
            case ActionType.PASTE:
                manager.PasteSelected();
                return true;
            case ActionType.SELECT_ALL:
                SelectAll(true);
                return true;
            case ActionType.UNSELECT_ALL:
                SelectAll(false);
                return true;
            case ActionType.SEARCH:
                SalmonDialogs.PromptSearch();
                return true;
            case ActionType.MULTI_SELECT:
                adapter.SetMultiSelect(true);
                return true;
            case ActionType.SINGLE_SELECT:
                adapter.SetMultiSelect(false);
                return true;
            case ActionType.STOP:
                manager.StopOperation();
                return true;
            case ActionType.SORT:
                PromptSortFiles();
                break;

            case ActionType.IMPORT_AUTH:
                SalmonDialogs.PromptImportAuth();
                break;
            case ActionType.EXPORT_AUTH:
                SalmonDialogs.PromptExportAuth();
                break;
            case ActionType.REVOKE_AUTH:
                SalmonDialogs.PromptRevokeAuth();
                break;
            case ActionType.DISPLAY_AUTH_ID:
                SalmonDialogs.OnDisplayAuthID();
                break;

            case ActionType.SETTINGS:
                StartSettings();
                return true;
            case ActionType.ABOUT:
                SalmonDialogs.PromptAbout();
                return true;
            case ActionType.EXIT:
                Exit();
                return true;
        }
        base.OnOptionsItemSelected(item);
        return false;
    }

    public override void OnCreateContextMenu(IContextMenu menu, View v, IContextMenuContextMenuInfo menuInfo)
    {
        menu.SetHeaderTitle(GetString(Resource.String.Action));
        menu.Add(0, ActionType.VIEW.Ordinal(), 0, GetString(Resource.String.View))
                .SetIcon(Android.Resource.Drawable.IcMenuView);
        menu.Add(0, ActionType.VIEW_AS_TEXT.Ordinal(), 0, GetString(Resource.String.ViewAsText))
                .SetIcon(Android.Resource.Drawable.IcMenuView);
        menu.Add(0, ActionType.VIEW_EXTERNAL.Ordinal(), 0, GetString(Resource.String.ViewExternal))
                .SetIcon(Android.Resource.Drawable.IcMenuView);
        menu.Add(0, ActionType.EDIT.Ordinal(), 0, GetString(Resource.String.EditExternal))
                .SetIcon(Android.Resource.Drawable.IcMenuSend);
        menu.Add(0, ActionType.SHARE.Ordinal(), 0, GetString(Resource.String.ShareExternal))
                .SetIcon(Android.Resource.Drawable.IcMenuSend);

        menu.Add(1, ActionType.COPY.Ordinal(), 0, GetString(Resource.String.Copy))
                .SetIcon(Android.Resource.Drawable.IcMenuDelete);
        menu.Add(1, ActionType.CUT.Ordinal(), 0, GetString(Resource.String.Cut))
                .SetIcon(Android.Resource.Drawable.IcMenuDelete);
        menu.Add(1, ActionType.DELETE.Ordinal(), 0, GetString(Resource.String.Delete))
                .SetIcon(Android.Resource.Drawable.IcMenuDelete);
        menu.Add(1, ActionType.RENAME.Ordinal(), 0, GetString(Resource.String.Rename))
                .SetIcon(Android.Resource.Drawable.IcMenuEdit);
        menu.Add(1, ActionType.EXPORT.Ordinal(), 0, GetString(Resource.String.ExportFiles))
                .SetIcon(Android.Resource.Drawable.ButtonMinus);
        menu.Add(1, ActionType.EXPORT_AND_DELETE.Ordinal(), 0, GetString(Resource.String.ExportAndDeleteFiles))
                .SetIcon(Android.Resource.Drawable.ButtonMinus);

        menu.Add(2, ActionType.PROPERTIES.Ordinal(), 0, GetString(Resource.String.Properties))
                .SetIcon(Android.Resource.Drawable.IcDialogInfo);
    }

    public override bool OnContextItemSelected(IMenuItem item)
    {
        int position = adapter.GetPosition();
        SalmonFile ifile = fileItemList[position];
        manager.SelectedFiles = new HashSet<SalmonFile>();
        manager.SelectedFiles.Add(ifile);

        switch ((ActionType)item.ItemId)
        {
            case ActionType.VIEW:
                OpenItem(position);
                break;
            case ActionType.VIEW_AS_TEXT:
                StartTextViewer(position);
                break;
            case ActionType.VIEW_EXTERNAL:
                OpenWith(ifile, ActionType.VIEW_EXTERNAL.Ordinal());
                break;
            case ActionType.EDIT:
                OpenWith(ifile, ActionType.EDIT.Ordinal());
                break;
            case ActionType.SHARE:
                OpenWith(ifile, ActionType.SHARE.Ordinal());
                break;
            case ActionType.EXPORT:
                manager.ExportSelectedFiles(false);
                break;
            case ActionType.EXPORT_AND_DELETE:
                manager.ExportSelectedFiles(true);
                break;
            case ActionType.COPY:
                manager.CopySelectedFiles();
                adapter.SetMultiSelect(false, false);
                break;
            case ActionType.CUT:
                manager.CutSelectedFiles();
                adapter.SetMultiSelect(false, false);
                break;
            case ActionType.DELETE:
                SalmonDialogs.PromptDelete();
                break;
            case ActionType.RENAME:
                SalmonDialogs.PromptRenameFile(ifile);
                break;
            case ActionType.PROPERTIES:
                SalmonDialogs.ShowProperties(ifile);
                break;
        }
        return true;
    }

    private bool OpenItem(int position)
    {
        return manager.OpenItem(fileItemList[position]);
    }

    private void SelectAll(bool value)
    {
        adapter.SelectAll(value);
    }

    public void ShowTaskMessage(string msg)
    {
        RunOnUiThread(() => statusText.Text = msg == null ? "" : msg);
    }

    private void SortFiles(SortType sortType)
    {
        this.sortType = sortType;
        switch (sortType)
        {
            case SortType.Default:
                fileItemList.Sort(SalmonFileComparators.DefaultComparator);
                break;
            case SortType.Name:
                fileItemList.Sort(SalmonFileComparators.FilenameAscComparator);
                break;
            case SortType.NameDesc:
                fileItemList.Sort(SalmonFileComparators.FilenameDescComparator);
                break;
            case SortType.Size:
                fileItemList.Sort(SalmonFileComparators.SizeAscComparator);
                break;
            case SortType.SizeDesc:
                fileItemList.Sort(SalmonFileComparators.SizeDescComparator);
                break;
            case SortType.Type:
                fileItemList.Sort(SalmonFileComparators.TypeAscComparator);
                break;
            case SortType.TypeDesc:
                fileItemList.Sort(SalmonFileComparators.TypeDescComparator);
                break;
            case SortType.Date:
                fileItemList.Sort(SalmonFileComparators.DateAscComparator);
                break;
            case SortType.DateDesc:
                fileItemList.Sort(SalmonFileComparators.DateDescComparator);
                break;
        }
    }

    private void PromptSortFiles()
    {
        List<string> sortTypes = new List<string>();
        SortType[] values = System.Enum.GetValues(typeof(SortType)).Cast<SortType>().ToArray();
        sortTypes.Add(values[0].ToString());
        for (int i = 1; i < values.Length; i++)
        {
            sortTypes.Add((i % 2 == 1 ? "↓" : "↑") + " " + values[i - (i + 1) % 2].ToString());
        }

        ArrayAdapter<string> itemsAdapter = new ArrayAdapter<string>(
                this, Android.Resource.Layout.SimpleListItemActivated1, sortTypes.ToArray());
        SalmonDialog.PromptSingleValue(itemsAdapter, GetString(Resource.String.Sort), -1,
            (AndroidX.AppCompat.App.AlertDialog dialog, int which) =>
                {
                    SortFiles(values[which]);
                    adapter.NotifyDataSetChanged();
                    dialog.Dismiss();
                }
        );
    }

    private void Exit()
    {
        Finish();
    }

    protected void StartSettings()
    {
        Intent intent = new Intent(this, typeof(SettingsActivity));
        StartActivity(intent);
    }

    private void OpenWith(SalmonFile salmonFile, int action)
    {
        try
        {
            if (salmonFile.Size > MAX_FILE_SIZE_TO_SHARE)
            {
                Toast toast = Toast.MakeText(this, GetString(Resource.String.FileSizeTooLarge), ToastLength.Long);
                toast.Show();
                return;
            }
            if (salmonFile.Size > MEDIUM_FILE_SIZE_TO_SHARE)
            {
                Toast toast = Toast.MakeText(this, GetString(Resource.String.PleaseWaitWhileDecrypting), ToastLength.Long);
                toast.SetGravity(GravityFlags.Center, 0, 0);
                toast.Show();
            }
            Task.Run(() =>
            {
                try
                {
                    ExternalAppChooser.ChooseApp(this, salmonFile, action, ReimportSharedFile);
                }
                catch (Exception exception)
                {
                    exception.PrintStackTrace();
                }
            });
        }
        catch (Exception exception)
        {
            exception.PrintStackTrace();
        }
    }

    private void ReimportSharedFile(Android.Net.Uri uri, AndroidSharedFileObserver fileObserver)
    {
        try
        {
            done.Acquire(1);
        }
        catch (InterruptedException e)
        {
            e.PrintStackTrace();
        }
        if (SalmonDriveManager.Drive.VirtualRoot == null || !SalmonDriveManager.Drive.IsAuthenticated)
            return;
        DocumentFile docFile = DocumentFile.FromSingleUri(SalmonApplication.GetInstance().ApplicationContext, uri);
        IRealFile realFile = new AndroidFile(docFile, this);
        if (realFile == null)
            return;
        SalmonFile oldSalmonFile = fileObserver.GetSalmonFile();
        SalmonFile parentDir = oldSalmonFile.Parent;

        manager.ImportFiles(new IRealFile[] { realFile }, parentDir, false, (SalmonFile[] importedSalmonFiles) =>
        {
            if (!importedSalmonFiles[0].Exists)
                return;
            // in case the list is meanwhile refreshed
            oldSalmonFile = fileItemList.FirstOrDefault(x => x.RealFile.BaseName.Equals(oldSalmonFile.RealFile.BaseName), null);
            if (oldSalmonFile == null)
                return;
            if (oldSalmonFile.Exists)
                oldSalmonFile.Delete();
            if (oldSalmonFile.Exists)
                return;
            importedSalmonFiles[0].Rename(oldSalmonFile.BaseName);

            fileObserver.SetSalmonFile(importedSalmonFiles[0]);
            RunOnUiThread(() =>
            {
                int index = fileItemList.IndexOf(oldSalmonFile);
                if (index < 0)
                    return;
                fileItemList.Remove(oldSalmonFile);
                fileItemList.Insert(index, importedSalmonFiles[0]);

                manager.FileItemList.Remove(oldSalmonFile);
                manager.FileItemList.Insert(index, importedSalmonFiles[0]);

                adapter.NotifyItemChanged(index);

                Toast.MakeText(this, GetString(Resource.String.FileSavedInSalmonVault), ToastLength.Long).Show();
            });
            done.Release(1);
        });
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
            string[] filesToImport = ActivityCommon.GetFilesFromIntent(this, data);
            Action<string[]> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
            callback(filesToImport);
        }
        else if (requestCode == SalmonVaultManager.REQUEST_IMPORT_AUTH_FILE)
        {
            string[] files = ActivityCommon.GetFilesFromIntent(this, data);
            string file = files != null ? files[0] : null;
            if (file == null)
                return;
            Action<string> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
            callback(file);
        }
        else if (requestCode == SalmonVaultManager.REQUEST_EXPORT_AUTH_FILE)
        {
            string[] dirs = ActivityCommon.GetFilesFromIntent(this, data);
            string dir = dirs != null ? dirs[0] : null;
            if (dir == null)
                return;
            Action<string[]> callback = ServiceLocator.GetInstance().Resolve<IFileDialogService>().GetCallback(requestCode);
            callback(new string[] { dir, SalmonDrive.AuthConfigFilename });
        }
    }

    public bool OpenListItem(SalmonFile file)
    {

        try
        {
            if (SalmonFileUtils.IsVideo(file.BaseName) || SalmonFileUtils.IsAudio(file.BaseName))
            {
                StartMediaPlayer(fileItemList.IndexOf(file));
                return true;
            }
            else if (SalmonFileUtils.IsImage(file.BaseName))
            {
                StartWebViewer(fileItemList.IndexOf(file));
                return true;
            }
            else if (SalmonFileUtils.IsText(file.BaseName))
            {
                StartWebViewer(fileItemList.IndexOf(file));
                return true;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            SalmonDialog.PromptDialog("Error", "Could not open: " + ex.Message);
        }
        return false;
    }

    private void Logout()
    {
        try
        {
            SalmonDriveManager.Drive.Close();
        }
        catch (Exception ex)
        {
            ex.PrintStackTrace();
        }
    }

    public void StartMediaPlayer(int position)
    {
        List<SalmonFile> salmonFiles = new List<SalmonFile>();
        int pos = 0;
        int i = 0;
        foreach (SalmonFile file in fileItemList)
        {
            string filename;
            try
            {
                filename = file.BaseName;
                if (SalmonFileUtils.IsVideo(filename) || SalmonFileUtils.IsAudio(filename))
                {
                    salmonFiles.Add(file);
                }
                if (i == position)
                    pos = salmonFiles.Count - 1;
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
            i++;
        }

        Intent intent = GetMediaPlayerIntent();
        MediaPlayerActivity.SetMediaFiles(pos, salmonFiles.ToArray());
        intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
        StartActivity(intent);
    }

    protected Intent GetMediaPlayerIntent()
    {
        return new Intent(this, typeof(MediaPlayerActivity));
    }

    private void StartTextViewer(int position)
    {
        try
        {
            if (fileItemList[position].Size > 1 * 1024 * 1024)
            {
                Toast.MakeText(this, "File too large", ToastLength.Long).Show();
                return;
            }
            StartWebViewer(position);
        }
        catch (Exception e)
        {
            e.PrintStackTrace();
        }
    }

    private void StartWebViewer(int position)
    {
        try
        {
            List<SalmonFile> salmonFiles = new List<SalmonFile>();
            SalmonFile file = fileItemList[position];
            string filename = file.BaseName;

            int pos = 0;
            int i = 0;
            foreach (SalmonFile listFile in fileItemList)
            {
                try
                {
                    string listFilename = listFile.BaseName;
                    if (i != position &&
                            (SalmonFileUtils.IsImage(filename) && SalmonFileUtils.IsImage(listFilename))
                            || (SalmonFileUtils.IsText(filename) && SalmonFileUtils.IsText(listFilename)))
                    {
                        salmonFiles.Add(listFile);
                    }
                    if (i == position)
                    {
                        salmonFiles.Add(listFile);
                        pos = salmonFiles.Count - 1;
                    }
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
                i++;
            }
            Intent intent = GetWebViewerIntent();
            SalmonFile selectedFile = fileItemList[position];
            WebViewerActivity.SetContentFiles(pos, salmonFiles.ToArray());
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
            StartActivity(intent);
        }
        catch (Exception e)
        {
            e.PrintStackTrace();
            Toast.MakeText(this, "Could not open viewer: " + e.Message, ToastLength.Long).Show();
        }
    }

    protected Intent GetWebViewerIntent()
    {
        return new Intent(this, typeof(WebViewerActivity));
    }

    protected override void OnDestroy()
    {
        Logout();
        adapter.Stop();
        base.OnDestroy();
    }

    public override void OnBackPressed()
    {
        if (adapter.GetMode() == FileAdapter.Mode.MULTI_SELECT)
        {
            adapter.SetMultiSelect(false);
            adapter.SelectAll(false);
        }
        else
            manager.GoBack();
    }

    public enum SortType
    {
        Default, Name, NameDesc, Size, SizeDesc, Type, TypeDesc, Date, DateDesc
    }

    protected SalmonVaultManager CreateVaultManager()
    {
        SalmonNativeTransformer.NativeProxy = new AndroidNativeProxy();
        AndroidDrive.Initialize(this.ApplicationContext);
        SalmonDriveManager.VirtualDriveClass = typeof(AndroidDrive);
        return SalmonVaultManager.Instance;
    }
}