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
using Mku.SalmonFS;
using Salmon.Vault.Model;
using System.ComponentModel;

namespace Salmon.Vault.ViewModel;

public class ContentViewerViewModel : INotifyPropertyChanged
{
    private SalmonFileViewModel item;
    private SalmonContentViewer contentViewer;

    private string _source;
    public string Source
    {
        get => _source;
        set
        {
            _source = value;
            if (PropertyChanged != null)
                PropertyChanged(this, new PropertyChangedEventArgs("Source"));
        }
    }
    public event PropertyChangedEventHandler PropertyChanged;

    public ContentViewerViewModel()
    {
        contentViewer = new SalmonContentViewer();
        contentViewer.PropertyChanged += ContentViewer_PropertyChanged;
    }
    private void ContentViewer_PropertyChanged(object sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == "Source")
        {
            Source = contentViewer.Source;
        }
    }

    public void Load(SalmonFileViewModel fileItem)
    {
        item = fileItem;
        SalmonFile file = item.GetSalmonFile();
        contentViewer.Load(file);
    }

    public void OnClose()
    {
        contentViewer.OnClose();
    }

}