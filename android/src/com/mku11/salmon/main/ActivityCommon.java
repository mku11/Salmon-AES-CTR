package com.mku11.salmon.main;
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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.arch.core.util.Function;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mku.android.salmonvault.R;
import com.mku11.salmon.file.AndroidDrive;
import com.mku11.salmon.file.AndroidSharedFileObserver;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;

public class ActivityCommon {
    static final String TAG = ActivityCommon.class.getName();

    public interface OnTextSubmittedListener {
        void onTextSubmitted(String text, Boolean option);
    }

    public static boolean setVaultFolder(Activity activity, Intent data) {

        android.net.Uri treeUri = data.getData();
        if (treeUri == null) {
            Toast.makeText(activity, "Cannot List Directory", Toast.LENGTH_LONG).show();
            return false;
        }

        String lastDir = treeUri.toString();

        if (lastDir.contains("com.android.providers.downloads")) {
            ((AndroidDrive) SalmonDriveManager.getDrive()).pickFiles(activity, "Directory Already Used For Downloads", true,
                    SettingsActivity.getVaultLocation(activity));
            return false;
        } else if (!lastDir.contains("com.android.externalstorage")) {
            ((AndroidDrive) SalmonDriveManager.getDrive()).pickFiles(activity, "Directory Not Supported", true,
                    SettingsActivity.getVaultLocation(activity));
            return false;
        }

        try {
            for (int i = 0; activity.getContentResolver().getPersistedUriPermissions().size() > 100; i++) {
                List<UriPermission> list = AndroidDrive.getPermissionsList();
                android.net.Uri uri = list.get(i).getUri();
                activity.getContentResolver().releasePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        } catch (Exception ex) {
            String err = "Could not release previous Persistable perms: " + ex;
            Log.e(TAG, err);
            Toast.makeText(activity, err, Toast.LENGTH_LONG).show();
        }

        try {
            activity.grantUriPermission(activity.getPackageName(), treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.grantUriPermission(activity.getPackageName(), treeUri,
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.grantUriPermission(activity.getPackageName(), treeUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        } catch (Exception ex) {
            String err = "Could not grant uri perms to Activity: " + ex;
            Log.e(TAG, err);
            Toast.makeText(activity, err, Toast.LENGTH_LONG).show();
        }

        try {
            activity.getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ex) {
            String err = "Could not take Persistable perms: " + ex;
            Log.e(TAG, err);
            Toast.makeText(activity, err, Toast.LENGTH_LONG).show();
        }
        SettingsActivity.setVaultLocation(activity, treeUri.toString());
        try {
            SalmonDriveManager.setDriveLocation(treeUri.toString());
            SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static void promptPassword(Activity activity, Function<Void, Void> OnAuthenticationSucceded) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setPadding(20, 20, 20, 20);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextInputLayout typePasswdText = new TextInputLayout(activity, null,
                R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        typePasswdText.setPasswordVisibilityToggleEnabled(true);
        typePasswdText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        typePasswdText.setBoxCornerRadii(5, 5, 5, 5);
        typePasswdText.setHint("Password");

        TextInputEditText typePasswd = new TextInputEditText(typePasswdText.getContext());
        typePasswd.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD |
                InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        typePasswdText.addView(typePasswd);

        layout.addView(typePasswdText, parameters);

        builder.setPositiveButton("Ok", (DialogInterface dialog, int which) ->
        {
            try {
                SalmonDriveManager.getDrive().authenticate(typePasswd.getText().toString());
                if (OnAuthenticationSucceded != null)
                    OnAuthenticationSucceded.apply(null);
            } catch (Exception ex) {
                ex.printStackTrace();
                ActivityCommon.promptPassword(activity, OnAuthenticationSucceded);
            }

        });
        builder.setNegativeButton(activity.getString(android.R.string.cancel), (DialogInterface dialog, int which) ->
                dialog.dismiss());

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(activity.getString(R.string.Authenticate));
        alertDialog.setCancelable(true);
        alertDialog.setView(layout);

        if (!activity.isFinishing())
            alertDialog.show();
    }

    public static void promptSetPassword(Activity activity, Function<String, Void> OnPasswordChanged) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setPadding(20, 20, 20, 20);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextInputLayout typePasswdText = new TextInputLayout(activity, null,
                R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        typePasswdText.setPasswordVisibilityToggleEnabled(true);
        typePasswdText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        typePasswdText.setBoxCornerRadii(5, 5, 5, 5);
        typePasswdText.setHint(activity.getString(R.string.Password));

        TextInputEditText typePasswd = new TextInputEditText(typePasswdText.getContext());
        typePasswd.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD |
                InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        typePasswdText.addView(typePasswd);

        layout.addView(typePasswdText, parameters);

        TextInputLayout retypePasswdText = new TextInputLayout(activity, null,
                R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        retypePasswdText.setPasswordVisibilityToggleEnabled(true);
        retypePasswdText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        retypePasswdText.setBoxCornerRadii(5, 5, 5, 5);
        retypePasswdText.setHint(activity.getString(R.string.RetypePassword));

        TextInputEditText retypePasswd = new TextInputEditText(retypePasswdText.getContext());
        retypePasswd.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD |
                InputType.TYPE_CLASS_TEXT);
        retypePasswdText.addView(retypePasswd);

        layout.addView(retypePasswdText, parameters);

        builder.setPositiveButton(activity.getString(android.R.string.ok), (DialogInterface dialog, int which) ->
        {
            if (!typePasswd.getText().toString().equals(retypePasswd.getText().toString()))
                ActivityCommon.promptSetPassword(activity, OnPasswordChanged);
            else {
                try {
                    SalmonDriveManager.getDrive().setPassword(typePasswd.getText().toString());
                    if (OnPasswordChanged != null)
                        OnPasswordChanged.apply(typePasswd.getText().toString());
                } catch (SalmonAuthException ex) {
                    promptPassword(activity, (a) ->
                    {
                        ActivityCommon.promptSetPassword(activity, OnPasswordChanged);
                        return null;
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        });
        builder.setNegativeButton(activity.getString(android.R.string.cancel), (DialogInterface dialog, int which) ->
                dialog.dismiss());

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(activity.getString(R.string.SetPassword));
        alertDialog.setCancelable(true);
        alertDialog.setView(layout);

        if (!activity.isFinishing())
            alertDialog.show();
    }

    public static void promptEdit(Activity activity, String title, String msg, String value, String option, OnTextSubmittedListener OnEdit) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

        LinearLayout layout = new LinearLayout(activity);
        layout.setPadding(20, 20, 20, 20);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextInputLayout msgText = new TextInputLayout(activity, null,
                R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        msgText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        msgText.setBoxCornerRadii(5, 5, 5, 5);
        msgText.setHint(msg);

        TextInputEditText valueText = new TextInputEditText(msgText.getContext());
        valueText.setText(value);
        valueText.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        msgText.addView(valueText);
        layout.addView(msgText, parameters);

        CheckBox optionCheckBox = new CheckBox(activity);
        if (option != null) {
            optionCheckBox.setText(option);
            layout.addView(optionCheckBox);
        }

        builder.setPositiveButton("Ok", (DialogInterface dialog, int which) ->
        {
            if (OnEdit != null)
                OnEdit.onTextSubmitted(valueText.getText().toString(), optionCheckBox.isChecked());
        });
        builder.setNegativeButton(activity.getString(android.R.string.cancel), (DialogInterface dialog, int which) ->
                dialog.dismiss());

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(title);
        alertDialog.setCancelable(true);
        alertDialog.setView(layout);

        if (!activity.isFinishing()) {
            alertDialog.show();
            new Handler(Looper.getMainLooper()).postDelayed(() ->
            {
                int last = value.lastIndexOf(".");
                valueText.requestFocus();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(valueText, InputMethodManager.SHOW_IMPLICIT);
                valueText.setSelection(0, last >= 0 ? last : value.length());
            }, 1000);
        }
    }

    static void promptOpenWith(Activity activity, Intent intent, LinkedHashMap<String, String> apps,
                               android.net.Uri uri, File sharedFile, SalmonFile salmonFile, boolean allowWrite,
                               Function<AndroidSharedFileObserver, Void> OnFileContentsChanged) {

        String[] names = apps.keySet().toArray(new String[0]);
        String[] packageNames = apps.values().toArray(new String[0]);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.getString(R.string.ChooseApp));
        builder.setSingleChoiceItems(names, -1, (DialogInterface dialog, int which) ->
        {
            try {
                androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) dialog;
                alertDialog.dismiss();

                int activityFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (allowWrite)
                    activityFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

                setFileContentsChangedObserver(sharedFile, salmonFile, OnFileContentsChanged);
                activity.grantUriPermission(packageNames[which], uri, activityFlags);
                intent.setPackage(packageNames[which]);
                activity.startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(activity, "Could not start application", Toast.LENGTH_LONG).show();
                ex.printStackTrace();
                sharedFile.delete();
            }
        });
        androidx.appcompat.app.AlertDialog alert = builder.create();
        alert.show();
    }

    private static void setFileContentsChangedObserver(File cacheFile, SalmonFile salmonFile, Function<AndroidSharedFileObserver, Void> onFileContentsChanged) {
        AndroidSharedFileObserver fileObserver = AndroidSharedFileObserver.createFileObserver(cacheFile,
                salmonFile, onFileContentsChanged);
        fileObserver.startWatching();
    }

    public static void promptDialog(Activity activity, String title, String body,
                                    String buttonLabel1, DialogInterface.OnClickListener buttonListener1,
                                    String buttonLabel2, DialogInterface.OnClickListener buttonListener2) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        if (title != null)
            builder.setTitle(title);
        builder.setMessage(body);
        if (buttonLabel1 != null)
            builder.setPositiveButton(buttonLabel1, buttonListener1);
        if (buttonLabel2 != null)
            builder.setNegativeButton(buttonLabel2, buttonListener2);

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(title);
        alertDialog.setCancelable(true);

        if (!activity.isFinishing())
            alertDialog.show();
    }

    public static void promptSingleValue(Activity activity, String title,
                                         List<String> items, int currSelection, DialogInterface.OnClickListener onClickListener) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        if (title != null)
            builder.setTitle(title);
        builder.setSingleChoiceItems(items.toArray(new String[0]), currSelection, onClickListener);

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(title);
        alertDialog.setCancelable(true);

        if (!activity.isFinishing())
            alertDialog.show();
    }
}
