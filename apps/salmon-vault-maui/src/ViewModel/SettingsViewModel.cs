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
using Salmon.Vault.Model;
using Salmon.Vault.Settings;
using System;
using System.Collections.Generic;
using System.ComponentModel;

namespace Salmon.Vault.ViewModel;

public class SettingsViewModel : INotifyPropertyChanged
{
    public MainViewModel MainViewModel { get; set; }
    public event PropertyChangedEventHandler PropertyChanged;
    private bool initialized;

    public List<SalmonSettings.AESType> _aesTypes = new List<SalmonSettings
        .AESType>((IEnumerable<SalmonSettings.AESType>)Enum
        .GetValues(typeof(SalmonSettings.AESType)));
    public List<SalmonSettings.AESType> AesTypes
    {
        get => _aesTypes;
        set
        {
            _aesTypes = value;
            if (PropertyChanged != null)
                PropertyChanged(this, new PropertyChangedEventArgs("AesTypes"));

        }
    }

    public SalmonSettings.AESType? _aesTypeSelected = null;
    public SalmonSettings.AESType? AesTypeSelected
    {
        get => _aesTypeSelected;
        set
        {
            if (_aesTypeSelected != value)
            {
                _aesTypeSelected = value;
                SalmonSettings.GetInstance().AesType = (SalmonSettings.AESType)value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("AesTypeSelected"));
            }
        }
    }

    public List<SalmonSettings.AuthType> _authTypes = new List<SalmonSettings.AuthType>((IEnumerable<SalmonSettings.AuthType>)Enum.GetValues(typeof(SalmonSettings.AuthType)));
    public List<SalmonSettings.AuthType> AuthTypes
    {
        get => _authTypes;
        set
        {
            _authTypes = value;
            if (PropertyChanged != null)
                PropertyChanged(this, new PropertyChangedEventArgs("AuthTypes"));

        }
    }
    public SalmonSettings.AuthType? _authTypeSelected = null;
    public SalmonSettings.AuthType? AuthTypeSelected
    {
        get => _authTypeSelected;
        set
        {
            if (_authTypeSelected != value)
            {
                _authTypeSelected = value;
                if (initialized)
                    SalmonSettings.GetInstance().SequencerAuthType = (SalmonSettings.AuthType)value;
                if (PropertyChanged != null)
                    PropertyChanged(this, new PropertyChangedEventArgs("AuthTypeSelected"));
            }
        }
    }

    public bool _deleteSourceAfterImport;
    public bool DeleteSourceAfterImport
    {
        get => _deleteSourceAfterImport;
        set
        {
            _deleteSourceAfterImport = value;
            SalmonSettings.GetInstance().DeleteAfterImport = value;
            if (PropertyChanged != null)
                PropertyChanged(this, new PropertyChangedEventArgs("DeleteSourceAfterImport"));

        }
    }

    public void Initialize()
    {
        AesTypes = new List<SalmonSettings.AESType>((IEnumerable<SalmonSettings.AESType>)Enum.GetValues(typeof(SalmonSettings.AESType)));
        AesTypeSelected = SalmonSettings.GetInstance().AesType;

        AuthTypes = new List<SalmonSettings.AuthType>((IEnumerable<SalmonSettings.AuthType>)Enum.GetValues(typeof(SalmonSettings.AuthType)));
        AuthTypeSelected = SalmonSettings.GetInstance().SequencerAuthType;

        DeleteSourceAfterImport = SalmonSettings.GetInstance().DeleteAfterImport;
		
        initialized = true;
    }
}