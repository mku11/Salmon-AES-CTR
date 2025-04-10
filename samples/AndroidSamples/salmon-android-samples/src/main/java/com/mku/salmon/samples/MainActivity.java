package com.mku.salmon.samples;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.Toast;

import com.mku.android.fs.file.AndroidFile;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.IFile;
import com.mku.salmon.samples.main.DataActivity;
import com.mku.salmon.samples.main.DataStreamActivity;
import com.mku.salmon.samples.main.FileActivity;
import com.mku.salmon.samples.main.HttpDriveActivity;
import com.mku.salmon.samples.main.LocalDriveActivity;
import com.mku.salmon.samples.main.TextActivity;
import com.mku.salmon.samples.main.WebServiceDriveActivity;

public class MainActivity extends AppCompatActivity {
    private Button textButton;
    private Button dataButton;
    private Button dataStreamButton;
    private Button fileButton;
    private Button localDriveButton;
    private Button httpDriveButton;
    private Button webServiceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textButton = findViewById(R.id.TEXT_BUTTON);
        textButton.setOnClickListener((e) -> {
            runActivity(TextActivity.class);
        });

        dataButton = findViewById(R.id.DATA_BUTTON);
        dataButton.setOnClickListener((e) -> {
            runActivity(DataActivity.class);
        });

        dataStreamButton = findViewById(R.id.DATA_STREAM_BUTTON);
        dataStreamButton.setOnClickListener((e) -> {
            runActivity(DataStreamActivity.class);
        });

        fileButton = findViewById(R.id.FILE_BUTTON);
        fileButton.setOnClickListener((e) -> {
            runActivity(FileActivity.class);
        });

        localDriveButton = findViewById(R.id.LOCAL_DRIVE_BUTTON);
        localDriveButton.setOnClickListener((e) -> {
            runActivity(LocalDriveActivity.class);
        });

        httpDriveButton = findViewById(R.id.HTTP_DRIVE_BUTTON);
        httpDriveButton.setOnClickListener((e) -> {
            runActivity(HttpDriveActivity.class);
        });

        webServiceButton = findViewById(R.id.WEB_SERVICE_BUTTON);
        webServiceButton.setOnClickListener((e) -> {
            runActivity(WebServiceDriveActivity.class);
        });
    }

    private void runActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }
}