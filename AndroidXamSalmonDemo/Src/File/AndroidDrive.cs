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
using System;
using System.Collections.Generic;
using Android.App;
using Android.Content;
using Android.OS;
using Android.Provider;
using Android.Widget;
using AndroidX.DocumentFile.Provider;
using Salmon.Droid.Utils;
using Salmon.FS;
using Uri = Android.Net.Uri;
using Salmon.Droid.Main;
using Android.Util;

namespace Salmon.Droid.FS
{
    /// <summary>
    /// Implementation of a virtual drive for android.
    /// </summary>
    public class AndroidDrive : SalmonDrive
    {
        public static string TAG = typeof(AndroidDrive).Name;
        public static readonly string EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";
        private static readonly int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
        private static readonly int ENC_THREADS = 4;
        public static int RequestSdcardCodeFile = 10000;
        public static int RequestSdcardCodeFolder = 10001;

        /// <summary>
        /// Construct a virtual Drive for android under a real directory path
        /// </summary>
        /// <param name="realRoot">The path of the real directory</param>
        public AndroidDrive(string realRoot) : base(realRoot) { }

        protected override IRealFile GetFile(string filepath, bool root)
        {
            DocumentFile docFile;
            if (root)
                docFile = DocumentFile.FromTreeUri(Application.Context, Uri.Parse(filepath));
            else
                docFile = DocumentFile.FromSingleUri(Application.Context, Uri.Parse(filepath));

            AndroidFile file = new AndroidFile(docFile, Application.Context);
            return file;
        }

        /// <summary>
        /// Prompt user to select a real directory for create the virtual drive
        /// </summary>
        /// <param name="sender"></param>
        /// <param name="body"></param>
        /// <param name="folder"></param>
        /// <param name="lastDir"></param>
        public override void PickRealFolder(object sender, string body, bool folder, string lastDir)
        {
            Activity activity = sender as Activity;
            activity.RunOnUiThread(() =>
            {
                ActivityCommon.PromptDialog(activity, null, body, "OK", (object sender, DialogClickEventArgs e) =>
                {
                    PromptSAFOpenDocument(activity, folder, lastDir);
                });
            });
        }

        protected override void OnAuthenticationSuccess()
        {
            AndroidSharedFileObserver.ClearFileObservers();
            ClearCache(Application.Context.CacheDir);
        }

        protected override void OnAuthenticationError()
        {
            AndroidSharedFileObserver.ClearFileObservers();
            ClearCache(Application.Context.CacheDir);
        }

        /// <summary>
        /// Clear the cache of any shared files and other temporary files
        /// </summary>
        /// <param name="file"></param>
        private void ClearCache(Java.IO.File file)
        {
            if (file.Exists() && file.IsDirectory)
            {
                Java.IO.File[] cacheFiles = file.ListFiles();
                foreach (Java.IO.File cacheFile in cacheFiles)
                {
                    ClearCache(cacheFile);
                }
            }
            else
            {
                file.Delete();
            }
        }

        /// <summary>
        /// Prompt the user for a Storage Access Framework uri
        /// </summary>
        /// <param name="activity"></param>
        /// <param name="folder"></param>
        /// <param name="lastDir"></param>
        private void PromptSAFOpenDocument(Activity activity, bool folder, string lastDir)
        {
            Intent intent = new Intent(folder ? Intent.ActionOpenDocumentTree : Intent.ActionOpenDocument);
            intent.AddFlags(ActivityFlags.GrantPersistableUriPermission);
            intent.AddFlags(ActivityFlags.GrantReadUriPermission);
            intent.AddFlags(ActivityFlags.GrantWriteUriPermission);

            if (folder && lastDir == null)
            {
                try
                {
                    Uri uri = DocumentsContract.BuildDocumentUri(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, "primary:");
                    intent.PutExtra(DocumentsContract.ExtraInitialUri, uri);
                }
                catch (Exception ex)
                {
                    Console.WriteLine(ex);
                }
            }
            if (!folder)
            {
                intent.PutExtra(Intent.ExtraAllowMultiple, true);
                intent.AddCategory(Intent.CategoryOpenable);
                intent.SetType("*/*");
            }

            string prompt = "Open File(s)";
            if (folder)
                prompt = "Open Directory";

            intent.PutExtra(DocumentsContract.ExtraPrompt, prompt);
            intent.PutExtra("android.content.extra.SHOW_ADVANCED", true);
            intent.PutExtra(Intent.ExtraLocalOnly, true);
            try
            {
                activity.StartActivityForResult(intent, folder ? RequestSdcardCodeFolder : RequestSdcardCodeFile);
            }
            catch (Exception e)
            {
                Toast.MakeText(activity, "Could not start file picker!" + e.Message, ToastLength.Long).Show();
            }
        }

        public static void SetFilePermissions(Intent data, Uri uri)
        {
            int takeFlags = 0;
            if (data != null)
                takeFlags = (int)data.Flags;
            takeFlags &= (
                    (int)ActivityFlags.GrantReadUriPermission |
                    (int)ActivityFlags.GrantWriteUriPermission
            );

            try
            {
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantReadUriPermission);
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantWriteUriPermission);
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantPersistableUriPermission);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
                string err = "Could not grant uri perms to activity: " + ex;
                Toast.MakeText(Application.Context, err, ToastLength.Long).Show();
            }

            try
            {
                Application.Context.ContentResolver.TakePersistableUriPermission(uri, (ActivityFlags)takeFlags);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
                string err = "Could not take Persistable perms: " + ex;
                Toast.MakeText(Application.Context, err, ToastLength.Long).Show();
            }
        }


        public static IList<UriPermission> GetPermissionsList()
        {
            IList<UriPermission> list = null;
            if (Build.VERSION.SdkInt >= Android.OS.BuildVersionCodes.Kitkat)
            {
                list = Application.Context.ContentResolver.PersistedUriPermissions;
            }
            return list;
        }

        // TODO: use multithreaded for performance
        public static Java.IO.File CopyToSharedFolder(SalmonFile salmonFile)
        {
            Java.IO.File privateDir = GetPrivateDir();
            Java.IO.File cacheFile = new Java.IO.File(privateDir, salmonFile.GetBaseName());
            AndroidSharedFileObserver.RemoveFileObserver(cacheFile);
            cacheFile.Delete();

            AndroidFile sharedDir = new AndroidFile(DocumentFile.FromFile(privateDir), Application.Context);
            SalmonFileExporter fileExporter = new SalmonFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
            fileExporter.ExportFile(salmonFile, sharedDir, false);
            return cacheFile;
        }
        protected static Java.IO.File GetPrivateDir()
        {
            Java.IO.File sharedDir = new Java.IO.File(Application.Context.CacheDir, SHARE_DIR);
            if (!sharedDir.Exists())
                sharedDir.Mkdir();
            return sharedDir;
        }

        /// <summary>
        /// Retrieve real files from an intent that was received
        /// </summary>
        /// <param name="context"></param>
        /// <param name="data"></param>
        /// <returns></returns>
        public static IRealFile[] GetFilesFromIntent(Context context, Intent data)
        {
            IRealFile[] filesToImport = null;

            if (data != null)
            {
                if (null != data.ClipData)
                { // checking multiple selection or not
                    filesToImport = new IRealFile[data.ClipData.ItemCount];
                    for (int i = 0; i < data.ClipData.ItemCount; i++)
                    {
                        Android.Net.Uri uri = data.ClipData.GetItemAt(i).Uri;
                        string filename = uri.ToString();
                        Log.Debug(TAG, "File: " + filename);
                        AndroidDrive.SetFilePermissions(data, uri);
                        DocumentFile docFile = DocumentFile.FromSingleUri(context, uri);
                        filesToImport[i] = new AndroidFile(docFile, context);
                    }
                }
                else
                {
                    Android.Net.Uri uri = data.Data;
                    filesToImport = new IRealFile[1];
                    AndroidDrive.SetFilePermissions(data, uri);
                    DocumentFile docFile = DocumentFile.FromSingleUri(context, uri);
                    filesToImport[0] = new AndroidFile(docFile, context);
                }
            }
            return filesToImport;
        }

        /// <summary>
        /// Get an android file from a DocumentFile
        /// </summary>
        /// <param name="docFile"></param>
        /// <returns></returns>
        public static IRealFile GetFile(DocumentFile docFile)
        {
            return new AndroidFile(docFile, Application.Context);
        }
    }
}