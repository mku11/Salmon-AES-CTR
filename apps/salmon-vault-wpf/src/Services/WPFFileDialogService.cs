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

using Mku.File;
using System;
using System.Collections.Generic;
using System.Windows.Forms;

namespace Salmon.Vault.Services;

public class WPFFileDialogService : IFileDialogService
{
    public Action<object> GetCallback(int requestCode)
    {
        throw new NotSupportedException();
    }

    public void PickFolder(string title, string initialDirectory, Action<object> OnFolderPicked, int requestCode)
    {
        FolderBrowserDialog directoryChooser = new FolderBrowserDialog();
        directoryChooser.Description = title;
        directoryChooser.InitialDirectory = initialDirectory;
        if (directoryChooser.ShowDialog() == DialogResult.OK)
            OnFolderPicked(directoryChooser.SelectedPath);
    }

    public void OpenFiles(string title, Dictionary<string, string> filter, string initialDirectory, Action<object> OnFilesPicked, int requestCode)
    {
        OpenFileDialog fileChooser = new OpenFileDialog();
        fileChooser.InitialDirectory = initialDirectory;
        fileChooser.Multiselect = true;
        fileChooser.Title = title;
        if (filter != null)
        {
            foreach (string key in filter.Keys)
                fileChooser.Filter += key + "(*." + filter[key] + ")| *." + filter[key];
        }
        if (fileChooser.ShowDialog() == DialogResult.Cancel)
            return;
        OnFilesPicked(fileChooser.FileNames);
    }

    public void OpenFile(string title, string filename, Dictionary<string, string> filter, string initialDirectory, 
        Action<object> OnFilePicked, int requestCode)
    {
        OpenFileDialog fileChooser = new OpenFileDialog();
        fileChooser.Title = title;
        fileChooser.FileName = filename;
        fileChooser.InitialDirectory = initialDirectory;
        if (filter != null)
        {
            foreach(string key in filter.Keys)
                fileChooser.Filter += key +"(*." + filter[key] + ")| *." + filter[key];
        }
        if (fileChooser.ShowDialog() == DialogResult.Cancel)
            return;
        OnFilePicked(fileChooser.FileName);
    }

    public void SaveFile(string title, string filename, Dictionary<string, string> filter, string initialDirectory, 
        Action<object> OnFilePicked, int requestCode)
    {
        SaveFileDialog fileChooser = new SaveFileDialog();
        fileChooser.Title = title;
        if (filter != null)
        {
            foreach (string key in filter.Keys)
                fileChooser.Filter += key + "(*." + filter[key] + ")| *." + filter[key];
        }
        fileChooser.FileName = filename;
        fileChooser.InitialDirectory = initialDirectory;
        if (fileChooser.ShowDialog() == DialogResult.Cancel)
            return;
        DotNetFile file = new DotNetFile(fileChooser.FileName);
        OnFilePicked(new string[] { file.Parent.Path, file.BaseName });
    }
}