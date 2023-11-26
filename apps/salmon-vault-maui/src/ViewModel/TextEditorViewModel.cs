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
using Mku.Salmon.IO;
using Mku.SalmonFS;
using Salmon.Vault.Dialog;
using Salmon.Vault.Model;
using Salmon.Vault.Utils;
using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Text;
using System.Windows.Input;

namespace Salmon.Vault.ViewModel;

public class TextEditorViewModel : INotifyPropertyChanged
{
    public event PropertyChangedEventHandler PropertyChanged;
    private SalmonFileViewModel item;
	private SalmonTextEditor editor;

    public delegate void SelectAndScrollToView(int start, int length);
    public SelectAndScrollToView SelectAndScrollTo;

    public delegate void PromptSearchDialog();
    public PromptSearchDialog PromptSearch;

    private int currentCaretPosition = 0;

    private string _contentArea = "";
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

    private string _searchText;
    public string SearchText
    {
        get => _searchText;
        set
        {
            if (_searchText != value)
            {
                _searchText = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("SearchText"));
            }
        }
    }

    private string _status = "";
    public string Status
    {
        get => _status;
        set
        {
            if (value != _status)
            {
                _status = value;
                PropertyChanged(this, new PropertyChangedEventArgs("Status"));
            }
        }
    }

    public TextEditorViewModel()
    {
        editor = new SalmonTextEditor();
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

    public void OnCommandClicked(ActionType actionType)
    {
        switch (actionType)
        {
            case ActionType.SAVE:
                OnSave();
                break;
            case ActionType.SEARCH:
                PromptSearch();
                break;
        }
    }

    private void OnSave()
    {
        try
        {

			SalmonFile oldFile = item.GetSalmonFile();
            SalmonFile targetFile = editor.OnSave(item.GetSalmonFile(), ContentArea);
            int index = SalmonVaultManager.Instance.FileItemList.IndexOf(oldFile);
            if (index >= 0)
            {
                SalmonVaultManager.Instance.FileItemList.Remove(oldFile);
                SalmonVaultManager.Instance.FileItemList.Insert(index, targetFile);
            }
            item.SetSalmonFile(targetFile);
            ShowTaskMessage("File saved");
            WindowUtils.RunOnMainThread(() =>
            {
                ShowTaskMessage("");
            }, 2000);
            
        }
        catch (Exception ignored)
        {
        }
    }

    public void Load(SalmonFileViewModel fileItem)
    {
        item = fileItem;
        try
        {
            string content = editor.GetTextContent(fileItem.GetSalmonFile());
            ContentArea = content;
            ShowTaskMessage("File loaded");
            WindowUtils.RunOnMainThread(() =>
            {
                ShowTaskMessage("");
            }, 2000);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }

    }

    public void ShowTaskMessage(string msg)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            Status = msg ?? "";
        });
    }

    public void OnSearch(string text, int caretPosition)
    {
        int searchStart;
        if (currentCaretPosition == -1)
        {
            searchStart = 0;
        }
        else if (currentCaretPosition != caretPosition)
        {
            searchStart = caretPosition;
        }
        else
        {
            searchStart = currentCaretPosition;
        }
        int start = ContentArea.ToLower().IndexOf(text.ToLower(), searchStart);
        if (start >= 0)
        {
            SelectAndScrollTo(start, text.Length);
            currentCaretPosition = start + 1;
        }
        else
        {
            currentCaretPosition = -1;
        }
    }
}