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
using Salmon.FS;
using Java.IO;
using System;
using System.Collections.Generic;

namespace Salmon.Droid.Model
{
    /// <summary>
    /// Implementation of a file observer that will detect if the file contents have changed
    /// </summary>
    public class AndroidSharedFileObserver : FileObserver
    {
        // we need to keep a reference of all the live fileObservers because they will stop firing events
        private static Dictionary<string, AndroidSharedFileObserver> fileObservers = new Dictionary<string, AndroidSharedFileObserver>();

        private SalmonFile salmonFile;
        private Action<AndroidSharedFileObserver> onFileContentsChanged;
        
        private AndroidSharedFileObserver(File file, SalmonFile salmonFile,
            Action<AndroidSharedFileObserver> onFileContentsChanged) : base(file, FileObserverEvents.CloseWrite)
        {
            this.salmonFile = salmonFile;
            this.onFileContentsChanged = onFileContentsChanged;
        }

        public override void OnEvent(FileObserverEvents e, string path)
        {
            if (onFileContentsChanged != null)
                onFileContentsChanged.Invoke(this);
        }

        /// <summary>
        /// Returns the encrypted salmon file that is associated with the shared file
        /// </summary>
        /// <returns></returns>
        public SalmonFile GetSalmonFile()
        {
            return salmonFile;
        }

        /// <summary>
        /// Create a file observer that will detect if the file contents of a shared salmon file have changed
        /// </summary>
        /// <param name="cacheFile">The temporrary private decrypted cached file</param>
        /// <param name="salmonFile">The encrypted file that is associated with the shared file</param>
        /// <param name="onFileContentsChanged">Action notifier when file contents change</param>
        /// <returns></returns>
        public static AndroidSharedFileObserver CreateFileObserver(File cacheFile, SalmonFile salmonFile,
            Action<AndroidSharedFileObserver> onFileContentsChanged)
        {
            AndroidSharedFileObserver fileObserver = new AndroidSharedFileObserver(cacheFile,
                salmonFile, onFileContentsChanged);
            fileObservers[cacheFile.Path] = fileObserver;
            return fileObserver;
        }

        public static void RemoveFileObserver(File cacheFile)
        {
            if (fileObservers.ContainsKey(cacheFile.Path))
            {
                AndroidSharedFileObserver fileObserver = fileObservers[cacheFile.Path];
                fileObserver.onFileContentsChanged = null;
                fileObservers.Remove(cacheFile.Path);
            }
        }

        public static void ClearFileObservers()
        {
            fileObservers.Clear();
        }

        internal void SetSalmonFile(SalmonFile salmonFile)
        {
            this.salmonFile = salmonFile;
        }
    }

}