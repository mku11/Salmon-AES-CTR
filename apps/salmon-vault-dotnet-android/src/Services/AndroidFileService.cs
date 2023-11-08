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