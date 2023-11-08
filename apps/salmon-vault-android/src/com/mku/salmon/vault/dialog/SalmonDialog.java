package com.mku.salmon.vault.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.mku.android.file.AndroidSharedFileObserver;
import com.mku.func.Consumer;
import com.mku.salmon.vault.android.R;
import com.mku.salmon.vault.utils.WindowUtils;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;

import com.mku.func.BiConsumer;

import java.util.LinkedHashMap;
import java.util.TreeMap;

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
public class SalmonDialog {
    public static void promptEdit(String title, String msg, BiConsumer<String, Boolean> OnEdit,
                                  String value, boolean isFileName, boolean readOnly, boolean isPassword,
                                  String option) {
        Activity activity = WindowUtils.getUiActivity();
        WindowUtils.runOnMainThread(() ->
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);

            LinearLayout layout = new LinearLayout(activity);
            layout.setPadding(20, 20, 20, 20);
            layout.setOrientation(LinearLayout.VERTICAL);

            TextInputLayout msgText = new TextInputLayout(activity, null,
                    R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            msgText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            msgText.setBoxCornerRadii(5, 5, 5, 5);
            msgText.setHint(msg);
            View valueText;
            TextInputEditText typePasswd = null;
            if (isPassword) {
                TextInputLayout typePasswdText = new TextInputLayout(activity, null,
                        R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
                typePasswdText.setPasswordVisibilityToggleEnabled(true);
                typePasswdText.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
                typePasswdText.setBoxCornerRadii(5, 5, 5, 5);
                typePasswdText.setHint(msg);

                typePasswd = new TextInputEditText(typePasswdText.getContext());
                typePasswd.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD |
                        InputType.TYPE_CLASS_TEXT);
                typePasswd.setText(value);
                typePasswdText.addView(typePasswd);
                valueText = typePasswdText;
            } else {
                TextInputEditText text = new TextInputEditText(msgText.getContext());
                text.setText(value);
                if (readOnly) {
                    text.setInputType(InputType.TYPE_NULL);
                    text.setTextIsSelectable(true);
                } else {
                    text.setInputType(InputType.TYPE_CLASS_TEXT);
                }
                if (isFileName) {
                    String ext = SalmonFileUtils.getExtensionFromFileName(value);
                    if (ext != null && ext.length() > 0)
                        text.setSelection(0, value.length() - ext.length() - 1);
                    else
                        text.setSelection(0, value.length());
                } else {
                    text.selectAll();
                }
                valueText = text;
            }
            LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            msgText.addView(valueText);
            layout.addView(msgText, parameters);
            CheckBox optionCheckBox = new CheckBox(activity);
            if (option != null) {
                optionCheckBox.setText(option);
                layout.addView(optionCheckBox, parameters);
            }
            TextInputEditText finalTypePasswd = typePasswd;
            builder.setPositiveButton(activity.getString(android.R.string.ok), (sender, e) ->
            {
                if (OnEdit != null) {
                    if (isPassword)
                        OnEdit.accept(finalTypePasswd.getText().toString(), optionCheckBox.isChecked());
                    else
                        OnEdit.accept(((EditText) valueText).getText().toString(), optionCheckBox.isChecked());
                }
            });
            builder.setNegativeButton(activity.getString(android.R.string.cancel), (sender, e) ->
            {
                ((AlertDialog) sender).dismiss();
            });

            AlertDialog alertDialog = builder.create();
            alertDialog.setTitle(title);
            alertDialog.setCancelable(true);
            alertDialog.setView(layout);

            if (!activity.isFinishing())
                alertDialog.show();
        });
    }

    public static void promptDialog(String title, String body,
                                    String buttonLabel1, Runnable buttonListener1,
                                    String buttonLabel2, Runnable buttonListener2) {
        if (buttonLabel1 == null)
            buttonLabel1 = "Ok";
        Activity activity = WindowUtils.getUiActivity();
        String finalButtonLabel = buttonLabel1;
        WindowUtils.runOnMainThread(() ->
        {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            if (title != null)
                builder.setTitle(title);
            builder.setMessage(body);
            if (finalButtonLabel != null)
                builder.setPositiveButton(finalButtonLabel, (s, e) ->
                {
                    if (buttonListener1 != null)
                        buttonListener1.run();
                });
            if (buttonLabel2 != null)
                builder.setNegativeButton(buttonLabel2, (s, e) ->
                {
                    if (buttonListener2 != null)
                        buttonListener2.run();
                });

            AlertDialog alertDialog = builder.create();
            alertDialog.setTitle(title);
            alertDialog.setCancelable(true);

            if (!activity.isFinishing())
                alertDialog.show();
        });
    }


    public static void promptSingleValue(ArrayAdapter<String> adapter, String title,
                                         int currSelection, BiConsumer<AlertDialog, Integer> onClickListener) {
        Activity activity = WindowUtils.getUiActivity();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        if (title != null)
            builder.setTitle(title);
        builder.setSingleChoiceItems(adapter, currSelection, (sender, which) ->
        {
            onClickListener.accept((AlertDialog) sender, which);
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.setTitle(title);
        alertDialog.setCancelable(true);

        if (!activity.isFinishing())
            alertDialog.show();
    }


    public static void promptOpenWith(Intent intent, TreeMap<String, String> apps,
                                      android.net.Uri uri, java.io.File sharedFile,
                                      SalmonFile salmonFile, boolean allowWrite,
                                      Consumer<AndroidSharedFileObserver> OnFileContentsChanged) {
        Activity activity = WindowUtils.getUiActivity();
        String[] names = apps.keySet().toArray(new String[0]);
        String[] packageNames = apps.values().toArray(new String[0]);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.getString(R.string.ChooseApp));
        builder.setSingleChoiceItems(names, -1, (sender, which) ->
        {
            try {
                AlertDialog alertDialog = (AlertDialog) sender;
                alertDialog.dismiss();

                int activityFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (allowWrite)
                    activityFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

                AndroidSharedFileObserver fileObserver = AndroidSharedFileObserver.createFileObserver(sharedFile,
                        salmonFile, OnFileContentsChanged);
                fileObserver.startWatching();

                activity.grantUriPermission(packageNames[which], uri, activityFlags);
                intent.setPackage(packageNames[which]);
                activity.startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(activity, "Could not start application", Toast.LENGTH_LONG).show();
                ex.printStackTrace();
                sharedFile.delete();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public static void promptDialog(String body) {
        promptDialog("", body, null, null, null, null);
    }

    public static void promptDialog(String title, String body) {
        promptDialog(title, body, null, null, null, null);
    }

    public static void promptDialog(String title, String body, String buttonLabel) {
        promptDialog(title, body, buttonLabel, null, null, null);
    }
}