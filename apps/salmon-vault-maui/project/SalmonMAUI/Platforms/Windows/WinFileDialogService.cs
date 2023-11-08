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

using Microsoft.Maui;
using Mku.File;
using Salmon.Vault.Services;
using System;
using System.Collections.Generic;
using System.Linq;
using Windows.Storage;
using Windows.Storage.Pickers;

namespace Salmon.Vault.MAUI.WinUI;

public class WinFileDialogService : IFileDialogService
{
    public Action<object> GetCallback(int requestCode)
    {
        return null;
    }

    public async void OpenFile(string title, string filename, Dictionary<string, string> filter, string initialDirectory,
        Action<object> OnFilePicked, int requestCode)
    {
        FileOpenPicker picker = new FileOpenPicker();
        if (filter != null)
        {
            foreach (string key in filter.Keys)
                picker.FileTypeFilter.Add("." + filter[key]);
        } else
        {
            picker.FileTypeFilter.Add("*");
        }
        var hwnd = ((MauiWinUIWindow)MauiWinUIApplication.Current.Application
            .Windows[0].Handler.PlatformView).WindowHandle;
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        StorageFile file = await picker.PickSingleFileAsync();
        if (file != null)
        {
            OnFilePicked(file.Path);
        }
    }

    public async void OpenFiles(string title, Dictionary<string, string> filter, string initialDirectory, Action<object> OnFilesPicked, int requestCode)
    {
        FileOpenPicker picker = new FileOpenPicker();
        if (filter != null)
        {
            foreach (string key in filter.Keys)
                picker.FileTypeFilter.Add("." + filter[key]);
        }
        else
        {
            picker.FileTypeFilter.Add("*");
        }
        var hwnd = ((MauiWinUIWindow)MauiWinUIApplication.Current.Application
            .Windows[0].Handler.PlatformView).WindowHandle;
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        IReadOnlyList<StorageFile> files = await picker.PickMultipleFilesAsync();
        if (files != null)
        {
            List<string> filesPaths = new List<string>(files.Select(x => x.Path));
            OnFilesPicked(filesPaths.ToArray());
        }
    }

    public async void PickFolder(string title, string initialDirectory, Action<object> OnFolderPicked, int requestCode)
    {
        FolderPicker picker = new FolderPicker();
        picker.FileTypeFilter.Add("*");
        var hwnd = ((MauiWinUIWindow)MauiWinUIApplication.Current.Application
            .Windows[0].Handler.PlatformView).WindowHandle;
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        var file = await picker.PickSingleFolderAsync();
        if (file != null)
        {
            OnFolderPicked(file.Path);
        }
    }

    public async void SaveFile(string title, string filename, Dictionary<string, string> filter, string initialDirectory, Action<object> OnFilePicked, int requestCode)
    {
        FileSavePicker picker = new FileSavePicker();
        foreach (string key in filter.Keys)
            picker.FileTypeChoices[key] = new List<string> { "." + filter[key] };
        if (filename != null)
            picker.SuggestedFileName = filename;
        var hwnd = ((MauiWinUIWindow)MauiWinUIApplication.Current.Application
            .Windows[0].Handler.PlatformView).WindowHandle;
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);
        StorageFile file = await picker.PickSaveFileAsync();
        if (file != null)
        {
            DotNetFile rfile = new DotNetFile(file.Path);
            if (rfile.Parent == null)
                return;
            string dir = rfile.Parent.Path;
            OnFilePicked(new string[] { dir, file.Name });
        }
    }
}