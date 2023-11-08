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
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Android.Preferences;
using Android.Widget;
using AndroidX.DocumentFile.Provider;
using Mku.Salmon;
using Mku.Salmon.IO;
using Mku.Salmon.Password;
using Salmon.Vault.Model;
using Salmon.Vault.Settings;
using Salmon.Vault.Utils;
using Salmon.Vault.DotNetAndroid;
using System;
using Salmon.Vault.Prefs;

namespace Salmon.Vault.Main;

[Activity(Label = "@string/app_name", Theme = "@style/AppTheme",
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
public class SettingsActivity : PreferenceActivity
{
    override
    protected void OnCreate(Bundle SavedInstanceState)
    {
        base.OnCreate(SavedInstanceState);
        AddPreferencesFromResource(Resource.Xml.settings);
        UpdateSummaries();
        SetupListeners();
    }

    override
    protected void OnResume()
    {
        base.OnResume();
        UpdateSummaries();
    }

    private void UpdateSummaries()
    {
        PreferenceManager.FindPreference("aesType").Summary = SalmonPreferences.GetProviderTypeString();
        PreferenceManager.FindPreference("pbkdfType").Summary = SalmonPreferences.GetPbkdfTypeString();
        PreferenceManager.FindPreference("pbkdfAlgo").Summary = SalmonPreferences.GetPbkdfAlgoString();
    }

    private string GetVaultDirName()
    {
        string path = SalmonPreferences.VaultLocation;
        if (path == null)
            return "None";
        DocumentFile documentFile = DocumentFile.FromTreeUri(this, Android.Net.Uri.Parse(path));
        return documentFile.Name;
    }

    private void SetupListeners()
    {

        PreferenceManager.FindPreference("aesType").PreferenceChange += (s, args) =>
        {
            SalmonStream.AesProviderType = (SalmonStream.ProviderType)Enum.Parse(typeof(SalmonStream.ProviderType), (string)args.NewValue);
            PreferenceManager.FindPreference("aesType").Summary = (string)args.NewValue;
        };

        PreferenceManager.FindPreference("pbkdfType").PreferenceChange += (s, args) =>
        {
            try
            {
                SalmonPassword.PbkdfImplType = (SalmonPassword.PbkdfType)Enum.Parse(typeof(SalmonPassword.PbkdfType), (string)args.NewValue);
            }
            catch (SalmonSecurityException e)
            {
                throw new Exception("Could not change Pbdkdf type", e);
            }
            PreferenceManager.FindPreference("pbkdfType").Summary = (string)args.NewValue;
        };

        PreferenceManager.FindPreference("pbkdfAlgo").PreferenceChange += (s, args) =>
        {
            SalmonPassword.PbkdfAlgorithm = (SalmonPassword.PbkdfAlgo)Enum.Parse(typeof(SalmonPassword.PbkdfAlgo), (string)args.NewValue);
            PreferenceManager.FindPreference("pbkdfAlgo").Summary = (string)args.NewValue;
        };

        PreferenceManager.FindPreference("excludeFromRecents").PreferenceChange += (s, args) =>
        {
            WindowUtils.RemoveFromRecents(this, (bool)args.NewValue);
        };
    }
}