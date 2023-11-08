package com.mku.salmon.vault.main;
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
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.util.HashMap;

public class ActivityCommon {
    public static final String EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";
    static final String TAG = ActivityCommon.class.getName();

    public static void openFilesystem(Activity activity, boolean folder, boolean multiSelect,
                                      HashMap<String, String> filter, String initialDir, int resultCode) {
        Intent intent = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (initialDir != null) {
            try {
                DocumentFile documentFile;
                if (folder)
                    documentFile = DocumentFile.fromTreeUri(activity, Uri.parse(initialDir));
                else
                    documentFile = DocumentFile.fromSingleUri(activity, Uri.parse(initialDir));
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentFile.getUri());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (!folder) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            String mimeType = null;
            if (filter != null) {
                String filterKey = filter.keySet().toArray(new String[0])[0];
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filter.get(filterKey));
            }
            if (mimeType != null) {
                intent.setType(mimeType);
            } else {
                intent.setType("*/*");
            }
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
            Toast.makeText(activity, "Could not start file picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void setUriPermissions(Intent data, Uri uri) {
        int takeFlags = 0;
        if (data != null)
            takeFlags = (int) data.getFlags();
        takeFlags &= (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            SalmonApplication.getInstance().grantUriPermission(SalmonApplication.getInstance().getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SalmonApplication.getInstance().grantUriPermission(SalmonApplication.getInstance().getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            SalmonApplication.getInstance().grantUriPermission(SalmonApplication.getInstance().getPackageName(), uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not grant uri perms to activity: " + ex;
            Toast.makeText(SalmonApplication.getInstance(), err, Toast.LENGTH_LONG).show();
        }

        try {
            SalmonApplication.getInstance().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not take Persistable perms: " + ex;
            Toast.makeText(SalmonApplication.getInstance(), err, Toast.LENGTH_LONG).show();
        }
    }


    /// <summary>
    /// Retrieve real files from an intent that was received
    /// </summary>
    /// <param name="context"></param>
    /// <param name="data"></param>
    /// <returns></returns>
    public static String[] getFilesFromIntent(Context context, Intent data) {
        String[] files = null;

        if (data != null) {
            if (null != data.getClipData()) {
                files = new String[data.getClipData().getItemCount()];
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    String filename = uri.toString();
                    Log.d(TAG, "File: " + filename);
                    files[i] = uri.toString();
                }
            } else {
                android.net.Uri uri = data.getData();
                files = new String[1];
                files[0] = uri.toString();
            }
        }
        return files;
    }

}
