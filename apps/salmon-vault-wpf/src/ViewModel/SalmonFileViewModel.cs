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
using Mku.Utils;
using Salmon.Vault.Image;
using Salmon.Vault.Utils;
using System;
using System.ComponentModel;
using System.Threading.Tasks;
using System.Windows.Media;

namespace Salmon.Vault.ViewModel;

public class SalmonFileViewModel : INotifyPropertyChanged
{
    private static string dateFormat = "dd/MM/yyyy hh:mm tt";

    private string _name;
    public string Name
    {
        get
        {
            if (_name == null)
            {
                UpdatePropertyAsync(() => salmonFile.BaseName, (basename) =>
                {
                    this.Name = basename;
                });
            }
            return _name;
        }
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
    public string Date
    {
        get
        {
            if (_date == null)
            {
                UpdatePropertyAsync(() => GetDateText(), (date) =>
                {
                    this.Date = date;
                });
            }
            return _date;
        }
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
    public string Type
    {
        get
        {
            if (_type == null)
            {
                UpdatePropertyAsync(() => GetExtText(), (type) =>
                {
                    this.Type = type;
                });
            }
            return _type;
        }
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

    private string _sizeText;
    public string SizeText
    {
        get
        {
            if (_sizeText == null)
            {
                UpdatePropertyAsync(() => GetSizeText(), (sizeText) =>
                {
                    this.SizeText = sizeText;
                });
            }
            return _sizeText;
        }
        set
        {
            if (_sizeText != value)
            {
                _sizeText = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("SizeText"));
            }
        }
    }

    private string _path;
    public string Path
    {
        get
        {
            if (_path == null)
            {
                UpdatePropertyAsync(() => salmonFile.Path, (path) =>
                {
                    this.Path = path;
                });
            }
            return _path;
        }
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

    ImageSource _imageSource = null;
    public ImageSource Image
    {
        get
        {
            if (_imageSource == null)
            {
                Thumbnails.GenerateThumbnail(this);
            }
            return _imageSource;
        }
        set
        {
            if (value != null)
            {
                _imageSource = value;
                NotifyProperty("Image");
            }
        }
    }

    public Color? _tintColor;
    public Color? TintColor
    {
        get
        {
            if (_tintColor == null)
            {
                UpdatePropertyAsync(() => Thumbnails.GetTintColor(salmonFile), (color) =>
                {
                    this.TintColor = color;
                });
            }
            return _tintColor;
        }
        set
        {
            if (value != null)
            {
                _tintColor = value;
                NotifyProperty("TintColor");
            }
        }
    }

    public string _ext;
    public string Ext
    {
        get
        {
            if (_ext == null)
            {
                UpdatePropertyAsync(() => Thumbnails.GetExt(salmonFile), (ext) =>
                {
                    this.Ext = ext;
                });
            }
            return _ext;
        }
        set
        {
            if (value != null)
            {
                _ext = value;
                NotifyProperty("Ext");
            }
        }
    }

    private SalmonFile salmonFile;

    private void UpdatePropertyAsync<T>(Func<T> Getter, Action<T> Setter)
    {
        Task.Run(() =>
        {
            object value = Getter();
            WindowUtils.RunOnMainThread(() => Setter((T)value));
        });
    }

    public SalmonFileViewModel(SalmonFile salmonFile)
    {
        this.salmonFile = salmonFile;
    }

    public void SetSalmonFile(SalmonFile file)
    {
        this.salmonFile = file;
        Update();
    }

    protected void NotifyProperty(string name)
    {
        if (PropertyChanged != null)
            PropertyChanged(this, new PropertyChangedEventArgs(name));
    }

    public event PropertyChangedEventHandler PropertyChanged;

    public void Update()
    {
        Name = salmonFile.BaseName;
        Date = GetDateText();
        SizeText = GetSizeText();
        Type = GetExtText();
        Path = salmonFile.Path;
    }

    private string GetExtText()
    {
        return SalmonFileUtils.GetExtensionFromFileName(salmonFile.BaseName).ToLower();
    }

    private string GetSizeText()
    {
        if (!salmonFile.IsDirectory)
            return ByteUtils.GetBytes(salmonFile.RealFile.Length, 2);
        else
        {
            int items = salmonFile.ListFiles().Length;
            return items + " item" + (items == 1 ? "" : "s");
        }
    }

    private string GetDateText()
    {
        return DateTimeOffset.FromUnixTimeMilliseconds(salmonFile.LastDateTimeModified).LocalDateTime.ToString(dateFormat);
    }

    public SalmonFile GetSalmonFile()
    {
        return salmonFile;
    }

    public SalmonFileViewModel[] ListFiles()
    {
        SalmonFile[] files = salmonFile.ListFiles();
        SalmonFileViewModel[] nfiles = new SalmonFileViewModel[files.Length];
        int count = 0;
        foreach (SalmonFile file in files)
            nfiles[count++] = new SalmonFileViewModel(file);
        return nfiles;
    }

    public long Size => salmonFile.RealFile.Length;

    public object Tag => salmonFile.Tag;

    public void CreateDirectory(string folderName, byte[] key, byte[] dirNameNonce)
    {
        salmonFile.CreateDirectory(folderName, key, dirNameNonce);
    }

    public long LastDateTimeModified => salmonFile.LastDateTimeModified;

    public void Delete()
    {
        salmonFile.Delete();
    }

    public void Rename(string newValue)
    {
        salmonFile.Rename(newValue);
        Name = salmonFile.BaseName;
    }

    override
    public String ToString()
    {
        return Name;
    }
}