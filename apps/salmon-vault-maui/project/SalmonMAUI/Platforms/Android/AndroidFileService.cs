
using Android.App;
using AndroidX.DocumentFile.Provider;
using Mku.Android.File;
using Mku.File;
using Salmon.Vault.Services;

namespace Salmon.Vault.MAUI.ANDROID;

public class AndroidFileService : IFileService
{
    public readonly int REQUEST_DIR = 1000;
    private Activity activity;

    public AndroidFileService(Activity activity)
    {
        this.activity = activity;
    }

    public IRealFile GetFile(string filepath, bool isDirectory)
    {
        IRealFile file;
        if (filepath.StartsWith("content:"))
        {
            DocumentFile docFile;
            if (isDirectory)
                docFile = DocumentFile.FromTreeUri(activity, Android.Net.Uri.Parse(filepath));
            else
                docFile = DocumentFile.FromSingleUri(activity, Android.Net.Uri.Parse(filepath));
            file = new AndroidFile(docFile, activity);
        } else
        {
            file = new DotNetFile(filepath);
        }
        return file;
    }

}