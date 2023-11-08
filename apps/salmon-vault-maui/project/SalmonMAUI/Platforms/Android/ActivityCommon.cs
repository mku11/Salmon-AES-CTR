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
using Context = Android.Content.Context;
using Android.Provider;
using Uri = Android.Net.Uri;
using Android.Util;
using AndroidX.DocumentFile.Provider;
using Android.App;
using System;
using Android.Widget;
using Salmon.Vault.Extensions;
using System.Collections.Generic;
using System.Linq;
using Android.Webkit;

namespace Salmon.Vault.Main;

public partial class ActivityCommon
{
    static readonly string TAG = typeof(ActivityCommon).Name;
    public static readonly string EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";

    /// <summary>
    /// Prompt the user for a Storage Access Framework uri
    /// </summary>
    /// <param name="activity"></param>
    /// <param name="folder"></param>
    /// <param name="initialDir"></param>
    public static void OpenFilesystem(Activity activity, bool folder, bool multiSelect,
        Dictionary<string, string> filter, string initialDir, int resultCode)
    {
        Intent intent = new Intent(folder ? Intent.ActionOpenDocumentTree : Intent.ActionOpenDocument);
        intent.AddFlags(ActivityFlags.GrantPersistableUriPermission);
        intent.AddFlags(ActivityFlags.GrantReadUriPermission);
        intent.AddFlags(ActivityFlags.GrantWriteUriPermission);

        if (initialDir != null)
        {
            try
            {
                DocumentFile documentFile;
                if (folder)
                    documentFile = DocumentFile.FromTreeUri(activity, Uri.Parse(initialDir));
                else
                    documentFile = DocumentFile.FromSingleUri(activity, Uri.Parse(initialDir));
                intent.PutExtra(DocumentsContract.ExtraInitialUri, documentFile.Uri);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
        }
        if (!folder)
        {
            intent.AddCategory(Intent.CategoryOpenable);
            string mimeType = null;
            if (filter != null)
            {
                string filterKey = filter.Keys.ToArray()[0];
                mimeType = MimeTypeMap.Singleton.GetMimeTypeFromExtension(filter[filterKey]);
            }
            if (mimeType != null)
            {
                intent.SetType(mimeType);
            }
            else
            {
                intent.SetType("*/*");
            }
        }
        if (multiSelect)
        {
            intent.PutExtra(Intent.ExtraAllowMultiple, true);
        }

        string prompt = "Open File(s)";
        if (folder)
            prompt = "Open Directory";

        intent.PutExtra(DocumentsContract.ExtraPrompt, prompt);
        intent.PutExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.PutExtra(Intent.ExtraLocalOnly, true);
        try
        {
            activity.StartActivityForResult(intent, resultCode);
        }
        catch (Exception e)
        {
            Toast.MakeText(activity, "Could not start file picker!" + e.Message, ToastLength.Long).Show();
        }
    }

    public static void SetUriPermissions(Intent data, Uri uri)
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

    /// <summary>
    /// Retrieve real files from an intent that was received
    /// </summary>
    /// <param name="context"></param>
    /// <param name="data"></param>
    /// <returns></returns>
    public static string[] GetFilesFromIntent(Context context, Intent data)
    {
        string[] files = null;

        if (data != null)
        {
            if (null != data.ClipData)
            {
                files = new string[data.ClipData.ItemCount];
                for (int i = 0; i < data.ClipData.ItemCount; i++)
                {
                    Android.Net.Uri uri = data.ClipData.GetItemAt(i).Uri;
                    string filename = uri.ToString();
                    Log.Debug(TAG, "File: " + filename);
                    files[i] = uri.ToString();
                }
            }
            else
            {
                Android.Net.Uri uri = data.Data;
                files = new string[1];
                files[0] = uri.ToString();
            }
        }
        return files;
    }
}