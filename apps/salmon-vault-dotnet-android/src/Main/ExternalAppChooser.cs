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
using Android.Webkit;
using AndroidX.Core.App;
using AndroidX.Core.Content;
using Mku.Android.File;
using Mku.SalmonFS;
using Mku.Utils;
using System.Collections.Generic;
using Salmon.Vault.Extensions;
using System;
using Android.Widget;
using Android.Content.PM;
using Salmon.Vault.Dialog;
using Android.Net;
using Salmon.Vault.DotNetAndroid;
using Salmon.Vault.Config;

namespace Salmon.Vault.Main;

public class ExternalAppChooser
{
    public static void ChooseApp(Activity activity, SalmonFile salmonFile, int action, 
        Action<Android.Net.Uri, AndroidSharedFileObserver> ReimportSharedFile)
    {
        Java.IO.File sharedFile = (SalmonDriveManager.Drive as AndroidDrive).CopyToSharedFolder(salmonFile);
        sharedFile.DeleteOnExit();
        string ext = SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
        string mimeType = MimeTypeMap.Singleton.GetMimeTypeFromExtension(ext);
        Android.Net.Uri uri = FileProvider.GetUriForFile(activity, SalmonConfig.FILE_PROVIDER, sharedFile);
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.From(activity).SetType(mimeType);

        Intent intent;
        // if we just share (final) we can show the android chooser activity
        // since we don't have to grant the app write permissions
        if (action == ActionType.VIEW_EXTERNAL.Ordinal())
        {
            intent = builder.CreateChooserIntent();
            intent.SetAction(Intent.ActionView);
            intent.SetData(uri);

            intent.AddFlags(ActivityFlags.GrantReadUriPermission);
            Intent finalIntent1 = intent;
            activity.RunOnUiThread(() =>
            {
                try
                {
                    activity.StartActivity(finalIntent1);
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                    Toast.MakeText(activity, activity.GetString(Resource.String.NoApplicationsFound), ToastLength.Long).Show();
                }
            });
        }
        else
        {

            // we show only apps that explicitly have intent filters for action edit
            if (action == ActionType.SHARE.Ordinal())
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
            SortedDictionary<string, string> apps = GetAppsForIntent(activity, intent);
            Intent finalIntent = intent;
            activity.RunOnUiThread(() =>
            {
                SalmonDialog.PromptOpenWith(finalIntent, apps, uri, sharedFile, salmonFile, 
                    action == ActionType.EDIT.Ordinal(), (fileObserver) =>
                    {
                        ReimportSharedFile(uri, fileObserver);
                    });
            });
        }
    }

    public static SortedDictionary<string, string> GetAppsForIntent(Activity activity, Intent intent)
    {
        IList<ResolveInfo> appInfoList = activity.PackageManager.QueryIntentActivities(intent, 0);
        SortedDictionary<string, string> apps = new SortedDictionary<string, string>();
        foreach (ResolveInfo resolveInfo in appInfoList)
        {
            string name = activity.PackageManager.GetApplicationLabel(resolveInfo.ActivityInfo.ApplicationInfo).ToString();
            string packageName = resolveInfo.ActivityInfo.ApplicationInfo.PackageName;
            apps[name] = packageName;
        }
        return apps;
    }

}