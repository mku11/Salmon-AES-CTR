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

using Microsoft.Maui.Storage;
using Salmon.Vault.Settings;
using System;

namespace Salmon.Vault.Services;

public class MAUISettingsService : ISettingsService
{
    public string VaultLocation
    {
        get
        {
            string vaultLocation = Preferences.Default.Get("VaultLocation", SalmonSettings.DEFAULT_VAULT_LOCATION);
            if (vaultLocation != null && vaultLocation.Trim().Length == 0)
                return null;
            return vaultLocation;
        }
        set
        {
            Preferences.Default.Set("VaultLocation", value);
        }
    }
    public string AesType
    {
        get => Preferences.Default.Get("AesType", SalmonSettings.DEFAULT_AES_TYPE.ToString());
        set
        {
            Preferences.Default.Set("AesType", value);
        }
    }

    public string PbkdfImplType
    {
        get => Preferences.Default.Get("pbkdfType", SalmonSettings.DEFAULT_IMPL_TYPE.ToString());
        set
        {
            Preferences.Default.Set("pbkdfType", value);
        }
    }

    public string PbkdfAlgoType
    {
        get => Preferences.Default.Get("pbkdfAlgo", SalmonSettings.DEFAULT_PBKDF_ALGO.ToString());
        set
        {
            Preferences.Default.Set("pbkdfAlgo", value);
        }
    }

    public string SequenceAuthType
    {
        get => Preferences.Default.Get("authType", SalmonSettings.DEFAULT_AUTH_TYPE.ToString());
        set
        {
            Preferences.Default.Set("authType", value);
        }
    }

    public string LastImportDir
    {
        get => Preferences.Default.Get("lastImportDir", SalmonSettings.DEFAULT_LAST_IMPORT_DIR);
        set
        {
            Preferences.Default.Set("lastImportDir", value);
        }
    }

    public bool DeleteAfterImport
    {
        get => Preferences.Default.Get("deleteAfterImport", SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
        set
        {
            Preferences.Default.Set("deleteAfterImport", value);
        }
    }

    public bool ExcludeFromRecents
    {
        get => throw new NotImplementedException();
        set => throw new NotImplementedException();
    }
}