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

using Mku.File;
using Mku.Salmon.IO;
using Mku.Salmon.Password;
using Mku.SalmonFS;
using Mku.Sequence;
using Mku.Utils;
using Salmon.Vault.Config;
using Salmon.Vault.Dialog;
using Salmon.Vault.Extensions;
using Salmon.Vault.Prefs;
using Salmon.Vault.Services;
using Salmon.Vault.Settings;
using Salmon.Vault.Utils;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;

namespace Salmon.Vault.Model;

public class SalmonVaultManager : INotifyPropertyChanged
{
    protected static readonly string SEQUENCER_DIR_NAME = ".salmon";
    protected static readonly string SERVICE_PIPE_NAME = "SalmonService";

    private static readonly int BUFFER_SIZE = 512 * 1024;
    private static readonly int THREADS = 3;

    public static readonly int REQUEST_OPEN_VAULT_DIR = 1000;
    public static readonly int REQUEST_CREATE_VAULT_DIR = 1001;
    public static readonly int REQUEST_IMPORT_FILES = 1002;
    public static readonly int REQUEST_EXPORT_DIR = 1003;
    public static readonly int REQUEST_IMPORT_AUTH_FILE = 1004;
    public static readonly int REQUEST_EXPORT_AUTH_FILE = 1005;

    public string SequencerDefaultDirPath { get; set; } = SalmonConfig.GetPrivateDir() + System.IO.Path.DirectorySeparatorChar + SEQUENCER_DIR_NAME;

    protected string SequencerFilepath => SequencerDefaultDirPath + System.IO.Path.DirectorySeparatorChar
        + SalmonConfig.FILE_SEQ_FILENAME;

    public delegate bool OpenListViewItem(SalmonFile item);
    public OpenListViewItem OpenListItem;

    public delegate void UpdateItem(SalmonFile file);
    public UpdateItem UpdateListItem;

    public delegate void OnFileItemAddedToList(int position, SalmonFile file);
    public OnFileItemAddedToList OnFileItemAdded;

    protected static SalmonVaultManager _instance;
    public static SalmonVaultManager Instance
    {
        [MethodImpl(MethodImplOptions.Synchronized)]
        get
        {
            if (_instance == null)
            {
                _instance = new SalmonVaultManager();
            }
            return _instance;
        }
    }

    private List<SalmonFile> _fileItemList;
    public List<SalmonFile> FileItemList
    {
        get => _fileItemList;
        private set
        {
            if (_fileItemList != value)
            {
                _fileItemList = value;
                PropertyChanged(this, new PropertyChangedEventArgs("FileItemList"));
            }
        }
    }

    private HashSet<SalmonFile> _SelectedFiles = new HashSet<SalmonFile>();
    public HashSet<SalmonFile> SelectedFiles
    {
        get => _SelectedFiles;
        private set
        {
            if (value != _SelectedFiles)
            {
                _SelectedFiles = value;
                PropertyChanged(this, new PropertyChangedEventArgs("SelectedFiles"));
            }
        }
    }

    private SalmonFile _currentItem;
    public SalmonFile CurrentItem
    {
        get => _currentItem;
        private set
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
        private set
        {
            if (value != _status)
            {
                _status = value;
                PropertyChanged(this, new PropertyChangedEventArgs("Status"));
            }
        }
    }

    private bool _isJobRunning;
    public bool IsJobRunning
    {
        get => _isJobRunning;
        private set
        {
            if (value != _isJobRunning)
            {
                _isJobRunning = value;
                PropertyChanged(this, new PropertyChangedEventArgs("IsJobRunning"));
            }
        }
    }

    private string _path;
    public string Path
    {
        get => _path;
        private set
        {
            if (value != _path)
            {
                _path = value;
                PropertyChanged(this, new PropertyChangedEventArgs("Path"));
            }
        }
    }

    private double _fileProgress;
    public double FileProgress
    {
        get => _fileProgress;
        private set
        {
            if (value != _fileProgress)
            {
                _fileProgress = value;
                PropertyChanged(this, new PropertyChangedEventArgs("FileProgress"));
            }
        }
    }

    private double _filesProgress;
    public double FilesProgress
    {
        get => _filesProgress;
        private set
        {
            if (value != _filesProgress)
            {
                _filesProgress = value;
                PropertyChanged(this, new PropertyChangedEventArgs("FilesProgress"));
            }
        }
    }

    public SalmonFile CurrDir;
    private SalmonFileCommander fileCommander;
    private SalmonFile[] copyFiles;
    private SalmonFile[] salmonFiles;
    private string searchTerm;
    public Mode FileManagerMode { get; private set; } = Mode.Browse;

    public event PropertyChangedEventHandler PropertyChanged;

    protected SalmonVaultManager()
    {
        SetupFileCommander();
        LoadSettings();
        SetupSalmonManager();
    }

    public void Initialize()
    {
        SetupDrive();
    }

    private void SetupDrive()
    {
        if (SalmonSettings.GetInstance().VaultLocation != null)
        {
            SetupRootDir();
        }
    }

    public bool OnOpenItem(int selectedItem)
    {
        try
        {
            SalmonFile selectedFile = FileItemList[selectedItem];
            return OpenItem(selectedFile);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
        }
        return false;
    }

    public void SetPathText(string value)
    {
        if (value.StartsWith("/"))
            value = value.Substring(1);
        Path = "fs://" + value;
    }

    public void StopOperation()
    {
        fileCommander.Cancel();
        FileManagerMode = Mode.Browse;
        SetTaskRunning(false);
    }

    public void CopySelectedFiles()
    {
        FileManagerMode = Mode.Copy;
        copyFiles = SelectedFiles.ToArray();
        SetTaskRunning(true, false);
        SetTaskMessage(copyFiles.Length + " Items selected for copy");
    }


    public void CutSelectedFiles()
    {
        FileManagerMode = Mode.Move;
        copyFiles = SelectedFiles.ToArray();
        SetTaskRunning(true, false);
        SetTaskMessage(copyFiles.Length + " Items selected for move");
    }

    private void SetupFileCommander()
    {
        fileCommander = new SalmonFileCommander(BUFFER_SIZE, BUFFER_SIZE, THREADS);
    }

    private void LoadSettings()
    {
        SalmonPreferences.LoadPrefs();
        SalmonStream.AesProviderType = (SalmonStream.ProviderType)Enum.Parse(typeof(SalmonStream.ProviderType), SalmonSettings.GetInstance().AesType.ToString());
        SalmonPassword.PbkdfImplType = (SalmonPassword.PbkdfType)Enum.Parse(typeof(SalmonPassword.PbkdfType), SalmonSettings.GetInstance().PbkdfImpl.ToString());
        SalmonPassword.PbkdfAlgorithm = (SalmonPassword.PbkdfAlgo)Enum.Parse(typeof(SalmonPassword.PbkdfAlgo), SalmonSettings.GetInstance().PbkdfAlgo.ToString());
        SalmonFileExporter.SetEnableLog(SalmonSettings.GetInstance().EnableLog);
        SalmonFileExporter.SetEnableLogDetails(SalmonSettings.GetInstance().EnableLogDetails);
        SalmonFileImporter.SetEnableLog(SalmonSettings.GetInstance().EnableLog);
        SalmonFileImporter.SetEnableLogDetails(SalmonSettings.GetInstance().EnableLogDetails);
    }

    protected void SetupRootDir()
    {
        string vaultLocation = SalmonSettings.GetInstance().VaultLocation;
        try
        {
            SalmonDriveManager.OpenDrive(vaultLocation);
            CurrDir = SalmonDriveManager.Drive.VirtualRoot;
            if (SalmonDriveManager.Drive.VirtualRoot == null)
            {
                SalmonDialogs.PromptSelectRoot();
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
            SalmonDialogs.PromptSelectRoot();
            return;
        }
        Refresh();
    }

    public void Refresh()
    {
        if (CheckFileSearcher())
            return;
        if (SalmonDriveManager.Drive == null)
            return;
        try
        {
            if (SalmonDriveManager.Drive.VirtualRoot == null || !SalmonDriveManager.Drive.VirtualRoot.Exists)
            {
                SalmonDialogs.PromptSelectRoot();
                return;
            }
            if (!SalmonDriveManager.Drive.IsAuthenticated)
            {
                CheckCredentials();
                return;
            }
            Task.Run(() =>
            {
                if (FileManagerMode != Mode.Search)
                    salmonFiles = CurrDir.ListFiles();
                PopulateFileList(SelectedFiles.FirstOrDefault((SalmonFile)null));
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
            SalmonDialogs.PromptAnotherProcessRunning();
            return true;
        }
        return false;
    }

    private void PopulateFileList(SalmonFile currentFile)
    {
        Task.Run(() =>
        {
            SelectedFiles.Clear();
            try
            {
                if (FileManagerMode == Mode.Search)
                    SetPathText(CurrDir.Path + "?search=" + searchTerm);
                else
                    SetPathText(CurrDir.Path);
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine(exception);
                SalmonDialog.PromptDialog("Error", exception.Message);
            }

            List<SalmonFile> list = new List<SalmonFile>();
            foreach (SalmonFile file in salmonFiles)
            {
                try
                {
                    list.Add(file);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            }
            FileItemList = list;
            SalmonFile currFile = FindCurrentItem(currentFile);
            CurrentItem = currFile;
        });
    }

    public virtual void SetupSalmonManager()
    {
        try
        {
            if (SalmonDriveManager.Sequencer != null)
                SalmonDriveManager.Sequencer.Close();
            // file sequencer for mobile is secure since it runs in sandbox
            SetupFileSequencer();
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            SalmonDialog.PromptDialog("Error", "Error during initializing: " + e.Message);
        }
    }


    private void SetupFileSequencer()
    {
        IRealFile dirFile = new DotNetFile(SequencerDefaultDirPath);
        if (!dirFile.Exists)
            dirFile.Mkdir();
        IRealFile seqFile = new DotNetFile(SequencerFilepath);
        SalmonFileSequencer sequencer = new SalmonFileSequencer(seqFile, new SalmonSequenceSerializer());
        SalmonDriveManager.Sequencer = sequencer;
    }

    public void PasteSelected()
    {
        CopySelectedFiles(FileManagerMode == Mode.Move);
    }

    public void SetTaskRunning(bool value, bool progress = true)
    {
        if (progress)
            IsJobRunning = value;
    }

    public void SetTaskMessage(string msg)
    {
        Status = msg ?? "";
    }

    public void OpenVault(string path)
    {
        if (path == null)
            return;

        try
        {
            CloseVault();
            SalmonDriveManager.OpenDrive(path);
            SalmonSettings.GetInstance().VaultLocation = path;
            SalmonPreferences.SavePrefs();
        }
        catch (Exception e)
        {
            SalmonDialog.PromptDialog("Error", "Could not open vault: " + e.Message);
        }
        Refresh();
    }

    public void DeleteSelectedFiles()
    {
        DeleteFiles(SelectedFiles.ToArray());
        ClearSelectedFiles();
    }

    private void CopySelectedFiles(bool move)
    {
        CopyFiles(copyFiles, CurrDir, move);
        ClearSelectedFiles();
    }

    private void DeleteFiles(SalmonFile[] files)
    {
        if (files == null)
            return;
        ThreadPool.QueueUserWorkItem(state =>
        {
            FileProgress = 0;
            FilesProgress = 0;

            SetTaskRunning(true);

            Exception exception = null;
            int[] processedFiles = new int[] { -1 };
            List<SalmonFile> failedFiles = new List<SalmonFile>();
            try
            {
                fileCommander.DeleteFiles(files,
                    (taskProgress) =>
                    {
                        if (processedFiles[0] < taskProgress.ProcessedFiles)
                        {
                            try
                            {
                                if (taskProgress.ProcessedBytes != taskProgress.TotalBytes)
                                {
                                    SetTaskMessage("Deleting: " + taskProgress.File.BaseName
                                        + " " + (taskProgress.ProcessedFiles + 1) + "/" + taskProgress.TotalFiles);
                                }
                            }
                            catch (Exception e)
                            {
                            }
                            processedFiles[0] = taskProgress.ProcessedFiles;
                        }
                        FileProgress = taskProgress.ProcessedBytes / (double)taskProgress.TotalBytes;
                        FilesProgress = taskProgress.ProcessedFiles / (double)taskProgress.TotalFiles;
                    }, (file, ex) =>
                    {
                        // TODO: report on failed files
                        failedFiles.Add(file);
                        exception = ex;
                    });
            }
            catch (Exception e)
            {
                if (!fileCommander.AreJobsStopped())
                {
                    Console.Error.WriteLine(e);
                    SalmonDialog.PromptDialog("Error", "Could not delete files: " + e.Message, "Ok");
                }
            }
            if (fileCommander.AreJobsStopped())
                SetTaskMessage("Delete Stopped");
            else if (failedFiles.Count > 0)
                SalmonDialog.PromptDialog("Delete", "Some files failed: " + exception.Message);
            else
                SetTaskMessage("Delete Complete");
            FileProgress = 1;
            FilesProgress = 1;
            Refresh();
            SetTaskRunning(false);
            copyFiles = null;
            FileManagerMode = Mode.Browse;
        });
    }

    private void CopyFiles(SalmonFile[] files, SalmonFile dir, bool move)
    {
        if (files == null)
            return;
        ThreadPool.QueueUserWorkItem(state =>
        {
            FileProgress = 0;
            FilesProgress = 0;

            SetTaskRunning(true);
            string action = move ? "Moving" : "Copying";
            Exception exception = null;
            int[] processedFiles = new int[] { -1 };
            List<SalmonFile> failedFiles = new List<SalmonFile>();
            try
            {
                fileCommander.CopyFiles(files, dir, move,
                    (taskProgress) =>
                    {
                        if (processedFiles[0] < taskProgress.ProcessedFiles)
                        {
                            try
                            {
                                SetTaskMessage(action + ": " + taskProgress.File.BaseName
                                    + " " + (taskProgress.ProcessedFiles + 1) + "/" + taskProgress.TotalFiles);
                            }
                            catch (Exception e)
                            {
                            }
                            processedFiles[0] = taskProgress.ProcessedFiles;
                        }
                        FileProgress = taskProgress.ProcessedBytes / (double)taskProgress.TotalBytes;
                        FilesProgress = taskProgress.ProcessedFiles / (double)taskProgress.TotalFiles;
                    }, SalmonFile.AutoRename, true, (file, ex) =>
                    {
                        HandleThrowException(ex);
                        // TODO: report on failed files
                        failedFiles.Add(file);
                        exception = ex;
                    });
            }
            catch (Exception e)
            {
                if (!fileCommander.AreJobsStopped())
                {
                    Console.Error.WriteLine(e);
                    SalmonDialog.PromptDialog("Error", "Could not copy files: " + e.Message, "Ok");
                }
            }
            if (fileCommander.AreJobsStopped())
                SetTaskMessage(action + " Stopped");
            else if (failedFiles.Count > 0)
                SalmonDialog.PromptDialog(action, "Some files failed: " + exception.Message);
            else
                SetTaskMessage(action + " Complete");
            FileProgress = 1;
            FilesProgress = 1;
            SetTaskRunning(false);
            Refresh();
            copyFiles = null;
            FileManagerMode = Mode.Browse;
        });
    }

    public void ExportSelectedFiles(bool deleteSource)
    {
        if (SalmonDriveManager.Drive.VirtualRoot == null || !SalmonDriveManager.Drive.IsAuthenticated)
            return;
        ExportFiles(SelectedFiles.ToArray(), (files) =>
        {
            Refresh();
        }, deleteSource);
        ClearSelectedFiles();
    }

    private void ClearSelectedFiles()
    {
        SelectedFiles = new HashSet<SalmonFile>();
    }

    public virtual bool HandleException(Exception exception)
    {
        return false;
    }

    public void CloseVault()
    {
        try
        {
            FileItemList = null;
            CurrDir = null;
            ClearCopiedFiles();
            SetPathText("");
            if (SalmonDriveManager.Drive != null)
                SalmonDriveManager.CloseDrive();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }

    private void CheckCredentials()
    {
        if (SalmonDriveManager.Drive.HasConfig())
        {
            SalmonDialogs.PromptPassword(() => OnPasswordSubmitted());
        }
    }

    public void OnPasswordSubmitted()
    {
        try
        {
            CurrDir = SalmonDriveManager.Drive.VirtualRoot;
        }
        catch (SalmonAuthException e)
        {
            Console.Error.WriteLine(e);
        }
        Refresh();
    }

    public bool OpenItem(SalmonFile selectedFile)
    {
        int position = FileItemList.IndexOf(selectedFile);
        if (position < 0)
            return true;
        if (selectedFile.IsDirectory)
        {
            ThreadPool.QueueUserWorkItem(state =>
            {
                if (CheckFileSearcher())
                    return;
                CurrDir = (selectedFile);
                salmonFiles = CurrDir.ListFiles();
                PopulateFileList(null);
            });
            return true;
        }
        string filename = selectedFile.BaseName;
        SalmonFile item = FileItemList[position];
        return OpenListItem(item);
    }

    public void GoBack()
    {
        if (FileManagerMode == Mode.Search && fileCommander.IsFileSearcherRunning())
        {
            fileCommander.StopFileSearch();
        }
        else if (FileManagerMode == Mode.Search)
        {
            Task.Run(() =>
            {
                FileManagerMode = Mode.Browse;
                salmonFiles = CurrDir.ListFiles();
                PopulateFileList(null);
            });
        }
        else if (CurrDir != null && CurrDir.Parent != null)
        {
            SalmonFile finalParent = CurrDir.Parent;
            Task.Run(() =>
            {
                if (CheckFileSearcher())
                    return;
                SalmonFile parentDir = CurrDir;
                CurrDir = finalParent;
                salmonFiles = CurrDir.ListFiles();
                PopulateFileList(parentDir);
            });
        }
        else
        {
            SalmonDialogs.PromptExit();
        }
    }

    private SalmonFile FindCurrentItem(SalmonFile currentFile)
    {
        if (currentFile == null)
            return null;
        foreach (SalmonFile file in FileItemList)
        {
            if (file.RealFile.Path.Equals(currentFile.RealFile.Path))
            {
                SelectedFiles.Clear();
                SelectedFiles.Add(file);
                return file;
            }
        }
        return null;
    }

    public void RenameFile(SalmonFile file, string newFilename)
    {
        fileCommander.RenameFile(file, newFilename);
    }

    public enum Mode
    {
        Browse, Search, Copy, Move
    }

    public void ExportFiles(SalmonFile[] items, Action<IRealFile[]> OnFinished, bool deleteSource)
    {
        ThreadPool.QueueUserWorkItem(state =>
        {
            FileProgress = 0;
            FilesProgress = 0;
            SetTaskRunning(true);

            Exception exception = null;
            int[] processedFiles = new int[] { -1 };
            IRealFile[] files = null;
            List<SalmonFile> failedFiles = new List<SalmonFile>();
            try
            {
                files = fileCommander.ExportFiles(items,
                    SalmonDriveManager.Drive.ExportDir,
                    deleteSource, true,
                    (taskProgress) =>
                    {
                        if (processedFiles[0] < taskProgress.ProcessedFiles)
                        {
                            try
                            {
                                SetTaskMessage("Exporting: " + taskProgress.File.BaseName
                                    + " " + (taskProgress.ProcessedFiles + 1) + "/" + taskProgress.TotalFiles);
                            }
                            catch (Exception e)
                            {
                            }
                            processedFiles[0] = taskProgress.ProcessedFiles;
                        }
                        FileProgress = taskProgress.ProcessedBytes / (double)taskProgress.TotalBytes;
                        FilesProgress = taskProgress.ProcessedFiles / (double)taskProgress.TotalFiles;
                    }, IRealFile.AutoRename, (file, ex) =>
                    {
                        //TODO: report failed files
                        failedFiles.Add(file);
                        exception = ex;
                    });
                if (OnFinished != null)
                    OnFinished(files);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                SalmonDialog.PromptDialog("Error", "Error while exporting files: " + e.Message);
            }
            if (fileCommander.AreJobsStopped())
                SetTaskMessage("Export Stopped");
            else if (failedFiles.Count > 0)
                SalmonDialog.PromptDialog("Export", "Some files failed: " + exception.Message);
            else if (files != null)
            {
                SetTaskMessage("Export Complete");
                SalmonDialog.PromptDialog("Export", "Files Exported To: "
                    + SalmonDriveManager.Drive.ExportDir.AbsolutePath);
            }
            FileProgress = 1;
            FilesProgress = 1;

            SetTaskRunning(false);
        });
    }

    public void ImportFiles(IRealFile[] fileNames, SalmonFile importDir, bool deleteSource,
                            Action<SalmonFile[]> OnFinished)
    {
        ThreadPool.QueueUserWorkItem(state =>
        {
            FileProgress = 0;
            FilesProgress = 0;

            SetTaskRunning(true);

            Exception exception = null;
            int[] processedFiles = new int[] { -1 };
            SalmonFile[] files = null;
            List<IRealFile> failedFiles = new List<IRealFile>();
            try
            {
                files = fileCommander.ImportFiles(fileNames, importDir,
                    deleteSource, true,
                    (taskProgress) =>
                    {
                        if (processedFiles[0] < taskProgress.ProcessedFiles)
                        {
                            try
                            {
                                SetTaskMessage("Importing: " + taskProgress.File.BaseName
                                    + " " + (taskProgress.ProcessedFiles + 1) + "/" + taskProgress.TotalFiles);
                            }
                            catch (Exception e)
                            {
                            }
                            processedFiles[0] = taskProgress.ProcessedFiles;
                        }
                        FileProgress = taskProgress.ProcessedBytes / (double)taskProgress.TotalBytes;
                        FilesProgress = taskProgress.ProcessedFiles / (double)taskProgress.TotalFiles;
                    }, IRealFile.AutoRename, (file, ex) =>
                    {
                        HandleThrowException(ex);
                        // TODO: report on failed files
                        failedFiles.Add(file);
                        exception = ex;
                    });
                OnFinished(files);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine(e);
                if (!HandleException(e))
                {
                    SalmonDialog.PromptDialog("Error", "Error while importing files: " + e.Message);
                }
            }
            if (fileCommander.AreJobsStopped())
                SetTaskMessage("Import Stopped");
            else if (failedFiles.Count > 0)
                SalmonDialog.PromptDialog("Import", "Some files failed: " + exception.Message);
            else if (files != null)
                SetTaskMessage("Import Complete");
            FileProgress = 1;
            FilesProgress = 1;
            SetTaskRunning(false);
        });
    }

    public virtual void HandleThrowException(Exception ex) { }

    public void Search(string value, bool any)
    {
        searchTerm = value;
        if (CheckFileSearcher())
            return;
        ThreadPool.QueueUserWorkItem(state =>
        {
            FileManagerMode = Mode.Search;
            FileProgress = 0;
            FilesProgress = 0;
            if (CurrDir.Path != null)
                SetPathText(CurrDir.Path + "?search=" + value);
            salmonFiles = new SalmonFile[] { };
            PopulateFileList(null);
            SetTaskRunning(true);
            Status = "Searching";
            salmonFiles = fileCommander.Search(CurrDir, value, any, (SalmonFile salmonFile) =>
            {
                int position = 0;
                // TODO: this slows down the search
                foreach (SalmonFile file in FileItemList)
                {
                    if (salmonFile.Tag != null &&
                        (file.Tag == null || (int)salmonFile.Tag > (int)file.Tag))
                        break;
                    position++;
                }
                try
                {
                    FileItemList.Insert(position, salmonFile);
                    OnFileItemAdded(position, salmonFile);
                }
                catch (Exception e)
                {
                    Console.Error.WriteLine(e);
                }
            }, null);
            if (!fileCommander.IsFileSearcherStopped())
                Status = "Search Complete";
            else
                Status = "Search Stopped";
            SetTaskRunning(false);
        });
    }

    public void CreateVault(string dirPath, string password)
    {
        SalmonDriveManager.CreateDrive(dirPath, password);
        SalmonSettings.GetInstance().VaultLocation = dirPath;
        SalmonPreferences.SavePrefs();
        CurrDir = SalmonDriveManager.Drive.VirtualRoot;
        Refresh();
    }

    public void ClearCopiedFiles()
    {
        copyFiles = null;
        FileManagerMode = Mode.Browse;
        SetTaskRunning(false, false);
        SetTaskMessage("");
    }

    public string GetFileProperties(SalmonFile item)
    {
        return "Name: " + item.BaseName + "\n" +
                "Path: " + item.Path + "\n" +
                (!item.IsDirectory ? ("Size: " + ByteUtils.GetBytes(item.Size, 2)
                        + " (" + item.Size + " bytes)") : "Items: " + item.ListFiles().Length) + "\n" +
                "Encrypted Name: " + item.RealFile.BaseName + "\n" +
                "Encrypted Path: " + item.RealFile.AbsolutePath + "\n" +
                (!item.IsDirectory ? "Encrypted Size: " + ByteUtils.GetBytes(item.RealFile.Length, 2)
                        + " (" + item.RealFile.Length + " bytes)" : "") + "\n";
    }
}