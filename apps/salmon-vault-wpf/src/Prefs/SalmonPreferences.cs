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
using Salmon.Vault.Settings;
using System;
using Properties = Salmon.Vault.WPF.Properties;

namespace Salmon.Vault.Prefs;

public class SalmonPreferences
{
    public static void LoadPrefs()
    {
        try
        {
            SalmonSettings.GetInstance().VaultLocation = Properties.Settings.Default.VaultLocation;
            if (SalmonSettings.GetInstance().VaultLocation.Trim().Length == 0)
                SalmonSettings.GetInstance().VaultLocation = null;
            SalmonSettings.GetInstance().DeleteAfterImport = Properties.Settings.Default.DeleteSource;
            SalmonSettings.GetInstance().AesType = (SalmonSettings.AESType)Enum.Parse(typeof(SalmonSettings.AESType), Properties.Settings.Default.AesType);
            SalmonSettings.GetInstance().SequencerAuthType = (SalmonSettings.AuthType)Enum.Parse(typeof(SalmonSettings.AuthType), Properties.Settings.Default.AuthType);
            SalmonSettings.GetInstance().EnableLog = Properties.Settings.Default.EnableLog;
            SalmonSettings.GetInstance().EnableLogDetails = Properties.Settings.Default.EnableLogDetails;
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
            Properties.Settings.Default.VaultLocation = SalmonSettings.GetInstance().VaultLocation;
            Properties.Settings.Default.DeleteSource = SalmonSettings.GetInstance().DeleteAfterImport;
            Properties.Settings.Default.AesType = SalmonSettings.GetInstance().AesType.ToString();
            Properties.Settings.Default.AuthType = SalmonSettings.GetInstance().SequencerAuthType.ToString();
            Properties.Settings.Default.EnableLog = SalmonSettings.GetInstance().EnableLog;
            Properties.Settings.Default.EnableLogDetails = SalmonSettings.GetInstance().EnableLogDetails;
            Properties.Settings.Default.Save();
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }
}