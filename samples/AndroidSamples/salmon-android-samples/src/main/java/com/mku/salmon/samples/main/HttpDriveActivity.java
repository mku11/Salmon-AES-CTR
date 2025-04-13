package com.mku.salmon.samples.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.HttpFile;
import com.mku.fs.file.IFile;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.samples.utils.AndroidFileChooser;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmonfs.drive.AesDrive;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HttpDriveActivity extends AppCompatActivity {
    public static final int REQUEST_EXPORT_FILES = 1003;
    private EditText httpURL;
    private TextInputEditText password;
    private Button openDriveButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;

    private int threads = 1;
    private static final String defaultPassword = "test123";
    String defaultHttpDriveURL = "";
    private AesDrive httpDrive;

    private final Executor executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_http_drive);

        httpURL = findViewById(R.id.HTTP_DRIVE_LOCATION);
        httpURL.setText(defaultHttpDriveURL);
        password = findViewById(R.id.TEXT_PASSWORD);

        openDriveButton = findViewById(R.id.OPEN_DRIVE_BUTTON);
        openDriveButton.setOnClickListener((e) -> {
            executor.execute(() -> {
                openDrive();
            });
        });

        listFilesButton = findViewById(R.id.LIST_DRIVE_BUTTON);
        listFilesButton.setOnClickListener((e) -> {
            executor.execute(() -> {
                listFiles();
            });
        });
        exportFilesButton = findViewById(R.id.EXPORT_FILES_BUTTON);
        exportFilesButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_EXPORT_FILES, true);
        });

        closeDriveButton = findViewById(R.id.CLOSE_DRIVE_BUTTON);
        closeDriveButton.setOnClickListener((e) -> {
            executor.execute(() -> {
                closeDrive();
            });
        });

        outputText = findViewById(R.id.OUTPUT_TEXT);
        password.setText(defaultPassword);

        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);

        initialize();
    }

    public void log(String msg) {
        runOnUiThread(() -> {
            outputText.append(msg + "\n");
        });
    }

    public void clearLog() {
        runOnUiThread(() -> {
            outputText.setText("");
        });
    }

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void openDrive() {
        clearLog();

        try {
            IFile driveDir = new HttpFile(httpURL.getText().toString());
            httpDrive = DriveSample.openDrive(driveDir, password.getText().toString(),
                    this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void listFiles() {
        try {
            DriveSample.listFiles(httpDrive, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void exportFiles(IFile exportDir) {
        try {
            DriveSample.exportFiles(httpDrive, exportDir, threads, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void closeDrive() {
        DriveSample.closeDrive(httpDrive, this::log);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        AndroidFileChooser.setUriPermissions(this, data, uri);
        if (requestCode == REQUEST_EXPORT_FILES) {
            IFile exportDir = AndroidFileSystem.getRealFile(uri.toString(), true);
            executor.execute(() -> {
                exportFiles(exportDir);
            });
        }
    }
}