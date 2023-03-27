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
using Salmon.Droid.Media;
using Salmon.Droid.Utils;
using Salmon.FS;
using Salmon.Streams;
using System;

namespace Salmon.Droid.Main
{
    [Activity(Label = "@string/app_name", ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    [System.Obsolete]
    public class SettingsActivity : PreferenceActivity
    {
        protected override void OnCreate(Bundle SavedInstanceState)
        {
            base.OnCreate(SavedInstanceState);
            AddPreferencesFromResource(Resource.Xml.settings);
            SetupListeners();
        }

        private void SetupListeners()
        {
            PreferenceManager.FindPreference("vaultLocation").PreferenceClick += delegate
            {
                ActivityCommon.OpenFilesystem(this, true, false, GetVaultLocation(this),
                    SalmonActivity.REQUEST_OPEN_VAULT_DIR);
            };

            PreferenceManager.FindPreference("changePassword").PreferenceClick += delegate
            {
                ActivityCommon.PromptSetPassword(this, (pass) =>
                {
                    try
                    {
                        SalmonDriveManager.GetDrive().SetPassword(pass);
                        Toast.MakeText(this, "Password changed", ToastLength.Long).Show();
                    }
                    catch (Exception e)
                    {
                        e.PrintStackTrace();
                        Toast.MakeText(this, "Could not change password", ToastLength.Long).Show();
                    }
                });
            };

            PreferenceManager.FindPreference("enableLog").PreferenceChange += (object sender, Preference.PreferenceChangeEventArgs e) =>
            {
                SalmonFileExporter.SetEnableLog((bool)e.NewValue);
                SalmonFileImporter.SetEnableLog((bool)e.NewValue);
                SalmonMediaDataSource.SetEnableLog((bool)e.NewValue);
            };

            PreferenceManager.FindPreference("enableLogDetails").PreferenceChange += (object sender, Preference.PreferenceChangeEventArgs e) =>
            {
                SalmonFileExporter.SetEnableLogDetails((bool)e.NewValue);
                SalmonFileImporter.SetEnableLogDetails((bool)e.NewValue);
                SalmonStream.SetEnableLogDetails((bool)e.NewValue);
            };

            PreferenceManager.FindPreference("excludeFromRecents").PreferenceChange += (object sender, Preference.PreferenceChangeEventArgs e) =>
            {
                WindowUtils.RemoveFromRecents(this, (bool)e.NewValue);
            };
        }

        /// <summary>
        /// Returns the current vault location on disk
        /// </summary>
        /// <param name="context"></param>
        /// <returns></returns>
        public static string GetVaultLocation(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetString("vaultLocation", null);
        }

        /// <summary>
        /// Sets the current vault location on disk
        /// </summary>
        /// <param name="context"></param>
        /// <param name="location"></param>
        public static void SetVaultLocation(Context context, string location)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            ISharedPreferencesEditor editor = prefs.Edit();
            editor.PutString("vaultLocation", location);
            editor.Commit();
        }

        public static bool GetDeleteAfterImport(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetBoolean("deleteAfterImport", false);
        }

        protected override void OnActivityResult(int requestCode, Result resultCode, Intent data)
        {
            if (data == null)
                return;
            Android.Net.Uri uri = data.Data;
            if (requestCode == SalmonActivity.REQUEST_OPEN_VAULT_DIR)
            {
                try
                {
                    ActivityCommon.SetUriPermissions(data, data.Data);
                    SetVaultLocation(this, data.Data.ToString());
                    ActivityCommon.OpenVault(this, uri.ToString());
                    //TODO: notify ui
                }
                catch (Exception e)
                {
                    Toast.MakeText(this, "Could not change vault: " + e.Message, ToastLength.Long).Show();
                }
            }
        }

        public static SalmonStream.ProviderType getProviderType(Context context)
        {
            return Enum.Parse<SalmonStream.ProviderType>(getProviderTypeString(context));
        }

        private static string getProviderTypeString(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetString("aesType", "Default");
        }

        public static bool getEnableLog(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetBoolean("enableLog", false);
        }

        public static bool getEnableLogDetails(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetBoolean("enableLogDetails", false);
        }

        public static bool getExcludeFromRecents(Context context)
        {
            ISharedPreferences prefs = PreferenceManager.GetDefaultSharedPreferences(context);
            return prefs.GetBoolean("excludeFromRecents", false);
        }

    }

}