using Android.App;
using Android.Content;
using Android.Provider;
using Android.Widget;
using Mku.Android.FS.File;
using Mku.Android.SalmonFS.Drive;
using Mku.FS.File;
using System;
using Uri = Android.Net.Uri;

namespace Mku.Salmon.Samples.Utils;

public class AndroidFileChooser
{

    public static void OpenFilesystem(Activity activity, int resultCode, bool isFolder)
    {
        OpenFilesystem(activity, resultCode, isFolder, false);
    }

    public static void OpenFilesystem(Activity activity, int resultCode, bool isFolder, bool multiSelect)
    {
        Intent intent = new Intent(isFolder ? Intent.ActionOpenDocumentTree : Intent.ActionOpenDocument);
        intent.AddFlags(ActivityFlags.GrantPersistableUriPermission);
        intent.AddFlags(ActivityFlags.GrantReadUriPermission);
        intent.AddFlags(ActivityFlags.GrantWriteUriPermission);

        if (!isFolder)
        {
            intent.AddCategory(Intent.CategoryOpenable);
            intent.SetType("*/*");
        }

        if (!isFolder && multiSelect)
        {
            intent.PutExtra(Intent.ExtraAllowMultiple, true);
        }

        string prompt = "Open File(s)";
        if (isFolder)
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
            Toast.MakeText(activity, "Could not start file picker: " + e.Message, ToastLength.Long).Show();
        }
    }

    public static void SetUriPermissions(Context context, Intent data, Uri uri)
    {
        ActivityFlags takeFlags = 0;
        if (data != null)
            takeFlags = data.Flags;
        takeFlags &= (ActivityFlags.GrantReadUriPermission | ActivityFlags.GrantWriteUriPermission);

        try
        {
            context.GrantUriPermission(context.PackageName, uri, ActivityFlags.GrantReadUriPermission);
            context.GrantUriPermission(context.PackageName, uri, ActivityFlags.GrantWriteUriPermission);
            context.GrantUriPermission(context.PackageName, uri, ActivityFlags.GrantPersistableUriPermission);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            string err = "Could not grant uri perms to activity: " + ex;
            Toast.MakeText(context, err, ToastLength.Long).Show();
        }

        try
        {
            context.ContentResolver.TakePersistableUriPermission(uri, takeFlags);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            string err = "Could not take Persistable perms: " + ex;
            Toast.MakeText(context, err, ToastLength.Long).Show();
        }
    }

    public static IFile[] GetFiles(Context context, Intent data)
    {
        IFile[] files = null;
        if (data != null)
        {
            if (null != data.ClipData)
            {
                files = new IFile[data.ClipData.ItemCount];
                for (int i = 0; i < data.ClipData.ItemCount; i++)
                {
                    Uri uri = data.ClipData.GetItemAt(i).Uri;
                    AndroidFileChooser.SetUriPermissions(context, data, uri);
                    files[i] = AndroidFileSystem.GetRealFile(uri.ToString(), false);
                }
            }
            else
            {
                Uri uri = data.Data;
                files = new IFile[1];
                files[0] = AndroidFileSystem.GetRealFile(uri.ToString(), false);
            }
        }
        return files;
    }
}
