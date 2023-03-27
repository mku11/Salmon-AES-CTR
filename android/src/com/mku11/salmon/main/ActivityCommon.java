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
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mku.android.salmonvault.R;
import com.mku11.salmon.file.AndroidFile;
import com.mku11.salmon.file.AndroidSharedFileObserver;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonAuthException;
import com.mku11.salmonfs.SalmonDrive;
import com.mku11.salmonfs.SalmonDriveManager;
import com.mku11.salmonfs.SalmonFile;

import java.io.File;
import java.util.LinkedHashMap;
import com.mku11.salmon.func.BiConsumer;
import com.mku11.salmon.func.Consumer;

public class ActivityCommon {
    public static final String EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";
    static final String TAG = ActivityCommon.class.getName();

    public static void promptPassword(Activity activity, Consumer<SalmonDrive> OnAuthenticationSucceded) {
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
                    OnAuthenticationSucceded.accept(SalmonDriveManager.getDrive());
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

    public static void promptSetPassword(Activity activity, Consumer<String> OnPasswordChanged) {
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
                promptSetPassword(activity, OnPasswordChanged);
            else {
            	if (OnPasswordChanged != null)
                	OnPasswordChanged.accept(typePasswd.getText().toString());
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

    public static void promptEdit(Activity activity, String title, String msg,
                                  String value, String option, BiConsumer<String, Boolean> OnEdit) {
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
                OnEdit.accept(valueText.getText().toString(), optionCheckBox.isChecked());
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
                               Consumer<AndroidSharedFileObserver> OnFileContentsChanged) {

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

    private static void setFileContentsChangedObserver(File cacheFile, SalmonFile salmonFile,
                                                       Consumer<AndroidSharedFileObserver> onFileContentsChanged) {
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

    public static void promptSingleValue(Activity activity, ArrayAdapter<CharSequence> adapter, String title,
                                         int currSelection, DialogInterface.OnClickListener onClickListener) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        if (title != null)
            builder.setTitle(title);
        builder.setSingleChoiceItems(adapter, currSelection, onClickListener);

        androidx.appcompat.app.AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(title);
        alertDialog.setCancelable(true);

        if (!activity.isFinishing())
            alertDialog.show();
    }


    public static void OpenVault(Context context, String dirPath) throws Exception {
        SalmonDriveManager.openDrive(dirPath);
        SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
        SettingsActivity.setVaultLocation(context, dirPath);
    }

    public static void CreateVault(Context context, String dirPath, String password) throws Exception {
        SalmonDriveManager.createDrive(dirPath, password);
        SalmonDriveManager.getDrive().setEnableIntegrityCheck(true);
        SettingsActivity.setVaultLocation(context, dirPath);
    }

    public static void openFilesystem(Activity activity, boolean folder, boolean multiSelect, String lastDir, int resultCode) {
        Intent intent = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (folder && lastDir != null) {
            try {
                Uri uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, "primary:");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (!folder) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        }

        if (multiSelect) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        String prompt = "Open File(s)";
        if (folder)
            prompt = "Open Directory";

        intent.putExtra(DocumentsContract.EXTRA_PROMPT, prompt);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            activity.startActivityForResult(intent, resultCode);
        } catch (Exception e) {
            Toast.makeText(activity, "Could not start picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void setUriPermissions(Context context, Intent data, Uri uri) {
        int takeFlags = 0;
        if (data != null)
            takeFlags = (int) data.getFlags();
        takeFlags &= (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            context.grantUriPermission(context.getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.grantUriPermission(context.getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            context.grantUriPermission(context.getPackageName(), uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not grant uri perms to activity: " + ex;
            Toast.makeText(context, err, Toast.LENGTH_LONG).show();
        }

        try {
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not take Persistable perms: " + ex;
            Toast.makeText(context, err, Toast.LENGTH_LONG).show();
        }
    }

    public static IRealFile[] getFilesFromIntent(Context context, Intent data) {
        IRealFile[] files = null;

        if (data != null) {
            if (null != data.getClipData()) {
                files = new IRealFile[data.getClipData().getItemCount()];
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    ActivityCommon.setUriPermissions(context, data, uri);
                    DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                    files[i] = new AndroidFile(docFile, context);
                }
            } else {
                android.net.Uri uri = data.getData();
                files = new IRealFile[1];
                ActivityCommon.setUriPermissions(context, data, uri);
                DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                files[0] = new AndroidFile(docFile, context);
            }
        }
        return files;
    }
}
