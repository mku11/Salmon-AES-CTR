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
using Salmon.Prefs;
using System;
using System.ComponentModel;
using System.Windows.Data;
using System.Windows.Input;
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

        public Settings.Settings.AESType _aesTypeSelected = Settings.Settings.AESType.Default;
        public Settings.Settings.AESType AesTypeSelected
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

        public CollectionView _authTypes = new CollectionView(Enum.GetValues(typeof(Settings.Settings.AuthType)));
        public CollectionView AuthTypes
        {
            get => _authTypes;
            set
            {
                _authTypes = value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("AuthTypes"));

            }
        }
        public Settings.Settings.AuthType _authTypeSelected = Settings.Settings.AuthType.Service;
        public Settings.Settings.AuthType AuthTypeSelected
        {
            get => _authTypeSelected;
            set
            {
                if (_authTypeSelected != value)
                {
                    _authTypeSelected = value;
                    if (PropertyChanged != null)
                        PropertyChanged(this, new PropertyChangedEventArgs("AuthTypeSelected"));
                }
                if (initialized && _authTypeSelected == Settings.Settings.AuthType.User)
                {
                    new SalmonDialog("WARNING! User based authorization is less secure. "
                            + "\n" + "Make sure the auth config file is not accessible by other users:"
                            + "\n" + MainViewModel.SEQUENCER_FILE_PATH
                            + "\n" + "If you need more security install the Salmon Service"
                    ).Show();
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
        private bool initialized;

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
                case ActionType.OPEN_VAULT:
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
            AesTypes = new CollectionView(Enum.GetValues(typeof(Settings.Settings.AESType)));
            AesTypeSelected = Settings.Settings.GetInstance().aesType;

            AuthTypes = new CollectionView(Enum.GetValues(typeof(Settings.Settings.AuthType)));
            AuthTypeSelected = Settings.Settings.GetInstance().authType;

            DeleteSourceAfterImport = Settings.Settings.GetInstance().deleteAfterImport;
            EnableLogs = Settings.Settings.GetInstance().enableLog;
            EnableDetailedLogs = Settings.Settings.GetInstance().enableLogDetails;
            if (Settings.Settings.GetInstance().vaultLocation != null)
                VaultLocation = "Location: " + Settings.Settings.GetInstance().vaultLocation;
            initialized = true;
        }

        private void ChangeVaultLocation()
        {
            string selectedDirectory = MainViewModel.SelectDirectory(window, "Select vault directory");
            if (selectedDirectory == null)
                return;
            string filePath = selectedDirectory;
            if (filePath != null)
            {
                WindowCommon.OpenVault(filePath);
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
                    SalmonDriveManager.GetDrive().SetPassword(pass);
                    //TODO: notify main screen to refresh
                    new SalmonDialog("Password Changed").Show();
                }
                catch (SalmonAuthException e)
                {
                    Console.Error.WriteLine(e);
                    new SalmonDialog("Could not change password: " + e).Show();
                    throw e;
                }
            });
        }

        private Settings.Settings.AESType GetAESType()
        {
            return AesTypeSelected;
        }

        private Settings.Settings.AuthType GetAuthType()
        {
            return AuthTypeSelected;
        }


        public static void OpenSettings(System.Windows.Window owner)
        {
            View.Settings settings = new View.Settings();
            settings.SetWindow(owner);
            settings.ShowDialog();

            Settings.Settings.GetInstance().aesType = settings.viewModel.GetAESType();
            Settings.Settings.GetInstance().authType = settings.viewModel.GetAuthType();
            Settings.Settings.GetInstance().deleteAfterImport = settings.viewModel.DeleteSourceAfterImport;
            Settings.Settings.GetInstance().enableLog = settings.viewModel.EnableLogs;
            Settings.Settings.GetInstance().enableLogDetails = settings.viewModel.EnableDetailedLogs;
            Preferences.SavePrefs();
        }

    }
}