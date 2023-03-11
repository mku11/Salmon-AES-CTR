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
using Android.Util;

namespace Salmon.Droid.FS
{
    /// <summary>
    /// Implementation of a virtual drive for android.
    /// </summary>
    public class AndroidDrive : SalmonDrive
    {
        public static string TAG = typeof(AndroidDrive).Name;
        private static readonly int ENC_BUFFER_SIZE = 5 * 1024 * 1024;
        private static readonly int ENC_THREADS = 4;

        /// <summary>
        /// Construct a virtual Drive for android under a real directory path
        /// </summary>
        /// <param name="realRoot">The path of the real directory</param>
        public AndroidDrive(string realRoot) : base(realRoot) { }

        public override IRealFile GetFile(string filepath, bool folder)
        {
            DocumentFile docFile;
            if (folder)
                docFile = DocumentFile.FromTreeUri(Application.Context, Uri.Parse(filepath));
            else
                docFile = DocumentFile.FromSingleUri(Application.Context, Uri.Parse(filepath));

            AndroidFile file = new AndroidFile(docFile, Application.Context);
            return file;
        }

        protected override void OnAuthenticationSuccess()
        {
            AndroidSharedFileObserver.ClearFileObservers();
            try
            {
                ClearCache(Application.Context.CacheDir);
            } catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
        }

        protected override void OnAuthenticationError()
        {
            AndroidSharedFileObserver.ClearFileObservers();
            try
            {
                ClearCache(Application.Context.CacheDir);
            } catch (Exception ex)
            {
                ex.PrintStackTrace();
            }
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