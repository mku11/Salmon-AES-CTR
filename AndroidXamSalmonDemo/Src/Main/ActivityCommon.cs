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
using Android.Util;
using Android.Widget;
using Salmon.Droid.Utils;
using Salmon.Droid.FS;
using Salmon.FS;
using Google.Android.Material.Dialog;
using Google.Android.Material.TextField;
using Java.IO;
using System;
using System.Collections.Generic;
using System.Linq;

namespace Salmon.Droid.Main
{
    public partial class ActivityCommon
    {
        static readonly string TAG = typeof(ActivityCommon).Name;
        public static bool SetVaultFolder(Activity activity, Intent data)
        {

            Android.Net.Uri treeUri = data.Data;
            if (treeUri == null)
            {
                Toast.MakeText(activity, "Cannot List Directory", ToastLength.Long).Show();
                return false;
            }

            string lastDir = treeUri.ToString();

            if (lastDir.Contains("com.android.providers.downloads"))
            {
                SalmonDriveManager.GetDrive().PickRealFolder(activity, "Directory Already Used For Downloads", true,
                    SettingsActivity.GetVaultLocation(activity));
                return false;
            }
            else if (!lastDir.Contains("com.android.externalstorage"))
            {
                SalmonDriveManager.GetDrive().PickRealFolder(activity, "Directory Not Supported", true,
                    SettingsActivity.GetVaultLocation(activity));
                return false;
            }

            ActivityFlags takeFlags = data.Flags & (
                                ActivityFlags.GrantReadUriPermission |
                                ActivityFlags.GrantWriteUriPermission);

            try
            {
                for (int i = 0; activity.ContentResolver.PersistedUriPermissions.Count > 100; i++)
                {
                    IList<UriPermission> list = AndroidDrive.GetPermissionsList();
                    Android.Net.Uri uri = list[0].Uri;
                    activity.ContentResolver.ReleasePersistableUriPermission(uri, takeFlags);
                }
            }
            catch (Exception ex)
            {
                string err = "Could not release previous Persistable perms: " + ex;
                Log.Error(TAG, err);
                Toast.MakeText(activity, err, ToastLength.Long).Show();
            }

            try
            {
                activity.GrantUriPermission(activity.PackageName, treeUri,
                    ActivityFlags.GrantReadUriPermission);
                activity.GrantUriPermission(activity.PackageName, treeUri,
                    ActivityFlags.GrantPersistableUriPermission);
                activity.GrantUriPermission(activity.PackageName, treeUri,
                    ActivityFlags.GrantWriteUriPermission);

            }
            catch (Exception ex)
            {
                string err = "Could not grant uri perms to Activity: " + ex;
                Log.Error(TAG, err);
                Toast.MakeText(activity, err, ToastLength.Long).Show();
            }

            try
            {
                activity.ContentResolver.TakePersistableUriPermission(treeUri, takeFlags);
            }
            catch (Exception ex)
            {
                string err = "Could not take Persistable perms: " + ex;
                Log.Error(TAG, err);
                Toast.MakeText(activity, err, ToastLength.Long).Show();
            }
            SettingsActivity.SetVaultLocation(activity, treeUri.ToString());
            try
            {
                SalmonDriveManager.SetDriveLocation(treeUri.ToString());
                SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
            }
            catch (Exception e)
            {
                e.PrintStackTrace();
                return false;
            }
            return true;
        }


        //TODO: this is the standard way to get a users password though within c# we
        // shoud try using SecureString which requires other ways to capture the password from the user
        public static void PromptPassword(Activity activity, Action OnAuthenticationSucceded)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

            LinearLayout layout = new LinearLayout(activity);
            layout.SetPadding(20, 20, 20, 20);
            layout.Orientation = Orientation.Vertical;

            TextInputLayout typePasswdText = new TextInputLayout(activity, null,
                Resource.Style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            typePasswdText.PasswordVisibilityToggleEnabled = true;
            typePasswdText.BoxBackgroundMode = TextInputLayout.BoxBackgroundOutline;
            typePasswdText.SetBoxCornerRadii(5, 5, 5, 5);
            typePasswdText.Hint = "Password";

            TextInputEditText typePasswd = new TextInputEditText(typePasswdText.Context);
            typePasswd.InputType = Android.Text.InputTypes.TextVariationPassword |
                          Android.Text.InputTypes.ClassText;
            LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MatchParent, LinearLayout.LayoutParams.MatchParent);
            typePasswdText.AddView(typePasswd);

            layout.AddView(typePasswdText, parameters);

            builder.SetPositiveButton("Ok", (object sender, DialogClickEventArgs e) =>
            {
                try
                {
                    SalmonDriveManager.GetDrive().Authenticate(typePasswd.Text.ToString());
                    if (OnAuthenticationSucceded != null)
                        OnAuthenticationSucceded.Invoke();
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                    ActivityCommon.PromptPassword(activity, OnAuthenticationSucceded);
                }

            });
            builder.SetNegativeButton(activity.GetString(Android.Resource.String.Cancel), (object sender, DialogClickEventArgs e) =>
            {
                ((AndroidX.AppCompat.App.AlertDialog)sender).Dismiss();
            });

            AndroidX.AppCompat.App.AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(activity.GetString(Resource.String.Authenticate));
            alertDialog.SetCancelable(true);
            alertDialog.SetView(layout);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }

        public static void PromptSetPassword(Activity activity, Action<string> OnPasswordChanged)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

            LinearLayout layout = new LinearLayout(activity);
            layout.SetPadding(20, 20, 20, 20);
            layout.Orientation = Orientation.Vertical;

            TextInputLayout typePasswdText = new TextInputLayout(activity, null,
                Resource.Style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            typePasswdText.PasswordVisibilityToggleEnabled = true;
            typePasswdText.BoxBackgroundMode = TextInputLayout.BoxBackgroundOutline;
            typePasswdText.SetBoxCornerRadii(5, 5, 5, 5);
            typePasswdText.Hint = activity.GetString(Resource.String.Password);

            TextInputEditText typePasswd = new TextInputEditText(typePasswdText.Context);
            typePasswd.InputType = Android.Text.InputTypes.TextVariationPassword |
                          Android.Text.InputTypes.ClassText;
            LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MatchParent, LinearLayout.LayoutParams.MatchParent);
            typePasswdText.AddView(typePasswd);

            layout.AddView(typePasswdText, parameters);

            TextInputLayout retypePasswdText = new TextInputLayout(activity, null,
                Resource.Style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            retypePasswdText.PasswordVisibilityToggleEnabled = true;
            retypePasswdText.BoxBackgroundMode = TextInputLayout.BoxBackgroundOutline;
            retypePasswdText.SetBoxCornerRadii(5, 5, 5, 5);
            retypePasswdText.Hint = activity.GetString(Resource.String.RetypePassword);

            TextInputEditText retypePasswd = new TextInputEditText(retypePasswdText.Context);
            retypePasswd.InputType = Android.Text.InputTypes.TextVariationPassword |
                          Android.Text.InputTypes.ClassText;
            retypePasswdText.AddView(retypePasswd);

            layout.AddView(retypePasswdText, parameters);

            builder.SetPositiveButton(activity.GetString(Android.Resource.String.Ok), (object sender, DialogClickEventArgs e) =>
            {
                if (!typePasswd.Text.ToString().Equals(retypePasswd.Text.ToString()))
                    ActivityCommon.PromptSetPassword(activity, OnPasswordChanged);
                else
                {
                    try
                    {
                        SalmonDriveManager.GetDrive().SetPassword(typePasswd.Text.ToString());
                        if (OnPasswordChanged != null)
                            OnPasswordChanged.Invoke(typePasswd.Text.ToString());
                    } catch (SalmonAuthException ex)
                    {
                        PromptPassword(activity, ()=>
                        {
                            ActivityCommon.PromptSetPassword(activity, OnPasswordChanged);
                        });
                    }
                }
                
            });
            builder.SetNegativeButton(activity.GetString(Android.Resource.String.Cancel), (object sender, DialogClickEventArgs e) =>
            {
                ((AndroidX.AppCompat.App.AlertDialog)sender).Dismiss();
            });

            AndroidX.AppCompat.App.AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(activity.GetString(Resource.String.SetPassword));
            alertDialog.SetCancelable(true);
            alertDialog.SetView(layout);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }

        public static void PromptNewFolder(Activity activity, Action<string> OnFolderCreated)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            TextView folderNameText = new TextView(activity);
            folderNameText.SetPadding(20, 20, 20, 20);
            folderNameText.SetText(activity.GetString(Resource.String.CreateFolder), TextView.BufferType.Normal);

            EditText folderName = new EditText(activity);
            folderName.InputType = Android.Text.InputTypes.ClassText |
                    Android.Text.InputTypes.TextVariationPassword |
                    Android.Text.InputTypes.TextFlagNoSuggestions;
            folderName.SetSelection(folderName.Text.Length);

            LinearLayout mLayout = new LinearLayout(activity);
            mLayout.SetPadding(20, 20, 20, 20);
            mLayout.Orientation = Orientation.Vertical;

            LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MatchParent, LinearLayout.LayoutParams.WrapContent);

            mLayout.AddView(folderNameText, parameters);
            mLayout.AddView(folderName, parameters);

            ScrollView s = new ScrollView(activity);
            s.SetPadding(40, 40, 40, 40);
            s.AddView(mLayout);

            builder.SetPositiveButton(activity.GetString(Android.Resource.String.Ok), (object sender, DialogClickEventArgs e) =>
            {
                if (OnFolderCreated != null)
                    OnFolderCreated.Invoke(folderName.Text.ToString());
            });
            builder.SetNegativeButton(activity.GetString(Android.Resource.String.Cancel), (object sender, DialogClickEventArgs e) =>
            {
                ((AndroidX.AppCompat.App.AlertDialog)sender).Dismiss();
            });

            AndroidX.AppCompat.App.AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(activity.GetString(Resource.String.Authenticate));
            alertDialog.SetCancelable(true);
            alertDialog.SetView(s);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }

        internal static void PromptOpenWith(Activity activity, Intent intent, SortedDictionary<string,string> apps,
            Android.Net.Uri uri, File sharedFile, SalmonFile salmonFile, bool allowWrite, 
            Action<AndroidSharedFileObserver> OnFileContentsChanged)
        {

            string[] names = apps.Keys.ToArray();
            string[] packageNames = apps.Values.ToArray();
            
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            builder.SetTitle(activity.GetString(Resource.String.ChooseApp));
            builder.SetSingleChoiceItems(names, -1, (object sender, DialogClickEventArgs e) =>
            {
                try
                {
                    AndroidX.AppCompat.App.AlertDialog alertDialog = sender as AndroidX.AppCompat.App.AlertDialog;
                    alertDialog.Dismiss();

                    ActivityFlags activityFlags = ActivityFlags.GrantReadUriPermission;
                    if (allowWrite)
                        activityFlags |= ActivityFlags.GrantWriteUriPermission;
                    
                    SetFileContentsChangedObserver(sharedFile, salmonFile, OnFileContentsChanged);
                    activity.GrantUriPermission(packageNames[e.Which], uri, activityFlags);
                    intent.SetPackage(packageNames[e.Which]);
                    activity.StartActivity(intent);
                }
                catch (System.Exception ex)
                {
                    Toast.MakeText(activity, "Could not start application", ToastLength.Long).Show();
                    ex.PrintStackTrace();
                    sharedFile.Delete();
                }
            });
            AndroidX.AppCompat.App.AlertDialog alert = builder.Create();
            alert.Show();
        }

        private static void SetFileContentsChangedObserver(File cacheFile, SalmonFile salmonFile, Action<AndroidSharedFileObserver> onFileContentsChanged)
        {
            AndroidSharedFileObserver fileObserver = AndroidSharedFileObserver.CreateFileObserver(cacheFile, 
                salmonFile, onFileContentsChanged);
            fileObserver.StartWatching();
        }

        public static void PromptDialog(Activity activity, string title, string body,
            string buttonLabel1 = null, EventHandler<DialogClickEventArgs> buttonListener1 = null,
            string buttonLabel2 = null, EventHandler<DialogClickEventArgs> buttonListener2 = null)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            if (title != null)
                builder.SetTitle(title);
            builder.SetMessage(body);
            if (buttonLabel1 != null)
                builder.SetPositiveButton(buttonLabel1, buttonListener1);
            if (buttonLabel2 != null)
                builder.SetNegativeButton(buttonLabel2, buttonListener2);

            AndroidX.AppCompat.App.AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(title);
            alertDialog.SetCancelable(true);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }
    }
}