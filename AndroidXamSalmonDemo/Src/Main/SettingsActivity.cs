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
using Salmon.Droid.FS;
using Salmon.FS;
using System.Threading;

namespace Salmon.Droid.Main
{
    [Activity(Label = "@string/app_name", Icon = "@drawable/logo",
        Theme = "@style/Theme.MaterialComponents",
        ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
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
                SalmonDriveManager.GetDrive().PickRealFolder(this, "Select a Folder for your Encrypted files", true, null);
            };

            PreferenceManager.FindPreference("changePassword").PreferenceClick += delegate
            {
                ActivityCommon.PromptSetPassword(this, null);
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

        protected override void OnActivityResult(int requestCode, Result resultCode, Intent data) 
        {
            if (requestCode == AndroidDrive.RequestSdcardCodeFolder)
            {
                if (data != null)
                {
                    Thread t = new Thread(() =>
                    {
                        bool res = ActivityCommon.SetVaultFolder(this, data);
                        if (!res)
                        {
                            return;
                        }
                    });
                    t.Start();
                }
            }
            base.OnActivityResult(requestCode, resultCode, data);
        }

        
    }

}