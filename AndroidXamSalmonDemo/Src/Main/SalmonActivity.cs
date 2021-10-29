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
using Android.Util;
using Android.Views;
using Android.Webkit;
using Android.Widget;
using AndroidX.AppCompat.App;
using AndroidX.Core.App;
using AndroidX.Core.Content;
using AndroidX.DocumentFile.Provider;
using AndroidX.RecyclerView.Widget;
using Salmon.Droid.Utils;
using Salmon.Droid.FS;
using Salmon.FS;
using Java.IO;
using Java.Lang;

using System;
using System.Collections.Generic;
using Exception = System.Exception;
using System.Linq;
using Java.Util.Concurrent;

namespace Salmon.Droid.Main
{

    [Activity(Label = "@string/app_name", Icon = "@drawable/logo",
        MainLauncher = true,
        Theme = "@style/Theme.MaterialComponents",
        ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    public class SalmonActivity : AppCompatActivity
    {
        private static readonly string TAG = typeof(SalmonActivity).Name;
        private static readonly long MAX_FILE_SIZE_TO_SHARE = 50 * 1024 * 1024;
        private static readonly long MEDIUM_FILE_SIZE_TO_SHARE = 10 * 1024 * 1024;
        private static readonly int ENC_IMPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        private static readonly int ENC_IMPORT_THREADS = 4;
        private static readonly int ENC_EXPORT_BUFFER_SIZE = 4 * 1024 * 1024;
        private static readonly int ENC_EXPORT_THREADS = 4;

        public static SalmonFile RootDir;

        private const int REFRESH = 1;
        private const int IMPORT = 2;
        private const int VIEW = 3;
        private const int EDIT = 4;
        private const int SHARE = 5;
        private const int EXPORT = 6;
        private const int DELETE = 7;
        private const int EXPORT_ALL = 8;
        private const int DELETE_ALL = 9;
        private const int SORT = 10;
        private const int SETTINGS = 11;
        private const int ABOUT = 12;
        private const int EXIT = 13;

        private View StatusControlLayout;
        private TextView StatusText;
        private ProgressBar FileProgress;
        private ProgressBar FilesProgress;
        private ImageButton CancelButton;
        private RecyclerView gridList;
        private FileGridAdapter adapter;

        private bool stopJobs;
        private List<SalmonFile> fileItemList = new List<SalmonFile>();
        SalmonFileImporter fileImporter;
        SalmonFileExporter fileExporter;

        // we queue all import export jobs with an executor
        private IExecutorService executor = Executors.NewFixedThreadPool(1);

        protected override void OnCreate(Bundle bundle)
        {
            base.OnCreate(bundle);
            SetContentView(Resource.Layout.main);
            SetupControls();
            SetupFileTools();
            SetupListeners();
            SetupVirtualDrive();
            SetupRootDir();
        }

        private void SetupFileTools()
        {
            fileImporter = new SalmonFileImporter(ENC_IMPORT_BUFFER_SIZE, ENC_IMPORT_THREADS);
            fileExporter = new SalmonFileExporter(ENC_EXPORT_BUFFER_SIZE, ENC_EXPORT_THREADS);
        }

        private void SetupListeners()
        {
            fileImporter.OnTaskProgressChanged += (object sender, long bytesRead, long totalBytesRead, string message) =>
            {
                RunOnUiThread(() =>
                {
                    StatusText.Text = message;
                    FileProgress.Progress = (int)(bytesRead * 100.0F / totalBytesRead);
                });
            };
            fileExporter.progressListener += (object sender, long bytesWritten, long totalBytesWritten, string message) =>
            {
                RunOnUiThread(() =>
                {
                    StatusText.Text = message;
                    FileProgress.Progress = (int)(bytesWritten * 100.0F / totalBytesWritten);
                });
            };
        }

        private void SetupControls()
        {
            FileProgress = (ProgressBar)FindViewById(Resource.Id.fileProgress);
            FilesProgress = (ProgressBar)FindViewById(Resource.Id.filesProgress);
            StatusText = (TextView)FindViewById(Resource.Id.status);
            CancelButton = (ImageButton)FindViewById(Resource.Id.cancelButton);
            CancelButton.Click += CancelJobs;
            StatusControlLayout = (View)FindViewById(Resource.Id.statusControlLayout);
            gridList = (RecyclerView)FindViewById(Resource.Id.gridList);
            adapter = new FileGridAdapter(this, fileItemList, (int pos) =>
            {
                return Selected(pos);
            });
            GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 4, GridLayoutManager.Vertical, false);
            gridList.SetLayoutManager(gridLayoutManager);
            gridList.SetAdapter(adapter);
            RegisterForContextMenu(gridList);
        }

        private void CancelJobs(object sender, EventArgs e)
        {
            stopJobs = true;
            fileImporter.Stop();
            fileExporter.Stop();
        }

        protected void SetupRootDir()
        {
            string vaultLocation = SettingsActivity.GetVaultLocation(this);
            try
            {
                SalmonDriveManager.SetDriveLocation(vaultLocation);
                SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                if (RootDir == null)
                {
                    PromptSelectRoot();
                    return;
                }
            }
            catch (SalmonAuthException e)
            {
                e.PrintStackTrace();
                CheckCredentials();
                return;
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                PromptSelectRoot();
                return;
            }
            Refresh();
        }

        public void Refresh()
        {
            if (SalmonDriveManager.GetDrive() == null)
                return;
            try
            {
                if (SalmonDriveManager.GetDrive().GetVirtualRoot() == null || !SalmonDriveManager.GetDrive().GetVirtualRoot().Exists())
                {
                    PromptSelectRoot();
                    return;
                }
                if (!SalmonDriveManager.GetDrive().IsAuthenticated())
                {
                    CheckCredentials();
                    return;
                }
                SalmonFile[] salmonFiles = RootDir.ListFiles();
                adapter.ResetCache();
                DisplayFiles(salmonFiles);
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

        private void DisplayFiles(SalmonFile[] salmonFiles)
        {
            RunOnUiThread(new Runnable(() =>
            {
                fileItemList.Clear();
                adapter.NotifyDataSetChanged();
                foreach (SalmonFile salmonFile in salmonFiles)
                {
                    if (!salmonFile.IsDirectory())
                    {
                        fileItemList.Add(salmonFile);
                    }
                }
                SortItemList();
                adapter.NotifyDataSetChanged();
            }));
        }

        private void SetupVirtualDrive()
        {
            SalmonDriveManager.SetVirtualDriveClass(typeof(AndroidDrive));
        }


        public override bool OnPrepareOptionsMenu(IMenu menu)
        {
            menu.Clear();
            menu.Add(0, REFRESH, 0, GetString(Resource.String.Refresh))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuRotate))
                .SetShowAsAction(ShowAsAction.Always);
            menu.Add(0, IMPORT, 0, GetString(Resource.String.Import))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuAdd))
                    .SetShowAsAction(ShowAsAction.Always);
            menu.Add(0, EXPORT_ALL, 0, GetString(Resource.String.ExportAll))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.ButtonMinus));
            menu.Add(0, DELETE_ALL, 0, GetString(Resource.String.DeleteAll))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuDelete));
            menu.Add(0, SORT, 0, GetString(Resource.String.Sort))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuSortAlphabetically));
            menu.Add(0, SETTINGS, 0, GetString(Resource.String.Settings))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuPreferences));
            menu.Add(0, ABOUT, 0, GetString(Resource.String.About))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuInfoDetails));
            menu.Add(0, EXIT, 0, GetString(Resource.String.Exit))
                    .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuCloseClearCancel));

            return base.OnPrepareOptionsMenu(menu);
        }

        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            switch (item.ItemId)
            {
                case REFRESH:
                    Refresh();
                    return true;
                case IMPORT:
                    PromptImportFiles();
                    return true;
                case EXPORT_ALL:
                    ExportAllFiles();
                    return true;
                case DELETE_ALL:
                    DeleteAllFiles();
                    return true;
                case SORT:
                    SortFiles();
                    break;
                case SETTINGS:
                    StartSettings();
                    return true;
                case ABOUT:
                    About();
                    return true;
                case EXIT:
                    Exit();
                    return true;
            }
            base.OnOptionsItemSelected(item);
            return false;
        }

        private void SortFiles()
        {
            SortItemList();
            adapter.NotifyDataSetChanged();
        }

        private void SortItemList()
        {
            fileItemList.Sort((SalmonFile c1, SalmonFile c2) =>
            {
                return TryGetBasename(c1).CompareTo(TryGetBasename(c2));
            });
        }

        private string TryGetBasename(SalmonFile salmonFile)
        {
            try
            {
                return salmonFile.GetBaseName();
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
            return "";
        }

        private void About()
        {
            ActivityCommon.PromptDialog(this, GetString(Resource.String.app_name), GetString(Resource.String.AboutText),
                GetString(Resource.String.GetSourceCode), (sender, e) =>
                {
                    Intent intent = new Intent(Intent.ActionView, Android.Net.Uri.Parse(GetString(Resource.String.SourceCodeURL)));
                    StartActivity(intent);
                },
               GetString(Android.Resource.String.Ok), null);
        }

        private void PromptImportFiles()
        {
            SalmonDriveManager.GetDrive().PickRealFolder(this, GetString(Resource.String.SelectFilesToImport), false,
                        SettingsActivity.GetVaultLocation(this));
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

        private void DeleteAllFiles()
        {
            DeleteFiles(fileItemList.ToArray());
        }

        private void DeleteFiles(SalmonFile[] files)
        {

            new Thread(new Runnable(() =>
            {
                try
                {
                    DoDeleteFiles(files);
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
            })).Start();

        }

        private void DoDeleteFiles(SalmonFile[] files)
        {
            foreach (SalmonFile ifile in files)
            {
                ifile.Delete();
                RunOnUiThread(new Runnable(() =>
                {
                    fileItemList.Remove(ifile);
                    SortItemList();
                    adapter.NotifyDataSetChanged();
                }));
            }
        }

        private void ExportAllFiles()
        {
            ExportFiles(fileItemList.ToArray(), (files) =>
            {
                Refresh();
            });
        }

        public override void OnCreateContextMenu(IContextMenu menu, View v, IContextMenuContextMenuInfo menuInfo)
        {
            menu.SetHeaderTitle(GetString(Resource.String.Action));
            menu.Add(0, VIEW, 0, GetString(Resource.String.ViewExternal))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuView));
            menu.Add(0, EDIT, 0, GetString(Resource.String.EditExternal))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuSend));
            menu.Add(0, SHARE, 0, GetString(Resource.String.ShareExternal))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuSend));
            menu.Add(0, EXPORT, 0, GetString(Resource.String.Export))
                .SetIcon(GetDrawable(Android.Resource.Drawable.ButtonMinus));
            menu.Add(0, DELETE, 0, GetString(Resource.String.Delete))
                .SetIcon(GetDrawable(Android.Resource.Drawable.IcMenuDelete));
        }

        public override bool OnContextItemSelected(IMenuItem item)
        {
            int position = adapter.GetPosition();
            SalmonFile ifile = fileItemList[position];
            switch (item.ItemId)
            {
                case VIEW:
                    OpenWith(ifile, VIEW);
                    break;
                case EDIT:
                    OpenWith(ifile, EDIT);
                    break;
                case SHARE:
                    OpenWith(ifile, SHARE);
                    break;
                case EXPORT:
                    ExportFile(ifile);
                    break;
                case DELETE:
                    DeleteFile(ifile);
                    break;
            }
            return true;
        }

        private void DeleteFile(SalmonFile ifile)
        {
            DeleteFiles(new SalmonFile[] { ifile });
            RunOnUiThread(() =>
            {
                fileItemList.Remove(ifile);
                adapter.NotifyDataSetChanged();
            });
        }

        private void ExportFile(SalmonFile ifile)
        {
            ExportFiles(new SalmonFile[] { ifile }, (IRealFile[] realFiles) =>
            {
                RunOnUiThread(() =>
                {
                    fileItemList.Remove(ifile);
                    adapter.NotifyDataSetChanged();
                });
            });
        }

        private void OpenWith(SalmonFile salmonFile, int action)
        {
            if (salmonFile.GetSize() > MAX_FILE_SIZE_TO_SHARE)
            {
                Toast toast = Toast.MakeText(this, GetString(Resource.String.FileSizeTooLarge), ToastLength.Long);
                toast.SetGravity(GravityFlags.Center, 0, 0);
                toast.Show();
                return;
            }
            if (salmonFile.GetSize() > MEDIUM_FILE_SIZE_TO_SHARE)
            {
                Toast toast = Toast.MakeText(this, GetString(Resource.String.PleaseWaitWhileDecrypting), ToastLength.Long);
                toast.SetGravity(GravityFlags.Center, 0, 0);
                toast.Show();
            }
            new Thread(new Runnable(() =>
            {
                ChooseApp(salmonFile, action);
            })).Start();
        }

        private void ChooseApp(SalmonFile salmonFile, int action)
        {

            Java.IO.File sharedFile = AndroidDrive.CopyToSharedFolder(salmonFile);
            sharedFile.DeleteOnExit();
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(salmonFile.GetBaseName()).ToLower();
            string mimeType = MimeTypeMap.Singleton.GetMimeTypeFromExtension(ext);
            Android.Net.Uri uri = FileProvider.GetUriForFile(this, GetString(Resource.String.FileProvider), sharedFile);
            ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.From(this)
                            .SetType(mimeType);

            Intent intent = null;
            // if we just share (readonly) we can show the android chooser activity
            // since we don't have to grant the app write permissions
            if (action == VIEW)
            {
                intent = builder.CreateChooserIntent();
                intent.SetAction(Intent.ActionView);
                intent.SetData(uri);

                intent.AddFlags(ActivityFlags.GrantReadUriPermission);
                RunOnUiThread(new Runnable(() =>
                {
                    try
                    {
                        StartActivity(intent);
                    }
                    catch (Exception ex)
                    {
                        ex.PrintStackTrace();
                        Toast.MakeText(this, GetString(Resource.String.NoApplicationsFound), ToastLength.Long).Show();
                    }
                }));
            }
            else
            {

                // we show only apps that explicitly have intent filters for action edit
                if (action == SHARE)
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
                RunOnUiThread(new Runnable(() =>
                {
                    ActivityCommon.PromptOpenWith(this, intent, apps, uri, sharedFile, salmonFile, action == EDIT,
                    (AndroidSharedFileObserver fileObserver) =>
                    {
                        ReimportSharedFile(uri, fileObserver);
                    });
                }));
            }
        }

        private SortedDictionary<string, string> GetAppsForIntent(Intent intent)
        {
            IList<ResolveInfo> appInfoList = PackageManager.QueryIntentActivities(intent, 0);
            SortedDictionary<string, string> apps = new SortedDictionary<string, string>();
            foreach (ResolveInfo resolveInfo in appInfoList)
            {
                //FIXME: the key should be the package name
                string name = PackageManager.GetApplicationLabel(resolveInfo.ActivityInfo.ApplicationInfo);
                string packageName = resolveInfo.ActivityInfo.ApplicationInfo.PackageName;
                apps[name] = packageName;
            }
            return apps;
        }


        System.Threading.ManualResetEvent done = new System.Threading.ManualResetEvent(true);
        private void ReimportSharedFile(Android.Net.Uri uri, AndroidSharedFileObserver fileObserver)
        {
            // TODO: we qeueue the jobs by using a semaphore
            done.WaitOne();
            done.Reset();
            DocumentFile docFile = DocumentFile.FromSingleUri(Application.Context, uri);
            IRealFile realFile = AndroidDrive.GetFile(docFile);
            SalmonFile oldSalmonFile = fileObserver.GetSalmonFile();
            SalmonFile parentDir = oldSalmonFile.GetParent();
            ImportFiles(new IRealFile[] { realFile }, parentDir, false, (SalmonFile[] importedSalmonFiles) =>
            {
                fileObserver.SetSalmonFile(importedSalmonFiles[0]);
                RunOnUiThread(new Runnable(() =>
                {
                    if (importedSalmonFiles[0] != null)
                    {
                        fileItemList.Add(importedSalmonFiles[0]);
                        fileItemList.Remove(oldSalmonFile);
                        if (oldSalmonFile.Exists())
                            oldSalmonFile.Delete();
                        SortItemList();
                        adapter.NotifyDataSetChanged();
                    }
                    Toast.MakeText(this, GetString(Resource.String.FileSavedInSalmonVault), ToastLength.Long).Show();
                }));
                done.Set();
            });
        }

        protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
        {
            if (requestCode == AndroidDrive.RequestSdcardCodeFile)
            {
                if (data == null)
                    return;

                IRealFile[] filesToImport = new IRealFile[0];
                try
                {
                    filesToImport = AndroidDrive.GetFilesFromIntent(this, data);
                    ImportFiles(filesToImport, RootDir, false, (SalmonFile[] importedFiles) =>
                    {
                        RunOnUiThread(() =>
                        {
                            Refresh();
                        });
                    });
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                    Toast.MakeText(this, GetString(Resource.String.CouldNotImportFiles), ToastLength.Long).Show();
                }
            }
            else if (requestCode == AndroidDrive.RequestSdcardCodeFolder)
            {
                if (data != null)
                {
                    bool res = ActivityCommon.SetVaultFolder(this, data);
                    if (!res)
                    {
                        PromptSelectRoot();
                        return;
                    }
                    Clear();
                    SetupRootDir();
                }
            }
        }

        public void Clear()
        {
            Logout();
            RootDir = null;
            RunOnUiThread(() =>
            {
                fileItemList.Clear();
                adapter.NotifyDataSetChanged();
            });
        }

        private void CheckCredentials()
        {
            if (SalmonDriveManager.GetDrive().HasConfig())
            {
                ActivityCommon.PromptPassword(this, () =>
                {
                    RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                    Refresh();
                });
            }
            else
            {
                ActivityCommon.PromptSetPassword(this, (string pass) =>
                {
                    RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                    Refresh();
                    if (fileItemList.Count == 0)
                        PromptImportFiles();
                });
            }
        }

        protected bool Selected(int position)
        {
            SalmonFile selectedFile = fileItemList[position];
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(selectedFile.GetBaseName()).ToLower();

            if (ext.Equals("mp4"))
            {
                StartMediaPlayer(selectedFile);
                return true;
            }
            else if (ext.Equals("wav") || ext.Equals("mp3"))
            {
                StartMediaPlayer(selectedFile);
                return true;
            }
            else if (ext.Equals("png") || ext.Equals("jpg") || ext.Equals("bmp") || ext.Equals("webp") || ext.Equals("gif"))
            {
                StartWebViewer(selectedFile);
                return true;
            }
            else if (ext.Equals("txt"))
            {
                StartWebViewer(selectedFile);
                return true;
            }
            return false;
        }

        public void ImportFiles(IRealFile[] fileNames, SalmonFile importDir, bool deleteSource,
            Action<SalmonFile[]> OnFinished = null)
        {
            executor.Submit(new Runnable(() =>
            {
                stopJobs = false;
                ShowTaskRunning(true);
                bool success = false;
                try
                {
                    success = DoImportFiles(fileNames, importDir, deleteSource, OnFinished);
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
                if (stopJobs)
                    ShowTaskMessage(GetString(Resource.String.ImportStopped));
                else if (!success)
                    ShowTaskMessage(GetString(Resource.String.ImportFailed));
                else if (success)
                    ShowTaskMessage(GetString(Resource.String.ImportComplete));
                RunOnUiThread(new Runnable(() =>
                {
                    FileProgress.Progress = 100;
                    FilesProgress.Progress = 100;
                }));
                new Handler(Looper.MainLooper).PostDelayed(() =>
                {
                    ShowTaskRunning(false);
                }, 1000);
            }));
        }



        private bool DoImportFiles(IRealFile[] filesToImport, SalmonFile importDir, bool deleteSource,
            Action<SalmonFile[]> OnFinished = null)
        {

            if (filesToImport == null)
                return false;

            if (RootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return false;

            IList<SalmonFile> importedFiles = new List<SalmonFile>();
            for (int i = 0; i < filesToImport.Length; i++)
            {
                if (stopJobs)
                    break;
                SalmonFile salmonFile = null;
                try
                {
                    salmonFile = fileImporter.ImportFile(filesToImport[i], importDir, deleteSource);
                    RunOnUiThread(() =>
                    {
                        FilesProgress.Progress = (int)(i * 100.0F / filesToImport.Length);
                    });
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
                importedFiles.Add(salmonFile);
            }
            if (OnFinished != null)
                OnFinished.Invoke(importedFiles.ToArray());
            return true;
        }

        

        private void ExportFiles(SalmonFile[] items, Action<IRealFile[]> OnFinished = null)
        {

            executor.Submit(new Runnable(() =>
            {
                stopJobs = false;
                ShowTaskRunning(true);
                bool success = false;
                try
                {
                    success = DoExportFiles(items, OnFinished);
                }
                catch (Exception e)
                {
                    e.PrintStackTrace();
                }
                if (stopJobs)
                    ShowTaskMessage(GetString(Resource.String.ExportStopped));
                else if (!success)
                    ShowTaskMessage(GetString(Resource.String.ExportFailed));
                else if (success)
                    ShowTaskMessage(GetString(Resource.String.ExportComplete));
                RunOnUiThread(new Runnable(() =>
                {
                    FileProgress.Progress = 100;
                    FilesProgress.Progress = 100;
                    ActivityCommon.PromptDialog(this, GetString(Resource.String.Export), GetString(Resource.String.FilesExportedTo) + SalmonDriveManager.GetDrive().GetExportDir().GetAbsolutePath(),
                        GetString(Android.Resource.String.Ok), null);
                }));
                new Handler(Looper.MainLooper).PostDelayed(() =>
                {
                    ShowTaskRunning(false);
                }, 1000);

            }));
        }

        private bool DoExportFiles(SalmonFile[] filesToExport, Action<IRealFile[]> OnFinished = null)
        {
            if (filesToExport == null)
                return false;

            if (RootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return false;
            IList<IRealFile> exportedFiles = new List<IRealFile>();
            IRealFile exportDir = SalmonDriveManager.GetDrive().GetExportDir();

            for (int i = 0; i < filesToExport.Length; i++)
            {
                if (stopJobs)
                    break;

                IRealFile realFile = null;
                try
                {
                    SalmonFile fileToExport = filesToExport[i];
                    realFile = fileExporter.ExportFile(fileToExport, exportDir, true);
                    exportedFiles.Add(realFile);
                    RunOnUiThread(() =>
                    {
                        FilesProgress.Progress = (int)(i * 100.0F / filesToExport.Length);
                    });
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                }
            }
            if (OnFinished != null)
                OnFinished.Invoke(exportedFiles.ToArray());
            return true;
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
        private void PromptSelectRoot()
        {
            SalmonDriveManager.GetDrive().PickRealFolder(this, GetString(Resource.String.SelectFolderForFiles), true,
                SettingsActivity.GetVaultLocation(this));
        }

        public void StartMediaPlayer(SalmonFile salmonFile)
        {
            Intent intent = new Intent(this, typeof(MediaPlayerActivity));
            MediaPlayerActivity.SetMediaFile(salmonFile);
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
            StartActivity(intent);
        }

        private void StartWebViewer(SalmonFile salmonFile)
        {
            Intent intent = new Intent(this, typeof(WebViewerActivity));
            WebViewerActivity.SetContentFile(salmonFile);
            intent.SetFlags(ActivityFlags.ClearTop | ActivityFlags.NewTask);
            StartActivity(intent);
        }

        public void ShowTaskRunning(bool value)
        {
            RunOnUiThread(new Runnable(() =>
            {
                FileProgress.Progress = 0;
                FilesProgress.Progress = 0;
                StatusControlLayout.Visibility = value ? ViewStates.Visible : ViewStates.Gone;
                if (!value)
                    StatusText.Text = "";
            }));
        }


        public void ShowTaskMessage(string msg)
        {
            RunOnUiThread(new Runnable(() =>
            {
                StatusText.Text = msg == null ? "" : msg;
            }));
        }

        protected override void OnDestroy()
        {
            Logout();
            WindowUtils.RemoveFromRecents(this, true);
            base.OnDestroy();
        }


    }
}