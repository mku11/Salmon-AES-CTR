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

using global::Android.Content;
using Java.Lang;
using Mku.FS.File;
using Mku.FS.Streams;
using Xamarin.Android.Net;
using static Mku.FS.File.HttpFile;

namespace Mku.Android.FS.File;

/// <summary>
///  Utility for Android file system
/// </summary>
///
public class AndroidFileSystem
{
    private static Context context;

    /// <summary>
    /// Initialize the Android Drive before creating or opening any virtual drives.
    /// </summary>
    /// <param name="context">The Android context</param>
    public static void Initialize(Context context)
    {
        AndroidFileSystem.context = context.ApplicationContext;

        // android needs its own handler
        HttpSyncClient client = new HttpSyncClient(new AndroidMessageHandler());
        HttpFile.Client = client;
        HttpFileStream.Client = client;
        WSFile.Client = client;
        WSFileStream.Client = client;
    }

    /// <summary>
    /// 
    /// Get the Android context.
    /// </summary>
    /// <returns>The android context</returns>
    /// <exception cref="RuntimeException">If context is not found</exception>
    public static Context GetContext()
    {
        if (context == null)
            throw new RuntimeException("Use AndroidFileSystem.initialize() before using any file");
        return context;
    }
}
