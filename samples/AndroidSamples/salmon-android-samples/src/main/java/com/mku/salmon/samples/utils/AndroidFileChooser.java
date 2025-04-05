package com.mku.salmon.samples.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.mku.android.fs.file.AndroidFile;
import com.mku.fs.file.IFile;

public class AndroidFileChooser {

    public static IFile getFile(Context context, String uri, boolean isDirectory) {
        IFile file;
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(context, android.net.Uri.parse(uri));
        else
            docFile = DocumentFile.fromSingleUri(context, android.net.Uri.parse(uri));
        file = new AndroidFile(docFile);
        return file;
    }

    public static void openFilesystem(Activity activity, int resultCode, boolean isFolder) {
        openFilesystem(activity, resultCode, isFolder, false);
    }

    public static void openFilesystem(Activity activity, int resultCode, boolean isFolder, boolean multiSelect) {
        Intent intent = new Intent(isFolder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (!isFolder) {
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        }

        if (!isFolder && multiSelect) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        String prompt = "Open File(s)";
        if (isFolder)
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

    public static IFile[] getFiles(Context context, Intent data) {
        IFile[] files = null;
        if (data != null) {
            if (null != data.getClipData()) {
                files = new IFile[data.getClipData().getItemCount()];
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    AndroidFileChooser.setUriPermissions(context, data, uri);
                    files[i] = getFile(context, uri.toString(), false);
                }
            } else {
                android.net.Uri uri = data.getData();
                files = new IFile[1];
                files[0] = getFile(context, uri.toString(), false);
            }
        }
        return files;
    }
}
