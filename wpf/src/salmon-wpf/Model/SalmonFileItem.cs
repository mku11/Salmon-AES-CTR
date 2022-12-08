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
using System;
using System.Windows.Media.Imaging;

namespace Salmon.Model
{
    public class SalmonFileItem : FileItem
    {
        private static string dateFormat = "dd/MM/yyyy hh:mm tt";
        private SalmonFile salmonFile;

        public SalmonFileItem(SalmonFile salmonFile)
        {
            this.salmonFile = salmonFile;
            Update();
        }

        public override void Update()
        {
            Name = salmonFile.GetBaseName();
            DateTime dt = DateTimeOffset.FromUnixTimeMilliseconds(salmonFile.GetLastDateTimeModified()).LocalDateTime;
            Date = dt.ToString(dateFormat);
            if (!salmonFile.IsDirectory())
                Size = Utils.getBytes(salmonFile.GetSize(), 2);
            else
            {
                int items = salmonFile.ListFiles().Length;
                Size = items + " item" + (items == 1 ? "" : "s");
            }
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(Name).ToLower();
            Type = ext;

            Path = salmonFile.GetPath();
            Image = GetImage();
        }

        public SalmonFile GetSalmonFile()
        {
            return salmonFile;
        }
        public void Rename(string newFilename, byte[] nonce)
        {
            salmonFile.Rename(newFilename, nonce);
            Name = salmonFile.GetBaseName();
        }

        public override string Name  {
            get => base.Name;
            set {
                if (!GetBaseName().Equals(value))
                    salmonFile.Rename(value, null);
                base.Name = value;
            }
        }

        public override bool IsDirectory()
        {
            return salmonFile.IsDirectory();
        }

        public override string GetBaseName()
        {
            return salmonFile.GetBaseName();
        }


        public BitmapImage GetImage()
        {
            string icon = salmonFile.IsFile() ? "/icons/file-small.png" : "/icons/folder-small.png";
            BitmapImage imageSource = new BitmapImage(new Uri(icon, UriKind.Relative));
            return imageSource;
        }

        public override FileItem[] ListFiles()
        {
            SalmonFile[] files = salmonFile.ListFiles();
            SalmonFileItem[] nfiles = new SalmonFileItem[files.Length];
            int count = 0;
            foreach (SalmonFile file in files)
                nfiles[count++] = new SalmonFileItem(file);
            return nfiles;
        }

        public override long GetSize()
        {
            return salmonFile.GetSize();
        }

        public override object GetTag()
        {
            return salmonFile.GetTag();
        }

        public override void CreateDirectory(string folderName, byte[] key, byte[] dirNameNonce)
        {
            salmonFile.CreateDirectory(folderName, key, dirNameNonce);
        }

        public override long GetLastDateTimeModified()
        {
            return salmonFile.GetLastDateTimeModified();
        }


        public override void Delete()
        {
            salmonFile.Delete();
        }

        public override void Rename(string newValue)
        {
            salmonFile.Rename(newValue);
            Name = salmonFile.GetBaseName();
        }

    }
}