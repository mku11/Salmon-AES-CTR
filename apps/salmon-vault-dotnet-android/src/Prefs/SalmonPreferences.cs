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
using Android.Preferences;
using Salmon.Vault.Main;
using Salmon.Vault.Settings;
using System;

namespace Salmon.Vault.Prefs;

public class SalmonPreferences
{
    static ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(Application.Context);

    public static string VaultLocation
    {
        get
        {
            return prefs.GetString("vaultLocation", null);
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("vaultLocation", value);
            editor.Commit();
        }
    }

    public static bool DeleteAfterImport
    {
        get
        {
            return prefs.GetBoolean("deleteAfterImport", false);
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutBoolean("deleteAfterImport", value);
            editor.Commit();
        }
    }

    public static SalmonSettings.AESType AesProviderType
    {
        get
        {
            return (SalmonSettings.AESType)Enum.Parse(typeof(SalmonSettings.AESType), GetProviderTypeString());
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("aesType", value.ToString());
            editor.Commit();
        }
    }

    public static string GetProviderTypeString()
    {
        return prefs.GetString("aesType", SalmonSettings.AESType.AesIntrinsics.ToString());
    }

    public static SalmonSettings.PbkdfImplType PbkdfImplType
    {
        get
        {
            return (SalmonSettings.PbkdfImplType)Enum.Parse(
        typeof(SalmonSettings.PbkdfImplType), GetPbkdfTypeString());
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("pbkdfType", value.ToString());
            editor.Commit();
        }
    }

    public static string GetPbkdfTypeString()
    {
        return prefs.GetString("pbkdfType", SalmonSettings.PbkdfImplType.Default.ToString());
    }

    public static SalmonSettings.PbkdfAlgoType PbkdfAlgorithm
    {
        get
        {
            return (SalmonSettings.PbkdfAlgoType)Enum.Parse(
        typeof(SalmonSettings.PbkdfAlgoType), GetPbkdfAlgoString());
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("pbkdfAlgo", value.ToString());
            editor.Commit();
        }
    }

    public static string GetPbkdfAlgoString()
    {
        return prefs.GetString("pbkdfAlgo", SalmonSettings.PbkdfAlgoType.SHA256.ToString());
    }

    public static bool ExcludeFromRecents => prefs.GetBoolean("excludeFromRecents", false);

    public static bool HideScreenContents => prefs.GetBoolean("hideScreenContents", true);

    public static string LastImportDir
    {
        get
        {
            return prefs.GetString("lastImportDir", null);
        }
        set
        {
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("lastImportDir", value);
            editor.Commit();
        }
    }

    public static void LoadPrefs()
    {
        try
        {
            SalmonSettings.GetInstance().VaultLocation = VaultLocation;
            if (SalmonSettings.GetInstance().VaultLocation != null && SalmonSettings.GetInstance().VaultLocation.Trim().Length == 0)
                SalmonSettings.GetInstance().VaultLocation = null;
            SalmonSettings.GetInstance().DeleteAfterImport = DeleteAfterImport;
            SalmonSettings.GetInstance().AesType = (SalmonSettings.AESType)Enum.Parse(typeof(SalmonSettings.AESType), AesProviderType.ToString());
            SalmonSettings.GetInstance().PbkdfImpl = (SalmonSettings.PbkdfImplType)Enum.Parse(typeof(SalmonSettings.PbkdfImplType), PbkdfImplType.ToString());
            SalmonSettings.GetInstance().PbkdfAlgo = (SalmonSettings.PbkdfAlgoType)Enum.Parse(typeof(SalmonSettings.PbkdfAlgoType), PbkdfAlgorithm.ToString());
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
            VaultLocation = SalmonSettings.GetInstance().VaultLocation;
            DeleteAfterImport = SalmonSettings.GetInstance().DeleteAfterImport;
            AesProviderType = SalmonSettings.GetInstance().AesType;
            PbkdfImplType = SalmonSettings.GetInstance().PbkdfImpl;
            PbkdfAlgorithm = SalmonSettings.GetInstance().PbkdfAlgo;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
        }
    }
}