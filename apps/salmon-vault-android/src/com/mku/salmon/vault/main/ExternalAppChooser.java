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
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import com.mku.android.file.AndroidDrive;
import com.mku.android.file.AndroidSharedFileObserver;
import com.mku.salmon.vault.android.R;
import com.mku.func.BiConsumer;
import com.mku.salmon.vault.config.SalmonConfig;
import com.mku.salmon.vault.dialog.SalmonDialog;
import com.mku.salmonfs.SalmonDriveManager;
import com.mku.salmonfs.SalmonFile;
import com.mku.utils.SalmonFileUtils;

import java.util.List;
import java.util.TreeMap;

public class ExternalAppChooser {
    public static void chooseApp(Activity activity, SalmonFile salmonFile, int action,
                                 BiConsumer<Uri, AndroidSharedFileObserver> ReimportSharedFile) throws Exception {
        java.io.File sharedFile = ((AndroidDrive) SalmonDriveManager.getDrive()).
                copyToSharedFolder(salmonFile);
        sharedFile.deleteOnExit();
        String ext = SalmonFileUtils.getExtensionFromFileName(salmonFile.getBaseName()).toLowerCase();
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        android.net.Uri uri = FileProvider.getUriForFile(activity, SalmonConfig.FILE_PROVIDER, sharedFile);
        ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity).setType(mimeType);

        Intent intent;
        // if we just share (final) we can show the android chooser activity
        // since we don't have to grant the app write permissions
        if (action == ActionType.VIEW_EXTERNAL.ordinal()) {
            intent = builder.createChooserIntent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(uri);

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent finalIntent1 = intent;
            activity.runOnUiThread(() ->
            {
                try {
                    activity.startActivity(finalIntent1);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Toast.makeText(activity, activity.getString(R.string.NoApplicationsFound), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // we show only apps that explicitly have intent filters for action edit
            if (action == ActionType.SHARE.ordinal()) {
                builder.setStream(uri);
                intent = builder.getIntent();
                intent.setAction(Intent.ACTION_SEND);
            } else {
                intent = builder.getIntent();
                intent.setAction(Intent.ACTION_EDIT);
                intent.setData(uri);
            }

            // we offer the user a list so they can grant write permissions only to that app
            TreeMap<String, String> apps = GetAppsForIntent(activity, intent);
            Intent finalIntent = intent;
            activity.runOnUiThread(() ->
            {
                SalmonDialog.promptOpenWith(finalIntent, apps, uri, sharedFile, salmonFile,
                        action == ActionType.EDIT.ordinal(), (fileObserver) ->
                        {
                            ReimportSharedFile.accept(uri, fileObserver);
                        });
            });
        }
    }

    public static TreeMap<String, String> GetAppsForIntent(Activity activity, Intent intent) {
        List<ResolveInfo> appInfoList = activity.getPackageManager().queryIntentActivities(intent, 0);
        TreeMap<String, String> apps = new TreeMap<>();
        for (ResolveInfo resolveInfo : appInfoList) {
            String name = activity.getPackageManager().getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString();
            String packageName = resolveInfo.activityInfo.applicationInfo.packageName;
            apps.put(name, packageName);
        }
        return apps;
    }
}