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
using Salmon.Vault.Utils;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Linq;
using Salmon.Vault.Dialog;
using Salmon.Vault.Model;
using Salmon.Vault.Config;
using System.Windows.Input;
using System.Runtime.CompilerServices;
using Salmon.Vault.Model.Win;

namespace Salmon.Vault.ViewModel;

public class MainViewModel : INotifyPropertyChanged
{
    public delegate void OpenTextEditorView(SalmonFileViewModel item);
    public OpenTextEditorView OpenTextEditor;

    public delegate void OpenImageViewerView(SalmonFileViewModel item);
    public OpenImageViewerView OpenImageViewer;

    public delegate void OpenMediaPlayerView(SalmonFileViewModel item);
    public OpenMediaPlayerView OpenMediaPlayer;

    public delegate void OpenContentViewerWindow(SalmonFileViewModel item);
    public OpenContentViewerWindow OpenContentViewer;

    public delegate void OpenSettingsView();
    public OpenSettingsView OpenSettingsViewer;

    private ObservableCollection<SalmonFileViewModel> _fileItemList;
    public ObservableCollection<SalmonFileViewModel> FileItemList
    {
        get => _fileItemList;
        set
        {
            if (value != _fileItemList)
            {
                _fileItemList = value;
                if (manager.FileItemList != null)
                {
                    manager.FileItemList.Clear();
                    manager.FileItemList.AddRange(value.Select(x => x.GetSalmonFile()).ToList());
                }
                PropertyChanged(this, new PropertyChangedEventArgs("FileItemList"));
            }
        }
    }

    private SalmonFileViewModel _currentItem;
    public SalmonFileViewModel CurrentItem
    {
        get => _currentItem;
        set
        {
            if (value != _currentItem)
            {
                _currentItem = value;
                PropertyChanged(this, new PropertyChangedEventArgs("CurrentItem"));
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

    private bool _stopVisibility;
    public bool StopVisibility
    {
        get => _stopVisibility;
        set
        {
            if (value != _stopVisibility)
            {
                _stopVisibility = value;
                PropertyChanged(this, new PropertyChangedEventArgs("StopVisibility"));
            }
        }
    }

    private bool _progressVisibility;
    public bool ProgressVisibility
    {
        get => _progressVisibility;
        set
        {
            if (value != _progressVisibility)
            {
                _progressVisibility = value;
                PropertyChanged(this, new PropertyChangedEventArgs("ProgressVisibility"));
            }
        }
    }

    private string _path;
    public string Path
    {
        get => _path;
        set
        {
            if (value != _path)
            {
                _path = value;
                PropertyChanged(this, new PropertyChangedEventArgs("Path"));
            }
        }
    }

    private int _fileProgress;
    public int FileProgress
    {
        get => _fileProgress;
        set
        {
            if (value != _fileProgress)
            {
                _fileProgress = value;
                PropertyChanged(this, new PropertyChangedEventArgs("FileProgress"));
            }
        }
    }

    private int _filesProgress;
    public int FilesProgress
    {
        get => _filesProgress;
        set
        {
            if (value != _filesProgress)
            {
                _filesProgress = value;
                PropertyChanged(this, new PropertyChangedEventArgs("FilesProgress"));
            }
        }
    }

    public event PropertyChangedEventHandler PropertyChanged;
    private SalmonWinVaultManager manager;

    public MainViewModel()
    {
        manager = SalmonWinVaultManager.Instance;
        manager.OpenListItem = OpenListItem;
        manager.PropertyChanged += Manager_PropertyChanged;
        manager.UpdateListItem = UpdateListItem;
        manager.OnFileItemAdded = FileItemAdded;
    }

    private void FileItemAdded(int position, SalmonFile file)
    {
        WindowUtils.RunOnMainThread(() =>
        {
            FileItemList.Insert(position, new SalmonFileViewModel(file));
        });
    }

    private void Manager_PropertyChanged(object sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == "FileItemList")
        {
            UpdateFileViewModels();
        }
        else if (e.PropertyName == "CurrentItem")
        {
            CurrentItem = GetViewModel(manager.CurrentItem);
        }
        else if (e.PropertyName == "Status") Status = manager.Status;
        else if (e.PropertyName == "IsJobRunning")
        {
            WindowUtils.RunOnMainThread(() =>
            {
                if (manager.FileManagerMode != SalmonVaultManager.Mode.Search)
                {
                    ProgressVisibility = manager.IsJobRunning;
                    StopVisibility = manager.IsJobRunning;
                }
                if (!manager.IsJobRunning)
                    Status = "";
            }, manager.IsJobRunning ? 0 : 1000);
        }
        else if (e.PropertyName == "Path") Path = manager.Path;
        else if (e.PropertyName == "FileProgress") FileProgress = (int)(manager.FileProgress * 100);
        else if (e.PropertyName == "FilesProgress") FilesProgress = (int)(manager.FilesProgress * 100);
    }

    private void UpdateFileViewModels()
    {
        if (manager.FileItemList == null)
            FileItemList = new ObservableCollection<SalmonFileViewModel>();
        else
            FileItemList = new ObservableCollection<SalmonFileViewModel>(manager.FileItemList
                .Select(x => new SalmonFileViewModel(x)));
    }

    private SalmonFileViewModel GetViewModel(SalmonFile item)
    {
        foreach (SalmonFileViewModel vm in FileItemList)
        {
            if (vm.GetSalmonFile() == item)
                return vm;
        }
        return null;
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

    public void OnCommandClicked(ActionType actionType)
    {
        switch (actionType)
        {
            case ActionType.REFRESH:
                manager.Refresh();
                break;
            case ActionType.SETTINGS:
                OpenSettings();
                break;
            case ActionType.STOP:
                manager.StopOperation();
                break;
            case ActionType.IMPORT:
                SalmonDialogs.PromptImportFiles();
                break;
            case ActionType.EXPORT:
                manager.ExportSelectedFiles(false);
                break;
            case ActionType.EXPORT_AND_DELETE:
                manager.ExportSelectedFiles(true);
                break;
            case ActionType.SEARCH:
                SalmonDialogs.PromptSearch();
                break;
            case ActionType.NEW_FOLDER:
                SalmonDialogs.PromptNewFolder();
                break;
            case ActionType.COPY:
                manager.CopySelectedFiles();
                break;
            case ActionType.CUT:
                manager.CutSelectedFiles();
                break;
            case ActionType.DELETE:
                SalmonDialogs.PromptDelete();
                break;
            case ActionType.PASTE:
                manager.PasteSelected();
                break;
            case ActionType.ABOUT:
                SalmonDialogs.PromptAbout();
                break;
            case ActionType.EXIT:
                SalmonDialogs.PromptExit();
                break;
            case ActionType.OPEN_VAULT:
                SalmonDialogs.PromptOpenVault();
                break;
            case ActionType.CREATE_VAULT:
                SalmonDialogs.PromptCreateVault();
                break;
            case ActionType.CLOSE_VAULT:
                manager.CloseVault();
                break;
            case ActionType.IMPORT_AUTH:
                SalmonDialogs.PromptImportAuth();
                break;
            case ActionType.EXPORT_AUTH:
                SalmonDialogs.PromptExportAuth();
                break;
            case ActionType.REVOKE_AUTH:
                SalmonDialogs.PromptRevokeAuth();
                break;
            case ActionType.DISPLAY_AUTH_ID:
                SalmonDialogs.OnDisplayAuthID();
                break;
            case ActionType.BACK:
                manager.GoBack();
                break;
            default:
                break;
        }
    }

    public void StartTextEditor(SalmonFileViewModel item)
    {
        if (item == null)
            return;
        if (item.GetSalmonFile().Size > 1 * 1024 * 1024)
        {
            SalmonDialog.PromptDialog("Error", "File too large");
            return;
        }
        OpenTextEditor(item);
    }

    private void StartImageViewer(SalmonFileViewModel item)
    {
        OpenImageViewer(item);
    }

    private void StartContentViewer(SalmonFileViewModel item)
    {
        OpenContentViewer(item);
    }

    private void StartMediaPlayer(SalmonFileViewModel item)
    {
        OpenMediaPlayer(item);
    }

    public void OpenSettings()
    {
        OpenSettingsViewer();
    }

    private void UpdateListItem(SalmonFile file)
    {
        SalmonFileViewModel vm = GetViewModel(file);
        vm.Update();
    }

    private bool OpenListItem(SalmonFile file)
    {
        SalmonFileViewModel vm = GetViewModel(file);

        try
        {
            if (SalmonFileUtils.IsVideo(file.BaseName) || SalmonFileUtils.IsAudio(file.BaseName))
            {
                if (!SalmonConfig.USE_CONTENT_VIEWER && MediaPlayerViewModel.HasFFMPEG())
                    StartMediaPlayer(vm);
                else
                    StartContentViewer(vm);
                return true;
            }
            else if (SalmonFileUtils.IsImage(file.BaseName))
            {
                if (!SalmonConfig.USE_CONTENT_VIEWER)
                    StartImageViewer(vm);
                else
                    StartContentViewer(vm);
                return true;
            }
            else if (SalmonFileUtils.IsPdf(file.BaseName))
            {
                StartContentViewer(vm);
                return true;
            }
            else if (SalmonFileUtils.IsText(file.BaseName))
            {
                StartTextEditor(vm);
                return true;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            SalmonDialog.PromptDialog("Error", "Could not open: " + ex.Message);
        }
        return false;
    }

    public void OnShow()
    {
        WindowUtils.RunOnMainThread(() =>
        {
            manager.Initialize();
        }, 1000);
    }

    public void ExportSelectedFiles(bool deleteSource)
    {
        manager.ExportSelectedFiles(deleteSource);
    }

    internal void ShowProperties(SalmonFileViewModel viewModel)
    {
        if (viewModel != null)
            SalmonDialogs.ShowProperties(viewModel.GetSalmonFile());
    }

    internal void OnCopy()
    {
        manager.CopySelectedFiles();
    }

    internal void OnCut()
    {
        manager.CutSelectedFiles();
    }

    internal void Refresh()
    {
        manager.Refresh();
    }

    internal void RenameFile(SalmonFileViewModel selectedItem)
    {
        if (selectedItem != null)
            SalmonDialogs.PromptRenameFile(selectedItem.GetSalmonFile());
    }

    internal void PromptDelete()
    {
        SalmonDialogs.PromptDelete();
    }

    internal void OnBack()
    {
        manager.GoBack();
    }

    public void OpenItem(SalmonFileViewModel viewModel)
    {
        if (viewModel != null)
            manager.OpenItem(viewModel.GetSalmonFile());
    }

    public void Cancel()
    {
        manager.ClearCopiedFiles();
    }

    [MethodImpl(MethodImplOptions.Synchronized)]
    internal void OnSelectedItems(List<SalmonFileViewModel> selectedItems)
    {
        manager.SelectedFiles.Clear();
        foreach (SalmonFileViewModel item in selectedItems)
        {
            manager.SelectedFiles.Add(item.GetSalmonFile());
        }
    }
}