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

using Android.OS;
using Mku.Salmon;
using System.Collections.Generic;
using static Android.OS.Build;

namespace Mku.Android.File;

/// <summary>
///  Implementation of a file observer that detects when a share file has been edited
///  by external apps. Use this to get notified to re-import the file into the drive.
	/// </summary>
	///
public class AndroidSharedFileObserver : FileObserver
{
    /// <summary>
    ///  Keeps references of all the live fileObservers otherwise they will stop firing events.
	/// </summary>
	///
    private static readonly Dictionary<string, AndroidSharedFileObserver> fileObservers = new Dictionary<string, AndroidSharedFileObserver>();

    /// <summary>
    ///  The associated salmon file.
	/// </summary>
	///
    private SalmonFile salmonFile;

    public delegate void OnFileContentsChanged(AndroidSharedFileObserver observer);

    /// <summary>
    ///  The callback to fire when the shared file changes.
	/// </summary>
	///
    private OnFileContentsChanged onFileContentsChanged;

    /// <summary>
    ///  Instantiate a file observer associated with an encrypted file.
	/// </summary>
	///  <param name="file">The shared file.</param>
    ///  <param name="salmonFile">The SalmonFile that is associated. This file will be updated with</param>
    ///                    the contents of the shared file after the file contents are changed.
    ///  <param name="onFileContentsChanged">Callback is called the shared file contents change.</param>
    private AndroidSharedFileObserver(Java.IO.File file, SalmonFile salmonFile,
                                      OnFileContentsChanged onFileContentsChanged) :
        base(file, FileObserverEvents.CloseWrite)
    {
        this.salmonFile = salmonFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    /// <summary>
    ///  Instantiate a file observer associated with an encrypted file.
	/// </summary>
	///  <param name="filePath">The filepath for the shared filed.</param>
    ///  <param name="salmonFile">The SalmonFile that is associated. This file will be updated with</param>
    ///                    the contents of the shared file after the file contents are changed.
    ///  <param name="onFileContentsChanged">Callback is called the shared file contents change.</param>
    private AndroidSharedFileObserver(string filePath, SalmonFile salmonFile,
                                      OnFileContentsChanged onFileContentsChanged) :
        base(filePath, FileObserverEvents.CloseWrite)
    {
        this.salmonFile = salmonFile;
        this.onFileContentsChanged = onFileContentsChanged;
    }

    /// <summary>
    ///  Create a file observer that will detect if the file contents of a shared salmon file have changed
	/// </summary>
	///  <param name="cacheFile">            The temporary private decrypted cached file</param>
    ///  <param name="salmonFile">           The encrypted file that is associated with the shared file</param>
    ///  <param name="onFileContentsChanged">Action notifier when file contents change</param>
    public static AndroidSharedFileObserver CreateFileObserver(Java.IO.File cacheFile, SalmonFile salmonFile,
                                                               OnFileContentsChanged onFileContentsChanged)
    {
        AndroidSharedFileObserver fileObserver;
        if (VERSION.SdkInt>= BuildVersionCodes.Q)
        {
            fileObserver = new AndroidSharedFileObserver(cacheFile,
                    salmonFile, onFileContentsChanged);
        }
        else
        {
            fileObserver = new AndroidSharedFileObserver(cacheFile.Path,
                    salmonFile, onFileContentsChanged);
        }
        fileObservers[cacheFile.Path]= fileObserver;
        return fileObserver;
    }

    /// <summary>
    ///  Remove the shared filed associated with this observer.
	/// </summary>
	///  <param name="cacheFile">The cache file.</param>
    public static void RemoveFileObserver(Java.IO.File cacheFile)
    {
        if (fileObservers.ContainsKey(cacheFile.Path))
        {
            AndroidSharedFileObserver fileObserver = fileObservers[cacheFile.Path];
            fileObserver.onFileContentsChanged = null;
            fileObservers.Remove(cacheFile.Path);
        }
    }

    /// <summary>
    ///  Clear all file observers. Call this to release any observes you no longer need.
	/// </summary>
	///
    public static void ClearFileObservers()
    {
        fileObservers.Clear();
    }

    /// <summary>
    ///  When a file event happens.
	/// </summary>
	///  <param name="e">The type of event which happened</param>
    ///  <param name="path">The path, relative to the main monitored file or directory,</param>
    ///      of the file or directory which triggered the event.  This value can
    ///      be {@code null} for certain events, such as {@link #MOVE_SELF}.
    override
    public void OnEvent(FileObserverEvents e, string path)
    {
        if (onFileContentsChanged != null)
            onFileContentsChanged(this);
    }

    /// <summary>
    ///  Returns the encrypted salmon file that is associated with the shared file
	/// </summary>
	///
    public SalmonFile GetSalmonFile()
    {
        return salmonFile;
    }

    /// <summary>
    ///  Set the salmon file associated with the shared file to observer.
	/// </summary>
	///  <param name="salmonFile">The file</param>
    public void SetSalmonFile(SalmonFile salmonFile)
    {
        this.salmonFile = salmonFile;
    }
}

