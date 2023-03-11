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
using Context = Android.Content.Context;
using Android.OS;
using Android.Provider;
using Console = System.Console;
using Uri = Android.Net.Uri;
using Android.Util;
using AndroidX.DocumentFile.Provider;
using AlertDialog = AndroidX.AppCompat.App.AlertDialog;

namespace Salmon.Droid.Main
{
    public partial class ActivityCommon
    {
        static readonly string TAG = typeof(ActivityCommon).Name;
        public static readonly string EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";

        //TODO: this is the standard way to get a users password though within c# we
        // shoud try using SecureString which requires other ways to capture the password from the user
        public static void PromptPassword(Activity activity, Action<SalmonDrive> OnAuthenticationSucceded)
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
                        OnAuthenticationSucceded.Invoke(SalmonDriveManager.GetDrive());
                }
                catch (Exception ex)
                {
                    ex.PrintStackTrace();
                    PromptPassword(activity, OnAuthenticationSucceded);
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
                    if (OnPasswordChanged != null)
                        OnPasswordChanged.Invoke(typePasswd.Text.ToString());
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


        public static void PromptEdit(Activity activity, string title, string msg,
            string value, string option, Action<string,bool> OnEdit, bool isFileName = false,
            bool readOnly = false)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

            LinearLayout layout = new LinearLayout(activity);
            layout.SetPadding(20, 20, 20, 20);
            layout.Orientation = Orientation.Vertical;

            TextInputLayout msgText = new TextInputLayout(activity, null,
                Resource.Style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            msgText.BoxBackgroundMode = TextInputLayout.BoxBackgroundOutline;
            msgText.SetBoxCornerRadii(5, 5, 5, 5);
            msgText.Hint = msg;

            TextInputEditText valueText = new TextInputEditText(msgText.Context);
            valueText.Text = value;
            if (readOnly)
            {
                valueText.InputType = Android.Text.InputTypes.Null;
                valueText.SetTextIsSelectable(true);
            }
            else
            {
                valueText.InputType = Android.Text.InputTypes.ClassText;
            }
            if (isFileName)
            {
                string ext = SalmonDriveManager.GetDrive().GetExtensionFromFileName(value);
                if (ext != null && ext.Length > 0)
                    valueText.SetSelection(0, value.Length - ext.Length - 1);
                else
                    valueText.SetSelection(0, value.Length);
            }
            else
            {
                valueText.SelectAll();
            }
            LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MatchParent, LinearLayout.LayoutParams.MatchParent);
            msgText.AddView(valueText);
            layout.AddView(msgText, parameters);
            CheckBox optionCheckBox = new CheckBox(activity);
            if (option != null)
            {
                optionCheckBox.Text = option;
                layout.AddView(optionCheckBox, parameters);
            }
            builder.SetPositiveButton(activity.GetString(Android.Resource.String.Ok), (object sender, DialogClickEventArgs e) =>
            {
                if (OnEdit != null)
                    OnEdit.Invoke(valueText.Text.ToString(), optionCheckBox.Checked);
            });
            builder.SetNegativeButton(activity.GetString(Android.Resource.String.Cancel), (object sender, DialogClickEventArgs e) =>
            {
                ((AndroidX.AppCompat.App.AlertDialog)sender).Dismiss();
            });

            AndroidX.AppCompat.App.AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(title);
            alertDialog.SetCancelable(true);
            alertDialog.SetView(layout);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }

        internal static void PromptOpenWith(Activity activity, Intent intent, SortedDictionary<string, string> apps,
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


        public static void PromptSingleValue(Activity activity, string title,
                                             List<string> items, int currSelection, Action<int> onClickListener)
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            if (title != null)
                builder.SetTitle(title);
            builder.SetSingleChoiceItems(items.ToArray(), currSelection, (object sender, DialogClickEventArgs e) =>
            {
                onClickListener.Invoke(e.Which);
            });
            AlertDialog alertDialog = builder.Create();
            alertDialog.SetTitle(title);
            alertDialog.SetCancelable(true);

            if (!activity.IsFinishing)
                alertDialog.Show();
        }

        public static void OpenVault(Context context, string dirPath)
        {
            SalmonDriveManager.OpenDrive(dirPath);
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
            SettingsActivity.SetVaultLocation(context, dirPath);
        }

        public static void CreateVault(Context context, string dirPath, string password)
        {
            SalmonDriveManager.CreateDrive(dirPath, password);
            SalmonDriveManager.GetDrive().SetEnableIntegrityCheck(true);
            SettingsActivity.SetVaultLocation(context, dirPath);
        }


        /// <summary>
        /// Prompt the user for a Storage Access Framework uri
        /// </summary>
        /// <param name="activity"></param>
        /// <param name="folder"></param>
        /// <param name="lastDir"></param>
        public static void OpenFilesystem(Activity activity, bool folder, bool multiSelect, string lastDir, int resultCode)
        {
            Intent intent = new Intent(folder ? Intent.ActionOpenDocumentTree : Intent.ActionOpenDocument);
            intent.AddFlags(ActivityFlags.GrantPersistableUriPermission);
            intent.AddFlags(ActivityFlags.GrantReadUriPermission);
            intent.AddFlags(ActivityFlags.GrantWriteUriPermission);

            if (folder && lastDir != null)
            {
                try
                {
                    Uri uri = DocumentsContract.BuildDocumentUri(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, "primary:");
                    intent.PutExtra(DocumentsContract.ExtraInitialUri, uri);
                }
                catch (Exception ex)
                {
                    Console.WriteLine(ex);
                }
            }
            if (!folder)
            {
                intent.AddCategory(Intent.CategoryOpenable);
                intent.SetType("*/*");
            }
            if (multiSelect)
            {
                intent.PutExtra(Intent.ExtraAllowMultiple, true);
            }

            string prompt = "Open File(s)";
            if (folder)
                prompt = "Open Directory";

            intent.PutExtra(DocumentsContract.ExtraPrompt, prompt);
            intent.PutExtra("android.content.extra.SHOW_ADVANCED", true);
            intent.PutExtra(Intent.ExtraLocalOnly, true);
            try
            {
                activity.StartActivityForResult(intent, resultCode);
            }
            catch (Exception e)
            {
                Toast.MakeText(activity, "Could not start file picker!" + e.Message, ToastLength.Long).Show();
            }
        }

        public static void SetUriPermissions(Intent data, Uri uri)
        {
            int takeFlags = 0;
            if (data != null)
                takeFlags = (int)data.Flags;
            takeFlags &= (
                    (int)ActivityFlags.GrantReadUriPermission |
                    (int)ActivityFlags.GrantWriteUriPermission
            );

            try
            {
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantReadUriPermission);
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantWriteUriPermission);
                Application.Context.GrantUriPermission(Application.Context.PackageName, uri, ActivityFlags.GrantPersistableUriPermission);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
                string err = "Could not grant uri perms to activity: " + ex;
                Toast.MakeText(Application.Context, err, ToastLength.Long).Show();
            }

            try
            {
                Application.Context.ContentResolver.TakePersistableUriPermission(uri, (ActivityFlags)takeFlags);
            }
            catch (Exception ex)
            {
                ex.PrintStackTrace();
                string err = "Could not take Persistable perms: " + ex;
                Toast.MakeText(Application.Context, err, ToastLength.Long).Show();
            }
        }


        /// <summary>
        /// Retrieve real files from an intent that was received
        /// </summary>
        /// <param name="context"></param>
        /// <param name="data"></param>
        /// <returns></returns>
        public static IRealFile[] GetFilesFromIntent(Context context, Intent data)
        {
            IRealFile[] files = null;

            if (data != null)
            {
                if (null != data.ClipData)
                {
                    files = new IRealFile[data.ClipData.ItemCount];
                    for (int i = 0; i < data.ClipData.ItemCount; i++)
                    {
                        Android.Net.Uri uri = data.ClipData.GetItemAt(i).Uri;
                        string filename = uri.ToString();
                        Log.Debug(TAG, "File: " + filename);
                        DocumentFile docFile = DocumentFile.FromSingleUri(context, uri);
                        files[i] = new AndroidFile(docFile, context);
                    }
                }
                else
                {
                    Uri uri = data.Data;
                    files = new IRealFile[1];
                    DocumentFile docFile = DocumentFile.FromSingleUri(context, uri);
                    files[0] = new AndroidFile(docFile, context);
                }
            }
            return files;
        }
    }
}