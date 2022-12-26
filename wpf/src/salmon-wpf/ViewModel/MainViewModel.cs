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
using Salmon.Alert;
using Salmon.FS;
using Salmon.Model;
using Salmon.Net.FS;
using Salmon.Prefs;
using Salmon.Streams;
using Salmon.Window;
using SalmonFS.Media;
using SalmonWPF;
using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Forms;
using System.Windows.Input;
using System.Windows.Media;
using OpenFileDialog = System.Windows.Forms.OpenFileDialog;

namespace Salmon.ViewModel
{
    public class MainViewModel : INotifyPropertyChanged
    {
        private static readonly int BUFFER_SIZE = 512 * 1024;
        private static readonly int THREADS = 4;

        public static SalmonFile RootDir;
        public static SalmonFile CurrDir;

        private FileCommander fileCommander;
        private SalmonFile[] copyFiles;

        public DataGrid dataGrid;
        private System.Windows.Window window;
        private string searchTerm;

        public ObservableCollection<FileItem> _fileItemList = new ObservableCollection<FileItem>();
        public ObservableCollection<FileItem> FileItemList
        {
            get => _fileItemList;
            set
            {
                _fileItemList = value;
            }
        }

        private string _status = "";
        public string Status
        {
            get => _status;
            set
            {
                string newValue = value;
                if (newValue != _status)
                {
                    _status = newValue;
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
                bool newValue = value;
                if (newValue != _stopVisibility)
                {
                    _stopVisibility = newValue;
                    PropertyChanged(this, new PropertyChangedEventArgs("StopVisibility"));
                }
            }
        }

        private bool _progressVisibility = false;
        public bool ProgressVisibility
        {
            get => _progressVisibility;
            set
            {
                bool newValue = value;
                if (newValue != _progressVisibility)
                {
                    _progressVisibility = newValue;
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
                string newValue = value;
                if (newValue != _path)
                {
                    _path = newValue;
                    PropertyChanged(this, new PropertyChangedEventArgs("Path"));
                }
            }
        }

        public int _fileProgress;
        public int FileProgress
        {
            get => _fileProgress;
            set
            {
                int newValue = value;
                if (newValue != _fileProgress)
                {
                    _fileProgress = newValue;
                    PropertyChanged(this, new PropertyChangedEventArgs("FileProgress"));
                }
            }
        }

        public int _filesProgress;
        public int FilesProgress
        {
            get => _filesProgress;
            set
            {
                int newValue = value;
                if (newValue != _filesProgress)
                {
                    _filesProgress = newValue;
                    PropertyChanged(this, new PropertyChangedEventArgs("FilesProgress"));
                }
            }
        }

        public enum MediaType
        {
            AUDIO, VIDEO
        }

        private Mode mode = Mode.Browse;

        private SalmonFile[] salmonFiles;

        public MainViewModel()
        {
            SetupFileCommander();
            SetupListeners();
            LoadSettings();
            SetupVirtualDrive();
        }

        private void PromptDelete()
        {
            WindowCommon.PromptDialog("Delete", "Delete " + GetSelectedFileItems().Length + " item(s)?",
                "Ok", () =>
                {
                    DeleteSelectedFiles();
                }, "Cancel", null);
        }

        private void OnDoubleClick(int selectedItem)
        {
            try
            {
                Selected(selectedItem);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private void OnShow()
        {
            WindowUtils.RunOnMainThread(() =>
            {
                SetupRootDir();
            }, 1000);
        }


        public void SetPath(string value)
        {
            if (value.StartsWith("/"))
                value = value.Substring(1);
            Path = "salmonfs://" + value;
        }


        public void OnSettings()
        {
            SettingsViewModel.OpenSettings(window);
            LoadSettings();
        }

        public SalmonFile[] GetSalmonFiles()
        {
            return salmonFiles;
        }

        public void SetDataGrid(DataGrid dataGrid)
        {
            this.dataGrid = dataGrid;
            dataGrid.MouseRightButtonUp += (object sender, MouseButtonEventArgs e) =>
            {
                SalmonFileItem[] selectedFileItems = GetSelectedFileItems();
                OpenContextMenu(selectedFileItems);
            };
            dataGrid.MouseDoubleClick += (object sender, MouseButtonEventArgs e) =>
            {
                OnDoubleClick(dataGrid.SelectedIndex);
            };

        }

        public void SetWindow(System.Windows.Window window)
        {
            this.window = window;
            window.Loaded += (object sender, RoutedEventArgs e) =>
            {
                OnShow();
            };
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

        public event PropertyChangedEventHandler PropertyChanged;

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
                case ActionType.REFRESH:
                    Refresh();
                    break;
                case ActionType.UP:
                    OnUp();
                    break;
                case ActionType.SETTINGS:
                    OnSettings();
                    break;
                case ActionType.STOP:
                    StopOperation();
                    break;
                case ActionType.IMPORT:
                    PromptImportFiles();
                    break;
                case ActionType.EXPORT:
                    ExportSelectedFiles();
                    break;
                case ActionType.SEARCH:
                    PromptSearch();
                    break;
                case ActionType.NEW_FOLDER:
                    PromptNewFolder();
                    break;
                case ActionType.NEW_FILE:
                    PromptNewFile();
                    break;
                case ActionType.COPY:
                    OnCopy();
                    break;
                case ActionType.CUT:
                    OnCut();
                    break;
                case ActionType.DELETE:
                    PromptDelete();
                    break;
                case ActionType.PASTE:
                    PasteSelected();
                    break;
                case ActionType.ABOUT:
                    OnAbout();
                    break;
                case ActionType.EXIT:
                    window.Close();
                    break;
                case ActionType.CHANGE_VAULT_LOCATION:
                    ChangeVaultLocation();
                    break;
                default:
                    break;
            }
        }

        private void ChangeVaultLocation()
        {
            string selectedDirectory = MainViewModel.SelectVault(window);
            if (selectedDirectory == null)
                return;
            string filePath = selectedDirectory;
            if (filePath != null)
            {
                Preferences.SetVaultFolder(filePath);
                Refresh();
            }
        }
        public void StopOperation()
        {
            fileCommander.CancelJobs();
            mode = Mode.Browse;
            WindowUtils.RunOnMainThread(() => ShowTaskRunning(false), 1000);
        }

        public void OnCopy()
        {
            mode = Mode.Copy;
            copyFiles = GetSelectedFiles();
            ShowTaskRunning(true, false);
            ShowTaskMessage(copyFiles.Length + " Items selected for copy");
        }


        public void OnCut()
        {
            mode = Mode.Move;
            copyFiles = GetSelectedFiles();
            ShowTaskRunning(true, false);
            ShowTaskMessage(copyFiles.Length + " Items selected for move");
        }

        private SalmonFile[] GetSelectedFiles()
        {
            IList selectedItems = dataGrid.SelectedItems;
            SalmonFile[] files = new SalmonFile[selectedItems.Count];
            int index = 0;
            foreach (FileItem item in selectedItems)
                files[index++] = ((SalmonFileItem)item).GetSalmonFile();
            return files;
        }

        private SalmonFileItem[] GetSelectedFileItems()
        {
            IList selectedItems = dataGrid.SelectedItems;
            SalmonFileItem[] files = new SalmonFileItem[selectedItems.Count];
            int index = 0;
            foreach (FileItem item in selectedItems)
                files[index++] = (SalmonFileItem)item;
            return files;
        }

        public void OnExit()
        {
            WindowCommon.PromptDialog("Exit", "Exit App", "Ok", () =>
            {
                window.Close();
            }, "Cancel", null);
        }

        private void SetupFileCommander()
        {
            fileCommander = new FileCommander(BUFFER_SIZE, THREADS);
        }

        private void LoadSettings()
        {
            Preferences.LoadPrefs(Settings.Settings.GetInstance());
            SalmonStream.SetProviderType((SalmonStream.ProviderType)Enum.Parse(typeof(SalmonStream.ProviderType), Settings.Settings.GetInstance().aesType.ToString()));
            SalmonFileExporter.SetEnableLog(Settings.Settings.GetInstance().enableLog);
            SalmonFileExporter.SetEnableLogDetails(Settings.Settings.GetInstance().enableLogDetails);
            SalmonFileImporter.SetEnableLog(Settings.Settings.GetInstance().enableLog);
            SalmonFileImporter.SetEnableLogDetails(Settings.Settings.GetInstance().enableLogDetails);
            SalmonStream.SetEnableLogDetails(Settings.Settings.GetInstance().enableLogDetails);
            SalmonMediaDataSource.SetEnableLog(Settings.Settings.GetInstance().enableLogDetails);
        }

        private void SetupListeners()
        {
            fileCommander.SetFileImporterOnTaskProgressChanged((object sender, long bytesRead, long totalBytesRead, string message) =>
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    Status = message;
                    FileProgress = (int)(bytesRead * 100 / totalBytesRead);
                });
            });

            fileCommander.SetFileExporterOnTaskProgressChanged((object sender, long bytesWritten, long totalBytesWritten, string message) =>
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    Status = message;
                    FileProgress = (int)(bytesWritten * 100 / totalBytesWritten);
                });
            });
        }

        protected void SetupRootDir()
        {
            string vaultLocation = Salmon.Settings.Settings.GetInstance().vaultLocation;
            try
            {
                SalmonDriveManager.SetDriveLocation(vaultLocation);
                SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                CurrDir = RootDir;
                if (RootDir == null)
                {
                    PromptSelectRoot();
                    return;
                }
            }
            catch (SalmonAuthException)
            {
                CheckCredentials();
                return;
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                PromptSelectRoot();
                return;
            }
            Refresh();
        }

        public void Refresh()
        {
            if (CheckFileSearcher())
                return;
            if (SalmonDriveManager.GetDrive() == null)
                return;
            try
            {
                if (SalmonDriveManager.GetDrive().GetVirtualRoot() == null || !SalmonDriveManager.GetDrive().GetVirtualRoot().Exists())
                {
                    PromptSelectRoot();
                    return;
                }
                if (!SalmonDriveManager.GetDrive().IsAuthenticated())
                {
                    CheckCredentials();
                    return;
                }
                ThreadPool.QueueUserWorkItem(state =>
                {
                    if (mode != Mode.Search)
                        salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(false);
                });
            }
            catch (SalmonAuthException e)
            {
                Console.Error.WriteLine(e);
                CheckCredentials();
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private bool CheckFileSearcher()
        {
            if (fileCommander.isFileSearcherRunning())
            {
                new SalmonDialog("Another process is running").Show();
                return true;
            }
            return false;
        }

        private void DisplayFiles(bool reset)
        {
            WindowUtils.RunOnMainThread(() =>
            {
                try
                {
                    if (mode == Mode.Search)
                        SetPath(CurrDir.GetPath() + "?search=" + searchTerm);
                    else
                        SetPath(CurrDir.GetPath());
                }
                catch (Exception exception)
                {
                    Console.Error.WriteLine(exception);
                }
                FileItemList.Clear();
                foreach (SalmonFile file in salmonFiles)
                {
                    try
                    {
                        SalmonFileItem item = new SalmonFileItem(file);
                        FileItemList.Add(item);
                    }
                    catch (Exception e)
                    {
                        Console.Error.WriteLine(e);
                    }
                }
                if (mode != Mode.Search)
                    SortFiles();
            });
        }

        private void SetupVirtualDrive()
        {
            try
            {
                SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private void PasteSelected()
        {
            CopySelectedFiles(mode == Mode.Move);
        }

        private void PromptSearch()
        {
            WindowCommon.PromptEdit("Search", "Keywords", "", "Match any term", (string value, bool isChecked) =>
            {
                Search(value, isChecked);
            });
        }

        public void ShowTaskRunning(bool value)
        {
            ShowTaskRunning(value, true);
        }

        public void ShowTaskRunning(bool value, bool progress)
        {
            WindowUtils.RunOnMainThread(() =>
            {
                if (progress)
                    ProgressVisibility = value;
                if (!value)
                    Status = "";
            });
        }

        public void ShowTaskMessage(string msg)
        {
            WindowUtils.RunOnMainThread(() =>
            {
                Status = msg ?? "";
            });
        }

        private void SortFiles()
        {
            CollectionViewSource.GetDefaultView(dataGrid.ItemsSource).SortDescriptions.Clear();
            List<FileItem> sortableList = new List<FileItem>(FileItemList);
            sortableList.Sort((FileItem fileItem1, FileItem fileItem2) =>
            {
                if (fileItem1.IsDirectory() && !fileItem2.IsDirectory())
                    return -1;
                else if (!fileItem1.IsDirectory() && fileItem2.IsDirectory())
                    return 1;
                else
                    return fileItem1.GetBaseName().CompareTo(fileItem2.GetBaseName());
            });

            FileItemList.Clear();
            foreach (FileItem fileItem in sortableList)
                FileItemList.Add(fileItem);
        }

        private void OnAbout()
        {
            WindowCommon.PromptDialog("About", Config.APP_NAME + " v" + Config.VERSION + "\n" +
                Config.ABOUT_TEXT, "Get Source Code", () =>
            {
                URLUtils.GoToUrl(Config.SourceCodeURL);
            }, "Cancel", null);
        }

        private void PromptImportFiles()
        {
            OpenFileDialog fileChooser = new OpenFileDialog();
            fileChooser.Multiselect = true;
            if (fileChooser.ShowDialog() == DialogResult.Cancel)
                return;
            string[] files = fileChooser.FileNames;
            DotNetFile[] filesToImport = new DotNetFile[files.Length];
            int count = 0;
            foreach (string file in files)
                filesToImport[count++] = new DotNetFile(file);
            ImportFiles(filesToImport, CurrDir, Settings.Settings.GetInstance().deleteAfterImport, (SalmonFile[] importedFiles) =>
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    Refresh();
                });
            });
        }

        private void PromptNewFolder()
        {
            WindowCommon.PromptEdit("New Folder", "Folder Name", "New Folder", null, (string folderName, bool isChecked) =>
            {
                try
                {
                    CurrDir.CreateDirectory(folderName, null, null);
                    Refresh();
                }
                catch (Exception exception)
                {
                    Console.Error.WriteLine(exception);
                    new SalmonDialog("Could Not Create Folder: " + exception.Message).Show();
                    Refresh();
                }
            }
        );
        }

        private void PromptNewFile()
        {
            WindowCommon.PromptEdit("New File", "File Name", "New File", null, (string fileName, bool isChecked) =>
            {
                try
                {
                    CurrDir.CreateFile(fileName);
                    Refresh();
                }
                catch (Exception exception)
                {
                    Console.Error.WriteLine(exception);
                    new SalmonDialog("Could Not Create File: " + exception.Message).Show();
                    Refresh();
                }
            }
        );
        }

        private void DeleteSelectedFiles()
        {
            DeleteFiles(GetSelectedFileItems());
        }

        private void CopySelectedFiles(bool move)
        {
            CopyFiles(copyFiles, CurrDir, move);
        }

        private void DeleteFiles(SalmonFileItem[] files)
        {
            ThreadPool.QueueUserWorkItem(state =>
            {
                ShowTaskRunning(true);
                try
                {
                    fileCommander.DoDeleteFiles((file) =>
                    {
                        WindowUtils.RunOnMainThread(() =>
                        {
                            FileItemList.Remove(file);
                            SortFiles();
                        });
                    }, files);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                WindowUtils.RunOnMainThread(() =>
                {
                    FileProgress = 100;
                    FilesProgress = 100;
                });
                WindowUtils.RunOnMainThread(() => ShowTaskRunning(false), 1000);
            });
        }

        private void CopyFiles(SalmonFile[] files, SalmonFile dir, bool move)
        {
            ThreadPool.QueueUserWorkItem(state =>
            {
                ShowTaskRunning(true);
                try
                {
                    fileCommander.DoCopyFiles(files, dir, move, (fileInfo) =>
                    {
                        WindowUtils.RunOnMainThread(() =>
                        {
                            FileProgress = (int)fileInfo.fileProgress;
                            FilesProgress = (int)(fileInfo.processedFiles / (double)fileInfo.totalFiles);
                            string action = move ? " Moving: " : " Copying: ";
                            ShowTaskMessage((fileInfo.processedFiles + 1) + "/" + fileInfo.totalFiles + action + fileInfo.filename);
                        });
                    });
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                WindowUtils.RunOnMainThread(() =>
                {
                    FileProgress = 100;
                    FilesProgress = 100;
                    Refresh();
                });
                WindowUtils.RunOnMainThread(() => ShowTaskRunning(false), 1000);
                copyFiles = null;
                mode = Mode.Browse;
            });
        }

        private void ExportSelectedFiles()
        {
            if (RootDir == null || !SalmonDriveManager.GetDrive().IsAuthenticated())
                return;
            ExportFiles(GetSelectedFiles(), (files) =>
            {
                Refresh();
            });
        }
        private void OpenContextMenu(FileItem[] items)
        {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem item = new MenuItem() { Header = "Export", InputGestureText = "Ctrl-E"};
            item.Click += (object sender, RoutedEventArgs e) => ExportSelectedFiles();
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "Copy", InputGestureText = "Ctrl-C" };
            item.Click += (object sender, RoutedEventArgs e) => OnCopy();
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "Cut", InputGestureText = "Ctrl-X" };
            item.Click += (object sender, RoutedEventArgs e) => OnCut();
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "Delete" };
            item.Click += (object sender, RoutedEventArgs e) => PromptDelete();
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "Rename" };
            item.Click += (object sender, RoutedEventArgs e) => RenameFile(items[0]);
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "Properties" };
            item.Click += (object sender, RoutedEventArgs e) => ShowProperties(((SalmonFileItem)items[0]).GetSalmonFile());
            contextMenu.Items.Add(item);

            contextMenu.PlacementTarget = dataGrid;
            contextMenu.Opened += (object sender, RoutedEventArgs e) =>
            {
                WindowUtils.SetChildBackground(contextMenu, (obj) =>
                {
                    if (obj.GetType() == typeof(Border))
                        ((Border)obj).Background = (Brush)App.Current.Resources["SalmonBackground"];
                });
            };
            contextMenu.IsOpen = true;
        }
        private void RenameFile(FileItem ifile)
        {
            WindowUtils.RunOnMainThread(() =>
            {
                try
                {
                    WindowCommon.PromptEdit("Rename", "New filename",
                            ifile.GetBaseName(), null, (string newFilename, bool isChecked) =>
                            {
                                try
                                {
                                    ifile.Rename(newFilename);
                                }
                                catch (Exception exception)
                                {
                                    Console.Error.WriteLine(exception);
                                }
                            });
                }
                catch (Exception exception)
                {
                    Console.Error.WriteLine(exception);
                }
            });
        }

        private void ShowProperties(SalmonFile ifile)
        {
            try
            {
                WindowCommon.PromptDialog("Properties",
                        "Name: " + ifile.GetBaseName() + "\n" +
                                "Path: " + ifile.GetPath() + "\n" +
                                (!ifile.IsDirectory() ? ("Size: " + Utils.getBytes(ifile.GetSize(), 2) + " (" + ifile.GetSize() + " bytes)") : "Items: " + ifile.ListFiles().Length) + "\n" +
                                "EncryptedName: " + ifile.GetRealFile().GetBaseName() + "\n" +
                                "EncryptedPath: " + ifile.GetRealFile().GetAbsolutePath() + "\n" +
                                (!ifile.IsDirectory() ? "EncryptedSize: " + Utils.getBytes(ifile.GetRealFile().Length(), 2) + " (" + ifile.GetRealFile().Length() + " bytes)" : "") + "\n"
                        , "Ok", null,
                        null, null
                );
            }
            catch (Exception exception)
            {
                Status = "Could not get file properties";
                Console.Error.WriteLine(exception);
            }
        }

        public void Clear()
        {
            Logout();
            RootDir = null;
            CurrDir = null;
            WindowUtils.RunOnMainThread(() =>
            {
                FileItemList.Clear();
            });
        }

        private void CheckCredentials()
        {
            if (SalmonDriveManager.GetDrive().HasConfig())
            {
                WindowCommon.PromptPassword(() =>
                {
                    try
                    {
                        RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                        CurrDir = RootDir;
                    }
                    catch (SalmonAuthException e)
                    {
                        Console.Error.WriteLine(e);
                    }
                    Refresh();
                });
            }
            else
            {
                WindowCommon.PromptSetPassword((string pass) =>
                        {
                            try
                            {
                                RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                                CurrDir = RootDir;
                            }
                            catch (SalmonAuthException e)
                            {
                                Console.Error.WriteLine(e);
                            }
                            Refresh();
                            if (FileItemList.Count == 0)
                                PromptImportFiles();
                        });
            }
        }

        protected bool Selected(int position)
        {
            FileItem selectedFile = FileItemList[position];
            if (selectedFile.IsDirectory())
            {
                ThreadPool.QueueUserWorkItem(state =>
                {
                    if (CheckFileSearcher())
                        return;
                    CurrDir = ((SalmonFileItem)selectedFile).GetSalmonFile();
                    salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(true);
                });
                return true;
            }
            string filename = selectedFile.GetBaseName();


            if (FileUtils.isVideo(filename))
            {
                StartMediaPlayer(position, MediaType.VIDEO);
                return true;
            }
            else if (FileUtils.isAudio(filename))
            {
                StartMediaPlayer(position, MediaType.AUDIO);
                return true;
            }
            else if (FileUtils.isImage(filename))
            {
                StartImageViewer(position);
                return true;
            }
            else if (FileUtils.isText(filename))
            {
                StartTextEditor(position);
                return true;
            }
            return false;
        }

        private void StartTextEditor(int position)
        {
            FileItem item = FileItemList[position];
            try
            {
                TextEditorViewModel.OpenTextEditor(item, window);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }


        private void StartImageViewer(int position)
        {
            FileItem item = FileItemList[position];
            try
            {
                ImageViewerViewModel.OpenImageViewer(item, window);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private void StartMediaPlayer(int position, MediaType type)
        {
            FileItem item = FileItemList[position];
            try
            {
                MediaPlayerViewModel.OpenMediaPlayer(item, window, type);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private void Logout()
        {
            try
            {
                SalmonDriveManager.GetDrive().Authenticate(null);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
            }
        }

        private void PromptSelectRoot()
        {
            WindowCommon.PromptDialog("Vault", "Choose a location for your vault", "Ok", () =>
            {
                string selectedDirectory = SelectVault(window);
                if (selectedDirectory == null)
                    return;
                if (selectedDirectory != null)
                {
                    Clear();
                    Preferences.SetVaultFolder(selectedDirectory);
                    SetupRootDir();
                }
            }, "Cancel", null);
        }

        public static string SelectVault(System.Windows.Window window)
        {
            FolderBrowserDialog directoryChooser = new FolderBrowserDialog();
            if (directoryChooser.ShowDialog() == DialogResult.OK)
                return directoryChooser.SelectedPath;
            return null;
        }

        public void OnUp()
        {
            SalmonFile parent = CurrDir.GetParent();
            if (mode == Mode.Search && fileCommander.isFileSearcherRunning())
            {
                fileCommander.StopFileSearch();
            }
            else if (mode == Mode.Search)
            {
                ThreadPool.QueueUserWorkItem(state =>
                {
                    mode = Mode.Browse;
                    salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(true);
                });
            }
            else if (parent != null)
            {
                ThreadPool.QueueUserWorkItem(state =>
                {
                    if (CheckFileSearcher())
                        return;
                    CurrDir = parent;
                    salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(true);
                });
            }
        }

        enum Mode
        {
            Browse, Search, Copy, Move
        }

        public void ExportFiles(SalmonFile[] items, Action<IRealFile[]> OnFinished)
        {
            ThreadPool.QueueUserWorkItem(state =>
            {
                StopVisibility = true;
                FileProgress = 0;
                FilesProgress = 0;
                ShowTaskRunning(true);
                bool success = false;
                try
                {
                    success = fileCommander.DoExportFiles(items, (progress) =>
                    {
                        WindowUtils.RunOnMainThread(() =>
                        {
                            FilesProgress = progress;
                        });
                    }, OnFinished);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                if (fileCommander.isStopped())
                    ShowTaskMessage("Export Stopped");
                else if (!success)
                    ShowTaskMessage("Export Failed");
                else if (success)
                    ShowTaskMessage("Export Complete");
                StopVisibility = false;
                WindowUtils.RunOnMainThread(() =>
                {
                    FileProgress = 100;
                    FilesProgress = 100;
                    WindowCommon.PromptDialog("Export", "Files Exported To: " + SalmonDriveManager.GetDrive().GetExportDir().GetAbsolutePath(),
                            "Ok", null, null, null);
                });
                WindowUtils.RunOnMainThread(() =>
                {
                    ShowTaskRunning(false);
                }, 1000);
            });
        }

        public void ImportFiles(IRealFile[] fileNames, SalmonFile importDir, bool deleteSource,
                                Action<SalmonFile[]> OnFinished)
        {

            ThreadPool.QueueUserWorkItem(state =>
            {
                StopVisibility = true;
                FileProgress = 0;
                FilesProgress = 0;
                ShowTaskRunning(true);
                bool success = false;
                try
                {
                    success = fileCommander.DoImportFiles(fileNames, importDir, deleteSource, (progress) =>
                    {
                        WindowUtils.RunOnMainThread(() =>
                        {
                            FilesProgress = progress;
                        });
                    }, OnFinished);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
                if (fileCommander.isStopped())
                    ShowTaskMessage("Import Stopped");
                else if (!success)
                    ShowTaskMessage("Import Failed");
                else if (success)
                    ShowTaskMessage("Import Complete");
                StopVisibility = false;
                WindowUtils.RunOnMainThread(() =>
                {
                    FileProgress = 100;
                    FilesProgress = 100;
                });
                WindowUtils.RunOnMainThread(() =>
                {
                    ShowTaskRunning(false);
                }, 1000);
            });
        }


        //TODO: refactor to a class and update ui frequently with progress
        private void Search(string value, bool any)
        {
            searchTerm = value;
            if (CheckFileSearcher())
                return;
            ThreadPool.QueueUserWorkItem(state =>
            {
                mode = Mode.Search;
                WindowUtils.RunOnMainThread(() =>
                {
                    FileProgress = 0;
                    FilesProgress = 0;

                    try
                    {
                        SetPath(CurrDir.GetPath() + "?search=" + value);
                    }
                    catch (Exception exception)
                    {
                        Console.Error.WriteLine(exception);
                    }
                    salmonFiles = new SalmonFile[] { };
                    DisplayFiles(true);
                });
                salmonFiles = fileCommander.Search(CurrDir, value, any, (SalmonFile salmonFile) =>
                {
                    WindowUtils.RunOnMainThread(() =>
                    {
                        int position = 0;
                        foreach (FileItem file in FileItemList)
                        {
                            if ((int)salmonFile.GetTag() > (int)file.GetTag())
                            {
                                break;
                            }
                            else
                                position++;
                        }
                        SalmonFileItem item = null;
                        try
                        {
                            item = new SalmonFileItem(salmonFile);
                            FileItemList.Insert(position, item);
                        }
                        catch (Exception e)
                        {
                            Console.Error.WriteLine(e);
                        }
                    });
                });
                WindowUtils.RunOnMainThread(() =>
                {
                    if (!fileCommander.isFileSearcherStopped())
                        Status = "Search: " + value;
                    else
                        Status = "Search Stopped: " + value;
                });
            });
        }

    }
}