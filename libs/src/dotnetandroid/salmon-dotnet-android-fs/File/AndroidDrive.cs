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
using AndroidX.DocumentFile.Provider;
using Mku.File;
using Mku.SalmonFS;
using Mku.Utils;
using Uri = Android.Net.Uri;

namespace Mku.Android.File;

/// <summary>
///  Implementation of a virtual drive for android.
/// </summary>
///
public class AndroidDrive : SalmonDrive
{
    public static readonly string TAG = nameof(AndroidDrive);
    private static readonly int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static readonly int ENC_THREADS = 4;
    private static Context context;

    /// <summary>
    ///  Initialize the Android Drive. This needs to run before you attempt to
    ///  create or open any virtual drives.
	/// </summary>
	///  <param name="context"></param>
    public static void Initialize(Context context)
    {
        AndroidDrive.context = context.ApplicationContext;
    }

    /// <summary>
    ///  Get the Android context.
	/// </summary>
	///  <returns></returns>
    public static Context Context => context;

    /// <summary>
    ///  Instantiate a virtual Drive for android under a real directory path
	/// </summary>
	///  <param name="realRoot">The path of the real directory</param>
    ///  <param name="createIfNotExists">Create the drive if it doesn't exist.</param>
    public AndroidDrive(string realRoot, bool createIfNotExists) : base(realRoot, createIfNotExists)
    {

    }

    public Java.IO.File CopyToSharedFolder(SalmonFile salmonFile)
    {
        Java.IO.File privateDir = GetPrivateDir();
        Java.IO.File cacheFile = new Java.IO.File(privateDir, salmonFile.BaseName);
        AndroidSharedFileObserver.RemoveFileObserver(cacheFile);
        cacheFile.Delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.FromFile(privateDir), context);
        SalmonFileExporter fileExporter = new SalmonFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        fileExporter.ExportFile(salmonFile, sharedDir, null,  false, true, null);
        return cacheFile;
    }

    /// <summary>
    ///  Get the private directory that will be used for sharing encrypted context with
    ///  other apps on the android device.
	/// </summary>
	///  <returns></returns>
    protected Java.IO.File GetPrivateDir()
    {
        Java.IO.File sharedDir = new Java.IO.File(context.CacheDir, ShareDirectoryName);
        if (!sharedDir.Exists())
            sharedDir.Mkdir();
        return sharedDir;
    }

    /// <summary>
    ///  Get the real file hosted on the android device.
	/// </summary>
	///  <param name="filepath"></param>
    ///  <param name="isDirectory">True if filepath corresponds to a directory.</param>
    ///  <returns></returns>
    override
    public IRealFile GetRealFile(string filepath, bool isDirectory)
    {
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.FromTreeUri(context, Uri.Parse(filepath));
        else
            docFile = DocumentFile.FromSingleUri(context, Uri.Parse(filepath));
        AndroidFile file = new AndroidFile(docFile, context);
        return file;
    }

    /// <summary>
    ///  Fired when authentication succeeds.
	/// </summary>
	///
    override
    protected void OnAuthenticationSuccess()
    {
        AndroidSharedFileObserver.ClearFileObservers();
        ClearCache(context.CacheDir);
    }

    /// <summary>
    ///  Fired when authentication fails.
	/// </summary>
	///
    override
    protected void OnAuthenticationError()
    {
        AndroidSharedFileObserver.ClearFileObservers();
        ClearCache(context.CacheDir);
    }

    /// <summary>
    ///  Clear the cache and the private folder that is used to share files with
    ///  other apps.
	/// </summary>
	///  <param name="file"></param>
    private void ClearCache(Java.IO.File file)
    {
        if (file.Exists() && file.IsDirectory)
        {
            Java.IO.File[] cacheFiles = file.ListFiles();
            if (cacheFiles != null)
            {
                foreach (Java.IO.File cacheFile in cacheFiles)
                {
                    ClearCache(cacheFile);
                }
            }
        }
        else
        {
            file.Delete();
        }
    }
}
