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
using System.ComponentModel;
using System.Windows.Media.Imaging;

namespace Salmon.Model
{
    public abstract class FileItem : INotifyPropertyChanged
    {

        private string _name;
        public virtual string Name
        {
            get => _name;
            set
            {
                if (_name != value)
                {
                    _name = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("Name"));
                }
            }
        }

        private string _date;
        public virtual string Date
        {
            get => _date;
            set
            {
                if (_date != value)
                {
                    _date = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("Date"));
                }
            }
        }

        private string _type;
        public virtual string Type
        {
            get => _type;
            set
            {
                if (_type != value)
                {
                    _type = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("Type"));
                }
            }
        }

        private string _size;
        public virtual string Size
        {
            get => _size;
            set
            {
                if (_size != value)
                {
                    _size = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("Size"));
                }
            }
        }

        private string _path;
        public virtual string Path
        {
            get => _path;
            set
            {
                if (_path != value)
                {
                    _path = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("Path"));
                }
            }
        }

        
        public event PropertyChangedEventHandler PropertyChanged;

        public virtual BitmapImage Image { get; set; }


        public abstract bool IsDirectory();

        public abstract string GetBaseName();

        public abstract FileItem[] ListFiles();

        public abstract long GetSize();

        public abstract object GetTag();

        public abstract void CreateDirectory(string folderName, byte[] key, byte[] dirNameNonce);

        public abstract long GetLastDateTimeModified();

        public abstract void Delete();

        public abstract void Rename(string newValue);

        public abstract void Update();
    }

}