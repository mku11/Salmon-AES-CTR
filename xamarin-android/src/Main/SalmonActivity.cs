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
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Android.Views;
using Android.Webkit;
using Android.Widget;
using AndroidX.AppCompat.App;
using AndroidX.Core.App;
using AndroidX.DocumentFile.Provider;
using AndroidX.RecyclerView.Widget;
using Salmon.Droid.Utils;
using Salmon.Droid.FS;
using Salmon.FS;
using Java.Lang;

using System;
using System.Collections.Generic;
using Exception = System.Exception;
using Thread = System.Threading.Thread;

using Java.Util.Concurrent;
using Salmon.Streams;
using Salmon.Droid.Media;
using Salmon.Net.FS;
using Semaphore = Java.Util.Concurrent.Semaphore;
using Java.IO;
using AndroidX.Core.View;
using Toolbar = AndroidX.AppCompat.Widget.Toolbar;
using System.Linq;
using AndroidX.Core.Content;

namespace Salmon.Droid.Main
{
    [Activity(Label = "@string/app_name", MainLauncher = true,
        ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    public class SalmonActivity : AppCompatActivity
    {
        private static readonly string TAG = typeof(SalmonApplication).Name;
        public static readonly int REQUEST_OPEN_VAULT_DIR = 1000;
        public static readonly int REQUEST_CREATE_VAULT_DIR = 1001;
        public static readonly int REQUEST_IMPORT_FILES = 1002;
        public static readonly int REQUEST_EXPORT_DIR = 1003;
        public static readonly int REQUEST_IMPORT_AUTH_FILE = 1004;
        public static readonly int REQUEST_EXPORT_AUTH_FILE = 1005;
        public static readonly string SEQUENCER_DIR_NAME = ".salmon";
        public static readonly string SEQUENCER_FILE_NAME = "config.xml";

        private static readonly long MAX_FILE_SIZE_TO_SHARE = 50 * 1024 * 1024;
        private static readonly long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;
        private static readonly int BUFFER_SIZE = 1 * 1024 * 1024;
        private static readonly int THREADS = 4;

        public SalmonFile rootDir;
        public SalmonFile currDir;
        private List<SalmonFile> fileItemList = new List<SalmonFile>();
        private IExecutorService executor = Executors.NewFixedThreadPool(1);
        private Semaphore done = new Semaphore(1);

        private TextView pathText;
        private RecyclerView listView;
        private FileAdapter adapter;
        private View statusControlLayout;
        private TextView statusText;
        private PieProgress fileProgress;
        private PieProgress filesProgress;
        private SalmonFile[] salmonFiles;
        private FileCommander fileCommander;
        private SalmonFile[] copyFiles;
        private string exportAuthID;
        private Mode mode = Mode.Browse;
        private SortType sortType = SortType.Name;

        protected override void OnCreate(Bundle bundle)
        {
            base.OnCreate(bundle);
            SetupWindow();
            SetContentView(Resource.Layout.main);
            SetupControls();
            SetupFileCommander();
            SetupListeners();
            LoadSettings();
            SetupSalmonManager();
            SetupRootDir();
        }

        private void SetupWindow()
        {
            Window.SetFlags(WindowManagerFlags.Secure, WindowManagerFlags.Secure);
        }

        private void SetupFileCommander()
        {
            fileCommander = new FileCommander(BUFFER_SIZE, THREADS);
        }

        private void LoadSettings()
        {
            SalmonStream.SetProviderType(SettingsActivity.getProviderType(this));
            SalmonFileExporter.SetEnableLog(SettingsActivity.getEnableLog(this));
            SalmonFileExporter.SetEnableLogDetails(SettingsActivity.getEnableLogDetails(this));
            SalmonFileImporter.SetEnableLog(SettingsActivity.getEnableLog(this));
            SalmonFileImporter.SetEnableLogDetails(SettingsActivity.getEnableLogDetails(this));
            SalmonStream.SetEnableLogDetails(SettingsActivity.getEnableLog(this));
            SalmonMediaDataSource.SetEnableLog(SettingsActivity.getEnableLog(this));
            WindowUtils.RemoveFromRecents(this, SettingsActivity.getExcludeFromRecents(this));
        }

        private void SetupListeners()
        {
            fileCommander.SetImporterProgressListener((IRealFile file, long bytesRead, long totalBytesRead, string message) =>
                    RunOnUiThread(() =>
                    {
                        statusText.Text = message;
                        fileProgress.SetProgress((int)(bytesRead * 100.0F / totalBytesRead));
                    }));

            fileCommander.SetExporterProgressListener((SalmonFile file, long bytesWritten, long totalBytesWritten, string message) =>
                    RunOnUiThread(() =>
                    {
                        statusText.Text = message;
                        fileProgress.SetProgress((int)(bytesWritten * 100.0F / totalBytesWritten));
                    }));
        }

        private void SetupControls()
        {
            fileProgress = (PieProgress)FindViewById(Resource.Id.fileProgress);
            filesProgress = (PieProgress)FindViewById(Resource.Id.filesProgress);
            statusText = (TextView)FindViewById(Resource.Id.status);
            statusControlLayout = FindViewById(Resource.Id.status_control_layout);
            statusControlLayout.Visibility = ViewStates.Gone;
            pathText = (TextView)FindViewById(Resource.Id.path);
            pathText.Text = "";
            listView = (RecyclerView)FindViewById(Resource.Id.list);
            listView.SetLayoutManager(new LinearLayoutManager(this));
            listView.AddItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.Vertical));
            RegisterForContextMenu(listView);
            adapter = CreateAdapter();
            listView.SetAdapter(adapter);
            Toolbar toolbar = (Toolbar)FindViewById(Resource.Id.toolbar);
            SetSupportActionBar(toolbar);
            SupportActionBar.SetDisplayShowTitleEnabled(true);
            SupportActionBar.SetDisplayUseLogoEnabled(true);
            SupportActionBar.SetLogo(Resource.Drawable.logo_48x48);
        }

        private FileAdapter CreateAdapter()
        {
            return new FileAdapter(this, fileItemList, (int pos) =>
            {
                try
                {
                    return OpenFile(pos);
                }
                catch (Exception exception)
                {
                    exception.PrintStackTrace();
                }
                return false;
            });
        }

        protected void SetupRootDir()
        {
            string vaultLocation = SettingsActivity.GetVaultLocation(this);
            try
            {
                SalmonDriveManager.OpenDrive(vaultLocation);
                SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
                rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                currDir = rootDir;
                if (rootDir == null)
                {
                    OnOpenVault();
                    return;
                }
            }
            catch (SalmonAuthException e)
            {
                CheckCredentials();
                return;
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
            Refresh();
        }

        public void Refresh()
        {
            if (CheckFileSearcher())
                return;
            if (SalmonDriveManager.GetDrive() == null)
                return;
            try
            {
                if (SalmonDriveManager.GetDrive().GetVirtualRoot() == null || !SalmonDriveManager.GetDrive().GetVirtualRoot().Exists())
                {
                    OnOpenVault();
                    return;
                }
                if (!SalmonDriveManager.GetDrive().IsAuthenticated())
                {
                    CheckCredentials();
                    return;
                }
                executor.Submit(new Runnable(() =>
                {
                    if (mode != Mode.Search)
                        salmonFiles = currDir.ListFiles();
                    DisplayFiles(false);
                }));
            }
            catch (SalmonAuthException e)
            {
                e.PrintStackTrace();
                CheckCredentials();
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
        }

        private bool CheckFileSearcher()
        {
            if (fileCommander.IsFileSearcherRunning())
            {
                Toast.MakeText(this, GetString(Resource.String.AnotherProcessRunning), ToastLength.Long).Show();
                return true;
            }
            return false;
        }

        private void DisplayFiles(bool reset)
        {
            RunOnUiThread(() =>
            {
                try
                {
                    SetPath(currDir.GetPath());
                }
                catch (Exception exception)
                {
                    exception.PrintStackTrace();
                }
                fileItemList.Clear();
                if (reset)
                {
                    adapter.ResetCache(listView);
                }
                adapter.NotifyDataSetChanged();
                fileItemList.AddRange(salmonFiles);
                if (mode == Mode.Browse)
                    SortFiles(SortType.Default);
                adapter.NotifyDataSetChanged();
            });
        }

        private void SetupSalmonManager()
        {
            try
            {
                SalmonDriveManager.SetVirtualDriveClass(typeof(AndroidDrive));
                if (SalmonDriveManager.GetSequencer() != null)
                    SalmonDriveManager.GetSequencer().Dispose();
                SetupFileSequencer();
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
            }
        }

        private void SetupFileSequencer()
        {
            string dirPath = NoBackupFilesDir + File.Separator + SEQUENCER_DIR_NAME;
            string filePath = dirPath + File.Separator + SEQUENCER_FILE_NAME;
            IRealFile dirFile = new DotNetFile(dirPath);
            if (!dirFile.Exists())
                dirFile.Mkdir();
            IRealFile seqFile = new DotNetFile(filePath);
            FileSequencer sequencer = new FileSequencer(seqFile, new SalmonSequenceParser());
            SalmonDriveManager.SetSequencer(sequencer);
        }

        public override bool OnPrepareOptionsMenu(IMenu menu)
        {
            MenuCompat.SetGroupDividerEnabled(menu, true);
            menu.Clear();

            menu.Add(1, Action.OPEN_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.OpenVault))
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(1, Action.CREATE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.NewVault))
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(1, Action.CLOSE_VAULT.Ordinal(), 0, Resources.GetString(Resource.String.CloseVault))
                    .SetShowAsAction(ShowAsAction.Never);

            if (fileCommander.IsRunning())
            {
                menu.Add(2, Action.STOP.Ordinal(), 0, Resources.GetString(Resource.String.Stop))
                        .SetShowAsAction(ShowAsAction.Never);
            }

            if (mode == Mode.Copy || mode == Mode.Move)
            {
                menu.Add(3, Action.PASTE.Ordinal(), 0, Resources.GetString(Resource.String.Paste));
            }
            menu.Add(3, Action.IMPORT.Ordinal(), 0, Resources.GetString(Resource.String.Import))
                    .SetIcon(Android.Resource.Drawable.IcMenuAdd)
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(3, Action.NEW_FOLDER.Ordinal(), 0, GetString(Resource.String.NewFolder))
                    .SetIcon(Android.Resource.Drawable.IcInputAdd);

            if (adapter.GetMode() == FileAdapter.Mode.MULTI_SELECT)
            {
                menu.Add(3, Action.COPY.Ordinal(), 0, Resources.GetString(Resource.String.Copy));
                menu.Add(3, Action.CUT.Ordinal(), 0, Resources.GetString(Resource.String.Cut));
                menu.Add(3, Action.DELETE.Ordinal(), 0, Resources.GetString(Resource.String.Delete));
                menu.Add(3, Action.EXPORT.Ordinal(), 0, Resources.GetString(Resource.String.Export));
            }

            menu.Add(4, Action.REFRESH.Ordinal(), 0, Resources.GetString(Resource.String.Refresh))
                    .SetIcon(Android.Resource.Drawable.IcMenuRotate)
                    .SetShowAsAction(ShowAsAction.Never);
            menu.Add(4, Action.SORT.Ordinal(), 0, Resources.GetString(Resource.String.Sort))
                    .SetIcon(Android.Resource.Drawable.IcMenuSortAlphabetically);
            menu.Add(4, Action.SEARCH.Ordinal(), 0, Resources.GetString(Resource.String.Search))
                    .SetIcon(Android.Resource.Drawable.IcMenuSearch);
            if (adapter.GetMode() == FileAdapter.Mode.SINGLE_SELECT)
                menu.Add(4, Action.MULTI_SELECT.Ordinal(), 0, GetString(Resource.String.MultiSelect))
                        .SetIcon(Android.Resource.Drawable.IcMenuAgenda);
            else
                menu.Add(4, Action.SINGLE_SELECT.Ordinal(), 0, GetString(Resource.String.SingleSelect))
                        .SetIcon(Android.Resource.Drawable.IcMenuAgenda);

            if (SalmonDriveManager.GetDrive() != null)
            {
                menu.Add(5, Action.IMPORT_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.ImportAuthFile))
                        .SetShowAsAction(ShowAsAction.Never);
                menu.Add(5, Action.EXPORT_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.ExportAuthFile))
                        .SetShowAsAction(ShowAsAction.Never);
                menu.Add(5, Action.REVOKE_AUTH.Ordinal(), 0, Resources.GetString(Resource.String.RevokeAuth))
                        .SetShowAsAction(ShowAsAction.Never);
                menu.Add(5, Action.DISPLAY_AUTH_ID.Ordinal(), 0, Resources.GetString(Resource.String.DisplayAuthID))
                        .SetShowAsAction(ShowAsAction.Never);
            }

            menu.Add(6, Action.SETTINGS.Ordinal(), 0, Resources.GetString(Resource.String.Settings))
                    .SetIcon(Android.Resource.Drawable.IcMenuPreferences);
            menu.Add(6, Action.ABOUT.Ordinal(), 0, Resources.GetString(Resource.String.About))
                    .SetIcon(Android.Resource.Drawable.IcMenuInfoDetails);
            menu.Add(6, Action.EXIT.Ordinal(), 0, Resources.GetString(Resource.String.Exit))
                    .SetIcon(Android.Resource.Drawable.IcMenuCloseClearCancel);

            return base.OnPrepareOptionsMenu(menu);
        }

        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            switch ((Action)item.ItemId)
            {
                case Action.OPEN_VAULT:
                    OnOpenVault();
                    break;
                case Action.CREATE_VAULT:
                    OnCreateVault();
                    break;
                case Action.CLOSE_VAULT:
                    OnCloseVault();
                    break;

                case Action.REFRESH:
                    Refresh();
                    return true;
                case Action.IMPORT:
                    PromptImportFiles();
                    return true;
                case Action.EXPORT:
                    ExportSelectedFiles();
                    return true;
                case Action.NEW_FOLDER:
                    PromptNewFolder();
                    return true;
                case Action.COPY:
                    mode = Mode.Copy;
                    copyFiles = adapter.GetSelectedFiles().ToArray<SalmonFile>();
                    ShowTaskRunning(true, false);
                    ShowTaskMessage(copyFiles.Length + " " + Resources.GetString(Resource.String.ItemsSelectedForCopy));
                    adapter.SetMultiSelect(false);
                    return true;
                case Action.CUT:
                    mode = Mode.Move;
                    copyFiles = adapter.GetSelectedFiles().ToArray<SalmonFile>();
                    ShowTaskRunning(true, false);
                    ShowTaskMessage(copyFiles.Length + " " + Resources.GetString(Resource.String.ItemsSelectedForMove));
                    adapter.SetMultiSelect(false);
                    return true;
                case Action.DELETE:
                    DeleteSelectedFiles();
                    return true;
                case Action.PASTE:
                    PasteSelected();
                    return true;
                case Action.SELECT_ALL:
                    SelectAll(true);
                    return true;
                case Action.UNSELECT_ALL:
                    SelectAll(false);
                    return true;
                case Action.SEARCH:
                    PromptSearch();
                    return true;
                case Action.MULTI_SELECT:
                    adapter.SetMultiSelect(true);
                    return true;
                case Action.SINGLE_SELECT:
                    adapter.SetMultiSelect(false);
                    return true;
                case Action.STOP:
                    fileCommander.CancelJobs();
                    return true;
                case Action.SORT:
                    PromptSortFiles();
                    break;

                case Action.IMPORT_AUTH:
                    OnImportAuth();
                    break;
                case Action.EXPORT_AUTH:
                    OnExportAuth();
                    break;
                case Action.REVOKE_AUTH:
                    OnRevokeAuth();
                    break;
                case Action.DISPLAY_AUTH_ID:
                    OnDisplayAuthID();
                    break;

                case Action.SETTINGS:
                    StartSettings();
                    return true;
                case Action.ABOUT:
                    About();
                    return true;
                case Action.EXIT:
                    Exit();
                    return true;
            }
            base.OnOptionsItemSelected(item);
            return false;
        }


        public override void OnCreateContextMenu(IContextMenu menu, View v, IContextMenuContextMenuInfo menuInfo)
        {
            menu.SetHeaderTitle(GetString(Resource.String.Action));
            menu.Add(0, Action.VIEW.Ordinal(), 0, GetString(Resource.String.View))
                    .SetIcon(Android.Resource.Drawable.IcMenuView);
            menu.Add(0, Action.VIEW_AS_TEXT.Ordinal(), 0, GetString(Resource.String.ViewAsText))
                    .SetIcon(Android.Resource.Drawable.IcMenuView);
            menu.Add(0, Action.VIEW_EXTERNAL.Ordinal(), 0, GetString(Resource.String.ViewExternal))
                    .SetIcon(Android.Resource.Drawable.IcMenuView);
            menu.Add(0, Action.EDIT.Ordinal(), 0, GetString(Resource.String.EditExternal))
                    .SetIcon(Android.Resource.Drawable.IcMenuSend);
            menu.Add(0, Action.SHARE.Ordinal(), 0, GetString(Resource.String.ShareExternal))
                    .SetIcon(Android.Resource.Drawable.IcMenuSend);

            menu.Add(1, Action.COPY.Ordinal(), 0, GetString(Resource.String.Copy))
                    .SetIcon(Android.Resource.Drawable.IcMenuDelete);
            menu.Add(1, Action.CUT.Ordinal(), 0, GetString(Resource.String.Cut))
                    .SetIcon(Android.Resource.Drawable.IcMenuDelete);
            menu.Add(1, Action.DELETE.Ordinal(), 0, GetString(Resource.String.Delete))
                    .SetIcon(Android.Resource.Drawable.IcMenuDelete);
            menu.Add(1, Action.RENAME.Ordinal(), 0, GetString(Resource.String.Rename))
                    .SetIcon(Android.Resource.Drawable.IcMenuEdit);
            menu.Add(1, Action.EXPORT.Ordinal(), 0, GetString(Resource.String.Export))
                    .SetIcon(Android.Resource.Drawable.ButtonMinus);

            menu.Add(2, Action.PROPERTIES.Ordinal(), 0, GetString(Resource.String.Properties))
                    .SetIcon(Android.Resource.Drawable.IcDialogInfo);
        }

        public override bool OnContextItemSelected(IMenuItem item)
        {
            int position = adapter.GetPosition();
            SalmonFile ifile = fileItemList[position];
            switch ((Action)item.ItemId)
            {
                case Action.VIEW:
                    OpenFile(position);
                    break;
                case Action.VIEW_AS_TEXT:
                    StartTextViewer(position);
                    break;
                case Action.VIEW_EXTERNAL:
                    OpenWith(ifile, Action.VIEW_EXTERNAL.Ordinal());
                    break;
                case Action.EDIT:
                    OpenWith(ifile, Action.EDIT.Ordinal());
                    break;
                case Action.SHARE:
                    OpenWith(ifile, Action.SHARE.Ordinal());
                    break;
                case Action.EXPORT:
                    ExportFile(ifile, position);
                    break;
                case Action.COPY:
                    mode = Mode.Copy;
                    copyFiles = new SalmonFile[] { ifile };
                    ShowTaskRunning(true, false);
                    ShowTaskMessage(copyFiles.Length + " " + Resources.GetString(Resource.String.ItemsSelectedForCopy));
                    break;
                case Action.CUT:
                    mode = Mode.Move;
                    copyFiles = new SalmonFile[] { ifile };
                    ShowTaskRunning(true, false);
                    ShowTaskMessage(copyFiles.Length + " " + Resources.GetString(Resource.String.ItemsSelectedForMove));
                    break;
                case Action.DELETE:
                    DeleteFile(ifile, position);
                    break;
                case Action.RENAME:
                    RenameFile(ifile, position);
                    break;
                case Action.PROPERTIES:
                    ShowProperties(ifile);
                    break;
            }
            return true;
        }

        private void PasteSelected()
        {
            CopySelectedFiles(mode == Mode.Move);
        }

        private void SelectAll(bool value)
        {
            adapter.SelectAll(value);
        }

        private void PromptSearch()
        {
            ActivityCommon.PromptEdit(this, GetString(Resource.String.Search), "Keywords", "", GetString(Resource.String.MatchAnyTerm),
                (value, any) => Search(value, any));
        }

        public void ShowTaskRunning(bool value)
        {
            ShowTaskRunning(value, true);
        }

        public void ShowTaskRunning(bool value, bool progress)
        {
            RunOnUiThread(() =>
            {
                fileProgress.SetProgress(0);
                filesProgress.SetProgress(0);
                statusControlLayout.Visibility = value ? ViewStates.Visible : ViewStates.Gone;
                if (progress)
                {
                    fileProgress.Visibility = value ? ViewStates.Visible : ViewStates.Gone;
                    filesProgress.Visibility = value ? ViewStates.Visible : ViewStates.Gone;
                }
                else
                {
                    fileProgress.Visibility = ViewStates.Gone;
                    filesProgress.Visibility = ViewStates.Gone;
                }
                if (!value)
                    statusText.Text = "";
            });
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
                    fileItemList.Sort(Comparators.defaultComparison);
                    break;
                case SortType.Name:
                    fileItemList.Sort(Comparators.filenameComparison);
                    break;
                case SortType.Size:
                    fileItemList.Sort(Comparators.sizeComparison);
                    break;
                case SortType.Type:
                    fileItemList.Sort(Comparators.typeComparison);
                    break;
                case SortType.Date:
                    fileItemList.Sort(Comparators.dateComparison);
                    break;
            }
        }

        private void About()
        {
            ActivityCommon.PromptDialog(this, GetString(Resource.String.About),
                    GetString(Resource.String.app_name) + " v" + SalmonApplication.GetVersion() + "\n"
                            + GetString(Resource.String.AboutText),
                    GetString(Resource.String.GetSourceCode), (sender, e) =>
                        {
                            Intent intent = new Intent(Intent.ActionView, Android.Net.Uri.Parse(GetString(Resource.String.SourceCodeURL)));
                            StartActivity(intent);
                        },
                        GetString(Android.Resource.String.Ok), null);
        }

        private void PromptImportFiles()
        {
            ActivityCommon.OpenFilesystem(this, false, true, null, REQUEST_IMPORT_FILES);
        }

        private void PromptNewFolder()
        {
            ActivityCommon.PromptEdit(this, GetString(Resource.String.NewFolder), GetString(Resource.String.FolderName),
                    "New Folder", null, (string folderName, bool ischecked) =>
                    {
                        try
                        {
                            currDir.CreateDirectory(folderName);
                            Refresh();
                        }
                        catch (Exception exception)
                        {
                            exception.PrintStackTrace();
                            Toast.MakeText(this,
                                    GetString(Resource.String.CouldNotCreateFolder) + " "
                                            + exception.Message, ToastLength.Long).Show();
                        }
                    }
            );
        }

        private void PromptSortFiles()
        {
            List<string> sortTypes = new List<string>();
            foreach (SortType type in System.Enum.GetValues(typeof(SortType)))
            {
                sortTypes.Add(type.ToString());
            }
            ActivityCommon.PromptSingleValue(this, GetString(Resource.String.Sort),
                    sortTypes, sortTypes.IndexOf(sortType.ToString()), (which) =>
                    {
                        SortType[] values = (SortType[])System.Enum.GetValues(typeof(SortType));
                        SortFiles(values[which]);
                        adapter.NotifyDataSetChanged();
                    }
                    );
        }

        private void Exit()
        {
            Finish();
        }

        private void StartSettings()
        {
            Intent intent = new Intent(this, typeof(SettingsActivity));
            StartActivity(intent);
        }

        private void DeleteSelectedFiles()
        {
            DeleteFiles(adapter.GetSelectedFiles().ToArray());
        }

        private void CopySelectedFiles(bool move)
        {
            CopyFiles(copyFiles, currDir, move);
        }

        private void DeleteFiles(SalmonFile[] files)
        {
            executor.Submit(new Runnable(() =>
                {
                    ShowTaskRunning(true);
                    try
                    {
                        fileCommander.DeleteFiles(files, (file) =>
                        {
                            RunOnUiThread(() =>
                            {
                                fileItemList.Remove(file);
                                SortFiles(sortType);
                                adapter.NotifyDataSetChanged();
                            });
                        });
                    }
                    catch (Exception e)
                    {
                        e.PrintStackTrace();
                    }
                    RunOnUiThread(() =>
                    {
                        fileProgress.SetProgress(100);
                        filesProgress.SetProgress(100);
                    });
                    new Handler(Looper.MainLooper).PostDelayed(() =>
                            ShowTaskRunning(false), 1000);
                }));
        }

        private void CopyFiles(SalmonFile[] files, SalmonFile dir, bool move)
        {
            executor.Submit(new Runnable(() =>
                {
                    ShowTaskRunning(true);
                    try
                    {
                        fileCommander.CopyFiles(files, dir, move, (fileInfo) =>
                        {
                            RunOnUiThread(() =>
                            {
                                fileProgress.SetProgress((int) fileInfo.fileProgress);
                                filesProgress.SetProgress((int)(fileInfo.processedFiles * 100F / fileInfo.totalFiles));
                                string action = move ? " Moving: " : " Copying: ";
                                ShowTaskMessage((fileInfo.processedFiles + 1) + "/" + fileInfo.totalFiles + action + fileInfo.filename);
                            });
                        });
                    }
                    catch (Exception e)
                    {
                        e.PrintStackTrace();
                    }
                    RunOnUiThread(() =>
                    {
                        fileProgress.SetProgress(100);
                        filesProgress.SetProgress(100);
                        Refresh();
                    });
                    new Handler(Looper.MainLooper).PostDelayed(() =>
                            ShowTaskRunning(false), 1000);
                    copyFiles = null;
                    mode = Mode.Browse;
                }));
        }

        private void ExportSelectedFiles()
        {
            if (rootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return;
            ExportFiles(adapter.GetSelectedFiles().ToArray(), (files) =>
                {
                    Refresh();
                });
        }

        private void ShowProperties(SalmonFile ifile)
        {
            try
            {
                ActivityCommon.PromptDialog(this, GetString(Resource.String.Properties),
                        GetString(Resource.String.Name) + ": " + ifile.GetBaseName() + "\n" +
                                GetString(Resource.String.Path) + ": " + ifile.GetPath() + "\n" +
                                GetString(Resource.String.Size) + ": " + WindowUtils.GetBytes(ifile.GetSize(), 2) + " (" + ifile.GetSize() + " bytes)" + "\n" +
                                "\n" +
                                GetString(Resource.String.EncryptedName) + ": " + ifile.GetRealFile().GetBaseName() + "\n" +
                                GetString(Resource.String.EncryptedPath) + ": " + ifile.GetRealFile().GetAbsolutePath() + "\n" +
                                GetString(Resource.String.EncryptedSize) + ": " + WindowUtils.GetBytes(ifile.GetRealFile().Length(), 2) + " (" + ifile.GetRealFile().Length() + " bytes)" + "\n"
                        , GetString(Android.Resource.String.Ok), null,
                        null, null
                );
            }
            catch (Exception exception)
            {
                Toast.MakeText(this, GetString(Resource.String.CouldNotGetFileProperties), ToastLength.Long).Show();
                exception.PrintStackTrace();
            }
        }

        private void DeleteFile(SalmonFile ifile, int position)
        {
            DeleteFiles(new SalmonFile[] { ifile });
            RunOnUiThread(() =>
            {
                fileItemList.Remove(ifile);
                adapter.NotifyItemRemoved(position);
            });
        }

        private void RenameFile(SalmonFile ifile, int position)
        {
            RunOnUiThread(() =>
            {
                try
                {
                    ActivityCommon.PromptEdit(this,
                        GetString(Resource.String.Rename), GetString(Resource.String.NewFilename),
                        ifile.GetBaseName(), null, (string newFilename, bool isChecked) =>
                        {
                            try
                            {
                                ifile.Rename(newFilename);
                            }
                            catch (Exception exception)
                            {
                                exception.PrintStackTrace();
                                RunOnUiThread(() =>
                                {
                                    Toast toast = Toast.MakeText(this, "Could not rename file: " + exception.Message, ToastLength.Long);
                                });
                            }
                            adapter.NotifyItemChanged(position);
                        });
                }
                catch (Exception exception)
                {
                    exception.PrintStackTrace();
                }
            });
        }

        private void ExportFile(SalmonFile ifile, int position)
        {
            if (ifile == null)
                return;

            if (rootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return;

            ExportFiles(new SalmonFile[] { ifile }, (IRealFile[] realFiles) =>
                {
                    RunOnUiThread(() =>
                    {
                        fileItemList.Remove(ifile);
                        adapter.NotifyItemRemoved(position);
                    });
                });
        }

        private void OpenWith(SalmonFile salmonFile, int action)
        {
            try
            {
                if (salmonFile.GetSize() > MAX_FILE_SIZE_TO_SHARE)
                {
                    Toast toast = Toast.MakeText(this, GetString(Resource.String.FileSizeTooLarge), ToastLength.Long);
                    toast.Show();
                    return;
                }
                if (salmonFile.GetSize() > MEDIUM_FILE_SIZE_TO_SHARE)
                {
                    Toast toast = Toast.MakeText(this, GetString(Resource.String.PleaseWaitWhileDecrypting), ToastLength.Long);
                    toast.SetGravity(GravityFlags.Center, 0, 0);
                    toast.Show();
                }
                new Thread(() =>
                    {
                        try
                        {
                            ChooseApp(salmonFile, action);
                        }
                        catch (Exception exception)
                        {
                            exception.PrintStackTrace();
                        }
                    }).Start();
            }
            catch (Exception exception)
            {
                exception.PrintStackTrace();
            }
        }

        private void ChooseApp(SalmonFile salmonFile, int action)
        {

            File sharedFile = AndroidDrive.CopyToSharedFolder(salmonFile);
            sharedFile.DeleteOnExit();
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            string mimeType = MimeTypeMap.Singleton.GetMimeTypeFromExtension(ext);
            Android.Net.Uri uri = FileProvider.GetUriForFile(this, GetString(Resource.String.FileProvider), sharedFile);
            ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.From(this).SetType(mimeType);

            Intent intent;
            // if we just share (final) we can show the android chooser activity
            // since we don't have to grant the app write permissions
            if (action == Action.VIEW_EXTERNAL.Ordinal())
            {
                intent = builder.CreateChooserIntent();
                intent.SetAction(Intent.ActionView);
                intent.SetData(uri);

                intent.AddFlags(ActivityFlags.GrantReadUriPermission);
                Intent finalIntent1 = intent;
                RunOnUiThread(() =>
                {
                    try
                    {
                        StartActivity(finalIntent1);
                    }
                    catch (Exception ex)
                    {
                        ex.PrintStackTrace();
                        Toast.MakeText(this, GetString(Resource.String.NoApplicationsFound), ToastLength.Long).Show();
                    }
                });
            }
            else
            {

                // we show only apps that explicitly have intent filters for action edit
                if (action == Action.SHARE.Ordinal())
                {
                    builder.SetStream(uri);
                    intent = builder.Intent;
                    intent.SetAction(Intent.ActionSend);
                }
                else
                {
                    intent = builder.Intent;
                    intent.SetAction(Intent.ActionEdit);
                    intent.SetData(uri);
                }

                // we offer the user a list so they can grant write permissions only to that app
                SortedDictionary<string, string> apps = GetAppsForIntent(intent);
                Intent finalIntent = intent;
                RunOnUiThread(() =>
                {
                    ActivityCommon.PromptOpenWith(this, finalIntent, apps, uri, sharedFile, salmonFile, action == Action.EDIT.Ordinal(),
                            (AndroidSharedFileObserver fileObserver) =>
                                {
                                    ReimportSharedFile(uri, fileObserver);
                                });
                });
            }
        }

        private SortedDictionary<string, string> GetAppsForIntent(Intent intent)
        {
            IList<ResolveInfo> appInfoList = PackageManager.QueryIntentActivities(intent, 0);
            SortedDictionary<string, string> apps = new SortedDictionary<string, string>();
            foreach (ResolveInfo resolveInfo in appInfoList)
            {
                //FIXME: the key should be the package name
                string name = PackageManager.GetApplicationLabel(resolveInfo.ActivityInfo.ApplicationInfo).ToString();
                string packageName = resolveInfo.ActivityInfo.ApplicationInfo.PackageName;
                apps[name] = packageName;
            }
            return apps;
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
            if (rootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return;
            DocumentFile docFile = DocumentFile.FromSingleUri(SalmonApplication.getInstance().ApplicationContext, uri);
            IRealFile realFile = AndroidDrive.GetFile(docFile);
            if (realFile == null)
                return;
            SalmonFile oldSalmonFile = fileObserver.GetSalmonFile();
            SalmonFile parentDir = oldSalmonFile.GetParent();

            ShowTaskRunning(true);
            ImportFiles(new IRealFile[] { realFile }, parentDir, false, (SalmonFile[] importedSalmonFiles) =>
                {
                    fileObserver.SetSalmonFile(importedSalmonFiles[0]);
                    RunOnUiThread(() =>
                    {
                        if (importedSalmonFiles[0] != null)
                        {
                            fileItemList.Add(importedSalmonFiles[0]);
                            fileItemList.Remove(oldSalmonFile);
                            if (oldSalmonFile.Exists())
                                oldSalmonFile.Delete();
                            SortFiles(sortType);
                            adapter.NotifyDataSetChanged();
                        }
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

            if (requestCode == REQUEST_IMPORT_FILES)
            {
                IRealFile[] filesToImport;
                try
                {
                    filesToImport = ActivityCommon.GetFilesFromIntent(this, data);
                    ImportFiles(filesToImport, currDir, false, (SalmonFile[] importedFiles) =>
                        {
                            RunOnUiThread(() => Refresh());
                        });
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                    Toast.MakeText(this, Resources.GetString(Resource.String.CouldNotImportFiles), ToastLength.Long).Show();
                }
            }
            else if (requestCode == REQUEST_OPEN_VAULT_DIR)
            {
                try
                {
                    ActivityCommon.SetUriPermissions(data, uri);
                    SettingsActivity.SetVaultLocation(this, uri.ToString());
                    ActivityCommon.OpenVault(this, uri.ToString());
                    Clear();
                    SetupRootDir();
                }
                catch (Exception e)
                {
                    Toast.MakeText(this, "Could not open vault: " + e.Message, ToastLength.Long).Show();
                }

            }
            else if (requestCode == REQUEST_CREATE_VAULT_DIR)
            {
                ActivityCommon.PromptSetPassword(this, (string pass) =>
                        {
                            try
                            {
                                ActivityCommon.CreateVault(this, uri.ToString(), pass);
                                Toast.MakeText(this, "Vault created", ToastLength.Long).Show();
                                rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                                currDir = rootDir;
                                Refresh();
                            }
                            catch (Exception e)
                            {
                                e.PrintStackTrace();
                                Toast.MakeText(this, "Could not create vault: " + e.Message, ToastLength.Long).Show();
                            }
                        });

            }
            else if (requestCode == REQUEST_IMPORT_AUTH_FILE)
            {
                try
                {
                    SalmonDriveManager.ImportAuthFile(uri.ToString());
                    Toast.MakeText(this, "Device is now Authorized", ToastLength.Long).Show();
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                    Toast.MakeText(this, "Could Not Import Auth: " + ex.Message, ToastLength.Long).Show();
                }

            }
            else if (requestCode == REQUEST_EXPORT_AUTH_FILE)
            {
                try
                {
                    DocumentFile dir = DocumentFile.FromTreeUri(this, uri);
                    string filename = SalmonDriveManager.GetAppDriveConfigFilename();
                    SalmonDriveManager.ExportAuthFile(exportAuthID, dir.Uri.ToString(), filename);
                    Toast.MakeText(this, "Auth File Exported", ToastLength.Long).Show();
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                    Toast.MakeText(this, "Could Not Export Auth: " + ex.Message, ToastLength.Long).Show();
                }
            }
        }

        public void Clear()
        {
            Logout();
            rootDir = null;
            currDir = null;
            RunOnUiThread(() =>
            {
                fileItemList.Clear();
                adapter.ResetCache(listView);
                adapter.NotifyDataSetChanged();
            });
        }

        private void CheckCredentials()
        {
            if (SalmonDriveManager.GetDrive().HasConfig())
            {
                ActivityCommon.PromptPassword(this, (drive) =>
                    {
                        try
                        {
                            rootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                            currDir = rootDir;
                        }
                        catch (SalmonAuthException e)
                        {
                            e.PrintStackTrace();
                        }
                        Refresh();
                    });
            }
        }

        protected bool OpenFile(int position)
        {
            SalmonFile selectedFile = fileItemList[position];
            if (selectedFile.IsDirectory())
            {
                executor.Submit(new Runnable(() =>
                {
                    if (CheckFileSearcher())
                        return;
                    currDir = selectedFile;
                    salmonFiles = currDir.ListFiles();
                    DisplayFiles(true);
                }));
                return true;
            }
            try
            {
                string filename = selectedFile.GetBaseName();
                if (FileUtils.IsVideo(filename))
                {
                    StartMediaPlayer(position, MediaType.VIDEO);
                    return true;
                }
                else if (FileUtils.IsAudio(filename))
                {
                    StartMediaPlayer(position, MediaType.AUDIO);
                    return true;
                }
                else if (FileUtils.IsImage(filename))
                {
                    StartWebViewer(position);
                    return true;
                }
                else if (FileUtils.IsText(filename))
                {
                    StartTextViewer(position);
                    return true;
                }
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                Toast.MakeText(this, "Could not open: " + e.Message, ToastLength.Long).Show();
            }

            return false;
        }

        private void Logout()
        {
            try
            {
                SalmonDriveManager.GetDrive().Authenticate(null);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
        }

        public void StartMediaPlayer(int position, MediaType type)
        {
            List<SalmonFile> salmonFiles = new List<SalmonFile>();
            int pos = 0;
            for (int i = 0; i < fileItemList.Count; i++)
            {
                SalmonFile selectedFile = fileItemList[i];
                string filename;
                try
                {
                    filename = selectedFile.GetBaseName();
                    if ((type == MediaType.VIDEO && FileUtils.IsVideo(filename))
                            || (type == MediaType.AUDIO && FileUtils.IsAudio(filename))
                    )
                    {
                        salmonFiles.Add(selectedFile);
                    }
                    if (i == position)
                        pos = salmonFiles.Count - 1;
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
            }

            Intent intent = new Intent(this, typeof(MediaPlayerActivity));
            SalmonFile file = fileItemList[position];
            MediaPlayerActivity.SetMediaFile(file);
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
            StartActivity(intent);
        }

        private void StartTextViewer(int position)
        {
            try
            {
                if (fileItemList[position].GetSize() > 1 * 1024 * 1024)
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
                string filename = file.GetBaseName();

                int pos = 0;
                for (int i = 0; i < fileItemList.Count; i++)
                {
                    try
                    {
                        SalmonFile listFile = fileItemList[i];
                        string listFilename = listFile.GetBaseName();
                        if (i != position &&
                                (FileUtils.IsImage(filename) && FileUtils.IsImage(listFilename))
                                || (FileUtils.IsText(filename) && FileUtils.IsText(listFilename)))
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
                }
                Intent intent = new Intent(this, typeof(WebViewerActivity));
                SalmonFile selectedFile = fileItemList[position];
                WebViewerActivity.SetContentFile(selectedFile);
                intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
                StartActivity(intent);
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                Toast.MakeText(this, "Could not open viewer: " + e.Message, ToastLength.Long).Show();
            }
        }

        protected override void OnDestroy()
        {
            Logout();
            WindowUtils.RemoveFromRecents(this, true);
            adapter.Stop();
            base.OnDestroy();
        }

        [Obsolete]
        public override void OnBackPressed()
        {
            SalmonFile parent = currDir.GetParent();
            if (adapter.GetMode() == FileAdapter.Mode.MULTI_SELECT)
            {
                adapter.SetMultiSelect(false);
            }
            else if (mode == Mode.Search && fileCommander.IsFileSearcherRunning())
            {
                fileCommander.StopFileSearch();
            }
            else if (mode == Mode.Search)
            {
                executor.Submit(new Runnable(() =>
                {
                    mode = Mode.Browse;
                    salmonFiles = currDir.ListFiles();
                    DisplayFiles(true);
                }));
            }
            else if (parent != null)
            {
                executor.Submit(new Runnable(() =>
                {
                    if (CheckFileSearcher())
                        return;
                    currDir = parent;
                    salmonFiles = currDir.ListFiles();
                    DisplayFiles(true);
                }));
            }
            else
            {
                ActivityCommon.PromptDialog(this, GetString(Resource.String.Exit), GetString(Resource.String.ExitApp),
                        GetString(Android.Resource.String.Ok), (sender, e) =>
                        {
                            Finish();
                        }, GetString(Android.Resource.String.Cancel), (dialog, e) =>
                        {

                        }
                        );
            }
        }

        public void ExportFiles(SalmonFile[] items, Action<IRealFile[]> OnFinished)
        {

            executor.Submit(new Runnable(() =>
                {
                    foreach (SalmonFile file in items)
                    {
                        if (file.IsDirectory())
                        {
                            RunOnUiThread(() =>
                    {
                        Toast.MakeText(this, "Cannot Export Directories select files only", ToastLength.Long).Show();
                    });
                            return;
                        }
                    }
                    ShowTaskRunning(true);
                    bool success = false;
                    try
                    {
                        success = fileCommander.ExportFiles(items,
                        (progress) =>
                        {
                            RunOnUiThread(() => filesProgress.SetProgress(progress));
                        }, OnFinished);
                    }
                    catch (Exception e)
                    {
                        e.PrintStackTrace();
                        RunOnUiThread(() =>
                {
                    Toast.MakeText(this, "Could not export files: " + e.Message, ToastLength.Long).Show();
                });
                    }
                    if (fileCommander.IsStopped())
                        ShowTaskMessage(GetString(Resource.String.ExportStopped));
                    else if (!success)
                        ShowTaskMessage(GetString(Resource.String.ExportFailed));
                    else ShowTaskMessage(GetString(Resource.String.ExportComplete));
                    RunOnUiThread(() =>
            {
                fileProgress.SetProgress(100);
                filesProgress.SetProgress(100);
                ActivityCommon.PromptDialog(this, GetString(Resource.String.Export), GetString(Resource.String.FilesExportedTo)
                                + ": " + SalmonDriveManager.GetDrive().GetExportDir().GetAbsolutePath(),
                        GetString(Android.Resource.String.Ok), null, null, null);
            });
                    new Handler(Looper.MainLooper).PostDelayed(() =>
                    ShowTaskRunning(false), 1000);

                }));
        }

        public void ImportFiles(IRealFile[] fileNames, SalmonFile importDir, bool deleteSource,
                                Action<SalmonFile[]> OnFinished)
        {

            executor.Submit(new Runnable(() =>
                {
                    ShowTaskRunning(true);
                    bool success = false;
                    try
                    {
                        success = fileCommander.ImportFiles(fileNames, importDir, deleteSource,
                                (progress) =>
                                {
                                    RunOnUiThread(() => filesProgress.SetProgress(progress));
                                }, OnFinished);
                    }
                    catch (Exception e)
                    {
                        e.PrintStackTrace();
                        RunOnUiThread(() =>
                        {
                            Toast.MakeText(this, "Could not import files: " + e.Message, ToastLength.Long).Show();
                        });
                    }
                    if (fileCommander.IsStopped())
                        ShowTaskMessage(GetString(Resource.String.ImportStopped));
                    else if (!success)
                        ShowTaskMessage(GetString(Resource.String.ImportFailed));
                    else ShowTaskMessage(GetString(Resource.String.ImportComplete));
                    RunOnUiThread(() =>
                    {
                        fileProgress.SetProgress(100);
                        filesProgress.SetProgress(100);
                    });
                    new Handler(Looper.MainLooper).PostDelayed(() =>
                            ShowTaskRunning(false), 2000);
                }));
        }

        //TODO: refactor to a class and update ui frequently with progress
        private void Search(string value, bool any)
        {
            if (CheckFileSearcher())
                return;
            executor.Submit(new Runnable(() =>
            {
                mode = Mode.Search;
                RunOnUiThread(new Runnable(() =>
                {
                    try
                    {
                        pathText.Text = GetString(Resource.String.Searching) + ": " + value;
                    }
                    catch (Exception exception)
                    {
                        exception.PrintStackTrace();
                    }
                    salmonFiles = new SalmonFile[] { };
                    DisplayFiles(true);
                }));

                salmonFiles = fileCommander.Search(currDir, value, any, (SalmonFile salmonFile) =>
                {
                    RunOnUiThread(new Runnable(() =>
                    {
                        int position = 0;
                        foreach (SalmonFile file in fileItemList)
                        {
                            if ((int)salmonFile.GetTag() > (int)file.GetTag())
                            {
                                break;
                            }
                            else
                                position++;
                        }
                        fileItemList.Insert(position, salmonFile);
                        adapter.NotifyItemInserted(position);
                    }));
                });
                RunOnUiThread(new Runnable(() =>
                {
                    if (!fileCommander.IsFileSearcherStopped())
                        pathText.Text = GetString(Resource.String.Search) + ": " + value;
                    else
                        SetPath(GetString(Resource.String.Search) + " " + GetString(Resource.String.Stopped) + ": " + value);
                }));
            }));
        }


        public void OnImportAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                Toast.MakeText(this, "No Drive Loaded", ToastLength.Long).Show();
                return;
            }
            // TODO: filter by extension
            string filename = SalmonDriveManager.GetAppDriveConfigFilename();
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename);
            ActivityCommon.OpenFilesystem(this, false, false, null, REQUEST_IMPORT_AUTH_FILE);
        }

        public void OnExportAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                Toast.MakeText(this, "No Drive Loaded", ToastLength.Long).Show();
                return;
            }

            ActivityCommon.PromptEdit(this, "Export Auth File",
                    "Enter the Auth ID for the device you want to authorize", "", null,
                    (targetAuthID, option) =>
                        {
                            exportAuthID = targetAuthID;
                            string filename = SalmonDriveManager.GetAppDriveConfigFilename();
                            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename);
                            ActivityCommon.OpenFilesystem(this, true, false, null, REQUEST_EXPORT_AUTH_FILE);
                        });
        }

        public void OnRevokeAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                Toast.MakeText(this, "No Drive Loaded", ToastLength.Long).Show();
                return;
            }
            ActivityCommon.PromptDialog(this, "Revoke Auth", "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
                    "Ok", (d, e) =>
                        {
                            try
                            {
                                SalmonDriveManager.RevokeSequences();
                                Toast.MakeText(this, "Revoke Auth Successful", ToastLength.Long).Show();
                            }
                            catch (Exception ex)
                            {
                                ex.PrintStackTrace();
                                Toast.MakeText(this, "Could Not Revoke Auth: " + ex.Message, ToastLength.Long).Show();
                            }
                        }, "Cancel", null);
        }


        public void OnDisplayAuthID()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                Toast.MakeText(this, "No Drive Loaded", ToastLength.Long).Show();
                return;
            }
            string driveID;
            try
            {
                driveID = SalmonDriveManager.GetAuthID();
                ActivityCommon.PromptEdit(this, "Salmon Auth ID", "", driveID, null, null);
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                Toast.MakeText(this, GetString(Resource.String.Error) + ": "
                        + GetString(Resource.String.CouldNotGetAuthID) + ": "
                        + e.Message, ToastLength.Long).Show();
            }
        }


        public void SetPath(string value)
        {
            if (value.StartsWith("/"))
                value = value.Substring(1);
            pathText.Text = "salmonfs://" + value;
        }

        public SalmonFile[] GetSalmonFiles()
        {
            return salmonFiles;
        }

        private void OnOpenVault()
        {
            ActivityCommon.OpenFilesystem(this, true, false, null, REQUEST_OPEN_VAULT_DIR);
        }

        public void OnCreateVault()
        {
            ActivityCommon.OpenFilesystem(this, true, false, null, REQUEST_CREATE_VAULT_DIR);
        }

        public void OnCloseVault()
        {
            Logout();
            rootDir = null;
            currDir = null;
            RunOnUiThread(new Runnable(() =>
            {
                pathText.Text = "";
                fileItemList.Clear();
                adapter.NotifyDataSetChanged();
            }));
        }

    }


    public enum MediaType
    {
        AUDIO, VIDEO
    }

    public enum Action
    {
        BACK, REFRESH, IMPORT, VIEW, VIEW_AS_TEXT, VIEW_EXTERNAL, EDIT, SHARE, SAVE,
        EXPORT, DELETE, RENAME, UP, DOWN,
        MULTI_SELECT, SINGLE_SELECT, SELECT_ALL, UNSELECT_ALL,
        COPY, CUT, PASTE,
        NEW_FOLDER, SEARCH, STOP, PLAY, SORT,
        OPEN_VAULT, CREATE_VAULT, CLOSE_VAULT, CHANGE_PASSWORD,
        IMPORT_AUTH, EXPORT_AUTH, REVOKE_AUTH, DISPLAY_AUTH_ID,
        PROPERTIES, SETTINGS, ABOUT, EXIT
    }

    public enum Mode
    {
        Browse, Search, Copy, Move
    }

    public enum SortType
    {
        Default, Name, Size, Type, Date
    }
}