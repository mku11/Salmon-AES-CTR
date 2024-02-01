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

using Android.Content;
using Android.Preferences;
using Salmon.Vault.Main;
using Salmon.Vault.Services;
using Salmon.Vault.Settings;

public class AndroidSettingsService : ISettingsService
{
    private readonly ISharedPreferences prefs;

    public AndroidSettingsService()
    {
        prefs = PreferenceManager.GetDefaultSharedPreferences(SalmonApplication.GetInstance().ApplicationContext);
    }

    public string VaultLocation
    {
        get => prefs.GetString("vaultLocation",
                SalmonSettings.DEFAULT_VAULT_LOCATION);
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("vaultLocation", value);
            editor.Apply();
        }
    }
    public string AesType
    {
        get => prefs.GetString("aesType",
                SalmonSettings.AESType.AesIntrinsics.ToString());
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("aesType", value);
            editor.Apply();
        }
    }
    public string PbkdfImplType
    {
        get => prefs.GetString("pbkdfType",
                SalmonSettings.DEFAULT_IMPL_TYPE.ToString());
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("pbkdfType", value);
            editor.Apply();
        }
    }
    public string PbkdfAlgoType {
        get => prefs.GetString("pbkdfAlgo",
                SalmonSettings.DEFAULT_PBKDF_ALGO.ToString());
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("pbkdfAlgo", value);
            editor.Apply();
        }
    }
    public string SequenceAuthType
    {
        get => prefs.GetString("authType",
                SalmonSettings.DEFAULT_AUTH_TYPE.ToString());
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("authType", value);
            editor.Apply();
        }
    }
    public string LastImportDir
    {
        get => prefs.GetString("lastImportDir",
                SalmonSettings.DEFAULT_LAST_IMPORT_DIR);
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("lastImportDir", value);
            editor.Apply();
        }
    }
    public bool DeleteAfterImport
    {
        get => prefs.GetBoolean("deleteAfterImport",
                SalmonSettings.DEFAULT_DELETE_AFTER_IMPORT);
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutBoolean("deleteAfterImport", value);
            editor.Apply();
        }
    }
}