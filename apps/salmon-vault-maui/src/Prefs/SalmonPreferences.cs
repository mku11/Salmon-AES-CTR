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

namespace Salmon.Vault.Prefs;

public class SalmonPreferences
{
    public static void LoadPrefs()
    {
        try
        {
            SalmonSettings.GetInstance().VaultLocation = Preferences.Default.Get("VaultLocation", SalmonSettings.DEFAULT_VAULT_LOCATION);
            if (SalmonSettings.GetInstance().VaultLocation != null && SalmonSettings.GetInstance().VaultLocation.Trim().Length == 0)
                SalmonSettings.GetInstance().VaultLocation = null;
            SalmonSettings.GetInstance().DeleteAfterImport = Preferences.Default.Get("DeleteSource", SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
            SalmonSettings.GetInstance().AesType = SalmonSettings.GetInstance().AesType = (SalmonSettings.AESType)Enum.Parse(typeof(SalmonSettings.AESType),
                Preferences.Default.Get("AesType", SalmonSettings.DEFAULT_AES_TYPE.ToString()));
            SalmonSettings.GetInstance().SequencerAuthType = (SalmonSettings.AuthType)Enum.Parse(typeof(SalmonSettings.AuthType),
                Preferences.Default.Get("AuthType", SalmonSettings.DEFAULT_AUTH_TYPE.ToString()));
            SalmonSettings.GetInstance().EnableLog = Preferences.Default.Get("EnableLog", SalmonSettings.DEFAULT_ENABLE_LOG);
            SalmonSettings.GetInstance().EnableLogDetails = Preferences.Default.Get("EnableLogDetails", SalmonSettings.DEFAULT_ENABLE_LOG_DETAILS);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }

    public static void SavePrefs()
    {
        try
        {
            Preferences.Default.Set("VaultLocation", SalmonSettings.GetInstance().VaultLocation);
            Preferences.Default.Set("DeleteSource", SalmonSettings.GetInstance().DeleteAfterImport);
            Preferences.Default.Set("AesType", SalmonSettings.GetInstance().AesType.ToString());
            Preferences.Default.Set("AuthType", SalmonSettings.GetInstance().SequencerAuthType.ToString());
            Preferences.Default.Set("EnableLog", SalmonSettings.GetInstance().EnableLog);
            Preferences.Default.Set("EnableLogDetails", SalmonSettings.GetInstance().EnableLogDetails);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }
}