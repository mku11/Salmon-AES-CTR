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
    public enum ActionType
    {
        BACK, REFRESH, IMPORT, VIEW, VIEW_AS_TEXT, VIEW_EXTERNAL, EDIT, SHARE, SAVE,
        EXPORT, DELETE, RENAME, UP, DOWN,
        MULTI_SELECT, SINGLE_SELECT, SELECT_ALL, UNSELECT_ALL,
        EXPORT_SELECTED, DELETE_SELECTED, COPY, CUT, PASTE,
        NEW_FOLDER, SEARCH, STOP, PLAY, SORT,
        OPEN_VAULT, CREATE_VAULT, CLOSE_VAULT, CHANGE_PASSWORD,
        IMPORT_AUTH, EXPORT_AUTH, REVOKE_AUTH, DISPLAY_AUTH_ID,
        PROPERTIES, SETTINGS, ABOUT, EXIT
    }

    public class MainViewModel : INotifyPropertyChanged
    {
        private static readonly string SEQUENCER_DIR_NAME = ".salmon";
        public static readonly string SEQUENCER_DIR_PATH = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData) + System.IO.Path.DirectorySeparatorChar + SEQUENCER_DIR_NAME;
        public static readonly string SEQUENCER_FILE_PATH = SEQUENCER_DIR_PATH + System.IO.Path.DirectorySeparatorChar + "config.xml";
        private static readonly string SERVICE_PIPE_NAME = "SalmonService";

        private static readonly int BUFFER_SIZE = 512 * 1024;
        private static readonly int THREADS = 4;

        public SalmonFile RootDir;
        public SalmonFile CurrDir;

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
            SetupSalmonManager();
        }

        private void PromptDelete()
        {
            WindowCommon.PromptDialog("Delete", "Delete " + GetSelectedFileItems().Length + " item(s)?",
                "Ok", () =>
                {
                    DeleteSelectedFiles();
                }, "Cancel", null);
        }

        private void OnOpenItem(int selectedItem)
        {
            try
            {
                OpenItem(selectedItem);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
            }
        }

        private void OnShow()
        {

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
            SetupSalmonManager();
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
                e.Handled = true;
                OnOpenItem(dataGrid.SelectedIndex);
            };
            dataGrid.PreviewKeyDown += (object sender, System.Windows.Input.KeyEventArgs e) =>
            {
                if (e.Key == Key.Enter)
                {
                    e.Handled = true;
                    OnOpenItem(dataGrid.SelectedIndex);
                }
            };
        }


        public void SetWindow(System.Windows.Window window)
        {
            this.window = window;
            window.Loaded += (object sender, RoutedEventArgs e) =>
            {
                OnShow();
            };
            window.KeyDown += (object sender, System.Windows.Input.KeyEventArgs e) =>
            {
                if (e.Key == Key.Down && !dataGrid.IsFocused)
                {
                    e.Handled = true;
                    dataGrid.SelectedIndex = 0;
                    DataGridRow row = (DataGridRow)dataGrid.ItemContainerGenerator.ContainerFromIndex(0);
                    row.MoveFocus(new TraversalRequest(FocusNavigationDirection.Next));
                }
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
                case ActionType.OPEN_VAULT:
                    OnOpenVault();
                    break;
                case ActionType.CREATE_VAULT:
                    OnCreateVault();
                    break;
                case ActionType.CLOSE_VAULT:
                    OnCloseVault();
                    break;
                case ActionType.IMPORT_AUTH:
                    OnImportAuth();
                    break;
                case ActionType.EXPORT_AUTH:
                    OnExportAuth();
                    break;
                case ActionType.REVOKE_AUTH:
                    OnRevokeAuth();
                    break;
                case ActionType.DISPLAY_AUTH_ID:
                    OnDisplayAuthID();
                    break;
                case ActionType.BACK:
                    OnBack();
                    break;
                default:
                    break;
            }
        }


        private void OnOpenVault()
        {
            string selectedDirectory = MainViewModel.SelectDirectory(window, "Select directory of existing vault");
            if (selectedDirectory == null)
                return;
            string filePath = selectedDirectory;
            if (filePath != null)
            {

                try
                {
                    WindowCommon.OpenVault(filePath);
                }
                catch (Exception e)
                {
                    new SalmonDialog("Could not open vault: " + e.Message).Show();
                }
                Refresh();
            }
        }


        public void OnCreateVault()
        {
            string selectedDirectory = SelectDirectory(window, "Select directory for your new vault");
            if (selectedDirectory == null)
                return;
            WindowCommon.PromptSetPassword((string pass) =>
            {
                try
                {
                    WindowCommon.CreateVault(selectedDirectory, pass);
                    RootDir = SalmonDriveManager.GetDrive().GetVirtualRoot();
                    CurrDir = RootDir;
                    Refresh();
                }
                catch (Exception e)
                {
                    new SalmonDialog("Could not create vault: " + e.Message).Show();
                }
            });
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
            fileCommander.SetImporterProgressListener((IRealFile file, long bytesRead, long totalBytesRead, string message) =>
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    Status = message;
                    FileProgress = (int)(bytesRead * 100 / totalBytesRead);
                });
            });

            fileCommander.SetExporterProgressListener((SalmonFile file, long bytesWritten, long totalBytesWritten, string message) =>
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
                SalmonDriveManager.OpenDrive(vaultLocation);
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
                    WindowUtils.RunOnMainThread(() =>
                    {
                        SalmonFile selectedFile = null;
                        if (dataGrid.SelectedItem != null)
                            selectedFile = (dataGrid.SelectedItem as SalmonFileItem).GetSalmonFile();
                        DisplayFiles(selectedFile);
                    });

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
            if (fileCommander.IsFileSearcherRunning())
            {
                new SalmonDialog("Another process is running").Show();
                return true;
            }
            return false;
        }

        private void DisplayFiles(SalmonFile selectedFile)
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
                SelectItem(selectedFile);
            });
        }

        private void SetupSalmonManager()
        {
            try
            {
                SalmonDriveManager.SetVirtualDriveClass(typeof(DotNetDrive));
                if (SalmonDriveManager.GetSequencer() != null)
                    SalmonDriveManager.GetSequencer().Dispose();
                if (Settings.Settings.GetInstance().authType == Settings.Settings.AuthType.User)
                {
                    SetupFileSequencer();
                }
                else if (Settings.Settings.GetInstance().authType == Settings.Settings.AuthType.Service)
                {
                    SetupClientSequencer();
                }
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                new SalmonDialog("Error during initializing: " + e.Message).Show();
            }
        }

        private void SetupClientSequencer()
        {
            try
            {
                WinClientSequencer sequencer = new WinClientSequencer(SERVICE_PIPE_NAME);
                SalmonDriveManager.SetSequencer(sequencer);
            }
            catch (Exception ex)
            {
                WindowUtils.RunOnMainThread(() =>
                {
                    new SalmonDialog("Error during service lookup. Make sure the Salmon Service is installed and running:\n" + ex.Message).Show();
                });
            }
        }

        private void SetupFileSequencer()
        {
            IRealFile dirFile = new DotNetFile(SEQUENCER_DIR_PATH);
            if (!dirFile.Exists())
                dirFile.Mkdir();
            IRealFile seqFile = new DotNetFile(SEQUENCER_FILE_PATH);
            FileSequencer sequencer = new FileSequencer(seqFile, new SalmonSequenceParser());
            SalmonDriveManager.SetSequencer(sequencer);
        }

        private void PasteSelected()
        {
            CopySelectedFiles(mode == Mode.Move);
        }

        private void PromptSearch()
        {
            WindowCommon.PromptEdit("Search", "Keywords", "", "Match any term", false,
                (string value, bool isChecked) =>
                {
                    Search(value, isChecked);
                }, false);
        }

        public void ShowTaskRunning(bool value, bool progress = true)
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
            WindowCommon.PromptEdit("New Folder", "Folder Name", "New Folder", null, false,
                (string folderName, bool isChecked) =>
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
                }, false);
        }

        private void DeleteSelectedFiles()
        {
            DeleteFiles(GetSelectedFiles());
        }

        private void CopySelectedFiles(bool move)
        {
            CopyFiles(copyFiles, CurrDir, move);
        }

        private void DeleteFiles(SalmonFile[] files)
        {
            ThreadPool.QueueUserWorkItem(state =>
            {
                ShowTaskRunning(true);
                try
                {
                    fileCommander.DeleteFiles(files, null);
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
                    fileCommander.CopyFiles(files, dir, move, (fileInfo) =>
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

            MenuItem item = new MenuItem() { Header = "View" };
            item.Click += (object sender, RoutedEventArgs e) => OnOpenItem(dataGrid.SelectedIndex);
            contextMenu.Items.Add(item);

            item = new MenuItem() { Header = "View As Text" };
            item.Click += (object sender, RoutedEventArgs e) => StartTextEditor(dataGrid.SelectedIndex);
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

            item = new MenuItem() { Header = "Export", InputGestureText = "Ctrl-E" };
            item.Click += (object sender, RoutedEventArgs e) => ExportSelectedFiles();
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
                    WindowCommon.PromptEdit("Rename", "New filename", ifile.GetBaseName(), null, true,
                        (string newFilename, bool isChecked) =>
                        {
                            try
                            {
                                ifile.Rename(newFilename);
                            }
                            catch (Exception exception)
                            {
                                Console.Error.WriteLine(exception);
                            }
                        }, false);
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

        public void OnCloseVault()
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
        }

        protected bool OpenItem(int position)
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
                    DisplayFiles(null);
                });
                return true;
            }
            string filename = selectedFile.GetBaseName();
            try
            {
                if (FileUtils.IsVideo(filename))
                {
                    StartMediaPlayer(position, MediaType.VIDEO);
                    return true;
                }
                else if (FileUtils.IsAudio(filename))
                {
                    StartMediaPlayer(position, MediaType.AUDIO);
                    return true;
                }
                else if (FileUtils.IsImage(filename))
                {
                    StartImageViewer(position);
                    return true;
                }
                else if (FileUtils.IsText(filename))
                {
                    StartTextEditor(position);
                    return true;
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                new SalmonDialog("Could not open: " + ex.Message).Show();
            }
            return false;
        }

        private void StartTextEditor(int position)
        {
            FileItem item = FileItemList[position];
            if (item.GetSize() > 1 * 1024 * 1024)
            {
                new SalmonDialog("File too large").Show();
                return;
            }
            TextEditorViewModel.OpenTextEditor(item, window);
        }

        private void StartImageViewer(int position)
        {
            FileItem item = FileItemList[position];
            ImageViewerViewModel.OpenImageViewer(item, window);


        }

        private void StartMediaPlayer(int position, MediaType type)
        {
            FileItem item = FileItemList[position];
            MediaPlayerViewModel.OpenMediaPlayer(item, window, type);
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
                string selectedDirectory = SelectDirectory(window, "Select vault directory");
                if (selectedDirectory == null)
                    return;
                OnCloseVault();
                WindowCommon.OpenVault(selectedDirectory);
                SetupRootDir();
            }, "Cancel", null);
        }

        public static string SelectDirectory(System.Windows.Window window, string title)
        {
            FolderBrowserDialog directoryChooser = new FolderBrowserDialog();
            directoryChooser.Description = title;
            if (directoryChooser.ShowDialog() == DialogResult.OK)
                return directoryChooser.SelectedPath;
            return null;
        }

        public void OnBack()
        {
            SalmonFile parent = CurrDir.GetParent();
            if (mode == Mode.Search && fileCommander.IsFileSearcherRunning())
            {
                fileCommander.StopFileSearch();
            }
            else if (mode == Mode.Search)
            {
                ThreadPool.QueueUserWorkItem(state =>
                {
                    mode = Mode.Browse;
                    salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(null);
                });
            }
            else if (parent != null)
            {
                ThreadPool.QueueUserWorkItem(state =>
                {
                    if (CheckFileSearcher())
                        return;
                    SalmonFile parentDir = CurrDir;
                    CurrDir = parent;
                    salmonFiles = CurrDir.ListFiles();
                    DisplayFiles(parentDir);
                });
            }
        }

        private void SelectItem(SalmonFile selectedFile)
        {
            int index = 0;
            foreach (SalmonFileItem file in FileItemList)
            {
                if (selectedFile != null && file.GetSalmonFile().GetPath().Equals(selectedFile.GetPath()))
                {
                    dataGrid.SelectedIndex = index;
                    dataGrid.ScrollIntoView(dataGrid.SelectedIndex);
                    WindowUtils.RunOnMainThread(() =>
                    {
                        DataGridRow row = (DataGridRow)dataGrid.ItemContainerGenerator.ContainerFromIndex(dataGrid.SelectedIndex);
                        row.MoveFocus(new TraversalRequest(FocusNavigationDirection.Next));
                    }, 1);
                    break;
                }
                index++;
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
                foreach (SalmonFile file in items)
                {
                    if (file.IsDirectory())
                    {
                        WindowUtils.RunOnMainThread(() =>
                        {
                            new SalmonDialog("Cannot Export Directories select files only").Show();
                        });
                        return;
                    }
                }
                StopVisibility = true;
                FileProgress = 0;
                FilesProgress = 0;
                ShowTaskRunning(true);
                bool success = false;
                try
                {
                    success = fileCommander.ExportFiles(items, (progress) =>
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
                    WindowUtils.RunOnMainThread(() => new SalmonDialog("Error while exporting files: " + e.Message).Show());
                }
                if (fileCommander.IsStopped())
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
                    success = fileCommander.ImportFiles(fileNames, importDir, deleteSource, (progress) =>
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
                    WindowUtils.RunOnMainThread(() => new SalmonDialog("Error while importing files: " + e.Message).Show());
                }
                if (fileCommander.IsStopped())
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
                    DisplayFiles(null);
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
                    if (!fileCommander.IsFileSearcherStopped())
                        Status = "Search: " + value;
                    else
                        Status = "Search Stopped: " + value;
                });
            });
        }


        public void OnImportAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                new SalmonDialog("No Drive Loaded").Show();
                return;
            }
            OpenFileDialog fileChooser = new OpenFileDialog();
            fileChooser.Title = "Import Auth File";
            string filename = SalmonDriveManager.GetAppDriveConfigFilename();
            string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename);
            fileChooser.Filter = "Salmon Auth Files(*." + ext + ")| *." + ext;
            if (fileChooser.ShowDialog() == DialogResult.Cancel)
                return;
            if (fileChooser.FileName == null)
                return;
            try
            {
                SalmonDriveManager.ImportAuthFile(fileChooser.FileName);
                new SalmonDialog("Device is now Authorized").Show();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine(ex);
                new SalmonDialog("Could Not Import Auth: " + ex.Message).Show();
            }
        }

        public void OnExportAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                new SalmonDialog("No Drive Loaded").Show();
                return;
            }
            WindowCommon.PromptEdit("Export Auth File",
                    "Enter the Auth ID for the device you want to authorize",
                    "", null, false,
                    (targetAuthID, option) =>
                    {
                        SaveFileDialog fileChooser = new SaveFileDialog();
                        fileChooser.Title = "Export Auth File";
                        string filename = SalmonDriveManager.GetAppDriveConfigFilename();
                        string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(filename);
                        fileChooser.Filter = "Salmon Auth Files(*." + ext + ")| *." + ext;
                        fileChooser.FileName = filename;
                        if (fileChooser.ShowDialog() == DialogResult.Cancel)
                            return;
                        if (fileChooser.FileName == null)
                            return;
                        try
                        {
                            DotNetFile authFile = new DotNetFile(fileChooser.FileName);
                            SalmonDriveManager.ExportAuthFile(targetAuthID, authFile.GetParent().GetPath(), authFile.GetBaseName());
                            new SalmonDialog("Auth File Exported").Show();
                        }
                        catch (Exception ex)
                        {
                            Console.Error.WriteLine(ex);
                            new SalmonDialog("Could Not Export Auth: " + ex.Message).Show();
                        }
                    }, false);
        }


        public void OnRevokeAuth()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                new SalmonDialog("No Drive Loaded").Show();
                return;
            }
            WindowCommon.PromptDialog("Revoke Auth", "Revoke Auth for this drive? You will still be able to decrypt and view your files but you won't be able to import any more files in this drive.",
                    "Ok", () =>
                    {
                        try
                        {
                            SalmonDriveManager.RevokeSequences();
                            new SalmonDialog("Revoke Auth Successful").Show();
                        }
                        catch (Exception e)
                        {
                            Console.Error.WriteLine(e);
                            new SalmonDialog("Could Not Revoke Auth: " + e.Message).Show();
                        }
                    }, "Cancel", null);
        }


        public void OnDisplayAuthID()
        {
            if (SalmonDriveManager.GetDrive() == null)
            {
                new SalmonDialog("No Drive Loaded").Show();
                return;
            }
            string driveID = SalmonDriveManager.GetAuthID();
            WindowCommon.PromptEdit("Salmon Auth App ID",
                    "", driveID, null, false,
                    null, true);

        }
    }
}