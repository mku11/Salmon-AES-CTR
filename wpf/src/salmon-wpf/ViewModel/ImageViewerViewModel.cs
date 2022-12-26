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
using Salmon.FS;
using Salmon.Model;
using Salmon.Streams;
using Salmon.View;
using System;
using System.ComponentModel;
using System.IO;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using static Salmon.Settings.Settings;

namespace Salmon.ViewModel
{
    public class ImageViewerViewModel : INotifyPropertyChanged
    {
        private System.Windows.Window window;

        public event PropertyChangedEventHandler PropertyChanged;
        public ImageSource _imageSource;
        private SalmonStream stream;

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

        public void SetWindow(System.Windows.Window window, System.Windows.Window owner)
        {
            this.window = window;
            this.window.Owner = owner;
        }

        public static void OpenImageViewer(FileItem file, System.Windows.Window owner)
        {
            ImageViewer imageViewer = new ImageViewer();
            imageViewer.SetWindow(owner);
            imageViewer.Load(file);
            imageViewer.ShowDialog();
        }

        public void Load(FileItem file)
        {
            SalmonFile salmonFile = ((SalmonFileItem)file).GetSalmonFile();
            try
            {
                stream = salmonFile.GetInputStream();
                BitmapImage imageSource = new BitmapImage();
                imageSource.BeginInit();
                imageSource.StreamSource = stream;
                imageSource.EndInit();
                ImageSource = imageSource;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        public void OnClosing()
        {
            if(stream!=null)
            {
                stream.Close();
            }
        }
    }
}