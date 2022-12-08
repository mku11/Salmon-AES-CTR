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
using Salmon.Prefs;
using System;
using System.ComponentModel;
using System.Windows.Data;
using System.Windows.Input;
using static Salmon.Settings.Settings;
namespace Salmon.ViewModel
{
    public class SettingsViewModel : INotifyPropertyChanged
    {
        private System.Windows.Window window;

        public event PropertyChangedEventHandler PropertyChanged;

        public CollectionView _aesTypes = new CollectionView(Enum.GetValues(typeof(Settings.Settings.AESType)));
        public CollectionView AesTypes 
        {
            get => _aesTypes;
            set
            {
                _aesTypes = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("AesTypes"));

            }
        }

        public AESType _aesTypeSelected = AESType.Default;
        public AESType AesTypeSelected
        {
            get => _aesTypeSelected;
            set
            {
                if (_aesTypeSelected != value)
                {
                    _aesTypeSelected = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("AesTypeSelected"));
                }
            }
        }

        public string _vaultLocation;
        public string VaultLocation
        {
            get => _vaultLocation;
            set
            {
                _vaultLocation = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("VaultLocation"));

            }
        }

        public bool _deleteSourceAfterImport;
        public bool DeleteSourceAfterImport
        {
            get => _deleteSourceAfterImport;
            set
            {
                _deleteSourceAfterImport = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("DeleteSourceAfterImport"));

            }
        }

        public bool _enableLogs;
        public bool EnableLogs
        {
            get => _enableLogs;
            set
            {
                _enableLogs = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("EnableLogs"));

            }
        }

        public bool _enableDetailedLogs;
        public bool EnableDetailedLogs
        {
            get => _enableDetailedLogs;
            set
            {
                _enableDetailedLogs = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("EnableDetailedLogs"));

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
                case ActionType.CHANGE_VAULT_LOCATION:
                    ChangeVaultLocation();
                    break;
                case ActionType.CHANGE_PASSWORD:
                    ChangePassword();
                    break;
                default:
                    break;
            }
        }

        public void SetWindow(System.Windows.Window window, System.Windows.Window owner)
        {
            this.window = window;
            this.window.Owner = owner;
            this.window.Loaded += (object sender, System.Windows.RoutedEventArgs e) =>
            {
                OnShow();
            };
        }
        private void OnShow()
        {
            AesTypes = new CollectionView(Enum.GetValues(typeof(AESType)));
            AesTypeSelected = GetInstance().aesType;

            DeleteSourceAfterImport = GetInstance().deleteAfterImport;
            EnableLogs = GetInstance().enableLog;
            EnableDetailedLogs = GetInstance().enableLogDetails;
            if (GetInstance().vaultLocation != null)
                VaultLocation = "Location: " + GetInstance().vaultLocation;
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
                VaultLocation = "Location: " + filePath;
                //TODO: notify the main screen to refresh
            }
        }

        private void ChangePassword()
        {
            WindowCommon.PromptSetPassword((string pass) =>
            {
                try
                {
                    SalmonDriveManager.GetDrive().GetVirtualRoot();
                    //TODO: notify main screen to refresh
                }
                catch (SalmonAuthException e)
                {
                    Console.Error.WriteLine(e);
                }
            });
        }

        private AESType GetAESType()
        {
            return AesTypeSelected;
        }

        public static void OpenSettings(System.Windows.Window owner)
        {
            View.Settings settings = new View.Settings();
            settings.SetWindow(owner);
            settings.ShowDialog();

            GetInstance().aesType = settings.viewModel.GetAESType();
            GetInstance().deleteAfterImport = settings.viewModel.DeleteSourceAfterImport;
            GetInstance().enableLog = settings.viewModel.EnableLogs;
            GetInstance().enableLogDetails = settings.viewModel.EnableDetailedLogs;
            Preferences.SavePrefs();
        }

    }
}