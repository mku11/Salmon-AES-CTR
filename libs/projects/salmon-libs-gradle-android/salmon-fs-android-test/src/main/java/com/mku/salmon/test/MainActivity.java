package com.mku.salmon.test;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;

import com.mku.android.fs.file.AndroidFile;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OPEN_FOLDER = 1000;
    Button testFolder;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setContentView(R.layout.activity_main);
        testFolder = findViewById(R.id.TEST_FOLDER_BUTTON);
        AndroidFileSystem.initialize(this);
        testFolder.setOnClickListener((e) -> {
            openFilesystem(this, REQUEST_OPEN_FOLDER);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        if (requestCode == REQUEST_OPEN_FOLDER) {
            setUriPermissions(data, uri);
            IFile file = getFile(uri.toString(), true);
            setVaultLocation(file.getPath());
        }
    }

    public IFile getFile(String filepath, boolean isDirectory) {
        IFile file;
        DocumentFile docFile;
        if (isDirectory)
            docFile = DocumentFile.fromTreeUri(this, android.net.Uri.parse(filepath));
        else
            docFile = DocumentFile.fromSingleUri(this, android.net.Uri.parse(filepath));
        file = new AndroidFile(docFile);
        return file;
    }

    public static void openFilesystem(Activity activity, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        String prompt = "Open Directory";
        intent.putExtra(DocumentsContract.EXTRA_PROMPT, prompt);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            activity.startActivityForResult(intent, resultCode);
        } catch (Exception e) {
            Toast.makeText(activity, "Could not start file picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void setUriPermissions(Intent data, Uri uri) {
        int takeFlags = 0;
        if (data != null)
            takeFlags = (int) data.getFlags();
        takeFlags &= (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not grant uri perms to activity: " + ex;
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        }

        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ex) {
            ex.printStackTrace();
            String err = "Could not take Persistable perms: " + ex;
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        }
    }

    public String getVaultLocation() {
        return prefs.getString("vaultLocation", null);
    }

    public void setVaultLocation(String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("vaultLocation", value);
        editor.apply();
    }
}