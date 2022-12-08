package com.mku11.salmon.file;
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
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.mku11.salmon.main.ActivityCommon;
import com.mku11.salmon.main.SalmonApplication;
import com.mku11.salmonfs.IRealFile;
import com.mku11.salmonfs.SalmonDrive;
import com.mku11.salmonfs.SalmonFile;
import com.mku11.salmonfs.SalmonFileExporter;

import java.util.List;

/**
 * Implementation of a virtual drive for android.
 */
public class AndroidDrive extends SalmonDrive {
    public static final String TAG = AndroidDrive.class.getName();
    public static final String EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents";
    private static final int ENC_BUFFER_SIZE = 512 * 1024;
    private static final int ENC_THREADS = 4;
    public static final int REQUEST_SDCARD_CODE_IMPORT_FILE = 10000;
    public static final int REQUEST_SDCARD_VAULT_FOLDER = 10001;


    /**
     * Instantiate a virtual Drive for android under a real directory path
     *
     * @param realRoot The path of the real directory
     */
    public AndroidDrive(String realRoot) {
        super(realRoot);
    }

    public static void setFilePermissions(Intent data, Uri uri) {
        int takeFlags = 0;
        if (data != null)
            takeFlags = (int) data.getFlags();
        takeFlags &= (
                (int) Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        (int) Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        try {
            SalmonApplication.getInstance().getApplicationContext().grantUriPermission(SalmonApplication.getInstance().getApplicationContext().getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SalmonApplication.getInstance().getApplicationContext().grantUriPermission(SalmonApplication.getInstance().getApplicationContext().getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            SalmonApplication.getInstance().getApplicationContext().grantUriPermission(SalmonApplication.getInstance().getApplicationContext().getPackageName(), uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(SalmonApplication.getInstance().getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            SalmonApplication.getInstance().getApplicationContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(SalmonApplication.getInstance().getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static List<UriPermission> getPermissionsList() {
        List<UriPermission> list = SalmonApplication.getInstance().getApplicationContext().getContentResolver().getPersistedUriPermissions();
        return list;
    }

    // TODO: use multiple threads for performance
    public static java.io.File copyToSharedFolder(SalmonFile salmonFile) throws Exception {
        java.io.File privateDir = getPrivateDir();
        java.io.File cacheFile = new java.io.File(privateDir, salmonFile.getBaseName());
        AndroidSharedFileObserver.removeFileObserver(cacheFile);
        cacheFile.delete();

        AndroidFile sharedDir = new AndroidFile(DocumentFile.fromFile(privateDir), SalmonApplication.getInstance().getApplicationContext());
        SalmonFileExporter fileExporter = new SalmonFileExporter(ENC_BUFFER_SIZE, ENC_THREADS);
        fileExporter.exportFile(salmonFile, sharedDir, false, null, 1, 1);
        return cacheFile;
    }

    protected static java.io.File getPrivateDir() {
        java.io.File sharedDir = new java.io.File(SalmonApplication.getInstance().getApplicationContext().getCacheDir(), SHARE_DIR);
        if (!sharedDir.exists())
            sharedDir.mkdir();
        return sharedDir;
    }

    public static IRealFile[] getFilesFromIntent(Context context, Intent data) {
        IRealFile[] filesToImport = null;

        if (data != null) {
            if (null != data.getClipData()) {
                filesToImport = new IRealFile[data.getClipData().getItemCount()];
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    AndroidDrive.setFilePermissions(data, uri);
                    DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                    filesToImport[i] = new AndroidFile(docFile, context);
                }
            } else {
                android.net.Uri uri = data.getData();
                filesToImport = new IRealFile[1];
                AndroidDrive.setFilePermissions(data, uri);
                DocumentFile docFile = DocumentFile.fromSingleUri(context, uri);
                filesToImport[0] = new AndroidFile(docFile, context);
            }
        }
        return filesToImport;
    }

    public static IRealFile getFile(DocumentFile docFile) {
        return new AndroidFile(docFile, SalmonApplication.getInstance().getApplicationContext());
    }

    protected IRealFile getFile(String filepath, boolean root) {
        DocumentFile docFile;
        if (root)
            docFile = DocumentFile.fromTreeUri(SalmonApplication.getInstance().getApplicationContext(), Uri.parse(filepath));
        else
            docFile = DocumentFile.fromSingleUri(SalmonApplication.getInstance().getApplicationContext(), Uri.parse(filepath));
        AndroidFile file = new AndroidFile(docFile, SalmonApplication.getInstance().getApplicationContext());
        return file;
    }


    /**
     * Prompt user to select a real directory for selecting/creating the virtual drive
     *
     * @param activity Activity
     * @param body     Text to show when file/directory dialog opens
     * @param folder   True if you want to select a folder
     * @param lastDir  The initial directory
     */
    public void pickFiles(Activity activity, String body, boolean folder, String lastDir) {
        activity.runOnUiThread(() -> ActivityCommon.promptDialog(activity, null, body, "OK",
                (DialogInterface dialog, int which) -> promptSAFOpenDocument(activity, folder, lastDir), null, null));
    }

    @Override
    public void onAuthenticationSuccess() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(SalmonApplication.getInstance().getApplicationContext().getCacheDir());
    }

    @Override
    protected void onAuthenticationError() {
        AndroidSharedFileObserver.clearFileObservers();
        clearCache(SalmonApplication.getInstance().getApplicationContext().getCacheDir());
    }

    private void clearCache(java.io.File file) {
        if (file.exists() && file.isDirectory()) {
            java.io.File[] cacheFiles = file.listFiles();
            if (cacheFiles != null) {
                for (java.io.File cacheFile : cacheFiles) {
                    clearCache(cacheFile);
                }
            }
        } else {
            file.delete();
        }
    }

    private void promptSAFOpenDocument(Activity activity, boolean folder, String lastDir) {
        Intent intent = new Intent(folder ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        if (folder && lastDir == null) {
            try {
                Uri uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_PROVIDER_AUTHORITY, "primary:");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (!folder) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        }

        String prompt = "Open File(s)";
        if (folder)
            prompt = "Open Directory";

        intent.putExtra(DocumentsContract.EXTRA_PROMPT, prompt);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            activity.startActivityForResult(intent, folder ? REQUEST_SDCARD_VAULT_FOLDER : REQUEST_SDCARD_CODE_IMPORT_FILE);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(activity, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
