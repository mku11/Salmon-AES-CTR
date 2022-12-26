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
using System.Runtime.CompilerServices;
using System.Text;
using System.Windows.Input;

namespace Salmon.ViewModel
{
    public class TextEditorViewModel : INotifyPropertyChanged
    {
        public event PropertyChangedEventHandler PropertyChanged;
        private System.Windows.Window window;
        private FileItem item;

        private string _contentArea;
        public string ContentArea
        {
            get => _contentArea;
            set
            {
                _contentArea = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("ContentArea"));
            }
        }

        public TextEditorViewModel()
        {

        }

        public class RelayCommand<T> : ICommand
        {
            readonly Action<T> command;


            public RelayCommand(Action<T> command)
            {
                this.command = command;
            }

            public event EventHandler CanExecuteChanged;

            public bool CanExecute(object parameter)
            {
                return true;
            }

            public void Execute(object parameter)
            {
                if (command != null)
                {
                    command((T)parameter);
                }
            }

        }


        private ICommand _clickCommand;
        public ICommand ClickCommand
        {
            get
            {
                if (_clickCommand == null)
                {
                    _clickCommand = new RelayCommand<ActionType>(OnCommandClicked);
                }
                return _clickCommand;
            }
        }
        private void OnCommandClicked(ActionType actionType)
        {
            switch (actionType)
            {
                case ActionType.SAVE:
                    OnSave();
                    break;
                case ActionType.EXIT:
                    OnClose();
                    break;
            }
        }

        public void SetWindow(System.Windows.Window window, System.Windows.Window owner)
        {
            this.window = window;
            this.window.Owner = owner;
        }

        public static void OpenTextEditor(FileItem file, System.Windows.Window owner)
        {
            TextEditor textEditor = new TextEditor();
            textEditor.SetWindow(owner);
            textEditor.Load(file);
            textEditor.Show();
        }

        public void Load(FileItem fileItem)
        {
            item = fileItem;
            try
            {
                string content = GetTextContent(item);
                ContentArea = content;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }

        }

        private string GetTextContent(FileItem item)
        {
            SalmonFile file = ((SalmonFileItem)item).GetSalmonFile();
            SalmonStream stream = file.GetInputStream();
            MemoryStream ms = new MemoryStream();
            stream.CopyTo(ms);
            stream.Close();
            byte[] bytes = ms.ToArray();
            string content = UTF8Encoding.UTF8.GetString(bytes);
            return content;
        }

        public void OnClose()
        {
            window.Close();
        }

        public void OnSave()
        {
            try
            {
                SaveContent();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        [MethodImpl(MethodImplOptions.Synchronized)]
        private void SaveContent()
        {
            byte[] contents = UTF8Encoding.UTF8.GetBytes(ContentArea);
            MemoryStream ins = new MemoryStream(contents);
            SalmonFile file = ((SalmonFileItem)item).GetSalmonFile();
            SalmonFile dir = file.GetParent();
            file.Delete();
            file = dir.CreateFile(file.GetBaseName());
            SalmonStream stream = file.GetOutputStream();
            ins.CopyTo(stream);
            stream.Flush();
            stream.Close();
            ins.Close();
            ((SalmonFileItem)item).SetSalmonFile(file);
        }
    }
}