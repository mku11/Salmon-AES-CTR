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

using AndroidX.DocumentFile.Provider;
using Mku.Android.FS.File;
using Mku.FS.File;
using Mku.SalmonFS.Drive;
using Mku.SalmonFS.File;
using Mku.FS.Drive.Utils;
using Mku.SalmonFS.Drive.Utils;
using Uri = Android.Net.Uri;
using Mku.Salmon.Sequence;

namespace Mku.Android.SalmonFS.Drive;

/// <summary>
///  Implementation of a virtual drive for android.
/// </summary>
///
public class AndroidDrive : AesDrive
{
    public static readonly string TAG = nameof(AndroidDrive);
    private static readonly int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
    private static readonly int ENC_THREADS = 4;

    /// <summary>
    /// Private constructor, use open() and create() instead.
    /// </summary>
    private AndroidDrive() : base()
    {

    }

    /// <summary>
    /// Helper method that opens and initializes an AndroidDrive
    /// </summary>
    /// <param name="dir">The directory that will host the drive.</param>
    /// <param name="password">The password</param>
    /// <param name="sequencer">The nonce sequencer that will be used for encryption.</param>
    /// <returns>The drive</returns>
    public static AesDrive Open(IFile dir, string password, INonceSequencer sequencer)
    {
        return AesDrive.OpenDrive(dir, typeof(AndroidDrive), password, sequencer);
    }

    /// <summary>
    /// Helper method that creates and initializes an AndroidDrive
    /// </summary>
    /// <param name="dir">The directory that will host the drive.</param>
    /// <param name="password">The password</param>
    /// <param name="sequencer">The nonce sequencer that will be used for encryption.</param>
    /// <returns>The drive</returns>
    public static AesDrive Create(IFile dir, string password, INonceSequencer sequencer)
    {
        return AesDrive.CreateDrive(dir, typeof(AndroidDrive), password, sequencer);
    }

    public Java.IO.File CopyToSharedFolder(AesFile salmonFile)
    {
        Java.IO.File privateDir = new Java.IO.File(this.PrivateDir.DisplayPath);
        Java.IO.File cacheFile = new Java.IO.File(privateDir, salmonFile.Name);
        AndroidSharedFileObserver.RemoveFileObserver(cacheFile);
        cacheFile.Delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.FromFile(privateDir));
        AesFileExporter fileExporter = new AesFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        FileExporter.FileExportOptions exportOptions = new FileExporter.FileExportOptions();
        exportOptions.integrity = true;
        fileExporter.ExportFile(salmonFile, sharedDir, exportOptions);
        return cacheFile;
    }

    /// <summary>
    ///  Get the private directory that will be used for sharing encrypted context with
    ///  other apps on the android device.
	/// </summary>
	///  <returns>The private directory</returns>
    override
    public IFile PrivateDir
    {
        get
        {
            Java.IO.File sharedDir = new Java.IO.File(AndroidFileSystem.GetContext().CacheDir, ShareDirectoryName);
            if (!sharedDir.Exists())
                sharedDir.Mkdir();
            return new File(sharedDir.AbsolutePath);
        }
    }

    /// <summary>
    ///  Fired when drive unlocks
	/// </summary>
	///
    override
    public void OnUnlockSuccess()
    {
        AndroidSharedFileObserver.ClearFileObservers();
        ClearCache(AndroidFileSystem.GetContext().CacheDir);
    }

    /// <summary>
    ///  Fired when authorization fails.
	/// </summary>
	///
    override
    public void OnUnlockError()
    {
        AndroidSharedFileObserver.ClearFileObservers();
        ClearCache(AndroidFileSystem.GetContext().CacheDir);
    }

    /// <summary>
    ///  Clear the cache and the private folder that is used to share files with
    ///  other apps.
	/// </summary>
	///  <param name="file">The file</param>
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
