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

using Salmon.Vault.Model;
using System.ComponentModel;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace Salmon.Vault.ViewModel;

public class ImageViewerViewModel : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler PropertyChanged;
    private SalmonImageViewer viewer;

    public ImageSource _imageSource;
    public ImageSource ImageSource
    {
        get => _imageSource;
        set
        {
            _imageSource = value;
            if (PropertyChanged != null)
                PropertyChanged(this, new PropertyChangedEventArgs("ImageSource"));
        }
    }
    
    public ImageViewerViewModel()
    {
        viewer = new SalmonImageViewer();
    }

    public void Load(SalmonFileViewModel file)
    {
        viewer.Load(file.GetSalmonFile());
        BitmapImage imageSource = new BitmapImage();
        imageSource.BeginInit();
        imageSource.StreamSource = viewer.ImageStream;
        imageSource.EndInit();
        ImageSource = imageSource;
    }

    public void OnClosing()
    {
        viewer.OnClosing();
    }
}